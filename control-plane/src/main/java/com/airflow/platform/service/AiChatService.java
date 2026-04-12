package com.airflow.platform.service;

import com.airflow.platform.dto.AiChatRequest;
import com.airflow.platform.dto.AiChatResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI coding assistant proxy. Supports Anthropic Claude and any OpenAI-compatible
 * endpoint (Groq, Ollama, OpenAI, Together AI, etc.).
 *
 * <p>Provider priority: user-supplied provider/key from browser → server config.
 */
@Service
@Slf4j
public class AiChatService {

    // ── Provider constants ────────────────────────────────────────────────────

    private static final String PROVIDER_ANTHROPIC = "anthropic";
    private static final String PROVIDER_GROQ      = "groq";
    private static final String PROVIDER_OLLAMA    = "ollama";
    private static final String PROVIDER_OPENAI    = "openai";

    private static final String ANTHROPIC_API_URL  = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION  = "2023-06-01";
    private static final String GROQ_BASE_URL      = "https://api.groq.com/openai/v1";
    private static final String OLLAMA_BASE_URL    = "http://localhost:11434/v1";
    private static final String OPENAI_BASE_URL    = "https://api.openai.com/v1";

    private static final Map<String, String> DEFAULT_MODELS = Map.of(
            PROVIDER_ANTHROPIC, "claude-3-5-haiku-20241022",
            PROVIDER_GROQ,      "llama-3.3-70b-versatile",
            PROVIDER_OLLAMA,    "llama3.2",
            PROVIDER_OPENAI,    "gpt-4o-mini"
    );

    // ── System prompt ─────────────────────────────────────────────────────────

    private static final String SYSTEM_PROMPT =
            "You are an Airflow DAG developer assistant in the Flow Deck IDE. " +
            "Help users write, debug, and optimise Airflow DAGs and Python code. " +
            "Tools available: list_files, read_file, write_file. " +
            "Be concise. Use Airflow 2/3 idioms. Use ```python fences for code.";

    // ── Tool definitions (Anthropic format — converted for OpenAI if needed) ──

    private static final List<Map<String, Object>> TOOLS = List.of(
            Map.of("name", "list_files",
                    "description", "List all files in the current project with their paths and types.",
                    "input_schema", Map.of("type", "object", "properties", Map.of())),
            Map.of("name", "read_file",
                    "description", "Read the full content of a project file by its path.",
                    "input_schema", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "path", Map.of("type", "string",
                                            "description", "File path relative to project root, e.g. dags/my_dag.py")),
                            "required", List.of("path"))),
            Map.of("name", "write_file",
                    "description", "Replace the complete content of an existing project file to apply code changes.",
                    "input_schema", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "path", Map.of("type", "string"),
                                    "content", Map.of("type", "string",
                                            "description", "Complete new file content")),
                            "required", List.of("path", "content")))
    );

    // ── Spring config ─────────────────────────────────────────────────────────

    @Value("${ai.provider:groq}")
    private String serverProvider;

    @Value("${ai.anthropic.api-key:}")
    private String anthropicServerKey;

    @Value("${ai.anthropic.model:claude-3-5-haiku-20241022}")
    private String anthropicServerModel;

    @Value("${ai.openai-compatible.base-url:" + GROQ_BASE_URL + "}")
    private String openAiBaseUrl;

    @Value("${ai.openai-compatible.api-key:}")
    private String openAiServerKey;

    @Value("${ai.openai-compatible.model:llama-3.3-70b-versatile}")
    private String openAiServerModel;

    @Value("${ai.anthropic.max-tokens:4096}")
    private int maxTokens;

    /** Max seconds to wait on a 429 before retrying once. */
    private static final int MAX_RETRY_WAIT_SECONDS = 30;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiChatService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    // ── Public entry point ────────────────────────────────────────────────────

    public AiChatResponse chat(AiChatRequest request) {
        String provider = resolveProvider(request.getUserProvider());
        String key      = resolveKey(provider, request.getUserApiKey());
        String model    = resolveModel(provider, request.getUserModel());

        if (!PROVIDER_OLLAMA.equals(provider) && !StringUtils.hasText(key)) {
            return AiChatResponse.error(
                    "No API key configured for provider '" + provider + "'. Enter your key via the key button in the chat panel.");
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return AiChatResponse.error("No messages provided");
        }

        try {
            if (PROVIDER_ANTHROPIC.equals(provider)) {
                return doAnthropicChat(request, key, model);
            } else {
                return doOpenAiCompatibleChat(request, key, model, resolveBaseUrl(provider, request.getUserProvider()));
            }
        } catch (Exception e) {
            log.error("AI chat failed [provider={}]: {}", provider, e.getMessage());
            return AiChatResponse.error("AI error: " + e.getMessage());
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    /**
     * POST to {@code url} with the given body, retrying once after the Retry-After
     * delay on HTTP 429 (up to {@link #MAX_RETRY_WAIT_SECONDS} seconds).
     */
    private String postWithRetry(String url, String authHeader, String body) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                var req = restClient.post().uri(url)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                if (authHeader != null) req = req.header(HttpHeaders.AUTHORIZATION, authHeader);
                return req.body(body).retrieve().body(String.class);
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt == 0) {
                    long waitMs = parse429WaitMs(e.getResponseBodyAsString());
                    log.warn("Groq 429 — waiting {}ms before retry", waitMs);
                    Thread.sleep(waitMs);
                } else {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    /** Parse "Please try again in X.XXs" from a Groq 429 body; falls back to 5 s. */
    private long parse429WaitMs(String body) {
        try {
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("try again in ([0-9.]+)s").matcher(body);
            if (m.find()) {
                double secs = Double.parseDouble(m.group(1));
                long ms = (long) (secs * 1000) + 500; // add 500 ms buffer
                return Math.min(ms, MAX_RETRY_WAIT_SECONDS * 1000L);
            }
        } catch (Exception ignored) {}
        return 5_000;
    }

    // ── Anthropic ─────────────────────────────────────────────────────────────

    private AiChatResponse doAnthropicChat(AiChatRequest request, String key, String model) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", buildSystemPrompt(request.getFileName(), request.getFileContent()));
        if (request.isUseTools()) {
            body.set("tools", objectMapper.valueToTree(TOOLS));
        }
        body.set("messages", objectMapper.valueToTree(trimHistory(request.getMessages())));

        // Anthropic uses x-api-key header — build a custom request (no Bearer prefix)
        String rawBody = objectMapper.writeValueAsString(body);
        String raw;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                raw = restClient.post().uri(ANTHROPIC_API_URL)
                        .header("x-api-key", key)
                        .header("anthropic-version", ANTHROPIC_VERSION)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(rawBody).retrieve().body(String.class);
                return parseAnthropicResponse(raw);
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt == 0) {
                    long waitMs = parse429WaitMs(e.getResponseBodyAsString());
                    log.warn("Anthropic 429 — waiting {}ms before retry", waitMs);
                    Thread.sleep(waitMs);
                } else {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    private AiChatResponse parseAnthropicResponse(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        String stopReason = root.path("stop_reason").asText("end_turn");
        JsonNode contentNode = root.path("content");
        List<Map<String, Object>> assistantContent = objectMapper.convertValue(
                contentNode, new TypeReference<>() {});

        if ("tool_use".equals(stopReason)) {
            List<AiChatResponse.ToolCall> calls = new ArrayList<>();
            for (JsonNode block : contentNode) {
                if ("tool_use".equals(block.path("type").asText())) {
                    calls.add(new AiChatResponse.ToolCall(
                            block.path("id").asText(),
                            block.path("name").asText(),
                            objectMapper.convertValue(block.path("input"), new TypeReference<>() {})));
                }
            }
            return AiChatResponse.toolUse(calls, assistantContent);
        }

        String reply = "";
        for (JsonNode block : contentNode) {
            if ("text".equals(block.path("type").asText())) {
                reply = block.path("text").asText("");
                break;
            }
        }
        return StringUtils.hasText(reply)
                ? AiChatResponse.ok(reply, assistantContent)
                : AiChatResponse.error("Empty response from AI");
    }

    // ── OpenAI-compatible (Groq / Ollama / OpenAI) ───────────────────────────

    private AiChatResponse doOpenAiCompatibleChat(
            AiChatRequest request, String key, String model, String baseUrl) throws Exception {

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);

        // System message as first message
        List<Map<String, Object>> openAiMessages = new ArrayList<>();
        openAiMessages.add(Map.of("role", "system",
                "content", buildSystemPrompt(request.getFileName(), request.getFileContent())));
        openAiMessages.addAll(convertToOpenAiMessages(trimHistory(request.getMessages())));

        body.set("messages", objectMapper.valueToTree(openAiMessages));

        if (request.isUseTools()) {
            body.set("tools", objectMapper.valueToTree(toOpenAiTools(TOOLS)));
            body.put("tool_choice", "auto");
        }

        String authHeader = StringUtils.hasText(key) ? "Bearer " + key : null;
        String raw = postWithRetry(baseUrl + "/chat/completions", authHeader,
                objectMapper.writeValueAsString(body));

        return parseOpenAiResponse(raw);
    }

    private AiChatResponse parseOpenAiResponse(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        JsonNode choice = root.path("choices").path(0);
        String finishReason = choice.path("finish_reason").asText("stop");
        JsonNode msgNode = choice.path("message");

        if ("tool_calls".equals(finishReason)) {
            // Build tool calls list
            List<AiChatResponse.ToolCall> calls = new ArrayList<>();
            List<Map<String, Object>> assistantContent = new ArrayList<>();

            String textContent = msgNode.path("content").asText("");
            if (StringUtils.hasText(textContent)) {
                assistantContent.add(Map.of("type", "text", "text", textContent));
            }

            for (JsonNode tc : msgNode.path("tool_calls")) {
                String id   = tc.path("id").asText();
                String name = tc.path("function").path("name").asText();
                String argsJson = tc.path("function").path("arguments").asText("{}");
                Map<String, Object> input = objectMapper.readValue(argsJson, new TypeReference<>() {});

                calls.add(new AiChatResponse.ToolCall(id, name, input));

                // Build Anthropic-format content block so frontend history stays consistent
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("type", "tool_use");
                block.put("id", id);
                block.put("name", name);
                block.put("input", input);
                assistantContent.add(block);
            }
            return AiChatResponse.toolUse(calls, assistantContent);
        }

        String reply = msgNode.path("content").asText("");
        if (!StringUtils.hasText(reply)) {
            return AiChatResponse.error("Empty response from AI");
        }
        List<Map<String, Object>> assistantContent = List.of(Map.of("type", "text", "text", reply));
        return AiChatResponse.ok(reply, assistantContent);
    }

    // ── Message format conversion: Anthropic history → OpenAI ────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertToOpenAiMessages(List<Map<String, Object>> anthropic) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> msg : anthropic) {
            String role = (String) msg.get("role");
            Object content = msg.get("content");

            if (content instanceof String) {
                result.add(Map.of("role", role, "content", content));
                continue;
            }

            if (!(content instanceof List)) continue;
            List<Map<String, Object>> blocks = (List<Map<String, Object>>) content;

            boolean hasToolUse    = blocks.stream().anyMatch(b -> "tool_use".equals(b.get("type")));
            boolean hasToolResult = blocks.stream().anyMatch(b -> "tool_result".equals(b.get("type")));

            if (hasToolUse) {
                // assistant message → OpenAI assistant with tool_calls
                String text = blocks.stream()
                        .filter(b -> "text".equals(b.get("type")))
                        .map(b -> (String) b.get("text"))
                        .filter(StringUtils::hasText)
                        .findFirst().orElse(null);

                List<Map<String, Object>> toolCalls = blocks.stream()
                        .filter(b -> "tool_use".equals(b.get("type")))
                        .map(b -> {
                            try {
                                String args = objectMapper.writeValueAsString(b.get("input"));
                                return (Map<String, Object>) Map.of(
                                        "id", b.get("id"),
                                        "type", "function",
                                        "function", Map.of(
                                                "name", b.get("name"),
                                                "arguments", args));
                            } catch (Exception ex) {
                                return (Map<String, Object>) Map.of(
                                        "id", b.get("id"),
                                        "type", "function",
                                        "function", Map.of("name", b.get("name"), "arguments", "{}"));
                            }
                        })
                        .collect(Collectors.toList());

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("role", "assistant");
                m.put("content", text);
                m.put("tool_calls", toolCalls);
                result.add(m);

            } else if (hasToolResult) {
                // user turn with tool results → multiple OpenAI tool messages
                for (Map<String, Object> block : blocks) {
                    if ("tool_result".equals(block.get("type"))) {
                        result.add(Map.of(
                                "role", "tool",
                                "tool_call_id", block.get("tool_use_id"),
                                "content", Objects.toString(block.get("content"), "")));
                    }
                }
            } else {
                // Generic array content → join text blocks
                String joined = blocks.stream()
                        .filter(b -> "text".equals(b.get("type")))
                        .map(b -> (String) b.get("text"))
                        .collect(Collectors.joining("\n"));
                result.add(Map.of("role", role, "content", joined));
            }
        }
        return result;
    }

    /** Convert Anthropic tool format to OpenAI function format. */
    private List<Map<String, Object>> toOpenAiTools(List<Map<String, Object>> tools) {
        return tools.stream()
                .map(t -> Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", t.get("name"),
                                "description", t.get("description"),
                                "parameters", t.get("input_schema"))))
                .collect(Collectors.toList());
    }

    // ── Resolution helpers ────────────────────────────────────────────────────

    private String resolveProvider(String userProvider) {
        if (StringUtils.hasText(userProvider)) return userProvider.toLowerCase();
        return StringUtils.hasText(serverProvider) ? serverProvider.toLowerCase() : PROVIDER_GROQ;
    }

    private String resolveKey(String provider, String userKey) {
        if (StringUtils.hasText(userKey)) return userKey.trim();
        return switch (provider) {
            case PROVIDER_ANTHROPIC -> anthropicServerKey;
            case PROVIDER_OLLAMA    -> "";   // Ollama needs no key
            default                 -> openAiServerKey;
        };
    }

    private String resolveModel(String provider, String userModel) {
        if (StringUtils.hasText(userModel)) return userModel.trim();
        return switch (provider) {
            case PROVIDER_ANTHROPIC -> anthropicServerModel;
            case PROVIDER_OLLAMA, PROVIDER_GROQ, PROVIDER_OPENAI -> openAiServerModel;
            default                 -> DEFAULT_MODELS.getOrDefault(provider, "llama-3.3-70b-versatile");
        };
    }

    private String resolveBaseUrl(String provider, String userProvider) {
        // If server provider matches, use server-configured URL (allows custom endpoints)
        if (!StringUtils.hasText(userProvider) || provider.equals(serverProvider)) {
            return openAiBaseUrl;
        }
        return switch (provider) {
            case PROVIDER_GROQ   -> GROQ_BASE_URL;
            case PROVIDER_OLLAMA -> OLLAMA_BASE_URL;
            case PROVIDER_OPENAI -> OPENAI_BASE_URL;
            default              -> openAiBaseUrl;
        };
    }

    private String buildSystemPrompt(String fileName, String fileContent) {
        if (!StringUtils.hasText(fileContent)) return SYSTEM_PROMPT;
        String name = StringUtils.hasText(fileName) ? fileName : "current file";
        return SYSTEM_PROMPT + "\n\nOpen file (" + name + "):\n```\n"
                + truncate(fileContent, 1200) + "\n```";
    }

    /** Keep only the most recent messages to stay within free-tier TPM limits. */
    private static List<Map<String, Object>> trimHistory(List<Map<String, Object>> messages) {
        if (messages == null) return messages;
        // Always keep the last 8 turns (pairs) maximum
        if (messages.size() <= 8) return messages;
        return messages.subList(messages.size() - 8, messages.size());
    }

    private static String truncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max) + "\n… (truncated)";
    }
}

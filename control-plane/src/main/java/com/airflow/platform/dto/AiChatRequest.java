package com.airflow.platform.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiChatRequest {

    /**
     * Full conversation history in Anthropic message format.
     * Each entry is {@code {role: "user"|"assistant", content: string|array}}.
     * Keeping it as raw maps lets the frontend pass tool_use / tool_result
     * content blocks without extra DTO mapping.
     */
    private List<Map<String, Object>> messages;

    /** Content of the file currently open in the editor (may be null). */
    private String fileContent;

    /** File name / path for context (e.g. "dags/my_dag.py"). */
    private String fileName;

    /**
     * Optional API key supplied by the user from the browser.
     * When present it overrides the server-configured key.
     */
    private String userApiKey;

    /**
     * Provider chosen by the user: "anthropic", "groq", "ollama", "openai".
     * When present it overrides the server-configured provider.
     */
    private String userProvider;

    /** Optional model override (e.g. "llama-3.3-70b-versatile", "llama3.2"). */
    private String userModel;

    /** When true, the server includes the project tool definitions in the request. */
    private boolean useTools;
}

package com.airflow.platform.controller;

import com.airflow.platform.dto.AiChatRequest;
import com.airflow.platform.dto.AiChatResponse;
import com.airflow.platform.service.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Assistant", description = "Airflow DAG coding assistant (Groq, Anthropic, Ollama, OpenAI)")
public class AiChatController {

    private final AiChatService aiChatService;

    @Value("${ai.anthropic.api-key:}")
    private String anthropicServerKey;

    @Value("${ai.openai-compatible.api-key:}")
    private String openAiCompatibleServerKey;

    @PostMapping("/chat")
    @Operation(summary = "Send a chat turn to the AI coding assistant")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        return ResponseEntity.ok(aiChatService.chat(request));
    }

    @GetMapping("/status")
    @Operation(summary = "Check whether the AI assistant is available")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "enabled", true,
                "serverKeyConfigured",
                StringUtils.hasText(anthropicServerKey) || StringUtils.hasText(openAiCompatibleServerKey)
        ));
    }
}

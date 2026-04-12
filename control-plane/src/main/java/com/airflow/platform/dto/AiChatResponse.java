package com.airflow.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {

    /** Final text reply from the assistant (null when stop_reason is tool_use). */
    private String reply;

    /** "end_turn" or "tool_use". */
    private String stopReason;

    /**
     * Non-empty when stop_reason == "tool_use".
     * Each entry: {id, name, input (map)}.
     */
    private List<ToolCall> toolCalls;

    /**
     * The full assistant content array as returned by Anthropic.
     * The frontend must append this as an assistant message to the history
     * before sending tool results, so Claude keeps context correctly.
     */
    private List<Map<String, Object>> assistantContent;

    /** Non-null when an error occurred. */
    private String error;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String name;
        private Map<String, Object> input;
    }

    public static AiChatResponse ok(String reply, List<Map<String, Object>> assistantContent) {
        return AiChatResponse.builder()
                .reply(reply)
                .stopReason("end_turn")
                .assistantContent(assistantContent)
                .build();
    }

    public static AiChatResponse toolUse(List<ToolCall> toolCalls, List<Map<String, Object>> assistantContent) {
        return AiChatResponse.builder()
                .stopReason("tool_use")
                .toolCalls(toolCalls)
                .assistantContent(assistantContent)
                .build();
    }

    public static AiChatResponse error(String error) {
        return AiChatResponse.builder().error(error).build();
    }
}

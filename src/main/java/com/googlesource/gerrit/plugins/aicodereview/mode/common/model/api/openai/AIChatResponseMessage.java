package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class AIChatResponseMessage {
    private String role;
    private String type;
    @SerializedName("tool_calls")
    private List<AIChatToolCall> toolCalls;
    @SerializedName("message_creation")
    private MessageCreation messageCreation;

    @Data
    public static class MessageCreation {
        @SerializedName("message_id")
        private String messageId;
    }
}

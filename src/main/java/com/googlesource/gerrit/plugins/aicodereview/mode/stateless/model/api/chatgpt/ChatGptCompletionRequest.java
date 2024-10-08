package com.googlesource.gerrit.plugins.aicodereview.mode.stateless.model.api.chatgpt;

import com.google.gson.annotations.SerializedName;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatRequestMessage;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatTool;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatToolChoice;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatGptCompletionRequest {
    private String model;
    private boolean stream;
    private double temperature;
    private int seed;
    private List<AIChatRequestMessage> messages;
    private AIChatTool[] tools;
    @SerializedName("tool_choice")
    private AIChatToolChoice toolChoice;
}

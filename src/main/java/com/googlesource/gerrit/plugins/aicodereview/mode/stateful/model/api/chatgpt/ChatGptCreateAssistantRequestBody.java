package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt;

import com.google.gson.annotations.SerializedName;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatTool;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatGptCreateAssistantRequestBody {
    private String name;
    private String description;
    private String instructions;
    private String model;
    private Double temperature;
    private AIChatTool[] tools;
    @SerializedName("tool_resources")
    private ChatGptToolResources toolResources;
}

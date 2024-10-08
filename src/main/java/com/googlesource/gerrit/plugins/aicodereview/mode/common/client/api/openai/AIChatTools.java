package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai;

import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatTool;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatToolChoice;
import com.googlesource.gerrit.plugins.aicodereview.utils.FileUtils;

import java.io.IOException;
import java.io.InputStreamReader;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

public class AIChatTools {
    public static AIChatTool retrieveFormatRepliesTool() {
        AIChatTool tools;
        try (InputStreamReader reader = FileUtils.getInputStreamReader("config/formatRepliesTool.json")) {
            tools = getGson().fromJson(reader, AIChatTool.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load data for ChatGPT `format_replies` tool", e);
        }
        return tools;
    }

    public static AIChatToolChoice retrieveFormatRepliesToolChoice() {
        AIChatToolChoice toolChoice;
        try (InputStreamReader reader = FileUtils.getInputStreamReader("config/formatRepliesToolChoice.json")) {
            toolChoice = getGson().fromJson(reader, AIChatToolChoice.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load data for ChatGPT `format_replies` tool choice", e);
        }
        return toolChoice;
    }
}

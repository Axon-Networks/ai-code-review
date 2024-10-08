package com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.prompt;

import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatMessageItem;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritComment;

import java.util.List;

public interface ChatAIDataPrompt {
    void addMessageItem(int i);
    List<GerritComment> getCommentProperties();
    List<AIChatMessageItem> getMessageItems();
}

package com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.stateful.client.prompt;

import java.util.List;

public interface ChatGptPromptStateful {
    void addGptAssistantInstructions(List<String> instructions);
    String getDefaultGptAssistantDescription();
    String getDefaultGptAssistantInstructions();
    String getDefaultGptThreadReviewMessage(String patchSet);
    String getAIRequestDataPrompt();
    void setCommentEvent(boolean isCommentEvent);
}

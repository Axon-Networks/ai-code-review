package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.prompt;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.stateful.client.prompt.ChatGptPromptStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AIChatGptPromptStatefulRequests extends AIChatGptPromptStatefulBase
    implements ChatGptPromptStateful {
  public static String DEFAULT_AI_CHAT_ASSISTANT_INSTRUCTIONS_REQUESTS;

  public AIChatGptPromptStatefulRequests(
      Configuration config, ChangeSetData changeSetData, GerritChange change) {
    super(config, changeSetData, change);
    if (DEFAULT_AI_CHAT_ASSISTANT_INSTRUCTIONS_REQUESTS == null) {
      loadDefaultPrompts("promptsStatefulRequests");
    }
  }

  @Override
  public void addGptAssistantInstructions(List<String> instructions) {
    instructions.addAll(
        List.of(
            DEFAULT_AI_CHAT_ASSISTANT_INSTRUCTIONS_REQUESTS,
            getCommentRequestPrompt(changeSetData.getCommentPropertiesSize())));
  }

  @Override
  public String getAIRequestDataPrompt() {
    if (changeSetData == null) return null;
    return changeSetData.getReviewAIDataPrompt();
  }
}

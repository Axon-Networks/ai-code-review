package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.prompt;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.prompt.ChatAIDataPrompt;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatMessageItem;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.GerritClientData;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AIChatDataPrompt {
  private final ChatAIDataPrompt aiChatDataPromptHandler;

  public AIChatDataPrompt(
      Configuration config,
      ChangeSetData changeSetData,
      GerritChange change,
      GerritClientData gerritClientData,
      Localizer localizer) {
    aiChatDataPromptHandler =
        AIChatPromptFactory.getChatGptDataPrompt(
            config, changeSetData, change, gerritClientData, localizer);
  }

  public String buildPrompt() {
    for (int i = 0; i < aiChatDataPromptHandler.getCommentProperties().size(); i++) {
      aiChatDataPromptHandler.addMessageItem(i);
    }
    List<AIChatMessageItem> messageItems = aiChatDataPromptHandler.getMessageItems();
    return messageItems.isEmpty() ? "" : getGson().toJson(messageItems);
  }
}

package com.googlesource.gerrit.plugins.aicodereview.mode.stateless.client.prompt;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.prompt.ChatAIDataPrompt;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.prompt.AIChatDataPromptRequests;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatMessageItem;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.GerritClientData;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AIChatDataPromptRequestsStateless extends AIChatDataPromptRequests
    implements ChatAIDataPrompt {
  public AIChatDataPromptRequestsStateless(
      Configuration config,
      ChangeSetData changeSetData,
      GerritClientData gerritClientData,
      Localizer localizer) {
    super(config, changeSetData, gerritClientData, localizer);
  }

  protected AIChatMessageItem getMessageItem(int i) {
    super.getMessageItem(i);
    setHistory(messageItem, messageHistory);

    return messageItem;
  }
}

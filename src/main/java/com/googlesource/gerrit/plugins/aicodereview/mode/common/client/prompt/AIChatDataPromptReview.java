// Copyright (C) 2024 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.prompt;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.prompt.ChatAIDataPrompt;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatMessageItem;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatRequestMessage;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.GerritClientData;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AIChatDataPromptReview extends AIChatDataPromptBase implements ChatAIDataPrompt {
  public AIChatDataPromptReview(
      Configuration config,
      ChangeSetData changeSetData,
      GerritClientData gerritClientData,
      Localizer localizer) {
    super(config, changeSetData, gerritClientData, localizer);
    commentProperties = new ArrayList<>(commentData.getCommentMap().values());
  }

  public void addMessageItem(int i) {
    AIChatMessageItem messageItem = getMessageItem(i);
    if (messageItem.getHistory() != null) {
      messageItems.add(messageItem);
    }
  }

  protected AIChatMessageItem getMessageItem(int i) {
    AIChatMessageItem messageItem = super.getMessageItem(i);
    List<AIChatRequestMessage> messageHistory =
        aiChatHistory.retrieveHistory(commentProperties.get(i), true);
    setHistory(messageItem, messageHistory);

    return messageItem;
  }
}

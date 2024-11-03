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
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.patch.code.InlineCode;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.patch.diff.FileDiffProcessed;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatMessageItem;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatRequestMessage;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.CommentData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.GerritClientData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AIChatDataPromptBase implements ChatAIDataPrompt {
  protected final GerritClientData gerritClientData;
  protected final HashMap<String, FileDiffProcessed> fileDiffsProcessed;
  protected final CommentData commentData;
  @Getter protected final List<AIChatMessageItem> messageItems;

  protected AIChatHistory aiChatHistory;
  @Getter protected List<GerritComment> commentProperties;

  public AIChatDataPromptBase(
      Configuration config,
      ChangeSetData changeSetData,
      GerritClientData gerritClientData,
      Localizer localizer) {
    this.gerritClientData = gerritClientData;
    fileDiffsProcessed = gerritClientData.getFileDiffsProcessed();
    commentData = gerritClientData.getCommentData();
    aiChatHistory = new AIChatHistory(config, changeSetData, gerritClientData, localizer);
    messageItems = new ArrayList<>();
  }

  public abstract void addMessageItem(int i);

  protected AIChatMessageItem getMessageItem(int i) {
    AIChatMessageItem messageItem = new AIChatMessageItem();
    GerritComment commentProperty = commentProperties.get(i);
    if (commentProperty.getLine() != null || commentProperty.getRange() != null) {
      String filename = commentProperty.getFilename();
      FileDiffProcessed fileDiffProcessed = fileDiffsProcessed.get(filename);
      if (fileDiffProcessed == null) {
        return messageItem;
      }
      InlineCode inlineCode = new InlineCode(fileDiffProcessed);
      messageItem.setFilename(filename);
      messageItem.setLineNumber(commentProperty.getLine());
      messageItem.setCodeSnippet(inlineCode.getInlineCode(commentProperty));
    }

    return messageItem;
  }

  protected void setHistory(
      AIChatMessageItem messageItem, List<AIChatRequestMessage> messageHistory) {
    if (!messageHistory.isEmpty()) {
      messageItem.setHistory(messageHistory);
    }
  }
}

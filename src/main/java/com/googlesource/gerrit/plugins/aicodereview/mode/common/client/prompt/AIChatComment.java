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
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.ClientBase;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.messages.ClientMessage;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AIChatComment extends ClientBase {
  protected ClientMessage commentMessage;

  private final ChangeSetData changeSetData;
  private final Localizer localizer;

  public AIChatComment(Configuration config, ChangeSetData changeSetData, Localizer localizer) {
    super(config);
    this.changeSetData = changeSetData;
    this.localizer = localizer;
  }

  protected String getCleanedMessage(GerritComment commentProperty) {
    commentMessage =
        new ClientMessage(config, changeSetData, commentProperty.getMessage(), localizer);
    if (isFromAssistant(commentProperty)) {
      commentMessage.removeDebugCodeBlocksReview().removeDebugCodeBlocksDynamicSettings();
    } else {
      commentMessage.removeMentions().parseRemoveCommands();
    }
    return commentMessage.removeHeadings().getMessage();
  }

  protected boolean isFromAssistant(GerritComment commentProperty) {
    return commentProperty.getAuthor().getAccountId() == changeSetData.getGptAccountId();
  }
}

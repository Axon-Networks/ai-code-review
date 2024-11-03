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
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatRequestMessage;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.CommentData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.GerritClientData;
import com.googlesource.gerrit.plugins.aicodereview.settings.Settings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AIChatHistory extends AIChatComment {
  private final Set<String> messagesExcludedFromHistory;
  private final HashMap<String, GerritComment> commentMap;
  private final HashMap<String, GerritComment> patchSetCommentMap;
  private final Set<String> patchSetCommentAdded;
  private final List<GerritComment> patchSetComments;
  private final int revisionBase;

  private boolean filterActive;

  public AIChatHistory(
      Configuration config,
      ChangeSetData changeSetData,
      GerritClientData gerritClientData,
      Localizer localizer) {
    super(config, changeSetData, localizer);
    CommentData commentData = gerritClientData.getCommentData();
    messagesExcludedFromHistory =
        Set.of(Settings.GERRIT_DEFAULT_MESSAGE_DONE, localizer.getText("message.empty.review"));
    commentMap = commentData.getCommentMap();
    patchSetCommentMap = commentData.getPatchSetCommentMap();
    patchSetComments = retrievePatchSetComments(gerritClientData);
    revisionBase = gerritClientData.getOneBasedRevisionBase();
    patchSetCommentAdded = new HashSet<>();
  }

  public List<AIChatRequestMessage> retrieveHistory(
      GerritComment commentProperty, boolean filterActive) {
    this.filterActive = filterActive;
    if (commentProperty.isPatchSetComment()) {
      return retrievePatchSetMessageHistory();
    } else {
      return retrieveMessageHistory(commentProperty);
    }
  }

  public List<AIChatRequestMessage> retrieveHistory(GerritComment commentProperty) {
    return retrieveHistory(commentProperty, false);
  }

  private List<GerritComment> retrievePatchSetComments(GerritClientData gerritClientData) {
    List<GerritComment> detailComments = gerritClientData.getDetailComments();
    // Normalize detailComments by setting the `update` field to match `date`
    detailComments.forEach(record -> record.setUpdated(record.getDate()));
    // Join the comments from patchSetCommentMap with detailComments
    List<GerritComment> patchSetComments =
        Stream.concat(patchSetCommentMap.values().stream(), detailComments.stream())
            .collect(Collectors.toList());
    sortPatchSetComments(patchSetComments);
    log.debug("Patch Set Comments sorted by `update` datetime: {}", patchSetComments);

    return patchSetComments;
  }

  private void sortPatchSetComments(List<GerritComment> patchSetComments) {
    Comparator<GerritComment> byDateUpdated =
        (GerritComment o1, GerritComment o2) -> {
          String dateTime1 = o1.getUpdated();
          String dateTime2 = o2.getUpdated();
          if (dateTime1 == null && dateTime2 == null) return 0;
          if (dateTime1 == null) return 1;
          if (dateTime2 == null) return -1;

          return dateTime1.compareTo(dateTime2);
        };
    patchSetComments.sort(byDateUpdated);
  }

  private String getRoleFromComment(GerritComment currentComment) {
    return isFromAssistant(currentComment)
        ? Settings.OPEN_AI_CHAT_ROLE_ASSISTANT
        : Settings.OPEN_AI_CHAT_ROLE_USER;
  }

  private List<AIChatRequestMessage> retrieveMessageHistory(GerritComment currentComment) {
    List<AIChatRequestMessage> messageHistory = new ArrayList<>();
    while (currentComment != null) {
      addMessageToHistory(messageHistory, currentComment);
      currentComment = commentMap.get(currentComment.getInReplyTo());
    }
    // Reverse the history sequence so that the oldest message appears first and the newest message
    // is last
    Collections.reverse(messageHistory);

    return messageHistory;
  }

  private List<AIChatRequestMessage> retrievePatchSetMessageHistory() {
    List<AIChatRequestMessage> messageHistory = new ArrayList<>();
    for (GerritComment patchSetComment : patchSetComments) {
      if (patchSetComment.isAutogenerated()) {
        continue;
      }
      if (!isFromAssistant(patchSetComment)) {
        GerritComment patchSetLevelMessage = patchSetCommentMap.get(patchSetComment.getId());
        if (patchSetLevelMessage != null) {
          patchSetComment = patchSetLevelMessage;
        }
      }
      addMessageToHistory(messageHistory, patchSetComment);
    }
    return messageHistory;
  }

  private boolean isInactiveComment(GerritComment comment) {
    return config.getIgnoreResolvedAIChatComments()
            && isFromAssistant(comment)
            && comment.isResolved()
        || config.getIgnoreOutdatedInlineComments()
            && comment.getOneBasedPatchSet() != revisionBase
            && !comment.isPatchSetComment();
  }

  private void addMessageToHistory(
      List<AIChatRequestMessage> messageHistory, GerritComment comment) {
    String messageContent = getCleanedMessage(comment);
    boolean shouldNotProcessComment =
        messageContent.isEmpty()
            || messagesExcludedFromHistory.contains(messageContent)
            || patchSetCommentAdded.contains(messageContent)
            || filterActive && isInactiveComment(comment);

    if (shouldNotProcessComment && !commentMessage.isContainingHistoryCommand()) {
      return;
    }
    patchSetCommentAdded.add(messageContent);
    if (commentMessage.isContainingHistoryCommand()) {
      commentMessage.processHistoryCommand();
      return;
    }
    AIChatRequestMessage message =
        AIChatRequestMessage.builder()
            .role(getRoleFromComment(comment))
            .content(messageContent)
            .build();
    messageHistory.add(message);
  }
}

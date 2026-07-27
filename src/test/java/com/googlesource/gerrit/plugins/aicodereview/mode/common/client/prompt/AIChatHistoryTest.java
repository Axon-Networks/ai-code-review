// Copyright (C) 2026 The Android Open Source Project
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

import static com.googlesource.gerrit.plugins.aicodereview.settings.Settings.GERRIT_PATCH_SET_FILENAME;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.patch.diff.FileDiffProcessed;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatRequestMessage;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.CommentData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.GerritClientData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AIChatHistoryTest {
  private static final int BOT_ACCOUNT_ID = 7;
  private static final int HUMAN_ACCOUNT_ID = 8;
  private static final String INLINE_FILE = "src/Example.java";

  @Mock private Configuration config;
  @Mock private Localizer localizer;

  private ChangeSetData changeSetData;

  @Before
  public void setUp() {
    changeSetData = new ChangeSetData(BOT_ACCOUNT_ID, -1, 1);
    when(config.getGerritUserName()).thenReturn("review-bot");
    when(config.getGerritUserEmail()).thenReturn("review-bot@example.com");
    when(config.getIgnoreResolvedAIChatComments()).thenReturn(false);
    when(config.getIgnoreOutdatedInlineComments()).thenReturn(false);
    when(localizer.getText("message.empty.review")).thenReturn("No update");
  }

  @Test
  public void inlineHistoryIsReturnedOldestFirst() {
    GerritComment root = comment("root", null, HUMAN_ACCOUNT_ID, "@review-bot explain", 1, true);
    GerritComment answer = comment("answer", "root", BOT_ACCOUNT_ID, "Initial answer", 1, true);
    GerritComment feedback =
        comment("feedback", "answer", HUMAN_ACCOUNT_ID, "@review-bot reconsider", 1, true);

    List<AIChatRequestMessage> history = history(root, answer, feedback).retrieveHistory(feedback);

    assertEquals(List.of("explain", "Initial answer", "reconsider"), contents(history));
  }

  @Test
  public void humanCommentsUseUserRole() {
    GerritComment root = comment("root", null, HUMAN_ACCOUNT_ID, "@review-bot explain", 1, true);

    List<AIChatRequestMessage> history = history(root).retrieveHistory(root);

    assertEquals("user", history.get(0).getRole());
  }

  @Test
  public void botCommentsUseAssistantRole() {
    GerritComment root = comment("root", null, HUMAN_ACCOUNT_ID, "@review-bot explain", 1, true);
    GerritComment answer = comment("answer", "root", BOT_ACCOUNT_ID, "Initial answer", 1, true);

    List<AIChatRequestMessage> history = history(root, answer).retrieveHistory(answer);

    assertEquals("assistant", history.get(1).getRole());
  }

  @Test
  public void inlineHistoryDoesNotIncludeAnotherThread() {
    GerritComment root = comment("root", null, HUMAN_ACCOUNT_ID, "@review-bot explain", 1, true);
    GerritComment answer = comment("answer", "root", BOT_ACCOUNT_ID, "Initial answer", 1, true);
    GerritComment unrelated =
        comment("unrelated", null, HUMAN_ACCOUNT_ID, "@review-bot other question", 1, true);

    List<AIChatRequestMessage> history = history(root, answer, unrelated).retrieveHistory(answer);

    assertEquals(List.of("explain", "Initial answer"), contents(history));
  }

  @Test
  public void resolvedBotCommentIsExcludedFromFilteredReviewHistory() {
    when(config.getIgnoreResolvedAIChatComments()).thenReturn(true);
    GerritComment root = comment("root", null, HUMAN_ACCOUNT_ID, "@review-bot explain", 1, true);
    GerritComment answer = comment("answer", "root", BOT_ACCOUNT_ID, "Initial answer", 1, false);
    GerritComment feedback =
        comment("feedback", "answer", HUMAN_ACCOUNT_ID, "@review-bot reconsider", 1, true);

    List<AIChatRequestMessage> history =
        history(root, answer, feedback).retrieveHistory(feedback, true);

    assertEquals(List.of("explain", "reconsider"), contents(history));
  }

  @Test
  public void resolvedHumanCommentRemainsInFilteredReviewHistory() {
    when(config.getIgnoreResolvedAIChatComments()).thenReturn(true);
    GerritComment root = comment("root", null, HUMAN_ACCOUNT_ID, "@review-bot explain", 1, false);

    List<AIChatRequestMessage> history = history(root).retrieveHistory(root, true);

    assertEquals(List.of("explain"), contents(history));
  }

  @Test
  public void resolvedBotCommentRemainsInUnfilteredConversationHistory() {
    GerritComment root = comment("root", null, HUMAN_ACCOUNT_ID, "@review-bot explain", 1, true);
    GerritComment answer = comment("answer", "root", BOT_ACCOUNT_ID, "Initial answer", 1, false);

    List<AIChatRequestMessage> history = history(root, answer).retrieveHistory(answer, false);

    assertEquals(List.of("explain", "Initial answer"), contents(history));
  }

  @Test
  public void outdatedInlineCommentIsExcludedWhenConfigured() {
    when(config.getIgnoreOutdatedInlineComments()).thenReturn(true);
    GerritComment oldComment =
        comment("old", null, HUMAN_ACCOUNT_ID, "@review-bot old feedback", 1, true);

    List<AIChatRequestMessage> history =
        historyAtRevision(2, oldComment).retrieveHistory(oldComment, true);

    assertEquals(List.of(), contents(history));
  }

  @Test
  public void outdatedInlineCommentRemainsWhenFilteringIsDisabled() {
    GerritComment oldComment =
        comment("old", null, HUMAN_ACCOUNT_ID, "@review-bot old feedback", 1, true);

    List<AIChatRequestMessage> history =
        historyAtRevision(2, oldComment).retrieveHistory(oldComment, false);

    assertEquals(List.of("old feedback"), contents(history));
  }

  @Test
  public void patchSetHistoryIsSortedChronologically() {
    GerritComment later =
        patchSetComment("later", HUMAN_ACCOUNT_ID, "@review-bot second", "2026-01-02 00:00:00");
    GerritComment earlier =
        patchSetComment("earlier", BOT_ACCOUNT_ID, "first", "2026-01-01 00:00:00");

    List<AIChatRequestMessage> history =
        patchSetHistory(later, earlier).retrieveHistory(later, true);

    assertEquals(List.of("first", "second"), contents(history));
  }

  @Test
  public void autogeneratedPatchSetMessageIsExcluded() {
    GerritComment request =
        patchSetComment("request", HUMAN_ACCOUNT_ID, "@review-bot explain", "2026-01-02 00:00:00");
    GerritComment autogenerated =
        detailComment(
            "upload",
            HUMAN_ACCOUNT_ID,
            "Uploaded patch set 2.",
            "2026-01-01 00:00:00",
            "autogenerated:gerrit:newPatchSet");

    List<AIChatRequestMessage> history =
        patchSetHistory(List.of(autogenerated), request).retrieveHistory(request, true);

    assertEquals(List.of("explain"), contents(history));
  }

  private AIChatHistory history(GerritComment... comments) {
    return historyAtRevision(1, comments);
  }

  private AIChatHistory historyAtRevision(int oneBasedRevision, GerritComment... comments) {
    HashMap<String, GerritComment> commentMap = new HashMap<>();
    for (GerritComment comment : comments) {
      commentMap.put(comment.getId(), comment);
    }
    return new AIChatHistory(
        config,
        changeSetData,
        clientData(new ArrayList<>(), commentMap, new HashMap<>(), oneBasedRevision - 1),
        localizer);
  }

  private AIChatHistory patchSetHistory(GerritComment... comments) {
    return patchSetHistory(List.of(), comments);
  }

  private AIChatHistory patchSetHistory(
      List<GerritComment> detailComments, GerritComment... comments) {
    HashMap<String, GerritComment> patchSetMap = new HashMap<>();
    for (GerritComment comment : comments) {
      patchSetMap.put(comment.getId(), comment);
    }
    return new AIChatHistory(
        config,
        changeSetData,
        clientData(detailComments, new HashMap<>(), patchSetMap, 0),
        localizer);
  }

  private GerritClientData clientData(
      List<GerritComment> detailComments,
      HashMap<String, GerritComment> commentMap,
      HashMap<String, GerritComment> patchSetMap,
      int revisionBase) {
    return new GerritClientData(
        new HashMap<String, FileDiffProcessed>(),
        detailComments,
        new CommentData(new ArrayList<>(commentMap.values()), commentMap, patchSetMap),
        revisionBase);
  }

  private GerritComment comment(
      String id,
      String inReplyTo,
      int accountId,
      String message,
      int patchSet,
      boolean unresolved) {
    GerritComment comment = baseComment(id, accountId, message, patchSet, unresolved);
    comment.setFilename(INLINE_FILE);
    comment.setInReplyTo(inReplyTo);
    return comment;
  }

  private GerritComment patchSetComment(String id, int accountId, String message, String updated) {
    GerritComment comment = baseComment(id, accountId, message, 1, true);
    comment.setFilename(GERRIT_PATCH_SET_FILENAME);
    comment.setUpdated(updated);
    return comment;
  }

  private GerritComment detailComment(
      String id, int accountId, String message, String date, String tag) {
    GerritComment comment = baseComment(id, accountId, message, 1, true);
    comment.setDate(date);
    comment.setTag(tag);
    return comment;
  }

  private GerritComment baseComment(
      String id, int accountId, String message, int patchSet, boolean unresolved) {
    GerritComment.Author author = new GerritComment.Author();
    author.setAccountId(accountId);
    author.setUsername(accountId == BOT_ACCOUNT_ID ? "review-bot" : "human");

    GerritComment comment = new GerritComment();
    comment.setId(id);
    comment.setAuthor(author);
    comment.setMessage(message);
    comment.setPatchSet(patchSet);
    comment.setUnresolved(unresolved);
    return comment;
  }

  private List<String> contents(List<AIChatRequestMessage> history) {
    return history.stream().map(AIChatRequestMessage::getContent).toList();
  }
}

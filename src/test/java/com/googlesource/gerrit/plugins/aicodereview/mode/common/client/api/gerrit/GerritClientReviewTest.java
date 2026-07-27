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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit;

import static com.googlesource.gerrit.plugins.aicodereview.settings.Settings.GERRIT_PATCH_SET_FILENAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.util.ManualRequestContext;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandler;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.review.ReviewBatch;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GerritClientReviewTest {
  private static final Project.NameKey PROJECT = Project.nameKey("project");
  private static final BranchNameKey BRANCH = BranchNameKey.create(PROJECT, "main");
  private static final Change.Key CHANGE = Change.key("I123");

  @Mock private Configuration config;
  @Mock private AccountCache accountCache;
  @Mock private PluginDataHandlerProvider pluginDataHandlerProvider;
  @Mock private PluginDataHandler pluginDataHandler;
  @Mock private Localizer localizer;
  @Mock private ManualRequestContext requestContext;
  @Mock private GerritApi gerritApi;
  @Mock private Changes changes;
  @Mock private ChangeApi changeApi;
  @Mock private RevisionApi revisionApi;
  @Mock private ReviewResult reviewResult;

  private GerritClientReview client;
  private GerritChange change;
  private ChangeSetData changeSetData;

  @Before
  public void setUp() throws Exception {
    change = new GerritChange(PROJECT, BRANCH, CHANGE);
    changeSetData = new ChangeSetData(7, -1, 1);
    client = new GerritClientReview(config, accountCache, pluginDataHandlerProvider, localizer);

    when(pluginDataHandlerProvider.getChangeScope()).thenReturn(pluginDataHandler);
    when(pluginDataHandler.getJsonValue("dynamicConfig", String.class)).thenReturn(null);
    when(localizer.getText("message.empty.review")).thenReturn("No update");
    when(localizer.getText("system.message.prefix")).thenReturn("SYSTEM MESSAGE:");
    when(config.getPatchSetCommentsAsResolved()).thenReturn(false);
    when(config.getInlineCommentsAsResolved()).thenReturn(false);
    when(config.openRequestContext()).thenReturn(requestContext);
    when(config.getGerritApi()).thenReturn(gerritApi);
    when(gerritApi.changes()).thenReturn(changes);
    when(changes.id(PROJECT.get(), BRANCH.shortName(), CHANGE.get())).thenReturn(changeApi);
    when(changeApi.revision("current")).thenReturn(revisionApi);
    when(revisionApi.review(any())).thenReturn(reviewResult);
  }

  @Test
  public void suppliedScoreIsPostedAsCodeReviewLabel() throws Exception {
    ReviewInput review = submit(List.of(new ReviewBatch("Finding")), -1);

    assertEquals(Short.valueOf((short) -1), review.labels.get("Code-Review"));
  }

  @Test
  public void absentScoreDoesNotPostCodeReviewLabel() throws Exception {
    ReviewInput review = submit(List.of(new ReviewBatch("Conversation reply")), null);

    assertNull(review.labels);
  }

  @Test
  public void inlineReplyTargetsParentComment() throws Exception {
    ReviewBatch batch = new ReviewBatch("Reconsidered finding");
    batch.setId("parent-comment");
    batch.setFilename("src/Example.java");
    batch.setLine(12);

    ReviewInput review = submit(List.of(batch), null);

    assertEquals("parent-comment", onlyComment(review, "src/Example.java").inReplyTo);
  }

  @Test
  public void inlineReplyPreservesLineNumber() throws Exception {
    ReviewBatch batch = new ReviewBatch("Reconsidered finding");
    batch.setFilename("src/Example.java");
    batch.setLine(12);

    ReviewInput review = submit(List.of(batch), null);

    assertEquals(Integer.valueOf(12), onlyComment(review, "src/Example.java").line);
  }

  @Test
  public void patchSetFindingIsUnresolvedByDefault() throws Exception {
    ReviewInput review = submit(List.of(new ReviewBatch("Finding")), null);

    assertTrue(onlyComment(review, GERRIT_PATCH_SET_FILENAME).unresolved);
  }

  @Test
  public void patchSetFindingCanDefaultToResolved() throws Exception {
    when(config.getPatchSetCommentsAsResolved()).thenReturn(true);

    ReviewInput review = submit(List.of(new ReviewBatch("Finding")), null);

    assertFalse(onlyComment(review, GERRIT_PATCH_SET_FILENAME).unresolved);
  }

  @Test
  public void inlineFindingCanDefaultToResolved() throws Exception {
    when(config.getInlineCommentsAsResolved()).thenReturn(true);
    ReviewBatch batch = new ReviewBatch("Finding");
    batch.setFilename("src/Example.java");
    batch.setLine(12);

    ReviewInput review = submit(List.of(batch), null);

    assertFalse(onlyComment(review, "src/Example.java").unresolved);
  }

  @Test
  public void explicitUnresolvedStateOverridesConfiguredDefault() throws Exception {
    when(config.getPatchSetCommentsAsResolved()).thenReturn(true);
    ReviewBatch batch = new ReviewBatch("Finding");
    batch.setUnresolved(true);

    ReviewInput review = submit(List.of(batch), null);

    assertTrue(onlyComment(review, GERRIT_PATCH_SET_FILENAME).unresolved);
  }

  @Test
  public void emptyReviewPostsLocalizedSystemMessage() throws Exception {
    ReviewInput review = submit(List.of(), null);

    assertEquals("SYSTEM MESSAGE: No update", review.message);
  }

  @Test
  public void customReviewSystemMessageIsPostedWithoutComments() throws Exception {
    changeSetData.setReviewSystemMessage("Waiting for finalization");

    ReviewInput review = submit(List.of(new ReviewBatch("Hidden finding")), -1);

    assertEquals("SYSTEM MESSAGE: Waiting for finalization", review.message);
    assertNull(review.comments);
    assertNull(review.labels);
  }

  private ReviewInput submit(List<ReviewBatch> batches, Integer score) throws Exception {
    client.setReview(change, batches, changeSetData, score);
    ArgumentCaptor<ReviewInput> captor = ArgumentCaptor.forClass(ReviewInput.class);
    verify(revisionApi).review(captor.capture());
    return captor.getValue();
  }

  private CommentInput onlyComment(ReviewInput review, String filename) {
    assertEquals(1, review.comments.get(filename).size());
    return review.comments.get(filename).get(0);
  }
}

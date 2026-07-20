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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit;

import static com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritClientDetail.toAuthor;
import static com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritClientDetail.toDateString;
import static com.googlesource.gerrit.plugins.aicodereview.settings.Settings.GERRIT_PATCH_SET_FILENAME;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TimeUtils.getTimeStamp;
import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.messages.ClientMessage;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritCodeRange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.CommentData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GerritClientComments extends GerritClientAccount {
  private static final Integer MAX_SECS_GAP_BETWEEN_EVENT_AND_COMMENT = 2;

  private final ChangeSetData changeSetData;
  private final HashMap<String, GerritComment> commentMap;
  private final HashMap<String, GerritComment> patchSetCommentMap;
  private final PluginDataHandlerProvider pluginDataHandlerProvider;
  private final Localizer localizer;

  private String authorUsername;
  @Getter private List<GerritComment> commentProperties;

  @VisibleForTesting
  @Inject
  public GerritClientComments(
      Configuration config,
      AccountCache accountCache,
      ChangeSetData changeSetData,
      PluginDataHandlerProvider pluginDataHandlerProvider,
      Localizer localizer) {
    super(config, accountCache);
    this.changeSetData = changeSetData;
    this.pluginDataHandlerProvider = pluginDataHandlerProvider;
    this.localizer = localizer;
    commentProperties = new ArrayList<>();
    commentMap = new HashMap<>();
    patchSetCommentMap = new HashMap<>();
  }

  public CommentData getCommentData() {
    return new CommentData(commentProperties, commentMap, patchSetCommentMap);
  }

  public boolean retrieveLastComments(GerritChange change) {
    commentProperties.clear();
    CommentAddedEvent commentAddedEvent = (CommentAddedEvent) change.getEvent();
    authorUsername = commentAddedEvent.author.get().username;
    log.debug("Found comments by '{}' on {}", authorUsername, change.getEventTimeStamp());
    if (authorUsername.equals(config.getGerritUserName())) {
      log.debug("These are the Bot's own comments, do not process them.");
      return false;
    }
    if (isDisabledUser(authorUsername)) {
      log.info("Review of comments from user '{}' is disabled.", authorUsername);
      return false;
    }
    addLastComments(change);

    return !commentProperties.isEmpty();
  }

  public void retrieveAllComments(GerritChange change) {
    try {
      retrieveComments(change);
    } catch (Exception e) {
      log.error("Error while retrieving all comments for change: {}", change.getFullChangeId(), e);
    }
  }

  public List<GerritComment> getOpenBotThreadTipsBefore(GerritChange change) {
    Optional<Integer> currentPatchSet = change.getPatchSetNumber();
    if (!change.isPatchSetCreatedEvent() || currentPatchSet.isEmpty()) {
      return List.of();
    }

    List<GerritComment> portedComments;
    try {
      portedComments = retrievePortedComments(change);
    } catch (Exception e) {
      log.error(
          "Error while retrieving ported comments for change: {}", change.getFullChangeId(), e);
      return List.of();
    }

    Map<String, GerritComment> portedCommentMap = new HashMap<>();
    portedComments.forEach(
        comment -> {
          if (comment.getId() != null) {
            portedCommentMap.put(comment.getId(), comment);
          }
        });
    Map<String, List<GerritComment>> threads = new HashMap<>();
    for (GerritComment comment : portedComments) {
      if (comment.getId() == null) {
        continue;
      }
      GerritComment root = findThreadRoot(comment, portedCommentMap);
      threads.computeIfAbsent(root.getId(), unused -> new ArrayList<>()).add(comment);
    }

    Comparator<GerritComment> byUpdateAndId =
        Comparator.comparing(
                (GerritComment comment) -> Optional.ofNullable(comment.getUpdated()).orElse(""))
            .thenComparing(comment -> Optional.ofNullable(comment.getId()).orElse(""));
    return threads.values().stream()
        .filter(thread -> isPriorBotThread(thread, currentPatchSet.get()))
        .map(thread -> thread.stream().max(byUpdateAndId).orElseThrow())
        .filter(comment -> Boolean.TRUE.equals(comment.getUnresolved()))
        .sorted(
            Comparator.comparing(
                    (GerritComment comment) ->
                        Optional.ofNullable(comment.getFilename()).orElse(""))
                .thenComparing(comment -> comment.getId()))
        .collect(toList());
  }

  private List<GerritComment> retrievePortedComments(GerritChange change) throws Exception {
    try (ManualRequestContext requestContext = config.openRequestContext()) {
      Map<String, List<CommentInfo>> comments =
          config
              .getGerritApi()
              .changes()
              .id(
                  change.getProjectName(),
                  change.getBranchNameKey().shortName(),
                  change.getChangeKey().get())
              .revision(change.getRevisionId())
              .portedComments();
      List<GerritComment> result = new ArrayList<>();
      comments.forEach(
          (filename, commentsForFile) ->
              commentsForFile.forEach(
                  commentInfo -> {
                    GerritComment comment = toComment(commentInfo);
                    comment.setFilename(filename);
                    result.add(comment);
                  }));
      return result;
    }
  }

  private GerritComment findThreadRoot(
      GerritComment comment, Map<String, GerritComment> commentsById) {
    GerritComment current = comment;
    Set<String> visited = new HashSet<>();
    while (current.getInReplyTo() != null && visited.add(current.getId())) {
      GerritComment parent = commentsById.get(current.getInReplyTo());
      if (parent == null) {
        break;
      }
      current = parent;
    }
    return current;
  }

  private boolean isPriorBotThread(List<GerritComment> thread, int currentPatchSet) {
    GerritComment root =
        thread.stream().filter(comment -> comment.getInReplyTo() == null).findFirst().orElse(null);
    return root != null
        && root.getAuthor() != null
        && root.getAuthor().getAccountId() == changeSetData.getGptAccountId()
        && root.getOneBasedPatchSet() < currentPatchSet;
  }

  private List<GerritComment> retrieveComments(GerritChange change) throws Exception {
    commentMap.clear();
    patchSetCommentMap.clear();
    try (ManualRequestContext requestContext = config.openRequestContext()) {
      Map<String, List<CommentInfo>> comments =
          config
              .getGerritApi()
              .changes()
              .id(
                  change.getProjectName(),
                  change.getBranchNameKey().shortName(),
                  change.getChangeKey().get())
              .commentsRequest()
              .get();

      // note that list of Map.Entry was used in order to keep the original response order
      List<Map.Entry<String, List<GerritComment>>> lastCommentEntries =
          comments.entrySet().stream()
              .map(
                  entry ->
                      Map.entry(
                          entry.getKey(),
                          entry.getValue().stream()
                              .map(GerritClientComments::toComment)
                              .collect(toList())))
              .collect(toList());

      String latestChangeMessageId = null;
      HashMap<String, List<GerritComment>> latestComments = new HashMap<>();
      for (Map.Entry<String, List<GerritComment>> entry : lastCommentEntries) {
        String filename = entry.getKey();
        log.info("Commented filename: {}", filename);

        List<GerritComment> commentsArray = entry.getValue();

        for (GerritComment commentObject : commentsArray) {
          commentObject.setFilename(filename);
          String commentId = commentObject.getId();
          String changeMessageId = commentObject.getChangeMessageId();
          String commentAuthorUsername = commentObject.getAuthor().getUsername();
          log.debug(
              "Change Message Id: {} - Author: {}", latestChangeMessageId, commentAuthorUsername);
          long updatedTimeStamp = getTimeStamp(commentObject.getUpdated());
          if (commentAuthorUsername.equals(authorUsername)
              && updatedTimeStamp
                  >= change.getEventTimeStamp() - MAX_SECS_GAP_BETWEEN_EVENT_AND_COMMENT) {
            log.debug("Found comment with updatedTimeStamp : {}", updatedTimeStamp);
            latestChangeMessageId = changeMessageId;
          }
          latestComments
              .computeIfAbsent(changeMessageId, k -> new ArrayList<>())
              .add(commentObject);
          commentMap.put(commentId, commentObject);
          if (filename.equals(GERRIT_PATCH_SET_FILENAME)) {
            patchSetCommentMap.put(changeMessageId, commentObject);
          }
        }
      }

      return latestComments.getOrDefault(latestChangeMessageId, null);
    }
  }

  private void addLastComments(GerritChange change) {
    ClientMessage clientMessage =
        new ClientMessage(config, changeSetData, pluginDataHandlerProvider, localizer);
    try {
      List<GerritComment> latestComments = retrieveComments(change);
      if (latestComments == null) {
        return;
      }
      for (GerritComment latestComment : latestComments) {
        String commentMessage = latestComment.getMessage();
        if (clientMessage.isBotAddressed(commentMessage)) {
          if (clientMessage.parseCommands(commentMessage, true)) {
            if (clientMessage.isContainingHistoryCommand()) {
              clientMessage.processHistoryCommand();
            }
            commentProperties.clear();
            return;
          }
          commentProperties.add(latestComment);
        }
      }
    } catch (Exception e) {
      log.error("Error while retrieving last comments for change: {}", change.getFullChangeId(), e);
    }
  }

  private static GerritComment toComment(CommentInfo comment) {
    GerritComment gerritComment = new GerritComment();
    gerritComment.setAuthor(toAuthor(comment.author));
    gerritComment.setChangeMessageId(comment.changeMessageId);
    gerritComment.setUnresolved(comment.unresolved);
    gerritComment.setPatchSet(comment.patchSet);
    gerritComment.setId(comment.id);
    gerritComment.setTag(comment.tag);
    gerritComment.setLine(comment.line);
    Optional.ofNullable(comment.range)
        .ifPresent(
            range ->
                gerritComment.setRange(
                    GerritCodeRange.builder()
                        .startLine(range.startLine)
                        .endLine(range.endLine)
                        .startCharacter(range.startCharacter)
                        .endCharacter(range.endCharacter)
                        .build()));
    gerritComment.setInReplyTo(comment.inReplyTo);
    Optional.ofNullable(comment.updated)
        .ifPresent(updated -> gerritComment.setUpdated(toDateString(updated)));
    gerritComment.setMessage(comment.message);
    gerritComment.setCommitId(comment.commitId);
    return gerritComment;
  }
}

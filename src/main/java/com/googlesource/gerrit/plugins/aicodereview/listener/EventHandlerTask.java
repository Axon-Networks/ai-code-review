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

package com.googlesource.gerrit.plugins.aicodereview.listener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.events.ChangeMergedEvent;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.PatchSetCreatedEvent;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.aicodereview.PatchSetReviewer;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.listener.IEventHandlerType;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritClient;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.git.GitRepoFiles;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventHandlerTask implements Runnable {
  @VisibleForTesting
  public enum Result {
    OK,
    NOT_SUPPORTED,
    FAILURE
  }

  public enum SupportedEvents {
    PATCH_SET_CREATED,
    COMMENT_ADDED,
    CHANGE_MERGED
  }

  public static final Map<SupportedEvents, Class<?>> EVENT_CLASS_MAP =
      Map.of(
          SupportedEvents.PATCH_SET_CREATED, PatchSetCreatedEvent.class,
          SupportedEvents.COMMENT_ADDED, CommentAddedEvent.class,
          SupportedEvents.CHANGE_MERGED, ChangeMergedEvent.class);

  private static final Map<String, SupportedEvents> EVENT_TYPE_MAP =
      Map.of(
          "patchset-created", SupportedEvents.PATCH_SET_CREATED,
          "comment-added", SupportedEvents.COMMENT_ADDED,
          "change-merged", SupportedEvents.CHANGE_MERGED);

  private final Configuration config;
  private final GerritClient gerritClient;
  private final ChangeSetData changeSetData;
  private final GerritChange change;
  private final PatchSetReviewer reviewer;
  private final GitRepoFiles gitRepoFiles;
  private final PluginDataHandlerProvider pluginDataHandlerProvider;

  private SupportedEvents processing_event_type;
  private IEventHandlerType eventHandlerType;

  @Inject
  EventHandlerTask(
      Configuration config,
      ChangeSetData changeSetData,
      GerritChange change,
      PatchSetReviewer reviewer,
      GerritClient gerritClient,
      GitRepoFiles gitRepoFiles,
      PluginDataHandlerProvider pluginDataHandlerProvider) {
    this.changeSetData = changeSetData;
    this.change = change;
    this.reviewer = reviewer;
    this.gerritClient = gerritClient;
    this.config = config;
    this.gitRepoFiles = gitRepoFiles;
    this.pluginDataHandlerProvider = pluginDataHandlerProvider;
  }

  @Override
  public void run() {
    execute();
  }

  @VisibleForTesting
  public Result execute() {
    if (!preProcessEvent()) {
      return Result.NOT_SUPPORTED;
    }

    try {
      log.info("Processing change: {}", change.getFullChangeId());
      eventHandlerType.processEvent();
      log.info("Finished processing change: {}", change.getFullChangeId());
    } catch (Exception e) {
      log.error("Error while processing change: {}", change.getFullChangeId(), e);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return Result.FAILURE;
    }
    return Result.OK;
  }

  private boolean preProcessEvent() {
    String eventType = Optional.ofNullable(change.getEventType()).orElse("");
    log.info("Event type {}", eventType);
    processing_event_type = EVENT_TYPE_MAP.get(eventType);
    if (processing_event_type == null) {
      return false;
    }

    if (!isReviewEnabled(change)) {
      return false;
    }

    while (true) {
      eventHandlerType = getEventHandlerType();
      switch (eventHandlerType.preprocessEvent()) {
        case EXIT -> {
          return false;
        }
        case SWITCH_TO_PATCH_SET_CREATED -> {
          processing_event_type = SupportedEvents.PATCH_SET_CREATED;
          continue;
        }
      }
      break;
    }

    return true;
  }

  private IEventHandlerType getEventHandlerType() {
    return switch (processing_event_type) {
      case PATCH_SET_CREATED ->
          new EventHandlerTypePatchSetReview(config, changeSetData, change, reviewer, gerritClient);
      case COMMENT_ADDED ->
          new EventHandlerTypeCommentAdded(changeSetData, change, reviewer, gerritClient);
      case CHANGE_MERGED ->
          new EventHandlerTypeChangeMerged(
              config, changeSetData, change, gitRepoFiles, pluginDataHandlerProvider);
    };
  }

  private boolean isReviewEnabled(GerritChange change) {
    List<String> enabledProjects =
        Splitter.on(",").omitEmptyStrings().splitToList(config.getEnabledProjects());
    if (!config.isGlobalEnable()
        && !enabledProjects.contains(change.getProjectNameKey().get())
        && !config.isProjectEnable()) {
      log.debug("The project {} is not enabled for review", change.getProjectNameKey());
      return false;
    }

    String topic = getTopic(change).orElse("");
    log.debug("PatchSet Topic retrieved: '{}'", topic);
    if (gerritClient.isDisabledTopic(topic)) {
      log.info("Disabled review for PatchSets with Topic '{}'", topic);
      return false;
    }

    return true;
  }

  private Optional<String> getTopic(GerritChange change) {
    try {
      ChangeAttribute changeAttribute = change.getPatchSetEvent().change.get();
      return Optional.ofNullable(changeAttribute.topic);
    } catch (NullPointerException e) {
      return Optional.empty();
    }
  }
}

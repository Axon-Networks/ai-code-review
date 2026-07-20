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

import com.google.gerrit.server.data.PatchSetAttribute;
import com.googlesource.gerrit.plugins.aicodereview.PatchSetReviewer;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.listener.IEventHandlerType;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritClient;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventHandlerTypePatchSetReview implements IEventHandlerType {
  private final Configuration config;
  private final GerritChange change;
  private final PatchSetReviewer reviewer;
  private final GerritClient gerritClient;

  EventHandlerTypePatchSetReview(
      Configuration config,
      GerritChange change,
      PatchSetReviewer reviewer,
      GerritClient gerritClient) {
    this.config = config;
    this.change = change;
    this.reviewer = reviewer;
    this.gerritClient = gerritClient;
  }

  @Override
  public PreprocessResult preprocessEvent() {
    if (!isPatchSetReviewEnabled(change)) {
      log.debug("Patch Set review disabled");
      return PreprocessResult.EXIT;
    }
    gerritClient.retrievePatchSetInfo(change);

    return PreprocessResult.OK;
  }

  @Override
  public void processEvent() throws Exception {
    reviewer.review(change);
  }

  private boolean isPatchSetReviewEnabled(GerritChange change) {
    if (!config.getAIReviewPatchSet()) {
      log.debug("Disabled review function for created or updated PatchSets.");
      return false;
    }
    Optional<PatchSetAttribute> patchSetAttributeOptional = change.getPatchSetAttribute();
    if (patchSetAttributeOptional.isEmpty()) {
      log.info("PatchSetAttribute event properties not retrieved");
      return false;
    }
    PatchSetAttribute patchSetAttribute = patchSetAttributeOptional.get();
    String authorUsername = patchSetAttribute.author.username;
    if (gerritClient.isDisabledUser(authorUsername)) {
      log.info("Review of PatchSets from user '{}' is disabled.", authorUsername);
      return false;
    }
    if (gerritClient.isWorkInProgress(change)) {
      log.debug("Skipping Patch Set processing due to its WIP status.");
      return false;
    }
    return true;
  }
}

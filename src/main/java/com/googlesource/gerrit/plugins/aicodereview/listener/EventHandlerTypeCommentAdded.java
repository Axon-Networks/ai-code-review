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

import com.googlesource.gerrit.plugins.aicodereview.PatchSetReviewer;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.listener.IEventHandlerType;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritClient;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventHandlerTypeCommentAdded implements IEventHandlerType {
  private final ChangeSetData changeSetData;
  private final GerritChange change;
  private final PatchSetReviewer reviewer;
  private final GerritClient gerritClient;

  EventHandlerTypeCommentAdded(
      ChangeSetData changeSetData,
      GerritChange change,
      PatchSetReviewer reviewer,
      GerritClient gerritClient) {
    this.changeSetData = changeSetData;
    this.change = change;
    this.reviewer = reviewer;
    this.gerritClient = gerritClient;
  }

  @Override
  public PreprocessResult preprocessEvent() {
    if (!gerritClient.retrieveLastComments(change)) {
      if (changeSetData.getForcedReview()) {
        return PreprocessResult.SWITCH_TO_PATCH_SET_CREATED;
      } else {
        log.info("No comments found for review");
        return PreprocessResult.EXIT;
      }
    }
    change.setIsCommentEvent(true);

    return PreprocessResult.OK;
  }

  @Override
  public void processEvent() throws Exception {
    reviewer.review(change);
  }
}

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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.patch.comment;

import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritClient;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.patch.code.InlineCode;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.patch.diff.FileDiffProcessed;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritCodeRange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatReplyItem;
import java.util.HashMap;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GerritCommentRange {
  private final HashMap<String, FileDiffProcessed> fileDiffsProcessed;

  public GerritCommentRange(GerritClient gerritClient, GerritChange change) {
    fileDiffsProcessed = gerritClient.getFileDiffsProcessed(change);
  }

  public Optional<GerritCodeRange> getGerritCommentRange(AIChatReplyItem replyItem) {
    Optional<GerritCodeRange> gerritCommentRange = Optional.empty();
    String filename = replyItem.getFilename();
    if (filename == null || filename.equals("/COMMIT_MSG")) {
      return gerritCommentRange;
    }
    if (replyItem.getCodeSnippet() == null) {
      log.info("CodeSnippet is null in reply '{}'.", replyItem);
      return gerritCommentRange;
    }
    if (!fileDiffsProcessed.containsKey(filename)) {
      log.info(
          "Filename '{}' not found for reply '{}'.\nFileDiffsProcessed = {}",
          filename,
          replyItem,
          fileDiffsProcessed);
      return gerritCommentRange;
    }
    InlineCode inlineCode = new InlineCode(fileDiffsProcessed.get(filename));
    gerritCommentRange = inlineCode.findCommentRange(replyItem);
    if (gerritCommentRange.isEmpty()) {
      log.info("Inline code not found for reply {}", replyItem);
    }
    return gerritCommentRange;
  }
}

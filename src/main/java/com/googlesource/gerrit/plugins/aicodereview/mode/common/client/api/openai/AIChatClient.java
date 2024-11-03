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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.ClientBase;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseContent;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseMessage;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseStreamed;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseUnstreamed;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatToolCall;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AIChatClient extends ClientBase {
  protected boolean isCommentEvent = false;
  @Getter protected String requestBody;

  public AIChatClient(Configuration config) {
    super(config);
  }

  protected AIChatResponseContent extractContent(Configuration config, String body)
      throws Exception {
    if (config.getAIStreamOutput() && !isCommentEvent) {
      StringBuilder finalContent = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new StringReader(body))) {
        String line;
        while ((line = reader.readLine()) != null) {
          extractContentFromLine(line).ifPresent(finalContent::append);
        }
      }
      return convertResponseContentFromJson(finalContent.toString());
    } else {
      AIChatResponseUnstreamed AIChatResponseUnstreamed =
          getGson().fromJson(body, AIChatResponseUnstreamed.class);
      return getResponseContent(
          AIChatResponseUnstreamed.getChoices().get(0).getMessage().getToolCalls());
    }
  }

  protected boolean validateResponse(
      AIChatResponseContent AIChatResponseContent, String changeId, int attemptInd) {
    String returnedChangeId = AIChatResponseContent.getChangeId();
    // A response is considered valid if either no changeId is returned or the changeId returned
    // matches the one
    // provided in the request
    boolean isValidated = returnedChangeId == null || changeId.equals(returnedChangeId);
    if (!isValidated) {
      log.error(
          "ChangedId mismatch error (attempt #{}).\nExpected value: {}\nReturned value: {}",
          attemptInd,
          changeId,
          returnedChangeId);
    }
    return isValidated;
  }

  protected AIChatResponseContent getResponseContent(List<AIChatToolCall> toolCalls) {
    if (toolCalls.size() > 1) {
      return mergeToolCalls(toolCalls);
    } else {
      return getArgumentAsResponse(toolCalls, 0);
    }
  }

  protected Optional<String> extractContentFromLine(String line) {
    String dataPrefix = "data: {\"id\"";

    if (!line.startsWith(dataPrefix)) {
      return Optional.empty();
    }
    AIChatResponseStreamed AIChatResponseStreamed =
        getGson().fromJson(line.substring("data: ".length()), AIChatResponseStreamed.class);
    AIChatResponseMessage delta = AIChatResponseStreamed.getChoices().get(0).getDelta();
    if (delta == null || delta.getToolCalls() == null) {
      return Optional.empty();
    }
    String content = getArgumentAsString(delta.getToolCalls(), 0);
    return Optional.ofNullable(content);
  }

  private AIChatResponseContent convertResponseContentFromJson(String content) {
    return getGson().fromJson(content, AIChatResponseContent.class);
  }

  private String getArgumentAsString(List<AIChatToolCall> toolCalls, int ind) {
    return toolCalls.get(ind).getFunction().getArguments();
  }

  private AIChatResponseContent getArgumentAsResponse(List<AIChatToolCall> toolCalls, int ind) {
    return convertResponseContentFromJson(getArgumentAsString(toolCalls, ind));
  }

  private AIChatResponseContent mergeToolCalls(List<AIChatToolCall> toolCalls) {
    AIChatResponseContent responseContent = getArgumentAsResponse(toolCalls, 0);
    for (int ind = 1; ind < toolCalls.size(); ind++) {
      responseContent.getReplies().addAll(getArgumentAsResponse(toolCalls, ind).getReplies());
    }
    return responseContent;
  }
}

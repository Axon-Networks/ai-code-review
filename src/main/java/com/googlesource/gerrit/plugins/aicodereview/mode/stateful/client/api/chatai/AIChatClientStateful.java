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

package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.chatai;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;
import static com.googlesource.gerrit.plugins.aicodereview.utils.JsonTextUtils.isJsonString;
import static com.googlesource.gerrit.plugins.aicodereview.utils.JsonTextUtils.unwrapJsonCode;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.api.openapi.ChatAIClient;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai.AIChatClient;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseContent;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.chatgpt.ChatGptRun;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.chatgpt.ChatGptThread;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.chatgpt.ChatGptThreadMessage;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.git.GitRepoFiles;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptThreadMessageResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AIChatClientStateful extends AIChatClient implements ChatAIClient {
  private static final String TYPE_MESSAGE_CREATION = "message_creation";
  private static final String TYPE_TOOL_CALLS = "tool_calls";

  private final GitRepoFiles gitRepoFiles;
  private final PluginDataHandlerProvider pluginDataHandlerProvider;

  @VisibleForTesting
  @Inject
  public AIChatClientStateful(
      Configuration config,
      GitRepoFiles gitRepoFiles,
      PluginDataHandlerProvider pluginDataHandlerProvider) {
    super(config);
    this.gitRepoFiles = gitRepoFiles;
    this.pluginDataHandlerProvider = pluginDataHandlerProvider;
  }

  public AIChatResponseContent ask(
      ChangeSetData changeSetData, GerritChange change, String patchSet) {
    isCommentEvent = change.getIsCommentEvent();
    String changeId = change.getFullChangeId();
    log.info(
        "Processing STATEFUL ChatGPT Request with changeId: {}, Patch Set: {}", changeId, patchSet);

    ChatGptThread chatGptThread = new ChatGptThread(config, pluginDataHandlerProvider);
    String threadId = chatGptThread.createThread();

    ChatGptThreadMessage chatGptThreadMessage =
        new ChatGptThreadMessage(threadId, config, changeSetData, change, patchSet);
    chatGptThreadMessage.addMessage();

    ChatGptRun chatGptRun =
        new ChatGptRun(
            threadId, config, changeSetData, change, gitRepoFiles, pluginDataHandlerProvider);
    chatGptRun.createRun();
    chatGptRun.pollRunStep();
    // Attribute `requestBody` is valued for testing purposes
    requestBody = chatGptThreadMessage.getAddMessageRequestBody();
    log.debug("ChatGPT request body: {}", requestBody);

    AIChatResponseContent AIChatResponseContent = getResponseContentStateful(threadId, chatGptRun);
    chatGptRun.cancelRun();

    return AIChatResponseContent;
  }

  private AIChatResponseContent getResponseContentStateful(String threadId, ChatGptRun chatGptRun) {
    return switch (chatGptRun.getFirstStepDetails().getType()) {
      case TYPE_MESSAGE_CREATION -> retrieveThreadMessage(threadId, chatGptRun);
      case TYPE_TOOL_CALLS -> getResponseContent(chatGptRun.getFirstStepToolCalls());
      default ->
          throw new IllegalStateException(
              "Unexpected Step Type in stateful ChatGpt response: " + chatGptRun);
    };
  }

  private AIChatResponseContent retrieveThreadMessage(String threadId, ChatGptRun chatGptRun) {
    ChatGptThreadMessage chatGptThreadMessage = new ChatGptThreadMessage(threadId, config);
    ChatGptThreadMessageResponse threadMessageResponse =
        chatGptThreadMessage.retrieveMessage(
            chatGptRun.getFirstStepDetails().getMessageCreation().getMessageId());
    String responseText = threadMessageResponse.getContent().get(0).getText().getValue();
    if (responseText == null) {
      throw new RuntimeException("ChatGPT thread message response content is null");
    }
    if (isJsonString(responseText)) {
      return extractResponseContent(responseText);
    }
    return new AIChatResponseContent(responseText);
  }

  private AIChatResponseContent extractResponseContent(String responseText) {
    return getGson().fromJson(unwrapJsonCode(responseText), AIChatResponseContent.class);
  }
}

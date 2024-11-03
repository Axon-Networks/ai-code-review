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

package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.chatgpt;

import static com.googlesource.gerrit.plugins.aicodereview.mode.common.client.prompt.AIChatPromptFactory.getAIChatPromptStateful;
import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.stateful.client.prompt.ChatGptPromptStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.ClientBase;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatRequestMessage;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.UriResourceLocatorStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptResponse;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptThreadMessageResponse;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

@Slf4j
public class ChatGptThreadMessage extends ClientBase {
  private final ChatGptHttpClient httpClient = new ChatGptHttpClient();
  private final String threadId;

  private ChangeSetData changeSetData;
  private GerritChange change;
  private String patchSet;
  private AIChatRequestMessage addMessageRequestBody;

  public ChatGptThreadMessage(String threadId, Configuration config) {
    super(config);
    this.threadId = threadId;
  }

  public ChatGptThreadMessage(
      String threadId,
      Configuration config,
      ChangeSetData changeSetData,
      GerritChange change,
      String patchSet) {
    this(threadId, config);
    this.changeSetData = changeSetData;
    this.change = change;
    this.patchSet = patchSet;
  }

  public ChatGptThreadMessageResponse retrieveMessage(String messageId) {
    Request request = createRetrieveMessageRequest(messageId);
    log.debug("ChatGPT Retrieve Thread Message request: {}", request);
    ChatGptThreadMessageResponse threadMessageResponse =
        getGson().fromJson(httpClient.execute(request), ChatGptThreadMessageResponse.class);
    log.info("Thread Message retrieved: {}", threadMessageResponse);

    return threadMessageResponse;
  }

  public void addMessage() {
    Request request = addMessageRequest();
    log.debug("ChatGPT Add Message request: {}", request);

    ChatGptResponse addMessageResponse =
        getGson().fromJson(httpClient.execute(request), ChatGptResponse.class);
    log.info("Message added: {}", addMessageResponse);
  }

  public String getAddMessageRequestBody() {
    return getGson().toJson(addMessageRequestBody);
  }

  private Request createRetrieveMessageRequest(String messageId) {
    URI uri =
        URI.create(
            config.getAIDomain()
                + UriResourceLocatorStateful.threadMessageRetrieveUri(threadId, messageId));
    log.debug("ChatGPT Retrieve Thread Message request URI: {}", uri);

    return httpClient.createRequestFromJson(uri.toString(), config, null);
  }

  private Request addMessageRequest() {
    URI uri =
        URI.create(config.getAIDomain() + UriResourceLocatorStateful.threadMessagesUri(threadId));
    log.debug("ChatGPT Add Message request URI: {}", uri);
    ChatGptPromptStateful chatGptPromptStateful =
        getAIChatPromptStateful(config, changeSetData, change);
    addMessageRequestBody =
        AIChatRequestMessage.builder()
            .role("user")
            .content(chatGptPromptStateful.getDefaultGptThreadReviewMessage(patchSet))
            .build();
    log.debug("ChatGPT Add Message request body: {}", addMessageRequestBody);

    return httpClient.createRequestFromJson(uri.toString(), config, addMessageRequestBody);
  }
}

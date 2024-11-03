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

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandler;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.UriResourceLocatorStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptResponse;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

@Slf4j
public class ChatGptThread {
  public static final String KEY_THREAD_ID = "threadId";

  private final ChatGptHttpClient httpClient = new ChatGptHttpClient();
  private final Configuration config;
  private final PluginDataHandler changeDataHandler;

  public ChatGptThread(Configuration config, PluginDataHandlerProvider pluginDataHandlerProvider) {
    this.config = config;
    this.changeDataHandler = pluginDataHandlerProvider.getChangeScope();
  }

  public String createThread() {
    String threadId = changeDataHandler.getValue(KEY_THREAD_ID);
    if (threadId == null) {
      Request request = createThreadRequest();
      log.debug("ChatGPT Create Thread request: {}", request);

      ChatGptResponse threadResponse =
          getGson().fromJson(httpClient.execute(request), ChatGptResponse.class);
      log.info("Thread created: {}", threadResponse);
      threadId = threadResponse.getId();
      changeDataHandler.setValue(KEY_THREAD_ID, threadId);
    } else {
      log.info("Thread found for the Change Set. Thread ID: {}", threadId);
    }
    return threadId;
  }

  private Request createThreadRequest() {
    URI uri = URI.create(config.getAIDomain() + UriResourceLocatorStateful.threadsUri());
    log.debug("ChatGPT Create Thread request URI: {}", uri);

    return httpClient.createRequestFromJson(uri.toString(), config, new Object());
  }
}

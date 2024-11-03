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
import static com.googlesource.gerrit.plugins.aicodereview.utils.ThreadUtils.threadSleep;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.ClientBase;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseMessage;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatToolCall;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.UriResourceLocatorStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.git.GitRepoFiles;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptCreateRunRequest;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptListResponse;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptResponse;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptRunStepsResponse;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

@Slf4j
public class ChatGptRun extends ClientBase {
  private static final int RUN_POLLING_INTERVAL = 1000;
  private static final int STEP_RETRIEVAL_INTERVAL = 10000;
  private static final int MAX_STEP_RETRIEVAL_RETRIES = 3;
  private static final Set<String> UNCOMPLETED_STATUSES =
      new HashSet<>(Arrays.asList("queued", "in_progress", "cancelling"));
  public static final String COMPLETED_STATUS = "completed";
  public static final String CANCELLED_STATUS = "cancelled";

  private final ChatGptHttpClient httpClient = new ChatGptHttpClient();
  private final ChangeSetData changeSetData;
  private final GerritChange change;
  private final String threadId;
  private final GitRepoFiles gitRepoFiles;
  private final PluginDataHandlerProvider pluginDataHandlerProvider;

  private ChatGptResponse runResponse;
  private ChatGptListResponse stepResponse;
  private String assistantId;

  public ChatGptRun(
      String threadId,
      Configuration config,
      ChangeSetData changeSetData,
      GerritChange change,
      GitRepoFiles gitRepoFiles,
      PluginDataHandlerProvider pluginDataHandlerProvider) {
    super(config);
    this.changeSetData = changeSetData;
    this.change = change;
    this.threadId = threadId;
    this.gitRepoFiles = gitRepoFiles;
    this.pluginDataHandlerProvider = pluginDataHandlerProvider;
  }

  public void createRun() {
    ChatGptAssistant chatGptAssistant =
        new ChatGptAssistant(
            config, changeSetData, change, gitRepoFiles, pluginDataHandlerProvider);
    assistantId = chatGptAssistant.setupAssistant();

    Request request = runCreateRequest();
    log.info("ChatGPT Create Run request: {}", request);

    runResponse = getGson().fromJson(httpClient.execute(request), ChatGptResponse.class);
    log.info("Run created: {}", runResponse);
  }

  public void pollRunStep() {
    for (int retries = 0; retries < MAX_STEP_RETRIEVAL_RETRIES; retries++) {
      int pollingCount = pollRun();

      Request stepsRequest = getStepsRequest();
      log.debug("ChatGPT Retrieve Run Steps request: {}", stepsRequest);

      String response = httpClient.execute(stepsRequest);
      stepResponse = getGson().fromJson(response, ChatGptListResponse.class);
      log.info("Run executed after {} polling requests: {}", pollingCount, stepResponse);
      if (stepResponse.getData().isEmpty()) {
        log.warn("Empty response from ChatGPT");
        threadSleep(STEP_RETRIEVAL_INTERVAL);
        continue;
      }
      return;
    }
  }

  public AIChatResponseMessage getFirstStepDetails() {
    return getFirstStep().getStepDetails();
  }

  public List<AIChatToolCall> getFirstStepToolCalls() {
    return getFirstStepDetails().getToolCalls();
  }

  public void cancelRun() {
    if (getFirstStep().getStatus().equals(COMPLETED_STATUS)) return;

    Request cancelRequest = getCancelRequest();
    log.debug("ChatGPT Cancel Run request: {}", cancelRequest);
    try {
      String fullResponse = httpClient.execute(cancelRequest);
      log.debug("ChatGPT Cancel Run Full response: {}", fullResponse);
      ChatGptResponse response = getGson().fromJson(fullResponse, ChatGptResponse.class);
      if (!response.getStatus().equals(CANCELLED_STATUS)) {
        log.error("Unable to cancel run. Run cancel response: {}", fullResponse);
      }
    } catch (Exception e) {
      log.error("Error cancelling run", e);
    }
  }

  private int pollRun() {
    int pollingCount = 0;

    while (UNCOMPLETED_STATUSES.contains(runResponse.getStatus())) {
      pollingCount++;
      log.debug("Polling request #{}", pollingCount);
      threadSleep(RUN_POLLING_INTERVAL);
      Request pollRequest = getPollRequest();
      log.debug("ChatGPT Poll Run request: {}", pollRequest);
      runResponse = getGson().fromJson(httpClient.execute(pollRequest), ChatGptResponse.class);
      log.debug("ChatGPT Run response: {}", runResponse);
    }
    return pollingCount;
  }

  private ChatGptRunStepsResponse getFirstStep() {
    return stepResponse.getData().get(0);
  }

  private Request runCreateRequest() {
    URI uri = URI.create(config.getAIDomain() + UriResourceLocatorStateful.runsUri(threadId));
    log.debug("ChatGPT Create Run request URI: {}", uri);
    ChatGptCreateRunRequest requestBody =
        ChatGptCreateRunRequest.builder().assistantId(assistantId).build();

    return httpClient.createRequestFromJson(uri.toString(), config, requestBody);
  }

  private Request getPollRequest() {
    URI uri =
        URI.create(
            config.getAIDomain()
                + UriResourceLocatorStateful.runRetrieveUri(threadId, runResponse.getId()));
    log.debug("ChatGPT Poll Run request URI: {}", uri);

    return getRunPollRequest(uri);
  }

  private Request getStepsRequest() {
    URI uri =
        URI.create(
            config.getAIDomain()
                + UriResourceLocatorStateful.runStepsUri(threadId, runResponse.getId()));
    log.debug("ChatGPT Run Steps request URI: {}", uri);

    return getRunPollRequest(uri);
  }

  private Request getCancelRequest() {
    URI uri =
        URI.create(
            config.getAIDomain()
                + UriResourceLocatorStateful.runCancelUri(threadId, runResponse.getId()));
    log.debug("ChatGPT Run Cancel request URI: {}", uri);

    return httpClient.createRequestFromJson(uri.toString(), config, new Object());
  }

  private Request getRunPollRequest(URI uri) {
    return httpClient.createRequestFromJson(uri.toString(), config, null);
  }
}

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
import static com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.chatgpt.ChatGptVectorStore.KEY_VECTOR_STORE_ID;
import static com.googlesource.gerrit.plugins.aicodereview.utils.FileUtils.createTempFileWithContent;
import static com.googlesource.gerrit.plugins.aicodereview.utils.FileUtils.sanitizeFilename;
import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandler;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.stateful.client.prompt.ChatGptPromptStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.ClientBase;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai.AIChatParameters;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai.AIChatTools;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatTool;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.UriResourceLocatorStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.git.GitRepoFiles;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.prompt.AIChatGptPromptStatefulBase;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptCreateAssistantRequestBody;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptFilesResponse;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptResponse;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptToolResources;
import com.googlesource.gerrit.plugins.aicodereview.utils.HashUtils;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

@Slf4j
public class ChatGptAssistant extends ClientBase {
  private final ChatGptHttpClient httpClient = new ChatGptHttpClient();
  private final ChangeSetData changeSetData;
  private final GerritChange change;
  private final GitRepoFiles gitRepoFiles;
  private final PluginDataHandler projectDataHandler;
  private final PluginDataHandler assistantsDataHandler;

  private String description;
  private String instructions;
  private String model;
  private Double temperature;

  public ChatGptAssistant(
      Configuration config,
      ChangeSetData changeSetData,
      GerritChange change,
      GitRepoFiles gitRepoFiles,
      PluginDataHandlerProvider pluginDataHandlerProvider) {
    super(config);
    this.changeSetData = changeSetData;
    this.change = change;
    this.gitRepoFiles = gitRepoFiles;
    this.projectDataHandler = pluginDataHandlerProvider.getProjectScope();
    this.assistantsDataHandler = pluginDataHandlerProvider.getAssistantsWorkspace();
  }

  public String setupAssistant() {
    setupAssistantParameters();
    String assistantIdHashKey = calculateAssistantIdHashKey();
    log.info("Calculated assistant id hash key: {}", assistantIdHashKey);
    String assistantId = assistantsDataHandler.getValue(assistantIdHashKey);
    if (assistantId == null || config.getForceCreateAssistant()) {
      log.debug("Setup Assistant for project {}", change.getProjectNameKey());
      String vectorStoreId = createVectorStore();
      assistantId = createAssistant(vectorStoreId);
      assistantsDataHandler.setValue(assistantIdHashKey, assistantId);
      log.info("Project assistant created with ID: {}", assistantId);
    } else {
      log.info("Project assistant found for the project. Assistant ID: {}", assistantId);
    }
    return assistantId;
  }

  public String createVectorStore() {
    String vectorStoreId = projectDataHandler.getValue(KEY_VECTOR_STORE_ID);
    if (vectorStoreId == null) {
      String fileId = uploadRepoFiles();
      ChatGptVectorStore vectorStore = new ChatGptVectorStore(fileId, config, change);
      ChatGptResponse createVectorStoreResponse = vectorStore.createVectorStore();
      vectorStoreId = createVectorStoreResponse.getId();
      projectDataHandler.setValue(KEY_VECTOR_STORE_ID, vectorStoreId);
      log.info("Vector Store created with ID: {}", vectorStoreId);
    } else {
      log.info("Vector Store found for the project. Vector Store ID: {}", vectorStoreId);
    }
    return vectorStoreId;
  }

  public void flushAssistantIds() {
    projectDataHandler.removeValue(KEY_VECTOR_STORE_ID);
    assistantsDataHandler.destroy();
  }

  private String uploadRepoFiles() {
    String repoFiles = gitRepoFiles.getGitRepoFiles(config, change);
    Path repoPath = null;
    try {
      repoPath =
          createTempFileWithContent(sanitizeFilename(change.getProjectName()), ".json", repoFiles);
      ChatGptFiles chatGptFiles = new ChatGptFiles(config);
      ChatGptFilesResponse chatGptFilesResponse = chatGptFiles.uploadFiles(repoPath);
      return chatGptFilesResponse.getId();
    } finally {
      if (repoPath != null) {
        try {
          Files.deleteIfExists(repoPath);
        } catch (IOException e) {
          log.warn("Failed to delete temp file " + repoPath, e);
        }
      }
    }
  }

  private String createAssistant(String vectorStoreId) {
    Request request = createRequest(vectorStoreId);
    log.debug("ChatGPT Create Assistant request: {}", request);

    ChatGptResponse assistantResponse =
        getGson().fromJson(httpClient.execute(request), ChatGptResponse.class);
    log.debug("Assistant created: {}", assistantResponse);

    return assistantResponse.getId();
  }

  private Request createRequest(String vectorStoreId) {
    URI uri = URI.create(config.getAIDomain() + UriResourceLocatorStateful.assistantCreateUri());
    log.debug("ChatGPT Create Assistant request URI: {}", uri);
    AIChatTool[] tools =
        new AIChatTool[] {new AIChatTool("file_search"), AIChatTools.retrieveFormatRepliesTool()};
    ChatGptToolResources toolResources =
        new ChatGptToolResources(
            new ChatGptToolResources.VectorStoreIds(new String[] {vectorStoreId}));
    ChatGptCreateAssistantRequestBody requestBody =
        ChatGptCreateAssistantRequestBody.builder()
            .name(AIChatGptPromptStatefulBase.DEFAULT_AI_CHAT_ASSISTANT_NAME)
            .description(description)
            .instructions(instructions)
            .model(model)
            .temperature(temperature)
            .tools(tools)
            .toolResources(toolResources)
            .build();
    log.debug("ChatGPT Create Assistant request body: {}", requestBody);

    return httpClient.createRequestFromJson(uri.toString(), config, requestBody);
  }

  private void setupAssistantParameters() {
    ChatGptPromptStateful chatGptPromptStateful =
        getAIChatPromptStateful(config, changeSetData, change);
    AIChatParameters AIChatParameters = new AIChatParameters(config, change.getIsCommentEvent());

    description = chatGptPromptStateful.getDefaultGptAssistantDescription();
    instructions = chatGptPromptStateful.getDefaultGptAssistantInstructions();
    model = config.getAIModel();
    temperature = AIChatParameters.getGptTemperature();
  }

  private String calculateAssistantIdHashKey() {
    return HashUtils.hashData(
        new ArrayList<>(List.of(description, instructions, model, temperature.toString())));
  }
}

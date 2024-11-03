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

package com.googlesource.gerrit.plugins.aicodereview;

import static com.googlesource.gerrit.plugins.aicodereview.mode.common.client.prompt.AIChatPromptFactory.getAIChatPromptStateful;
import static com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.chatgpt.ChatGptRun.COMPLETED_STATUS;
import static com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.chatgpt.ChatGptVectorStore.KEY_VECTOR_STORE_ID;
import static com.googlesource.gerrit.plugins.aicodereview.settings.Settings.GERRIT_PATCH_SET_FILENAME;
import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.common.net.HttpHeaders;
import com.google.gerrit.extensions.api.changes.FileApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandler;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.stateful.client.prompt.ChatGptPromptStateful;
import com.googlesource.gerrit.plugins.aicodereview.listener.EventHandlerTask.SupportedEvents;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseContent;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.UriResourceLocatorStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptListResponse;
import com.googlesource.gerrit.plugins.aicodereview.settings.Settings.Modes;
import com.googlesource.gerrit.plugins.aicodereview.utils.ThreadUtils;
import java.io.ByteArrayInputStream;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class AIChatReviewStatefulTest extends AIChatReviewTestBase {
  private static final String AI_CHAT_FILE_ID = "file-TEST_FILE_ID";
  private static final String AI_CHAT_VECTOR_ID = "file-TEST_VECTOR_ID";
  private static final String AI_CHAT_ASSISTANT_ID = "asst_TEST_ASSISTANT_ID";
  private static final String AI_CHAT_THREAD_ID = "thread_TEST_THREAD_ID";
  private static final String AI_CHAT_MESSAGE_ID = "msg_TEST_MESSAGE_ID";
  private static final String AI_CHAT_RUN_ID = "run_TEST_RUN_ID";

  private String formattedPatchContent;
  private ChatGptPromptStateful chatGptPromptStateful;
  private String requestContent;
  private PluginDataHandler projectHandler;

  public AIChatReviewStatefulTest() {
    MockitoAnnotations.openMocks(this);
  }

  protected void initGlobalAndProjectConfig() {
    super.initGlobalAndProjectConfig();

    // Mock the Global Config values that differ from the ones provided by Default
    when(globalConfig.getString(Mockito.eq("aiMode"), Mockito.anyString()))
        .thenReturn(Modes.stateful.name());

    setupPluginData();
    PluginDataHandlerProvider provider =
        new PluginDataHandlerProvider(mockPluginDataPath, getGerritChange());
    projectHandler = provider.getProjectScope();
    // Mock the pluginDataHandlerProvider to return the mocked project pluginDataHandler
    when(pluginDataHandlerProvider.getProjectScope()).thenReturn(projectHandler);
    // Mock the pluginDataHandlerProvider to return the mocked assistant pluginDataHandler
    when(pluginDataHandlerProvider.getAssistantsWorkspace()).thenReturn(projectHandler);
  }

  protected void initTest() {
    super.initTest();

    // Load the prompts
    chatGptPromptStateful = getAIChatPromptStateful(config, changeSetData, getGerritChange());
  }

  protected void setupMockRequests() throws RestApiException {
    super.setupMockRequests();

    // Mock the behavior of the Git Repository Manager
    String repoJson = readTestFile("__files/stateful/gitProjectFiles.json");
    when(gitRepoFiles.getGitRepoFiles(any(), any())).thenReturn(repoJson);

    // Mock the behavior of the ChatGPT create-file request
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlEqualTo(
                    URI.create(config.getAIDomain() + UriResourceLocatorStateful.filesCreateUri())
                        .getPath()))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                    .withBody("{\"id\": " + AI_CHAT_FILE_ID + "}")));

    // Mock the behavior of the ChatGPT create-vector-store request
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlEqualTo(
                    URI.create(
                            config.getAIDomain()
                                + UriResourceLocatorStateful.vectorStoreCreateUri())
                        .getPath()))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                    .withBody("{\"id\": " + AI_CHAT_VECTOR_ID + "}")));

    // Mock the behavior of the ChatGPT create-assistant request
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlEqualTo(
                    URI.create(
                            config.getAIDomain() + UriResourceLocatorStateful.assistantCreateUri())
                        .getPath()))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                    .withBody("{\"id\": " + AI_CHAT_ASSISTANT_ID + "}")));

    // Mock the behavior of the ChatGPT create-thread request
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlEqualTo(
                    URI.create(config.getAIDomain() + UriResourceLocatorStateful.threadsUri())
                        .getPath()))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                    .withBody("{\"id\": " + AI_CHAT_THREAD_ID + "}")));

    // Mock the behavior of the ChatGPT add-message-to-thread request
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlEqualTo(
                    URI.create(
                            config.getAIDomain()
                                + UriResourceLocatorStateful.threadMessagesUri(AI_CHAT_THREAD_ID))
                        .getPath()))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                    .withBody("{\"id\": " + AI_CHAT_MESSAGE_ID + "}")));

    // Mock the behavior of the ChatGPT create-run request
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlEqualTo(
                    URI.create(
                            config.getAIDomain()
                                + UriResourceLocatorStateful.runsUri(AI_CHAT_THREAD_ID))
                        .getPath()))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                    .withBody("{\"id\": " + AI_CHAT_RUN_ID + "}")));

    // Mock the behavior of the ChatGPT retrieve-run request
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlEqualTo(
                    URI.create(
                            config.getAIDomain()
                                + UriResourceLocatorStateful.runRetrieveUri(
                                    AI_CHAT_THREAD_ID, AI_CHAT_RUN_ID))
                        .getPath()))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                    .withBody("{\"status\": " + COMPLETED_STATUS + "}")));

    mockRetrieveRunSteps("chatGptRunStepsResponse.json");

    // Mock the behavior of the formatted patch request
    formattedPatchContent = readTestFile("__files/stateful/gerritFormattedPatch.txt");
    ByteArrayInputStream inputStream = new ByteArrayInputStream(formattedPatchContent.getBytes());
    BinaryResult binaryResult =
        BinaryResult.create(inputStream)
            .setContentType("text/plain")
            .setContentLength(formattedPatchContent.length());
    when(revisionApiMock.patch()).thenReturn(binaryResult);

    FileApi testFileMock = mock(FileApi.class);
    when(revisionApiMock.file("test_file_1.py")).thenReturn(testFileMock);
    DiffInfo testFileDiff =
        readTestFileToClass("__files/stateful/gerritPatchSetDiffTestFile.json", DiffInfo.class);
    when(testFileMock.diff(0)).thenReturn(testFileDiff);
  }

  protected void initComparisonContent() {
    super.initComparisonContent();

    promptTagComments = readTestFile("__files/stateful/aiChatPromptTagRequests.json");
  }

  protected ArgumentCaptor<ReviewInput> testRequestSent() throws RestApiException {
    ArgumentCaptor<ReviewInput> reviewInputCaptor = super.testRequestSent();
    requestContent = gptRequestBody.getAsJsonObject().get("content").getAsString();
    return reviewInputCaptor;
  }

  private String getReviewMessage(String responseFile, int tollCallId) {
    ChatGptListResponse responseContent =
        getGson().fromJson(readTestFile(responseFile), ChatGptListResponse.class);
    String reviewJsonResponse =
        responseContent
            .getData()
            .get(0)
            .getStepDetails()
            .getToolCalls()
            .get(tollCallId)
            .getFunction()
            .getArguments();
    return getGson()
        .fromJson(reviewJsonResponse, AIChatResponseContent.class)
        .getReplies()
        .get(0)
        .getReply();
  }

  private String getCapturedMessage(ArgumentCaptor<ReviewInput> captor, String filename) {
    return captor.getAllValues().get(0).comments.get(filename).get(0).message;
  }

  private void mockRetrieveRunSteps(String bodyFile) {
    // Mock the behavior of the ChatGPT retrieve-run-steps request
    WireMock.stubFor(
        WireMock.get(
                WireMock.urlEqualTo(
                    URI.create(
                            config.getAIDomain()
                                + UriResourceLocatorStateful.runStepsUri(
                                    AI_CHAT_THREAD_ID, AI_CHAT_RUN_ID))
                        .getPath()))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                    .withBodyFile(bodyFile)));
  }

  @Test
  public void patchSetCreatedOrUpdated() throws Exception {
    String reviewMessageCode = getReviewMessage("__files/chatGptRunStepsResponse.json", 0);
    String reviewMessageCommitMessage = getReviewMessage("__files/chatGptRunStepsResponse.json", 1);

    String reviewPrompt =
        chatGptPromptStateful.getDefaultGptThreadReviewMessage(formattedPatchContent);

    handleEventBasedOnType(SupportedEvents.PATCH_SET_CREATED);

    ArgumentCaptor<ReviewInput> captor = testRequestSent();
    Assert.assertEquals(reviewPrompt, requestContent);
    Assert.assertEquals(reviewMessageCode, getCapturedMessage(captor, "test_file_1.py"));
    Assert.assertEquals(
        reviewMessageCommitMessage, getCapturedMessage(captor, GERRIT_PATCH_SET_FILENAME));
  }

  @Test
  public void initialEmptyResponse() throws Exception {
    // To effectively test how an initial empty response from ChatGPT is managed, the following
    // approach is adopted:
    // 1. the ChatGPT run-steps request is initially mocked to return an empty data field, and
    // 2. the sleep function is mocked to replace the empty response with a valid one, instead of
    // pausing execution
    mockRetrieveRunSteps("chatGptRunStepsEmptyResponse.json");

    try (MockedStatic<ThreadUtils> mocked = Mockito.mockStatic(ThreadUtils.class)) {
      mocked
          .when(() -> ThreadUtils.threadSleep(Mockito.anyLong()))
          .thenAnswer(
              invocation -> {
                mockRetrieveRunSteps("chatGptRunStepsResponse.json");
                return null;
              });

      String reviewMessageCode = getReviewMessage("__files/chatGptRunStepsResponse.json", 0);
      String reviewMessageCommitMessage =
          getReviewMessage("__files/chatGptRunStepsResponse.json", 1);

      String reviewPrompt =
          chatGptPromptStateful.getDefaultGptThreadReviewMessage(formattedPatchContent);

      handleEventBasedOnType(SupportedEvents.PATCH_SET_CREATED);

      ArgumentCaptor<ReviewInput> captor = testRequestSent();
      Assert.assertEquals(reviewPrompt, requestContent);
      Assert.assertEquals(reviewMessageCode, getCapturedMessage(captor, "test_file_1.py"));
      Assert.assertEquals(
          reviewMessageCommitMessage, getCapturedMessage(captor, GERRIT_PATCH_SET_FILENAME));
    }
  }

  @Test
  public void gptMentionedInComment() throws RestApiException {
    String reviewMessageCommitMessage =
        getReviewMessage("__files/chatGptResponseRequestStateful.json", 0);

    chatGptPromptStateful.setCommentEvent(true);
    mockRetrieveRunSteps("chatGptResponseRequestStateful.json");

    handleEventBasedOnType(SupportedEvents.COMMENT_ADDED);

    ArgumentCaptor<ReviewInput> captor = testRequestSent();
    Assert.assertEquals(promptTagComments, requestContent);
    Assert.assertEquals(
        reviewMessageCommitMessage, getCapturedMessage(captor, GERRIT_PATCH_SET_FILENAME));
  }

  @Test
  public void gptMentionedInCommentMessageResponseText() throws RestApiException {
    String reviewMessageCommitMessage =
        getReviewMessage("__files/chatGptResponseRequestStateful.json", 0);

    chatGptPromptStateful.setCommentEvent(true);
    mockRetrieveRunSteps("chatGptResponseRequestMessageStateful.json");
    WireMock.stubFor(
        WireMock.get(
                WireMock.urlEqualTo(
                    URI.create(
                            config.getAIDomain()
                                + UriResourceLocatorStateful.threadMessageRetrieveUri(
                                    AI_CHAT_THREAD_ID, AI_CHAT_MESSAGE_ID))
                        .getPath()))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                    .withBodyFile("chatGptResponseThreadMessageText.json")));

    handleEventBasedOnType(SupportedEvents.COMMENT_ADDED);

    ArgumentCaptor<ReviewInput> captor = testRequestSent();
    Assert.assertEquals(promptTagComments, requestContent);
    Assert.assertEquals(
        reviewMessageCommitMessage, getCapturedMessage(captor, GERRIT_PATCH_SET_FILENAME));
  }

  @Test
  public void gptMentionedInCommentMessageResponseJson() throws RestApiException {
    String reviewMessageCommitMessage =
        getReviewMessage("__files/chatGptResponseRequestStateful.json", 0);

    chatGptPromptStateful.setCommentEvent(true);
    mockRetrieveRunSteps("chatGptResponseRequestMessageStateful.json");
    WireMock.stubFor(
        WireMock.get(
                WireMock.urlEqualTo(
                    URI.create(
                            config.getAIDomain()
                                + UriResourceLocatorStateful.threadMessageRetrieveUri(
                                    AI_CHAT_THREAD_ID, AI_CHAT_MESSAGE_ID))
                        .getPath()))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                    .withBodyFile("chatGptResponseThreadMessageJson.json")));

    handleEventBasedOnType(SupportedEvents.COMMENT_ADDED);

    ArgumentCaptor<ReviewInput> captor = testRequestSent();
    Assert.assertEquals(promptTagComments, requestContent);
    Assert.assertEquals(
        reviewMessageCommitMessage, getCapturedMessage(captor, GERRIT_PATCH_SET_FILENAME));
  }

  @Test
  public void gerritMergedCommits() {
    projectHandler.removeValue(KEY_VECTOR_STORE_ID);
    handleEventBasedOnType(SupportedEvents.CHANGE_MERGED);

    Assert.assertEquals(AI_CHAT_VECTOR_ID, projectHandler.getValue(KEY_VECTOR_STORE_ID));
  }
}

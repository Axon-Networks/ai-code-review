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

import static com.googlesource.gerrit.plugins.aicodereview.config.Configuration.KEY_AI_CHAT_ENDPOINT;
import static com.googlesource.gerrit.plugins.aicodereview.config.Configuration.KEY_AI_TYPE;
import static com.googlesource.gerrit.plugins.aicodereview.config.Configuration.KEY_STREAM_OUTPUT;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.joinWithNewLine;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.common.net.HttpHeaders;
import com.google.gerrit.extensions.api.changes.FileApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.json.OutputFormat;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.googlesource.gerrit.plugins.aicodereview.listener.EventHandlerTask;
import com.googlesource.gerrit.plugins.aicodereview.listener.EventHandlerTask.SupportedEvents;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateless.client.api.UriResourceLocatorStateless;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateless.client.prompt.AIChatPromptStateless;
import com.googlesource.gerrit.plugins.aicodereview.settings.Settings;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.TypeLiteral;
import org.apache.http.entity.ContentType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class AIChatReviewStatelessTest extends AIChatReviewTestBase {
  private ReviewInput expectedResponseStreamed;
  private String expectedSystemPromptReview;
  private String promptTagReview;
  private String diffContent;
  private ReviewInput gerritPatchSetReview;
  private JsonArray prompts;

  private AIChatPromptStateless AIChatPromptStateless;

  protected void initConfig() {
    super.initGlobalAndProjectConfig();

    when(globalConfig.getBoolean(Mockito.eq(KEY_STREAM_OUTPUT), Mockito.anyBoolean()))
        .thenReturn(GPT_STREAM_OUTPUT);
    when(globalConfig.getBoolean(Mockito.eq("aiReviewCommitMessages"), Mockito.anyBoolean()))
        .thenReturn(true);

    super.initConfig();

    // Load the prompts
    AIChatPromptStateless = new AIChatPromptStateless(config);
  }

  protected void setupMockRequests() throws RestApiException {
    super.setupMockRequests();

    // Mock the behavior of the gerritPatchSetFiles request
    Map<String, FileInfo> files =
        readTestFileToType(
            "__files/stateless/gerritPatchSetFiles.json",
            new TypeLiteral<Map<String, FileInfo>>() {}.getType());
    when(revisionApiMock.files(0)).thenReturn(files);

    // Mock the behavior of the gerritPatchSet diff requests
    FileApi commitMsgFileMock = mock(FileApi.class);
    when(revisionApiMock.file("/COMMIT_MSG")).thenReturn(commitMsgFileMock);
    DiffInfo commitMsgFileDiff =
        readTestFileToClass("__files/stateless/gerritPatchSetDiffCommitMsg.json", DiffInfo.class);
    when(commitMsgFileMock.diff(0)).thenReturn(commitMsgFileDiff);
    FileApi testFileMock = mock(FileApi.class);
    when(revisionApiMock.file("test_file.py")).thenReturn(testFileMock);
    DiffInfo testFileDiff =
        readTestFileToClass("__files/stateless/gerritPatchSetDiffTestFile.json", DiffInfo.class);
    when(testFileMock.diff(0)).thenReturn(testFileDiff);

    // Mock the behavior of the askGpt request
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlEqualTo(
                    URI.create(
                            config.getAIDomain() + UriResourceLocatorStateless.chatCompletionsUri())
                        .getPath()))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                    .withBodyFile("aiChatResponseStreamed.txt")));
  }

  protected void initComparisonContent() {
    super.initComparisonContent();

    diffContent = readTestFile("reducePatchSet/patchSetDiffOutput.json");
    gerritPatchSetReview =
        readTestFileToClass("__files/stateless/gerritPatchSetReview.json", ReviewInput.class);
    expectedResponseStreamed =
        readTestFileToClass(
            "__files/stateless/aiChatExpectedResponseStreamed.json", ReviewInput.class);
    promptTagReview = readTestFile("__files/stateless/aiChatPromptTagReview.json");
    promptTagComments = readTestFile("__files/stateless/aiChatPromptTagRequests.json");
    expectedSystemPromptReview = AIChatPromptStateless.getDefaultGptReviewSystemPrompt();
  }

  protected ArgumentCaptor<ReviewInput> testRequestSent() throws RestApiException {
    ArgumentCaptor<ReviewInput> reviewInputCaptor = super.testRequestSent();
    prompts = gptRequestBody.get("messages").getAsJsonArray();
    return reviewInputCaptor;
  }

  private String getReviewUserPrompt() {
    return joinWithNewLine(
        Arrays.asList(
            AIChatPromptStateless.DEFAULT_AI_CHAT_REVIEW_PROMPT,
            AIChatPromptStateless.DEFAULT_AI_CHAT_REVIEW_PROMPT_REVIEW
                + " "
                + AIChatPromptStateless.DEFAULT_AI_CHAT_PROMPT_FORCE_JSON_FORMAT
                + " "
                + AIChatPromptStateless.getPatchSetReviewPrompt(),
            AIChatPromptStateless.getReviewPromptCommitMessages(),
            AIChatPromptStateless.DEFAULT_AI_CHAT_REVIEW_PROMPT_DIFF,
            diffContent,
            AIChatPromptStateless.DEFAULT_AI_CHAT_REVIEW_PROMPT_MESSAGE_HISTORY,
            promptTagReview));
  }

  @Test
  public void patchSetCreatedOrUpdatedStreamed() throws Exception {
    String reviewUserPrompt = getReviewUserPrompt();
    AIChatPromptStateless.setCommentEvent(false);

    handleEventBasedOnType(SupportedEvents.PATCH_SET_CREATED);

    ArgumentCaptor<ReviewInput> captor = testRequestSent();
    String systemPrompt = prompts.get(0).getAsJsonObject().get("content").getAsString();
    Assert.assertEquals(expectedSystemPromptReview, systemPrompt);
    String userPrompt = prompts.get(1).getAsJsonObject().get("content").getAsString();
    Assert.assertEquals(reviewUserPrompt, userPrompt);

    Gson gson = OutputFormat.JSON_COMPACT.newGson();
    Assert.assertEquals(
        gson.toJson(expectedResponseStreamed), gson.toJson(captor.getAllValues().get(0)));
  }

  @Test
  public void patchSetCreatedOrUpdatedUnstreamed() throws Exception {
    when(globalConfig.getBoolean(Mockito.eq("aiStreamOutput"), Mockito.anyBoolean()))
        .thenReturn(false);
    when(globalConfig.getBoolean(Mockito.eq("enabledVoting"), Mockito.anyBoolean()))
        .thenReturn(true);

    String reviewUserPrompt = getReviewUserPrompt();
    AIChatPromptStateless.setCommentEvent(false);
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlEqualTo(
                    URI.create(
                            config.getAIDomain() + UriResourceLocatorStateless.chatCompletionsUri())
                        .getPath()))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                    .withBodyFile("aiChatResponseReview.json")));

    handleEventBasedOnType(SupportedEvents.PATCH_SET_CREATED);

    ArgumentCaptor<ReviewInput> captor = testRequestSent();
    String userPrompt = prompts.get(1).getAsJsonObject().get("content").getAsString();
    Assert.assertEquals(reviewUserPrompt, userPrompt);

    Gson gson = OutputFormat.JSON_COMPACT.newGson();
    Assert.assertEquals(
        gson.toJson(gerritPatchSetReview), gson.toJson(captor.getAllValues().get(0)));
  }

  @Test
  public void patchSetDisableUserGroup() {
    when(globalConfig.getString(Mockito.eq("disabledGroups"), Mockito.anyString()))
        .thenReturn(GERRIT_USER_GROUP);

    Assert.assertEquals(
        EventHandlerTask.Result.NOT_SUPPORTED,
        handleEventBasedOnType(SupportedEvents.PATCH_SET_CREATED));
  }

  @Test
  public void gptMentionedInComment() throws RestApiException {
    when(config.getGerritUserName()).thenReturn(GERRIT_GPT_USERNAME);
    AIChatPromptStateless.setCommentEvent(true);
    WireMock.stubFor(
        WireMock.post(
                WireMock.urlEqualTo(
                    URI.create(
                            config.getAIDomain() + UriResourceLocatorStateless.chatCompletionsUri())
                        .getPath()))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                    .withBodyFile("aiChatResponseRequestStateless.json")));

    handleEventBasedOnType(SupportedEvents.COMMENT_ADDED);
    int commentPropertiesSize =
        gerritClient.getClientData(getGerritChange()).getCommentProperties().size();

    String commentUserPrompt =
        joinWithNewLine(
            Arrays.asList(
                AIChatPromptStateless.DEFAULT_AI_CHAT_REQUEST_PROMPT_DIFF,
                diffContent,
                AIChatPromptStateless.DEFAULT_AI_CHAT_REQUEST_PROMPT_REQUESTS,
                readTestFile("__files/stateless/aiChatExpectedRequestMessage.json"),
                AIChatPromptStateless.getCommentRequestPrompt(commentPropertiesSize)));
    testRequestSent();
    String userPrompt = prompts.get(1).getAsJsonObject().get("content").getAsString();
    Assert.assertEquals(commentUserPrompt, userPrompt);
  }

  @Test
  public void testAITypeValidOptions() {
    when(globalConfig.getString(Mockito.eq("aiType"), Mockito.anyString())).thenReturn("CHATGPT");

    // check default for aiType is chatGPT.
    Assert.assertEquals(config.getAIType(), Settings.AIType.CHATGPT);

    when(globalConfig.getString(Mockito.eq("aiType"), Mockito.anyString())).thenReturn("OLLAMA");

    Assert.assertEquals(config.getAIType(), Settings.AIType.OLLAMA);
  }

  @Test
  public void testAITypeControlsEndpoint() {
    when(globalConfig.getString(Mockito.eq("aiType"), Mockito.anyString())).thenReturn("CHATGPT");

    // check default for aiType is chatGPT.
    Assert.assertEquals(config.getChatEndpoint(), "");
    Assert.assertEquals(
        UriResourceLocatorStateless.chatCompletionsUri(),
        UriResourceLocatorStateless.getChatResourceUri(config));

    // swap it to ollama, check we still get the chatCompletionsUri, as its the openai
    // compat endpoint we use.
    when(globalConfig.getString(Mockito.eq("aiType"), Mockito.anyString())).thenReturn("OLLAMA");
    Assert.assertEquals(
        UriResourceLocatorStateless.chatCompletionsUri(),
        UriResourceLocatorStateless.getChatResourceUri(config));

    // finally change to GENERIC, and check that we can specify any endpoint
    when(globalConfig.getString(Mockito.eq(KEY_AI_TYPE), Mockito.anyString()))
        .thenReturn("GENERIC");

    final String expectedValueForEndpoint = "/someendpoint/someapi/chat";
    when(globalConfig.getString(Mockito.eq(KEY_AI_CHAT_ENDPOINT), Mockito.anyString()))
        .thenReturn(expectedValueForEndpoint);
    Assert.assertEquals(
        expectedValueForEndpoint, UriResourceLocatorStateless.getChatResourceUri(config));
  }

  @Test
  public void testAITypeControlsAuthHeader() {
    when(globalConfig.getString(Mockito.eq("aiType"), Mockito.anyString())).thenReturn("CHATGPT");

    // check default for aiType is chatGPT.
    Assert.assertEquals("Authorization", config.getAuthorizationHeaderInfo().getName());
    Assert.assertEquals(
        "Bearer " + config.getAIToken(), config.getAuthorizationHeaderInfo().getValue());

    // swap it to ollama, check we still get the chatCompletionsUri, as its the openai
    // compat endpoint we use.
    when(globalConfig.getString(Mockito.eq("aiType"), Mockito.anyString())).thenReturn("OLLAMA");
    Assert.assertNull(
        "No expected value for auth header for ollama", config.getAuthorizationHeaderInfo());
  }
}

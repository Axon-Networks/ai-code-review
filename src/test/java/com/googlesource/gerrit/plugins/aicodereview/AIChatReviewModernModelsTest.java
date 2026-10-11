// Copyright (C) 2026 The Android Open Source Project
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

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.common.net.HttpHeaders;
import com.google.gerrit.extensions.api.changes.FileApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.json.OutputFormat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.googlesource.gerrit.plugins.aicodereview.listener.EventHandlerTask;
import com.googlesource.gerrit.plugins.aicodereview.listener.EventHandlerTask.SupportedEvents;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.TypeLiteral;
import org.apache.http.entity.ContentType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Exercises event handling, the real OpenAI HTTP client, and Gerrit review construction together.
 */
@RunWith(MockitoJUnitRunner.class)
public class AIChatReviewModernModelsTest extends AIChatReviewTestBase {
  private static final String MODEL = "gpt-6.1-sol";
  private static final String RESPONSES_PATH = "/v1/responses";

  public AIChatReviewModernModelsTest() {
    wireMockRule = new WireMockRule(WireMockConfiguration.options().dynamicPort());
  }

  @Override
  protected void initGlobalAndProjectConfig() {
    super.initGlobalAndProjectConfig();
    when(globalConfig.getBoolean(Mockito.eq("aiStreamOutput"), Mockito.anyBoolean()))
        .thenReturn(false);
    when(globalConfig.getBoolean(Mockito.eq("enabledVoting"), Mockito.anyBoolean()))
        .thenReturn(true);
    when(globalConfig.getBoolean(Mockito.eq("aiReviewCommitMessages"), Mockito.anyBoolean()))
        .thenReturn(true);
  }

  @Override
  protected String getTestAIDomain() {
    return "http://localhost:" + wireMockRule.port();
  }

  @Override
  protected String getTestAIModel() {
    return MODEL;
  }

  @Override
  protected void setupMockRequests() throws RestApiException {
    super.setupMockRequests();
    Map<String, FileInfo> files =
        readTestFileToType(
            "__files/stateless/gerritPatchSetFiles.json",
            new TypeLiteral<Map<String, FileInfo>>() {}.getType());
    when(revisionApiMock.files(0)).thenReturn(files);
    stubDiff("/COMMIT_MSG", "__files/stateless/gerritPatchSetDiffCommitMsg.json");
    stubDiff("test_file.py", "__files/stateless/gerritPatchSetDiffTestFile.json");
    stubModel(MODEL);
  }

  @Test
  public void gpt61SolPostsInlineReviewAndVoteUsingResponses() throws Exception {
    assertUnstreamedReview(MODEL);
  }

  @Test
  public void defaultStatelessChatGptModelPostsReviewUsingResponses() throws Exception {
    when(globalConfig.getString(Mockito.eq("aiModel"), Mockito.anyString()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    stubResponse(response("completed", "aiChatResponseReview.json").toString(), false);

    Assert.assertEquals(
        EventHandlerTask.Result.OK, handleEventBasedOnType(SupportedEvents.PATCH_SET_CREATED));

    assertResponsesRequest(MODEL, false);
    assertReviewPosted();
  }

  @Test
  public void gpt6AstraPostsReviewUsingResponses() throws Exception {
    assertUnstreamedReview("gpt-6-astra");
  }

  @Test
  public void gpt6SolPostsReviewUsingResponses() throws Exception {
    assertUnstreamedReview("gpt-6-sol");
  }

  @Test
  public void gpt6LunaPostsReviewUsingResponses() throws Exception {
    assertUnstreamedReview("gpt-6-luna");
  }

  @Test
  public void gpt56SolPostsReviewUsingResponses() throws Exception {
    assertUnstreamedReview("gpt-5.6-sol");
  }

  @Test
  public void gpt56TerraPostsReviewUsingResponses() throws Exception {
    assertUnstreamedReview("gpt-5.6-terra");
  }

  @Test
  public void gpt56LunaPostsReviewUsingResponses() throws Exception {
    assertUnstreamedReview("gpt-5.6-luna");
  }

  @Test
  public void versionedSnapshotPostsReviewUsingResponses() throws Exception {
    assertUnstreamedReview("gpt-5.4-2026-03-05");
  }

  @Test
  public void completedStreamPostsInlineReviewAndVoteOnce() throws Exception {
    when(globalConfig.getBoolean(Mockito.eq("aiStreamOutput"), Mockito.anyBoolean()))
        .thenReturn(true);
    JsonObject response = response("completed", "aiChatResponseReview.json");
    JsonObject itemDone = new JsonObject();
    itemDone.addProperty("type", "response.output_item.done");
    itemDone.addProperty("output_index", 1);
    itemDone.add("item", response.getAsJsonArray("output").get(1));
    stubResponse(
        "event: response.output_item.done\ndata: "
            + itemDone
            + "\n\n"
            + streamEvent("response.completed", response),
        true);

    Assert.assertEquals(
        EventHandlerTask.Result.OK, handleEventBasedOnType(SupportedEvents.PATCH_SET_CREATED));

    assertResponsesRequest(MODEL, true);
    assertReviewPosted();
  }

  @Test
  public void commentUsesUnstreamedResponsesAndRepliesToTriggeringInlineComment() throws Exception {
    when(globalConfig.getBoolean(Mockito.eq("aiStreamOutput"), Mockito.anyBoolean()))
        .thenReturn(true);
    stubResponse(response("completed", "aiChatResponseRequestStateless.json").toString(), false);

    Assert.assertEquals(
        EventHandlerTask.Result.OK, handleEventBasedOnType(SupportedEvents.COMMENT_ADDED));

    assertResponsesRequest(MODEL, false);
    ReviewInput review = testRequestSent().getValue();
    List<CommentInput> replies = review.comments.get("test_file.py");
    Assert.assertEquals(1, replies.size());
    Assert.assertEquals("08141f77_56026b40", replies.get(0).inReplyTo);
    Assert.assertTrue(replies.get(0).message.contains("Confirm that the method"));
    Assert.assertNull(review.labels);
  }

  @Test
  public void failedResponseDoesNotPostReview() throws Exception {
    assertTerminalFailure("failed", false);
  }

  @Test
  public void incompleteResponseDoesNotPostReview() throws Exception {
    assertTerminalFailure("incomplete", false);
  }

  @Test
  public void failedStreamDoesNotPostReview() throws Exception {
    assertTerminalFailure("failed", true);
  }

  @Test
  public void incompleteStreamDoesNotPostReview() throws Exception {
    assertTerminalFailure("incomplete", true);
  }

  @Test
  public void mismatchedChangeIdsExhaustRetriesWithoutPostingReview() throws Exception {
    for (int attempt = 0; attempt < 3; attempt++) {
      JsonObject response = response("completed", "aiChatResponseReview.json");
      JsonObject call = response.getAsJsonArray("output").get(1).getAsJsonObject();
      JsonObject arguments =
          getGson().fromJson(call.get("arguments").getAsString(), JsonObject.class);
      arguments.addProperty("changeId", "unexpectedChange" + attempt);
      call.addProperty("arguments", arguments.toString());
      WireMock.stubFor(
          WireMock.post(WireMock.urlEqualTo(RESPONSES_PATH))
              .inScenario("changeId retries")
              .whenScenarioStateIs(attempt == 0 ? Scenario.STARTED : "attempt" + attempt)
              .willSetStateTo("attempt" + (attempt + 1))
              .willReturn(
                  WireMock.aResponse()
                      .withStatus(HTTP_OK)
                      .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                      .withBody(response.toString())));
    }

    Assert.assertEquals(
        EventHandlerTask.Result.FAILURE, handleEventBasedOnType(SupportedEvents.PATCH_SET_CREATED));

    assertResponsesRequest(MODEL, false, 3);
    verify(revisionApiMock, never()).review(Mockito.any(ReviewInput.class));
  }

  private void assertUnstreamedReview(String model) throws Exception {
    when(globalConfig.getString(Mockito.eq("aiModel"), Mockito.anyString())).thenReturn(model);
    stubModel(model);
    stubResponse(response("completed", "aiChatResponseReview.json").toString(), false);

    Assert.assertEquals(
        EventHandlerTask.Result.OK, handleEventBasedOnType(SupportedEvents.PATCH_SET_CREATED));

    assertResponsesRequest(model, false);
    assertReviewPosted();
  }

  private void assertTerminalFailure(String status, boolean streamed) throws Exception {
    when(globalConfig.getBoolean(Mockito.eq("aiStreamOutput"), Mockito.anyBoolean()))
        .thenReturn(streamed);
    // A partial response may already contain plausible arguments. Terminal status must prevent
    // those findings and their vote from being published.
    JsonObject response = response(status, "aiChatResponseReview.json");
    stubResponse(
        streamed ? streamEvent("response." + status, response) : response.toString(), streamed);

    Assert.assertEquals(
        EventHandlerTask.Result.FAILURE, handleEventBasedOnType(SupportedEvents.PATCH_SET_CREATED));

    assertResponsesRequest(MODEL, streamed);
    verify(revisionApiMock, never()).review(Mockito.any(ReviewInput.class));
  }

  private void assertReviewPosted() throws Exception {
    ReviewInput expected =
        readTestFileToClass("__files/stateless/gerritPatchSetReview.json", ReviewInput.class);
    ReviewInput actual = testRequestSent().getValue();
    Assert.assertEquals(
        OutputFormat.JSON_COMPACT.newGson().toJson(expected),
        OutputFormat.JSON_COMPACT.newGson().toJson(actual));
    Mockito.verify(changeApiMock, Mockito.atLeastOnce()).revision(TEST_PATCH_SET_REVISION);
    Mockito.verify(changeApiMock, Mockito.never()).current();
  }

  private void assertResponsesRequest(String model, boolean streamed) {
    assertResponsesRequest(model, streamed, 1);
  }

  private void assertResponsesRequest(String model, boolean streamed, int count) {
    WireMock.verify(
        count,
        WireMock.postRequestedFor(WireMock.urlEqualTo(RESPONSES_PATH))
            .withHeader(HttpHeaders.AUTHORIZATION, WireMock.equalTo("Bearer " + AI_TOKEN)));
    WireMock.verify(0, WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/chat/completions")));
    List<LoggedRequest> requests =
        WireMock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo(RESPONSES_PATH)));
    JsonObject request = getGson().fromJson(requests.get(0).getBodyAsString(), JsonObject.class);
    Assert.assertEquals(model, request.get("model").getAsString());
    Assert.assertFalse(request.get("store").getAsBoolean());
    Assert.assertEquals(streamed, request.get("stream").getAsBoolean());
    Assert.assertFalse(request.has("temperature"));
    Assert.assertFalse(request.has("seed"));
    Assert.assertFalse(request.has("messages"));
    Assert.assertFalse(request.get("instructions").getAsString().isEmpty());
    Assert.assertTrue(request.get("input").getAsString().contains("test_file.py"));
    JsonArray tools = request.getAsJsonArray("tools");
    Assert.assertEquals(1, tools.size());
    JsonObject tool = tools.get(0).getAsJsonObject();
    Assert.assertEquals("function", tool.get("type").getAsString());
    Assert.assertEquals("format_replies", tool.get("name").getAsString());
    Assert.assertFalse(tool.get("strict").getAsBoolean());
    Assert.assertFalse(tool.has("function"));
    Assert.assertTrue(
        tool.getAsJsonObject("parameters").getAsJsonObject("properties").has("replies"));
    Assert.assertEquals(
        getGson().fromJson("{\"type\":\"function\",\"name\":\"format_replies\"}", JsonObject.class),
        request.getAsJsonObject("tool_choice"));
  }

  private void stubDiff(String filename, String fixture) throws RestApiException {
    FileApi file = mock(FileApi.class);
    when(revisionApiMock.file(filename)).thenReturn(file);
    when(file.diff(0)).thenReturn(readTestFileToClass(fixture, DiffInfo.class));
  }

  private void stubModel(String model) {
    JsonObject response = new JsonObject();
    response.addProperty("id", model);
    response.addProperty("object", "model");
    WireMock.stubFor(
        WireMock.get(WireMock.urlEqualTo("/v1/models/" + model))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                    .withBody(response.toString())));
  }

  private void stubResponse(String body, boolean streamed) {
    WireMock.stubFor(
        WireMock.post(WireMock.urlEqualTo(RESPONSES_PATH))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HTTP_OK)
                    .withHeader(
                        HttpHeaders.CONTENT_TYPE,
                        streamed ? "text/event-stream" : ContentType.APPLICATION_JSON.toString())
                    .withBody(body)));
  }

  private JsonObject response(String status, String argumentsFixture) {
    JsonObject legacy = readTestFileToClass("__files/" + argumentsFixture, JsonObject.class);
    String arguments =
        legacy
            .getAsJsonArray("choices")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("message")
            .getAsJsonArray("tool_calls")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("function")
            .get("arguments")
            .getAsString();
    JsonObject reasoning = new JsonObject();
    reasoning.addProperty("id", "rs_review");
    reasoning.addProperty("type", "reasoning");
    reasoning.add("summary", new JsonArray());
    JsonObject call = new JsonObject();
    call.addProperty("id", "fc_review");
    call.addProperty("type", "function_call");
    call.addProperty("name", "format_replies");
    call.addProperty("call_id", "call_review");
    call.addProperty("arguments", arguments);
    call.addProperty("status", "completed");
    JsonArray output = new JsonArray();
    output.add(reasoning);
    output.add(call);
    JsonObject response = new JsonObject();
    response.addProperty("id", "resp_review");
    response.addProperty("object", "response");
    response.addProperty("status", status);
    response.add("output", output);
    if ("incomplete".equals(status)) {
      JsonObject details = new JsonObject();
      details.addProperty("reason", "max_output_tokens");
      response.add("incomplete_details", details);
    } else if ("failed".equals(status)) {
      JsonObject error = new JsonObject();
      error.addProperty("code", "server_error");
      error.addProperty("message", "The model failed to complete the review");
      response.add("error", error);
    }
    return response;
  }

  private String streamEvent(String type, JsonObject response) {
    JsonObject event = new JsonObject();
    event.addProperty("type", type);
    event.add("response", response);
    return "event: " + type + "\ndata: " + event + "\n\n";
  }
}

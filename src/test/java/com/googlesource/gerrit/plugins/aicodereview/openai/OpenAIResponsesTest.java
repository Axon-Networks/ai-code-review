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

package com.googlesource.gerrit.plugins.aicodereview.openai;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai.AIChatTools;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai.OpenAIResponses;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseContent;
import org.junit.Assert;
import org.junit.Test;

public class OpenAIResponsesTest {
  @Test
  public void createsRequestWithForcedToolAndExistingOptionalFields() {
    JsonObject request =
        getGson()
            .fromJson(
                OpenAIResponses.createRequestBody("gpt-6.1-sol", "Review code", "The patch", true),
                JsonObject.class);

    Assert.assertEquals("gpt-6.1-sol", request.get("model").getAsString());
    Assert.assertEquals("Review code", request.get("instructions").getAsString());
    Assert.assertEquals("The patch", request.get("input").getAsString());
    Assert.assertTrue(request.get("stream").getAsBoolean());
    Assert.assertFalse(request.get("store").getAsBoolean());
    Assert.assertFalse(request.get("parallel_tool_calls").getAsBoolean());
    Assert.assertFalse(request.has("temperature"));
    Assert.assertFalse(request.has("seed"));
    Assert.assertFalse(request.has("reasoning"));
    Assert.assertEquals(1, request.getAsJsonArray("tools").size());
    JsonObject tool = request.getAsJsonArray("tools").get(0).getAsJsonObject();
    Assert.assertEquals("function", tool.get("type").getAsString());
    Assert.assertEquals("format_replies", tool.get("name").getAsString());
    Assert.assertFalse(tool.get("strict").getAsBoolean());
    Assert.assertFalse(tool.has("function"));
    Assert.assertEquals(
        getGson().toJsonTree(AIChatTools.retrieveFormatRepliesTool().getFunction().getParameters()),
        tool.get("parameters"));
    Assert.assertEquals(
        "function", request.getAsJsonObject("tool_choice").get("type").getAsString());
    Assert.assertEquals(
        "format_replies", request.getAsJsonObject("tool_choice").get("name").getAsString());
  }

  @Test
  public void decodesCompletedFunctionCallAlongsideReasoning() throws Exception {
    JsonObject response = response("completed");
    JsonObject reasoning = new JsonObject();
    reasoning.addProperty("type", "reasoning");
    response.getAsJsonArray("output").add(reasoning);
    response
        .getAsJsonArray("output")
        .add(functionCall("format_replies", arguments("Use a constant")));

    AIChatResponseContent content = OpenAIResponses.decode(response.toString(), false);

    Assert.assertEquals("Iabc", content.getChangeId());
    Assert.assertEquals(1, content.getReplies().size());
    Assert.assertEquals("Use a constant", content.getReplies().get(0).getReply());
    Assert.assertEquals("Example.java", content.getReplies().get(0).getFilename());
    Assert.assertEquals(Integer.valueOf(7), content.getReplies().get(0).getLineNumber());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsIncompleteResponseEvenWithCompleteLookingArguments() throws Exception {
    JsonObject response = response("incomplete");
    response.getAsJsonArray("output").add(functionCall("format_replies", arguments("Partial")));
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsFailedResponse() throws Exception {
    JsonObject response = response("failed");
    response.getAsJsonArray("output").add(functionCall("format_replies", arguments("Partial")));
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsErrorEvenWithCompletedStatus() throws Exception {
    JsonObject response = response("completed");
    JsonObject error = new JsonObject();
    error.addProperty("message", "Generation failed");
    response.add("error", error);
    response.getAsJsonArray("output").add(functionCall("format_replies", arguments("Partial")));
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsIncompleteDetailsEvenWithCompletedStatus() throws Exception {
    JsonObject response = response("completed");
    JsonObject details = new JsonObject();
    details.addProperty("reason", "max_output_tokens");
    response.add("incomplete_details", details);
    response.getAsJsonArray("output").add(functionCall("format_replies", arguments("Partial")));
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsRefusalWithoutReviewTool() throws Exception {
    JsonObject response = response("completed");
    JsonObject message = new JsonObject();
    message.addProperty("type", "message");
    JsonObject refusal = new JsonObject();
    refusal.addProperty("type", "refusal");
    refusal.addProperty("refusal", "Cannot review");
    JsonArray content = new JsonArray();
    content.add(refusal);
    message.add("content", content);
    response.getAsJsonArray("output").add(message);
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsWrongFunctionName() throws Exception {
    JsonObject response = response("completed");
    response.getAsJsonArray("output").add(functionCall("another_tool", arguments("Wrong tool")));
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsNullArguments() throws Exception {
    JsonObject response = response("completed");
    response.getAsJsonArray("output").add(functionCall("format_replies", "null"));
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsArgumentsWithoutReplies() throws Exception {
    JsonObject response = response("completed");
    response
        .getAsJsonArray("output")
        .add(functionCall("format_replies", "{\"changeId\":\"Iabc\"}"));
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsMalformedArguments() throws Exception {
    JsonObject response = response("completed");
    response.getAsJsonArray("output").add(functionCall("format_replies", "{\"replies\":["));
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test
  public void combinesRepliesFromMatchingCalls() throws Exception {
    JsonObject response = response("completed");
    response.getAsJsonArray("output").add(functionCall("format_replies", arguments("First")));
    response.getAsJsonArray("output").add(functionCall("another_tool", arguments("Ignore")));
    response.getAsJsonArray("output").add(functionCall("format_replies", arguments("Second")));

    AIChatResponseContent content = OpenAIResponses.decode(response.toString(), false);

    Assert.assertEquals("Iabc", content.getChangeId());
    Assert.assertEquals(2, content.getReplies().size());
    Assert.assertEquals("First", content.getReplies().get(0).getReply());
    Assert.assertEquals("Second", content.getReplies().get(1).getReply());
  }

  @Test
  public void decodesFinalResponseAfterSplitFunctionArguments() throws Exception {
    JsonObject response = response("completed");
    response
        .getAsJsonArray("output")
        .add(functionCall("format_replies", arguments("Use a constant")));
    JsonObject first = new JsonObject();
    first.addProperty("type", "response.function_call_arguments.delta");
    first.addProperty("delta", "{\"changeId\":\"Iabc\",");
    JsonObject second = new JsonObject();
    second.addProperty("type", "response.function_call_arguments.delta");
    second.addProperty("delta", "\"replies\":[{\"reply\":\"Use a constant\"}]}");
    String stream = ": keepalive\n\n" + event(first) + event(second) + completedEvent(response);

    AIChatResponseContent content = OpenAIResponses.decode(stream, true);

    Assert.assertEquals("Iabc", content.getChangeId());
    Assert.assertEquals(1, content.getReplies().size());
    Assert.assertEquals("Use a constant", content.getReplies().get(0).getReply());
    Assert.assertEquals("Example.java", content.getReplies().get(0).getFilename());
  }

  @Test
  public void decodesMultilineEventDataWithWindowsLineEndings() throws Exception {
    JsonObject response = response("completed");
    response.getAsJsonArray("output").add(functionCall("format_replies", arguments("Review")));
    String stream =
        "event: response.completed\r\n"
            + "data: {\"type\":\"response.completed\",\r\n"
            + "data: \"response\":"
            + response
            + "}\r\n\r\n";

    AIChatResponseContent content = OpenAIResponses.decode(stream, true);

    Assert.assertEquals("Review", content.getReplies().get(0).getReply());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsStreamWithoutCompletedEvent() throws Exception {
    JsonObject done = new JsonObject();
    done.addProperty("type", "response.output_item.done");
    done.add("item", functionCall("format_replies", arguments("Partial")));
    OpenAIResponses.decode(event(done), true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsStreamErrorEvenAfterCompletedEvent() throws Exception {
    JsonObject response = response("completed");
    response.getAsJsonArray("output").add(functionCall("format_replies", arguments("Partial")));
    JsonObject error = new JsonObject();
    error.addProperty("type", "error");
    error.addProperty("message", "Service error");
    OpenAIResponses.decode(completedEvent(response) + event(error), true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsIncompleteStream() throws Exception {
    JsonObject incomplete = new JsonObject();
    incomplete.addProperty("type", "response.incomplete");
    incomplete.add("response", response("incomplete"));
    OpenAIResponses.decode(event(incomplete), true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsFailedStream() throws Exception {
    JsonObject failed = new JsonObject();
    failed.addProperty("type", "response.failed");
    failed.add("response", response("failed"));
    OpenAIResponses.decode(event(failed), true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsCompletedEventWithoutResponse() throws Exception {
    JsonObject completed = new JsonObject();
    completed.addProperty("type", "response.completed");
    OpenAIResponses.decode(event(completed), true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsMalformedStreamEvent() throws Exception {
    OpenAIResponses.decode("event: response.completed\ndata: {\"type\":\n\n", true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsResponseWithoutStatus() throws Exception {
    JsonObject response = response("completed");
    response.remove("status");
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsResponseWithoutOutput() throws Exception {
    JsonObject response = response("completed");
    response.remove("output");
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsResponseWithNonArrayOutput() throws Exception {
    JsonObject response = response("completed");
    response.addProperty("output", "invalid");
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsMalformedOutputItem() throws Exception {
    JsonObject response = response("completed");
    response.getAsJsonArray("output").add(new JsonObject());
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsFunctionCallWithoutName() throws Exception {
    JsonObject response = response("completed");
    JsonObject call = functionCall("format_replies", arguments("Review"));
    call.remove("name");
    response.getAsJsonArray("output").add(call);
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsFunctionCallWithoutArguments() throws Exception {
    JsonObject response = response("completed");
    JsonObject call = functionCall("format_replies", arguments("Review"));
    call.remove("arguments");
    response.getAsJsonArray("output").add(call);
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsIncompleteFunctionCall() throws Exception {
    JsonObject response = response("completed");
    JsonObject call = functionCall("format_replies", arguments("Partial"));
    call.addProperty("status", "in_progress");
    response.getAsJsonArray("output").add(call);
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsNullReplyEntry() throws Exception {
    JsonObject response = response("completed");
    response.getAsJsonArray("output").add(functionCall("format_replies", "{\"replies\":[null]}"));
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsReplyWithoutRequiredText() throws Exception {
    JsonObject response = response("completed");
    response.getAsJsonArray("output").add(functionCall("format_replies", "{\"replies\":[{}]}"));
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsNonStringReplyText() throws Exception {
    JsonObject response = response("completed");
    response
        .getAsJsonArray("output")
        .add(functionCall("format_replies", "{\"replies\":[{\"reply\":1}]}"));
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsConflictingChangeIdsAcrossCalls() throws Exception {
    JsonObject response = response("completed");
    response.getAsJsonArray("output").add(functionCall("format_replies", arguments("First")));
    JsonObject second = getGson().fromJson(arguments("Second"), JsonObject.class);
    second.addProperty("changeId", "Ianother");
    response.getAsJsonArray("output").add(functionCall("format_replies", second.toString()));
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsDifferentChangeIdWhenFirstCallOmittedIt() throws Exception {
    JsonObject response = response("completed");
    JsonObject first = getGson().fromJson(arguments("First"), JsonObject.class);
    first.remove("changeId");
    response.getAsJsonArray("output").add(functionCall("format_replies", first.toString()));
    response.getAsJsonArray("output").add(functionCall("format_replies", arguments("Second")));
    OpenAIResponses.decode(response.toString(), false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsStreamEventWithoutType() throws Exception {
    OpenAIResponses.decode("data: {}\n\n", true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsCompletedEventWithNonObjectResponse() throws Exception {
    JsonObject completed = new JsonObject();
    completed.addProperty("type", "response.completed");
    completed.addProperty("response", "invalid");
    OpenAIResponses.decode(event(completed), true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsMalformedResponseBody() throws Exception {
    OpenAIResponses.decode("{\"status\":\"completed\",", false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsNullResponseBody() throws Exception {
    OpenAIResponses.decode("null", false);
  }

  private static String event(JsonObject event) {
    return "event: " + event.get("type").getAsString() + "\ndata: " + event + "\n\n";
  }

  private static String completedEvent(JsonObject response) {
    JsonObject completed = new JsonObject();
    completed.addProperty("type", "response.completed");
    completed.add("response", response);
    return event(completed);
  }

  private static JsonObject response(String status) {
    JsonObject response = new JsonObject();
    response.addProperty("id", "resp_1");
    response.addProperty("object", "response");
    response.addProperty("status", status);
    response.add("output", new JsonArray());
    return response;
  }

  private static JsonObject functionCall(String name, String arguments) {
    JsonObject call = new JsonObject();
    call.addProperty("type", "function_call");
    call.addProperty("name", name);
    call.addProperty("arguments", arguments);
    call.addProperty("status", "completed");
    return call;
  }

  private static String arguments(String reply) {
    JsonObject content = new JsonObject();
    content.addProperty("changeId", "Iabc");
    JsonObject item = new JsonObject();
    item.addProperty("reply", reply);
    item.addProperty("filename", "Example.java");
    item.addProperty("lineNumber", 7);
    JsonArray replies = new JsonArray();
    replies.add(item);
    content.add("replies", replies);
    return content.toString();
  }
}

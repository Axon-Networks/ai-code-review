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

package com.googlesource.gerrit.plugins.aicodereview.anthropic;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

import com.google.gson.JsonObject;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.anthropic.AnthropicStreamDecoder;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.anthropic.AnthropicTools;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.anthropic.AnthropicContentBlock;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.anthropic.AnthropicMessagesResponse;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.anthropic.AnthropicTool;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseContent;
import org.junit.Assert;
import org.junit.Test;

/**
 * Focused unit tests for the Anthropic adapter layer: schema translation of the {@code
 * format_replies} tool, and parsing of the Messages API response back into the plugin's internal
 * {@link AIChatResponseContent} model.
 *
 * <p>These don't need the Gerrit test harness (WireMock, Guice, Mockito) because the adapter layer
 * is pure — string/JSON in, typed object out. End-to-end behavior is exercised by the existing
 * stateless review tests via the AIType=CHATGPT path; the ANTHROPIC branch is verified config-side
 * in {@code AIChatReviewStatelessTest} and parse-side here.
 */
public class AnthropicAdaptersTest {

  @Test
  public void retrieveFormatRepliesTool_mapsOpenAISchemaToAnthropicShape() {
    AnthropicTool tool = AnthropicTools.retrieveFormatRepliesTool();

    // Name and description come from the `function` wrapper in formatRepliesTool.json
    Assert.assertEquals(AnthropicTools.FORMAT_REPLIES_TOOL_NAME, tool.getName());
    Assert.assertEquals("format_replies", tool.getName());
    Assert.assertNotNull(
        "description should propagate from the OpenAI tool JSON", tool.getDescription());
    Assert.assertFalse("description should not be empty", tool.getDescription().isEmpty());

    // input_schema is the `parameters` object from the OpenAI tool, verbatim. It should contain
    // the `replies` + `changeId` properties the review flow expects.
    JsonObject schema = tool.getInputSchema();
    Assert.assertNotNull(schema);
    Assert.assertEquals("object", schema.get("type").getAsString());
    JsonObject properties = schema.getAsJsonObject("properties");
    Assert.assertTrue("schema should advertise `replies` property", properties.has("replies"));
    Assert.assertTrue("schema should advertise `changeId` property", properties.has("changeId"));
  }

  @Test
  public void retrieveFormatRepliesToolChoice_pinsToFormatRepliesTool() {
    Assert.assertEquals("tool", AnthropicTools.retrieveFormatRepliesToolChoice().getType());
    Assert.assertEquals(
        AnthropicTools.FORMAT_REPLIES_TOOL_NAME,
        AnthropicTools.retrieveFormatRepliesToolChoice().getName());
  }

  @Test
  public void anthropicMessagesResponse_deserializesToolUseBlock() {
    // Minimal fixture approximating the Messages API response shape when Anthropic makes a
    // forced tool call to `format_replies`. The `input` field is a structured object that
    // matches AIChatResponseContent's fields.
    String body =
        "{"
            + "\"id\":\"msg_01\","
            + "\"type\":\"message\","
            + "\"role\":\"assistant\","
            + "\"model\":\"claude-opus-4-7\","
            + "\"stop_reason\":\"tool_use\","
            + "\"content\":["
            + "  {\"type\":\"text\",\"text\":\"I'll now call the tool.\"},"
            + "  {\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"format_replies\","
            + "   \"input\":{"
            + "     \"changeId\":\"Iabc123\","
            + "     \"replies\":["
            + "       {\"id\":1,\"reply\":\"Consider using a constant here\","
            + "        \"score\":0,\"relevance\":0.9,"
            + "        \"repeated\":false,\"conflicting\":false,"
            + "        \"filename\":\"foo.java\",\"lineNumber\":42,\"codeSnippet\":\"x = 7;\"}"
            + "     ]}"
            + "  }"
            + "]}";

    AnthropicMessagesResponse response = getGson().fromJson(body, AnthropicMessagesResponse.class);
    Assert.assertNotNull(response.getContent());
    Assert.assertEquals(2, response.getContent().size());

    AnthropicContentBlock toolUse = response.getContent().get(1);
    Assert.assertEquals("tool_use", toolUse.getType());
    Assert.assertEquals("format_replies", toolUse.getName());
    Assert.assertNotNull(toolUse.getInput());

    // Decode the tool_use.input into AIChatResponseContent and check the round trip.
    AIChatResponseContent content =
        getGson().fromJson(toolUse.getInput(), AIChatResponseContent.class);
    Assert.assertEquals("Iabc123", content.getChangeId());
    Assert.assertNotNull(content.getReplies());
    Assert.assertEquals(1, content.getReplies().size());
    Assert.assertEquals("Consider using a constant here", content.getReplies().get(0).getReply());
    Assert.assertEquals("foo.java", content.getReplies().get(0).getFilename());
  }

  @Test
  public void anthropicStreamDecoder_reassemblesPartialToolUseJson() throws Exception {
    // Faithful-ish fragment of an Anthropic SSE stream: a leading text block we ignore, then the
    // tool_use block for format_replies whose input JSON arrives split across three deltas.
    // `event:` and blank separator lines are included to confirm the decoder tolerates them.
    String sse =
        "event: message_start\n"
            + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"role\":\"assistant\","
            + "\"content\":[],\"model\":\"claude-opus-4-7\"}}\n"
            + "\n"
            + "event: content_block_start\n"
            + "data: {\"type\":\"content_block_start\",\"index\":0,"
            + "\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n"
            + "\n"
            + "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"index\":0,"
            + "\"delta\":{\"type\":\"text_delta\",\"text\":\"Reviewing...\"}}\n"
            + "\n"
            + "event: content_block_stop\n"
            + "data: {\"type\":\"content_block_stop\",\"index\":0}\n"
            + "\n"
            + "event: content_block_start\n"
            + "data: {\"type\":\"content_block_start\",\"index\":1,"
            + "\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_9\",\"name\":\"format_replies\",\"input\":{}}}\n"
            + "\n"
            + "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"index\":1,"
            + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"changeId\\\":\\\"Ixyz\\\",\"}}\n"
            + "\n"
            + "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"index\":1,"
            + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"replies\\\":[{\\\"id\\\":1,\\\"reply\\\":\\\"ship it\\\",\"}}\n"
            + "\n"
            + "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"index\":1,"
            + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"filename\\\":\\\"a.java\\\",\\\"lineNumber\\\":3,\\\"codeSnippet\\\":\\\"x\\\"}]}\"}}\n"
            + "\n"
            + "event: content_block_stop\n"
            + "data: {\"type\":\"content_block_stop\",\"index\":1}\n"
            + "\n"
            + "event: message_delta\n"
            + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"}}\n"
            + "\n"
            + "event: message_stop\n"
            + "data: {\"type\":\"message_stop\"}\n";

    AIChatResponseContent decoded = AnthropicStreamDecoder.decode(sse);
    Assert.assertEquals("Ixyz", decoded.getChangeId());
    Assert.assertNotNull(decoded.getReplies());
    Assert.assertEquals(1, decoded.getReplies().size());
    Assert.assertEquals("ship it", decoded.getReplies().get(0).getReply());
    Assert.assertEquals("a.java", decoded.getReplies().get(0).getFilename());
  }

  @Test(expected = RuntimeException.class)
  public void anthropicStreamDecoder_failsWhenNoFormatRepliesBlockSeen() throws Exception {
    // A stream that produces only a text block and ends with stop_reason=end_turn should fail
    // fast rather than silently return an empty review.
    String sse =
        "event: content_block_start\n"
            + "data: {\"type\":\"content_block_start\",\"index\":0,"
            + "\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n"
            + "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"index\":0,"
            + "\"delta\":{\"type\":\"text_delta\",\"text\":\"hmm\"}}\n"
            + "event: content_block_stop\n"
            + "data: {\"type\":\"content_block_stop\",\"index\":0}\n"
            + "event: message_delta\n"
            + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}\n";
    AnthropicStreamDecoder.decode(sse);
  }
}

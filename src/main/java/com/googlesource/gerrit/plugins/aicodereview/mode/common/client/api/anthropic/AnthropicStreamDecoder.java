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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.anthropic;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseContent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import lombok.extern.slf4j.Slf4j;

/**
 * Decodes a streamed response from Anthropic's Messages API (SSE) and reconstructs the tool_use
 * input JSON that the review flow expects.
 *
 * <p>Anthropic's SSE uses typed events, line-prefixed with {@code event:} and {@code data:}, for
 * example:
 *
 * <pre>
 *   event: content_block_start
 *   data: {"type":"content_block_start","index":1,
 *          "content_block":{"type":"tool_use","id":"toolu_x","name":"format_replies","input":{}}}
 *
 *   event: content_block_delta
 *   data: {"type":"content_block_delta","index":1,
 *          "delta":{"type":"input_json_delta","partial_json":"{\"changeId\":\""}}
 *
 *   event: content_block_stop
 *   data: {"type":"content_block_stop","index":1}
 * </pre>
 *
 * <p>For the review use case we only care about the {@code tool_use} block for the {@code
 * format_replies} tool: its {@code input} arrives split across a sequence of {@code
 * input_json_delta} events and must be concatenated in order, then parsed as a single JSON object
 * matching {@link AIChatResponseContent}.
 *
 * <p>The decoder is lenient about unrelated events (text blocks, pings, usage reports,
 * message-level deltas): they're ignored. If the {@code format_replies} tool_use block is never
 * seen, a {@link RuntimeException} is raised with the last-seen stop reason for easier debugging.
 */
@Slf4j
public class AnthropicStreamDecoder {

  private static final String DATA_PREFIX = "data:";

  private AnthropicStreamDecoder() {}

  public static AIChatResponseContent decode(String body) throws IOException {
    StringBuilder toolInputJson = new StringBuilder();
    Integer formatRepliesBlockIndex = null;
    boolean toolBlockClosed = false;
    String lastStopReason = null;

    try (BufferedReader reader = new BufferedReader(new StringReader(body))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.startsWith(DATA_PREFIX)) {
          // SSE also has `event:` and blank separator lines; only the `data:` payloads carry JSON.
          continue;
        }
        String payload = line.substring(DATA_PREFIX.length()).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
          continue;
        }

        JsonObject event;
        try {
          event = getGson().fromJson(payload, JsonObject.class);
        } catch (JsonSyntaxException e) {
          log.warn("Skipping non-JSON SSE data line from Anthropic: {}", payload);
          continue;
        }
        if (event == null || !event.has("type")) {
          continue;
        }

        String eventType = event.get("type").getAsString();
        switch (eventType) {
          case "content_block_start":
            // Look for the tool_use block corresponding to format_replies. There can be preceding
            // text blocks we don't care about.
            JsonObject contentBlock = event.getAsJsonObject("content_block");
            if (contentBlock != null
                && "tool_use".equals(asStringOrEmpty(contentBlock.get("type")))
                && AnthropicTools.FORMAT_REPLIES_TOOL_NAME.equals(
                    asStringOrEmpty(contentBlock.get("name")))) {
              formatRepliesBlockIndex = event.has("index") ? event.get("index").getAsInt() : null;
              toolInputJson.setLength(0);
              toolBlockClosed = false;
            }
            break;

          case "content_block_delta":
            if (formatRepliesBlockIndex == null || toolBlockClosed) {
              break;
            }
            int deltaIndex = event.has("index") ? event.get("index").getAsInt() : -1;
            if (deltaIndex != formatRepliesBlockIndex) {
              break;
            }
            JsonObject delta = event.getAsJsonObject("delta");
            if (delta == null) {
              break;
            }
            if ("input_json_delta".equals(asStringOrEmpty(delta.get("type")))
                && delta.has("partial_json")) {
              toolInputJson.append(delta.get("partial_json").getAsString());
            }
            break;

          case "content_block_stop":
            if (formatRepliesBlockIndex != null
                && event.has("index")
                && event.get("index").getAsInt() == formatRepliesBlockIndex) {
              toolBlockClosed = true;
            }
            break;

          case "message_delta":
            JsonObject msgDelta = event.getAsJsonObject("delta");
            if (msgDelta != null && msgDelta.has("stop_reason")) {
              lastStopReason = asStringOrEmpty(msgDelta.get("stop_reason"));
            }
            break;

          case "error":
            // Surface the server-sent error instead of silently producing a partial response.
            throw new RuntimeException(
                "Anthropic streaming error event: "
                    + (event.has("error") ? event.get("error").toString() : payload));

          default:
            // message_start, message_stop, ping, usage, etc. — not needed for review extraction.
            break;
        }
      }
    }

    if (formatRepliesBlockIndex == null || toolInputJson.length() == 0) {
      throw new RuntimeException(
          "Anthropic stream did not produce a `"
              + AnthropicTools.FORMAT_REPLIES_TOOL_NAME
              + "` tool_use block; stop_reason="
              + (lastStopReason == null ? "<none>" : lastStopReason));
    }

    String assembled = toolInputJson.toString();
    log.debug("Assembled Anthropic tool_use input: {}", assembled);
    try {
      return getGson().fromJson(assembled, AIChatResponseContent.class);
    } catch (JsonSyntaxException e) {
      throw new RuntimeException(
          "Anthropic stream produced invalid tool_use input JSON: " + assembled, e);
    }
  }

  private static String asStringOrEmpty(JsonElement element) {
    return element == null || element.isJsonNull() ? "" : element.getAsString();
  }
}

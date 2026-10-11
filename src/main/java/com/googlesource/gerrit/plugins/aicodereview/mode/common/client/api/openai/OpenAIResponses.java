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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseContent;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Objects;

/** Translates the plugin's review tool to the OpenAI Responses API. */
public final class OpenAIResponses {
  private static final String FORMAT_REPLIES = "format_replies";

  private OpenAIResponses() {}

  /** Builds a stateless Responses request using the plugin's existing review schema. */
  public static String createRequestBody(
      String model, String systemPrompt, String userPrompt, boolean stream) {
    JsonObject request = new JsonObject();
    request.addProperty("model", model);
    request.addProperty("instructions", systemPrompt);
    request.addProperty("input", userPrompt);
    request.addProperty("stream", stream);
    request.addProperty("store", false);
    request.addProperty("parallel_tool_calls", false);

    JsonObject tool =
        getGson()
            .toJsonTree(AIChatTools.retrieveFormatRepliesTool().getFunction())
            .getAsJsonObject();
    tool.addProperty("type", "function");
    // Responses otherwise attempts to normalize optional fields into a strict schema.
    tool.addProperty("strict", false);
    JsonArray tools = new JsonArray();
    tools.add(tool);
    request.add("tools", tools);

    JsonObject choice = new JsonObject();
    choice.addProperty("type", "function");
    choice.addProperty("name", FORMAT_REPLIES);
    request.add("tool_choice", choice);
    return getGson().toJson(request);
  }

  /**
   * Decodes a complete HTTP response body. Streaming bodies must include a successful {@code
   * response.completed} event; intermediate argument deltas cannot authorize a partial review.
   *
   * @throws IllegalArgumentException if the response is unsuccessful or has no valid review tool
   */
  public static AIChatResponseContent decode(String body, boolean stream) throws Exception {
    JsonObject response = stream ? completedResponse(body) : parseObject(body);
    if (!"completed".equals(requiredString(response, "status"))
        || hasValue(response, "error")
        || hasValue(response, "incomplete_details")) {
      throw new IllegalArgumentException("OpenAI response did not complete successfully");
    }
    AIChatResponseContent content = null;
    for (JsonElement item : requiredArray(response, "output")) {
      JsonObject output = requiredObject(item, "output item");
      if ("function_call".equals(requiredString(output, "type"))
          && FORMAT_REPLIES.equals(requiredString(output, "name"))) {
        if (hasValue(output, "status") && !"completed".equals(requiredString(output, "status"))) {
          throw new IllegalArgumentException(
              "OpenAI format_replies function call did not complete");
        }
        JsonObject arguments = parseObject(requiredString(output, "arguments"));
        for (JsonElement reply : requiredArray(arguments, "replies")) {
          requiredString(requiredObject(reply, "reply entry"), "reply");
        }
        AIChatResponseContent next;
        try {
          next = getGson().fromJson(arguments, AIChatResponseContent.class);
        } catch (JsonParseException e) {
          throw new IllegalArgumentException("OpenAI format_replies arguments were malformed", e);
        }
        if (content == null) {
          content = next;
        } else {
          if (!Objects.equals(content.getChangeId(), next.getChangeId())) {
            throw new IllegalArgumentException(
                "OpenAI format_replies calls returned conflicting changeIds");
          }
          content.getReplies().addAll(next.getReplies());
        }
      }
    }
    if (content == null) {
      throw new IllegalArgumentException(
          "OpenAI response did not contain a format_replies function call");
    }
    return content;
  }

  private static boolean hasValue(JsonObject object, String name) {
    return object.has(name) && !object.get(name).isJsonNull();
  }

  private static String requiredString(JsonObject object, String name) {
    JsonElement value = object.get(name);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("OpenAI response expected a string field: " + name);
    }
    return value.getAsString();
  }

  private static JsonArray requiredArray(JsonObject object, String name) {
    JsonElement value = object.get(name);
    if (value == null || !value.isJsonArray()) {
      throw new IllegalArgumentException("OpenAI response expected an array field: " + name);
    }
    return value.getAsJsonArray();
  }

  private static JsonObject requiredObject(JsonElement value, String context) {
    if (value == null || !value.isJsonObject()) {
      throw new IllegalArgumentException("OpenAI response expected an object: " + context);
    }
    return value.getAsJsonObject();
  }

  private static JsonObject parseObject(String json) {
    try {
      JsonObject object = getGson().fromJson(json, JsonObject.class);
      if (object == null) {
        throw new IllegalArgumentException("OpenAI response was empty");
      }
      return object;
    } catch (JsonParseException e) {
      throw new IllegalArgumentException("OpenAI response JSON was malformed", e);
    }
  }

  private static JsonObject completedResponse(String body) throws Exception {
    JsonObject completed = null;
    StringBuilder data = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new StringReader(body))) {
      String line;
      do {
        line = reader.readLine();
        if (line == null || line.isEmpty()) {
          if (data.length() > 0) {
            JsonObject event = parseObject(data.toString());
            String type = requiredString(event, "type");
            if ("error".equals(type)
                || "response.failed".equals(type)
                || "response.incomplete".equals(type)) {
              throw new IllegalArgumentException(
                  "OpenAI response stream did not complete successfully");
            }
            if ("response.completed".equals(type)) {
              completed = requiredObject(event.get("response"), "completed response");
            }
            data.setLength(0);
          }
        } else if (line.startsWith("data:")) {
          if (data.length() > 0) {
            data.append('\n');
          }
          data.append(line.substring("data:".length()).stripLeading());
        }
      } while (line != null);
    }
    if (completed == null) {
      throw new IllegalArgumentException("OpenAI response stream ended before response.completed");
    }
    return completed;
  }
}

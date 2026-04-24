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

import com.google.gson.JsonObject;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.anthropic.AnthropicTool;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.anthropic.AnthropicToolChoice;
import com.googlesource.gerrit.plugins.aicodereview.utils.FileUtils;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Builds Anthropic-shape tool definitions from the single source of truth at {@code
 * config/formatRepliesTool.json} (which is written in OpenAI's function-calling shape). Rather than
 * duplicating the JSON schema into an Anthropic-specific resource file, we translate it at runtime
 * so the review schema has exactly one canonical definition.
 */
public class AnthropicTools {

  public static final String FORMAT_REPLIES_TOOL_NAME = "format_replies";

  /**
   * Convert the OpenAI-shape {@code formatRepliesTool.json} into an Anthropic {@link
   * AnthropicTool}. OpenAI wraps the schema as {@code {type:"function", function:{name,
   * description, parameters}}}; Anthropic expects {@code {name, description, input_schema}}
   * directly.
   */
  public static AnthropicTool retrieveFormatRepliesTool() {
    try (InputStreamReader reader =
        FileUtils.getInputStreamReader("config/formatRepliesTool.json")) {
      JsonObject root = getGson().fromJson(reader, JsonObject.class);
      JsonObject function = root.getAsJsonObject("function");
      if (function == null) {
        throw new IllegalStateException(
            "Expected `function` object in formatRepliesTool.json for Anthropic translation");
      }
      JsonObject parameters = function.getAsJsonObject("parameters");
      return AnthropicTool.builder()
          .name(
              function.has("name") ? function.get("name").getAsString() : FORMAT_REPLIES_TOOL_NAME)
          .description(function.has("description") ? function.get("description").getAsString() : "")
          .inputSchema(parameters)
          .build();
    } catch (IOException e) {
      throw new RuntimeException("Failed to load data for Anthropic `format_replies` tool", e);
    }
  }

  /** Pin Anthropic to always call {@code format_replies} so we get the structured review output. */
  public static AnthropicToolChoice retrieveFormatRepliesToolChoice() {
    return AnthropicToolChoice.builder().type("tool").name(FORMAT_REPLIES_TOOL_NAME).build();
  }
}

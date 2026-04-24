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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.anthropic;

import com.google.gson.JsonObject;
import lombok.Data;

/**
 * One block inside Anthropic's {@code content} response array. Can be of type {@code "text"} or
 * {@code "tool_use"} (and others we don't exercise). When it's {@code tool_use}, {@link #input}
 * holds the structured arguments the model chose for the tool call. For the review flow we only
 * care about the {@code format_replies} tool_use block whose {@code input} matches the schema of
 * {@link
 * com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseContent}.
 */
@Data
public class AnthropicContentBlock {
  private String type;
  // text blocks
  private String text;
  // tool_use blocks
  private String id;
  private String name;
  private JsonObject input;
}

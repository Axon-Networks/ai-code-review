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

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Request body for Anthropic's Messages API (POST /v1/messages).
 *
 * <p>Key differences from OpenAI's chat completions request:
 *
 * <ul>
 *   <li>The system prompt lives in a top-level {@code system} string, not in {@code messages}.
 *   <li>{@code max_tokens} is required.
 *   <li>Tools are defined with a top-level {@code input_schema}, not nested under {@code function}.
 * </ul>
 */
@Data
@Builder
public class AnthropicMessagesRequest {
  private String model;
  private String system;
  private List<AnthropicMessage> messages;
  private double temperature;

  @SerializedName("max_tokens")
  private int maxTokens;

  private boolean stream;
  private List<AnthropicTool> tools;

  @SerializedName("tool_choice")
  private AnthropicToolChoice toolChoice;
}

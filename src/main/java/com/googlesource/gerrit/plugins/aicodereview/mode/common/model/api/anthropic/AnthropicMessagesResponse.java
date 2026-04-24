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
import lombok.Data;

/**
 * Top-level response shape from Anthropic's Messages API ({@code POST /v1/messages}).
 *
 * <p>We only model the fields the plugin actually reads. The review flow cares about the {@code
 * content} array of {@link AnthropicContentBlock}s, from which we locate the {@code tool_use} block
 * for the {@code format_replies} tool call.
 */
@Data
public class AnthropicMessagesResponse {
  private String id;
  private String type;
  private String role;
  private String model;
  private List<AnthropicContentBlock> content;

  @SerializedName("stop_reason")
  private String stopReason;
}

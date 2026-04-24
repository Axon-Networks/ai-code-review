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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single item in the Anthropic Messages API {@code messages} array.
 *
 * <p>The Messages API only accepts {@code user} and {@code assistant} roles in this list; the
 * {@code system} prompt is sent as a top-level field on {@link AnthropicMessagesRequest}. For the
 * review flow the plugin only ever sends a single {@code user} message containing the prompt
 * payload.
 *
 * <p>The {@code content} field can be either a plain string or a list of typed content blocks. For
 * the simple user prompt we send, a plain string is sufficient and Anthropic handles it
 * transparently.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnthropicMessage {
  private String role;
  private String content;
}

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
 * Forces Anthropic to call a specific tool. The plugin always pins this to {@code format_replies}
 * so that the model is required to return the structured review output.
 *
 * <p>Shape: {@code {"type":"tool","name":"format_replies"}}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnthropicToolChoice {
  private String type;
  private String name;
}

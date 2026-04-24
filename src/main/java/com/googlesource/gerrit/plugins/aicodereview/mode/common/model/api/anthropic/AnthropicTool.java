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
import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

/**
 * Anthropic tool definition. Differs from OpenAI's {@code {type:"function", function:{...}}}
 * wrapper shape: Anthropic puts the schema directly at the tool level as {@code input_schema}.
 *
 * <p>We keep {@code input_schema} as a raw {@link JsonObject} because the schema itself is the
 * tool's JSON-schema description, and trying to map it to typed classes would duplicate the
 * already-authoritative {@code formatRepliesTool.json} resource. Instead we let Gson emit the
 * parsed schema verbatim.
 */
@Data
@Builder
public class AnthropicTool {
  private String name;
  private String description;

  @SerializedName("input_schema")
  private JsonObject inputSchema;
}

// Copyright (C) 2024 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.prompt;

import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.COLON_SPACE;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.getNumberedList;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.joinWithNewLine;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.stateful.client.prompt.ChatGptPromptStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AIChatGptPromptStatefulReview extends AIChatGptPromptStatefulBase
    implements ChatGptPromptStateful {
  private static final String RULE_NUMBER_PREFIX = "RULE #";

  public static String DEFAULT_AI_CHAT_ASSISTANT_INSTRUCTIONS_REVIEW;
  public static String DEFAULT_AI_CHAT_ASSISTANT_INSTRUCTIONS_DONT_GUESS_CODE;
  public static String DEFAULT_AI_CHAT_ASSISTANT_INSTRUCTIONS_HISTORY;

  public AIChatGptPromptStatefulReview(
      Configuration config, ChangeSetData changeSetData, GerritChange change) {
    super(config, changeSetData, change);
    if (DEFAULT_AI_CHAT_ASSISTANT_INSTRUCTIONS_REVIEW == null) {
      loadDefaultPrompts("promptsStatefulReview");
    }
  }

  @Override
  public void addGptAssistantInstructions(List<String> instructions) {
    instructions.addAll(List.of(getGptAssistantInstructionsReview(), getPatchSetReviewPrompt()));
    if (config.getAIReviewCommitMessages()) {
      instructions.add(getReviewPromptCommitMessages());
    }
  }

  @Override
  public String getAIRequestDataPrompt() {
    return null;
  }

  private String getGptAssistantInstructionsReview() {
    return String.format(
        DEFAULT_AI_CHAT_ASSISTANT_INSTRUCTIONS_REVIEW,
        joinWithNewLine(
            getNumberedList(
                new ArrayList<>(
                    List.of(
                        DEFAULT_AI_CHAT_PROMPT_FORCE_JSON_FORMAT,
                        DEFAULT_AI_CHAT_ASSISTANT_INSTRUCTIONS_DONT_GUESS_CODE,
                        DEFAULT_AI_CHAT_ASSISTANT_INSTRUCTIONS_HISTORY)),
                RULE_NUMBER_PREFIX,
                COLON_SPACE)));
  }
}

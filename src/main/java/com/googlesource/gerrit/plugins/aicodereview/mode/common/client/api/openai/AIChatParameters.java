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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.ClientBase;
import java.util.concurrent.ThreadLocalRandom;

public class AIChatParameters extends ClientBase {
  private static boolean isCommentEvent;

  public AIChatParameters(Configuration config, boolean isCommentEvent) {
    super(config);
    AIChatParameters.isCommentEvent = isCommentEvent;
  }

  public double getGptTemperature() {
    if (isCommentEvent) {
      return retrieveTemperature(
          Configuration.KEY_AI_COMMENT_TEMPERATURE,
          Configuration.DEFAULT_AI_CHAT_COMMENT_TEMPERATURE);
    } else {
      return retrieveTemperature(
          Configuration.KEY_AI_REVIEW_TEMPERATURE,
          Configuration.DEFAULT_AI_CHAT_REVIEW_TEMPERATURE);
    }
  }

  public boolean getStreamOutput() {
    return config.getAIStreamOutput() && !isCommentEvent;
  }

  public int getRandomSeed() {
    if (retrieveUsePositiveSeedOnly()) {
      return ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
    }
    return ThreadLocalRandom.current().nextInt();
  }

  private Double retrieveTemperature(String temperatureKey, Double defaultTemperature) {
    return Double.parseDouble(config.getString(temperatureKey, String.valueOf(defaultTemperature)));
  }

  private boolean retrieveUsePositiveSeedOnly() {
    return Boolean.parseBoolean(config.getString(Configuration.KEY_AI_POSITIVE_SEED_ONLY, "false"));
  }
}

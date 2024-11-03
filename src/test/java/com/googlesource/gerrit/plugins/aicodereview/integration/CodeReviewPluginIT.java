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

package com.googlesource.gerrit.plugins.aicodereview.integration;

import static junit.framework.TestCase.assertNotNull;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.account.AccountCache;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.api.openapi.ChatAIClient;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritClient;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritClientReview;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseContent;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.review.ReviewBatch;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateless.client.prompt.AIChatPromptStateless;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@Ignore(
    "This test suite is designed to demonstrate how to test the Gerrit and GPT interfaces in a real"
        + " environment. It is not intended to be executed during the regular build process")
@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class CodeReviewPluginIT {
  @Mock private Configuration config;

  @Mock protected PluginDataHandlerProvider pluginDataHandlerProvider;

  @InjectMocks private GerritClient gerritClient;

  @InjectMocks private ChatAIClient chatGptClient;

  @InjectMocks private AccountCache accountCache;

  @Test
  public void sayHelloToGPT() throws Exception {
    ChangeSetData changeSetData =
        new ChangeSetData(1, config.getVotingMinScore(), config.getMaxReviewFileSize());
    AIChatPromptStateless AIChatPromptStateless = new AIChatPromptStateless(config, true);
    when(config.getAIDomain()).thenReturn(Configuration.OPENAI_DOMAIN);
    when(config.getAIToken()).thenReturn("Your GPT token");
    when(config.getAIModel()).thenReturn(Configuration.DEFAULT_CHATGPT_MODEL);
    when(AIChatPromptStateless.getAISystemPrompt())
        .thenReturn(AIChatPromptStateless.DEFAULT_AI_CHAT_SYSTEM_PROMPT);

    AIChatResponseContent answer = chatGptClient.ask(changeSetData, new GerritChange(""), "hello");
    log.info("answer: {}", answer);
    assertNotNull(answer);
  }

  @Test
  public void getPatchSet() throws Exception {
    when(config.getGerritUserName()).thenReturn("Your Gerrit username");

    String patchSet = gerritClient.getPatchSet("${changeId}");
    log.info("patchSet: {}", patchSet);
    assertNotNull(patchSet);
  }

  @Test
  public void setReview() throws Exception {
    ChangeSetData changeSetData =
        new ChangeSetData(1, config.getVotingMinScore(), config.getMaxReviewFileSize());
    Localizer localizer = new Localizer(config);
    when(config.getGerritUserName()).thenReturn("Your Gerrit username");

    List<ReviewBatch> reviewBatches = new ArrayList<>();
    reviewBatches.add(new ReviewBatch("message"));

    GerritClientReview gerritClientReview =
        new GerritClientReview(config, accountCache, pluginDataHandlerProvider, localizer);
    gerritClientReview.setReview(new GerritChange("Your changeId"), reviewBatches, changeSetData);
  }
}

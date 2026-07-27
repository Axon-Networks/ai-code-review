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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ClientMessageTest {
  @Mock private Configuration config;
  @Mock private Localizer localizer;

  private ChangeSetData changeSetData;

  @Before
  public void setUp() {
    changeSetData = new ChangeSetData(7, -1, 1);
    when(config.getGerritUserName()).thenReturn("review-bot");
    when(config.getGerritUserEmail()).thenReturn("review-bot@example.com");
  }

  @Test
  public void usernameMentionAddressesBot() {
    assertTrue(message().isBotAddressed("@review-bot please reconsider"));
  }

  @Test
  public void emailMentionAddressesBot() {
    assertTrue(message().isBotAddressed("@review-bot@example.com please reconsider"));
  }

  @Test
  public void commentWithoutMentionDoesNotAddressBot() {
    assertFalse(message().isBotAddressed("please reconsider"));
  }

  @Test
  public void quotedMentionDoesNotAddressBot() {
    assertFalse(message().isBotAddressed("> @review-bot please reconsider"));
  }

  @Test
  public void unquotedMentionAfterQuotedTextAddressesBot() {
    assertTrue(message().isBotAddressed("> @review-bot earlier request\n@review-bot new feedback"));
  }

  @Test
  public void configuredUsernameIsTreatedAsLiteralText() {
    when(config.getGerritUserName()).thenReturn("review.bot+ci");

    assertTrue(message().isBotAddressed("@review.bot+ci please reconsider"));
  }

  @Test
  public void mentionIsRemovedBeforeSendingFeedbackToModel() {
    ClientMessage message =
        new ClientMessage(config, changeSetData, "@review-bot   please reconsider", localizer);

    assertEquals("please reconsider", message.removeMentions().getMessage());
  }

  @Test
  public void patchSetHeadingIsRemovedBeforeSendingFeedbackToModel() {
    ClientMessage message =
        new ClientMessage(
            config,
            changeSetData,
            "Patch Set 3:\n\n(1 comment)\n\n@review-bot please reconsider",
            localizer);

    assertEquals("@review-bot please reconsider", message.removeHeadings().getMessage());
  }

  private ClientMessage message() {
    return new ClientMessage(config, changeSetData, (PluginDataHandlerProvider) null, localizer);
  }
}

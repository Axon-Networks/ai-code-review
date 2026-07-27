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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ClientCommandsTest {
  @Mock private Configuration config;
  @Mock private Localizer localizer;

  private ChangeSetData changeSetData;
  private ClientCommands commands;

  @Before
  public void setUp() {
    changeSetData = new ChangeSetData(7, -1, 1);
    commands = new ClientCommands(config, changeSetData, null, localizer);
  }

  @Test
  public void reviewCommandRequestsFullReview() {
    assertTrue(commands.parseCommands("/review", true));

    assertTrue(changeSetData.getForcedReview());
  }

  @Test
  public void reviewLastCommandRequestsLastPatchSetOnly() {
    assertTrue(commands.parseCommands("/review_last", true));

    assertTrue(changeSetData.getForcedReviewLastPatchSet());
  }

  @Test
  public void textWithoutCommandIsNotParsedAsCommand() {
    assertFalse(commands.parseCommands("please review this", true));
  }

  @Test
  public void longerWordWithReviewPrefixIsNotParsedAsCommand() {
    assertFalse(commands.parseCommands("/reviewer", true));
  }

  @Test
  public void reviewCommandCanDisableReplyFiltering() {
    commands.parseCommands("/review --filter=false", true);

    assertFalse(changeSetData.getReplyFilterEnabled());
  }

  @Test
  public void debugReviewDisablesReplyFiltering() {
    when(config.getEnableMessageDebugging()).thenReturn(true);

    commands.parseCommands("/review --debug", true);

    assertTrue(changeSetData.getDebugReviewMode());
    assertFalse(changeSetData.getReplyFilterEnabled());
  }

  @Test
  public void disabledDebugReviewProducesSystemMessage() {
    when(config.getEnableMessageDebugging()).thenReturn(false);
    when(localizer.getText("message.debugging.review.disabled"))
        .thenReturn("Debugging is disabled");

    commands.parseCommands("/review --debug", true);

    assertEquals("Debugging is disabled", changeSetData.getReviewSystemMessage());
  }

  @Test
  public void reviewCommandIsRemovedFromConversationText() {
    String textWithoutCommand =
        commands.parseRemoveCommands("please /review reconsider").trim().replaceAll("\\s+", " ");

    assertEquals("please reconsider", textWithoutCommand);
  }

  @Test
  public void directiveIsCopiedIntoReviewSettings() {
    commands.parseCommands("/directive never accept SQL injection", false);
    commands.getDirectives().copyDirectiveToSettings();

    assertEquals(java.util.Set.of("never accept SQL injection"), changeSetData.getDirectives());
  }
}

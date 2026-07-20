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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.config.PluginConfig;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GerritClientAccountTest {
  @Mock private PluginConfig globalConfig;
  @Mock private PluginConfig projectConfig;

  private GerritClientAccount client;

  @Before
  public void setUp() {
    when(globalConfig.getString(anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(1));

    Configuration config = new Configuration(null, null, globalConfig, projectConfig, null, null);
    client = new GerritClientAccount(config, null);
  }

  @Test
  public void nonEmptyTopicIsEnabledWhenDisabledFilterIsUnset() {
    assertFalse(client.isDisabledTopic("feature-topic"));
  }

  @Test
  public void topicMatchingDisabledFilterIsDisabled() {
    when(globalConfig.getString(eq("disabledTopicFilter"), anyString())).thenReturn("skip-review");

    assertTrue(client.isDisabledTopic("feature-skip-review"));
  }

  @Test
  public void topicNotMatchingDisabledFilterIsEnabled() {
    when(globalConfig.getString(eq("disabledTopicFilter"), anyString())).thenReturn("skip-review");

    assertFalse(client.isDisabledTopic("feature-topic"));
  }
}

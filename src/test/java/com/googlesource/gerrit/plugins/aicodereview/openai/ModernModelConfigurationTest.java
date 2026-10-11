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

package com.googlesource.gerrit.plugins.aicodereview.openai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.config.PluginConfig;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai.OpenAIModelValidator;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateless.client.api.UriResourceLocatorStateless;
import org.junit.Test;

public class ModernModelConfigurationTest {
  private final PluginConfig globalConfig = mock(PluginConfig.class);

  private Configuration configuration(String provider, String mode) {
    when(globalConfig.getString(anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(globalConfig.getString(eq("aiType"), anyString())).thenReturn(provider);
    when(globalConfig.getString(eq("aiMode"), anyString())).thenReturn(mode);
    return new Configuration(
        null, null, globalConfig, mock(PluginConfig.class), "bot@example.com", Account.id(1));
  }

  @Test
  public void statelessChatGptDefaultsToGpt61Sol() {
    assertEquals("gpt-6.1-sol", configuration("CHATGPT", "stateless").getAIModel());
  }

  @Test
  public void statefulChatGptRetainsLegacyDefault() {
    assertEquals("gpt-4o", configuration("CHATGPT", "stateful").getAIModel());
  }

  @Test
  public void genericProviderRetainsLegacyDefault() {
    assertEquals("gpt-4o", configuration("GENERIC", "stateless").getAIModel());
  }

  @Test
  public void modernChatGptModelsRouteToResponses() {
    Configuration config = configuration("CHATGPT", "stateless");
    for (String model :
        new String[] {
          "gpt-6.1-sol",
          "gpt-6-astra",
          "gpt-6-sol",
          "gpt-6-luna",
          "gpt-5.6-sol",
          "gpt-5.6-terra",
          "gpt-5.6-luna",
          "gpt-5",
          "gpt-5-mini",
          "gpt-5.4-2026-03-05"
        }) {
      when(globalConfig.getString(eq("aiModel"), anyString())).thenReturn(model);
      assertEquals(model, "/v1/responses", UriResourceLocatorStateless.getChatResourceUri(config));
    }
  }

  @Test
  public void olderChatGptModelsRetainChatCompletions() {
    Configuration config = configuration("CHATGPT", "stateless");
    when(globalConfig.getString(eq("aiModel"), anyString())).thenReturn("gpt-4o");
    assertEquals("/v1/chat/completions", UriResourceLocatorStateless.getChatResourceUri(config));
  }

  @Test
  public void genericModernModelNameKeepsConfiguredEndpoint() {
    Configuration config = configuration("GENERIC", "stateless");
    when(globalConfig.getString(eq("aiModel"), anyString())).thenReturn("gpt-6.1-sol");
    when(globalConfig.getString(eq("aiChatEndpoint"), anyString())).thenReturn("/custom/chat");
    assertEquals("/custom/chat", UriResourceLocatorStateless.getChatResourceUri(config));
  }

  @Test
  public void ollamaModernModelNameKeepsChatCompletions() {
    Configuration config = configuration("OLLAMA", "stateless");
    when(globalConfig.getString(eq("aiModel"), anyString())).thenReturn("gpt-6.1-sol");
    assertEquals("/v1/chat/completions", UriResourceLocatorStateless.getChatResourceUri(config));
  }

  @Test
  public void explicitModernModelRejectsLegacyStatefulModeBeforeHttp() {
    Configuration config = configuration("CHATGPT", "stateful");
    when(globalConfig.getString(eq("aiModel"), anyString())).thenReturn("gpt-6.1-sol");
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> new OpenAIModelValidator().validateConfiguredModel(config));
    assertTrue(error.getMessage(), error.getMessage().contains("aiMode=stateless"));
  }
}

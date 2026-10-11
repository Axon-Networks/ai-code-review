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

package com.googlesource.gerrit.plugins.aicodereview.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.events.PatchSetCreatedEvent;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.Message;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.listener.IEventHandlerType.PreprocessResult;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.git.GitRepoFiles;
import java.util.List;
import org.junit.Test;

public class ModernModelEventCompatibilityTest {
  @Test
  public void rejectsModernStatefulModelDuringEventBinding() {
    Configuration config = configuration("CHATGPT", "stateful", "gpt-6.1-sol");

    List<Element> elements = Elements.getElements(new GerritEventContextModule(config, event()));

    assertTrue(
        "Modern stateful models must fail before clients or storage can be constructed",
        elements.stream()
            .filter(Message.class::isInstance)
            .map(Message.class::cast)
            .anyMatch(message -> message.getMessage().contains("aiMode=stateless")));
  }

  @Test
  public void acceptsLegacyStatefulModelDuringEventBinding() {
    Configuration config = configuration("CHATGPT", "stateful", "gpt-4o");

    List<Element> elements = Elements.getElements(new GerritEventContextModule(config, event()));

    assertFalse(elements.stream().anyMatch(Message.class::isInstance));
  }

  @Test
  public void modernStatelessMergeExitsBeforeAssistantOrStorageWork() {
    GitRepoFiles gitRepoFiles = mock(GitRepoFiles.class);
    PluginDataHandlerProvider storage = mock(PluginDataHandlerProvider.class);
    EventHandlerTypeChangeMerged handler =
        new EventHandlerTypeChangeMerged(
            configuration("CHATGPT", "stateless", "gpt-6.1-sol"),
            mock(ChangeSetData.class),
            mock(GerritChange.class),
            gitRepoFiles,
            storage);

    assertEquals(PreprocessResult.EXIT, handler.preprocessEvent());
    verifyNoInteractions(gitRepoFiles, storage);
  }

  @Test
  public void legacyStatelessMergeRetainsExistingPreprocessing() {
    assertEquals(
        PreprocessResult.OK,
        mergeHandler(configuration("CHATGPT", "stateless", "gpt-4o")).preprocessEvent());
  }

  @Test
  public void legacyStatefulMergeRetainsExistingPreprocessing() {
    assertEquals(
        PreprocessResult.OK,
        mergeHandler(configuration("CHATGPT", "stateful", "gpt-4o")).preprocessEvent());
  }

  @Test
  public void genericModernModelNameRetainsExistingMergePreprocessing() {
    assertEquals(
        PreprocessResult.OK,
        mergeHandler(configuration("GENERIC", "stateless", "gpt-6.1-sol")).preprocessEvent());
  }

  private static EventHandlerTypeChangeMerged mergeHandler(Configuration config) {
    return new EventHandlerTypeChangeMerged(
        config,
        mock(ChangeSetData.class),
        mock(GerritChange.class),
        mock(GitRepoFiles.class),
        mock(PluginDataHandlerProvider.class));
  }

  private static Configuration configuration(String provider, String mode, String model) {
    PluginConfig globalConfig = mock(PluginConfig.class);
    when(globalConfig.getString(anyString(), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(globalConfig.getString(eq("aiType"), anyString())).thenReturn(provider);
    when(globalConfig.getString(eq("aiMode"), anyString())).thenReturn(mode);
    when(globalConfig.getString(eq("aiModel"), anyString())).thenReturn(model);
    return new Configuration(
        null, null, globalConfig, mock(PluginConfig.class), "bot@example.com", Account.id(1));
  }

  private static PatchSetCreatedEvent event() {
    PatchSetCreatedEvent event = mock(PatchSetCreatedEvent.class);
    when(event.getProjectNameKey()).thenReturn(Project.nameKey("project"));
    when(event.getBranchNameKey()).thenReturn(BranchNameKey.create("project", "master"));
    when(event.getChangeKey()).thenReturn(Change.key("Iabc"));
    return event;
  }
}

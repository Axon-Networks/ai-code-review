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

package com.googlesource.gerrit.plugins.aicodereview.data;

import static com.googlesource.gerrit.plugins.aicodereview.utils.FileUtils.sanitizeFilename;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import java.nio.file.Path;

@Singleton
public class PluginDataHandlerProvider extends PluginDataHandlerBaseProvider
    implements Provider<PluginDataHandler> {
  private static final String PATH_ASSISTANTS = ".assistants";

  private final String projectName;
  private final String changeKey;
  private final String assistantsWorkspace;

  @Inject
  public PluginDataHandlerProvider(
      @com.google.gerrit.extensions.annotations.PluginData Path defaultPluginDataPath,
      GerritChange change) {
    super(defaultPluginDataPath);
    projectName = sanitizeFilename(change.getProjectName());
    changeKey = change.getChangeKey().toString();
    assistantsWorkspace = projectName + PATH_ASSISTANTS;
  }

  public PluginDataHandler getGlobalScope() {
    return super.get();
  }

  public PluginDataHandler getProjectScope() {
    return super.get(projectName);
  }

  public PluginDataHandler getChangeScope() {
    return super.get(changeKey);
  }

  public PluginDataHandler getAssistantsWorkspace() {
    return super.get(assistantsWorkspace);
  }
}

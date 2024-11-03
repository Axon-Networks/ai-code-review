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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.prompt;

import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;

public class Directives {
  private final ChangeSetData changeSetData;
  private String directive;

  public Directives(ChangeSetData changeSetData) {
    this.changeSetData = changeSetData;
  }

  public void addDirective(String directive) {
    this.directive = directive.trim();
  }

  public void copyDirectiveToSettings() {
    if (directive != null && !directive.isEmpty()) {
      changeSetData.getDirectives().add(directive);
    }
  }
}

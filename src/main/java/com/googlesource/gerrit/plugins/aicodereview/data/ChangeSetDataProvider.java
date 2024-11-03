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

import com.google.gerrit.server.account.AccountCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;

public class ChangeSetDataProvider implements Provider<ChangeSetData> {
  private final int gptAccountId;
  private final Configuration config;

  @Inject
  ChangeSetDataProvider(Configuration config, AccountCache accountCache) {
    this.config = config;
    this.gptAccountId =
        accountCache.getByUsername(config.getGerritUserName()).get().account().id().get();
  }

  @Override
  public ChangeSetData get() {
    return new ChangeSetData(gptAccountId, config.getVotingMinScore(), config.getVotingMaxScore());
  }
}

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
        this.gptAccountId = accountCache.getByUsername(config.getGerritUserName()).get().account().id().get();
    }

    @Override
    public ChangeSetData get() {
        return new ChangeSetData(gptAccountId, config.getVotingMinScore(), config.getVotingMaxScore());
    }
}

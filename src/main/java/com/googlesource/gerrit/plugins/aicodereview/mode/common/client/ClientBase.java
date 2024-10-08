package com.googlesource.gerrit.plugins.aicodereview.mode.common.client;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;

public abstract class ClientBase {
    protected Configuration config;

    public ClientBase(Configuration config) {
        this.config = config;
    }
}

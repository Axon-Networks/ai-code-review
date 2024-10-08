package com.googlesource.gerrit.plugins.aicodereview.listener;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.events.Event;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.ChangeSetDataProvider;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandler;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.api.openapi.ChatAIClient;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.api.gerrit.GerritClientPatchSet;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.chatai.AIChatClientStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.gerrit.GerritClientPatchSetStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateless.client.api.chatai.AIChatClientStateless;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateless.client.api.gerrit.GerritClientPatchSetStateless;

import static com.google.inject.Scopes.SINGLETON;

public class GerritEventContextModule extends FactoryModule {
    private final Event event;
    private final Configuration config;

    public GerritEventContextModule(Configuration config, Event event) {
        this.event = event;
        this.config = config;
    }

    @Override
    protected void configure() {
        bind(ChatAIClient.class).to(getChatAIMode());
        bind(GerritClientPatchSet.class).to(getClientPatchSet());

        bind(Configuration.class).toInstance(config);
        bind(GerritChange.class).toInstance(new GerritChange(event));
        bind(ChangeSetData.class).toProvider(ChangeSetDataProvider.class).in(SINGLETON);
        bind(PluginDataHandler.class).toProvider(PluginDataHandlerProvider.class).in(Singleton.class);
    }

    private Class<? extends ChatAIClient> getChatAIMode() {
        return switch (config.getAIMode()){
            case stateful -> AIChatClientStateful.class;
            case stateless -> AIChatClientStateless.class;
        };
    }

    private Class<? extends GerritClientPatchSet> getClientPatchSet() {
        return switch (config.getAIMode()){
            case stateful -> GerritClientPatchSetStateful.class;
            case stateless -> GerritClientPatchSetStateless.class;
        };
    }
}

package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.chatgpt;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandler;
import com.googlesource.gerrit.plugins.aicodereview.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.UriResourceLocatorStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

import java.net.URI;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

@Slf4j
public class ChatGptThread {
    public static final String KEY_THREAD_ID = "threadId";

    private final ChatGptHttpClient httpClient = new ChatGptHttpClient();
    private final Configuration config;
    private final PluginDataHandler changeDataHandler;

    public ChatGptThread(
            Configuration config,
            PluginDataHandlerProvider pluginDataHandlerProvider
    ) {
        this.config = config;
        this.changeDataHandler = pluginDataHandlerProvider.getChangeScope();
    }

    public String createThread() {
        String threadId = changeDataHandler.getValue(KEY_THREAD_ID);
        if (threadId == null) {
            Request request = createThreadRequest();
            log.debug("ChatGPT Create Thread request: {}", request);

            ChatGptResponse threadResponse = getGson().fromJson(httpClient.execute(request), ChatGptResponse.class);
            log.info("Thread created: {}", threadResponse);
            threadId = threadResponse.getId();
            changeDataHandler.setValue(KEY_THREAD_ID, threadId);
        }
        else {
            log.info("Thread found for the Change Set. Thread ID: {}", threadId);
        }
        return threadId;
    }

    private Request createThreadRequest() {
        URI uri = URI.create(config.getAIDomain() + UriResourceLocatorStateful.threadsUri());
        log.debug("ChatGPT Create Thread request URI: {}", uri);

        return httpClient.createRequestFromJson(uri.toString(), config, new Object());
    }
}

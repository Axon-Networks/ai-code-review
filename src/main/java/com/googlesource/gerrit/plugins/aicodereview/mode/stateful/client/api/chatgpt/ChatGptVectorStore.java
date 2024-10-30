package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.chatgpt;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.ClientBase;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.UriResourceLocatorStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptCreateVectorStoreRequest;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptResponse;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

@Slf4j
public class ChatGptVectorStore extends ClientBase {
  public static final String KEY_VECTOR_STORE_ID = "vectorStoreId";

  private final ChatGptHttpClient httpClient = new ChatGptHttpClient();
  private final String fileId;
  private final GerritChange change;

  public ChatGptVectorStore(String fileId, Configuration config, GerritChange change) {
    super(config);
    this.fileId = fileId;
    this.change = change;
  }

  public ChatGptResponse createVectorStore() {
    Request request = vectorStoreCreateRequest();
    log.debug("ChatGPT Create Vector Store request: {}", request);

    ChatGptResponse createVectorStoreResponse =
        getGson().fromJson(httpClient.execute(request), ChatGptResponse.class);
    log.info("Vector Store created: {}", createVectorStoreResponse);

    return createVectorStoreResponse;
  }

  private Request vectorStoreCreateRequest() {
    URI uri = URI.create(config.getAIDomain() + UriResourceLocatorStateful.vectorStoreCreateUri());
    log.debug("ChatGPT Create Vector Store request URI: {}", uri);

    ChatGptCreateVectorStoreRequest requestBody =
        ChatGptCreateVectorStoreRequest.builder()
            .name(change.getProjectName())
            .fileIds(new String[] {fileId})
            .build();

    log.debug("ChatGPT Create Vector Store request body: {}", requestBody);
    return httpClient.createRequestFromJson(uri.toString(), config, requestBody);
  }
}

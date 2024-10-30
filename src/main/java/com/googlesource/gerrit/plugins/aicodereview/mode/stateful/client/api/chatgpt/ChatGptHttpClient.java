package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.chatgpt;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.http.HttpClient;
import java.util.Map;
import okhttp3.Request;

public class ChatGptHttpClient extends HttpClient {
  private static final Map<String, String> BETA_VERSION_HEADER =
      Map.of("OpenAI-Beta", "assistants=v2");

  public Request createRequestFromJson(
      String uri, Configuration configuration, Object requestObject) {
    return createRequestFromJson(uri, configuration, requestObject, BETA_VERSION_HEADER);
  }
}

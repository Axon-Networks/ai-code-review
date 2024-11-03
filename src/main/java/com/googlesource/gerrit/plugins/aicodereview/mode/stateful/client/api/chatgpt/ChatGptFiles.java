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

package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.chatgpt;

import static com.googlesource.gerrit.plugins.aicodereview.utils.GsonUtils.getGson;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.ClientBase;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.http.HttpClient;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.client.api.UriResourceLocatorStateful;
import com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt.ChatGptFilesResponse;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

@Slf4j
public class ChatGptFiles extends ClientBase {
  private final HttpClient httpClient = new HttpClient();

  public ChatGptFiles(Configuration config) {
    super(config);
  }

  public ChatGptFilesResponse uploadFiles(Path repoPath) {
    Request request = createUploadFileRequest(repoPath);
    log.debug("ChatGPT Upload Files request: {}", request);

    String response = httpClient.execute(request);
    log.debug("ChatGPT Upload Files response: {}", response);

    return getGson().fromJson(response, ChatGptFilesResponse.class);
  }

  private Request createUploadFileRequest(Path repoPath) {
    URI uri = URI.create(config.getAIDomain() + UriResourceLocatorStateful.filesCreateUri());
    log.debug("ChatGPT Upload Files request URI: {}", uri);
    File file = repoPath.toFile();
    RequestBody requestBody =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("purpose", "assistants")
            .addFormDataPart(
                "file",
                file.getName(),
                RequestBody.create(file, MediaType.parse("application/json")))
            .build();

    return httpClient.createRequest(uri.toString(), config, requestBody, null);
  }
}

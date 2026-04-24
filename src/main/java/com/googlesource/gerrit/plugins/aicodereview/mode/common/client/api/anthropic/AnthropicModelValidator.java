// Copyright (C) 2026 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.anthropic;

import com.google.common.net.HttpHeaders;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.http.HttpClientWithRetry;
import com.googlesource.gerrit.plugins.aicodereview.settings.Settings;
import com.googlesource.gerrit.plugins.aicodereview.settings.Settings.AIType;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.entity.ContentType;

/**
 * Fails fast when the configured Anthropic model ID doesn't exist or isn't accessible with the
 * configured API key. Mirrors {@link
 * com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai.OpenAIModelValidator}
 * so a misconfigured {@code aiModel} surfaces as an {@link IllegalArgumentException} before the
 * first review is attempted, instead of an opaque error in the middle of the retry loop.
 *
 * <p>Anthropic exposes {@code GET /v1/models/{model_id}} on the same base domain, authenticated
 * with the same {@code x-api-key} + {@code anthropic-version} headers used by the Messages API. A
 * 200 OK confirms the model exists and the caller is authorized for it.
 *
 * <p>Validation results are cached per (domain, model) pair so we only pay the extra round-trip the
 * first time a given configuration is exercised.
 */
@Singleton
@Slf4j
public class AnthropicModelValidator {
  private static final Set<String> VALIDATED_MODELS = ConcurrentHashMap.newKeySet();

  private final HttpClientWithRetry httpClientWithRetry = new HttpClientWithRetry();

  public void validateConfiguredModel(Configuration config) {
    if (config.getAIType() != AIType.ANTHROPIC) {
      return;
    }

    String model = config.getAIModel();
    String cacheKey = config.getAIDomain() + "|" + model;
    if (VALIDATED_MODELS.contains(cacheKey)) {
      return;
    }

    URI uri = createModelUri(config, model);
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(uri)
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
            .header(Settings.ANTHROPIC_VERSION_HEADER, config.getAnthropicVersion())
            .GET();

    NameValuePair authHeader = config.getAuthorizationHeaderInfo();
    if (authHeader != null) {
      builder.header(authHeader.getName(), authHeader.getValue());
    }

    try {
      httpClientWithRetry.execute(builder.build());
      VALIDATED_MODELS.add(cacheKey);
      log.info("Validated Anthropic aiModel '{}' via {}", model, uri);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          String.format(
              "Configured aiModel '%s' could not be validated at %s. Check that the model ID is"
                  + " correct and that the aiToken has access to it.",
              model, uri),
          e);
    }
  }

  private URI createModelUri(Configuration config, String model) {
    return URI.create(
        config.getAIDomain()
            + "/v1/models/"
            + URLEncoder.encode(model, StandardCharsets.UTF_8).replace("+", "%20"));
  }
}

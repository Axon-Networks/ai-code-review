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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.http;

import static java.net.HttpURLConnection.HTTP_OK;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class HttpClientWithRetry {
  private static final int HTTP_TOO_MANY_REQUESTS = 429;
  private final Retryer<HttpResponse<String>> retryer;

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(5)).build();

  public HttpClientWithRetry() {
    // Attention, 'com.github.rholder.retry.RetryListener' is marked unstable with @Beta annotation
    RetryListener listener =
        new RetryListener() {
          @Override
          public <V> void onRetry(Attempt<V> attempt) {
            if (attempt.hasException()) {
              log.error("Retry failed with exception: " + attempt.getExceptionCause());
            }
          }
        };

    this.retryer =
        RetryerBuilder.<HttpResponse<String>>newBuilder()
            .retryIfException()
            .retryIfResult(
                response -> {
                  if (response.statusCode() != HTTP_OK) {
                    String responseBody = response.body();
                    if (isRetryableStatus(response.statusCode())) {
                      log.error(
                          "Retry because HTTP status code is retryable. Status: {}, body: {}",
                          response.statusCode(),
                          responseBody);
                      return true;
                    }
                    log.error(
                        "Do not retry because HTTP status code is not retryable. Status: {}, body:"
                            + " {}",
                        response.statusCode(),
                        responseBody);
                  } else {
                    return false;
                  }
                  return false;
                })
            .withWaitStrategy(WaitStrategies.fixedWait(20, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(5))
            .withRetryListener(listener)
            .build();
  }

  public HttpResponse<String> execute(HttpRequest request)
      throws ExecutionException, RetryException {
    HttpResponse<String> response =
        retryer.call(() -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
    if (response.statusCode() != HTTP_OK) {
      throw new ExecutionException(
          new IOException(
              String.format(
                  "HTTP request failed with status %d and body: %s",
                  response.statusCode(), response.body())));
    }
    return response;
  }

  private boolean isRetryableStatus(int statusCode) {
    return statusCode == HTTP_TOO_MANY_REQUESTS || statusCode >= 500;
  }
}

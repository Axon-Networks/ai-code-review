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
                    log.error(
                        "Retry because HTTP status code is not 200. The status code is: "
                            + response.statusCode());
                    return true;
                  } else {
                    return false;
                  }
                })
            .withWaitStrategy(WaitStrategies.fixedWait(20, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(5))
            .withRetryListener(listener)
            .build();
  }

  public HttpResponse<String> execute(HttpRequest request)
      throws ExecutionException, RetryException {
    return retryer.call(() -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
  }
}

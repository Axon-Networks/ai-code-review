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

package com.googlesource.gerrit.plugins.aicodereview.mode.stateless.client.api;

import com.google.common.base.Strings;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;

public class UriResourceLocatorStateless {

  public static String getChatResourceUri(Configuration configuration) {
    // different resource Uri endpoints exist for each aiType we support.
    // some have backward compatible endpoints, but some do not.  This abstracts the knowledge
    // of which to call for which aiType set in the configuration settings.
    switch (configuration.getAIType()) {
      case CHATGPT:
      // ollama has an OpenAI compatible completions URI endpoint now,
      // use so that the tools_calls format is used for returned data by default saving
      // on loads more code changes in the response parsing.
      case OLLAMA:
        return chatCompletionsUri();

      case AZUREOPENAI:
        // TODO: add the endpoint information here, along with the additional query param with the
        // version
        // info - for testing use generic for now, but we will want to add the additional version
        // info as another
        // config element and then append the 2 together here.
        throw new UnsupportedOperationException("AzureOpenAi endpoint not yet supported.");
      case GENERIC:
        // generic ai development will require you to override the endpoint if it doesn't support
        // the existing
        // chatCompletionsUri.. Usually you will provide the optional chatEndpoint configuration
        // option.
        final String chatEndpoint = configuration.getChatEndpoint();
        // fallback onto chatCompletions api if nothing has been specified.
        return Strings.isNullOrEmpty(chatEndpoint) ? chatCompletionsUri() : chatEndpoint;
      default:
        throw new UnsupportedOperationException(
            "Unsupported aiType, chat resource endpoint not yet described.");
    }
  }

  public static String chatCompletionsUri() {
    return "/v1/chat/completions";
  }

  public static String ollamaChatUri() {
    return "/api/chat";
  }
}

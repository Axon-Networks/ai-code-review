package com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.api.openapi;

import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseContent;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;

public interface ChatAIClient {
  AIChatResponseContent ask(ChangeSetData changeSetData, GerritChange change, String patchSet)
      throws Exception;

  String getRequestBody();
}

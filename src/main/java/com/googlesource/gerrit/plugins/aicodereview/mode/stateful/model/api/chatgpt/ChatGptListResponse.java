package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt;

import java.util.List;
import lombok.Data;

@Data
public class ChatGptListResponse {
  private String object;
  private List<ChatGptRunStepsResponse> data;
}

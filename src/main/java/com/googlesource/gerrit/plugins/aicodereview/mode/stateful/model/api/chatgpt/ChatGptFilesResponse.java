package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChatGptFilesResponse extends ChatGptResponse {
  private String filename;
}

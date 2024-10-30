package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai;

import java.util.List;
import lombok.Data;

@Data
public class AIChatResponseUnstreamed {
  private List<MessageChoice> choices;

  @Data
  public static class MessageChoice {
    private AIChatResponseMessage message;
  }
}

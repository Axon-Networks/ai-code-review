package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChatGptThreadMessageResponse extends ChatGptResponse {
  private List<Content> content;

  @Data
  public static class Content {
    private String type;
    private Text text;

    @Data
    public static class Text {
      private String value;
    }
  }
}

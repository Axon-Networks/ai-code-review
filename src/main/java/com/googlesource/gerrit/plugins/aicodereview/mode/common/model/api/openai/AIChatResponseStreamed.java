package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;

@Data
public class AIChatResponseStreamed {
  private List<Choice> choices;

  @Data
  public static class Choice {
    protected AIChatResponseMessage delta;
    protected int index;

    @SerializedName("finish_reason")
    protected String finishReason;
  }
}

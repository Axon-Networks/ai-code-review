package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class GerritReview {
  private Map<String, List<GerritComment>> comments;
  private String message;
  private Labels labels;

  @AllArgsConstructor
  @Data
  public static class Labels {
    @SerializedName("Code-Review")
    private int codeReview;
  }
}

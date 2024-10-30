package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class GerritPatchSetFileDiff extends GerritFileDiff {
  private List<Content> content;

  @Data
  public static class Content {
    public List<String> a;
    public List<String> b;
    public List<String> ab;
  }
}

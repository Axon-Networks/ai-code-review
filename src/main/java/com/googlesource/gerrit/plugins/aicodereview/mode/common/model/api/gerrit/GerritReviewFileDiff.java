package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit;

import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.patch.diff.DiffContent;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class GerritReviewFileDiff extends GerritFileDiff {
  private List<DiffContent> content;

  public GerritReviewFileDiff(Meta metaA, Meta metaB) {
    this.metaA = metaA;
    this.metaB = metaB;
  }
}

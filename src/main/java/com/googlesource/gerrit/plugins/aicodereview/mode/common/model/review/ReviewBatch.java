package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.review;

import static com.googlesource.gerrit.plugins.aicodereview.settings.Settings.GERRIT_PATCH_SET_FILENAME;

import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritCodeRange;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ReviewBatch {
  private String id;
  @NonNull private String content;
  private String filename;
  private Integer line;
  private GerritCodeRange range;

  public String getFilename() {
    return filename == null ? GERRIT_PATCH_SET_FILENAME : filename;
  }
}

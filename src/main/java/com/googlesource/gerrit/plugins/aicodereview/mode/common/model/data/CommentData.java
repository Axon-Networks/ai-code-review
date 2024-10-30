package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data;

import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritComment;
import java.util.HashMap;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class CommentData {
  private List<GerritComment> commentProperties;
  private HashMap<String, GerritComment> commentMap;
  private HashMap<String, GerritComment> patchSetCommentMap;
}

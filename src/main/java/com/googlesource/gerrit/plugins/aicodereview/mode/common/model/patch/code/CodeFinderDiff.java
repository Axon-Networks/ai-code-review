package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.patch.code;

import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.patch.diff.DiffContent;
import java.util.TreeMap;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class CodeFinderDiff {
  private DiffContent content;
  private TreeMap<Integer, Integer> charToLineMap;
}

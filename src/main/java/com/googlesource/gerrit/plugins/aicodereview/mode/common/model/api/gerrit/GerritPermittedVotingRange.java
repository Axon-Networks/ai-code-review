package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit;

import lombok.Data;

@Data
public class GerritPermittedVotingRange {
  private int min;
  private int max;
}

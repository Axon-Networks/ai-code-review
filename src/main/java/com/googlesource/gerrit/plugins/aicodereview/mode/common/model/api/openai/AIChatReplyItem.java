package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AIChatReplyItem extends AIChatDialogueItem {
  private String reply;
  private Integer score;
  private Double relevance;
  private boolean repeated;
  private boolean conflicting;
}

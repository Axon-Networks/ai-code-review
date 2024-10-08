package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class AIChatMessageItem extends AIChatDialogueItem {
    private String request;
    private List<AIChatRequestMessage> history;
}

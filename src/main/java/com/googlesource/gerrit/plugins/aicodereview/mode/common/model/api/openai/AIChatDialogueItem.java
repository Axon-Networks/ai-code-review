package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai;

import lombok.Data;

@Data
public abstract class AIChatDialogueItem {
    protected Integer id;
    protected String filename;
    protected Integer lineNumber;
    protected String codeSnippet;
}

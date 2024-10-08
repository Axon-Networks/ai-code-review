package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
public class AIChatResponseContent {
    private List<AIChatReplyItem> replies;
    private String changeId;
    @NonNull
    private String messageContent;
}

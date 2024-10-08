package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt;

import lombok.Data;

@Data
public class ChatGptResponse {
    private String id;
    private String object;
    private String status;
}

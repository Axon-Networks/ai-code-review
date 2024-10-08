package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt;

import lombok.Data;

import java.util.List;

@Data
public class ChatGptListResponse {
    private String object;
    private List<ChatGptRunStepsResponse> data;
}

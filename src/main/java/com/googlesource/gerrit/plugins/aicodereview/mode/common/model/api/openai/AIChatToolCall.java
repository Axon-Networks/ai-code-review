package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai;

import lombok.Data;

@Data
public class AIChatToolCall {
    private String id;
    private String type;
    private Function function;

    @Data
    public static class Function {
        private String name;
        private String arguments;
    }
}

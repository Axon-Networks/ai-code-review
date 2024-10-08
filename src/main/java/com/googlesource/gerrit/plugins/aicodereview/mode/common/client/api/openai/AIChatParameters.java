package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.openai;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.ClientBase;

import java.util.concurrent.ThreadLocalRandom;

public class AIChatParameters extends ClientBase {
    private static boolean isCommentEvent;

    public AIChatParameters(Configuration config, boolean isCommentEvent) {
        super(config);
        AIChatParameters.isCommentEvent = isCommentEvent;
    }

    public double getGptTemperature() {
        if (isCommentEvent) {
            return retrieveTemperature(Configuration.KEY_AI_COMMENT_TEMPERATURE,
                    Configuration.DEFAULT_AI_CHAT_COMMENT_TEMPERATURE);
        }
       else {
            return retrieveTemperature(Configuration.KEY_AI_REVIEW_TEMPERATURE,
                    Configuration.DEFAULT_AI_CHAT_REVIEW_TEMPERATURE);
        }
    }

    public boolean getStreamOutput() {
        return config.getAIStreamOutput() && !isCommentEvent;
    }

    public int getRandomSeed() {
        return ThreadLocalRandom.current().nextInt();
    }

    private Double retrieveTemperature(String temperatureKey, Double defaultTemperature) {
        return Double.parseDouble(config.getString(temperatureKey, String.valueOf(defaultTemperature)));
    }
}

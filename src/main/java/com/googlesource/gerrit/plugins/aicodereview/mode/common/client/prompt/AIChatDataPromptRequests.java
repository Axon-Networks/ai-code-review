package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.prompt;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatMessageItem;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatRequestMessage;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.GerritClientData;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.googlesource.gerrit.plugins.aicodereview.settings.Settings.OPEN_AI_CHAT_ROLE_USER;

@Slf4j
public class AIChatDataPromptRequests extends AIChatDataPromptBase {
    protected AIChatMessageItem messageItem;
    protected List<AIChatRequestMessage> messageHistory;

    public AIChatDataPromptRequests(
            Configuration config,
            ChangeSetData changeSetData,
            GerritClientData gerritClientData,
            Localizer localizer
    ) {
        super(config, changeSetData, gerritClientData, localizer);
        commentProperties = commentData.getCommentProperties();
    }

    public void addMessageItem(int i) {
        AIChatMessageItem messageItem = getMessageItem(i);
        messageItem.setId(i);
        messageItems.add(messageItem);
    }

    protected AIChatMessageItem getMessageItem(int i) {
        messageItem = super.getMessageItem(i);
        messageHistory = aiChatHistory.retrieveHistory(commentProperties.get(i));
        AIChatRequestMessage request = extractLastUserMessageFromHistory();
        messageItem.setRequest(request.getContent());

        return messageItem;
    }

    private AIChatRequestMessage extractLastUserMessageFromHistory() {
        for (int i = messageHistory.size() - 1; i >= 0; i--) {
            if (OPEN_AI_CHAT_ROLE_USER.equals(messageHistory.get(i).getRole())) {
                return messageHistory.remove(i);
            }
        }
        throw new RuntimeException("Error extracting request from message history: no user message found in " +
                messageHistory);
    }
}

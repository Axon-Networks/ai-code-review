package com.googlesource.gerrit.plugins.aicodereview.data;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritClient;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.prompt.AIChatDataPrompt;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.gerrit.GerritPermittedVotingRange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.GerritClientData;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;

@Slf4j
public class ChangeSetDataHandler {
    public static void update(
            Configuration config,
            GerritChange change,
            GerritClient gerritClient,
            ChangeSetData changeSetData,
            Localizer localizer
    ) {
        GerritClientData gerritClientData = gerritClient.getClientData(change);
        AIChatDataPrompt AIChatDataPrompt = new AIChatDataPrompt(
                config,
                changeSetData,
                change,
                gerritClientData,
                localizer);

        changeSetData.setCommentPropertiesSize(gerritClientData.getCommentProperties().size());
        changeSetData.setDirectives(new HashSet<>());
        changeSetData.setReviewSystemMessage(null);
        changeSetData.setReviewAIDataPrompt(AIChatDataPrompt.buildPrompt());
        if (config.isVotingEnabled() && !change.getIsCommentEvent()) {
            GerritPermittedVotingRange permittedVotingRange = gerritClient.getPermittedVotingRange(change);
            if (permittedVotingRange != null) {
                if (permittedVotingRange.getMin() > config.getVotingMinScore()) {
                    log.debug("Minimum ChatGPT voting score set to {}", permittedVotingRange.getMin());
                    changeSetData.setVotingMinScore(permittedVotingRange.getMin());
                }
                if (permittedVotingRange.getMax() < config.getVotingMaxScore()) {
                    log.debug("Maximum ChatGPT voting score set to {}", permittedVotingRange.getMax());
                    changeSetData.setVotingMaxScore(permittedVotingRange.getMax());
                }
            }
        }
    }
}

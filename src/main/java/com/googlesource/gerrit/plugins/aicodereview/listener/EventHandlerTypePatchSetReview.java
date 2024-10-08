package com.googlesource.gerrit.plugins.aicodereview.listener;

import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.data.PatchSetAttribute;
import com.googlesource.gerrit.plugins.aicodereview.PatchSetReviewer;
import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.interfaces.listener.IEventHandlerType;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritClient;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static com.google.gerrit.extensions.client.ChangeKind.REWORK;

@Slf4j
public class EventHandlerTypePatchSetReview implements IEventHandlerType {
    private final Configuration config;
    private final ChangeSetData changeSetData;
    private final GerritChange change;
    private final PatchSetReviewer reviewer;
    private final GerritClient gerritClient;

    EventHandlerTypePatchSetReview(
            Configuration config,
            ChangeSetData changeSetData,
            GerritChange change,
            PatchSetReviewer reviewer,
            GerritClient gerritClient
    ) {
        this.config = config;
        this.changeSetData = changeSetData;
        this.change = change;
        this.reviewer = reviewer;
        this.gerritClient = gerritClient;
    }

    @Override
    public PreprocessResult preprocessEvent() {
        if (!isPatchSetReviewEnabled(change)) {
            log.debug("Patch Set review disabled");
            return PreprocessResult.EXIT;
        }
        gerritClient.retrievePatchSetInfo(change);

        return PreprocessResult.OK;
    }

    @Override
    public void processEvent() throws Exception {
        reviewer.review(change);
    }

    private boolean isPatchSetReviewEnabled(GerritChange change) {
        if (!config.getAIReviewPatchSet()) {
            log.debug("Disabled review function for created or updated PatchSets.");
            return false;
        }
        Optional<PatchSetAttribute> patchSetAttributeOptional = change.getPatchSetAttribute();
        if (patchSetAttributeOptional.isEmpty()) {
            log.info("PatchSetAttribute event properties not retrieved");
            return false;
        }
        PatchSetAttribute patchSetAttribute = patchSetAttributeOptional.get();
        ChangeKind patchSetEventKind = patchSetAttribute.kind;
        // The only Change kind that automatically triggers the review is REWORK. If review is forced via command, this
        // condition is bypassed
        if (patchSetEventKind != REWORK && !changeSetData.getForcedReview()) {
            log.debug("Change kind '{}' not processed", patchSetEventKind);
            return false;
        }
        String authorUsername = patchSetAttribute.author.username;
        if (gerritClient.isDisabledUser(authorUsername)) {
            log.info("Review of PatchSets from user '{}' is disabled.", authorUsername);
            return false;
        }
        if (gerritClient.isWorkInProgress(change)) {
            log.debug("Skipping Patch Set processing due to its WIP status.");
            return false;
        }
        return true;
    }
}

package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
@Data
@Slf4j
public class ChangeSetData {
    @NonNull
    private Integer gptAccountId;
    private String reviewAIDataPrompt;
    private Integer commentPropertiesSize;
    @NonNull
    private Integer votingMinScore;
    @NonNull
    private Integer votingMaxScore;

    // Command variables
    private Boolean forcedReview = false;
    private Boolean forcedReviewLastPatchSet = false;
    private Boolean replyFilterEnabled = true;
    private Boolean debugReviewMode = false;
    private Boolean hideAICodeReview = false;
    private Set<String> directives = new HashSet<>();
    private String reviewSystemMessage;

    public Boolean shouldHideAICodeReview() {
        return hideAICodeReview && !forcedReview;
    }

    public Boolean shouldRequestAICodeReview() {
        return reviewSystemMessage == null && !shouldHideAICodeReview();
    }
}

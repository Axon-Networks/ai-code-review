package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.messages;

import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.prettyStringifyObject;

import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatReplyItem;
import java.util.List;

public class DebugCodeBlocksReview extends DebugCodeBlocks {
  private static final String HIDDEN_REPLY = "hidden: %s";

  public DebugCodeBlocksReview(Localizer localizer) {
    super(localizer.getText("message.debugging.review.title"));
  }

  public String getDebugCodeBlock(AIChatReplyItem replyItem, boolean isHidden) {
    return super.getDebugCodeBlock(
        List.of(String.format(HIDDEN_REPLY, isHidden), prettyStringifyObject(replyItem)));
  }
}

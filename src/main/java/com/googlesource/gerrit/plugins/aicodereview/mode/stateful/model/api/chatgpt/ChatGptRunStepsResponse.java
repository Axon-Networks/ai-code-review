package com.googlesource.gerrit.plugins.aicodereview.mode.stateful.model.api.chatgpt;

import com.google.gson.annotations.SerializedName;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.api.openai.AIChatResponseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChatGptRunStepsResponse extends ChatGptResponse {
  @SerializedName("step_details")
  private AIChatResponseMessage stepDetails;
}

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.messages;

import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.prettyStringifyMap;

import com.googlesource.gerrit.plugins.aicodereview.localization.Localizer;
import java.util.List;
import java.util.Map;

public class DebugCodeBlocksDynamicSettings extends DebugCodeBlocks {
  public DebugCodeBlocksDynamicSettings(Localizer localizer) {
    super(localizer.getText("message.dynamic.configuration.title"));
  }

  public String getDebugCodeBlock(Map<String, String> dynamicConfig) {
    return super.getDebugCodeBlock(List.of(prettyStringifyMap(dynamicConfig)));
  }
}

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit;

import com.googlesource.gerrit.plugins.aicodereview.config.Configuration;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.ClientBase;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.patch.diff.FileDiffProcessed;
import java.util.HashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class GerritClientBase extends ClientBase {
  @Getter protected HashMap<String, FileDiffProcessed> fileDiffsProcessed = new HashMap<>();

  public GerritClientBase(Configuration config) {
    super(config);
  }
}

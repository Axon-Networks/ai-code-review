package com.googlesource.gerrit.plugins.aicodereview.interfaces.mode.common.client.api.gerrit;

import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.client.patch.diff.FileDiffProcessed;
import com.googlesource.gerrit.plugins.aicodereview.mode.common.model.data.ChangeSetData;
import java.util.HashMap;

public interface GerritClientPatchSet {
  String getPatchSet(ChangeSetData changeSetData, GerritChange gerritChange) throws Exception;

  boolean isDisabledUser(String authorUsername);

  boolean isDisabledTopic(String topic);

  void retrieveRevisionBase(GerritChange change);

  Integer getNotNullAccountId(String authorUsername);

  HashMap<String, FileDiffProcessed> getFileDiffsProcessed();

  Integer getRevisionBase();
}

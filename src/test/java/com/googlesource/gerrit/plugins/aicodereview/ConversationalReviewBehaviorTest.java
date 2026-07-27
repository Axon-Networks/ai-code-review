// Copyright (C) 2026 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.aicodereview;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * TDD backlog for conversational review behavior.
 *
 * <p>Each ignored test names one independently deliverable behavior. As production support is
 * introduced, remove {@link Ignore} from one test, implement its executable setup and assertion,
 * confirm red, and then make the smallest production change needed to turn it green.
 */
public class ConversationalReviewBehaviorTest {
  private static final String PENDING =
      "Pending implementation of the conversational review lifecycle";

  @Ignore(PENDING)
  @Test
  public void initialConversationalReviewPublishesFindingsWithoutVote() {
    Assert.fail(PENDING);
  }

  @Ignore(PENDING)
  @Test
  public void finalizationReevaluatesEveryReviewThread() {
    Assert.fail(PENDING);
  }

  @Ignore(PENDING)
  @Test
  public void withdrawnFindingDoesNotAffectFinalScore() {
    Assert.fail(PENDING);
  }

  @Ignore(PENDING)
  @Test
  public void unresolvedFindingStillAffectsFinalScore() {
    Assert.fail(PENDING);
  }

  @Ignore(PENDING)
  @Test
  public void repeatedFinalizationIsIdempotent() {
    Assert.fail(PENDING);
  }

  @Ignore(PENDING)
  @Test
  public void newerPatchSetInvalidatesPendingDecision() {
    Assert.fail(PENDING);
  }

  @Ignore(PENDING)
  @Test
  public void staleReplyCannotOverwriteNewerDecision() {
    Assert.fail(PENDING);
  }

  @Ignore(PENDING)
  @Test
  public void onlyAuthorizedReviewerCanFinalize() {
    Assert.fail(PENDING);
  }

  @Ignore(PENDING)
  @Test
  public void automaticFinalizationWaitsForConfiguredQuietPeriod() {
    Assert.fail(PENDING);
  }
}

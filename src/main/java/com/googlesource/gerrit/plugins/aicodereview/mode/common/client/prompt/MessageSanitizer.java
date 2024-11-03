// Copyright (C) 2024 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.aicodereview.mode.common.client.prompt;

import static com.googlesource.gerrit.plugins.aicodereview.utils.StringUtils.backslashEachChar;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.CODE_DELIMITER;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.CODE_DELIMITER_BEGIN;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.CODE_DELIMITER_END;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.INLINE_CODE_DELIMITER;
import static com.googlesource.gerrit.plugins.aicodereview.utils.TextUtils.parseOutOfDelimiters;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageSanitizer {
  private static final Pattern SANITIZE_BOLD_REGEX =
      Pattern.compile("(\\*{1,2}|(?<!\\w)_{1,2})(.+?)\\1", Pattern.DOTALL);
  private static final Pattern SANITIZE_NUM_REGEX =
      Pattern.compile("^(\\s*)(#+)(?=\\s)", Pattern.MULTILINE);

  public static String sanitizeAIChatMessage(String message) {
    // Sanitize code blocks (delimited by CODE_DELIMITER) by stripping out the language for syntax
    // highlighting and
    // ensuring that is preceded by two "\n" chars. Additionally, sanitize the content outside these
    // blocks.
    return parseOutOfDelimiters(
        message,
        "\\s*" + CODE_DELIMITER + "\\w*\\s*",
        MessageSanitizer::sanitizeOutsideInlineCodeBlocks,
        CODE_DELIMITER_BEGIN,
        CODE_DELIMITER_END);
  }

  private static String sanitizeOutsideInlineCodeBlocks(String message) {
    // Sanitize the content outside the inline code blocks (delimited by INLINE_CODE_DELIMITER).
    return parseOutOfDelimiters(
        message, INLINE_CODE_DELIMITER, MessageSanitizer::sanitizeGerritComment);
  }

  private static String sanitizeGerritComment(String message) {
    // Sanitize sequences of asterisks ("*") and underscores ("_") that would be incorrectly
    // interpreted as
    // delimiters of Italic and Bold text.
    Matcher boldSanitizeMatcher = SANITIZE_BOLD_REGEX.matcher(message);
    StringBuilder result = new StringBuilder();
    while (boldSanitizeMatcher.find()) {
      String slashedDelimiter = backslashEachChar(boldSanitizeMatcher.group(1));
      boldSanitizeMatcher.appendReplacement(
          result, slashedDelimiter + boldSanitizeMatcher.group(2) + slashedDelimiter);
    }
    boldSanitizeMatcher.appendTail(result);
    message = result.toString();

    // Sanitize sequences of number signs ("#") that would be incorrectly interpreted as header
    // prefixes.
    Matcher numSanitizeMatcher = SANITIZE_NUM_REGEX.matcher(message);
    message = numSanitizeMatcher.replaceAll("$1\\\\$2");

    return message;
  }
}

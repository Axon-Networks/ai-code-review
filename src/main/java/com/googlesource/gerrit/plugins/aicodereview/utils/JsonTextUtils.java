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

package com.googlesource.gerrit.plugins.aicodereview.utils;

import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonTextUtils extends TextUtils {
  private static final Pattern JSON_DELIMITED =
      Pattern.compile(
          "^.*?" + CODE_DELIMITER + "json\\s*(.*)\\s*" + CODE_DELIMITER + ".*$", Pattern.DOTALL);
  private static final Pattern JSON_OBJECT = Pattern.compile("^\\{.*\\}$", Pattern.DOTALL);

  public static String unwrapJsonCode(String text) {
    return JSON_DELIMITED.matcher(text).replaceAll("$1");
  }

  public static boolean isJsonString(String text) {
    return JSON_OBJECT.matcher(text).matches() || JSON_DELIMITED.matcher(text).matches();
  }
}

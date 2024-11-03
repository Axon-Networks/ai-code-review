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

import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StringUtils {
  public static String backslashEachChar(String body) {
    StringBuilder slashedBody = new StringBuilder();

    for (char ch : body.toCharArray()) {
      slashedBody.append("\\\\").append(ch);
    }
    return slashedBody.toString();
  }

  public static String concatenate(List<String> components) {
    return String.join("", components);
  }

  public static String capitalizeFirstLetter(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return str.substring(0, 1).toUpperCase() + str.substring(1);
  }
}

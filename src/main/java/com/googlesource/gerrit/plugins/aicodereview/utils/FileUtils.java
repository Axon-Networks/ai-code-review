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

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class FileUtils {
  public static InputStreamReader getInputStreamReader(String filename) {
    return new InputStreamReader(
        Objects.requireNonNull(FileUtils.class.getClassLoader().getResourceAsStream(filename)),
        StandardCharsets.UTF_8);
  }

  public static Path createTempFileWithContent(String prefix, String suffix, String content) {
    Path tempFile;
    try {
      tempFile = Files.createTempFile(prefix, suffix);
      Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    tempFile.toFile().deleteOnExit();

    return tempFile;
  }

  public static boolean matchesExtensionList(String filename, List<String> extensions) {
    int extIndex = filename.lastIndexOf('.');
    return extIndex >= 1 && extensions.contains(filename.substring(extIndex));
  }

  public static String sanitizeFilename(String filename) {
    // Replace any characters that are invalid in filenames (especially slashes) with a "+"
    return filename.replaceAll("[^-_a-zA-Z0-9]", "+");
  }
}

package com.googlesource.gerrit.plugins.aicodereview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.googlesource.gerrit.plugins.aicodereview.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TemporaryFileTest {
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void testCreateTempFileWithContent() throws IOException {
    // Prepare
    String prefix = "testFile";
    String suffix = ".txt";
    String content = "This is a test content";

    // Execute
    Path tempFile = FileUtils.createTempFileWithContent(prefix, suffix, content);

    // Verify the file exists
    assertTrue("Temporary file should exist", Files.exists(tempFile));

    // Read back the content
    String fileContent = Files.readString(tempFile);
    assertEquals("File content should match", content, fileContent);
  }
}

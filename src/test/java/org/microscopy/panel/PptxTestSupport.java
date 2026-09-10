package org.microscopy.panel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Shared reader for the tests that inspect generated PPTX parts. */
final class PptxTestSupport {
  private PptxTestSupport() {}

  static String slide(File file) throws Exception { return part(file, "ppt/slides/slide1.xml"); }

  static String part(File file, String name) throws Exception {
    ZipFile zip = new ZipFile(file);
    try {
      ZipEntry entry = zip.getEntry(name);
      if (entry == null) throw new IllegalArgumentException("Missing part: " + name);
      InputStream in = zip.getInputStream(entry);
      try {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
      } finally {
        in.close();
      }
    } finally {
      zip.close();
    }
  }
}

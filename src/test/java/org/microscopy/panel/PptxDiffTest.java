package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.microscopy.figure.InputImageManager;

/**
 * Reopening has to say what changed in PowerPoint and import only what maps back without
 * guessing. These edit a saved file the way PowerPoint would and check both halves.
 */
class PptxDiffTest {
  private Document document;
  private Node caption;

  private Document withCaption() {
    document = Document.empty();
    document.page(0).size = PageSize.fixed(120, 80);
    Node root = Node.container("Page", 1, 1);
    caption = Node.leaf("Caption", Content.of(TextContent.of("Original wording")));
    caption.content.text.paragraphs.get(0).runs.get(0).bold = Boolean.TRUE;
    root.add(caption, 0, 0);
    document.page(0).rootNode = root;
    document.validate();
    return document;
  }

  private File save(Path folder, String name) throws Exception {
    File file = folder.resolve(name).toFile();
    new PptxProjectWriter().save(file, document,
        new InputImageManagerSources(new InputImageManager()), new PptxProjectWriter.Options());
    return file;
  }

  @Test
  void anUntouchedFileReportsNothing(@TempDir Path folder) throws Exception {
    withCaption();
    assertTrue(new PptxDiff().compare(save(folder, "clean.pptx"), document).isEmpty());
  }

  @Test
  void editedTextIsReportedAndImported(@TempDir Path folder) throws Exception {
    withCaption();
    File saved = save(folder, "before.pptx");
    File edited = folder.resolve("after.pptx").toFile();
    rewriteSlide(saved, edited, "Original wording", "Reworded by a co-author");

    PptxDiff diff = new PptxDiff();
    PptxDiff.Report report = diff.compare(edited, document);
    assertEquals(1, report.importable().size(), report.summary());
    assertEquals(0, report.ignored().size());
    assertTrue(report.summary().contains("1 compatible change(s) imported"));

    assertEquals(1, diff.apply(edited, document));
    assertEquals("Reworded by a co-author", caption.content.text.plainText());
    assertEquals(Boolean.TRUE, caption.content.text.paragraphs.get(0).runs.get(0).bold,
        "the run's formatting is kept even though PowerPoint may have merged the runs");
  }

  @Test
  void rotationIsReportedButNotImported(@TempDir Path folder) throws Exception {
    withCaption();
    File saved = save(folder, "before.pptx");
    File edited = folder.resolve("rotated.pptx").toFile();
    rewriteSlide(saved, edited, "<a:xfrm rot=\"0\"", "<a:xfrm rot=\"780000\"");

    PptxDiff.Report report = new PptxDiff().compare(edited, document);
    assertEquals(0, report.importable().size());
    assertEquals(1, report.ignored().size());
    assertTrue(report.ignored().get(0).description.contains("rotation 13 degrees"),
        report.summary());
    assertTrue(report.summary().contains("Unsupported:"));
  }

  @Test
  void aFlippedShapeIsReported(@TempDir Path folder) throws Exception {
    withCaption();
    File saved = save(folder, "before.pptx");
    File edited = folder.resolve("flipped.pptx").toFile();
    rewriteSlide(saved, edited, "<a:xfrm rot=\"0\"", "<a:xfrm flipH=\"1\" rot=\"0\"");
    PptxDiff.Report report = new PptxDiff().compare(edited, document);
    assertEquals(1, report.ignored().size());
    assertTrue(report.ignored().get(0).description.contains("flipped"));
  }

  /** Rewrites one slide part, standing in for what PowerPoint would have written. */
  private static void rewriteSlide(File source, File destination, String from, String to)
      throws Exception {
    ZipFile zip = new ZipFile(source);
    try {
      ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(destination.toPath()));
      try {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          out.putNextEntry(new ZipEntry(entry.getName()));
          byte[] bytes = readAll(zip.getInputStream(entry));
          if (entry.getName().startsWith("ppt/slides/slide")
              && entry.getName().endsWith(".xml")) {
            String text = new String(bytes, StandardCharsets.UTF_8).replace(from, to);
            bytes = text.getBytes(StandardCharsets.UTF_8);
          }
          out.write(bytes);
          out.closeEntry();
        }
      } finally {
        out.close();
      }
    } finally {
      zip.close();
    }
  }

  private static byte[] readAll(InputStream in) throws Exception {
    try {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int count;
      while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
      return out.toByteArray();
    } finally {
      in.close();
    }
  }
}

package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.microscopy.figure.InputImageManager;

class PptxProjectTest {
  private InputImageManager inputs;

  private Document poster(Path folder, int rows, int columns) throws Exception {
    inputs = new InputImageManager();
    return PanelFixtures.poster(folder, inputs, rows, columns);
  }

  private SourceProvider sources() { return new InputImageManagerSources(inputs); }

  @Test
  void everyPartIsWellFormedAndTheSlideIsTheRealPhysicalSize(@TempDir Path folder) throws Exception {
    Document document = poster(folder, 2, 2);
    File file = folder.resolve("project.pptx").toFile();
    PptxProjectWriter.Saved saved =
        new PptxProjectWriter().save(file, document, sources(), new PptxProjectWriter.Options());
    assertEquals(1, saved.slides);
    assertTrue(saved.bytes > 0);

    List<String> names = new ArrayList<String>();
    ZipFile zip = new ZipFile(file);
    try {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        names.add(entry.getName());
        if (!entry.getName().endsWith(".xml") && !entry.getName().endsWith(".rels")) continue;
        InputStream in = zip.getInputStream(entry);
        try {
          DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        } finally {
          in.close();
        }
      }
      assertTrue(names.contains(ProjectPart.ITEM_PATH), "project part must be present");
      assertTrue(names.contains(ProjectPart.ITEM_PROPS_PATH));
      assertTrue(names.contains("docProps/custom.xml"));
      String presentation = read(zip, "ppt/presentation.xml");
      // A1 portrait is 594 x 841 mm: exactly millimetres times 36000, not a normalised long edge.
      assertTrue(presentation.contains("cx=" + quoted(Units.mmToEmu(594))), presentation);
      assertTrue(presentation.contains("cy=" + quoted(Units.mmToEmu(841))));
      String slide = read(zip, "ppt/slides/slide1.xml");
      for (Node cell : panels(document).children)
        assertTrue(slide.contains(cell.id), "shape must carry its node id: " + cell.name);
      assertTrue(slide.contains("preview 150 dpi"), "picture names state the quality");
    } finally {
      zip.close();
    }
  }

  @Test
  void reopeningUsesTheProjectPartAndSavingAgainChangesNothing(@TempDir Path folder)
      throws Exception {
    Document document = poster(folder, 2, 3);
    File file = folder.resolve("project.pptx").toFile();
    PptxProjectWriter writer = new PptxProjectWriter();
    writer.save(file, document, sources(), new PptxProjectWriter.Options());

    PptxProjectReader.Opened opened = new PptxProjectReader().open(file);
    assertTrue(opened.editable());
    assertEquals("preview 150 dpi", opened.saveQuality);
    DocumentSerializer serializer = new DocumentSerializer();
    assertEquals(serializer.toJson(document), serializer.toJson(opened.document),
        "The project part must round trip exactly");

    writer.save(file, opened.document, sources(), new PptxProjectWriter.Options());
    assertEquals(serializer.toJson(document),
        serializer.toJson(new PptxProjectReader().open(file).document),
        "Save, reopen and save again must be a fixed point");
  }

  @Test
  void mediaIsDerivedDataAndIsReusedRatherThanRerendered(@TempDir Path folder) throws Exception {
    Document document = poster(folder, 3, 3);
    File file = folder.resolve("project.pptx").toFile();
    PptxProjectWriter writer = new PptxProjectWriter();
    PptxProjectWriter.Saved first =
        writer.save(file, document, sources(), new PptxProjectWriter.Options());
    assertTrue(first.renderedMedia > 0);
    assertEquals(0, first.reusedMedia);

    PptxProjectWriter.Saved second =
        writer.save(file, document, sources(), new PptxProjectWriter.Options());
    assertEquals(0, second.renderedMedia,
        "Nothing changed, so no panel should be rendered a second time");
    assertEquals(first.mediaParts, second.reusedMedia);
  }

  @Test
  void aFileWithItsMediaDestroyedStillReopensAndRerenders(@TempDir Path folder) throws Exception {
    Document document = poster(folder, 2, 2);
    File file = folder.resolve("project.pptx").toFile();
    PptxProjectWriter writer = new PptxProjectWriter();
    writer.save(file, document, sources(), new PptxProjectWriter.Options());
    File broken = folder.resolve("broken.pptx").toFile();
    corruptMedia(file, broken.toPath());

    PptxProjectReader.Opened opened = new PptxProjectReader().open(broken);
    assertNotNull(opened.document, "The project part alone must be enough to reopen");
    PptxProjectWriter.Saved resaved =
        writer.save(broken, opened.document, sources(), new PptxProjectWriter.Options());
    assertTrue(resaved.renderedMedia > 0, "Damaged thumbnails must be rendered again from source");
  }

  @Test
  void aPptxWithoutAProjectPartExplainsItselfInsteadOfFailing(@TempDir Path folder)
      throws Exception {
    File plain = folder.resolve("plain.pptx").toFile();
    InputStream template =
        org.microscopy.figure.PptxExporter.class.getResourceAsStream("blank.pptx");
    try {
      Files.copy(template, plain.toPath());
    } finally {
      template.close();
    }
    PptxProjectReader.Opened opened = new PptxProjectReader().open(plain);
    assertTrue(!opened.editable());
    assertTrue(opened.message.contains("no project information"), opened.message);
    assertTrue(opened.message.contains("read-only"), opened.message);
  }

  @Test
  void multiplePagesBecomeMultipleSlides(@TempDir Path folder) throws Exception {
    Document document = poster(folder, 1, 2);
    Page second = new Page("Page 2");
    second.size = PagePresets.byName("A4 Portrait").toPageSize();
    second.rootNode = Node.leaf("Notes", Content.of(TextContent.of("Second page")));
    document.pages.add(second);
    document.validate();
    File file = folder.resolve("two-pages.pptx").toFile();
    PptxProjectWriter.Saved saved =
        new PptxProjectWriter().save(file, document, sources(), new PptxProjectWriter.Options());
    assertEquals(2, saved.slides);
    ZipFile zip = new ZipFile(file);
    try {
      assertNotNull(zip.getEntry("ppt/slides/slide2.xml"));
      assertTrue(read(zip, "[Content_Types].xml").contains("/ppt/slides/slide2.xml"));
      assertTrue(read(zip, "ppt/_rels/presentation.xml.rels").contains("slide2.xml"));
      assertTrue(read(zip, "ppt/presentation.xml").contains("rIdSlide2"));
      assertTrue(read(zip, "ppt/slides/slide2.xml").contains("Second page"));
    } finally {
      zip.close();
    }
    assertEquals(2, new PptxProjectReader().open(file).slides);
  }

  @Test
  void savingStaysFastEnoughToPressOften(@TempDir Path folder) throws Exception {
    Document document = poster(folder, 6, 5);
    assertEquals(30, panels(document).children.size());
    File file = folder.resolve("thirty.pptx").toFile();
    long started = System.nanoTime();
    PptxProjectWriter.Saved saved =
        new PptxProjectWriter().save(file, document, sources(), new PptxProjectWriter.Options());
    double seconds = (System.nanoTime() - started) / 1e9;
    // The design goal is well under a second; this bound is loose enough for a slow machine but
    // still fails if saving ever turns into a full resolution export.
    assertTrue(seconds < 15, "Saving 30 panels took " + seconds + " s");
    assertTrue(saved.bytes < 30000000L, "Preview quality keeps the file small: " + saved.bytes);
  }

  private static String quoted(long value) { return "\"" + value + "\""; }

  private static Node panels(Document document) {
    for (Node child : document.page(0).rootNode.children)
      if ("Panels".equals(child.name)) return child;
    throw new AssertionError("No panels container");
  }

  private static String read(ZipFile zip, String name) throws Exception {
    ZipEntry entry = zip.getEntry(name);
    assertNotNull(entry, "Missing part: " + name);
    InputStream in = zip.getInputStream(entry);
    try {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int count;
      while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
      return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    } finally {
      in.close();
    }
  }

  /** Rewrites the package with every picture replaced by rubbish. */
  private static void corruptMedia(File source, Path destination) throws Exception {
    ZipFile zip = new ZipFile(source);
    try {
      java.util.zip.ZipOutputStream out =
          new java.util.zip.ZipOutputStream(Files.newOutputStream(destination));
      try {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          out.putNextEntry(new ZipEntry(entry.getName()));
          if (entry.getName().startsWith("ppt/media/")) {
            out.write("not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8));
          } else {
            InputStream in = zip.getInputStream(entry);
            try {
              byte[] buffer = new byte[8192];
              int count;
              while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            } finally {
              in.close();
            }
          }
          out.closeEntry();
        }
      } finally {
        out.close();
      }
    } finally {
      zip.close();
    }
  }
}

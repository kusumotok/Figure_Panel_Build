package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.microscopy.figure.InputImageManager;

/**
 * PowerPoint cannot hold an L-shaped text frame, so the saved project splits one into editable
 * boxes. These pin down that the split keeps all the words and marks the parts.
 */
class RegionTextExportTest {
  private static final String BODY =
      "Sections were acquired at 0.065 micrometres per pixel and merged without further "
          + "processing. Scale bars are ten micrometres. Every panel in a row shares one "
          + "brightness range so intensities stay comparable, and no channel was adjusted after "
          + "acquisition. Counts are the mean of three biological replicates, and the same "
          + "settings were reused for the paired control throughout the experiment.";

  private Document lShapedCaption() {
    Document document = Document.empty();
    Page page = document.page(0);
    page.size = PageSize.fixed(180, 120);
    page.margins = Margins.uniform(10);
    Node root = Node.container("Figure", 2, 2);
    root.layout.columns.set(0, SizeExpr.fill());
    root.layout.columns.set(1, SizeExpr.fill());
    // A short top band forces the caption to continue into the foot of the L.
    root.layout.rows.set(0, SizeExpr.fixed(18));
    root.layout.rows.set(1, SizeExpr.fixed(40));
    root.layout.columnGapMm = 5;
    root.layout.rowGapMm = 5;

    Node block = Node.leaf("Block", Content.of(new ShapeContent()));
    block.size.width = SizeExpr.fill();
    block.size.height = SizeExpr.fill();
    root.add(block, 0, 1);

    TextContent text = TextContent.of(BODY);
    text.paragraphs.get(0).runs.get(0).fontSizePt = 8.0;
    text.flowRegion = new FlowRegion();
    text.flowRegion.cells.add(new FlowRegion.Cell(0, 0));
    text.flowRegion.cells.add(new FlowRegion.Cell(1, 0));
    text.flowRegion.cells.add(new FlowRegion.Cell(1, 1));
    root.add(Node.leaf("Caption", Content.of(text)), 0, 0);
    page.rootNode = root;
    document.validate();
    return document;
  }

  @Test
  void theRegionBecomesOneEditableBoxPerRectangle(@TempDir Path folder) throws Exception {
    Document document = lShapedCaption();
    File file = folder.resolve("region.pptx").toFile();
    new PptxProjectWriter().save(file, document,
        new InputImageManagerSources(new InputImageManager()), new PptxProjectWriter.Options());
    String slide = read(file, "ppt/slides/slide1.xml");

    int parts = 0, at = 0;
    while ((at = slide.indexOf("Caption#part", at + 1)) > 0) parts++;
    assertTrue(parts >= 2, "an L-shape needs at least two boxes, found " + parts);
    assertTrue(!slide.contains("name=\"Caption\""),
        "the whole region must not also be written as one box");
  }

  @Test
  void everyWordSurvivesTheSplit(@TempDir Path folder) throws Exception {
    Document document = lShapedCaption();
    File file = folder.resolve("region.pptx").toFile();
    new PptxProjectWriter().save(file, document,
        new InputImageManagerSources(new InputImageManager()), new PptxProjectWriter.Options());
    String slide = read(file, "ppt/slides/slide1.xml");

    StringBuilder written = new StringBuilder();
    int at = 0;
    while ((at = slide.indexOf("<a:t xml:space=\"preserve\">", at)) >= 0) {
      int start = at + "<a:t xml:space=\"preserve\">".length();
      int end = slide.indexOf("</a:t>", start);
      written.append(slide, start, end);
      at = end;
    }
    assertEquals(compact(BODY), compact(written.toString()),
        "the split must not drop or duplicate characters");
  }

  @Test
  void theProjectPartStillHoldsTheRegionItself(@TempDir Path folder) throws Exception {
    Document document = lShapedCaption();
    File file = folder.resolve("region.pptx").toFile();
    new PptxProjectWriter().save(file, document,
        new InputImageManagerSources(new InputImageManager()), new PptxProjectWriter.Options());
    Document reopened = new PptxProjectReader().open(file).document;
    Node caption = null;
    for (Node child : reopened.page(0).rootNode.children)
      if ("Caption".equals(child.name)) caption = child;
    assertTrue(caption != null);
    assertEquals(3, caption.content.text.flowRegion.cells.size(),
        "the region survives the round trip even though the shapes are rectangles");
  }

  private static String read(File file, String entryName) throws Exception {
    ZipFile zip = new ZipFile(file);
    try {
      ZipEntry entry = zip.getEntry(entryName);
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

  /** Everything except whitespace, so wrapping differences do not matter. */
  private static String compact(String text) {
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++)
      if (!Character.isWhitespace(text.charAt(i))) out.append(text.charAt(i));
    return out.toString();
  }
}

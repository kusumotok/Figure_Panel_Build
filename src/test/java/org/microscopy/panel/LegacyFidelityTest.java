package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.microscopy.figure.ChannelConfig;
import org.microscopy.figure.ConditionConfig;
import org.microscopy.figure.DisplayChannel;
import org.microscopy.figure.FigureConfiguration;
import org.microscopy.figure.InputImageManager;
import org.microscopy.figure.LabelRenderer;
import org.microscopy.figure.PanelLayoutEngine;

/**
 * The regression that keeps the new pipeline honest: an imported figure must reproduce what the
 * original engine drew, cell for cell and pixel for pixel.
 */
class LegacyFidelityTest {
  private static final int CELL_WIDTH = 256, CELL_HEIGHT = 192, GAP = 5;

  private InputImageManager inputs;

  private FigureConfiguration figure(Path folder) throws Exception {
    org.microscopy.figure.TestImageGenerator.generate(folder);
    inputs = new InputImageManager();
    FigureConfiguration config = new FigureConfiguration();
    for (String name : new String[] {"Image A", "Image B", "Image C"})
      config.conditions.add(
          new ConditionConfig(name, inputs.load(folder.resolve(name + ".tif").toFile()).id));
    config.channels.add(new ChannelConfig(1, "Green", ChannelConfig.Lut.Green));
    config.channels.add(new ChannelConfig(2, "Red", ChannelConfig.Lut.Red));
    config.channels.add(new ChannelConfig(3, "Blue", ChannelConfig.Lut.Blue));
    config.displayChannels.add(new DisplayChannel("Green", false, 1));
    config.displayChannels.add(new DisplayChannel("Red", false, 2));
    config.displayChannels.add(new DisplayChannel("Merge", true, 1, 2));
    config.labels.showRows = true;
    config.labels.showColumns = true;
    config.horizontalGap = GAP;
    config.verticalGap = GAP;
    config.scaleBar.show = true;
    return config;
  }

  @Test
  void everyImportedCellMatchesTheLegacyRenderPixelForPixel(@TempDir Path folder) throws Exception {
    FigureConfiguration config = figure(folder);
    BufferedImage legacy = new PanelLayoutEngine().render(config, inputs);
    LegacyImporter.Imported imported = new LegacyImporter().convert(config, inputs, 180);
    LabelRenderer labels = new LabelRenderer();
    int x0 = labels.rowBand(config.labels), y0 = labels.columnBand(config.labels);

    Node panels = child(imported.document.page(0).rootNode, "Panels");
    ScientificImageRenderer renderer = new ScientificImageRenderer();
    assertEquals(9, panels.children.size());
    for (Node cell : panels.children) {
      ScientificImageContent content = cell.content.scientificImage;
      BufferedImage rendered =
          renderer.render(content, inputs.get(content.assetId), CELL_WIDTH, CELL_HEIGHT);
      int left = x0 + cell.placement.column * (CELL_WIDTH + GAP);
      int top = y0 + cell.placement.row * (CELL_HEIGHT + GAP);
      assertRegionEquals(legacy, left, top, rendered, cell.name);
    }
  }

  @Test
  void theRasterisedPageKeepsTheLegacyGeometry(@TempDir Path folder) throws Exception {
    FigureConfiguration config = figure(folder);
    LegacyImporter.Imported imported = new LegacyImporter().convert(config, inputs, 180);
    // One output pixel per original pixel, so millimetre rounding is directly observable.
    double dpi = Units.MM_PER_INCH / imported.mmPerPx;
    Page page = imported.document.page(0);
    LayoutResult layout = new LayoutEngine().layout(imported.document, page);
    RenderTarget target = new RenderTarget(dpi, RenderTarget.Background.BLACK);
    int[] size = new DocumentRasterizer(new InputImageManagerSources(inputs)).sizePx(layout, target);
    assertEquals(imported.legacyWidthPx, size[0]);
    assertEquals(imported.legacyHeightPx, size[1]);

    LabelRenderer labels = new LabelRenderer();
    int x0 = labels.rowBand(config.labels), y0 = labels.columnBand(config.labels);
    for (Node cell : child(page.rootNode, "Panels").children) {
      int[] px = layout.of(cell.id).toPx(dpi);
      assertEquals(x0 + cell.placement.column * (CELL_WIDTH + GAP), px[0], cell.name + " x");
      assertEquals(y0 + cell.placement.row * (CELL_HEIGHT + GAP), px[1], cell.name + " y");
      assertEquals(CELL_WIDTH, px[2], cell.name + " width");
      assertEquals(CELL_HEIGHT, px[3], cell.name + " height");
    }
  }

  @Test
  void bandHeightDoesNotChangeTheOutput(@TempDir Path folder) throws Exception {
    FigureConfiguration config = figure(folder);
    LegacyImporter.Imported imported = new LegacyImporter().convert(config, inputs, 180);
    Page page = imported.document.page(0);
    LayoutResult layout = new LayoutEngine().layout(imported.document, page);
    RenderTarget target =
        new RenderTarget(Units.MM_PER_INCH / imported.mmPerPx, RenderTarget.Background.BLACK);
    DocumentRasterizer rasterizer = new DocumentRasterizer(new InputImageManagerSources(inputs));
    BufferedImage whole = rasterizer.rasterizeWhole(imported.document, page, layout, target);
    BufferedImage banded = collect(rasterizer, imported.document, page, layout, target, 7);
    assertRegionEquals(whole, 0, 0, banded, "seven row bands");
  }

  @Test
  void streamedPngReadsBackIdentically(@TempDir Path folder) throws Exception {
    FigureConfiguration config = figure(folder);
    LegacyImporter.Imported imported = new LegacyImporter().convert(config, inputs, 180);
    Page page = imported.document.page(0);
    LayoutResult layout = new LayoutEngine().layout(imported.document, page);
    RenderTarget target =
        new RenderTarget(Units.MM_PER_INCH / imported.mmPerPx, RenderTarget.Background.BLACK);
    DocumentRasterizer rasterizer = new DocumentRasterizer(new InputImageManagerSources(inputs));
    File png = folder.resolve("figure.png").toFile();
    rasterizer.writePng(png, imported.document, page, layout, target);
    assertTrue(png.length() > 0);
    BufferedImage read = ImageIO.read(png);
    assertEquals(imported.legacyWidthPx, read.getWidth());
    assertEquals(imported.legacyHeightPx, read.getHeight());
    assertRegionEquals(rasterizer.rasterizeWhole(imported.document, page, layout, target), 0, 0,
        read, "streamed PNG");
  }

  @Test
  void resolutionReportNamesThePicturesThatAreTooCoarse(@TempDir Path folder) throws Exception {
    FigureConfiguration config = figure(folder);
    LegacyImporter.Imported imported = new LegacyImporter().convert(config, inputs, 180);
    Page page = imported.document.page(0);
    LayoutResult layout = new LayoutEngine().layout(imported.document, page);
    ResolutionReport report = ResolutionReport.of(imported.document, page, layout);
    assertEquals(9, report.entries().size());
    assertTrue(report.hasWarnings(), report.summary());
    assertEquals(9, report.below(ResolutionReport.Level.VERY_LOW).size(),
        "256 px across 56 mm is about 116 dpi");
    // Fitting to native pixels is the escape hatch the export dialog offers.
    assertEquals(21.67, ResolutionReport.fitToNativeWidthMm(256, 300), 0.01);
  }

  private static Node child(Node parent, String name) {
    for (Node node : parent.children) if (name.equals(node.name)) return node;
    throw new AssertionError("No child named " + name);
  }

  private static BufferedImage collect(DocumentRasterizer rasterizer, Document document, Page page,
      LayoutResult layout, RenderTarget target, int bandRows) throws Exception {
    int[] size = rasterizer.sizePx(layout, target);
    final BufferedImage out = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
    rasterizer.rasterize(document, page, layout, target, bandRows,
        new DocumentRasterizer.BandSink() {
          public void band(BufferedImage band, int topPx) {
            java.awt.Graphics2D g = out.createGraphics();
            try {
              g.drawImage(band, 0, topPx, null);
            } finally {
              g.dispose();
            }
          }
        });
    return out;
  }

  private static void assertRegionEquals(BufferedImage expected, int left, int top,
      BufferedImage actual, String what) {
    for (int y = 0; y < actual.getHeight(); y++)
      for (int x = 0; x < actual.getWidth(); x++) {
        int a = expected.getRGB(left + x, top + y) & 0xffffff;
        int b = actual.getRGB(x, y) & 0xffffff;
        if (a != b)
          throw new AssertionError(String.format(
              "%s differs at (%d, %d): expected %06X but was %06X", what, x, y, a, b));
      }
  }
}

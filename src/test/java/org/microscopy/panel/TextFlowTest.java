package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextFlowTest {
  private static final String BODY =
      "Confocal sections were acquired at 0.065 micrometres per pixel and merged without any "
          + "further processing. Scale bars are ten micrometres throughout, and every panel of "
          + "a row shares one brightness range so intensities stay comparable across fields.";

  private Graphics2D graphics() {
    return new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB).createGraphics();
  }

  private static TextContent body(double sizePt) {
    TextContent content = TextContent.of(BODY);
    content.paragraphs.get(0).runs.get(0).fontSizePt = sizePt;
    return content;
  }

  /** A 2 by 2 grid of 60 mm cells with no gap, so cell n starts at 60n. */
  private static LayoutResult.Tracks grid() {
    double[] start = {0, 60};
    double[] size = {60, 60};
    return new LayoutResult.Tracks(start, size, start.clone(), size.clone(), 0, 0);
  }

  private static List<TextRenderer.FlowShape> shapes(FlowRegion region, double dpi) {
    List<TextRenderer.FlowShape> shapes = new ArrayList<TextRenderer.FlowShape>();
    for (FlowRegionGeometry.Island island
        : FlowRegionGeometry.ordered(FlowRegionGeometry.islands(grid(), region), region))
      shapes.add(TextRenderer.island(island, 0, 0, dpi));
    return shapes;
  }

  @Test
  void textWidensWhereAnLShapedRegionWidens() {
    // Text down the left column and along the bottom row; the figure sits at top right.
    FlowRegion region = new FlowRegion();
    region.cells.add(new FlowRegion.Cell(0, 0));
    region.cells.add(new FlowRegion.Cell(1, 0));
    region.cells.add(new FlowRegion.Cell(1, 1));
    Graphics2D g = graphics();
    try {
      TextContent long3 = body(9);
      long3.paragraphs.add(Paragraph.of(BODY));
      long3.paragraphs.add(Paragraph.of(BODY));
      for (Paragraph paragraph : long3.paragraphs) paragraph.runs.get(0).fontSizePt = 9.0;
      TextRenderer.Flowed flowed =
          new TextRenderer().flow(g, long3, shapes(region, 96), 96, Align.START);
      assertTrue(flowed.lines.size() > 4, "expected several lines: " + flowed.lines.size());
      double narrow = 0, wide = 0;
      for (TextRenderer.Line line : flowed.lines) {
        double right = line.x + line.layout.getAdvance();
        if (line.baseline < Units.mmToPx(60, 96)) narrow = Math.max(narrow, right);
        else wide = Math.max(wide, right);
      }
      // The top half is one 60 mm cell wide; the bottom row spans both.
      assertTrue(narrow <= Units.mmToPx(60, 96) + 1, "top stays in the left column: " + narrow);
      assertTrue(wide > Units.mmToPx(60, 96), "the foot uses the full width: " + wide);
    } finally {
      g.dispose();
    }
  }

  @Test
  void aLineSpanningTwoIslandsIsFilledTwice() {
    // Two cells on the same row with a hole between them: one line, two runs.
    LayoutResult.Tracks tracks = new LayoutResult.Tracks(
        new double[] {0, 60, 120}, new double[] {50, 50, 50},
        new double[] {0}, new double[] {60}, 10, 0);
    FlowRegion region = new FlowRegion();
    region.cells.add(new FlowRegion.Cell(0, 0));
    region.cells.add(new FlowRegion.Cell(0, 1));
    FlowRegionGeometry.Island island = FlowRegionGeometry.islands(tracks, region).get(0);
    List<double[]> runs = island.runsAt(5, 4);
    assertEquals(2, runs.size(), "the gap splits the line into two runs");

    Graphics2D g = graphics();
    try {
      List<TextRenderer.FlowShape> shapes = new ArrayList<TextRenderer.FlowShape>();
      shapes.add(TextRenderer.island(island, 0, 0, 96));
      TextRenderer.Flowed flowed =
          new TextRenderer().flow(g, body(8), shapes, 96, Align.START);
      int onFirstLine = 0;
      float firstBaseline = flowed.lines.get(0).baseline;
      for (TextRenderer.Line line : flowed.lines)
        if (Math.abs(line.baseline - firstBaseline) < 0.5f) onFirstLine++;
      assertEquals(2, onFirstLine, "both runs of the first band were filled");
    } finally {
      g.dispose();
    }
  }

  @Test
  void overflowIsReportedRatherThanHidden() {
    Graphics2D g = graphics();
    try {
      TextContent content = body(14);
      TextRenderer.Flowed flowed = new TextRenderer()
          .flow(g, content, Units.mmToPx(40, 96), Units.mmToPx(10, 96), 96, Align.START);
      assertTrue(flowed.overflowed, "a long paragraph cannot fit 40 by 10 mm at 14 pt");
      assertTrue(flowed.shortfall > 0, "the shortfall says how much more room is needed");
      assertEquals(1, flowed.shrink, 1e-9, "Warn never shrinks");
    } finally {
      g.dispose();
    }
  }

  @Test
  void autoShrinkFitsTheTextAndStopsAtSeventyPercent() {
    Graphics2D g = graphics();
    try {
      TextContent content = body(11);
      content.overflow = TextContent.Overflow.AUTO_SHRINK;
      TextRenderer.Flowed flowed = new TextRenderer()
          .flow(g, content, Units.mmToPx(60, 96), Units.mmToPx(30, 96), 96, Align.START);
      assertTrue(!flowed.overflowed, "auto shrink should have made it fit");
      assertTrue(flowed.shrink < 1 && flowed.shrink >= 0.7, "shrink was " + flowed.shrink);

      TextContent hopeless = body(30);
      hopeless.overflow = TextContent.Overflow.AUTO_SHRINK;
      TextRenderer.Flowed given = new TextRenderer()
          .flow(g, hopeless, Units.mmToPx(30, 96), Units.mmToPx(8, 96), 96, Align.START);
      assertTrue(given.overflowed, "past seventy percent it reports instead of shrinking further");
      assertEquals(1, given.shrink, 1e-9);
    } finally {
      g.dispose();
    }
  }

  @Test
  void clipStopsAtTheEdgeInsteadOfMeasuringOn() {
    Graphics2D g = graphics();
    try {
      TextContent warn = body(12);
      TextContent clip = body(12);
      clip.overflow = TextContent.Overflow.CLIP;
      TextRenderer renderer = new TextRenderer();
      float width = Units.mmToPx(50, 96), height = Units.mmToPx(12, 96);
      TextRenderer.Flowed warned = renderer.flow(g, warn, width, height, 96, Align.START);
      TextRenderer.Flowed clipped = renderer.flow(g, clip, width, height, 96, Align.START);
      assertTrue(warned.overflowed && clipped.overflowed);
      assertTrue(clipped.usedHeight <= warned.usedHeight,
          "clipping stops early: " + clipped.usedHeight + " vs " + warned.usedHeight);
      for (TextRenderer.Line line : clipped.lines)
        assertTrue(line.baseline <= height + 2, "no line is drawn past the edge");
    } finally {
      g.dispose();
    }
  }
}

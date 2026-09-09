package org.microscopy.panel;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays out rich text with AttributedString and LineBreakMeasurer, so run overrides, CJK line
 * breaking and per-line widths all come from the platform rather than a hand-rolled wrapper.
 *
 * <p>A rectangle and an L-shaped region go through the same code: both are a list of shapes, and
 * each line asks its shape which horizontal runs are free at that height. A line that finds two
 * runs is filled twice, which is what makes text wrap around a figure rather than over it.
 */
public class TextRenderer {
  /** Somewhere text may be placed, in pixels relative to the node. */
  public interface FlowShape {
    double top();

    double bottom();

    /** Free horizontal runs for a line occupying the band, left to right. */
    List<double[]> runsAt(double top, double height);
  }

  public static final class Line {
    public final TextLayout layout;
    public final float x, baseline;

    Line(TextLayout layout, float x, float baseline) {
      this.layout = layout; this.x = x; this.baseline = baseline;
    }
  }

  public static final class Flowed {
    public final List<Line> lines = new ArrayList<Line>();
    public float usedHeight;
    /** True when the text needed more room than the shapes allowed. */
    public boolean overflowed;
    /** Extra height the last shape would have needed to hold everything. */
    public float shortfall;
    /** Font scale applied to make it fit, 1 when nothing was shrunk. */
    public double shrink = 1;
  }

  public static FlowShape rectangle(final double widthPx, final double heightPx) {
    return new FlowShape() {
      public double top() { return 0; }

      public double bottom() { return heightPx; }

      public List<double[]> runsAt(double top, double height) {
        return Collections.singletonList(new double[] {0, widthPx});
      }
    };
  }

  /** An island of a flow region, converted to pixels relative to the node's own rectangle. */
  public static FlowShape island(final FlowRegionGeometry.Island island, final double originXmm,
      final double originYmm, final double dpi) {
    final RectMm bounds = island.bounds();
    return new FlowShape() {
      public double top() { return px(bounds.y - originYmm); }

      public double bottom() { return px(bounds.bottom() - originYmm); }

      public List<double[]> runsAt(double top, double height) {
        List<double[]> runs = new ArrayList<double[]>();
        for (double[] run : island.runsAt(mm(top) + originYmm, mm(height)))
          runs.add(new double[] {px(run[0] - originXmm), px(run[1] - originXmm)});
        return runs;
      }

      private double px(double millimetres) { return millimetres / Units.MM_PER_INCH * dpi; }

      private double mm(double pixels) { return pixels / dpi * Units.MM_PER_INCH; }
    };
  }

  public Flowed flow(Graphics2D g, TextContent content, float widthPx, float heightPx,
      double dpi, Align defaultAlign) {
    return flow(g, content, Collections.singletonList(rectangle(widthPx, heightPx)), dpi,
        defaultAlign);
  }

  /**
   * Flows the text through the shapes in order, honouring the overflow policy.
   *
   * <p>Warn is the default and simply reports: silently shrinking type would break a poster's
   * typographic consistency, which is the whole reason the sizes were chosen.
   */
  public Flowed flow(Graphics2D g, TextContent content, List<FlowShape> shapes, double dpi,
      Align defaultAlign) {
    Flowed first = fill(g, content, shapes, dpi, defaultAlign, 1);
    if (!first.overflowed || content.overflow != TextContent.Overflow.AUTO_SHRINK) return first;
    // Shrinking stops at seventy percent; past that the text is no longer the size it was set in
    // and the honest answer is the warning.
    for (double scale = 0.95; scale >= 0.7; scale -= 0.05) {
      Flowed attempt = fill(g, content, shapes, dpi, defaultAlign, scale);
      if (!attempt.overflowed) {
        attempt.shrink = scale;
        return attempt;
      }
    }
    return first;
  }

  private Flowed fill(Graphics2D g, TextContent content, List<FlowShape> shapes, double dpi,
      Align defaultAlign, double scale) {
    Flowed flowed = new Flowed();
    if (shapes.isEmpty()) return flowed;
    int shapeIndex = 0;
    double y = shapes.get(0).top();
    boolean clip = content.overflow == TextContent.Overflow.CLIP;

    for (Paragraph paragraph : content.paragraphs) {
      Align align = paragraph.align == null ? defaultAlign : paragraph.align;
      double sizePt = largestFontPt(paragraph) * scale;
      double lineHeight = ptToPx(sizePt, dpi) * 1.2;
      if (paragraph.spaceBeforePt != null) y += ptToPx(paragraph.spaceBeforePt * scale, dpi);
      String text = paragraph.plainText();
      if (text.isEmpty()) {
        y += lineHeight;
        continue;
      }
      AttributedCharacterIterator iterator = attributed(paragraph, dpi, scale).getIterator();
      LineBreakMeasurer measurer = new LineBreakMeasurer(iterator, g.getFontRenderContext());
      while (measurer.getPosition() < iterator.getEndIndex()) {
        FlowShape shape = shapes.get(shapeIndex);
        if (y + lineHeight > shape.bottom() + 1e-6) {
          if (shapeIndex + 1 < shapes.size()) {
            shapeIndex++;
            y = shapes.get(shapeIndex).top();
            continue;
          }
          flowed.overflowed = true;
          flowed.shortfall = (float) Math.max(flowed.shortfall, y + lineHeight - shape.bottom());
          if (clip) return finish(flowed, y);
          // Keep measuring so the shortfall reflects everything that did not fit.
          y += lineHeight;
          measurer.nextLayout(Float.MAX_VALUE);
          continue;
        }
        List<double[]> runs = shape.runsAt(y, lineHeight);
        if (runs.isEmpty()) {
          y += Math.max(1, lineHeight / 4);
          continue;
        }
        double tallest = 0;
        for (double[] run : runs) {
          if (measurer.getPosition() >= iterator.getEndIndex()) break;
          double available = Math.max(1, run[1] - run[0]);
          TextLayout layout = measurer.nextLayout((float) available);
          if (layout == null) break;
          double offset = 0;
          if (align == Align.CENTER) offset = (available - layout.getAdvance()) / 2;
          else if (align == Align.END) offset = available - layout.getAdvance();
          flowed.lines.add(new Line(layout, (float) (run[0] + offset),
              (float) (y + layout.getAscent())));
          tallest = Math.max(tallest,
              layout.getAscent() + layout.getDescent() + layout.getLeading());
        }
        y += tallest > 0 ? tallest : lineHeight;
      }
      if (paragraph.spaceAfterPt != null) y += ptToPx(paragraph.spaceAfterPt * scale, dpi);
    }
    return finish(flowed, y);
  }

  private static Flowed finish(Flowed flowed, double y) {
    flowed.usedHeight = (float) y;
    return flowed;
  }

  public void draw(Graphics2D g, Flowed flowed) {
    for (Line line : flowed.lines) line.layout.draw(g, line.x, line.baseline);
  }

  private static double largestFontPt(Paragraph paragraph) {
    double size = 12;
    for (Run run : paragraph.runs) if (run.fontSizePt != null) size = Math.max(size, run.fontSizePt);
    return size;
  }

  static float ptToPx(double pt, double dpi) {
    return (float) (Units.ptToMm(pt) / Units.MM_PER_INCH * dpi);
  }

  /** Every run override becomes a text attribute over its character range. */
  private AttributedString attributed(Paragraph paragraph, double dpi, double scale) {
    AttributedString attributed = new AttributedString(paragraph.plainText());
    int start = 0;
    for (Run run : paragraph.runs) {
      int end = start + run.text.length();
      if (end > start) apply(attributed, run, start, end, dpi, scale);
      start = end;
    }
    return attributed;
  }

  private void apply(AttributedString attributed, Run run, int start, int end, double dpi,
      double scale) {
    Map<TextAttribute, Object> attributes = new HashMap<TextAttribute, Object>();
    attributes.put(TextAttribute.FAMILY, run.latinFamily == null ? Font.SANS_SERIF : run.latinFamily);
    attributes.put(TextAttribute.SIZE,
        ptToPx((run.fontSizePt == null ? 12 : run.fontSizePt) * scale, dpi));
    if (Boolean.TRUE.equals(run.bold)) attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
    if (Boolean.TRUE.equals(run.italic)) attributes.put(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE);
    if (Boolean.TRUE.equals(run.underline))
      attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
    if (Boolean.TRUE.equals(run.superscript))
      attributes.put(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUPER);
    if (Boolean.TRUE.equals(run.subscript))
      attributes.put(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB);
    if (run.colorHex != null) attributes.put(TextAttribute.FOREGROUND, PropertyValue.of(run.colorHex).asColor());
    for (Map.Entry<TextAttribute, Object> entry : attributes.entrySet())
      attributed.addAttribute(entry.getKey(), entry.getValue(), start, end);
  }

  /** Fallback colour when a run says nothing about it. */
  public static Color defaultColor(RenderTarget target) {
    return target.background == RenderTarget.Background.BLACK ? Color.WHITE : Color.BLACK;
  }
}

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays out rich text with AttributedString and LineBreakMeasurer, so run overrides, CJK line
 * breaking and per-line widths all come from the platform rather than a hand-rolled wrapper.
 * Non-rectangular regions extend this by feeding it a different width per line.
 */
public class TextRenderer {
  /** One laid-out line and where it sits, in pixels relative to the text box. */
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
    /** True when the text needed more room than the box allowed. */
    public boolean overflowed;
  }

  /** Width available for a line starting at the given vertical offset. */
  public interface LineWidths {
    float widthAt(float top, float height);
  }

  public Flowed flow(Graphics2D g, TextContent content, float widthPx, float heightPx,
      double dpi, Align defaultAlign) {
    return flow(g, content, constant(widthPx), widthPx, heightPx, dpi, defaultAlign);
  }

  public static LineWidths constant(final float widthPx) {
    return new LineWidths() {
      public float widthAt(float top, float height) { return widthPx; }
    };
  }

  public Flowed flow(Graphics2D g, TextContent content, LineWidths widths, float maxWidthPx,
      float heightPx, double dpi, Align defaultAlign) {
    Flowed flowed = new Flowed();
    float y = 0;
    for (Paragraph paragraph : content.paragraphs) {
      if (paragraph.spaceBeforePt != null) y += ptToPx(paragraph.spaceBeforePt, dpi);
      String text = paragraph.plainText();
      Align align = paragraph.align == null ? defaultAlign : paragraph.align;
      if (text.isEmpty()) {
        y += ptToPx(largestFontPt(paragraph), dpi) * 1.2f;
        continue;
      }
      AttributedCharacterIterator iterator = attributed(paragraph, dpi).getIterator();
      LineBreakMeasurer measurer = new LineBreakMeasurer(iterator, g.getFontRenderContext());
      while (measurer.getPosition() < iterator.getEndIndex()) {
        float lineHeight = 0;
        float available = Math.max(1, widths.widthAt(y, 0));
        TextLayout layout = measurer.nextLayout(available);
        lineHeight = layout.getAscent() + layout.getDescent() + layout.getLeading();
        float x = 0;
        if (align == Align.CENTER) x = (available - layout.getAdvance()) / 2;
        else if (align == Align.END) x = available - layout.getAdvance();
        flowed.lines.add(new Line(layout, x + (maxWidthPx - available) * offsetShare(align),
            y + layout.getAscent()));
        y += lineHeight;
        if (heightPx > 0 && y > heightPx) flowed.overflowed = true;
      }
      if (paragraph.spaceAfterPt != null) y += ptToPx(paragraph.spaceAfterPt, dpi);
    }
    flowed.usedHeight = y;
    if (heightPx > 0 && y > heightPx + 0.5f) flowed.overflowed = true;
    return flowed;
  }

  private static float offsetShare(Align align) {
    return align == Align.CENTER ? 0.5f : align == Align.END ? 1f : 0f;
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
  private AttributedString attributed(Paragraph paragraph, double dpi) {
    AttributedString attributed = new AttributedString(paragraph.plainText());
    int start = 0;
    for (Run run : paragraph.runs) {
      int end = start + run.text.length();
      if (end > start) apply(attributed, run, start, end, dpi);
      start = end;
    }
    return attributed;
  }

  private void apply(AttributedString attributed, Run run, int start, int end, double dpi) {
    Map<TextAttribute, Object> attributes = new HashMap<TextAttribute, Object>();
    attributes.put(TextAttribute.FAMILY, run.latinFamily == null ? Font.SANS_SERIF : run.latinFamily);
    attributes.put(TextAttribute.SIZE, ptToPx(run.fontSizePt == null ? 12 : run.fontSizePt, dpi));
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

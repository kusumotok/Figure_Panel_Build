package org.microscopy.panel;

import java.util.ArrayList;
import java.util.List;

public final class TextContent {
  /** Warn by default: silently shrinking type would break a poster's typographic consistency. */
  public enum Overflow { WARN, AUTO_SHRINK, EXPAND_REGION, CLIP }

  /** Only right angles: PPTX can carry these, and arbitrary rotation is out of scope by design. */
  public enum Rotation { NONE, CLOCKWISE_90, COUNTERCLOCKWISE_90 }

  public List<Paragraph> paragraphs = new ArrayList<Paragraph>();
  public Rotation rotation = Rotation.NONE;
  /** Null means the node's own rectangle is the text area. */
  public FlowRegion flowRegion;
  public Overflow overflow = Overflow.WARN;

  public TextContent() {}

  public static TextContent of(String text) {
    TextContent content = new TextContent();
    content.paragraphs.add(Paragraph.of(text));
    return content;
  }

  public String plainText() {
    StringBuilder text = new StringBuilder();
    for (Paragraph paragraph : paragraphs) {
      if (text.length() > 0) text.append('\n');
      text.append(paragraph.plainText());
    }
    return text.toString();
  }

  public void validate(String where) {
    if (paragraphs == null) throw new IllegalArgumentException(where + ": text without paragraphs.");
    if (overflow == null) throw new IllegalArgumentException(where + ": text without an overflow policy.");
    if (rotation == null) throw new IllegalArgumentException(where + ": text without a rotation.");
    for (int i = 0; i < paragraphs.size(); i++) {
      if (paragraphs.get(i) == null) throw new IllegalArgumentException(where + ": null paragraph.");
      paragraphs.get(i).validate(where + " paragraph " + (i + 1));
    }
    if (flowRegion != null) flowRegion.validate(where + " flow region");
  }
}

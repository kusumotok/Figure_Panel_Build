package org.microscopy.panel;

import java.util.ArrayList;
import java.util.List;

public final class Paragraph {
  public List<Run> runs = new ArrayList<Run>();
  public Align align = Align.START;
  public Double spaceBeforePt, spaceAfterPt;

  public Paragraph() {}

  public static Paragraph of(String text) {
    Paragraph paragraph = new Paragraph();
    paragraph.runs.add(new Run(text));
    return paragraph;
  }

  public String plainText() {
    StringBuilder text = new StringBuilder();
    for (Run run : runs) text.append(run.text);
    return text.toString();
  }

  public void validate(String where) {
    if (runs == null) throw new IllegalArgumentException(where + ": paragraph without runs.");
    if (align == null) throw new IllegalArgumentException(where + ": paragraph without alignment.");
    for (int i = 0; i < runs.size(); i++) {
      if (runs.get(i) == null) throw new IllegalArgumentException(where + ": null run.");
      runs.get(i).validate(where + " run " + (i + 1));
    }
  }
}

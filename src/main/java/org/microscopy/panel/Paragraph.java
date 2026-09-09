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

  /**
   * The part of this paragraph between two character offsets, with run boundaries and overrides
   * preserved. Used when a non-rectangular region has to be exported as separate text boxes.
   */
  public Paragraph slice(int start, int limit) {
    Paragraph piece = new Paragraph();
    piece.align = align;
    piece.spaceBeforePt = spaceBeforePt;
    piece.spaceAfterPt = spaceAfterPt;
    int at = 0;
    for (Run run : runs) {
      int end = at + run.text.length();
      int from = Math.max(start, at), to = Math.min(limit, end);
      if (to > from) {
        Run copy = new Run(run.text.substring(from - at, to - at));
        copy.bold = run.bold;
        copy.italic = run.italic;
        copy.underline = run.underline;
        copy.superscript = run.superscript;
        copy.subscript = run.subscript;
        copy.fontSizePt = run.fontSizePt;
        copy.latinFamily = run.latinFamily;
        copy.eaFamily = run.eaFamily;
        copy.colorHex = run.colorHex;
        piece.runs.add(copy);
      }
      at = end;
    }
    return piece;
  }
}

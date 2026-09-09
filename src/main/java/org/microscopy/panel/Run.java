package org.microscopy.panel;

/**
 * A span of text inside a paragraph. Every override is nullable: null means "keep the paragraph's
 * resolved style", so a run only records what it actually changes.
 */
public final class Run {
  public String text = "";
  public Boolean bold, italic, underline, superscript, subscript;
  public Double fontSizePt;
  public String latinFamily, eaFamily;
  public String colorHex;

  public Run() {}

  public Run(String text) { this.text = text; }

  public void validate(String where) {
    if (text == null) throw new IllegalArgumentException(where + ": run without text.");
    if (fontSizePt != null && !(fontSizePt > 0 && fontSizePt <= 4096))
      throw new IllegalArgumentException(where + ": font size out of range.");
    if (colorHex != null && !colorHex.matches("#[0-9a-fA-F]{6}"))
      throw new IllegalArgumentException(where + ": colour must be #RRGGBB.");
    if (Boolean.TRUE.equals(superscript) && Boolean.TRUE.equals(subscript))
      throw new IllegalArgumentException(where + ": run cannot be both superscript and subscript.");
  }
}

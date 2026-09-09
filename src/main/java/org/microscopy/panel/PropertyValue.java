package org.microscopy.panel;

import java.awt.Color;

/**
 * A style or appearance value. Literals are stored as text so the document stays a plain Gson
 * tree; typed access happens through the accessors, which fail loudly on malformed documents.
 */
public final class PropertyValue {
  public enum Kind { LITERAL, TOKEN_REF, INHERIT }

  public Kind kind = Kind.LITERAL;
  public String literal;
  public String tokenId;

  public PropertyValue() {}

  private PropertyValue(Kind kind, String literal, String tokenId) {
    this.kind = kind; this.literal = literal; this.tokenId = tokenId;
  }

  public static PropertyValue of(String literal) { return new PropertyValue(Kind.LITERAL, literal, null); }
  public static PropertyValue of(double value) { return of(Double.toString(value)); }
  public static PropertyValue of(boolean value) { return of(Boolean.toString(value)); }
  public static PropertyValue of(Color color) { return of(hex(color)); }
  public static PropertyValue token(String tokenId) { return new PropertyValue(Kind.TOKEN_REF, null, tokenId); }
  public static PropertyValue inherit() { return new PropertyValue(Kind.INHERIT, null, null); }

  public static String hex(Color color) {
    return String.format("#%06X", color.getRGB() & 0xffffff);
  }

  public double asDouble() {
    try {
      return Double.parseDouble(text());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Not a number: " + literal, ex);
    }
  }

  public boolean asBoolean() {
    String text = text();
    if ("true".equalsIgnoreCase(text)) return true;
    if ("false".equalsIgnoreCase(text)) return false;
    throw new IllegalArgumentException("Not a boolean: " + literal);
  }

  public Color asColor() {
    String text = text();
    if (!text.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Not a #RRGGBB colour: " + text);
    return new Color(Integer.parseInt(text.substring(1), 16));
  }

  public String text() {
    if (kind != Kind.LITERAL || literal == null)
      throw new IllegalArgumentException("Property is not a literal; resolve it against the style tables first.");
    return literal;
  }

  public void validate(String where) {
    if (kind == null) throw new IllegalArgumentException(where + ": missing property kind.");
    if (kind == Kind.LITERAL && literal == null) throw new IllegalArgumentException(where + ": literal without a value.");
    if (kind == Kind.TOKEN_REF && (tokenId == null || tokenId.trim().isEmpty()))
      throw new IllegalArgumentException(where + ": token reference without a token.");
  }
}

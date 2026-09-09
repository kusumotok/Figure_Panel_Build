package org.microscopy.panel;

/**
 * A named value. Styles reference tokens; tokens never reference styles, and may alias another
 * token at most one level deep. That rule is what keeps the style graph acyclic by construction.
 */
public final class Token {
  public enum Kind { COLOR, LENGTH_MM, FONT_SIZE_PT, FONT_FAMILY, NUMBER, BOOLEAN, TEXT }

  public String id;
  public String name = "";
  public Kind kind = Kind.TEXT;
  public String value = "";
  /** Optional single-level alias to another token; mutually exclusive with a literal value. */
  public String aliasOf;

  public Token() {}

  public Token(String id, String name, Kind kind, String value) {
    this.id = id; this.name = name; this.kind = kind; this.value = value;
  }

  public void validate() {
    if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Token without an id.");
    if (kind == null) throw new IllegalArgumentException("Token " + id + ": missing kind.");
    if (aliasOf != null && !aliasOf.trim().isEmpty()) {
      if (aliasOf.equals(id)) throw new IllegalArgumentException("Token " + id + " aliases itself.");
      return;
    }
    if (value == null) throw new IllegalArgumentException("Token " + id + ": missing value.");
    switch (kind) {
      case COLOR:
        if (!value.matches("#[0-9a-fA-F]{6}"))
          throw new IllegalArgumentException("Token " + id + ": colour must be #RRGGBB.");
        break;
      case LENGTH_MM:
      case FONT_SIZE_PT:
      case NUMBER:
        try {
          double parsed = Double.parseDouble(value);
          if (!Double.isFinite(parsed))
            throw new IllegalArgumentException("Token " + id + ": non-finite number.");
        } catch (NumberFormatException ex) {
          throw new IllegalArgumentException("Token " + id + ": not a number.", ex);
        }
        break;
      case BOOLEAN:
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
          throw new IllegalArgumentException("Token " + id + ": not a boolean.");
        break;
      default:
        break;
    }
  }
}

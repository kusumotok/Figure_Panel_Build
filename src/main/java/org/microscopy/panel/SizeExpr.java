package org.microscopy.panel;

/**
 * The closed set of size relations. Deliberately not a formula language: every kind here is
 * resolvable by ordered passes, and {@link Kind#SAME_AS} may only name a sibling, so reference
 * cycles cannot be expressed at all.
 */
public final class SizeExpr {
  public enum Kind { AUTO, FIXED, FRACTION, PERCENTAGE, SAME_AS, ASPECT_RATIO }

  public Kind kind = Kind.AUTO;
  /** FIXED: millimetres. FRACTION: weight. PERCENTAGE: percent of parent. ASPECT_RATIO: ratio. */
  public double value;
  /** SAME_AS only: id of a sibling under the same parent. */
  public String siblingId;
  /** Optional clamp in millimetres, applied after the kind resolves. */
  public Double minMm, maxMm;

  public SizeExpr() {}

  private SizeExpr(Kind kind, double value, String siblingId) {
    this.kind = kind; this.value = value; this.siblingId = siblingId;
  }

  public static SizeExpr auto() { return new SizeExpr(Kind.AUTO, 0, null); }
  public static SizeExpr fixed(double mm) { return new SizeExpr(Kind.FIXED, mm, null); }
  public static SizeExpr fraction(double weight) { return new SizeExpr(Kind.FRACTION, weight, null); }
  public static SizeExpr percentage(double percent) { return new SizeExpr(Kind.PERCENTAGE, percent, null); }
  public static SizeExpr sameAs(String siblingId) { return new SizeExpr(Kind.SAME_AS, 0, siblingId); }
  public static SizeExpr aspectRatio(double ratio) { return new SizeExpr(Kind.ASPECT_RATIO, ratio, null); }

  /** Fill is the common case of a single fraction; named separately only in the UI. */
  public static SizeExpr fill() { return fraction(1); }

  public SizeExpr clamped(Double minMm, Double maxMm) {
    this.minMm = minMm; this.maxMm = maxMm; return this;
  }

  public double clamp(double mm) {
    if (minMm != null && mm < minMm) mm = minMm;
    if (maxMm != null && mm > maxMm) mm = maxMm;
    return mm;
  }

  public void validate(String where) {
    if (kind == null) throw new IllegalArgumentException(where + ": missing size kind.");
    switch (kind) {
      case FIXED:
        require(value > 0, where + ": fixed size must be positive millimetres.");
        break;
      case FRACTION:
        require(value > 0, where + ": fraction weight must be positive.");
        break;
      case PERCENTAGE:
        require(value > 0 && value <= 1000, where + ": percentage must be between 0 and 1000.");
        break;
      case ASPECT_RATIO:
        require(value > 0, where + ": aspect ratio must be positive.");
        break;
      case SAME_AS:
        require(siblingId != null && !siblingId.trim().isEmpty(), where + ": Same as needs a sibling.");
        break;
      default:
        break;
    }
    if (minMm != null) require(minMm >= 0 && Double.isFinite(minMm), where + ": invalid minimum.");
    if (maxMm != null) require(maxMm > 0 && Double.isFinite(maxMm), where + ": invalid maximum.");
    if (minMm != null && maxMm != null)
      require(minMm <= maxMm, where + ": minimum exceeds maximum.");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }
}

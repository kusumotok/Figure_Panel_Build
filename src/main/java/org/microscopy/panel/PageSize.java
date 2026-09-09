package org.microscopy.panel;

/** Auto grows with the content; the presets and Custom pin one or both edges. */
public final class PageSize {
  public enum Mode { AUTO, FIXED, FIXED_WIDTH }

  public Mode mode = Mode.AUTO;
  public double widthMm, heightMm;
  /** Preset name for display only; the numbers above stay authoritative. */
  public String presetName;

  public static PageSize auto() { return new PageSize(); }

  public static PageSize fixed(double widthMm, double heightMm) {
    PageSize size = new PageSize();
    size.mode = Mode.FIXED; size.widthMm = widthMm; size.heightMm = heightMm;
    return size;
  }

  public static PageSize fixedWidth(double widthMm) {
    PageSize size = new PageSize();
    size.mode = Mode.FIXED_WIDTH; size.widthMm = widthMm;
    return size;
  }

  public void validate(String where) {
    if (mode == null) throw new IllegalArgumentException(where + ": missing page size mode.");
    if (mode == Mode.AUTO) return;
    if (!(widthMm > 0) || !Double.isFinite(widthMm))
      throw new IllegalArgumentException(where + ": page width must be positive millimetres.");
    if (mode == Mode.FIXED && (!(heightMm > 0) || !Double.isFinite(heightMm)))
      throw new IllegalArgumentException(where + ": page height must be positive millimetres.");
  }

  /** PowerPoint cannot represent a slide longer than 56 inches on either edge. */
  public String slideSizeWarning() {
    double longest = Math.max(widthMm, heightMm);
    if (mode == Mode.AUTO || longest <= Units.MAX_SLIDE_MM) return "";
    return String.format(
        "WARNING: %.0f mm exceeds the %.0f mm PowerPoint slide limit; PPTX output will be clamped.",
        longest, Units.MAX_SLIDE_MM);
  }
}

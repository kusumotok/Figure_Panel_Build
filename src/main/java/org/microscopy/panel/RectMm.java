package org.microscopy.panel;

/** An immutable rectangle in document millimetres. */
public final class RectMm {
  public final double x, y, width, height;

  public RectMm(double x, double y, double width, double height) {
    this.x = x; this.y = y; this.width = width; this.height = height;
  }

  public double right() { return x + width; }

  public double bottom() { return y + height; }

  public RectMm inset(Margins margins) {
    return new RectMm(x + margins.leftMm, y + margins.topMm,
        Math.max(0, width - margins.leftMm - margins.rightMm),
        Math.max(0, height - margins.topMm - margins.bottomMm));
  }

  public RectMm inset(double mm) {
    return new RectMm(x + mm, y + mm, Math.max(0, width - 2 * mm), Math.max(0, height - 2 * mm));
  }

  public RectMm translated(double dx, double dy) { return new RectMm(x + dx, y + dy, width, height); }

  public boolean contains(double px, double py) {
    return px >= x && py >= y && px < right() && py < bottom();
  }

  public boolean intersects(RectMm other) {
    return other.x < right() && x < other.right() && other.y < bottom() && y < other.bottom();
  }

  /** Absolute edges are rounded before the size is taken, so tiling never drifts by a unit. */
  public long[] toEmu() {
    long left = Units.mmToEmu(x), top = Units.mmToEmu(y);
    return new long[] {left, top, Units.mmToEmu(right()) - left, Units.mmToEmu(bottom()) - top};
  }

  public int[] toPx(double dpi) {
    Units.checkDpi(dpi);
    long left = Math.round(x / Units.MM_PER_INCH * dpi), top = Math.round(y / Units.MM_PER_INCH * dpi);
    long right = Math.round(right() / Units.MM_PER_INCH * dpi);
    long bottom = Math.round(bottom() / Units.MM_PER_INCH * dpi);
    return new int[] {(int) left, (int) top, (int) (right - left), (int) (bottom - top)};
  }

  @Override
  public String toString() {
    return String.format("[%.3f, %.3f %.3f x %.3f mm]", x, y, width, height);
  }
}

package org.microscopy.panel;

/**
 * The single place where logical millimetres are converted to any other unit.
 * Keeping every conversion here is what makes "A0 is exactly A0" checkable.
 */
public final class Units {
  public static final double EMU_PER_MM = 36000.0;
  public static final double MM_PER_INCH = 25.4;
  public static final double POINTS_PER_INCH = 72.0;
  /** PowerPoint refuses slides longer than 56 inches on either edge. */
  public static final double MAX_SLIDE_MM = 56.0 * MM_PER_INCH;

  private Units() {}

  public static long mmToEmu(double mm) {
    if (!Double.isFinite(mm)) throw new IllegalArgumentException("Non-finite millimetres.");
    return Math.round(mm * EMU_PER_MM);
  }

  public static double emuToMm(long emu) { return emu / EMU_PER_MM; }

  public static double ptToMm(double pt) { return pt * MM_PER_INCH / POINTS_PER_INCH; }

  public static double mmToPt(double mm) { return mm * POINTS_PER_INCH / MM_PER_INCH; }

  /** PPTX writes font sizes in hundredths of a point. */
  public static int mmToCentipoints(double mm) {
    return Math.max(100, (int) Math.round(mmToPt(mm) * 100.0));
  }

  public static int mmToPx(double mm, double dpi) {
    checkDpi(dpi);
    return Math.max(1, (int) Math.round(mm / MM_PER_INCH * dpi));
  }

  public static double pxToMm(double px, double dpi) {
    checkDpi(dpi);
    return px / dpi * MM_PER_INCH;
  }

  /** Resolution a source image actually reaches once it is drawn at the given physical width. */
  public static double effectiveDpi(int sourcePx, double mm) {
    if (sourcePx <= 0 || !(mm > 0)) throw new IllegalArgumentException("Invalid effective dpi input.");
    return sourcePx / (mm / MM_PER_INCH);
  }

  static void checkDpi(double dpi) {
    if (!(dpi > 0) || dpi > 20000) throw new IllegalArgumentException("Dpi must be between 0 and 20000.");
  }
}

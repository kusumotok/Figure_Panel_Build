package org.microscopy.panel;

public final class Margins {
  public double topMm, rightMm, bottomMm, leftMm;

  public Margins() {}

  public static Margins uniform(double mm) {
    Margins margins = new Margins();
    margins.topMm = margins.rightMm = margins.bottomMm = margins.leftMm = mm;
    return margins;
  }

  public void validate(String where) {
    if (topMm < 0 || rightMm < 0 || bottomMm < 0 || leftMm < 0
        || !Double.isFinite(topMm) || !Double.isFinite(rightMm)
        || !Double.isFinite(bottomMm) || !Double.isFinite(leftMm))
      throw new IllegalArgumentException(where + ": margins must be zero or positive millimetres.");
  }
}

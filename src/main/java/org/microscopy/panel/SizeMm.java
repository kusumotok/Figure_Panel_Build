package org.microscopy.panel;

/** A measured intrinsic size in millimetres. */
public final class SizeMm {
  public static final SizeMm ZERO = new SizeMm(0, 0);

  public final double width, height;

  public SizeMm(double width, double height) { this.width = width; this.height = height; }
}

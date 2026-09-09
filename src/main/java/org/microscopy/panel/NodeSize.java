package org.microscopy.panel;

public final class NodeSize {
  public SizeExpr width = SizeExpr.auto();
  public SizeExpr height = SizeExpr.auto();

  public void validate(String where) {
    if (width == null || height == null) throw new IllegalArgumentException(where + ": missing size.");
    width.validate(where + " width");
    height.validate(where + " height");
    if (width.kind == SizeExpr.Kind.ASPECT_RATIO && height.kind == SizeExpr.Kind.ASPECT_RATIO)
      throw new IllegalArgumentException(
          where + ": width and height cannot both be an aspect ratio; one edge must be resolvable.");
  }
}

package org.microscopy.panel;

public final class ShapeContent {
  public enum Shape { RECTANGLE, ELLIPSE, LINE, ARROW }

  public Shape shape = Shape.RECTANGLE;

  public ShapeContent() {}

  public ShapeContent(Shape shape) { this.shape = shape; }

  public void validate(String where) {
    if (shape == null) throw new IllegalArgumentException(where + ": shape without a kind.");
  }
}

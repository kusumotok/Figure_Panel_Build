package org.microscopy.panel;

/**
 * Intrinsic size of a leaf's content. Kept behind an interface so the real text engine can
 * replace the estimate without the layout engine changing.
 */
public interface ContentMeasurer {
  /** Nominal print resolution used to give a pixel image a physical intrinsic size. */
  double NOMINAL_DPI = 300;

  SizeMm measure(Document document, Node node, double availableWidthMm);
}

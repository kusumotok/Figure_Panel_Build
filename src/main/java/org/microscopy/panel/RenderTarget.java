package org.microscopy.panel;

import java.awt.Color;

/** Output resolution and background. Resolution is a first-class part of every render. */
public final class RenderTarget {
  public enum Background { WHITE, BLACK, TRANSPARENT }

  public static final double PRINT_DPI = 300;
  public static final double PREVIEW_DPI = 150;

  public final double dpi;
  public final Background background;

  public RenderTarget(double dpi, Background background) {
    Units.checkDpi(dpi);
    if (background == null) throw new IllegalArgumentException("Missing background.");
    this.dpi = dpi;
    this.background = background;
  }

  public static RenderTarget print() { return new RenderTarget(PRINT_DPI, Background.WHITE); }

  public static RenderTarget preview() { return new RenderTarget(PREVIEW_DPI, Background.WHITE); }

  public int px(double mm) { return Units.mmToPx(mm, dpi); }

  public Color awtBackground() {
    return background == Background.BLACK ? Color.BLACK : Color.WHITE;
  }

  public boolean transparent() { return background == Background.TRANSPARENT; }
}

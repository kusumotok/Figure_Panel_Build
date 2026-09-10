package org.microscopy.panel;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Rasterises a page in horizontal bands, painting only the nodes each band touches. The whole
 * page is never held as one image, which is what makes poster sizes possible: A0 at 300 dpi is
 * about 139 megapixels, well past what the figure engine could ever allocate.
 */
public final class DocumentRasterizer {
  public static final int DEFAULT_BAND_ROWS = 256;

  public interface BandSink {
    void band(BufferedImage band, int topPx) throws IOException;
  }

  private final SourceProvider sources;
  private java.util.Set<String> overflowed = new java.util.LinkedHashSet<String>();

  public DocumentRasterizer(SourceProvider sources) { this.sources = sources; }

  /** Text nodes that did not fit during the last rasterisation. */
  public java.util.Set<String> overflowedNodes() { return overflowed; }

  public int[] sizePx(LayoutResult layout, RenderTarget target) {
    RectMm page = layout.pageBox();
    return new int[] {Math.max(1, target.px(page.width)), Math.max(1, target.px(page.height))};
  }

  public void rasterize(Document document, Page page, LayoutResult layout, RenderTarget target,
      int bandRows, BandSink sink) throws IOException {
    if (bandRows < 1) throw new IllegalArgumentException("Band height must be at least one row.");
    int[] size = sizePx(layout, target);
    NodeRenderer renderer = new NodeRenderer(sources);
    double mmPerPx = Units.MM_PER_INCH / target.dpi;
    for (int top = 0; top < size[1]; top += bandRows) {
      int rows = Math.min(bandRows, size[1] - top);
      BufferedImage band = new BufferedImage(size[0], rows,
          target.transparent() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
      Graphics2D g = band.createGraphics();
      try {
        if (!target.transparent()) {
          g.setColor(target.awtBackground());
          g.fillRect(0, 0, size[0], rows);
        }
        RectMm clip = new RectMm(0, top * mmPerPx, size[0] * mmPerPx, rows * mmPerPx);
        renderer.paint(g, document, page, layout, target, clip, 0, top);
      } finally {
        g.dispose();
      }
      sink.band(band, top);
    }
    overflowed = renderer.overflowedNodes();
  }

  /** Convenience for previews and tests, where the page is small enough to hold at once. */
  public BufferedImage rasterizeWhole(Document document, Page page, LayoutResult layout,
      RenderTarget target) throws IOException {
    int[] size = sizePx(layout, target);
    final BufferedImage whole = new BufferedImage(size[0], size[1],
        target.transparent() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    rasterize(document, page, layout, target, DEFAULT_BAND_ROWS, new BandSink() {
      public void band(BufferedImage band, int topPx) {
        Graphics2D g = whole.createGraphics();
        try {
          g.drawImage(band, 0, topPx, null);
        } finally {
          g.dispose();
        }
      }
    });
    return whole;
  }

  public void writePng(File destination, Document document, Page page, LayoutResult layout,
      RenderTarget target) throws IOException {
    ProjectSafety.checkDestination(destination, document);
    int[] size = sizePx(layout, target);
    Path path = destination.toPath().toAbsolutePath();
    Path temp = Files.createTempFile(path.getParent(), "panel-", ".png.tmp");
    try {
      try (OutputStream out = Files.newOutputStream(temp);
          final PngStreamWriter png = new PngStreamWriter(out, size[0], size[1], target.transparent())) {
        rasterize(document, page, layout, target, DEFAULT_BAND_ROWS, new BandSink() {
          public void band(BufferedImage band, int topPx) throws IOException {
            png.band(band);
          }
        });
      }
      Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /**
   * Writes a TIFF through ImageJ's encoder, which needs the whole raster at once. PNG is banded
   * and has no such limit, so this refuses rather than trying and failing on a poster.
   */
  public void writeTiff(File destination, Document document, Page page, LayoutResult layout,
      RenderTarget target, long maxPixels) throws IOException {
    ProjectSafety.checkDestination(destination, document);
    int[] size = sizePx(layout, target);
    long pixels = (long) size[0] * size[1];
    if (pixels > maxPixels)
      throw new IllegalArgumentException(String.format(
          "TIFF is written in one piece and this page is %.0f megapixels at %.0f dpi."
              + " Export PNG instead, which is written in bands, or lower the resolution.",
          pixels / 1e6, target.dpi));
    java.awt.image.BufferedImage whole = rasterizeWhole(document, page, layout, target);
    Path path = destination.toPath().toAbsolutePath();
    Path temp = Files.createTempFile(path.getParent(), "panel-", ".tif.tmp");
    try {
      ij.ImagePlus image = new ij.ImagePlus(destination.getName(), whole);
      // Record the physical size so the TIFF opens at the right scale elsewhere.
      ij.measure.Calibration calibration = image.getCalibration();
      calibration.pixelWidth = Units.MM_PER_INCH / target.dpi;
      calibration.pixelHeight = calibration.pixelWidth;
      calibration.setUnit("mm");
      if (!new ij.io.FileSaver(image).saveAsTiff(temp.toString()))
        throw new IOException("ImageJ could not write the TIFF.");
      Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /** The default ceiling for a one-piece TIFF: comfortable inside a normal Fiji heap. */
  public static final long TIFF_PIXEL_LIMIT = 120_000_000L;
}

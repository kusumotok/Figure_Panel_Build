package org.microscopy.panel;

import java.awt.image.BufferedImage;
import org.microscopy.figure.FigureConfiguration;
import org.microscopy.figure.ImageRenderer;
import org.microscopy.figure.InputImageManager;
import org.microscopy.figure.InsetCell;
import org.microscopy.figure.InsetRenderer;
import org.microscopy.figure.ScaleBarRenderer;

/**
 * Draws one microscopy cell at an exact pixel size by making the same calls, in the same order,
 * as the existing figure engine: display transform, then scale bar, then inset. Keeping the call
 * order identical is what lets an imported figure be compared pixel for pixel with the original.
 */
public final class ScientificImageRenderer {
  public BufferedImage render(ScientificImageContent content, InputImageManager.Source source,
      int widthPx, int heightPx) {
    if (widthPx < 1 || heightPx < 1) throw new IllegalArgumentException("Invalid cell size in pixels.");
    FigureConfiguration config = content.figureConfig;
    BufferedImage image = content.resample == ScientificImageContent.Resample.AREA_AVERAGE
        && widthPx < source.width && heightPx < source.height
        ? areaAverage(config, source, widthPx, heightPx)
        : new ImageRenderer().render(source, config.displayChannels.get(0), config, widthPx, heightPx);
    double scale = widthPx / (double) source.width;
    if (config.scaleBar.show) new ScaleBarRenderer().draw(image, source, config.scaleBar, scale);
    InsetCell cell = config.insetCell(0, 0);
    if (new InsetRenderer().applies(cell, source, config.inset))
      new InsetRenderer().draw(image, source, config.displayChannels.get(0), config, config.inset,
          cell, scale);
    return image;
  }

  /**
   * Averaging the pixels a downscaled output covers, rather than dropping all but one of them.
   * Off by default: a quantitative figure should not be interpolated unless it was asked for.
   */
  private BufferedImage areaAverage(FigureConfiguration config, InputImageManager.Source source,
      int widthPx, int heightPx) {
    BufferedImage full = new ImageRenderer()
        .render(source, config.displayChannels.get(0), config, source.width, source.height);
    BufferedImage out = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB);
    int[] line = new int[source.width];
    for (int y = 0; y < heightPx; y++) {
      int y0 = (int) ((long) y * source.height / heightPx);
      int y1 = Math.max(y0 + 1, (int) ((long) (y + 1) * source.height / heightPx));
      for (int x = 0; x < widthPx; x++) {
        int x0 = (int) ((long) x * source.width / widthPx);
        int x1 = Math.max(x0 + 1, (int) ((long) (x + 1) * source.width / widthPx));
        long r = 0, g = 0, b = 0, n = 0;
        for (int sy = y0; sy < y1; sy++) {
          full.getRGB(0, sy, source.width, 1, line, 0, source.width);
          for (int sx = x0; sx < x1; sx++) {
            int rgb = line[sx];
            r += (rgb >> 16) & 255;
            g += (rgb >> 8) & 255;
            b += rgb & 255;
            n++;
          }
        }
        out.setRGB(x, y, (int) ((r / n) << 16 | (g / n) << 8 | (b / n)));
      }
    }
    return out;
  }
}

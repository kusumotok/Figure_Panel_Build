package org.microscopy.panel;

import ij.ImagePlus;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads Bio-Formats containers through reflection.
 *
 * <p>Bio-Formats ships with Fiji and is GPL, so it is neither a build dependency nor bundled in
 * the jar. Reaching it reflectively keeps the plugin buildable offline, keeps the licence
 * boundary clean, and lets everything except container import keep working where it is absent.
 */
public final class BioFormatsReader {
  public static final class Series {
    public final int index;
    public final String name;
    public final int sizeX, sizeY, sizeC, sizeZ, sizeT, bitDepth;
    public final double pixelWidth;
    public final String unit;

    Series(int index, String name, int sizeX, int sizeY, int sizeC, int sizeZ, int sizeT,
        int bitDepth, double pixelWidth, String unit) {
      this.index = index; this.name = name; this.sizeX = sizeX; this.sizeY = sizeY;
      this.sizeC = sizeC; this.sizeZ = sizeZ; this.sizeT = sizeT; this.bitDepth = bitDepth;
      this.pixelWidth = pixelWidth; this.unit = unit;
    }

    @Override
    public String toString() {
      return String.format("%d: %s  %dx%d C=%d Z=%d T=%d %d-bit", index, name, sizeX, sizeY,
          sizeC, sizeZ, sizeT, bitDepth);
    }
  }

  public static boolean available() {
    try {
      Class.forName("loci.formats.ImageReader");
      Class.forName("loci.plugins.in.ImporterOptions");
      return true;
    } catch (Throwable ex) {
      return false;
    }
  }

  private static void require() {
    if (!available())
      throw new IllegalStateException("Bio-Formats is not on the classpath. Run inside Fiji, or"
          + " convert the file to TIFF first.");
  }

  /** Everything the series picker needs, without decoding any pixels. */
  public List<Series> list(File file) {
    require();
    List<Series> series = new ArrayList<Series>();
    Object reader = null;
    try {
      reader = Class.forName("loci.formats.ImageReader").newInstance();
      Object metadata = Class.forName("loci.formats.MetadataTools")
          .getMethod("createOMEXMLMetadata").invoke(null);
      reader.getClass().getMethod("setMetadataStore",
          Class.forName("loci.formats.meta.MetadataStore")).invoke(reader, metadata);
      reader.getClass().getMethod("setId", String.class).invoke(reader, file.getAbsolutePath());
      int count = (Integer) reader.getClass().getMethod("getSeriesCount").invoke(reader);
      for (int i = 0; i < count; i++) {
        reader.getClass().getMethod("setSeries", int.class).invoke(reader, i);
        series.add(new Series(i, name(metadata, i), size(reader, "getSizeX"), size(reader, "getSizeY"),
            size(reader, "getSizeC"), size(reader, "getSizeZ"), size(reader, "getSizeT"),
            bitDepth(reader), physicalSize(metadata, i), unit(metadata, i)));
      }
      return series;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Cannot read " + file.getName() + ": " + message(ex), ex);
    } finally {
      close(reader);
    }
  }

  /** One plane set: every channel of the given series at the given Z and T, both one-based. */
  public ImagePlus open(File file, int seriesIndex, int z, int t) {
    require();
    try {
      Class<?> optionsClass = Class.forName("loci.plugins.in.ImporterOptions");
      Object options = optionsClass.newInstance();
      set(options, "setId", String.class, file.getAbsolutePath());
      set(options, "setQuiet", boolean.class, Boolean.TRUE);
      set(options, "setWindowless", boolean.class, Boolean.TRUE);
      set(options, "setVirtual", boolean.class, Boolean.FALSE);
      set(options, "setOpenAllSeries", boolean.class, Boolean.FALSE);
      optionsClass.getMethod("setSeriesOn", int.class, boolean.class)
          .invoke(options, seriesIndex, Boolean.TRUE);
      set(options, "setSpecifyRanges", boolean.class, Boolean.TRUE);
      range(optionsClass, options, "setZBegin", "setZEnd", seriesIndex, z - 1);
      range(optionsClass, options, "setTBegin", "setTEnd", seriesIndex, t - 1);
      Object[] images = (Object[]) Class.forName("loci.plugins.BF")
          .getMethod("openImagePlus", optionsClass).invoke(null, options);
      if (images == null || images.length == 0 || images[0] == null)
        throw new IllegalArgumentException("Bio-Formats returned no image for series "
            + (seriesIndex + 1) + ".");
      return (ImagePlus) images[0];
    } catch (Exception ex) {
      throw new IllegalArgumentException("Cannot open series " + (seriesIndex + 1) + " of "
          + file.getName() + ": " + message(ex), ex);
    }
  }

  private static void range(Class<?> optionsClass, Object options, String begin, String end,
      int series, int value) throws Exception {
    optionsClass.getMethod(begin, int.class, int.class).invoke(options, series, value);
    optionsClass.getMethod(end, int.class, int.class).invoke(options, series, value);
  }

  private static void set(Object target, String method, Class<?> type, Object value)
      throws Exception {
    target.getClass().getMethod(method, type).invoke(target, value);
  }

  private static int size(Object reader, String method) throws Exception {
    return (Integer) reader.getClass().getMethod(method).invoke(reader);
  }

  private static int bitDepth(Object reader) {
    try {
      int type = (Integer) reader.getClass().getMethod("getPixelType").invoke(reader);
      Object bits = Class.forName("loci.formats.FormatTools")
          .getMethod("getBytesPerPixel", int.class).invoke(null, type);
      return ((Integer) bits) * 8;
    } catch (Exception ex) {
      return 16;
    }
  }

  private static String name(Object metadata, int index) {
    try {
      Object name = metadata.getClass().getMethod("getImageName", int.class).invoke(metadata, index);
      if (name != null && !name.toString().trim().isEmpty()) return name.toString();
    } catch (Exception ex) {
      // Metadata is optional; a positional name is still usable.
    }
    return "Series " + (index + 1);
  }

  private static double physicalSize(Object metadata, int index) {
    try {
      Object length = metadata.getClass().getMethod("getPixelsPhysicalSizeX", int.class)
          .invoke(metadata, index);
      if (length == null) return 1;
      Object value = length.getClass().getMethod("value").invoke(length);
      double size = ((Number) value).doubleValue();
      return size > 0 ? size : 1;
    } catch (Exception ex) {
      return 1;
    }
  }

  private static String unit(Object metadata, int index) {
    try {
      Object length = metadata.getClass().getMethod("getPixelsPhysicalSizeX", int.class)
          .invoke(metadata, index);
      if (length == null) return "";
      Object unit = length.getClass().getMethod("unit").invoke(length);
      Object symbol = unit.getClass().getMethod("getSymbol").invoke(unit);
      return symbol == null ? "" : symbol.toString();
    } catch (Exception ex) {
      return "";
    }
  }

  private static void close(Object reader) {
    if (reader == null) return;
    try {
      Method close = reader.getClass().getMethod("close");
      close.invoke(reader);
    } catch (Exception ex) {
      // Nothing useful to do; the handle is released when the reader is collected.
    }
  }

  private static String message(Exception ex) {
    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
    return cause.getMessage() == null ? cause.toString() : cause.getMessage();
  }
}

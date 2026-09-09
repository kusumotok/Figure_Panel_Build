package org.microscopy.panel;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.microscopy.figure.InputImageManager;

/**
 * Resolves asset ids to pixels. Files are read on first use, composites are assembled on demand,
 * and what has been read is held in a bounded cache so a container of thirty large series does
 * not have to fit in memory at once.
 *
 * <p>Images that came from an open Fiji window are pinned instead of cached, because they cannot
 * be read back if they are dropped and the user may still be editing them.
 */
public final class AssetLibrary implements SourceProvider {
  /** Roughly how much decoded pixel data to keep before dropping the least recently used. */
  public static final long DEFAULT_BUDGET_BYTES = 512L * 1024 * 1024;

  private final Map<String, InputImageManager.Source> pinned =
      new HashMap<String, InputImageManager.Source>();
  private final LinkedHashMap<String, InputImageManager.Source> cache =
      new LinkedHashMap<String, InputImageManager.Source>(16, 0.75f, true);
  private final long budgetBytes;
  private Document document;
  private BioFormatsReader reader;
  private long cachedBytes;

  public AssetLibrary() { this(DEFAULT_BUDGET_BYTES); }

  public AssetLibrary(long budgetBytes) { this.budgetBytes = Math.max(1, budgetBytes); }

  public void use(Document document) { this.document = document; }

  public Document document() { return document; }

  /** Takes over snapshots that already exist, keyed by their own id, and pins them. */
  public void adopt(InputImageManager other) {
    for (InputImageManager.Source source : other.all()) pinned.put(source.id, source);
  }

  public void register(String assetId, InputImageManager.Source source) {
    pinned.put(assetId, source);
  }

  public boolean has(String assetId) { return pinned.containsKey(assetId); }

  @Override
  public InputImageManager.Source source(String assetId, int z, int t) {
    if (z < 1 || t < 1) throw new IllegalArgumentException("Z and T selections start at 1.");
    InputImageManager.Source held = pinned.get(assetId);
    if (held != null) {
      if (z != 1 || t != 1)
        throw new IllegalArgumentException("Z " + z + " / T " + t
            + " is not available for an image taken from an open Fiji window.");
      return held;
    }
    String key = assetId + "|z" + z + "|t" + t;
    InputImageManager.Source cached = cache.get(key);
    if (cached != null) return cached;
    Asset asset = asset(assetId);
    InputImageManager.Source loaded = asset.composite()
        ? composite(asset, z, t) : plain(asset, z, t);
    put(key, loaded);
    return loaded;
  }

  private Asset asset(String assetId) {
    if (document == null || !document.assets.containsKey(assetId))
      throw new IllegalArgumentException("Unknown asset: " + assetId);
    return document.assets.get(assetId);
  }

  private void put(String key, InputImageManager.Source source) {
    cache.put(key, source);
    cachedBytes += bytes(source);
    Iterator<Map.Entry<String, InputImageManager.Source>> entries = cache.entrySet().iterator();
    while (cachedBytes > budgetBytes && cache.size() > 1 && entries.hasNext()) {
      Map.Entry<String, InputImageManager.Source> eldest = entries.next();
      if (eldest.getKey().equals(key)) continue;
      cachedBytes -= bytes(eldest.getValue());
      entries.remove();
    }
  }

  private static long bytes(InputImageManager.Source source) {
    return (long) source.width * source.height * source.channels
        * Math.max(1, source.bitDepth / 8);
  }

  public long cachedBytes() { return cachedBytes; }

  public int cachedCount() { return cache.size(); }

  // ------------------------------------------------------------------ loading

  private InputImageManager.Source plain(Asset asset, int z, int t) {
    File file = fileFor(asset);
    if (file == null)
      throw new IllegalArgumentException("The source for this picture is missing: "
          + describe(asset) + ". Relink it before drawing.");
    boolean simple = z == 1 && t == 1 && asset.originalSeriesIndex == 0
        && file.getName().toLowerCase(java.util.Locale.ROOT).matches(".*[.]tiff?");
    // A plain single-plane TIFF goes through the existing loader; anything else needs the reader.
    if (simple) return new InputImageManager().load(file);
    ImagePlus image = reader().open(file, asset.originalSeriesIndex, z, t);
    try {
      return new InputImageManager().snapshot(image, file.getAbsolutePath());
    } finally {
      image.flush();
    }
  }

  /** Builds one multi-channel image out of one channel from each part. */
  private InputImageManager.Source composite(Asset asset, int z, int t) {
    List<InputImageManager.Source> sources = new ArrayList<InputImageManager.Source>();
    int depth = 8;
    for (Asset.Part part : asset.parts) {
      InputImageManager.Source source = source(part.assetId, z, t);
      if (source.width != asset.sizeX || source.height != asset.sizeY)
        throw new IllegalArgumentException("Composite " + describe(asset) + " combines images of"
            + " different sizes; they cannot be overlaid.");
      if (part.channel > source.channels)
        throw new IllegalArgumentException("Composite " + describe(asset) + " asks for channel "
            + part.channel + " of an image with " + source.channels + ".");
      sources.add(source);
      depth = Math.max(depth, source.bitDepth);
    }
    ImageStack stack = new ImageStack(asset.sizeX, asset.sizeY);
    for (int i = 0; i < asset.parts.size(); i++) {
      Asset.Part part = asset.parts.get(i);
      String label = part.label == null || part.label.isEmpty() ? "Channel " + (i + 1) : part.label;
      stack.addSlice(label, plane(sources.get(i), part.channel, asset.sizeX, asset.sizeY, depth));
    }
    ImagePlus image = new ImagePlus(describe(asset), stack);
    image.setDimensions(asset.parts.size(), 1, 1);
    // Prefer what the asset records, but fall back to the parts so a composite never loses a
    // calibration its sources had; a scale bar depends on it.
    InputImageManager.Source reference = sources.get(0);
    Calibration calibration = image.getCalibration();
    calibration.pixelWidth = asset.pixelWidth > 1 || reference.pixelWidth <= 0
        ? asset.pixelWidth : reference.pixelWidth;
    calibration.pixelHeight = asset.pixelHeight > 1 || reference.pixelHeight <= 0
        ? asset.pixelHeight : reference.pixelHeight;
    String unit = asset.unit == null || asset.unit.isEmpty() ? reference.unit : asset.unit;
    if (unit != null && !unit.isEmpty()) calibration.setUnit(unit);
    try {
      return new InputImageManager().snapshot(image, null);
    } finally {
      image.flush();
    }
  }

  private static ImageProcessor plane(InputImageManager.Source source, int channel,
      int width, int height, int depth) {
    ImageProcessor out = depth <= 8 ? new ByteProcessor(width, height)
        : depth <= 16 ? (ImageProcessor) new ShortProcessor(width, height)
            : new FloatProcessor(width, height);
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) out.setf(x, y, source.value(channel, x, y));
    return out;
  }

  private BioFormatsReader reader() {
    if (reader == null) reader = new BioFormatsReader();
    return reader;
  }

  // ---------------------------------------------------------------- relinking

  /** Assets whose file cannot be found; the caller offers relinking rather than failing later. */
  public List<Asset> missing() {
    List<Asset> missing = new ArrayList<Asset>();
    if (document == null) return missing;
    for (Asset asset : document.assets.values()) {
      if (asset.composite() || pinned.containsKey(asset.id)) continue;
      if (fileFor(asset) == null) missing.add(asset);
    }
    return missing;
  }

  /** Points an asset at a different file after the user located it. */
  public void relink(String assetId, File file) {
    Asset asset = asset(assetId);
    if (asset.composite())
      throw new IllegalArgumentException("Relink the sources a composite is built from, not the"
          + " composite itself.");
    asset.originalUri = file.getAbsolutePath();
    asset.fingerprint = AssetFingerprint.of(asset.originalUri);
    dropCached(assetId);
  }

  private void dropCached(String assetId) {
    Iterator<Map.Entry<String, InputImageManager.Source>> entries = cache.entrySet().iterator();
    while (entries.hasNext()) {
      Map.Entry<String, InputImageManager.Source> entry = entries.next();
      if (!entry.getKey().startsWith(assetId + "|")) continue;
      cachedBytes -= bytes(entry.getValue());
      entries.remove();
    }
  }

  private File fileFor(Asset asset) {
    String uri = asset.embeddedUri != null && !asset.embeddedUri.isEmpty()
        ? asset.embeddedUri : asset.originalUri;
    if (uri == null || uri.trim().isEmpty()) return null;
    File file = new File(uri);
    return file.isFile() ? file : null;
  }

  private static String describe(Asset asset) {
    if (asset.seriesName != null && !asset.seriesName.isEmpty()) return asset.seriesName;
    if (asset.originalUri != null) return new File(asset.originalUri).getName();
    return asset.id;
  }
}

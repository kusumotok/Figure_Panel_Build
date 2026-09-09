package org.microscopy.panel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns the series of a Bio-Formats container into assets.
 *
 * <p>Acquisitions differ in how they store a field: some put every channel in one series, others
 * write one series per channel. Grouping consecutive series into a composite covers the second
 * case without the renderer ever knowing the difference.
 */
public final class ContainerImport {
  public static final class Plan {
    public final File file;
    public final List<BioFormatsReader.Series> series;
    /** Series that will not be imported at all. */
    public final List<Integer> skipped = new ArrayList<Integer>();
    /** How many consecutive kept series make up one image; 1 imports them individually. */
    public int channelsPerImage = 1;
    public List<String> channelLabels = new ArrayList<String>();

    public Plan(File file, List<BioFormatsReader.Series> series) {
      this.file = file;
      this.series = series;
    }

    public List<BioFormatsReader.Series> kept() {
      List<BioFormatsReader.Series> kept = new ArrayList<BioFormatsReader.Series>();
      for (BioFormatsReader.Series one : series)
        if (!skipped.contains(Integer.valueOf(one.index))) kept.add(one);
      return kept;
    }

    /** Series left over when the kept count is not a whole number of images. */
    public int remainder() {
      return channelsPerImage < 1 ? 0 : kept().size() % channelsPerImage;
    }

    public int imageCount() {
      return channelsPerImage < 1 ? 0 : kept().size() / channelsPerImage;
    }

    /** Marks every series whose name contains the given text, for skipping failed captures. */
    public int skipNamesContaining(String text) {
      if (text == null || text.trim().isEmpty()) return 0;
      int marked = 0;
      for (BioFormatsReader.Series one : series) {
        if (!one.name.contains(text)) continue;
        if (skipped.contains(Integer.valueOf(one.index))) continue;
        skipped.add(Integer.valueOf(one.index));
        marked++;
      }
      return marked;
    }
  }

  public Plan plan(File file) {
    return new Plan(file, new BioFormatsReader().list(file));
  }

  /**
   * Adds the planned assets to the document and returns the assets a caller should place: one per
   * image, so a composite rather than each of its parts.
   */
  public List<Asset> apply(Document document, Plan plan) {
    if (plan.channelsPerImage < 1)
      throw new IllegalArgumentException("An image needs at least one series.");
    List<BioFormatsReader.Series> kept = plan.kept();
    if (kept.isEmpty()) throw new IllegalArgumentException("No series were selected.");
    if (plan.remainder() != 0)
      throw new IllegalArgumentException(kept.size() + " series do not divide into groups of "
          + plan.channelsPerImage + "; " + plan.remainder() + " would be left over.");
    List<Asset> placeable = new ArrayList<Asset>();
    for (int start = 0; start < kept.size(); start += plan.channelsPerImage) {
      List<Asset> group = new ArrayList<Asset>();
      for (int i = 0; i < plan.channelsPerImage; i++) {
        Asset source = source(plan.file, kept.get(start + i));
        document.assets.put(source.id, source);
        group.add(source);
      }
      placeable.add(plan.channelsPerImage == 1 ? group.get(0) : composite(document, plan, group));
    }
    document.validate();
    return placeable;
  }

  private static Asset source(File file, BioFormatsReader.Series series) {
    Asset asset = new Asset();
    asset.id = UUID.randomUUID().toString();
    asset.kind = Asset.Kind.SOURCE;
    asset.originalUri = file.getAbsolutePath();
    asset.originalFormat = extension(file);
    asset.originalSeriesIndex = series.index;
    asset.seriesName = series.name;
    asset.sizeX = series.sizeX;
    asset.sizeY = series.sizeY;
    asset.sizeC = series.sizeC;
    asset.sizeZ = series.sizeZ;
    asset.sizeT = series.sizeT;
    asset.bitDepth = series.bitDepth;
    asset.pixelWidth = series.pixelWidth;
    asset.pixelHeight = series.pixelWidth;
    asset.unit = series.unit;
    asset.fingerprint = AssetFingerprint.of(asset.originalUri);
    return asset;
  }

  private Asset composite(Document document, Plan plan, List<Asset> group) {
    Asset first = group.get(0);
    Asset asset = new Asset();
    asset.id = UUID.randomUUID().toString();
    asset.kind = Asset.Kind.COMPOSITE;
    asset.originalFormat = first.originalFormat;
    asset.originalUri = first.originalUri;
    asset.seriesName = first.seriesName + " +" + (group.size() - 1);
    asset.sizeX = first.sizeX;
    asset.sizeY = first.sizeY;
    asset.sizeC = group.size();
    asset.sizeZ = first.sizeZ;
    asset.sizeT = first.sizeT;
    asset.bitDepth = first.bitDepth;
    asset.pixelWidth = first.pixelWidth;
    asset.pixelHeight = first.pixelHeight;
    asset.unit = first.unit;
    for (int i = 0; i < group.size(); i++) {
      String label = i < plan.channelLabels.size() ? plan.channelLabels.get(i)
          : "Channel " + (i + 1);
      asset.parts.add(new Asset.Part(group.get(i).id, 1, label));
      asset.bitDepth = Math.max(asset.bitDepth, group.get(i).bitDepth);
    }
    document.assets.put(asset.id, asset);
    return asset;
  }

  private static String extension(File file) {
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toUpperCase(java.util.Locale.ROOT);
  }

  /**
   * Which image each series lands in, and as which channel. Skipping a single failed capture
   * shifts everything after it, so the grouping has to be visible before it is applied rather
   * than inferred from a rule.
   *
   * @return image number and channel label per series index, or null for a skipped series
   */
  public static String[] grouping(Plan plan) {
    String[] assignment = new String[plan.series.size()];
    List<BioFormatsReader.Series> kept = plan.kept();
    for (int i = 0; i < kept.size(); i++) {
      int image = plan.channelsPerImage < 1 ? 0 : i / plan.channelsPerImage;
      int channel = plan.channelsPerImage < 1 ? 0 : i % plan.channelsPerImage;
      boolean complete = plan.channelsPerImage > 0
          && i < kept.size() - plan.remainder();
      String label = channel < plan.channelLabels.size()
          ? plan.channelLabels.get(channel) : "Channel " + (channel + 1);
      String text = plan.channelsPerImage == 1 ? "Image " + (image + 1)
          : "Image " + (image + 1) + " / " + label;
      for (int row = 0; row < plan.series.size(); row++)
        if (plan.series.get(row).index == kept.get(i).index)
          assignment[row] = complete ? text : "left over";
    }
    return assignment;
  }
}

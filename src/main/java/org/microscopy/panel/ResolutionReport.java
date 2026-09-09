package org.microscopy.panel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What resolution each picture actually reaches at its physical size. Feeds the export dialog
 * and the status bar, because a millimetre layout over pixel sources makes this the number that
 * decides whether an output is usable.
 */
public final class ResolutionReport {
  public enum Level { OK, LOW, VERY_LOW }

  public static final double LOW_DPI = 300, VERY_LOW_DPI = 150;

  public static final class Entry {
    public final String nodeId, name;
    public final int sourcePx;
    public final double widthMm, dpi;
    public final Level level;

    Entry(String nodeId, String name, int sourcePx, double widthMm, double dpi) {
      this.nodeId = nodeId; this.name = name; this.sourcePx = sourcePx;
      this.widthMm = widthMm; this.dpi = dpi;
      this.level = dpi < VERY_LOW_DPI ? Level.VERY_LOW : dpi < LOW_DPI ? Level.LOW : Level.OK;
    }

    @Override
    public String toString() {
      return String.format("%s: %d px over %.1f mm = %.0f dpi (%s)", name, sourcePx, widthMm, dpi, level);
    }
  }

  private final List<Entry> entries;

  private ResolutionReport(List<Entry> entries) {
    this.entries = Collections.unmodifiableList(entries);
  }

  public static ResolutionReport of(Document document, Page page, LayoutResult layout) {
    List<Entry> entries = new ArrayList<Entry>();
    collect(document, page.rootNode, layout, entries);
    return new ResolutionReport(entries);
  }

  private static void collect(Document document, Node node, LayoutResult layout, List<Entry> entries) {
    String assetId = node.content.kind == Content.Kind.SCIENTIFIC_IMAGE
        ? node.content.scientificImage.assetId
        : node.content.kind == Content.Kind.IMAGE ? node.content.image.assetId : null;
    if (assetId != null && layout.has(node.id)) {
      RectMm rect = layout.of(node.id);
      if (rect.width > 0) {
        Asset asset = document.asset(assetId);
        entries.add(new Entry(node.id, node.name, asset.sizeX, rect.width,
            Units.effectiveDpi(asset.sizeX, rect.width)));
      }
    }
    for (Node child : node.children) collect(document, child, layout, entries);
  }

  public List<Entry> entries() { return entries; }

  public List<Entry> below(Level level) {
    List<Entry> matches = new ArrayList<Entry>();
    for (Entry entry : entries) if (entry.level.compareTo(level) >= 0) matches.add(entry);
    return matches;
  }

  public boolean hasWarnings() { return !below(Level.LOW).isEmpty(); }

  public double lowestDpi() {
    double lowest = Double.MAX_VALUE;
    for (Entry entry : entries) lowest = Math.min(lowest, entry.dpi);
    return entries.isEmpty() ? 0 : lowest;
  }

  public String summary() {
    if (entries.isEmpty()) return "No pictures to check.";
    int low = below(Level.LOW).size(), veryLow = below(Level.VERY_LOW).size();
    if (low == 0) return String.format("All %d pictures reach %.0f dpi or better.",
        entries.size(), LOW_DPI);
    return String.format("%d of %d pictures are below %.0f dpi (%d below %.0f dpi).",
        low, entries.size(), LOW_DPI, veryLow, VERY_LOW_DPI);
  }

  /** Physical width at which a picture is drawn one output pixel per source pixel. */
  public static double fitToNativeWidthMm(int sourcePx, double dpi) {
    Units.checkDpi(dpi);
    if (sourcePx <= 0) throw new IllegalArgumentException("Invalid source width.");
    return Units.pxToMm(sourcePx, dpi);
  }
}

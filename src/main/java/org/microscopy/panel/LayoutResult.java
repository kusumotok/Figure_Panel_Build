package org.microscopy.panel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single source of layout truth: node id to millimetre rectangle. The canvas, hit testing
 * and every exporter read this rather than recomputing coordinates, which is exactly the
 * duplication that produced drifting cell maths in the original figure code.
 */
public final class LayoutResult {
  /** Where a container placed its grid lines, so a boundary can be grabbed and dragged. */
  public static final class Tracks {
    public final double[] columnStart, columnSize, rowStart, rowSize;
    public final double columnGapMm, rowGapMm;

    Tracks(double[] columnStart, double[] columnSize, double[] rowStart, double[] rowSize,
        double columnGapMm, double rowGapMm) {
      this.columnStart = columnStart; this.columnSize = columnSize;
      this.rowStart = rowStart; this.rowSize = rowSize;
      this.columnGapMm = columnGapMm; this.rowGapMm = rowGapMm;
    }

    public int columns() { return columnSize.length; }

    public int rows() { return rowSize.length; }

    public double[] starts(boolean columns) { return columns ? columnStart : rowStart; }

    public double[] sizes(boolean columns) { return columns ? columnSize : rowSize; }

    public double gap(boolean columns) { return columns ? columnGapMm : rowGapMm; }
  }

  private final Map<String, RectMm> rectangles;
  private final Map<String, Tracks> tracks;
  private final List<String> paintOrder;
  private final List<String> warnings;
  private final RectMm pageBox;

  LayoutResult(Map<String, RectMm> rectangles, Map<String, Tracks> tracks, List<String> paintOrder,
      List<String> warnings, RectMm pageBox) {
    this.rectangles = Collections.unmodifiableMap(new LinkedHashMap<String, RectMm>(rectangles));
    this.tracks = Collections.unmodifiableMap(new LinkedHashMap<String, Tracks>(tracks));
    this.paintOrder = Collections.unmodifiableList(new ArrayList<String>(paintOrder));
    this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    this.pageBox = pageBox;
  }

  /** The whole page including margins. */
  public RectMm pageBox() { return pageBox; }

  public RectMm of(String nodeId) {
    RectMm rect = rectangles.get(nodeId);
    if (rect == null) throw new IllegalArgumentException("Node was not laid out: " + nodeId);
    return rect;
  }

  public boolean has(String nodeId) { return rectangles.containsKey(nodeId); }

  public Map<String, RectMm> all() { return rectangles; }

  /** Grid lines of a container, or null when it is not a grid or has no children. */
  public Tracks tracksOf(String nodeId) { return tracks.get(nodeId); }

  /** Back to front, so painting and PPTX shape order agree. */
  public List<String> paintOrder() { return paintOrder; }

  public List<String> warnings() { return warnings; }

  /** Deepest node whose rectangle contains the point; the basis of canvas hit testing. */
  public String hit(Node root, double xMm, double yMm) {
    return hit(root, xMm, yMm, null);
  }

  private String hit(Node node, double xMm, double yMm, String found) {
    RectMm rect = rectangles.get(node.id);
    if (rect == null || !rect.contains(xMm, yMm)) return found;
    String best = node.id;
    for (Node child : node.children) {
      String inChild = hit(child, xMm, yMm, null);
      if (inChild != null) best = inChild;
    }
    return best;
  }
}

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
  private final Map<String, RectMm> rectangles;
  private final List<String> paintOrder;
  private final List<String> warnings;
  private final RectMm pageBox;

  LayoutResult(Map<String, RectMm> rectangles, List<String> paintOrder, List<String> warnings,
      RectMm pageBox) {
    this.rectangles = Collections.unmodifiableMap(new LinkedHashMap<String, RectMm>(rectangles));
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

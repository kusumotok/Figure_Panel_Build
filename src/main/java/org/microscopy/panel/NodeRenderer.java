package org.microscopy.panel;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.microscopy.figure.InputImageManager;

/**
 * Paints nodes from a {@link LayoutResult} into a pixel surface. Rendered pictures are cached by
 * node and output size, so a node that straddles several output tiles is only rendered once.
 */
public final class NodeRenderer {
  private static final int CACHE_LIMIT = 64;

  private final SourceProvider sources;
  private StyleResolver styles;
  private Page styledPage;
  private final ScientificImageRenderer images = new ScientificImageRenderer();
  private final TextRenderer text = new TextRenderer();
  private final java.util.Set<String> overflowed = new java.util.LinkedHashSet<String>();
  private final LinkedHashMap<String, BufferedImage> cache =
      new LinkedHashMap<String, BufferedImage>(16, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
          return size() > CACHE_LIMIT;
        }
      };

  public NodeRenderer(SourceProvider sources) { this.sources = sources; }

  public void clearCache() { cache.clear(); }

  /** Text nodes whose content did not fit, gathered during the last paint. */
  public java.util.Set<String> overflowedNodes() { return overflowed; }

  /**
   * @param originPxX pixel coordinate of the surface's left edge within the page
   * @param clipMm only nodes touching this rectangle are painted; null paints everything
   */
  public void paint(Graphics2D g, Document document, Page page, LayoutResult layout,
      RenderTarget target, RectMm clipMm, int originPxX, int originPxY) {
    styles = new StyleResolver(document);
    styledPage = page;
    Map<String, Node> byId = new HashMap<String, Node>();
    index(page.rootNode, byId);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    for (String id : layout.paintOrder()) {
      Node node = byId.get(id);
      if (node == null) continue;
      RectMm rect = layout.of(id);
      if (clipMm != null && !rect.intersects(clipMm)) continue;
      if (isHidden(node)) continue;
      int[] px = rect.toPx(target.dpi);
      paintNode(g, document, node, px[0] - originPxX, px[1] - originPxY,
          Math.max(1, px[2]), Math.max(1, px[3]), target, shapesFor(page, layout, node, rect, target));
    }
  }

  private static void index(Node node, Map<String, Node> out) {
    out.put(node.id, node);
    for (Node child : node.children) index(child, out);
  }

  private boolean isHidden(Node node) {
    Boolean visible = styles.flag(styledPage, node.id, Prop.VISIBLE);
    return visible != null && !visible;
  }

  private void paintNode(Graphics2D g, Document document, Node node, int x, int y,
      int width, int height, RenderTarget target, java.util.List<TextRenderer.FlowShape> shapes) {
    background(g, node, x, y, width, height);
    switch (node.content.kind) {
      case SCIENTIFIC_IMAGE:
        g.drawImage(scientific(node, width, height), x, y, null);
        break;
      case IMAGE:
        BufferedImage picture = picture(document, node);
        if (picture != null) g.drawImage(picture, x, y, width, height, null);
        break;
      case TEXT:
        paintText(g, node, x, y, width, height, target, shapes);
        break;
      case SHAPE:
        paintShape(g, node, x, y, width, height);
        break;
      default:
        break;
    }
    border(g, node, x, y, width, height, target);
  }

  /** Overrides, then the node's style, then inheritance: all of it through one resolver. */
  private Color color(Node node, String key) {
    return styles.color(styledPage, node.id, key);
  }

  private double number(Node node, String key) {
    Double value = styles.number(styledPage, node.id, key);
    return value == null ? 0 : value;
  }

  private void background(Graphics2D g, Node node, int x, int y, int width, int height) {
    Color fill = color(node, Prop.FILL);
    if (fill == null) return;
    g.setColor(fill);
    double radius = number(node, Prop.CORNER_RADIUS_MM);
    if (radius > 0) g.fillRoundRect(x, y, width, height, (int) radius, (int) radius);
    else g.fillRect(x, y, width, height);
  }

  private void border(Graphics2D g, Node node, int x, int y, int width, int height,
      RenderTarget target) {
    Color line = color(node, Prop.BORDER_COLOR);
    double widthMm = number(node, Prop.BORDER_WIDTH_MM);
    if (line == null || widthMm <= 0) return;
    int stroke = Math.max(1, target.px(widthMm));
    g.setColor(line);
    g.setStroke(new BasicStroke(stroke));
    double radius = number(node, Prop.CORNER_RADIUS_MM);
    if (radius > 0)
      g.drawRoundRect(x + stroke / 2, y + stroke / 2, width - stroke, height - stroke,
          (int) radius, (int) radius);
    else g.drawRect(x + stroke / 2, y + stroke / 2, width - stroke, height - stroke);
  }

  private BufferedImage scientific(Node node, int width, int height) {
    String key = node.id + "|" + width + "x" + height;
    BufferedImage cached = cache.get(key);
    if (cached != null) return cached;
    ScientificImageContent content = node.content.scientificImage;
    InputImageManager.Source source = sources.source(content.assetId, content.z, content.t);
    BufferedImage rendered = images.render(content, source, width, height);
    cache.put(key, rendered);
    return rendered;
  }

  private BufferedImage picture(Document document, Node node) {
    Asset asset = document.asset(node.content.image.assetId);
    String key = "picture|" + asset.id;
    BufferedImage cached = cache.get(key);
    if (cached != null) return cached;
    String uri = asset.embeddedUri != null && !asset.embeddedUri.isEmpty()
        ? asset.embeddedUri : asset.originalUri;
    if (uri == null) return null;
    try {
      BufferedImage read = ImageIO.read(new File(uri));
      if (read != null) cache.put(key, read);
      return read;
    } catch (Exception ex) {
      // A missing logo must not abort a poster render; the relink dialog reports it instead.
      return null;
    }
  }

  private void paintText(Graphics2D g, Node node, int x, int y, int width, int height,
      RenderTarget target, java.util.List<TextRenderer.FlowShape> shapes) {
    TextContent content = node.content.text;
    Graphics2D local = (Graphics2D) g.create();
    try {
      Color fallback = color(node, Prop.TEXT_COLOR);
      local.setColor(fallback == null ? TextRenderer.defaultColor(target) : fallback);
      float boxWidth = width, boxHeight = height;
      if (content.rotation != TextContent.Rotation.NONE) {
        // Sideways text lays out along the tall edge, then the whole box is turned.
        boxWidth = height;
        boxHeight = width;
        double angle = content.rotation == TextContent.Rotation.CLOCKWISE_90
            ? Math.PI / 2 : -Math.PI / 2;
        local.translate(x + width / 2.0, y + height / 2.0);
        local.rotate(angle);
        local.translate(-boxWidth / 2.0, -boxHeight / 2.0);
      } else {
        local.translate(x, y);
      }
      TextRenderer.Defaults defaults = textDefaults(node);
      TextRenderer.Flowed flowed = shapes != null && !shapes.isEmpty()
          ? text.flow(local, content, shapes, target.dpi, Align.START, defaults)
          : text.flow(local, content,
              java.util.Collections.singletonList(TextRenderer.rectangle(boxWidth, boxHeight)),
              target.dpi, Align.START, defaults);
      // A box centres its block, matching how figure labels sit in their band. A region has
      // already placed every line where it belongs.
      if (shapes == null || shapes.isEmpty())
        local.translate(0, Math.max(0, (boxHeight - flowed.usedHeight) / 2.0));
      // Overflow is only knowable once the text is measured, so the renderer collects it and the
      // window reports it; the default policy is to say so rather than to silently reflow.
      if (flowed.overflowed) overflowed.add(node.id);
      text.draw(local, flowed);
    } finally {
      local.dispose();
    }
  }

  private void paintShape(Graphics2D g, Node node, int x, int y, int width, int height) {
    ShapeContent shape = node.content.shape;
    Color fill = color(node, Prop.FILL);
    if (fill != null && shape.shape == ShapeContent.Shape.ELLIPSE) {
      g.setColor(fill);
      g.fillOval(x, y, width, height);
    }
    Color line = color(node, Prop.BORDER_COLOR);
    if (line == null) return;
    g.setColor(line);
    if (shape.shape == ShapeContent.Shape.ELLIPSE) g.drawOval(x, y, width, height);
    else if (shape.shape == ShapeContent.Shape.LINE || shape.shape == ShapeContent.Shape.ARROW)
      g.drawLine(x, y + height / 2, x + width, y + height / 2);
  }

  /** Exposed for the canvas, which draws a single node into its own buffer. */
  public AffineTransform pageToPixels(double dpi) {
    double scale = dpi / Units.MM_PER_INCH;
    return AffineTransform.getScaleInstance(scale, scale);
  }

  /**
   * Where a text node may put its lines. A plain box uses its own rectangle; a node with a flow
   * region uses the islands of that region, so the text runs down beside a figure and widens
   * where the figure ends rather than being clipped into a column.
   */
  private static java.util.List<TextRenderer.FlowShape> shapesFor(Page page, LayoutResult layout,
      Node node, RectMm rect, RenderTarget target) {
    if (node.content.kind != Content.Kind.TEXT) return null;
    FlowRegion region = node.content.text.flowRegion;
    // A region and a right angle together have no sensible meaning; the region wins.
    if (region == null || region.cells.isEmpty()
        || node.content.text.rotation != TextContent.Rotation.NONE) return null;
    Node parent = page.rootNode.parentOf(node.id);
    LayoutResult.Tracks tracks = parent == null ? null : layout.tracksOf(parent.id);
    if (tracks == null) return null;
    java.util.List<FlowRegionGeometry.Island> islands =
        FlowRegionGeometry.ordered(FlowRegionGeometry.islands(tracks, region), region);
    java.util.List<TextRenderer.FlowShape> shapes =
        new java.util.ArrayList<TextRenderer.FlowShape>();
    for (FlowRegionGeometry.Island island : islands)
      shapes.add(TextRenderer.island(island, rect.x, rect.y, target.dpi));
    return shapes;
  }

  /**
   * What a run falls back to when it says nothing itself: the node's style, resolved through its
   * tokens and inherited from its ancestors.
   */
  private TextRenderer.Defaults textDefaults(Node node) {
    TextRenderer.Defaults defaults = new TextRenderer.Defaults();
    defaults.fontSizePt = styles.number(styledPage, node.id, Prop.FONT_SIZE_PT);
    defaults.latinFamily = styles.text(styledPage, node.id, Prop.LATIN_FAMILY);
    defaults.eaFamily = styles.text(styledPage, node.id, Prop.EA_FAMILY);
    defaults.bold = styles.flag(styledPage, node.id, Prop.BOLD);
    defaults.italic = styles.flag(styledPage, node.id, Prop.ITALIC);
    defaults.underline = styles.flag(styledPage, node.id, Prop.UNDERLINE);
    java.awt.Color colour = styles.color(styledPage, node.id, Prop.TEXT_COLOR);
    if (colour != null) defaults.colorHex = PropertyValue.hex(colour);
    return defaults;
  }
}

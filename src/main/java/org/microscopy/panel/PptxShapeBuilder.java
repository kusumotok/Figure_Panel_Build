package org.microscopy.panel;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.microscopy.figure.InputImageManager;

/**
 * Turns one page of a {@link LayoutResult} into a slide shape tree. Text, scale bars and frames
 * stay vector; only the microscopy pictures become raster, and at save time those are written at
 * preview resolution so that saving stays cheap.
 */
final class PptxShapeBuilder {
  /** Lets a save reuse a media part from the file it is about to replace. */
  interface MediaCache {
    byte[] existing(String name);
  }

  static final class Slide {
    final StringBuilder shapes = new StringBuilder();
    final StringBuilder relationships = new StringBuilder();
    final Map<String, byte[]> media = new LinkedHashMap<String, byte[]>();
    int reusedMedia, renderedMedia;
  }

  private final SourceProvider sources;
  private StyleResolver styles;
  private Page styledPage;
  private final ScientificImageRenderer images = new ScientificImageRenderer();
  private int nextId = 2;

  PptxShapeBuilder(SourceProvider sources) { this.sources = sources; }

  Slide build(Document document, Page page, LayoutResult layout, double dpi, String qualityLabel,
      MediaCache cache) throws IOException {
    styles = new StyleResolver(document);
    styledPage = page;
    Slide slide = new Slide();
    Map<String, Node> byId = new HashMap<String, Node>();
    index(page.rootNode, byId);
    Map<String, String> relByName = new HashMap<String, String>();
    for (String id : layout.paintOrder()) {
      Node node = byId.get(id);
      if (node == null) continue;
      RectMm rect = layout.of(id);
      if (rect.width <= 0 || rect.height <= 0) continue;
      switch (node.content.kind) {
        case SCIENTIFIC_IMAGE:
          picture(slide, document, node, rect, dpi, qualityLabel, cache, relByName);
          break;
        case TEXT:
          if (node.content.text.flowRegion == null || node.content.text.flowRegion.cells.isEmpty())
            text(slide, node, rect, node.content.text.paragraphs, name(node));
          else regionText(slide, page, layout, node, rect);
          break;
        case SHAPE:
          shape(slide, node, rect);
          break;
        default:
          frame(slide, node, rect);
          break;
      }
    }
    return slide;
  }

  private static void index(Node node, Map<String, Node> out) {
    out.put(node.id, node);
    for (Node child : node.children) index(child, out);
  }

  private static String name(Node node) {
    return node.name == null || node.name.isEmpty() ? node.id : node.name;
  }

  private void picture(Slide slide, Document document, Node node, RectMm rect, double dpi,
      String qualityLabel, MediaCache cache, Map<String, String> relByName) throws IOException {
    ScientificImageContent content = node.content.scientificImage;
    int width = Math.max(1, Units.mmToPx(rect.width, dpi));
    int height = Math.max(1, Units.mmToPx(rect.height, dpi));
    String key = content.assetId + "|" + content.z + "/" + content.t + "|" + content.resample
        + "|" + width + "x" + height + "|" + new DocumentSerializer().toJsonFragment(content.figureConfig);
    String media = "p-" + Ooxml.hash(key) + ".png";
    String relId = relByName.get(media);
    if (relId == null) {
      byte[] bytes = slide.media.get(media);
      if (bytes == null) {
        byte[] previous = cache == null ? null : cache.existing("ppt/media/" + media);
        // Only reuse a part that is still the picture we expect: a thumbnail is derived data, so
        // one that PowerPoint recompressed or that was damaged must be rendered again.
        if (isPng(previous, width, height)) bytes = previous;
      }
      if (bytes == null) {
        InputImageManager.Source source = sources.source(content.assetId, content.z, content.t);
        BufferedImage rendered = images.render(content, source, width, height);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(rendered, "png", png);
        bytes = png.toByteArray();
        slide.renderedMedia++;
      } else {
        slide.reusedMedia++;
      }
      slide.media.put(media, bytes);
      relId = "rIdPic" + slide.media.size();
      relByName.put(media, relId);
      slide.relationships.append(Ooxml.relationship(relId, "image", "../media/" + media));
    }
    slide.shapes.append("<p:pic><p:nvPicPr><p:cNvPr id=\"").append(nextId++)
        .append("\" name=\"").append(Ooxml.xml(name(node) + " — " + qualityLabel))
        .append("\" descr=\"").append(Ooxml.xml(node.id))
        .append("\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/>")
        .append("</p:nvPicPr><p:blipFill><a:blip r:embed=\"").append(relId)
        .append("\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr>")
        .append(Ooxml.transform(rect, 0))
        .append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>")
        .append(outline(node)).append("</p:spPr></p:pic>");
  }

  /**
   * The same resolver the renderer uses, so a style that shows on screen is the style that
   * reaches PowerPoint.
   */
  private PropertyValue literal(Node node, String key) {
    return styles == null ? null : styles.resolve(styledPage, node.id, key);
  }

  private String outline(Node node) {
    PropertyValue color = literal(node, Prop.BORDER_COLOR);
    PropertyValue width = literal(node, Prop.BORDER_WIDTH_MM);
    if (color == null || width == null || width.asDouble() <= 0) return "<a:ln><a:noFill/></a:ln>";
    return "<a:ln w=\"" + Units.mmToEmu(width.asDouble()) + "\"><a:solidFill><a:srgbClr val=\""
        + hex(color) + "\"/></a:solidFill></a:ln>";
  }

  private String fill(Node node) {
    PropertyValue color = literal(node, Prop.FILL);
    return color == null ? "<a:noFill/>"
        : "<a:solidFill><a:srgbClr val=\"" + hex(color) + "\"/></a:solidFill>";
  }

  private static String hex(PropertyValue value) {
    return String.format("%06X", value.asColor().getRGB() & 0xffffff);
  }

  /** A container only needs a shape of its own when it actually paints something. */
  private void frame(Slide slide, Node node, RectMm rect) {
    if (literal(node, Prop.FILL) == null && literal(node, Prop.BORDER_COLOR) == null) return;
    PropertyValue radius = literal(node, Prop.CORNER_RADIUS_MM);
    String geometry = radius != null && radius.asDouble() > 0 ? "roundRect" : "rect";
    slide.shapes.append("<p:sp><p:nvSpPr><p:cNvPr id=\"").append(nextId++)
        .append("\" name=\"").append(Ooxml.xml(name(node)))
        .append("\" descr=\"").append(Ooxml.xml(node.id))
        .append("\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>")
        .append(Ooxml.transform(rect, 0))
        .append("<a:prstGeom prst=\"").append(geometry).append("\"><a:avLst/></a:prstGeom>")
        .append(fill(node)).append(outline(node)).append("</p:spPr></p:sp>");
  }

  private void shape(Slide slide, Node node, RectMm rect) {
    String geometry;
    switch (node.content.shape.shape) {
      case ELLIPSE: geometry = "ellipse"; break;
      case LINE: geometry = "line"; break;
      case ARROW: geometry = "straightConnector1"; break;
      default: geometry = "rect"; break;
    }
    slide.shapes.append("<p:sp><p:nvSpPr><p:cNvPr id=\"").append(nextId++)
        .append("\" name=\"").append(Ooxml.xml(name(node)))
        .append("\" descr=\"").append(Ooxml.xml(node.id))
        .append("\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>")
        .append(Ooxml.transform(rect, 0))
        .append("<a:prstGeom prst=\"").append(geometry).append("\"><a:avLst/></a:prstGeom>")
        .append(fill(node)).append(outline(node)).append("</p:spPr></p:sp>");
  }

  /**
   * Text stays editable. Rotation is emitted the way PowerPoint expects it: the box is given the
   * swapped extent about the same centre, then turned by a right angle.
   */
  private void text(Slide slide, Node node, RectMm rect, java.util.List<Paragraph> paragraphs,
      String shapeName) {
    TextContent content = node.content.text;
    int rotation = 0;
    RectMm box = rect;
    if (content.rotation != TextContent.Rotation.NONE) {
      rotation = content.rotation == TextContent.Rotation.CLOCKWISE_90 ? 5400000 : 16200000;
      double cx = rect.x + rect.width / 2, cy = rect.y + rect.height / 2;
      box = new RectMm(cx - rect.height / 2, cy - rect.width / 2, rect.height, rect.width);
    }
    StringBuilder body = new StringBuilder();
    for (Paragraph paragraph : paragraphs) {
      body.append("<a:p><a:pPr algn=\"").append(align(paragraph.align)).append("\"/>");
      for (Run run : paragraph.runs) body.append(run(node, run));
      body.append("</a:p>");
    }
    if (paragraphs.isEmpty()) body.append("<a:p/>");
    slide.shapes.append("<p:sp><p:nvSpPr><p:cNvPr id=\"").append(nextId++)
        .append("\" name=\"").append(Ooxml.xml(shapeName))
        .append("\" descr=\"").append(Ooxml.xml(node.id))
        .append("\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr>")
        .append(Ooxml.transform(box, rotation))
        .append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>")
        .append(fill(node)).append(outline(node)).append("</p:spPr>")
        .append("<p:txBody><a:bodyPr wrap=\"square\" lIns=\"0\" rIns=\"0\" tIns=\"0\" bIns=\"0\"")
        .append(" anchor=\"ctr\"><a:noAutofit/></a:bodyPr><a:lstStyle/>")
        .append(body).append("</p:txBody></p:sp>");
  }

  private static String align(Align align) {
    if (align == Align.CENTER) return "ctr";
    if (align == Align.END) return "r";
    return "l";
  }

  private String run(Node node, Run run) {
    StringBuilder properties = new StringBuilder("<a:rPr lang=\"en-US\"");
    // A run override wins; otherwise the node's resolved style supplies the value.
    PropertyValue styleSize = literal(node, Prop.FONT_SIZE_PT);
    double size = run.fontSizePt != null ? run.fontSizePt
        : styleSize != null ? styleSize.asDouble() : 12;
    properties.append(" sz=\"").append(Math.max(100, (int) Math.round(size * 100))).append("\"");
    if (Boolean.TRUE.equals(flagOr(run.bold, node, Prop.BOLD))) properties.append(" b=\"1\"");
    if (Boolean.TRUE.equals(flagOr(run.italic, node, Prop.ITALIC))) properties.append(" i=\"1\"");
    if (Boolean.TRUE.equals(flagOr(run.underline, node, Prop.UNDERLINE)))
      properties.append(" u=\"sng\"");
    if (Boolean.TRUE.equals(run.superscript)) properties.append(" baseline=\"30000\"");
    if (Boolean.TRUE.equals(run.subscript)) properties.append(" baseline=\"-25000\"");
    properties.append(">");
    String color = run.colorHex;
    if (color == null) {
      PropertyValue textColor = literal(node, Prop.TEXT_COLOR);
      if (textColor != null) color = textColor.text();
    }
    if (color != null)
      properties.append("<a:solidFill><a:srgbClr val=\"")
          .append(color.replace("#", "").toUpperCase(java.util.Locale.ROOT))
          .append("\"/></a:solidFill>");
    // Latin and East Asian faces stay separate, as PowerPoint models them.
    properties.append("<a:latin typeface=\"")
        .append(Ooxml.xml(textOr(run.latinFamily, node, Prop.LATIN_FAMILY, "Arial")))
        .append("\"/>");
    properties.append("<a:ea typeface=\"")
        .append(Ooxml.xml(textOr(run.eaFamily, node, Prop.EA_FAMILY, "Yu Gothic")))
        .append("\"/>");
    properties.append("</a:rPr>");
    return "<a:r>" + properties + "<a:t xml:space=\"preserve\">" + Ooxml.xml(run.text) + "</a:t></a:r>";
  }

  private Boolean flagOr(Boolean override, Node node, String key) {
    if (override != null) return override;
    PropertyValue value = literal(node, key);
    return value == null ? null : Boolean.valueOf(value.asBoolean());
  }

  private String textOr(String override, Node node, String key, String fallback) {
    if (override != null) return override;
    PropertyValue value = literal(node, key);
    return value == null ? fallback : value.text();
  }

  /** A PNG of exactly the expected size, checked straight from the IHDR header. */
  static boolean isPng(byte[] bytes, int width, int height) {
    if (bytes == null || bytes.length < 24) return false;
    int[] signature = {137, 80, 78, 71, 13, 10, 26, 10};
    for (int i = 0; i < signature.length; i++)
      if ((bytes[i] & 255) != signature[i]) return false;
    return readInt(bytes, 16) == width && readInt(bytes, 20) == height;
  }

  private static int readInt(byte[] bytes, int offset) {
    return (bytes[offset] & 255) << 24 | (bytes[offset + 1] & 255) << 16
        | (bytes[offset + 2] & 255) << 8 | (bytes[offset + 3] & 255);
  }

  /**
   * PowerPoint has no non-rectangular text frame, so a flow region is written as one editable
   * text box per rectangle of the region. The region itself is kept losslessly in the project
   * part, so nothing is lost; what changes is that the split between boxes is fixed at save time
   * and PowerPoint reflows within each box rather than across the whole L.
   *
   * <p>The parts are named nodeId#partN so that reading edits back can skip them: putting text
   * from several boxes back into one flowing paragraph has no unique answer.
   */
  private void regionText(Slide slide, Page page, LayoutResult layout, Node node, RectMm rect) {
    TextContent content = node.content.text;
    Node parent = page.rootNode.parentOf(node.id);
    LayoutResult.Tracks tracks = parent == null ? null : layout.tracksOf(parent.id);
    if (tracks == null) {
      text(slide, node, rect, content.paragraphs, name(node));
      return;
    }
    List<FlowRegionGeometry.Island> islands = FlowRegionGeometry.ordered(
        FlowRegionGeometry.islands(tracks, content.flowRegion), content.flowRegion);
    List<RectMm> boxes = new ArrayList<RectMm>();
    List<TextRenderer.FlowShape> shapes = new ArrayList<TextRenderer.FlowShape>();
    double dpi = 150;
    for (FlowRegionGeometry.Island island : islands) {
      boxes.addAll(island.rectangles);
      shapes.add(TextRenderer.island(island, rect.x, rect.y, dpi));
    }
    if (boxes.size() <= 1) {
      text(slide, node, boxes.isEmpty() ? rect : boxes.get(0), content.paragraphs, name(node));
      return;
    }
    java.awt.Graphics2D scratch =
        new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
    TextRenderer.Flowed flowed;
    try {
      flowed = new TextRenderer().flow(scratch, content, shapes, dpi, Align.START);
    } finally {
      scratch.dispose();
    }
    int part = 0;
    for (RectMm box : boxes) {
      List<Paragraph> pieces = piecesIn(content, flowed, box, rect, dpi);
      part++;
      if (pieces.isEmpty()) continue;
      text(slide, node, box, pieces, name(node) + "#part" + part);
    }
  }

  /** The paragraphs whose lines landed inside one rectangle, sliced to just those characters. */
  private static List<Paragraph> piecesIn(TextContent content, TextRenderer.Flowed flowed,
      RectMm box, RectMm origin, double dpi) {
    List<Paragraph> pieces = new ArrayList<Paragraph>();
    int currentParagraph = -1, from = -1, to = -1;
    for (TextRenderer.Line line : flowed.lines) {
      double xMm = origin.x + Units.pxToMm(line.x, dpi);
      double yMm = origin.y + Units.pxToMm(line.baseline - line.layout.getAscent() / 2, dpi);
      if (!box.contains(xMm, yMm)) continue;
      if (line.paragraph != currentParagraph) {
        if (currentParagraph >= 0)
          pieces.add(content.paragraphs.get(currentParagraph).slice(from, to));
        currentParagraph = line.paragraph;
        from = line.start;
      }
      to = line.limit;
    }
    if (currentParagraph >= 0)
      pieces.add(content.paragraphs.get(currentParagraph).slice(from, to));
    return pieces;
  }
}

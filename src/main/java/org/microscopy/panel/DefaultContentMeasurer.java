package org.microscopy.panel;

/**
 * Pixel content is measured at a nominal print resolution; text is estimated from its font size
 * until the flow engine takes over.
 */
public class DefaultContentMeasurer implements ContentMeasurer {
  private final double nominalDpi;
  private final LinkedProjects attachments;

  public DefaultContentMeasurer() { this(NOMINAL_DPI, new LinkedProjects()); }

  public DefaultContentMeasurer(double nominalDpi) { this(nominalDpi, new LinkedProjects()); }

  public DefaultContentMeasurer(double nominalDpi, LinkedProjects attachments) {
    Units.checkDpi(nominalDpi);
    this.nominalDpi = nominalDpi;
    this.attachments = attachments;
  }

  @Override
  public SizeMm measure(Document document, Node node, double availableWidthMm) {
    switch (node.content.kind) {
      case SCIENTIFIC_IMAGE:
        return pixels(document.asset(node.content.scientificImage.assetId));
      case IMAGE:
        return pixels(document.asset(node.content.image.assetId));
      case TEXT:
        return text(node.content.text, availableWidthMm);
      case PROJECT:
        return attached(node.content.project);
      default:
        return SizeMm.ZERO;
    }
  }

  private SizeMm pixels(Asset asset) {
    return new SizeMm(Units.pxToMm(asset.sizeX, nominalDpi), Units.pxToMm(asset.sizeY, nominalDpi));
  }

  /**
   * A deliberately crude estimate: half an em per character, 1.2 line spacing. Good enough to
   * give an Auto row a plausible height; the flow engine measures for real in its own pass.
   */
  private SizeMm text(TextContent content, double availableWidthMm) {
    double lines = 0, widest = 0;
    for (Paragraph paragraph : content.paragraphs) {
      double sizePt = 12;
      for (Run run : paragraph.runs) if (run.fontSizePt != null) sizePt = Math.max(sizePt, run.fontSizePt);
      double emMm = Units.ptToMm(sizePt);
      double widthMm = paragraph.plainText().length() * emMm * 0.5;
      widest = Math.max(widest, widthMm);
      double wrapped = availableWidthMm > 0 ? Math.ceil(widthMm / availableWidthMm) : 1;
      lines += Math.max(1, wrapped) * emMm * 1.2;
    }
    if (content.paragraphs.isEmpty()) return SizeMm.ZERO;
    double width = availableWidthMm > 0 ? Math.min(widest, availableWidthMm) : widest;
    boolean sideways = content.rotation != TextContent.Rotation.NONE;
    return sideways ? new SizeMm(lines, width) : new SizeMm(width, lines);
  }

  /**
   * An attached project is as big as the page it holds, which is what gives the cell its shape.
   * One that cannot be read gets a small placeholder rather than collapsing to nothing, so the
   * gap is visible and can be clicked.
   */
  private SizeMm attached(ProjectContent content) {
    LinkedProjects.Attached open = attachments.open(content);
    if (open == null) return new SizeMm(40, 30);
    return new SizeMm(open.box.width, open.box.height);
  }
}

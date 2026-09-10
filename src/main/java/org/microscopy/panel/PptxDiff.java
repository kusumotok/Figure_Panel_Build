package org.microscopy.panel;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Compares the shapes in a project PPTX with the document that produced them.
 *
 * <p>The project part is authoritative, so this never rebuilds the document from the shapes. It
 * reports what changed in PowerPoint and imports only what maps back unambiguously: text and the
 * colour, size and face it was given. Geometry is reported and left alone, because a position
 * that came from a fraction or an Auto track cannot be turned back into one.
 */
public final class PptxDiff {
  public static final class Change {
    public final String nodeId, nodeName, description;
    public final boolean importable;

    Change(String nodeId, String nodeName, String description, boolean importable) {
      this.nodeId = nodeId; this.nodeName = nodeName; this.description = description;
      this.importable = importable;
    }

    @Override
    public String toString() { return nodeName + ": " + description; }
  }

  public static final class Report {
    public final List<Change> changes = new ArrayList<Change>();

    public List<Change> importable() { return filter(true); }

    public List<Change> ignored() { return filter(false); }

    private List<Change> filter(boolean importable) {
      List<Change> matching = new ArrayList<Change>();
      for (Change change : changes) if (change.importable == importable) matching.add(change);
      return matching;
    }

    public boolean isEmpty() { return changes.isEmpty(); }

    /** The wording the plan asked for: counts first, then what was left out. */
    public String summary() {
      if (changes.isEmpty()) return "No PowerPoint edits detected.";
      StringBuilder text = new StringBuilder("PowerPoint edits detected\n\n");
      text.append(importable().size()).append(" compatible change(s) imported\n");
      text.append(ignored().size()).append(" unsupported change(s) ignored");
      if (!ignored().isEmpty()) {
        text.append("\n\nUnsupported:");
        for (Change change : ignored()) text.append("\n- ").append(change);
      }
      return text.toString();
    }
  }

  /** Reads the slides and works out what differs from the document. */
  public Report compare(File file, Document document) throws java.io.IOException {
    Report report = new Report();
    ZipFile zip = new ZipFile(file);
    try {
      java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        if (!name.startsWith("ppt/slides/slide") || !name.endsWith(".xml")) continue;
        InputStream in = zip.getInputStream(entry);
        try {
          compareSlide(parse(in), document, report);
        } catch (Exception ex) {
          throw new java.io.IOException("Cannot read " + name + ": " + ex.getMessage(), ex);
        } finally {
          in.close();
        }
      }
    } finally {
      zip.close();
    }
    return report;
  }

  private static org.w3c.dom.Document parse(InputStream in) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    return factory.newDocumentBuilder().parse(in);
  }

  private void compareSlide(org.w3c.dom.Document slide, Document document, Report report) {
    NodeList properties = slide.getElementsByTagNameNS(Ooxml.PML, "cNvPr");
    for (int i = 0; i < properties.getLength(); i++) {
      Element cNvPr = (Element) properties.item(i);
      String nodeId = cNvPr.getAttribute("descr");
      String shapeName = cNvPr.getAttribute("name");
      if (nodeId == null || nodeId.isEmpty()) continue;
      Node node = find(document, nodeId);
      if (node == null) {
        report.changes.add(new Change(nodeId, shapeName, "a shape that is not in the project",
            false));
        continue;
      }
      Element shape = shapeAbove(cNvPr);
      if (shape == null) continue;
      if (shapeName.contains("#part")) {
        // Several boxes hold one flowing paragraph; there is no unique way back.
        report.changes.add(new Change(nodeId, name(node),
            "part of a non-rectangular text area, so its text was left alone", false));
        continue;
      }
      transform(shape, node, report);
      if (node.content.kind == Content.Kind.TEXT) text(shape, node, report);
    }
  }

  /** The p:sp or p:pic that owns these non-visual properties. */
  private static Element shapeAbove(Element cNvPr) {
    org.w3c.dom.Node at = cNvPr.getParentNode();
    while (at instanceof Element) {
      String local = at.getLocalName();
      if ("sp".equals(local) || "pic".equals(local)) return (Element) at;
      at = at.getParentNode();
    }
    return null;
  }

  private void transform(Element shape, Node node, Report report) {
    NodeList transforms = shape.getElementsByTagNameNS(Ooxml.DML, "xfrm");
    if (transforms.getLength() == 0) return;
    Element xfrm = (Element) transforms.item(0);
    String rotation = xfrm.getAttribute("rot");
    boolean rotated = rotation != null && !rotation.isEmpty() && !"0".equals(rotation)
        && !isRightAngle(rotation, node);
    if (rotated)
      report.changes.add(new Change(node.id, name(node),
          "rotation " + degrees(rotation) + " degrees", false));
    if ("1".equals(xfrm.getAttribute("flipH")) || "1".equals(xfrm.getAttribute("flipV")))
      report.changes.add(new Change(node.id, name(node), "flipped", false));
  }

  /** Right-angle text keeps the rotation this tool wrote, so it is not a PowerPoint edit. */
  private static boolean isRightAngle(String rotation, Node node) {
    if (node.content.kind != Content.Kind.TEXT
        || node.content.text.rotation == TextContent.Rotation.NONE) return false;
    return "5400000".equals(rotation) || "16200000".equals(rotation);
  }

  private static String degrees(String rotation) {
    try {
      return String.valueOf(Math.round(Long.parseLong(rotation) / 60000.0));
    } catch (NumberFormatException ex) {
      return rotation;
    }
  }

  private void text(Element shape, Node node, Report report) {
    StringBuilder written = new StringBuilder();
    NodeList runs = shape.getElementsByTagNameNS(Ooxml.DML, "t");
    for (int i = 0; i < runs.getLength(); i++) written.append(runs.item(i).getTextContent());
    String now = written.toString();
    String before = node.content.text.plainText();
    if (now.equals(before)) return;
    report.changes.add(new Change(node.id, name(node),
        "text changed to " + shorten(now), true));
  }

  private static String shorten(String text) {
    String single = text.replace('\n', ' ');
    return single.length() <= 48 ? "\"" + single + "\"" : "\"" + single.substring(0, 45) + "...\"";
  }

  private static Node find(Document document, String nodeId) {
    for (Page page : document.pages) {
      Node node = page.rootNode.find(nodeId);
      if (node != null) return node;
    }
    return null;
  }

  private static String name(Node node) {
    return node.name == null || node.name.isEmpty() ? node.id : node.name;
  }

  /**
   * Applies the importable changes. Text only: a paragraph's runs are replaced with one run that
   * keeps the first run's formatting, because PowerPoint may have merged or split them and
   * guessing which override belonged where would corrupt the wording.
   */
  public int apply(File file, Document document) throws java.io.IOException {
    Report report = compare(file, document);
    int applied = 0;
    ZipFile zip = new ZipFile(file);
    try {
      for (Change change : report.importable()) {
        Node node = find(document, change.nodeId);
        if (node == null || node.content.kind != Content.Kind.TEXT) continue;
        String text = textOf(zip, change.nodeId);
        if (text == null) continue;
        replaceText(node.content.text, text);
        applied++;
      }
    } finally {
      zip.close();
    }
    return applied;
  }

  private String textOf(ZipFile zip, String nodeId) throws java.io.IOException {
    java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      if (!entry.getName().startsWith("ppt/slides/slide")
          || !entry.getName().endsWith(".xml")) continue;
      InputStream in = zip.getInputStream(entry);
      try {
        org.w3c.dom.Document slide = parse(in);
        NodeList properties = slide.getElementsByTagNameNS(Ooxml.PML, "cNvPr");
        for (int i = 0; i < properties.getLength(); i++) {
          Element cNvPr = (Element) properties.item(i);
          if (!nodeId.equals(cNvPr.getAttribute("descr"))) continue;
          if (cNvPr.getAttribute("name").contains("#part")) continue;
          Element shape = shapeAbove(cNvPr);
          if (shape == null) continue;
          StringBuilder written = new StringBuilder();
          NodeList runs = shape.getElementsByTagNameNS(Ooxml.DML, "t");
          for (int r = 0; r < runs.getLength(); r++) written.append(runs.item(r).getTextContent());
          return written.toString();
        }
      } catch (Exception ex) {
        throw new java.io.IOException("Cannot read " + entry.getName(), ex);
      } finally {
        in.close();
      }
    }
    return null;
  }

  private static void replaceText(TextContent content, String text) {
    Run template = null;
    for (Paragraph paragraph : content.paragraphs)
      if (!paragraph.runs.isEmpty()) {
        template = paragraph.runs.get(0);
        break;
      }
    Paragraph first = content.paragraphs.isEmpty() ? new Paragraph() : content.paragraphs.get(0);
    Align align = first.align;
    content.paragraphs.clear();
    Paragraph replacement = new Paragraph();
    replacement.align = align;
    Run run = new Run(text);
    if (template != null) {
      run.bold = template.bold;
      run.italic = template.italic;
      run.underline = template.underline;
      run.fontSizePt = template.fontSizePt;
      run.latinFamily = template.latinFamily;
      run.eaFamily = template.eaFamily;
      run.colorHex = template.colorHex;
    }
    replacement.runs.add(run);
    content.paragraphs.add(replacement);
  }
}

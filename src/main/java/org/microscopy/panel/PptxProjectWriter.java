package org.microscopy.panel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Writes the project as a PPTX. Three layers with different jobs: the project part holds the
 * document losslessly and is the only thing read back; the shape tree is a full-quality vector
 * rendering that PowerPoint can edit; the media are preview-resolution thumbnails, and are
 * derived data that a later save simply regenerates.
 */
public final class PptxProjectWriter {
  private final LinkedProjects attachments;

  public PptxProjectWriter() { this(new LinkedProjects()); }

  public PptxProjectWriter(LinkedProjects attachments) { this.attachments = attachments; }

  public static final class Options {
    /** Preview by default: a save must stay fast enough to press every few seconds. */
    public double thumbnailDpi = RenderTarget.PREVIEW_DPI;
    public RenderTarget.Background background = RenderTarget.Background.WHITE;
    public boolean writeNote = true;

    public String qualityLabel() {
      return thumbnailDpi >= RenderTarget.PRINT_DPI
          ? "export-grade " + (int) thumbnailDpi + " dpi"
          : "preview " + (int) thumbnailDpi + " dpi";
    }
  }

  public static final class Saved {
    public final int slides, mediaParts, renderedMedia, reusedMedia;
    public final long bytes;
    public final List<String> warnings;

    Saved(int slides, int mediaParts, int rendered, int reused, long bytes, List<String> warnings) {
      this.slides = slides; this.mediaParts = mediaParts; this.renderedMedia = rendered;
      this.reusedMedia = reused; this.bytes = bytes; this.warnings = warnings;
    }
  }

  public Saved save(File destination, Document document, SourceProvider sources, Options options)
      throws IOException {
    document.validate();
    ProjectSafety.checkDestination(destination, document);
    if (options == null) options = new Options();
    Units.checkDpi(options.thumbnailDpi);

    Map<String, byte[]> parts = new LinkedHashMap<String, byte[]>();
    List<String> warnings = new ArrayList<String>();
    PptxShapeBuilder.MediaCache cache = previousMedia(destination);
    PptxShapeBuilder builder = new PptxShapeBuilder(sources, attachments);
    StringBuilder slideRels = new StringBuilder(), slideIds = new StringBuilder();
    StringBuilder overrides = new StringBuilder();
    int rendered = 0, reused = 0, mediaParts = 0;
    RectMm firstPage = null;

    for (int i = 0; i < document.pages.size(); i++) {
      Page page = document.pages.get(i);
      LayoutResult layout = new LayoutEngine(
          new DefaultContentMeasurer(ContentMeasurer.NOMINAL_DPI, attachments)).layout(document, page);
      warnings.addAll(layout.warnings());
      if (i == 0) firstPage = layout.pageBox();
      PptxShapeBuilder.Slide slide = builder.build(document, page, layout, options.thumbnailDpi,
          options.qualityLabel(), cache);
      rendered += slide.renderedMedia;
      reused += slide.reusedMedia;
      int number = i + 1;
      parts.put("ppt/slides/slide" + number + ".xml", Ooxml.utf8(slideXml(slide, options)));
      parts.put("ppt/slides/_rels/slide" + number + ".xml.rels", Ooxml.utf8(
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\""
              + "http://schemas.openxmlformats.org/package/2006/relationships\">"
              + Ooxml.relationship("rIdLayout", "slideLayout", "../slideLayouts/slideLayout1.xml")
              + slide.relationships + "</Relationships>"));
      for (Map.Entry<String, byte[]> media : slide.media.entrySet()) {
        if (parts.put("ppt/media/" + media.getKey(), media.getValue()) == null) mediaParts++;
      }
      if (number > 1) {
        slideRels.append(Ooxml.relationship("rIdSlide" + number, "slide",
            "/ppt/slides/slide" + number + ".xml"));
        slideIds.append("<p:sldId id=\"").append(255 + number)
            .append("\" r:id=\"rIdSlide").append(number).append("\"/>");
        overrides.append(Ooxml.override("/ppt/slides/slide" + number + ".xml",
            "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"));
      }
    }

    String json = new DocumentSerializer().toJson(document);
    parts.put(ProjectPart.ITEM_PATH, Ooxml.utf8(ProjectPart.wrap(json, options.qualityLabel())));
    parts.put(ProjectPart.ITEM_PROPS_PATH, Ooxml.utf8(ProjectPart.itemProps()));
    parts.put("customXml/_rels/item1.xml.rels", Ooxml.utf8(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\""
            + "http://schemas.openxmlformats.org/package/2006/relationships\">"
            + Ooxml.relationship("rIdProps", "customXmlProps", "itemProps1.xml")
            + "</Relationships>"));
    parts.put("docProps/custom.xml", Ooxml.utf8(customProperties(options)));

    long bytes = write(destination, document, parts, firstPage, slideRels.toString(),
        slideIds.toString(), overrides.toString());
    return new Saved(document.pages.size(), mediaParts, rendered, reused, bytes, warnings);
  }

  private static String slideXml(PptxShapeBuilder.Slide slide, Options options) {
    String background = options.background == RenderTarget.Background.TRANSPARENT ? ""
        : "<p:bg><p:bgPr><a:solidFill><a:srgbClr val=\""
            + (options.background == RenderTarget.Background.BLACK ? "000000" : "FFFFFF")
            + "\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>";
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><p:sld xmlns:p=\"" + Ooxml.PML
        + "\" xmlns:a=\"" + Ooxml.DML + "\" xmlns:r=\"" + Ooxml.REL + "\"><p:cSld>" + background
        + "<p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/>"
        + "</p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/>"
        + "<a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"
        + slide.shapes + "</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>"
        + "</p:sld>";
  }

  /** Records the quality in the file itself, so a reopened project can say what it holds. */
  private static String customProperties(Options options) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Properties xmlns=\"http://schemas."
        + "openxmlformats.org/officeDocument/2006/custom-properties\" xmlns:vt=\"http://schemas."
        + "openxmlformats.org/officeDocument/2006/docPropsVTypes\">"
        + "<property fmtid=\"{D5CDD505-2E9C-101B-9397-08002B2CF9AE}\" pid=\"2\""
        + " name=\"FijiPanelBuild.SaveQuality\"><vt:lpwstr>" + Ooxml.xml(options.qualityLabel())
        + "</vt:lpwstr></property>"
        + "<property fmtid=\"{D5CDD505-2E9C-101B-9397-08002B2CF9AE}\" pid=\"3\""
        + " name=\"FijiPanelBuild.SchemaVersion\"><vt:lpwstr>" + Document.SCHEMA_VERSION
        + "</vt:lpwstr></property></Properties>";
  }

  static String noteText(Options options) {
    return options.thumbnailDpi >= RenderTarget.PRINT_DPI
        ? "This PPTX is the project file, saved at export-grade resolution."
        : "This PPTX is the project file. Its pictures are preview quality ("
            + (int) options.thumbnailDpi + " dpi); use Export in Fiji for printing or submission.";
  }

  /** Reads the media parts of the file being replaced so unchanged panels are not re-rendered. */
  private static PptxShapeBuilder.MediaCache previousMedia(File destination) {
    if (!destination.isFile()) return null;
    final Map<String, byte[]> existing = new LinkedHashMap<String, byte[]>();
    try {
      ZipFile zip = new ZipFile(destination);
      try {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          if (!entry.getName().startsWith("ppt/media/")) continue;
          existing.put(entry.getName(), readAll(zip.getInputStream(entry)));
        }
      } finally {
        zip.close();
      }
    } catch (IOException ex) {
      return null;
    }
    return new PptxShapeBuilder.MediaCache() {
      public byte[] existing(String name) { return existing.get(name); }
    };
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
    in.close();
    return out.toByteArray();
  }

  /** Streams the template through, replacing the parts we own and adding the ones we introduce. */
  private long write(File destination, Document document, Map<String, byte[]> parts,
      RectMm firstPage, String slideRels, String slideIds, String overrides) throws IOException {
    Path path = destination.toPath().toAbsolutePath();
    Path temp = Files.createTempFile(path.getParent(), "panel-project-", ".pptx.tmp");
    long size;
    try {
      ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp));
      try {
        InputStream resource =
            org.microscopy.figure.PptxExporter.class.getResourceAsStream("blank.pptx");
        if (resource == null) throw new IOException("Missing PPTX template.");
        ZipInputStream template = new ZipInputStream(resource);
        try {
          ZipEntry entry;
          while ((entry = template.getNextEntry()) != null) {
            String name = entry.getName();
            byte[] data = readAll(new NonClosing(template));
            if (parts.containsKey(name)) data = parts.remove(name);
            else if (name.equals("[Content_Types].xml")) data = contentTypes(data, overrides);
            else if (name.equals("ppt/_rels/presentation.xml.rels"))
              data = insert(data, "</Relationships>", slideRels
                  + Ooxml.relationship("rIdProject", "customXml", "../customXml/item1.xml"));
            else if (name.equals("_rels/.rels"))
              data = insert(data, "</Relationships>", Ooxml.relationship("rIdCustomProps",
                  "custom-properties", "docProps/custom.xml"));
            else if (name.equals("ppt/presentation.xml")) data = presentation(data, firstPage, slideIds);
            entry(out, name, data);
          }
        } finally {
          template.close();
        }
        for (Map.Entry<String, byte[]> part : parts.entrySet()) entry(out, part.getKey(), part.getValue());
      } finally {
        out.close();
      }
      size = Files.size(temp);
      Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
    return size;
  }

  private static byte[] contentTypes(byte[] original, String overrides) {
    String text = new String(original, java.nio.charset.StandardCharsets.UTF_8);
    return Ooxml.utf8(text.replace("</Types>",
        "<Default Extension=\"png\" ContentType=\"image/png\"/>"
            + Ooxml.override("/" + ProjectPart.ITEM_PATH, "application/xml")
            + Ooxml.override("/" + ProjectPart.ITEM_PROPS_PATH,
                "application/vnd.openxmlformats-officedocument.customXmlProperties+xml")
            + Ooxml.override("/docProps/custom.xml",
                "application/vnd.openxmlformats-officedocument.custom-properties+xml")
            + overrides + "</Types>"));
  }

  private static byte[] insert(byte[] original, String before, String addition) {
    String text = new String(original, java.nio.charset.StandardCharsets.UTF_8);
    return Ooxml.utf8(text.replace(before, addition + before));
  }

  private static byte[] presentation(byte[] original, RectMm firstPage, String slideIds) {
    String text = new String(original, java.nio.charset.StandardCharsets.UTF_8);
    // Clamped rather than rejected: PowerPoint cannot hold a slide over 56 inches.
    double width = Math.min(firstPage.width, Units.MAX_SLIDE_MM);
    double height = Math.min(firstPage.height, Units.MAX_SLIDE_MM);
    text = text.replaceAll("<p:sldSz[^>]*/>",
        "<p:sldSz cx=\"" + Units.mmToEmu(width) + "\" cy=\"" + Units.mmToEmu(height) + "\"/>");
    if (!slideIds.isEmpty()) text = text.replace("</p:sldIdLst>", slideIds + "</p:sldIdLst>");
    return Ooxml.utf8(text);
  }

  private static void entry(ZipOutputStream out, String name, byte[] data) throws IOException {
    out.putNextEntry(new ZipEntry(name));
    out.write(data);
    out.closeEntry();
  }

  /** Keeps readAll from closing the shared template stream between entries. */
  private static final class NonClosing extends java.io.FilterInputStream {
    NonClosing(InputStream in) { super(in); }

    @Override
    public void close() {}
  }
}

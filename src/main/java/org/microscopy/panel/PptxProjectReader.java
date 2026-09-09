package org.microscopy.panel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reopens a project PPTX. The project part is authoritative; the shape tree is only ever used to
 * report what changed in PowerPoint, never to reconstruct the document.
 */
public final class PptxProjectReader {
  public static final class Opened {
    /** Null when the file carries no project part. */
    public final Document document;
    public final String saveQuality;
    public final int slides;
    public final String message;

    Opened(Document document, String saveQuality, int slides, String message) {
      this.document = document; this.saveQuality = saveQuality; this.slides = slides;
      this.message = message;
    }

    public boolean editable() { return document != null; }
  }

  public Opened open(File file) throws IOException {
    ZipFile zip = new ZipFile(file);
    try {
      int slides = 0;
      java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        String name = entries.nextElement().getName();
        if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) slides++;
      }
      ZipEntry item = zip.getEntry(ProjectPart.ITEM_PATH);
      ZipEntry props = zip.getEntry(ProjectPart.ITEM_PROPS_PATH);
      if (item == null || props == null || !text(zip, props).contains(ProjectPart.ITEM_ID))
        return new Opened(null, "", slides,
            "This PowerPoint file has no project information, so it cannot be edited as a project."
                + " Its shapes can be imported read-only, and saving it again from Fiji makes it"
                + " editable.");
      String part = text(zip, item);
      Document document = new DocumentSerializer().fromJson(ProjectPart.unwrap(part));
      String quality = ProjectPart.saveQuality(part);
      return new Opened(document, quality, slides,
          quality.startsWith("preview")
              ? "Opened the project. Its embedded pictures are " + quality
                  + "; Export re-renders them at full resolution."
              : "Opened the project (" + quality + ").");
    } finally {
      zip.close();
    }
  }

  /** True when this file was written by this tool and still carries its project part. */
  public boolean hasProject(File file) throws IOException {
    return open(file).editable();
  }

  private static String text(ZipFile zip, ZipEntry entry) throws IOException {
    InputStream in = zip.getInputStream(entry);
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    } finally {
      in.close();
    }
  }
}

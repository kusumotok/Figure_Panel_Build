package org.microscopy.panel;

import java.util.ArrayList;
import java.util.List;

/**
 * A source dataset. Nodes never hold a file path; they hold an asset id, so relinking and
 * portable materialisation happen in one place.
 *
 * <p>A composite asset binds one channel from each of several sources into a single multi-channel
 * image. Acquisitions that put each fluorescence channel in its own series need that to be
 * merged at all, and doing it here means the figure renderer keeps seeing one ordinary source.
 */
public final class Asset {
  public enum Kind { SOURCE, COMPOSITE }

  public enum StorageMode { LINKED, PORTABLE }

  /** One channel of one source, contributing a single channel to a composite. */
  public static final class Part {
    public String assetId;
    public int channel = 1;
    public String label = "";

    public Part() {}

    public Part(String assetId, int channel, String label) {
      this.assetId = assetId; this.channel = channel; this.label = label;
    }
  }

  public String id;
  public Kind kind = Kind.SOURCE;
  public String originalUri;
  /** Set once the source has been materialised inside the project container. */
  public String embeddedUri;
  public String originalFormat = "";
  public int originalSeriesIndex;
  /** Series name as the acquisition software recorded it, kept as provenance. */
  public String seriesName = "";
  public int sizeX, sizeY;
  public int sizeC = 1, sizeZ = 1, sizeT = 1;
  public double pixelWidth = 1, pixelHeight = 1;
  public String unit = "";
  public int bitDepth = 16;
  /** Size, modified time and a leading-bytes hash; used to spot a moved or changed source. */
  public String fingerprint = "";
  public StorageMode storageMode = StorageMode.LINKED;
  /** COMPOSITE only, one entry per channel in channel order. */
  public List<Part> parts = new ArrayList<Part>();

  public boolean composite() { return kind == Kind.COMPOSITE; }

  public void validate() {
    if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Asset without an id.");
    if (kind == null) throw new IllegalArgumentException("Asset " + id + ": missing kind.");
    if (storageMode == null) throw new IllegalArgumentException("Asset " + id + ": missing storage mode.");
    if (parts == null) throw new IllegalArgumentException("Asset " + id + ": missing parts list.");
    if (sizeX <= 0 || sizeY <= 0) throw new IllegalArgumentException("Asset " + id + ": invalid pixel size.");
    if (sizeC < 1 || sizeZ < 1 || sizeT < 1)
      throw new IllegalArgumentException("Asset " + id + ": dimensions must be at least 1.");
    if (originalSeriesIndex < 0) throw new IllegalArgumentException("Asset " + id + ": negative series index.");
    if (!(pixelWidth > 0) || !(pixelHeight > 0))
      throw new IllegalArgumentException("Asset " + id + ": invalid calibration.");
    if (kind == Kind.COMPOSITE) {
      if (parts.isEmpty())
        throw new IllegalArgumentException("Asset " + id + ": a composite needs at least one part.");
      if (parts.size() != sizeC)
        throw new IllegalArgumentException("Asset " + id + ": a composite has one channel per part.");
      for (Part part : parts) {
        if (part == null || part.assetId == null || part.assetId.trim().isEmpty())
          throw new IllegalArgumentException("Asset " + id + ": a composite part needs a source.");
        if (part.assetId.equals(id))
          throw new IllegalArgumentException("Asset " + id + ": a composite cannot include itself.");
        if (part.channel < 1)
          throw new IllegalArgumentException("Asset " + id + ": composite channels start at 1.");
      }
      return;
    }
    if (!parts.isEmpty())
      throw new IllegalArgumentException("Asset " + id + ": only a composite may have parts.");
    if (storageMode == StorageMode.LINKED && (originalUri == null || originalUri.trim().isEmpty()))
      throw new IllegalArgumentException("Asset " + id + ": a linked asset needs a source URI.");
    if (storageMode == StorageMode.PORTABLE && (embeddedUri == null || embeddedUri.trim().isEmpty()))
      throw new IllegalArgumentException("Asset " + id + ": a portable asset needs an embedded URI.");
  }
}

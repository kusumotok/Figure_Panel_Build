package org.microscopy.panel;

/**
 * A source dataset. Nodes never hold a file path; they hold an asset id, so relinking and
 * portable materialisation happen in one place.
 */
public final class Asset {
  public enum StorageMode { LINKED, PORTABLE }

  public String id;
  public String originalUri;
  /** Set once the source has been materialised inside the project container. */
  public String embeddedUri;
  public String originalFormat = "";
  public int originalSeriesIndex;
  public int sizeX, sizeY;
  public int sizeC = 1, sizeZ = 1, sizeT = 1;
  public double pixelWidth = 1, pixelHeight = 1;
  public String unit = "";
  public int bitDepth = 16;
  /** Size, modified time and a leading-bytes hash; used to spot a moved or changed source. */
  public String fingerprint = "";
  public StorageMode storageMode = StorageMode.LINKED;

  public void validate() {
    if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Asset without an id.");
    if (storageMode == null) throw new IllegalArgumentException("Asset " + id + ": missing storage mode.");
    if (sizeX <= 0 || sizeY <= 0) throw new IllegalArgumentException("Asset " + id + ": invalid pixel size.");
    if (sizeC < 1 || sizeZ < 1 || sizeT < 1)
      throw new IllegalArgumentException("Asset " + id + ": dimensions must be at least 1.");
    if (originalSeriesIndex < 0) throw new IllegalArgumentException("Asset " + id + ": negative series index.");
    if (!(pixelWidth > 0) || !(pixelHeight > 0))
      throw new IllegalArgumentException("Asset " + id + ": invalid calibration.");
    if (storageMode == StorageMode.LINKED && (originalUri == null || originalUri.trim().isEmpty()))
      throw new IllegalArgumentException("Asset " + id + ": a linked asset needs a source URI.");
    if (storageMode == StorageMode.PORTABLE && (embeddedUri == null || embeddedUri.trim().isEmpty()))
      throw new IllegalArgumentException("Asset " + id + ": a portable asset needs an embedded URI.");
  }
}

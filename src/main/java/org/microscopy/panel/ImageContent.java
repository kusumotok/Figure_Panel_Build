package org.microscopy.panel;

/** A plain picture such as a logo or a diagram; no display transform is applied. */
public final class ImageContent {
  public String assetId;

  public ImageContent() {}

  public ImageContent(String assetId) { this.assetId = assetId; }

  public void validate(String where) {
    if (assetId == null || assetId.trim().isEmpty())
      throw new IllegalArgumentException(where + ": image without an asset.");
  }
}

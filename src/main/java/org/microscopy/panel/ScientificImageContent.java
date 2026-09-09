package org.microscopy.panel;

import org.microscopy.figure.FigureConfiguration;

/**
 * A microscopy image cell. The existing {@link FigureConfiguration} is stored verbatim so that
 * B and C, LUTs, merges, scale bars and insets keep working through the code that already
 * renders them; this class only adds the asset reference and the Z/T selection around it.
 */
public final class ScientificImageContent {
  /** How the source pixels are mapped onto the physical rectangle. */
  public enum Resample { NEAREST, AREA_AVERAGE }

  public String assetId;
  public int z = 1, t = 1;
  /** Exactly one condition and one display channel; the grid lives in the node tree instead. */
  public FigureConfiguration figureConfig = new FigureConfiguration();
  /** Nearest by default: quantitative figures should not be silently interpolated. */
  public Resample resample = Resample.NEAREST;

  public void validate(String where) {
    if (assetId == null || assetId.trim().isEmpty())
      throw new IllegalArgumentException(where + ": scientific image without an asset.");
    if (z < 1 || t < 1) throw new IllegalArgumentException(where + ": Z and T selections start at 1.");
    if (figureConfig == null) throw new IllegalArgumentException(where + ": missing figure configuration.");
    if (resample == null) throw new IllegalArgumentException(where + ": missing resample mode.");
    if (figureConfig.conditions.size() != 1 || figureConfig.displayChannels.size() != 1)
      throw new IllegalArgumentException(
          where + ": a scientific image node holds exactly one image display.");
  }
}

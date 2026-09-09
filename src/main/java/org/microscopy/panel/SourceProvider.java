package org.microscopy.panel;

import org.microscopy.figure.InputImageManager;

/**
 * Supplies pixels for an asset. The renderer only ever asks for the plane set it is about to
 * draw, so a lazy, cached implementation can serve Z and T stacks without holding whole files
 * in memory.
 */
public interface SourceProvider {
  /**
   * @param assetId asset to read
   * @param z one-based Z slice
   * @param t one-based time point
   */
  InputImageManager.Source source(String assetId, int z, int t);
}

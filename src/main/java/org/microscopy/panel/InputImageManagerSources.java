package org.microscopy.panel;

import org.microscopy.figure.InputImageManager;

/**
 * Adapter over the existing snapshot store, where asset ids and source ids are the same. Used by
 * imported figures and by tests; file-backed and Bio-Formats providers implement the same seam.
 */
public final class InputImageManagerSources implements SourceProvider {
  private final InputImageManager inputs;

  public InputImageManagerSources(InputImageManager inputs) { this.inputs = inputs; }

  @Override
  public InputImageManager.Source source(String assetId, int z, int t) {
    if (z != 1 || t != 1)
      throw new IllegalArgumentException(
          "This provider holds a single plane set; Z " + z + " / T " + t + " needs the asset library.");
    return inputs.get(assetId);
  }
}

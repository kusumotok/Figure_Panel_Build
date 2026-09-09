package org.microscopy.panel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** The document-level counterpart of the figure exporter's source-overwrite guard. */
public final class ProjectSafety {
  private ProjectSafety() {}

  public static void checkDestination(File destination, Document document) throws IOException {
    File canonical = destination.getCanonicalFile();
    for (Asset asset : document.assets.values()) {
      if (asset.originalUri == null || asset.originalUri.trim().isEmpty()) continue;
      File original = new File(asset.originalUri).getCanonicalFile();
      if (original.equals(canonical)
          || (original.exists() && canonical.exists()
              && Files.isSameFile(original.toPath(), canonical.toPath())))
        throw new IllegalArgumentException("Cannot overwrite a source image: " + asset.originalUri);
    }
  }
}

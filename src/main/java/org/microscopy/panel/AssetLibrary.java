package org.microscopy.panel;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.microscopy.figure.InputImageManager;

/**
 * Resolves asset ids to pixels. Files are read on first use and then kept, so a linked project
 * opens without touching anything it does not draw. Images that came from an open Fiji window are
 * registered up front instead, because the user may still be editing those.
 */
public final class AssetLibrary implements SourceProvider {
  private final InputImageManager inputs = new InputImageManager();
  private final Map<String, InputImageManager.Source> byAsset =
      new HashMap<String, InputImageManager.Source>();
  private Document document;

  public void use(Document document) { this.document = document; }

  /** Takes over snapshots that already exist, keyed by their own id. */
  public void adopt(InputImageManager other) {
    inputs.include(other);
    for (InputImageManager.Source source : other.all()) byAsset.put(source.id, source);
  }

  public void register(String assetId, InputImageManager.Source source) {
    byAsset.put(assetId, source);
  }

  public boolean has(String assetId) { return byAsset.containsKey(assetId); }

  @Override
  public InputImageManager.Source source(String assetId, int z, int t) {
    InputImageManager.Source cached = byAsset.get(assetId);
    if (cached != null) {
      if (z != 1 || t != 1)
        throw new IllegalArgumentException("Z " + z + " / T " + t
            + " needs a Bio-Formats source; this asset holds a single plane set.");
      return cached;
    }
    File file = fileFor(assetId);
    if (file == null)
      throw new IllegalArgumentException("The source for this picture is missing: "
          + describe(assetId) + ". Relink it before drawing.");
    InputImageManager.Source loaded = inputs.load(file);
    byAsset.put(assetId, loaded);
    return loaded;
  }

  /** Assets whose file cannot be found; the caller offers relinking rather than failing later. */
  public List<Asset> missing() {
    List<Asset> missing = new ArrayList<Asset>();
    if (document == null) return missing;
    for (Asset asset : document.assets.values()) {
      if (byAsset.containsKey(asset.id)) continue;
      if (fileFor(asset.id) == null) missing.add(asset);
    }
    return missing;
  }

  /** Points an asset at a different file after the user located it. */
  public void relink(String assetId, File file) {
    if (document == null) throw new IllegalStateException("No document to relink against.");
    Asset asset = document.asset(assetId);
    InputImageManager.Source loaded = inputs.load(file);
    asset.originalUri = file.getAbsolutePath();
    asset.fingerprint = AssetFingerprint.of(asset.originalUri);
    byAsset.put(assetId, loaded);
  }

  private File fileFor(String assetId) {
    if (document == null || !document.assets.containsKey(assetId)) return null;
    Asset asset = document.assets.get(assetId);
    String uri = asset.embeddedUri != null && !asset.embeddedUri.isEmpty()
        ? asset.embeddedUri : asset.originalUri;
    if (uri == null || uri.trim().isEmpty()) return null;
    File file = new File(uri);
    return file.isFile() ? file : null;
  }

  private String describe(String assetId) {
    if (document == null || !document.assets.containsKey(assetId)) return assetId;
    String uri = document.assets.get(assetId).originalUri;
    if (uri == null) return assetId;
    return new File(uri).getName();
  }
}

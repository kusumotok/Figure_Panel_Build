package org.microscopy.panel;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Cross-table checks: ids stay unique document-wide and every reference resolves. */
final class DocumentReferences {
  private final Document document;
  private final Set<String> nodeIds = new HashSet<String>();
  private final Set<String> pageIds = new HashSet<String>();

  DocumentReferences(Document document) { this.document = document; }

  void validate() {
    composites();
    for (Page page : document.pages) {
      if (!pageIds.add(page.id)) throw new IllegalArgumentException("Duplicate page id: " + page.id);
      walk(page.rootNode, "Page '" + page.name + "'");
    }
  }

  /**
   * A composite may only bind plain sources, one level deep, and every part has to agree on the
   * pixel grid; otherwise the channels could not be drawn over each other.
   */
  private void composites() {
    for (Asset asset : document.assets.values()) {
      if (!asset.composite()) continue;
      for (Asset.Part part : asset.parts) {
        Asset source = document.assets.get(part.assetId);
        if (source == null)
          throw new IllegalArgumentException(
              "Composite " + asset.id + " references a missing source: " + part.assetId);
        if (source.composite())
          throw new IllegalArgumentException("Composite " + asset.id + " references another"
              + " composite; composites stay one level deep so the channel order is unambiguous.");
        if (part.channel > source.sizeC)
          throw new IllegalArgumentException("Composite " + asset.id + " asks for channel "
              + part.channel + " of a source that has " + source.sizeC + ".");
        if (source.sizeX != asset.sizeX || source.sizeY != asset.sizeY)
          throw new IllegalArgumentException("Composite " + asset.id + " combines sources of"
              + " different sizes (" + asset.sizeX + "x" + asset.sizeY + " and "
              + source.sizeX + "x" + source.sizeY + "); they cannot be overlaid.");
      }
    }
  }

  private void walk(Node node, String where) {
    if (!nodeIds.add(node.id))
      throw new IllegalArgumentException("Node id " + node.id + " is used on more than one page.");
    String label = where + " / " + (node.name.isEmpty() ? node.id : node.name);
    if (node.appearance.styleId != null && !document.styles.containsKey(node.appearance.styleId))
      throw new IllegalArgumentException(label + ": references a missing style: " + node.appearance.styleId);
    for (Map.Entry<String, PropertyValue> entry : node.appearance.overrides.entrySet()) {
      PropertyValue value = entry.getValue();
      if (value.kind == PropertyValue.Kind.TOKEN_REF && !document.tokens.containsKey(value.tokenId))
        throw new IllegalArgumentException(
            label + "." + entry.getKey() + ": references a missing token: " + value.tokenId);
    }
    if (node.content.kind == Content.Kind.SCIENTIFIC_IMAGE)
      requireAsset(label, node.content.scientificImage.assetId);
    if (node.content.kind == Content.Kind.IMAGE) requireAsset(label, node.content.image.assetId);
    for (Node child : node.children) walk(child, label);
  }

  private void requireAsset(String label, String assetId) {
    if (!document.assets.containsKey(assetId))
      throw new IllegalArgumentException(label + ": references a missing asset: " + assetId);
  }
}

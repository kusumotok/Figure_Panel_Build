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
    for (Page page : document.pages) {
      if (!pageIds.add(page.id)) throw new IllegalArgumentException("Duplicate page id: " + page.id);
      walk(page.rootNode, "Page '" + page.name + "'");
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

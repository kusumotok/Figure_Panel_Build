package org.microscopy.panel;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out what a property is actually worth for a node.
 *
 * <p>The order is override, then the node's style, then inheritance from ancestors, then the
 * built-in default. Tokens sit between styles and values: a style names a token and the token
 * holds the number or colour. Because a style never names another style, and a token may alias
 * another only one level deep, the graph cannot cycle and no detection is needed.
 */
public final class StyleResolver {
  /** Typography descends the tree; a fill or a border belongs to the node that set it. */
  private static final Set<String> INHERITED = new HashSet<String>(Arrays.asList(
      Prop.FONT_SIZE_PT, Prop.LATIN_FAMILY, Prop.EA_FAMILY, Prop.TEXT_COLOR, Prop.BOLD,
      Prop.ITALIC, Prop.UNDERLINE, Prop.LINE_SPACING, Prop.TEXT_ALIGN));

  private final Document document;

  public StyleResolver(Document document) { this.document = document; }

  public static boolean inherited(String key) { return INHERITED.contains(key); }

  /** The literal a node ends up with, or null when nothing in the chain supplies one. */
  public PropertyValue resolve(Page page, String nodeId, String key) {
    List<Node> path = page.rootNode.pathTo(nodeId);
    if (path.isEmpty()) return null;
    for (int i = path.size() - 1; i >= 0; i--) {
      PropertyValue value = onNode(path.get(i), key);
      // Nothing here; keep walking up only for properties that are meant to descend.
      if (value == null) {
        if (!inherited(key)) return null;
        continue;
      }
      if (value.kind == PropertyValue.Kind.INHERIT) continue;
      return literal(value);
    }
    return null;
  }

  /** What this node itself says, override first and then its style. */
  private PropertyValue onNode(Node node, String key) {
    PropertyValue override = node.appearance.overrides.get(key);
    if (override != null) return override;
    if (node.appearance.styleId == null) return null;
    Style style = document.styles.get(node.appearance.styleId);
    return style == null ? null : style.properties.get(key);
  }

  /** Follows a token reference, and the single alias level a token may have. */
  private PropertyValue literal(PropertyValue value) {
    if (value.kind != PropertyValue.Kind.TOKEN_REF) return value;
    Token token = document.tokens.get(value.tokenId);
    if (token == null) return null;
    if (token.aliasOf != null && !token.aliasOf.trim().isEmpty()) {
      Token target = document.tokens.get(token.aliasOf);
      if (target == null) return null;
      token = target;
    }
    return token.value == null ? null : PropertyValue.of(token.value);
  }

  public Double number(Page page, String nodeId, String key) {
    PropertyValue value = resolve(page, nodeId, key);
    return value == null ? null : Double.valueOf(value.asDouble());
  }

  public Color color(Page page, String nodeId, String key) {
    PropertyValue value = resolve(page, nodeId, key);
    return value == null ? null : value.asColor();
  }

  public String text(Page page, String nodeId, String key) {
    PropertyValue value = resolve(page, nodeId, key);
    return value == null ? null : value.text();
  }

  public Boolean flag(Page page, String nodeId, String key) {
    PropertyValue value = resolve(page, nodeId, key);
    return value == null ? null : Boolean.valueOf(value.asBoolean());
  }
}

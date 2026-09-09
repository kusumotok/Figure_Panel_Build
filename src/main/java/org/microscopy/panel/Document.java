package org.microscopy.panel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole project: style tables, asset table and one or more pages. Versioned from the start
 * so that migrations have somewhere to hang.
 */
public final class Document {
  public static final int SCHEMA_VERSION = 1;

  public int schemaVersion = SCHEMA_VERSION;
  public String title = "";
  public Map<String, Token> tokens = new LinkedHashMap<String, Token>();
  public Map<String, Style> styles = new LinkedHashMap<String, Style>();
  public Map<String, Asset> assets = new LinkedHashMap<String, Asset>();
  public List<Page> pages = new ArrayList<Page>();

  public static Document empty() {
    Document document = new Document();
    document.pages.add(new Page("Page 1"));
    return document;
  }

  public Page page(int index) { return pages.get(index); }

  public Asset asset(String assetId) {
    Asset asset = assets.get(assetId);
    if (asset == null) throw new IllegalArgumentException("Missing asset: " + assetId);
    return asset;
  }

  public void validate() {
    if (schemaVersion != SCHEMA_VERSION)
      throw new IllegalArgumentException(
          "Unsupported project schema version " + schemaVersion + "; this build reads version "
              + SCHEMA_VERSION + ".");
    if (tokens == null || styles == null || assets == null || pages == null)
      throw new IllegalArgumentException("Incomplete document.");
    if (pages.isEmpty()) throw new IllegalArgumentException("A document needs at least one page.");
    for (Map.Entry<String, Token> entry : tokens.entrySet()) {
      Token token = entry.getValue();
      if (token == null) throw new IllegalArgumentException("Null token: " + entry.getKey());
      token.validate();
      if (!entry.getKey().equals(token.id))
        throw new IllegalArgumentException("Token key does not match its id: " + entry.getKey());
      if (token.aliasOf != null && !token.aliasOf.trim().isEmpty()) {
        Token target = tokens.get(token.aliasOf);
        if (target == null)
          throw new IllegalArgumentException("Token " + token.id + " aliases a missing token.");
        if (target.aliasOf != null && !target.aliasOf.trim().isEmpty())
          throw new IllegalArgumentException(
              "Token " + token.id + " aliases " + target.id + ", which is itself an alias;"
                  + " aliases are single level so the table stays acyclic.");
      }
    }
    for (Map.Entry<String, Style> entry : styles.entrySet()) {
      Style style = entry.getValue();
      if (style == null) throw new IllegalArgumentException("Null style: " + entry.getKey());
      style.validate();
      if (!entry.getKey().equals(style.id))
        throw new IllegalArgumentException("Style key does not match its id: " + entry.getKey());
      for (Map.Entry<String, PropertyValue> property : style.properties.entrySet()) {
        PropertyValue value = property.getValue();
        if (value.kind == PropertyValue.Kind.TOKEN_REF && !tokens.containsKey(value.tokenId))
          throw new IllegalArgumentException(
              "Style " + style.id + "." + property.getKey() + " references a missing token: "
                  + value.tokenId);
      }
    }
    for (Map.Entry<String, Asset> entry : assets.entrySet()) {
      Asset asset = entry.getValue();
      if (asset == null) throw new IllegalArgumentException("Null asset: " + entry.getKey());
      asset.validate();
      if (!entry.getKey().equals(asset.id))
        throw new IllegalArgumentException("Asset key does not match its id: " + entry.getKey());
    }
    for (int i = 0; i < pages.size(); i++) {
      Page page = pages.get(i);
      if (page == null) throw new IllegalArgumentException("Null page " + (i + 1) + ".");
      page.validate("Page " + (i + 1));
    }
    new DocumentReferences(this).validate();
  }
}

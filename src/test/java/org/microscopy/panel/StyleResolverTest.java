package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class StyleResolverTest {
  private Document document;
  private Node root, section, caption;

  private void tree() {
    document = Document.empty();
    document.tokens.put("body-size",
        new Token("body-size", "Body size", Token.Kind.FONT_SIZE_PT, "28"));
    document.tokens.put("cjk", new Token("cjk", "CJK", Token.Kind.FONT_FAMILY, "Yu Gothic"));

    Style body = new Style("body", "Body");
    body.set(Prop.FONT_SIZE_PT, PropertyValue.token("body-size"));
    body.set(Prop.LATIN_FAMILY, PropertyValue.of("Times New Roman"));
    body.set(Prop.EA_FAMILY, PropertyValue.token("cjk"));
    document.styles.put(body.id, body);

    Style label = new Style("figure-label", "Figure label");
    // The point of tokens: the size is shared with Body while the Latin font is not.
    label.set(Prop.FONT_SIZE_PT, PropertyValue.token("body-size"));
    label.set(Prop.LATIN_FAMILY, PropertyValue.of("Arial"));
    label.set(Prop.EA_FAMILY, PropertyValue.token("cjk"));
    document.styles.put(label.id, label);

    root = Node.container("Poster", 1, 1);
    section = Node.container("Results", 1, 1);
    caption = Node.leaf("Caption", Content.of(TextContent.of("caption")));
    section.add(caption, 0, 0);
    root.add(section, 0, 0);
    document.page(0).rootNode = root;
    document.validate();
  }

  private Page page() { return document.page(0); }

  @Test
  void aStyleResolvesThroughItsTokens() {
    tree();
    caption.appearance.styleId = "body";
    StyleResolver resolver = new StyleResolver(document);
    assertEquals(28.0, resolver.number(page(), caption.id, Prop.FONT_SIZE_PT), 1e-9);
    assertEquals("Times New Roman", resolver.text(page(), caption.id, Prop.LATIN_FAMILY));
    assertEquals("Yu Gothic", resolver.text(page(), caption.id, Prop.EA_FAMILY));
  }

  @Test
  void twoStylesShareASizeWithoutSharingAFont() {
    tree();
    Node other = Node.leaf("Label", Content.of(TextContent.of("A")));
    section.add(other, 0, 0);
    caption.appearance.styleId = "body";
    other.appearance.styleId = "figure-label";
    document.validate();
    StyleResolver resolver = new StyleResolver(document);
    assertEquals(resolver.number(page(), caption.id, Prop.FONT_SIZE_PT),
        resolver.number(page(), other.id, Prop.FONT_SIZE_PT), 1e-9);
    assertEquals("Arial", resolver.text(page(), other.id, Prop.LATIN_FAMILY));
    // Changing the token moves both, which is the whole reason it exists.
    document.tokens.get("body-size").value = "36";
    assertEquals(36.0, resolver.number(page(), other.id, Prop.FONT_SIZE_PT), 1e-9);
    assertEquals(36.0, resolver.number(page(), caption.id, Prop.FONT_SIZE_PT), 1e-9);
  }

  @Test
  void anOverrideBeatsTheStyle() {
    tree();
    caption.appearance.styleId = "body";
    caption.appearance.set(Prop.FONT_SIZE_PT, PropertyValue.of(11.0));
    StyleResolver resolver = new StyleResolver(document);
    assertEquals(11.0, resolver.number(page(), caption.id, Prop.FONT_SIZE_PT), 1e-9);
  }

  @Test
  void typographyDescendsButAppearanceDoesNot() {
    tree();
    section.appearance.set(Prop.FONT_SIZE_PT, PropertyValue.of(9.0));
    section.appearance.set(Prop.FILL, PropertyValue.of(Color.RED));
    StyleResolver resolver = new StyleResolver(document);
    assertEquals(9.0, resolver.number(page(), caption.id, Prop.FONT_SIZE_PT), 1e-9);
    assertNull(resolver.color(page(), caption.id, Prop.FILL),
        "a section's fill must not paint every descendant");
    assertEquals(Color.RED, resolver.color(page(), section.id, Prop.FILL));
  }

  @Test
  void inheritSkipsPastTheNodeThatAsksForIt() {
    tree();
    root.appearance.set(Prop.TEXT_COLOR, PropertyValue.of(Color.BLUE));
    section.appearance.set(Prop.TEXT_COLOR, PropertyValue.of(Color.RED));
    caption.appearance.set(Prop.TEXT_COLOR, PropertyValue.inherit());
    StyleResolver resolver = new StyleResolver(document);
    assertEquals(Color.RED, resolver.color(page(), caption.id, Prop.TEXT_COLOR));
    section.appearance.set(Prop.TEXT_COLOR, PropertyValue.inherit());
    assertEquals(Color.BLUE, resolver.color(page(), caption.id, Prop.TEXT_COLOR));
  }

  @Test
  void aTokenMayAliasAnotherOneLevelDeep() {
    tree();
    Token alias = new Token("heading-size", "Heading", Token.Kind.FONT_SIZE_PT, null);
    alias.aliasOf = "body-size";
    document.tokens.put(alias.id, alias);
    Style heading = new Style("heading", "Heading");
    heading.set(Prop.FONT_SIZE_PT, PropertyValue.token("heading-size"));
    document.styles.put(heading.id, heading);
    caption.appearance.styleId = "heading";
    document.validate();
    assertEquals(28.0, new StyleResolver(document).number(page(), caption.id, Prop.FONT_SIZE_PT),
        1e-9);
  }

  @Test
  void nothingAnywhereResolvesToNothing() {
    tree();
    assertNull(new StyleResolver(document).number(page(), caption.id, Prop.CORNER_RADIUS_MM));
  }

  @Test
  void aStyleReachesTheSavedPowerPoint(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder)
      throws Exception {
    tree();
    document.page(0).size = PageSize.fixed(120, 80);
    caption.appearance.styleId = "figure-label";
    caption.appearance.set(Prop.TEXT_COLOR, PropertyValue.of(java.awt.Color.RED));
    document.validate();

    java.io.File file = folder.resolve("styled.pptx").toFile();
    new PptxProjectWriter().save(file, document,
        new InputImageManagerSources(new org.microscopy.figure.InputImageManager()),
        new PptxProjectWriter.Options());

    String slide = readSlide(file);
    // 28 pt from the shared token, Arial from the style, red from the node override.
    assertTrue(slide.contains("sz=\"2800\""), slide);
    assertTrue(slide.contains("typeface=\"Arial\""), "the style's Latin font");
    assertTrue(slide.contains("typeface=\"Yu Gothic\""), "the token's CJK font");
    assertTrue(slide.contains("val=\"FF0000\""), "the override's colour");
  }

  private static String readSlide(java.io.File file) throws Exception {
    java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file);
    try {
      java.io.InputStream in = zip.getInputStream(zip.getEntry("ppt/slides/slide1.xml"));
      try {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
      } finally {
        in.close();
      }
    } finally {
      zip.close();
    }
  }
}

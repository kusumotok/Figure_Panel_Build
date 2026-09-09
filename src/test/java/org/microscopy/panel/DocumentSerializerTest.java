package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentSerializerTest {
  private static Document sample() {
    Document document = Document.empty();
    document.title = "Poster";
    document.tokens.put("body-size", new Token("body-size", "Body size", Token.Kind.FONT_SIZE_PT, "28"));
    document.tokens.put("cjk", new Token("cjk", "CJK", Token.Kind.FONT_FAMILY, "Yu Gothic"));
    Style body = new Style("body", "Body");
    body.set(Prop.FONT_SIZE_PT, PropertyValue.token("body-size"));
    body.set(Prop.LATIN_FAMILY, PropertyValue.of("Times New Roman"));
    body.set(Prop.EA_FAMILY, PropertyValue.token("cjk"));
    document.styles.put(body.id, body);
    Style figureLabel = new Style("figure-label", "Figure label");
    // The point of tokens: the size is shared with Body while the Latin font is not.
    figureLabel.set(Prop.FONT_SIZE_PT, PropertyValue.token("body-size"));
    figureLabel.set(Prop.LATIN_FAMILY, PropertyValue.of("Arial"));
    figureLabel.set(Prop.EA_FAMILY, PropertyValue.token("cjk"));
    document.styles.put(figureLabel.id, figureLabel);

    Node root = Node.container("Poster", 2, 2);
    root.layout.rowGapMm = 6;
    root.layout.columnGapMm = 6;
    Node caption = Node.leaf("Caption", Content.of(TextContent.of("Scale bar 10 um")));
    caption.appearance.styleId = "body";
    caption.size.width = SizeExpr.fill();
    caption.size.height = SizeExpr.auto();
    Node heading = Node.leaf("Heading", Content.of(TextContent.of("Results")));
    heading.appearance.styleId = "figure-label";
    heading.size.height = SizeExpr.fixed(12).clamped(8.0, 20.0);
    root.add(heading, 0, 0);
    root.add(caption, 1, 0);
    document.page(0).size = PagePresets.byName(PagePresets.JOURNAL_DOUBLE_COLUMN).toPageSize();
    document.page(0).rootNode = root;
    return document;
  }

  @Test
  void jsonRoundTripKeepsTheDocumentIdentical() {
    DocumentSerializer serializer = new DocumentSerializer();
    Document original = sample();
    String json = serializer.toJson(original);
    Document reloaded = serializer.fromJson(json);
    assertEquals(json, serializer.toJson(reloaded), "Round trip must be an exact fixed point");
    assertEquals(180, reloaded.page(0).size.widthMm, 1e-9);
    assertEquals(PageSize.Mode.FIXED_WIDTH, reloaded.page(0).size.mode);
    assertEquals("Results", reloaded.page(0).rootNode.children.get(0).content.text.plainText());
    assertEquals(8.0, reloaded.page(0).rootNode.children.get(0).size.height.minMm, 1e-9);
  }

  @Test
  void copyIsIndependent() {
    DocumentSerializer serializer = new DocumentSerializer();
    Document original = sample();
    Document copy = serializer.copy(original);
    assertNotSame(original.page(0).rootNode, copy.page(0).rootNode);
    copy.page(0).rootNode.name = "Changed";
    assertEquals("Poster", original.page(0).rootNode.name);
  }

  @Test
  void savedFilesAreWrittenAtomicallyAndReloaded(@TempDir Path folder) throws Exception {
    DocumentSerializer serializer = new DocumentSerializer();
    File file = folder.resolve("project.fpb").toFile();
    serializer.save(file, sample());
    assertTrue(file.isFile());
    assertEquals(0, Files.list(folder).filter(p -> p.toString().endsWith(".tmp")).count(),
        "No temporary file should be left behind");
    assertEquals("Poster", serializer.load(file).title);
  }

  @Test
  void malformedJsonFailsWithAReadableMessage() {
    DocumentSerializer serializer = new DocumentSerializer();
    assertTrue(assertThrows(IllegalArgumentException.class, () -> serializer.fromJson("{not json"))
        .getMessage().contains("not valid JSON"));
    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> serializer.fromJson("{\"schemaVersion\":7,\"pages\":[]}"))
        .getMessage().contains("Unsupported project schema version"));
  }
}

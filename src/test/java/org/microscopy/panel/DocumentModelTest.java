package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DocumentModelTest {
  private static Document withTwoPanels() {
    Document document = Document.empty();
    Node root = Node.container("Poster", 1, 2);
    root.add(Node.leaf("Left", Content.of(TextContent.of("left"))), 0, 0);
    root.add(Node.leaf("Right", Content.of(TextContent.of("right"))), 0, 1);
    document.page(0).rootNode = root;
    return document;
  }

  private static String message(org.junit.jupiter.api.function.Executable body) {
    return assertThrows(IllegalArgumentException.class, body).getMessage();
  }


  @Test
  void aValidDocumentPasses() {
    withTwoPanels().validate();
  }

  @Test
  void everyDepthHasTheSameCapabilities() {
    // A node three levels down accepts layout, appearance and content just like the root.
    Document document = Document.empty();
    Node root = Node.container("Poster", 1, 1);
    Node section = Node.container("Results", 1, 1);
    Node group = Node.container("Figure 2", 1, 1);
    group.appearance.set(Prop.CORNER_RADIUS_MM, PropertyValue.of(3.0));
    group.add(Node.leaf("Caption", Content.of(TextContent.of("caption"))), 0, 0);
    section.add(group, 0, 0);
    root.add(section, 0, 0);
    document.page(0).rootNode = root;
    document.validate();
    assertEquals(4, root.count());
    java.util.List<Node> path = root.pathTo(group.id);
    assertEquals(3, path.size());
    assertEquals("Poster", path.get(0).name);
    assertEquals("Results", path.get(1).name);
    assertEquals("Figure 2", path.get(2).name);
  }

  @Test
  void contentAndChildrenAreMutuallyExclusive() {
    Document document = Document.empty();
    Node node = Node.leaf("Text", Content.of(TextContent.of("hello")));
    node.children.add(new Node("Child"));
    document.page(0).rootNode = node;
    assertTrue(message(document::validate).contains("cannot also have children"));
  }

  @Test
  void duplicateNodeIdsAreRejected() {
    Document document = withTwoPanels();
    Node root = document.page(0).rootNode;
    root.children.get(1).id = root.children.get(0).id;
    assertTrue(message(document::validate).contains("Duplicate node id"));
  }

  @Test
  void sameAsMustNameASibling() {
    Document document = withTwoPanels();
    Node root = document.page(0).rootNode;
    root.children.get(0).size.width = SizeExpr.sameAs(root.children.get(1).id);
    document.validate();
    root.children.get(0).size.width = SizeExpr.sameAs("not-a-sibling");
    assertTrue(message(document::validate).contains("must name a sibling"));
  }

  @Test
  void bothEdgesCannotBeAnAspectRatio() {
    Document document = withTwoPanels();
    Node panel = document.page(0).rootNode.children.get(0);
    panel.size.width = SizeExpr.aspectRatio(1.5);
    panel.size.height = SizeExpr.aspectRatio(1.5);
    assertTrue(message(document::validate).contains("one edge must be resolvable"));
  }

  @Test
  void danglingStyleTokenAndAssetReferencesAreRejected() {
    Document document = withTwoPanels();
    document.page(0).rootNode.children.get(0).appearance.styleId = "missing";
    assertTrue(message(document::validate).contains("missing style"));
    document.page(0).rootNode.children.get(0).appearance.styleId = null;
    document.page(0).rootNode.children.get(0).appearance.set(Prop.FILL, PropertyValue.token("gone"));
    assertTrue(message(document::validate).contains("missing token"));
  }

  @Test
  void tokenAliasesStayOneLevelDeep() {
    Document document = withTwoPanels();
    document.tokens.put("a", new Token("a", "A", Token.Kind.FONT_SIZE_PT, "28"));
    Token alias = new Token("b", "B", Token.Kind.FONT_SIZE_PT, null);
    alias.aliasOf = "a";
    document.tokens.put("b", alias);
    document.validate();
    Token second = new Token("c", "C", Token.Kind.FONT_SIZE_PT, null);
    second.aliasOf = "b";
    document.tokens.put("c", second);
    assertTrue(message(document::validate).contains("single level"));
  }

  @Test
  void anUnknownSchemaVersionIsRefusedRatherThanGuessed() {
    Document document = withTwoPanels();
    document.schemaVersion = 99;
    assertTrue(message(document::validate).contains("Unsupported project schema version"));
  }
}

package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DocumentEditsTest {
  private Document document;
  private Node root, first, second;

  private void twoPanels() {
    document = Document.empty();
    root = Node.container("Root", 1, 2);
    first = Node.leaf("First", Content.of(TextContent.of("one")));
    second = Node.leaf("Second", Content.of(TextContent.of("two")));
    root.add(first, 0, 0);
    root.add(second, 0, 1);
    document.page(0).rootNode = root;
    document.validate();
  }

  private Page page() { return document.page(0); }

  @Test
  void wrappingKeepsThePlaceAndGivesTheNodeAHome() {
    twoPanels();
    Node container = DocumentEdits.wrapInContainer(page(), first.id);
    document.validate();
    assertEquals(0, container.placement.column);
    assertSame(first, container.children.get(0));
    assertEquals(SizeExpr.Kind.FRACTION, first.size.width.kind);
    assertEquals(root, page().rootNode.parentOf(container.id));
    assertEquals(2, root.children.size());
  }

  @Test
  void splittingAddsAnEmptyCellBesideTheNode() {
    twoPanels();
    Node empty = DocumentEdits.split(page(), first.id, true);
    document.validate();
    Node container = page().rootNode.parentOf(first.id);
    assertEquals(2, container.children.size());
    assertEquals(2, container.layout.columns.size());
    assertEquals(0, empty.placement.row);
    assertEquals(1, empty.placement.column);
    assertEquals(Content.Kind.NONE, empty.content.kind);
  }

  @Test
  void splittingVerticallyStacksTheNewCell() {
    twoPanels();
    Node empty = DocumentEdits.split(page(), first.id, false);
    document.validate();
    assertEquals(1, empty.placement.row);
    assertEquals(0, empty.placement.column);
    assertEquals(2, page().rootNode.parentOf(first.id).layout.rows.size());
  }

  @Test
  void unwrappingLiftsChildrenBackIntoTheParent() {
    twoPanels();
    DocumentEdits.split(page(), first.id, true);
    Node container = page().rootNode.parentOf(first.id);
    DocumentEdits.unwrap(page(), container.id);
    document.validate();
    assertEquals(root, page().rootNode.parentOf(first.id));
    assertEquals(3, root.children.size());
    assertNotNull(root.find(second.id));
  }

  @Test
  void theRootOnlyDissolvesWhenItHoldsOneChild() {
    twoPanels();
    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> DocumentEdits.unwrap(page(), root.id)).getMessage().contains("single child"));
    DocumentEdits.remove(page(), second.id);
    DocumentEdits.unwrap(page(), root.id);
    document.validate();
    assertSame(first, page().rootNode);
  }

  @Test
  void movingOntoAnOccupiedCellSwapsTheTwo() {
    twoPanels();
    DocumentEdits.moveTo(page(), first.id, 0, 1);
    document.validate();
    assertEquals(1, first.placement.column);
    assertEquals(0, second.placement.column);
  }

  @Test
  void draggingABoundaryTurnsBothTracksIntoFractions() {
    twoPanels();
    root.layout.columns.set(0, SizeExpr.fixed(30));
    DocumentEdits.setBoundary(root, true, 0, 120, 80);
    document.validate();
    assertEquals(SizeExpr.Kind.FRACTION, root.layout.columns.get(0).kind);
    assertEquals(120, root.layout.columns.get(0).value, 1e-9);
    assertEquals(80, root.layout.columns.get(1).value, 1e-9);
    document.page(0).size = PageSize.fixed(200, 100);
    LayoutResult result = new LayoutEngine().layout(document);
    assertEquals(120, result.of(first.id).width, 1e-9);
    assertEquals(80, result.of(second.id).width, 1e-9);
  }

  @Test
  void aBoundaryCannotBeDraggedPastTheEdge() {
    twoPanels();
    DocumentEdits.setBoundary(root, true, 0, 500, 0);
    // Five percent stays on each side so a track can always be grabbed again.
    assertEquals(475, root.layout.columns.get(0).value, 1e-9);
    assertEquals(25, root.layout.columns.get(1).value, 1e-9);
  }

  @Test
  void convertingToAnOverlayStacksTheChildrenInOrder() {
    twoPanels();
    DocumentEdits.convertToStack(page(), root.id, true);
    document.validate();
    assertEquals(LayoutSpec.Mode.STACK, root.layout.mode);
    assertEquals(0, first.placement.zIndex);
    assertEquals(1, second.placement.zIndex);
    assertEquals(0, second.placement.column);
    DocumentEdits.bringForward(page(), first.id);
    DocumentEdits.bringForward(page(), first.id);
    assertEquals(2, first.placement.zIndex);
  }

  @Test
  void theRootCannotBeRemoved() {
    twoPanels();
    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> DocumentEdits.remove(page(), root.id)).getMessage().contains("cannot be removed"));
  }
}

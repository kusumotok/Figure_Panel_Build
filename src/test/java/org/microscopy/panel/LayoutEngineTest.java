package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LayoutEngineTest {
  private static final double EPSILON = 1e-9;

  private static Document page(double widthMm, double heightMm, Node root) {
    Document document = Document.empty();
    document.page(0).size = PageSize.fixed(widthMm, heightMm);
    document.page(0).rootNode = root;
    return document;
  }

  private static Node cell(String name) {
    Node node = Node.leaf(name, Content.of(new ShapeContent()));
    node.size.width = SizeExpr.fill();
    node.size.height = SizeExpr.fill();
    return node;
  }

  @Test
  void tracksAndGapsExactlyFillTheContentBox() {
    Node root = Node.container("Root", 1, 3);
    root.layout.columns.set(0, SizeExpr.fixed(40));
    root.layout.columns.set(1, SizeExpr.fraction(1));
    root.layout.columns.set(2, SizeExpr.fraction(2));
    root.layout.columnGapMm = 5;
    for (int i = 0; i < 3; i++) root.add(cell("c" + i), 0, i);
    LayoutResult result = new LayoutEngine().layout(page(200, 100, root));

    RectMm a = result.of(root.children.get(0).id);
    RectMm b = result.of(root.children.get(1).id);
    RectMm c = result.of(root.children.get(2).id);
    assertEquals(40, a.width, EPSILON);
    // 200 - 40 - 2 gaps of 5 = 150, shared 1:2.
    assertEquals(50, b.width, EPSILON);
    assertEquals(100, c.width, EPSILON);
    assertEquals(200, c.right(), EPSILON);
    assertEquals(a.right() + 5, b.x, EPSILON);
    assertEquals(b.right() + 5, c.x, EPSILON);
    assertTrue(result.warnings().isEmpty(), "A clean fit should not warn: " + result.warnings());
  }

  @Test
  void aspectRatioKeepsAnImageUndistortedWhenTheColumnStretches() {
    Node root = Node.container("Root", 1, 2);
    root.layout.columns.set(0, SizeExpr.fraction(1));
    root.layout.columns.set(1, SizeExpr.fraction(1));
    Node image = cell("Image");
    image.size.height = SizeExpr.aspectRatio(0.75);
    root.add(image, 0, 0);
    root.add(cell("Other"), 0, 1);
    LayoutResult result = new LayoutEngine().layout(page(200, 200, root));
    RectMm rect = result.of(image.id);
    assertEquals(100, rect.width, EPSILON);
    assertEquals(75, rect.height, EPSILON);
  }

  @Test
  void sameAsCopiesASiblingRegardlessOfOrder() {
    Node root = Node.container("Root", 1, 2);
    Node first = cell("First"), second = cell("Second");
    second.size.width = SizeExpr.fixed(60);
    first.size.width = SizeExpr.sameAs(second.id);
    root.add(first, 0, 0);
    root.add(second, 0, 1);
    LayoutResult result = new LayoutEngine().layout(page(200, 100, root));
    assertEquals(60, result.of(first.id).width, EPSILON);
  }

  @Test
  void sameAsCyclesAreRefusedByTheDocumentItself() {
    Node root = Node.container("Root", 1, 2);
    Node first = cell("First"), second = cell("Second");
    first.size.width = SizeExpr.sameAs(second.id);
    second.size.width = SizeExpr.sameAs(first.id);
    root.add(first, 0, 0);
    root.add(second, 0, 1);
    Document document = page(200, 100, root);
    assertTrue(assertThrows(IllegalArgumentException.class, document::validate)
        .getMessage().contains("cycle"));
  }

  @Test
  void clampsAreHonouredAndTheRemainderStaysAccountedFor() {
    Node root = Node.container("Root", 1, 2);
    root.layout.columns.set(0, SizeExpr.fraction(1).clamped(null, 60.0));
    root.layout.columns.set(1, SizeExpr.fraction(1));
    root.add(cell("Capped"), 0, 0);
    root.add(cell("Free"), 0, 1);
    LayoutResult result = new LayoutEngine().layout(page(200, 100, root));
    RectMm capped = result.of(root.children.get(0).id);
    RectMm free = result.of(root.children.get(1).id);
    assertEquals(60, capped.width, EPSILON);
    // The space the clamp gave up goes to the other fraction track rather than vanishing.
    assertEquals(140, free.width, EPSILON);
    assertEquals(200, capped.width + free.width, EPSILON);
    assertTrue(result.warnings().isEmpty(), "Clamping settles in one extra pass: " + result.warnings());
  }

  @Test
  void spansCoverTheirTracksAndTheGapsBetweenThem() {
    Node root = Node.container("Root", 2, 3);
    for (int i = 0; i < 3; i++) root.layout.columns.set(i, SizeExpr.fraction(1));
    root.layout.columnGapMm = 4;
    Node wide = cell("Wide");
    wide.placement.columnSpan = 2;
    root.add(wide, 0, 0);
    root.add(cell("Narrow"), 0, 2);
    LayoutResult result = new LayoutEngine().layout(page(200, 100, root));
    double track = (200 - 8) / 3.0;
    assertEquals(track * 2 + 4, result.of(wide.id).width, 1e-9);
  }

  @Test
  void autoPagesGrowToFitTheirContent() {
    Node root = Node.container("Root", 2, 1);
    Node top = Node.leaf("Top", Content.of(new ShapeContent()));
    top.size.height = SizeExpr.fixed(30);
    top.size.width = SizeExpr.fixed(80);
    Node bottom = Node.leaf("Bottom", Content.of(new ShapeContent()));
    bottom.size.height = SizeExpr.fixed(20);
    bottom.size.width = SizeExpr.fixed(50);
    root.layout.rowGapMm = 10;
    root.add(top, 0, 0);
    root.add(bottom, 1, 0);
    Document document = Document.empty();
    document.page(0).margins = Margins.uniform(5);
    document.page(0).rootNode = root;
    LayoutResult result = new LayoutEngine().layout(document);
    assertEquals(80 + 10, result.pageBox().width, EPSILON);
    assertEquals(30 + 10 + 20 + 10, result.pageBox().height, EPSILON);
    assertEquals(5, result.of(root.id).x, EPSILON);
  }

  @Test
  void stackOverlaysChildrenAndOrdersThemByLocalZIndex() {
    Node root = Node.container("Root", 1, 1);
    root.layout.mode = LayoutSpec.Mode.STACK;
    Node image = cell("Image"), label = cell("Label");
    label.placement.zIndex = 5;
    image.placement.zIndex = 1;
    root.children.add(label);
    root.children.add(image);
    LayoutResult result = new LayoutEngine().layout(page(100, 80, root));
    assertEquals(result.of(image.id).width, result.of(label.id).width, EPSILON);
    assertTrue(result.paintOrder().indexOf(image.id) < result.paintOrder().indexOf(label.id),
        "Lower z-index paints first");
  }

  @Test
  void hitTestingReturnsTheDeepestNodeUnderThePoint() {
    Node root = Node.container("Root", 1, 2);
    Node left = cell("Left"), right = cell("Right");
    root.add(left, 0, 0);
    root.add(right, 0, 1);
    Document document = page(200, 100, root);
    LayoutResult result = new LayoutEngine().layout(document);
    assertEquals(left.id, result.hit(root, 10, 10));
    assertEquals(right.id, result.hit(root, 150, 10));
  }

  @Test
  void oversizedContentIsReportedRatherThanSilentlyOverlapping() {
    Node root = Node.container("Root", 1, 1);
    root.layout.columns.set(0, SizeExpr.fixed(40));
    Node child = cell("Too wide");
    child.size.width = SizeExpr.fixed(90);
    root.add(child, 0, 0);
    LayoutResult result = new LayoutEngine().layout(page(200, 100, root));
    assertEquals(40, result.of(child.id).width, EPSILON);
    assertTrue(result.warnings().toString().contains("larger than the space available"));
  }

  @Test
  void aChildPinnedByItsAspectRatioIsCentredInTheCellItCannotFill() {
    Node root = Node.container("Root", 1, 1);
    Node image = cell("Image");
    image.size.height = SizeExpr.aspectRatio(0.5);
    root.add(image, 0, 0);
    LayoutResult result = new LayoutEngine().layout(page(200, 200, root));
    RectMm rect = result.of(image.id);
    assertEquals(200, rect.width, EPSILON);
    assertEquals(100, rect.height, EPSILON);
    // Stretch cannot apply, so the leftover 100 mm is split above and below rather than dumped
    // at the bottom.
    assertEquals(50, rect.y, EPSILON);
  }

  @Test
  void tracksThatCannotFitThePageAreReported() {
    Node root = Node.container("Root", 2, 1);
    root.layout.rows.set(0, SizeExpr.fixed(80));
    root.layout.rows.set(1, SizeExpr.fixed(80));
    root.add(cell("Top"), 0, 0);
    root.add(cell("Bottom"), 1, 0);
    LayoutResult result = new LayoutEngine().layout(page(100, 100, root));
    assertTrue(result.warnings().toString().contains("the rows need 160 mm but only 100 mm"),
        result.warnings().toString());
  }
}

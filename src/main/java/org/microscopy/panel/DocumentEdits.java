package org.microscopy.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Structural edits on the node tree, kept out of the canvas so the gestures stay thin and the
 * behaviour can be checked without a screen.
 *
 * <p>Every operation leaves the document valid, or throws before changing anything.
 */
public final class DocumentEdits {
  private DocumentEdits() {}

  /** Wraps a node in a container so it can be subdivided; returns the new container. */
  public static Node wrapInContainer(Page page, String nodeId) {
    Node node = require(page, nodeId);
    Node parent = page.rootNode.parentOf(nodeId);
    Node container = Node.container(node.name + " group", 1, 1);
    container.size.width = node.size.width;
    container.size.height = node.size.height;
    container.placement.row = node.placement.row;
    container.placement.column = node.placement.column;
    container.placement.rowSpan = node.placement.rowSpan;
    container.placement.columnSpan = node.placement.columnSpan;
    container.placement.zIndex = node.placement.zIndex;
    node.size = new NodeSize();
    node.size.width = SizeExpr.fill();
    node.size.height = SizeExpr.fill();
    node.placement = new Placement(0, 0);
    container.children.add(node);
    if (parent == null) page.rootNode = container;
    else parent.children.set(parent.children.indexOf(node), container);
    return container;
  }

  /** Puts an empty cell beside the node, wrapping it first if it is not already in a pair. */
  public static Node split(Page page, String nodeId, boolean horizontally) {
    Node node = require(page, nodeId);
    Node container = wrapInContainer(page, nodeId);
    container.name = node.name + (horizontally ? " row" : " column");
    container.layout = horizontally ? LayoutSpec.grid(1, 2) : LayoutSpec.grid(2, 1);
    List<SizeExpr> tracks = horizontally ? container.layout.columns : container.layout.rows;
    for (int i = 0; i < tracks.size(); i++) tracks.set(i, SizeExpr.fill());
    Node empty = Node.container("Empty", 1, 1);
    empty.size.width = SizeExpr.fill();
    empty.size.height = SizeExpr.fill();
    container.add(empty, horizontally ? 0 : 1, horizontally ? 1 : 0);
    return empty;
  }

  /** Dissolves a container, lifting its children into the parent grid. */
  public static void unwrap(Page page, String nodeId) {
    Node node = require(page, nodeId);
    if (node.children.isEmpty())
      throw new IllegalArgumentException("Only a container can be dissolved.");
    Node parent = page.rootNode.parentOf(nodeId);
    if (parent == null) {
      if (node.children.size() != 1)
        throw new IllegalArgumentException(
            "The page root can only be dissolved when it holds a single child.");
      page.rootNode = node.children.get(0);
      page.rootNode.placement = new Placement(0, 0);
      return;
    }
    int at = parent.children.indexOf(node);
    parent.children.remove(at);
    // Children keep their own grid positions, which is what makes the result predictable when a
    // one-cell wrapper is removed and surprising otherwise; the caller re-flows if it matters.
    for (Node child : node.children) {
      child.placement.row += node.placement.row;
      child.placement.column += node.placement.column;
      parent.children.add(at++, child);
    }
  }

  public static void remove(Page page, String nodeId) {
    Node parent = page.rootNode.parentOf(nodeId);
    if (parent == null) throw new IllegalArgumentException("The page root cannot be removed.");
    Node node = require(page, nodeId);
    parent.children.remove(node);
  }

  /** Moves a node to a cell, swapping with whatever already occupies it. */
  public static void moveTo(Page page, String nodeId, int row, int column) {
    Node node = require(page, nodeId);
    Node parent = page.rootNode.parentOf(nodeId);
    if (parent == null) throw new IllegalArgumentException("The page root has no cell to move to.");
    if (row < 0 || column < 0) throw new IllegalArgumentException("Negative grid position.");
    for (Node sibling : parent.children) {
      if (sibling == node) continue;
      if (sibling.placement.row != row || sibling.placement.column != column) continue;
      sibling.placement.row = node.placement.row;
      sibling.placement.column = node.placement.column;
      break;
    }
    node.placement.row = row;
    node.placement.column = column;
  }

  public static void bringForward(Page page, String nodeId) { shift(page, nodeId, 1); }

  public static void sendBackward(Page page, String nodeId) { shift(page, nodeId, -1); }

  private static void shift(Page page, String nodeId, int delta) {
    Node node = require(page, nodeId);
    Node parent = page.rootNode.parentOf(nodeId);
    if (parent == null) return;
    // Z order is local to the parent by design, so this only ever reorders siblings.
    node.placement.zIndex += delta;
  }

  /** Turns a container into an overlay, which is how annotations sit on top of an image. */
  public static void convertToStack(Page page, String nodeId, boolean stack) {
    Node node = require(page, nodeId);
    if (node.children.isEmpty())
      throw new IllegalArgumentException("Only a container can become an overlay.");
    node.layout.mode = stack ? LayoutSpec.Mode.STACK : LayoutSpec.Mode.GRID;
    if (!stack) return;
    int index = 0;
    for (Node child : node.children) {
      child.placement.row = 0;
      child.placement.column = 0;
      child.placement.zIndex = index++;
    }
  }

  /**
   * Sets the weights of two neighbouring tracks from a dragged boundary. Both become fractions so
   * the pair keeps filling the same span whatever the container is later resized to.
   */
  public static void setBoundary(Node parent, boolean columns, int before, double beforeMm,
      double afterMm) {
    if (beforeMm < 0 || afterMm < 0) throw new IllegalArgumentException("Negative track size.");
    List<SizeExpr> tracks = columns ? parent.layout.columns : parent.layout.rows;
    while (tracks.size() <= before + 1) tracks.add(SizeExpr.auto());
    double total = beforeMm + afterMm;
    if (!(total > 0)) throw new IllegalArgumentException("A boundary needs room on both sides.");
    double minimum = total * 0.05;
    double first = Math.max(minimum, Math.min(total - minimum, beforeMm));
    tracks.set(before, SizeExpr.fraction(first));
    tracks.set(before + 1, SizeExpr.fraction(total - first));
  }

  /** Every node that can be selected, in paint order; used for keyboard and marquee selection. */
  public static List<Node> flatten(Node root) {
    List<Node> all = new ArrayList<Node>();
    collect(root, all);
    return all;
  }

  private static void collect(Node node, List<Node> out) {
    out.add(node);
    for (Node child : node.children) collect(child, out);
  }

  private static Node require(Page page, String nodeId) {
    Node node = page.rootNode.find(nodeId);
    if (node == null) throw new IllegalArgumentException("No such node: " + nodeId);
    return node;
  }

  /**
   * Grows a node over the neighbouring cell. Only an empty neighbour is absorbed: merging over
   * something that holds content would silently destroy it.
   */
  public static void expandSpan(Page page, String nodeId, boolean horizontally) {
    Node node = require(page, nodeId);
    Node parent = page.rootNode.parentOf(nodeId);
    if (parent == null) throw new IllegalArgumentException("The page root has nothing to merge into.");
    int row = node.placement.row + (horizontally ? 0 : node.placement.rowSpan);
    int column = node.placement.column + (horizontally ? node.placement.columnSpan : 0);
    Node occupant = null;
    for (Node sibling : parent.children) {
      if (sibling == node) continue;
      if (covers(sibling, row, column)) {
        occupant = sibling;
        break;
      }
    }
    if (occupant != null) {
      boolean empty = occupant.children.isEmpty() && occupant.content.kind == Content.Kind.NONE;
      if (!empty)
        throw new IllegalArgumentException("The next cell holds '" + occupant.name
            + "'. Move or delete it before merging.");
      parent.children.remove(occupant);
    }
    if (horizontally) node.placement.columnSpan++;
    else node.placement.rowSpan++;
  }

  private static boolean covers(Node node, int row, int column) {
    return row >= node.placement.row && row < node.placement.row + node.placement.rowSpan
        && column >= node.placement.column
        && column < node.placement.column + node.placement.columnSpan;
  }

  /**
   * Extends a text node's flow area into the neighbouring cell, creating the region on the first
   * call. Growing one cell at a time avoids needing a multiple selection, and each step is
   * visible on the canvas straight away.
   */
  public static void extendTextArea(Page page, String nodeId, boolean horizontally) {
    Node node = require(page, nodeId);
    if (node.content.kind != Content.Kind.TEXT)
      throw new IllegalArgumentException("Only a text node has a text area.");
    Node parent = page.rootNode.parentOf(nodeId);
    if (parent == null) throw new IllegalArgumentException("The page root has no cells to use.");
    TextContent text = node.content.text;
    if (text.flowRegion == null || text.flowRegion.cells.isEmpty()) {
      text.flowRegion = new FlowRegion();
      text.flowRegion.cells.add(
          new FlowRegion.Cell(node.placement.row, node.placement.column));
    }
    FlowRegion.Cell last = text.flowRegion.cells.get(text.flowRegion.cells.size() - 1);
    FlowRegion.Cell next = new FlowRegion.Cell(last.row + (horizontally ? 0 : 1),
        last.column + (horizontally ? 1 : 0));
    for (FlowRegion.Cell cell : text.flowRegion.cells)
      if (cell.same(next))
        throw new IllegalArgumentException("That cell is already part of the text area.");
    text.flowRegion.cells.add(next);
  }

  /** Puts a text node back in its own cell. */
  public static void resetTextArea(Page page, String nodeId) {
    Node node = require(page, nodeId);
    if (node.content.kind != Content.Kind.TEXT)
      throw new IllegalArgumentException("Only a text node has a text area.");
    node.content.text.flowRegion = null;
  }

  /**
   * Replaces an attached project with a copy of its contents, cutting the link. The same choice
   * a page layout program offers for placed artwork: linked while the figure is still moving,
   * embedded once it has settled or when the poster has to travel on its own.
   *
   * <p>Ids are given a fresh prefix so two copies of the same figure can sit side by side, and
   * the styles and assets it needs come with it.
   */
  public static Node embedAttached(Page page, String nodeId, Document into,
      LinkedProjects.Attached attached) {
    Node node = require(page, nodeId);
    if (node.content.kind != Content.Kind.PROJECT)
      throw new IllegalArgumentException("Only an attached project can be embedded.");
    Node parent = page.rootNode.parentOf(nodeId);
    if (parent == null) throw new IllegalArgumentException("The page root cannot be replaced.");
    String prefix = java.util.UUID.randomUUID().toString().substring(0, 8) + "-";
    Document copy = new DocumentSerializer().copy(attached.document);
    Page source = copy.pages.get(copy.pages.indexOf(pageOf(copy, attached.page.id)));

    for (Map.Entry<String, Token> token : attached.document.tokens.entrySet())
      into.tokens.put(prefix + token.getKey(), rename(token.getValue(), prefix));
    for (Map.Entry<String, Style> style : copy.styles.entrySet())
      into.styles.put(prefix + style.getKey(), rename(style.getValue(), prefix));
    for (Map.Entry<String, Asset> asset : copy.assets.entrySet())
      into.assets.put(prefix + asset.getKey(), rename(asset.getValue(), prefix));

    Node embedded = source.rootNode;
    reid(embedded, prefix);
    embedded.name = node.name == null || node.name.isEmpty() ? embedded.name : node.name;
    embedded.placement = node.placement;
    embedded.size = node.size;
    parent.children.set(parent.children.indexOf(node), embedded);
    return embedded;
  }

  private static Page pageOf(Document document, String pageId) {
    for (Page page : document.pages) if (page.id.equals(pageId)) return page;
    return document.pages.get(0);
  }

  private static Token rename(Token token, String prefix) {
    token.id = prefix + token.id;
    if (token.aliasOf != null && !token.aliasOf.isEmpty()) token.aliasOf = prefix + token.aliasOf;
    return token;
  }

  private static Style rename(Style style, String prefix) {
    style.id = prefix + style.id;
    for (PropertyValue value : style.properties.values())
      if (value.kind == PropertyValue.Kind.TOKEN_REF) value.tokenId = prefix + value.tokenId;
    return style;
  }

  private static Asset rename(Asset asset, String prefix) {
    asset.id = prefix + asset.id;
    for (Asset.Part part : asset.parts) part.assetId = prefix + part.assetId;
    return asset;
  }

  private static void reid(Node node, String prefix) {
    node.id = prefix + node.id;
    if (node.appearance.styleId != null) node.appearance.styleId = prefix + node.appearance.styleId;
    for (PropertyValue value : node.appearance.overrides.values())
      if (value.kind == PropertyValue.Kind.TOKEN_REF) value.tokenId = prefix + value.tokenId;
    if (node.size.width.kind == SizeExpr.Kind.SAME_AS)
      node.size.width.siblingId = prefix + node.size.width.siblingId;
    if (node.size.height.kind == SizeExpr.Kind.SAME_AS)
      node.size.height.siblingId = prefix + node.size.height.siblingId;
    if (node.content.kind == Content.Kind.SCIENTIFIC_IMAGE) {
      node.content.scientificImage.assetId = prefix + node.content.scientificImage.assetId;
      // The wrapped figure configuration addresses its source by the same id.
      for (org.microscopy.figure.ConditionConfig condition
          : node.content.scientificImage.figureConfig.conditions)
        condition.sourceId = prefix + condition.sourceId;
    }
    if (node.content.kind == Content.Kind.IMAGE)
      node.content.image.assetId = prefix + node.content.image.assetId;
    for (Node child : node.children) reid(child, prefix);
  }
}

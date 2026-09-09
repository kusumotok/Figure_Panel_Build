package org.microscopy.panel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One node of the document tree. The capability set is the same at every depth: "poster",
 * "panel" and "figure frame" are names people use, not different types.
 */
public final class Node {
  public String id = UUID.randomUUID().toString();
  /** Shown in the breadcrumb and written into exported shape names. */
  public String name = "";
  public LayoutSpec layout = new LayoutSpec();
  public NodeSize size = new NodeSize();
  public Placement placement = new Placement();
  public Appearance appearance = new Appearance();
  public List<Node> children = new ArrayList<Node>();
  public Content content = Content.none();

  public Node() {}

  public Node(String name) { this.name = name; }

  public static Node container(String name, int rows, int columns) {
    Node node = new Node(name);
    node.layout = LayoutSpec.grid(rows, columns);
    return node;
  }

  public static Node leaf(String name, Content content) {
    Node node = new Node(name);
    node.content = content;
    return node;
  }

  public Node add(Node child, int row, int column) {
    // Keeps any span and z-index the caller already set on the child.
    child.placement.row = row;
    child.placement.column = column;
    children.add(child);
    return this;
  }

  public Node find(String nodeId) {
    if (id.equals(nodeId)) return this;
    for (Node child : children) {
      Node found = child.find(nodeId);
      if (found != null) return found;
    }
    return null;
  }

  public Node parentOf(String nodeId) {
    for (Node child : children) {
      if (child.id.equals(nodeId)) return this;
      Node found = child.parentOf(nodeId);
      if (found != null) return found;
    }
    return null;
  }

  /** Ancestors first, ending with this node: the breadcrumb path. */
  public List<Node> pathTo(String nodeId) {
    List<Node> path = new ArrayList<Node>();
    if (!collectPath(nodeId, path)) return new ArrayList<Node>();
    return path;
  }

  private boolean collectPath(String nodeId, List<Node> path) {
    path.add(this);
    if (id.equals(nodeId)) return true;
    for (Node child : children) if (child.collectPath(nodeId, path)) return true;
    path.remove(path.size() - 1);
    return false;
  }

  public int count() {
    int total = 1;
    for (Node child : children) total += child.count();
    return total;
  }

  void validate(String where, Set<String> seenIds) {
    if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException(where + ": node without an id.");
    if (!seenIds.add(id)) throw new IllegalArgumentException("Duplicate node id: " + id);
    if (name == null) throw new IllegalArgumentException(where + ": node without a name.");
    if (children == null) throw new IllegalArgumentException(where + ": node without a child list.");
    if (layout == null || size == null || placement == null || appearance == null || content == null)
      throw new IllegalArgumentException(where + ": incomplete node.");
    String label = where + " / " + (name.isEmpty() ? id : name);
    layout.validate(label);
    size.validate(label);
    placement.validate(label);
    appearance.validate(label);
    content.validate(label);
    if (content.kind != Content.Kind.NONE && !children.isEmpty())
      throw new IllegalArgumentException(label + ": a node with content cannot also have children.");
    Set<String> siblingIds = new HashSet<String>();
    for (Node child : children) siblingIds.add(child.id);
    for (Node child : children) {
      checkSibling(label, child.size.width, siblingIds, child.id);
      checkSibling(label, child.size.height, siblingIds, child.id);
    }
    checkNoSameAsCycle(label, children, true);
    checkNoSameAsCycle(label, children, false);
    for (Node child : children) child.validate(label, seenIds);
  }

  private static void checkSibling(String label, SizeExpr expr, Set<String> siblingIds, String selfId) {
    if (expr.kind != SizeExpr.Kind.SAME_AS) return;
    if (expr.siblingId.equals(selfId))
      throw new IllegalArgumentException(label + ": a node cannot be the same size as itself.");
    if (!siblingIds.contains(expr.siblingId))
      throw new IllegalArgumentException(
          label + ": Same as must name a sibling under the same parent (" + expr.siblingId + ").");
  }

  /**
   * Two siblings pointing Same as at each other would never resolve, so the document refuses to
   * hold such a chain rather than leaving the layout engine to discover it.
   */
  private static void checkNoSameAsCycle(String label, List<Node> children, boolean widthAxis) {
    java.util.Map<String, Node> byId = new java.util.HashMap<String, Node>();
    for (Node child : children) byId.put(child.id, child);
    for (Node start : children) {
      Set<String> seen = new HashSet<String>();
      Node current = start;
      while (current != null) {
        SizeExpr expr = widthAxis ? current.size.width : current.size.height;
        if (expr.kind != SizeExpr.Kind.SAME_AS) break;
        if (!seen.add(current.id))
          throw new IllegalArgumentException(label + ": Same as forms a cycle through '"
              + (current.name.isEmpty() ? current.id : current.name) + "'.");
        current = byId.get(expr.siblingId);
      }
    }
  }
}

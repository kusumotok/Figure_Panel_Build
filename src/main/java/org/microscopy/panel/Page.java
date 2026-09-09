package org.microscopy.panel;

import java.util.HashSet;
import java.util.UUID;

/**
 * One printable surface. A poster needs a single page, but slides and multi-part figures need
 * several, and retrofitting that later would mean rebuilding the id scheme and the PPTX mapping.
 */
public final class Page {
  public String id = UUID.randomUUID().toString();
  public String name = "";
  public PageSize size = PageSize.auto();
  public Margins margins = new Margins();
  public Node rootNode = Node.container("Page", 1, 1);

  public Page() {}

  public Page(String name) { this.name = name; }

  public void validate(String where) {
    if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException(where + ": page without an id.");
    if (name == null) throw new IllegalArgumentException(where + ": page without a name.");
    if (size == null || margins == null || rootNode == null)
      throw new IllegalArgumentException(where + ": incomplete page.");
    size.validate(where);
    margins.validate(where);
    rootNode.validate(where, new HashSet<String>());
  }
}

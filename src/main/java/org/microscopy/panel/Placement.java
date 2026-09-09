package org.microscopy.panel;

/** Where a child sits inside its parent's grid. Z order is local to the parent by design. */
public final class Placement {
  public int row, column;
  public int rowSpan = 1, columnSpan = 1;
  public int zIndex;

  public Placement() {}

  public Placement(int row, int column) { this.row = row; this.column = column; }

  public void validate(String where) {
    if (row < 0 || column < 0) throw new IllegalArgumentException(where + ": negative grid position.");
    if (rowSpan < 1 || columnSpan < 1) throw new IllegalArgumentException(where + ": span must be at least 1.");
  }
}

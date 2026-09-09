package org.microscopy.panel;

import java.util.ArrayList;
import java.util.List;

/**
 * A text area built from grid cells rather than a rectangle: union(cells) - exclusions. An
 * L-shaped region is the normal case, not a special one.
 */
public final class FlowRegion {
  /** Reading order when the region falls into disconnected islands. */
  public enum Order { AUTOMATIC, LEFT_TO_RIGHT, RIGHT_TO_LEFT, TOP_TO_BOTTOM, MANUAL }

  public static final class Cell {
    public int row, column;

    public Cell() {}

    public Cell(int row, int column) { this.row = row; this.column = column; }

    public boolean same(Cell other) { return other != null && other.row == row && other.column == column; }
  }

  public List<Cell> cells = new ArrayList<Cell>();
  public List<Cell> exclusions = new ArrayList<Cell>();
  public Order order = Order.AUTOMATIC;
  /** MANUAL only: island indices in reading order. */
  public List<Integer> manualOrder = new ArrayList<Integer>();

  public void validate(String where) {
    if (cells == null || exclusions == null || manualOrder == null)
      throw new IllegalArgumentException(where + ": incomplete flow region.");
    if (order == null) throw new IllegalArgumentException(where + ": flow region without an order.");
    if (cells.isEmpty()) throw new IllegalArgumentException(where + ": flow region needs at least one cell.");
    for (Cell cell : cells) {
      if (cell == null || cell.row < 0 || cell.column < 0)
        throw new IllegalArgumentException(where + ": invalid flow region cell.");
    }
    for (Cell cell : exclusions) {
      if (cell == null || cell.row < 0 || cell.column < 0)
        throw new IllegalArgumentException(where + ": invalid flow region exclusion.");
    }
    if (order != Order.MANUAL && !manualOrder.isEmpty())
      throw new IllegalArgumentException(where + ": manual order set but flow order is " + order + ".");
  }
}

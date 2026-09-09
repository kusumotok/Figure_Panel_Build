package org.microscopy.panel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a flow region into the shapes text has to fill.
 *
 * <p>A region is a union of grid cells minus exclusions, so it is always a set of axis-aligned
 * rectangles. Working with those directly, rather than a general area, keeps the per-line
 * arithmetic exact. Connectivity is judged on the grid rather than on geometry, because two cells
 * separated by a gap are still meant to read as one region.
 */
public final class FlowRegionGeometry {
  /** One connected part of a region, with its cells and their rectangles. */
  public static final class Island {
    public final List<FlowRegion.Cell> cells = new ArrayList<FlowRegion.Cell>();
    public final List<RectMm> rectangles = new ArrayList<RectMm>();

    public RectMm bounds() {
      double left = Double.MAX_VALUE, top = Double.MAX_VALUE;
      double right = -Double.MAX_VALUE, bottom = -Double.MAX_VALUE;
      for (RectMm rect : rectangles) {
        left = Math.min(left, rect.x);
        top = Math.min(top, rect.y);
        right = Math.max(right, rect.right());
        bottom = Math.max(bottom, rect.bottom());
      }
      return new RectMm(left, top, right - left, bottom - top);
    }

    /**
     * Horizontal runs available to a line occupying the band, left to right. An L-shaped island
     * yields one run on most lines and a wider one where the foot begins.
     */
    public List<double[]> runsAt(double top, double height) {
      List<double[]> runs = new ArrayList<double[]>();
      for (RectMm rect : rectangles) {
        // The whole band has to fit, not merely overlap. A line that straddles two rows would
        // otherwise borrow the wider row's width and hang outside the region for half its height.
        if (rect.y > top + 1e-9 || rect.bottom() < top + height - 1e-9) continue;
        runs.add(new double[] {rect.x, rect.right()});
      }
      Collections.sort(runs, new Comparator<double[]>() {
        public int compare(double[] a, double[] b) { return Double.compare(a[0], b[0]); }
      });
      List<double[]> merged = new ArrayList<double[]>();
      for (double[] run : runs) {
        if (!merged.isEmpty() && run[0] <= merged.get(merged.size() - 1)[1] + 1e-9) {
          double[] last = merged.get(merged.size() - 1);
          last[1] = Math.max(last[1], run[1]);
        } else {
          merged.add(new double[] {run[0], run[1]});
        }
      }
      return merged;
    }
  }

  private FlowRegionGeometry() {}

  /** The rectangle of one grid cell, gaps excluded. */
  public static RectMm cellRect(LayoutResult.Tracks tracks, int row, int column) {
    int r = Math.max(0, Math.min(row, tracks.rows() - 1));
    int c = Math.max(0, Math.min(column, tracks.columns() - 1));
    return new RectMm(tracks.columnStart[c], tracks.rowStart[r],
        tracks.columnSize[c], tracks.rowSize[r]);
  }

  public static List<Island> islands(LayoutResult.Tracks tracks, FlowRegion region) {
    Set<String> included = new LinkedHashSet<String>();
    List<FlowRegion.Cell> cells = new ArrayList<FlowRegion.Cell>();
    for (FlowRegion.Cell cell : region.cells) {
      if (excluded(region, cell)) continue;
      if (included.add(key(cell.row, cell.column))) cells.add(cell);
    }
    List<Island> islands = new ArrayList<Island>();
    Set<String> seen = new LinkedHashSet<String>();
    for (FlowRegion.Cell start : cells) {
      if (!seen.add(key(start.row, start.column))) continue;
      Island island = new Island();
      List<FlowRegion.Cell> queue = new ArrayList<FlowRegion.Cell>();
      queue.add(start);
      while (!queue.isEmpty()) {
        FlowRegion.Cell cell = queue.remove(queue.size() - 1);
        island.cells.add(cell);
        island.rectangles.add(cellRect(tracks, cell.row, cell.column));
        for (FlowRegion.Cell other : cells) {
          if (!adjacent(cell, other)) continue;
          if (!seen.add(key(other.row, other.column))) continue;
          queue.add(other);
        }
      }
      Collections.sort(island.cells, new Comparator<FlowRegion.Cell>() {
        public int compare(FlowRegion.Cell a, FlowRegion.Cell b) {
          return a.row != b.row ? Integer.compare(a.row, b.row)
              : Integer.compare(a.column, b.column);
        }
      });
      Collections.sort(island.rectangles, new Comparator<RectMm>() {
        public int compare(RectMm a, RectMm b) {
          return Math.abs(a.y - b.y) > 1e-9 ? Double.compare(a.y, b.y) : Double.compare(a.x, b.x);
        }
      });
      islands.add(island);
    }
    return islands;
  }

  /** Reading order between islands. Only offered when a region actually falls into several. */
  public static List<Island> ordered(List<Island> islands, FlowRegion region) {
    List<Island> ordered = new ArrayList<Island>(islands);
    if (islands.size() < 2) return ordered;
    if (region.order == FlowRegion.Order.MANUAL && !region.manualOrder.isEmpty()) {
      List<Island> manual = new ArrayList<Island>();
      for (Integer index : region.manualOrder)
        if (index != null && index >= 0 && index < islands.size()) manual.add(islands.get(index));
      for (Island island : islands) if (!manual.contains(island)) manual.add(island);
      return manual;
    }
    final boolean rightToLeft = region.order == FlowRegion.Order.RIGHT_TO_LEFT;
    final boolean columnsFirst = region.order == FlowRegion.Order.LEFT_TO_RIGHT || rightToLeft;
    Collections.sort(ordered, new Comparator<Island>() {
      public int compare(Island a, Island b) {
        RectMm first = a.bounds(), second = b.bounds();
        if (columnsFirst) {
          int byColumn = rightToLeft ? Double.compare(second.x, first.x)
              : Double.compare(first.x, second.x);
          return byColumn != 0 ? byColumn : Double.compare(first.y, second.y);
        }
        // Automatic and Top to bottom both read down first, then across.
        int byRow = Double.compare(first.y, second.y);
        return byRow != 0 ? byRow : Double.compare(first.x, second.x);
      }
    });
    return ordered;
  }

  private static boolean excluded(FlowRegion region, FlowRegion.Cell cell) {
    for (FlowRegion.Cell exclusion : region.exclusions) if (exclusion.same(cell)) return true;
    return false;
  }

  private static boolean adjacent(FlowRegion.Cell a, FlowRegion.Cell b) {
    int rows = Math.abs(a.row - b.row), columns = Math.abs(a.column - b.column);
    return rows + columns == 1;
  }

  private static String key(int row, int column) { return row + ":" + column; }
}

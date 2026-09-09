package org.microscopy.panel;

import java.util.ArrayList;
import java.util.List;

public final class LayoutSpec {
  public enum Mode { GRID, FLOW, STACK }

  public Mode mode = Mode.GRID;
  /** Track sizes. An empty list means "one track per occupied row/column, all Auto". */
  public List<SizeExpr> columns = new ArrayList<SizeExpr>();
  public List<SizeExpr> rows = new ArrayList<SizeExpr>();
  public double columnGapMm, rowGapMm;
  public Align alignX = Align.STRETCH, alignY = Align.STRETCH;

  public static LayoutSpec grid(int rowCount, int columnCount) {
    LayoutSpec spec = new LayoutSpec();
    for (int i = 0; i < rowCount; i++) spec.rows.add(SizeExpr.auto());
    for (int i = 0; i < columnCount; i++) spec.columns.add(SizeExpr.auto());
    return spec;
  }

  public void validate(String where) {
    if (mode == null) throw new IllegalArgumentException(where + ": missing layout mode.");
    if (columns == null || rows == null) throw new IllegalArgumentException(where + ": missing tracks.");
    if (columnGapMm < 0 || rowGapMm < 0 || !Double.isFinite(columnGapMm) || !Double.isFinite(rowGapMm))
      throw new IllegalArgumentException(where + ": gaps must be zero or positive millimetres.");
    if (alignX == null || alignY == null) throw new IllegalArgumentException(where + ": missing alignment.");
    for (int i = 0; i < rows.size(); i++) rows.get(i).validate(where + " row " + (i + 1));
    for (int i = 0; i < columns.size(); i++) columns.get(i).validate(where + " column " + (i + 1));
  }
}

package org.microscopy.panel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Two passes, measure then arrange, producing one immutable {@link LayoutResult}.
 *
 * <p>Sizes resolve in a fixed order rather than through a general dependency graph: literals and
 * percentages, then Auto from content, then fractions from what is left, then Same as and aspect
 * ratios, then the min/max clamps. Because Same as may only name a sibling, that order always
 * terminates.
 */
public final class LayoutEngine {
  private static final int CLAMP_PASSES = 3;

  private final ContentMeasurer measurer;

  public LayoutEngine() { this(new DefaultContentMeasurer()); }

  public LayoutEngine(ContentMeasurer measurer) { this.measurer = measurer; }

  public LayoutResult layout(Document document, Page page) {
    page.validate("Page '" + page.name + "'");
    Map<String, RectMm> rectangles = new LinkedHashMap<String, RectMm>();
    Map<String, LayoutResult.Tracks> tracks = new LinkedHashMap<String, LayoutResult.Tracks>();
    List<String> order = new ArrayList<String>();
    List<String> warnings = new ArrayList<String>();

    double availableWidth = page.size.mode == PageSize.Mode.AUTO ? 0
        : page.size.widthMm - page.margins.leftMm - page.margins.rightMm;
    double availableHeight = page.size.mode == PageSize.Mode.FIXED
        ? page.size.heightMm - page.margins.topMm - page.margins.bottomMm : 0;
    if (page.size.mode != PageSize.Mode.AUTO && availableWidth <= 0)
      throw new IllegalArgumentException("Page margins leave no width for content.");
    if (page.size.mode == PageSize.Mode.FIXED && availableHeight <= 0)
      throw new IllegalArgumentException("Page margins leave no height for content.");

    SizeMm measured = measure(document, page.rootNode, availableWidth, availableHeight);
    double contentWidth = availableWidth > 0 ? availableWidth : measured.width;
    double contentHeight = availableHeight > 0 ? availableHeight : measured.height;
    RectMm content = new RectMm(page.margins.leftMm, page.margins.topMm, contentWidth, contentHeight);
    arrange(document, page.rootNode, content, rectangles, tracks, order, warnings);

    RectMm pageBox = new RectMm(0, 0,
        contentWidth + page.margins.leftMm + page.margins.rightMm,
        contentHeight + page.margins.topMm + page.margins.bottomMm);
    String warning = page.size.slideSizeWarning();
    if (!warning.isEmpty()) warnings.add(warning);
    return new LayoutResult(rectangles, tracks, order, warnings, pageBox);
  }

  /** Convenience for the common single page document. */
  public LayoutResult layout(Document document) { return layout(document, document.page(0)); }

  // ---------------------------------------------------------------- measure

  private SizeMm measure(Document document, Node node, double availableWidth, double availableHeight) {
    double padding = paddingMm(node);
    double innerWidth = availableWidth > 0 ? Math.max(0, availableWidth - 2 * padding) : 0;
    double innerHeight = availableHeight > 0 ? Math.max(0, availableHeight - 2 * padding) : 0;
    SizeMm intrinsic = node.children.isEmpty()
        ? measurer.measure(document, node, innerWidth)
        : measureChildren(document, node, innerWidth, innerHeight);
    double width = self(node.size.width, intrinsic.width + 2 * padding, availableWidth);
    double height = self(node.size.height, intrinsic.height + 2 * padding, availableHeight);
    if (node.size.width.kind == SizeExpr.Kind.ASPECT_RATIO && height > 0)
      width = height * node.size.width.value;
    if (node.size.height.kind == SizeExpr.Kind.ASPECT_RATIO && width > 0)
      height = width * node.size.height.value;
    return new SizeMm(node.size.width.clamp(width), node.size.height.clamp(height));
  }

  /** What a node asks for on its own, before its parent hands out leftover space. */
  private static double self(SizeExpr expr, double intrinsic, double available) {
    switch (expr.kind) {
      case FIXED:
        return expr.value;
      case PERCENTAGE:
        return available > 0 ? available * expr.value / 100.0 : intrinsic;
      case FRACTION:
        // Fill takes the whole offered extent. Knowing that here is what lets an Auto row work
        // out how tall an aspect-locked picture will be once its column width is settled.
        return available > 0 ? available : intrinsic;
      default:
        return intrinsic;
    }
  }

  private SizeMm measureChildren(Document document, Node node, double innerWidth, double innerHeight) {
    if (node.layout.mode == LayoutSpec.Mode.STACK) {
      double width = 0, height = 0;
      for (Node child : node.children) {
        SizeMm size = measure(document, child, innerWidth, innerHeight);
        width = Math.max(width, size.width);
        height = Math.max(height, size.height);
      }
      return new SizeMm(width, height);
    }
    if (node.layout.mode == LayoutSpec.Mode.FLOW) {
      double width = 0, height = 0;
      for (int i = 0; i < node.children.size(); i++) {
        SizeMm size = measure(document, node.children.get(i), innerWidth, 0);
        width = Math.max(width, size.width);
        height += size.height + (i > 0 ? node.layout.rowGapMm : 0);
      }
      return new SizeMm(width, height);
    }
    // When the width is already known, share it out before measuring rows. Otherwise a Fill
    // child is offered nothing, falls back to its intrinsic print size, and an aspect ratio
    // turns that into a wildly too tall row.
    double[] columns = innerWidth > 0
        ? resolveTracks(document, node, true, innerWidth, innerHeight, new ArrayList<String>(), null)
        : trackBases(document, node, true, innerWidth, innerHeight, null);
    double[] rows = trackBases(document, node, false, innerWidth, innerHeight, columns);
    return new SizeMm(total(columns, node.layout.columnGapMm), total(rows, node.layout.rowGapMm));
  }

  // ----------------------------------------------------------------- tracks

  static int trackCount(Node node, boolean columns) {
    int declared = columns ? node.layout.columns.size() : node.layout.rows.size();
    int used = 0;
    for (Node child : node.children) {
      int end = columns
          ? child.placement.column + child.placement.columnSpan
          : child.placement.row + child.placement.rowSpan;
      used = Math.max(used, end);
    }
    return Math.max(1, Math.max(declared, used));
  }

  static SizeExpr track(Node node, boolean columns, int index) {
    List<SizeExpr> list = columns ? node.layout.columns : node.layout.rows;
    return index < list.size() ? list.get(index) : SizeExpr.auto();
  }

  private static double total(double[] sizes, double gap) {
    double sum = 0;
    for (double size : sizes) sum += size;
    return sum + Math.max(0, sizes.length - 1) * gap;
  }

  /**
   * Base size of every track before leftover space is shared out. A child spanning several
   * tracks contributes an equal share to each Auto track it covers.
   */
  private double[] trackBases(Document document, Node node, boolean columns,
      double innerWidth, double innerHeight, double[] resolvedColumns) {
    int count = trackCount(node, columns);
    double available = columns ? innerWidth : innerHeight;
    double[] bases = new double[count];
    for (int i = 0; i < count; i++) {
      SizeExpr expr = track(node, columns, i);
      if (expr.kind == SizeExpr.Kind.FIXED) bases[i] = expr.value;
      else if (expr.kind == SizeExpr.Kind.PERCENTAGE && available > 0)
        bases[i] = available * expr.value / 100.0;
    }
    for (Node child : node.children) {
      // Rows are measured against the width the child will actually get, so an aspect ratio is
      // accounted for instead of being discovered after the row height is fixed.
      double offeredWidth = columns ? 0
          : resolvedColumns != null
              ? span(resolvedColumns, child.placement.column, child.placement.columnSpan,
                  node.layout.columnGapMm)
              : innerWidth;
      SizeMm size = measure(document, child, offeredWidth, columns ? innerHeight : 0);
      int start = columns ? child.placement.column : child.placement.row;
      int span = columns ? child.placement.columnSpan : child.placement.rowSpan;
      double share = (columns ? size.width : size.height) / span;
      for (int i = start; i < Math.min(count, start + span); i++)
        if (track(node, columns, i).kind == SizeExpr.Kind.AUTO) bases[i] = Math.max(bases[i], share);
    }
    return bases;
  }

  /**
   * Shares out whatever the tracks did not claim, then honours each track's clamps. A track that
   * a clamp moved is frozen so the space it gave up is redistributed instead of oscillating.
   * With no fraction track present, Stretch spreads the remainder across the Auto tracks, which
   * is what makes a plain one-cell container fill its parent.
   */
  private double[] resolveTracks(Document document, Node node, boolean columns,
      double innerWidth, double innerHeight, List<String> warnings, double[] resolvedColumns) {
    int count = trackCount(node, columns);
    double available = columns ? innerWidth : innerHeight;
    double gap = columns ? node.layout.columnGapMm : node.layout.rowGapMm;
    Align align = columns ? node.layout.alignX : node.layout.alignY;
    double[] sizes = trackBases(document, node, columns, innerWidth, innerHeight, resolvedColumns);
    boolean[] frozen = new boolean[count];
    double gaps = Math.max(0, count - 1) * gap;
    for (int pass = 0; pass < CLAMP_PASSES; pass++) {
      double claimed = 0, weight = 0;
      for (int i = 0; i < count; i++) {
        SizeExpr expr = track(node, columns, i);
        if (expr.kind == SizeExpr.Kind.FRACTION && !frozen[i]) weight += expr.value;
        else claimed += sizes[i];
      }
      double leftover = available - gaps - claimed;
      if (weight > 0) {
        for (int i = 0; i < count; i++) {
          SizeExpr expr = track(node, columns, i);
          if (expr.kind == SizeExpr.Kind.FRACTION && !frozen[i])
            sizes[i] = Math.max(0, leftover) * expr.value / weight;
        }
      } else if (leftover > 1e-9 && align == Align.STRETCH) {
        int stretchable = 0;
        for (int i = 0; i < count; i++)
          if (track(node, columns, i).kind == SizeExpr.Kind.AUTO && !frozen[i]) stretchable++;
        if (stretchable > 0) {
          double share = leftover / stretchable;
          for (int i = 0; i < count; i++)
            if (track(node, columns, i).kind == SizeExpr.Kind.AUTO && !frozen[i]) sizes[i] += share;
        }
      }
      boolean clamped = false;
      for (int i = 0; i < count; i++) {
        double before = sizes[i];
        sizes[i] = track(node, columns, i).clamp(before);
        if (Math.abs(sizes[i] - before) > 1e-9) {
          clamped = true;
          frozen[i] = true;
        }
      }
      if (!clamped) return reportOverflow(node, columns, sizes, available, gap, warnings);
      if (pass == CLAMP_PASSES - 1)
        warnings.add(label(node) + ": track sizes did not settle within " + CLAMP_PASSES
            + " passes; the last values were kept.");
    }
    return reportOverflow(node, columns, sizes, available, gap, warnings);
  }

  /**
   * Over-constraint is reported rather than absorbed. A fixed page, a wide fraction column and an
   * aspect-locked picture can easily ask for more room than the page has, and the honest answer
   * is to say so instead of quietly distorting or cropping the figure.
   */
  private static double[] reportOverflow(Node node, boolean columns, double[] sizes,
      double available, double gap, List<String> warnings) {
    if (available <= 0) return sizes;
    double needed = total(sizes, gap);
    if (needed <= available + 0.01) return sizes;
    warnings.add(String.format("%s: the %s need %.0f mm but only %.0f mm is available;"
            + " reduce a track, lower the page size or drop an aspect ratio.",
        label(node), columns ? "columns" : "rows", needed, available));
    return sizes;
  }

  private static double paddingMm(Node node) {
    PropertyValue value = node.appearance.overrides.get(Prop.PADDING_MM);
    // Token-backed padding resolves once the style tables are wired in; literals work now.
    if (value == null || value.kind != PropertyValue.Kind.LITERAL) return 0;
    double padding = value.asDouble();
    return padding > 0 ? padding : 0;
  }

  private static String label(Node node) {
    return node.name == null || node.name.isEmpty() ? node.id : node.name;
  }

  // ---------------------------------------------------------------- arrange

  private void arrange(Document document, Node node, RectMm rect, Map<String, RectMm> out,
      Map<String, LayoutResult.Tracks> tracks, List<String> order, List<String> warnings) {
    out.put(node.id, rect);
    order.add(node.id);
    if (node.children.isEmpty()) return;
    RectMm inner = rect.inset(paddingMm(node));
    List<Node> painted = new ArrayList<Node>(node.children);
    Collections.sort(painted, new Comparator<Node>() {
      public int compare(Node a, Node b) { return Integer.compare(a.placement.zIndex, b.placement.zIndex); }
    });
    if (node.layout.mode == LayoutSpec.Mode.STACK) {
      for (Node child : painted) {
        SizeMm size = box(document, child, node, inner.width, inner.height);
        arrange(document, child, place(child, inner, size, node, warnings), out, tracks, order, warnings);
      }
      return;
    }
    if (node.layout.mode == LayoutSpec.Mode.FLOW) {
      double y = inner.y;
      for (int i = 0; i < painted.size(); i++) {
        Node child = painted.get(i);
        if (i > 0) y += node.layout.rowGapMm;
        SizeMm size = box(document, child, node, inner.width, 0);
        RectMm cell = new RectMm(inner.x, y, inner.width, size.height);
        arrange(document, child, place(child, cell, size, node, warnings), out, tracks, order, warnings);
        y += size.height;
      }
      return;
    }
    double[] columns = resolveTracks(document, node, true, inner.width, inner.height, warnings, null);
    double[] rows = resolveTracks(document, node, false, inner.width, inner.height, warnings, columns);
    double[] x = offsets(columns, node.layout.columnGapMm, inner.x, inner.width, node.layout.alignX);
    double[] y = offsets(rows, node.layout.rowGapMm, inner.y, inner.height, node.layout.alignY);
    tracks.put(node.id, new LayoutResult.Tracks(x, columns, y, rows,
        node.layout.columnGapMm, node.layout.rowGapMm));
    Map<String, SizeMm> resolved = resolveChildren(document, node, painted, columns, rows, warnings);
    for (Node child : painted) {
      RectMm cell = cell(child, columns, rows, x, y, node.layout);
      arrange(document, child, place(child, cell, resolved.get(child.id), node, warnings),
          out, tracks, order, warnings);
    }
  }

  private static double[] offsets(double[] sizes, double gap, double start, double available, Align align) {
    double used = total(sizes, gap);
    double slack = Math.max(0, available - used);
    double origin = start;
    if (align == Align.CENTER) origin += slack / 2;
    else if (align == Align.END) origin += slack;
    double[] offsets = new double[sizes.length];
    double cursor = origin;
    for (int i = 0; i < sizes.length; i++) {
      offsets[i] = cursor;
      cursor += sizes[i] + gap;
    }
    return offsets;
  }

  private static RectMm cell(Node child, double[] columns, double[] rows, double[] x, double[] y,
      LayoutSpec layout) {
    int column = Math.min(child.placement.column, columns.length - 1);
    int row = Math.min(child.placement.row, rows.length - 1);
    int columnSpan = Math.min(child.placement.columnSpan, columns.length - column);
    int rowSpan = Math.min(child.placement.rowSpan, rows.length - row);
    double width = (columnSpan - 1) * layout.columnGapMm, height = (rowSpan - 1) * layout.rowGapMm;
    for (int i = column; i < column + columnSpan; i++) width += columns[i];
    for (int i = row; i < row + rowSpan; i++) height += rows[i];
    return new RectMm(x[column], y[row], width, height);
  }

  /** Align a child inside the cell it was given, once its own size is known. */
  private static RectMm place(Node child, RectMm cell, SizeMm size, Node parent, List<String> warnings) {
    double width = Math.min(size.width, cell.width), height = Math.min(size.height, cell.height);
    if (size.width - cell.width > 1e-6 || size.height - cell.height > 1e-6)
      warnings.add(label(child) + ": content is larger than the space available in "
          + label(parent) + "; it was clipped to the cell.");
    double x = cell.x, y = cell.y;
    // Stretch means "fill the cell"; a child that cannot, because an aspect ratio pins it, is
    // centred rather than left against the top left corner.
    if (parent.layout.alignX == Align.CENTER || parent.layout.alignX == Align.STRETCH)
      x += (cell.width - width) / 2;
    else if (parent.layout.alignX == Align.END) x += cell.width - width;
    if (parent.layout.alignY == Align.CENTER || parent.layout.alignY == Align.STRETCH)
      y += (cell.height - height) / 2;
    else if (parent.layout.alignY == Align.END) y += cell.height - height;
    return new RectMm(x, y, width, height);
  }

  // ------------------------------------------------------- child size order

  /**
   * Resolves every child's size inside its grid cell in the fixed order: literals, percentages,
   * fractions and Auto first, then Same as, then aspect ratios, then the clamps.
   */
  private Map<String, SizeMm> resolveChildren(Document document, Node node, List<Node> children,
      double[] columns, double[] rows, List<String> warnings) {
    int n = children.size();
    double[] width = new double[n], height = new double[n];
    boolean[] haveWidth = new boolean[n], haveHeight = new boolean[n];
    Map<String, Integer> index = new HashMap<String, Integer>();
    for (int i = 0; i < n; i++) index.put(children.get(i).id, i);

    for (int i = 0; i < n; i++) {
      Node child = children.get(i);
      double cellWidth = span(columns, child.placement.column, child.placement.columnSpan,
          node.layout.columnGapMm);
      double cellHeight = span(rows, child.placement.row, child.placement.rowSpan,
          node.layout.rowGapMm);
      SizeMm intrinsic = measure(document, child, cellWidth, cellHeight);
      width[i] = axis(child.size.width, cellWidth, intrinsic.width, node.layout.alignX);
      height[i] = axis(child.size.height, cellHeight, intrinsic.height, node.layout.alignY);
      haveWidth[i] = child.size.width.kind != SizeExpr.Kind.SAME_AS
          && child.size.width.kind != SizeExpr.Kind.ASPECT_RATIO;
      haveHeight[i] = child.size.height.kind != SizeExpr.Kind.SAME_AS
          && child.size.height.kind != SizeExpr.Kind.ASPECT_RATIO;
    }
    propagateSameAs(children, index, width, haveWidth, true);
    propagateSameAs(children, index, height, haveHeight, false);
    for (int i = 0; i < n; i++) {
      Node child = children.get(i);
      if (!haveWidth[i] && child.size.width.kind == SizeExpr.Kind.ASPECT_RATIO && haveHeight[i]) {
        width[i] = height[i] * child.size.width.value;
        haveWidth[i] = true;
      }
      if (!haveHeight[i] && child.size.height.kind == SizeExpr.Kind.ASPECT_RATIO && haveWidth[i]) {
        height[i] = width[i] * child.size.height.value;
        haveHeight[i] = true;
      }
    }
    Map<String, SizeMm> resolved = new LinkedHashMap<String, SizeMm>();
    for (int i = 0; i < n; i++) {
      Node child = children.get(i);
      if (!haveWidth[i] || !haveHeight[i])
        warnings.add(label(child) + ": size could not be resolved; the measured size was used.");
      resolved.put(child.id, new SizeMm(
          child.size.width.clamp(width[i]), child.size.height.clamp(height[i])));
    }
    return resolved;
  }

  private static double axis(SizeExpr expr, double cell, double intrinsic, Align align) {
    switch (expr.kind) {
      case FIXED:
        return expr.value;
      case PERCENTAGE:
        return cell * expr.value / 100.0;
      case FRACTION:
        return cell;
      case AUTO:
        // Stretch is what makes an Auto child fill its cell, as in a CSS grid.
        return align == Align.STRETCH ? cell : intrinsic;
      default:
        return intrinsic;
    }
  }

  private static void propagateSameAs(List<Node> children, Map<String, Integer> index,
      double[] sizes, boolean[] known, boolean widthAxis) {
    boolean progress = true;
    while (progress) {
      progress = false;
      for (int i = 0; i < children.size(); i++) {
        if (known[i]) continue;
        SizeExpr expr = widthAxis ? children.get(i).size.width : children.get(i).size.height;
        if (expr.kind != SizeExpr.Kind.SAME_AS) continue;
        Integer target = index.get(expr.siblingId);
        if (target == null || !known[target]) continue;
        sizes[i] = sizes[target];
        known[i] = true;
        progress = true;
      }
    }
    for (int i = 0; i < children.size(); i++) {
      SizeExpr expr = widthAxis ? children.get(i).size.width : children.get(i).size.height;
      if (!known[i] && expr.kind == SizeExpr.Kind.SAME_AS)
        throw new IllegalArgumentException("Same as forms a cycle around "
            + label(children.get(i)) + "; break the chain before laying out.");
    }
  }

  private static double span(double[] sizes, int start, int spanCount, double gap) {
    int from = Math.min(start, sizes.length - 1);
    int count = Math.min(spanCount, sizes.length - from);
    double size = (count - 1) * gap;
    for (int i = from; i < from + count; i++) size += sizes[i];
    return size;
  }

  /** A child's own size against a container box rather than a grid cell. */
  private SizeMm box(Document document, Node child, Node parent, double width, double height) {
    SizeMm intrinsic = measure(document, child, width, height);
    double w = axis(child.size.width, width, intrinsic.width, parent.layout.alignX);
    double h;
    if (height > 0) h = axis(child.size.height, height, intrinsic.height, parent.layout.alignY);
    else if (child.size.height.kind == SizeExpr.Kind.FIXED) h = child.size.height.value;
    else h = intrinsic.height;
    return new SizeMm(child.size.width.clamp(w), child.size.height.clamp(h));
  }
}

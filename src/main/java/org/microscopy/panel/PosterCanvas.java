package org.microscopy.panel;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The page as it will print, with direct manipulation on top. Every coordinate comes from the
 * shared {@link LayoutResult}, so what is clicked, what is dragged and what is exported cannot
 * drift apart.
 */
public class PosterCanvas extends JPanel {
  public interface Listener {
    void selected(String nodeId);

    void enterChild(String nodeId);

    /** The canvas changed the document; the frame re-lays out and repaints. */
    void documentChanged();

    void contextMenu(String nodeId, int x, int y);
  }

  /** A grid line between two tracks of one container. */
  private static final class Boundary {
    final Node parent;
    final boolean columns;
    final int before;
    final LayoutResult.Tracks tracks;

    Boundary(Node parent, boolean columns, int before, LayoutResult.Tracks tracks) {
      this.parent = parent; this.columns = columns; this.before = before; this.tracks = tracks;
    }
  }

  private static final Color SELECTION = new Color(30, 170, 215);
  private static final Color PARENT_HINT = new Color(30, 170, 215, 70);
  private static final Color DROP = new Color(255, 200, 65);
  private static final double GRAB_MM = 1.6;

  private final Listener listener;
  private final SourceProvider sources;
  private Document document;
  private Page page;
  private LayoutResult layout;
  private BufferedImage rendered;
  private double zoom = 1;
  private String selectedId;
  private Node dragNode;
  private Boundary dragBoundary;
  private double boundaryMm;
  private Point pressPoint, panPoint;
  private int dropRow = -1, dropColumn = -1;
  private boolean dragging;

  public PosterCanvas(SourceProvider sources, Listener listener) {
    this.sources = sources;
    this.listener = listener;
    setBackground(new Color(60, 60, 60));
    setFocusable(true);
    Mouse mouse = new Mouse();
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
    addMouseWheelListener(mouse);
  }

  public void show(Document document, Page page, LayoutResult layout) {
    this.document = document;
    this.page = page;
    this.layout = layout;
    this.rendered = null;
    resetDrag();
    revalidate();
    repaint();
  }

  public void setZoom(double zoom) {
    this.zoom = Math.max(0.02, Math.min(4, zoom));
    rendered = null;
    revalidate();
    repaint();
  }

  public double zoom() { return zoom; }

  /** Zoom that makes the whole page fit the viewport. */
  public double fitZoom(Dimension viewport) {
    if (layout == null) return 1;
    RectMm box = layout.pageBox();
    double screenDpi = 96;
    double wide = viewport.width / (box.width / Units.MM_PER_INCH * screenDpi);
    double tall = viewport.height / (box.height / Units.MM_PER_INCH * screenDpi);
    return Math.max(0.02, Math.min(wide, tall) * 0.96);
  }

  public void select(String nodeId) {
    selectedId = nodeId;
    repaint();
  }

  private double dpi() { return 96 * zoom; }

  private double toMm(double px) { return Units.pxToMm(px, dpi()); }

  private double toPx(double mm) { return mm / Units.MM_PER_INCH * dpi(); }

  private Point origin() {
    if (layout == null) return new Point(0, 0);
    int width = Units.mmToPx(layout.pageBox().width, dpi());
    int height = Units.mmToPx(layout.pageBox().height, dpi());
    return new Point(Math.max(0, (getWidth() - width) / 2), Math.max(0, (getHeight() - height) / 2));
  }

  @Override
  public Dimension getPreferredSize() {
    if (layout == null) return new Dimension(900, 600);
    RectMm box = layout.pageBox();
    return new Dimension(Units.mmToPx(box.width, dpi()) + 40, Units.mmToPx(box.height, dpi()) + 40);
  }

  private void resetDrag() {
    dragNode = null;
    dragBoundary = null;
    pressPoint = null;
    panPoint = null;
    dragging = false;
    dropRow = -1;
    dropColumn = -1;
    setCursor(Cursor.getDefaultCursor());
  }

  // ------------------------------------------------------------------ gestures

  /** The deepest grid line within grabbing distance of the point, or null. */
  private Boundary boundaryAt(double xMm, double yMm) {
    if (layout == null) return null;
    Boundary found = null;
    for (Node node : DocumentEdits.flatten(page.rootNode)) {
      LayoutResult.Tracks tracks = layout.tracksOf(node.id);
      if (tracks == null || !layout.has(node.id)) continue;
      RectMm rect = layout.of(node.id);
      if (!rect.contains(xMm, yMm)) continue;
      for (int axis = 0; axis < 2; axis++) {
        boolean columns = axis == 0;
        double[] starts = tracks.starts(columns), sizes = tracks.sizes(columns);
        double along = columns ? xMm : yMm;
        for (int i = 0; i + 1 < sizes.length; i++) {
          double line = starts[i] + sizes[i] + tracks.gap(columns) / 2;
          if (Math.abs(along - line) <= GRAB_MM) found = new Boundary(node, columns, i, tracks);
        }
      }
    }
    return found;
  }

  private class Mouse extends MouseAdapter {
    @Override
    public void mouseMoved(MouseEvent event) {
      if (layout == null) return;
      Boundary boundary = boundaryAt(mmX(event), mmY(event));
      setCursor(boundary == null ? Cursor.getDefaultCursor()
          : Cursor.getPredefinedCursor(boundary.columns
              ? Cursor.E_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR));
    }

    @Override
    public void mousePressed(MouseEvent event) {
      if (layout == null) return;
      requestFocusInWindow();
      if (event.isPopupTrigger() || SwingUtilities.isRightMouseButton(event)) {
        String hit = layout.hit(page.rootNode, mmX(event), mmY(event));
        listener.contextMenu(hit, event.getX(), event.getY());
        return;
      }
      pressPoint = event.getPoint();
      if (event.isShiftDown()) {
        panPoint = event.getPoint();
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        return;
      }
      dragBoundary = boundaryAt(mmX(event), mmY(event));
      if (dragBoundary != null) {
        boundaryMm = dragBoundary.columns ? mmX(event) : mmY(event);
        return;
      }
      String hit = layout.hit(page.rootNode, mmX(event), mmY(event));
      dragNode = hit == null ? null : page.rootNode.find(hit);
      if (dragNode != null && page.rootNode.parentOf(dragNode.id) == null) dragNode = null;
    }

    @Override
    public void mouseDragged(MouseEvent event) {
      if (panPoint != null) {
        pan(event);
        return;
      }
      if (pressPoint != null && pressPoint.distance(event.getPoint()) > 5) dragging = true;
      if (!dragging) return;
      if (dragBoundary != null) {
        boundaryMm = dragBoundary.columns ? mmX(event) : mmY(event);
        repaint();
        return;
      }
      if (dragNode == null) return;
      setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
      Node parent = page.rootNode.parentOf(dragNode.id);
      LayoutResult.Tracks tracks = parent == null ? null : layout.tracksOf(parent.id);
      if (tracks == null) return;
      dropColumn = trackAt(tracks, true, mmX(event));
      dropRow = trackAt(tracks, false, mmY(event));
      repaint();
    }

    @Override
    public void mouseReleased(MouseEvent event) {
      if (panPoint != null) {
        resetDrag();
        return;
      }
      boolean acted = false;
      if (dragging && dragBoundary != null) acted = applyBoundary();
      else if (dragging && dragNode != null && dropRow >= 0 && dropColumn >= 0) {
        if (dropRow != dragNode.placement.row || dropColumn != dragNode.placement.column) {
          DocumentEdits.moveTo(page, dragNode.id, dropRow, dropColumn);
          acted = true;
        }
      }
      String moved = dragNode == null ? null : dragNode.id;
      resetDrag();
      if (acted) {
        listener.documentChanged();
        if (moved != null) listener.selected(moved);
      }
    }

    @Override
    public void mouseClicked(MouseEvent event) {
      if (layout == null || SwingUtilities.isRightMouseButton(event)) return;
      String hit = layout.hit(page.rootNode, mmX(event), mmY(event));
      if (hit == null) return;
      if (event.getClickCount() >= 2) listener.enterChild(hit);
      else listener.selected(hit);
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent event) {
      if (!event.isControlDown()) {
        getParent().dispatchEvent(SwingUtilities.convertMouseEvent(PosterCanvas.this, event,
            getParent()));
        return;
      }
      setZoom(zoom * (event.getWheelRotation() < 0 ? 1.1 : 1 / 1.1));
    }

    private void pan(MouseEvent event) {
      java.awt.Container parent = getParent();
      if (!(parent instanceof javax.swing.JViewport)) return;
      javax.swing.JViewport viewport = (javax.swing.JViewport) parent;
      Point at = viewport.getViewPosition();
      at.translate(panPoint.x - event.getX(), panPoint.y - event.getY());
      at.x = Math.max(0, Math.min(Math.max(0, getWidth() - viewport.getWidth()), at.x));
      at.y = Math.max(0, Math.min(Math.max(0, getHeight() - viewport.getHeight()), at.y));
      viewport.setViewPosition(at);
    }

    private double mmX(MouseEvent event) { return toMm(event.getX() - origin().x); }

    private double mmY(MouseEvent event) { return toMm(event.getY() - origin().y); }
  }

  private boolean applyBoundary() {
    LayoutResult.Tracks tracks = dragBoundary.tracks;
    boolean columns = dragBoundary.columns;
    int before = dragBoundary.before;
    double[] starts = tracks.starts(columns), sizes = tracks.sizes(columns);
    double from = starts[before];
    double to = starts[before + 1] + sizes[before + 1];
    double span = to - from - tracks.gap(columns);
    double first = Math.max(0, Math.min(span, boundaryMm - tracks.gap(columns) / 2 - from));
    DocumentEdits.setBoundary(dragBoundary.parent, columns, before, first, span - first);
    return true;
  }

  private static int trackAt(LayoutResult.Tracks tracks, boolean columns, double mm) {
    double[] starts = tracks.starts(columns), sizes = tracks.sizes(columns);
    for (int i = 0; i < sizes.length; i++)
      if (mm < starts[i] + sizes[i] + tracks.gap(columns) / 2) return i;
    return sizes.length - 1;
  }

  // ------------------------------------------------------------------ painting

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    if (layout == null) {
      graphics.setColor(Color.LIGHT_GRAY);
      graphics.drawString("Add images, import a figure settings file, or open a project PPTX.",
          24, getHeight() / 2);
      return;
    }
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      Point origin = origin();
      if (rendered == null) rendered = renderPage();
      g.drawImage(rendered, origin.x, origin.y, null);
      g.translate(origin.x, origin.y);
      g.setStroke(new BasicStroke(1.5f));
      if (selectedId != null && layout.has(selectedId)) {
        Node parent = page.rootNode.parentOf(selectedId);
        if (parent != null && layout.has(parent.id)) {
          g.setColor(PARENT_HINT);
          outline(g, layout.of(parent.id));
        }
        g.setColor(SELECTION);
        outline(g, layout.of(selectedId));
      }
      if (dragging && dragBoundary != null) paintBoundary(g);
      if (dragging && dragNode != null && dropRow >= 0) paintDropCell(g);
    } finally {
      g.dispose();
    }
  }

  private void paintBoundary(Graphics2D g) {
    RectMm rect = layout.of(dragBoundary.parent.id);
    g.setColor(DROP);
    g.setStroke(new BasicStroke(2f));
    if (dragBoundary.columns) {
      int x = (int) Math.round(toPx(boundaryMm));
      g.drawLine(x, (int) Math.round(toPx(rect.y)), x, (int) Math.round(toPx(rect.bottom())));
    } else {
      int y = (int) Math.round(toPx(boundaryMm));
      g.drawLine((int) Math.round(toPx(rect.x)), y, (int) Math.round(toPx(rect.right())), y);
    }
  }

  private void paintDropCell(Graphics2D g) {
    Node parent = page.rootNode.parentOf(dragNode.id);
    LayoutResult.Tracks tracks = parent == null ? null : layout.tracksOf(parent.id);
    if (tracks == null) return;
    int column = Math.min(dropColumn, tracks.columns() - 1);
    int row = Math.min(dropRow, tracks.rows() - 1);
    Rectangle cell = new Rectangle(
        (int) Math.round(toPx(tracks.columnStart[column])),
        (int) Math.round(toPx(tracks.rowStart[row])),
        (int) Math.round(toPx(tracks.columnSize[column])),
        (int) Math.round(toPx(tracks.rowSize[row])));
    g.setColor(new Color(255, 200, 65, 60));
    g.fill(cell);
    g.setColor(DROP);
    g.setStroke(new BasicStroke(2f));
    g.draw(cell);
  }

  private void outline(Graphics2D g, RectMm rect) {
    g.drawRect((int) Math.round(toPx(rect.x)), (int) Math.round(toPx(rect.y)),
        Math.max(1, (int) Math.round(toPx(rect.width))),
        Math.max(1, (int) Math.round(toPx(rect.height))));
  }

  private BufferedImage renderPage() {
    RenderTarget target = new RenderTarget(dpi(), RenderTarget.Background.WHITE);
    try {
      return new DocumentRasterizer(sources).rasterizeWhole(document, page, layout, target);
    } catch (Exception ex) {
      BufferedImage error = new BufferedImage(
          Math.max(1, Units.mmToPx(layout.pageBox().width, dpi())),
          Math.max(1, Units.mmToPx(layout.pageBox().height, dpi())), BufferedImage.TYPE_INT_RGB);
      Graphics2D g = error.createGraphics();
      try {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, error.getWidth(), error.getHeight());
        g.setColor(Color.RED);
        g.drawString("Cannot render: " + ex.getMessage(), 12, 24);
      } finally {
        g.dispose();
      }
      return error;
    }
  }
}

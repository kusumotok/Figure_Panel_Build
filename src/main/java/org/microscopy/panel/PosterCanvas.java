package org.microscopy.panel;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

/**
 * The page as it will print, with selection drawn on top. Every coordinate comes from the shared
 * {@link LayoutResult}, so what is clicked and what is exported can never drift apart.
 */
public class PosterCanvas extends JPanel {
  public interface Listener {
    void selected(String nodeId);

    void enterChild(String nodeId);
  }

  private static final Color SELECTION = new Color(30, 170, 215);
  private static final Color PARENT_HINT = new Color(30, 170, 215, 70);

  private final Listener listener;
  private final SourceProvider sources;
  private Document document;
  private Page page;
  private LayoutResult layout;
  private BufferedImage rendered;
  private double zoom = 1;
  private String selectedId;

  public PosterCanvas(SourceProvider sources, Listener listener) {
    this.sources = sources;
    this.listener = listener;
    setBackground(new Color(60, 60, 60));
    MouseAdapter mouse = new MouseAdapter() {
      public void mouseClicked(MouseEvent event) {
        if (layout == null) return;
        Point origin = origin();
        double xMm = pxToMm(event.getX() - origin.x), yMm = pxToMm(event.getY() - origin.y);
        String hit = layout.hit(page.rootNode, xMm, yMm);
        if (hit == null) return;
        if (event.getClickCount() >= 2) listener.enterChild(hit);
        else listener.selected(hit);
      }
    };
    addMouseListener(mouse);
  }

  public void show(Document document, Page page, LayoutResult layout) {
    this.document = document;
    this.page = page;
    this.layout = layout;
    this.rendered = null;
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

  private double pxToMm(double px) { return Units.pxToMm(px, dpi()); }

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

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    if (layout == null) {
      graphics.setColor(Color.LIGHT_GRAY);
      graphics.drawString("Import a figure settings file or open a project PPTX to begin.",
          24, getHeight() / 2);
      return;
    }
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      Point origin = origin();
      if (rendered == null) rendered = renderPage();
      g.drawImage(rendered, origin.x, origin.y, null);
      g.translate(origin.x, origin.y);
      double scale = dpi() / Units.MM_PER_INCH;
      g.scale(scale, scale);
      g.setStroke(new BasicStroke((float) (1.5 / scale)));
      if (selectedId != null && layout.has(selectedId)) {
        Node parent = page.rootNode.parentOf(selectedId);
        if (parent != null && layout.has(parent.id)) {
          g.setColor(PARENT_HINT);
          outline(g, layout.of(parent.id));
        }
        g.setColor(SELECTION);
        outline(g, layout.of(selectedId));
      }
    } finally {
      g.dispose();
    }
  }

  private static void outline(Graphics2D g, RectMm rect) {
    g.drawRect((int) Math.round(rect.x), (int) Math.round(rect.y),
        Math.max(1, (int) Math.round(rect.width)), Math.max(1, (int) Math.round(rect.height)));
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

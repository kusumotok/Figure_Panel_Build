package org.microscopy.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.io.File;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.microscopy.figure.InputImageManager;

/**
 * The poster window. One canvas, one inspector and the same four inspector sections whatever is
 * selected; the breadcrumb and the Escape/Enter pair are what keep a deep node tree navigable.
 */
public class PosterFrame extends JFrame {
  private final AssetLibrary library = new AssetLibrary();
  private final SourceProvider sources = library;
  private final JPanel breadcrumb = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
  private final JLabel status = new JLabel("Import a figure settings file to begin.");
  private final PosterCanvas canvas;
  private final Inspector inspector;
  private Document document;
  private LayoutResult layout;
  private String selectedId;
  private File projectFile;
  private final JPanel contrastDock = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 4));
  private org.microscopy.figure.ContrastPanel contrast;
  private String contrastNodeId;
  private java.util.Set<String> overflowing = new java.util.LinkedHashSet<String>();
  private final StyleManagerPanel stylePanel;

  public PosterFrame() {
    super("Poster / Layout");
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    canvas = new PosterCanvas(sources, new PosterCanvas.Listener() {
      public void selected(String nodeId) { select(nodeId); }

      public void enterChild(String nodeId) { select(nodeId); }

      public void documentChanged() { attempt(() -> relayout(true)); }

      public void contextMenu(String nodeId, int x, int y) { showMenu(nodeId, x, y); }

      public void overflowChanged(java.util.Set<String> nodeIds) { reportOverflow(nodeIds); }
    });
    inspector = new Inspector(new Inspector.Listener() {
      public void documentChanged() { relayout(true); }
    });
    stylePanel = new StyleManagerPanel(new StyleManagerPanel.Listener() {
      public String selectedNodeId() { return selectedId; }

      public void documentChanged() { attempt(() -> relayout(true)); }
    });
    installStyleDrops();

    JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
    button(toolbar, "Add images...", "addImages", () -> addImagesFromContainer());
    button(toolbar, "Import figure settings...", "importSettings", () -> importSettings());
    button(toolbar, "Open project PPTX...", "openProject", () -> openProject());
    button(toolbar, "Save project", "saveProject", () -> saveProject(false));
    button(toolbar, "Save as...", "saveProjectAs", () -> saveProject(true));
    button(toolbar, "Export PNG...", "exportPng", () -> exportPng());
    toolbar.add(Box.createHorizontalStrut(12));
    button(toolbar, "Fit", "zoomFit", () -> zoomFit());
    button(toolbar, "-", "zoomOut", () -> canvas.setZoom(canvas.zoom() / 1.25));
    button(toolbar, "+", "zoomIn", () -> canvas.setZoom(canvas.zoom() * 1.25));

    breadcrumb.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
    JPanel top = new JPanel(new BorderLayout());
    top.add(toolbar, BorderLayout.NORTH);
    top.add(breadcrumb, BorderLayout.SOUTH);

    JScrollPane scroll = new JScrollPane(canvas);
    scroll.getVerticalScrollBar().setUnitIncrement(24);
    status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

    setLayout(new BorderLayout());
    add(top, BorderLayout.NORTH);
    add(scroll, BorderLayout.CENTER);
    add(stylePanel, BorderLayout.WEST);
    add(new JScrollPane(inspector), BorderLayout.EAST);
    contrastDock.setVisible(false);
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(contrastDock, BorderLayout.CENTER);
    bottom.add(status, BorderLayout.SOUTH);
    add(bottom, BorderLayout.SOUTH);
    installKeys();

    Rectangle screen = getGraphicsConfiguration().getBounds();
    setSize(Math.min(1500, screen.width - 80), Math.min(950, screen.height - 80));
    setLocationRelativeTo(null);
  }

  private void button(JPanel parent, String title, String name, Runnable action) {
    JButton button = new JButton(title);
    button.setName(name);
    button.setFocusPainted(false);
    button.addActionListener(event -> attempt(action));
    parent.add(button);
  }

  private void attempt(Runnable action) {
    try {
      action.run();
    } catch (Exception ex) {
      String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
      JOptionPane.showMessageDialog(this, message, "Poster / Layout", JOptionPane.WARNING_MESSAGE);
      status.setText(message);
    }
  }

  /** Escape selects the parent and Enter steps into the first child. */
  private void installKeys() {
    JComponent root = getRootPane();
    root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke("ESCAPE"), "selectParent");
    root.getActionMap().put("selectParent", new javax.swing.AbstractAction() {
      public void actionPerformed(java.awt.event.ActionEvent event) { selectParent(); }
    });
    root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke("ENTER"), "selectChild");
    root.getActionMap().put("selectChild", new javax.swing.AbstractAction() {
      public void actionPerformed(java.awt.event.ActionEvent event) { selectFirstChild(); }
    });
    root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke("TAB"), "selectSibling");
    root.getActionMap().put("selectSibling", new javax.swing.AbstractAction() {
      public void actionPerformed(java.awt.event.ActionEvent event) { selectNextSibling(); }
    });
    root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke("DELETE"), "deleteNode");
    root.getActionMap().put("deleteNode", new javax.swing.AbstractAction() {
      public void actionPerformed(java.awt.event.ActionEvent event) {
        attempt(() -> deleteSelected());
      }
    });
  }

  // ----------------------------------------------------------------- actions

  public void importSettings() {
    File file = choose("Import figure settings", "json", "Figure settings (*.json)", false);
    if (file == null) return;
    LegacyImporter.Imported imported;
    try {
      imported = new LegacyImporter().load(file);
    } catch (java.io.IOException ex) {
      throw new IllegalArgumentException("Cannot read " + file.getName() + ": " + ex.getMessage(), ex);
    }
    library.adopt(imported.inputs);
    library.use(imported.document);
    document = imported.document;
    projectFile = null;
    selectedId = document.page(0).rootNode.id;
    relayout(true);
    status.setText(String.format(
        "Imported %s at %.0f mm wide (%.0f dpi). Set the page size or switch tracks to Fill to"
            + " rearrange it.",
        file.getName(), document.page(0).size.widthMm, imported.effectiveDpi()));
  }

  public void openProject() {
    File file = choose("Open project", "pptx", "Project PowerPoint (*.pptx)", false);
    if (file == null) return;
    PptxProjectReader.Opened opened;
    try {
      opened = new PptxProjectReader().open(file);
    } catch (java.io.IOException ex) {
      throw new IllegalArgumentException("Cannot read " + file.getName() + ": " + ex.getMessage(), ex);
    }
    if (!opened.editable()) throw new IllegalArgumentException(opened.message);
    document = opened.document;
    projectFile = file;
    library.use(document);
    relinkMissing();
    selectedId = document.page(0).rootNode.id;
    relayout(true);
    status.setText(opened.message);
  }

  /**
   * A linked project stores paths only. Nothing is read here; the library loads each source the
   * first time it is drawn. What this does check is that the files are still where they were, so
   * the user is told at open time rather than by a blank panel.
   */
  private void relinkMissing() {
    List<Asset> missing = library.missing();
    if (missing.isEmpty()) return;
    StringBuilder names = new StringBuilder();
    for (Asset asset : missing) {
      if (names.length() > 0) names.append(", ");
      names.append(asset.originalUri == null ? asset.id : new File(asset.originalUri).getName());
    }
    int answer = JOptionPane.showConfirmDialog(this,
        missing.size() + " source image(s) could not be found:\n" + names
            + "\n\nLocate them now?",
        "Relink sources", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
    if (answer != JOptionPane.YES_OPTION) {
      status.setText("Opened with " + missing.size() + " missing source(s); those panels cannot"
          + " be drawn until they are relinked.");
      return;
    }
    for (Asset asset : missing) {
      File located = choose("Locate " + (asset.originalUri == null ? asset.id
          : new File(asset.originalUri).getName()), "tif", "TIFF image (*.tif, *.tiff)", false);
      if (located == null) break;
      library.relink(asset.id, located);
    }
  }

  public void saveProject(boolean chooseFile) {
    require();
    File file = projectFile;
    if (chooseFile || file == null)
      file = choose("Save project", "pptx", "Project PowerPoint (*.pptx)", true);
    if (file == null) return;
    PptxProjectWriter.Saved saved;
    try {
      saved = new PptxProjectWriter().save(file, document, sources, new PptxProjectWriter.Options());
    } catch (java.io.IOException ex) {
      throw new IllegalArgumentException("Cannot write " + file.getName() + ": " + ex.getMessage(), ex);
    }
    projectFile = file;
    status.setText(String.format(
        "Saved %s as a project (%.1f MB, pictures at preview quality). Use Export for printing.",
        file.getName(), saved.bytes / 1e6));
  }

  public void exportPng() {
    require();
    File file = choose("Export PNG", "png", "PNG image (*.png)", true);
    if (file == null) return;
    String answer = (String) JOptionPane.showInputDialog(this,
        "Target resolution in dpi:\n" + resolution().summary(), "Export PNG",
        JOptionPane.QUESTION_MESSAGE, null, null, String.valueOf((int) RenderTarget.PRINT_DPI));
    if (answer == null) return;
    double dpi = Double.parseDouble(answer.trim());
    RenderTarget target = new RenderTarget(dpi, RenderTarget.Background.WHITE);
    Page page = document.page(0);
    try {
      new DocumentRasterizer(sources).writePng(file, document, page, layout, target);
    } catch (java.io.IOException ex) {
      throw new IllegalArgumentException("Cannot write " + file.getName() + ": " + ex.getMessage(), ex);
    }
    int[] size = new DocumentRasterizer(sources).sizePx(layout, target);
    status.setText(String.format("Exported %s at %.0f dpi (%d x %d px). %s",
        file.getName(), dpi, size[0], size[1], resolution().summary()));
  }

  private void require() {
    if (document == null) throw new IllegalArgumentException("Open or import a document first.");
  }

  private ResolutionReport resolution() {
    return ResolutionReport.of(document, document.page(0), layout);
  }

  private File choose(String title, String extension, String description, boolean save) {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(title);
    chooser.setFileFilter(new FileNameExtensionFilter(description, extension));
    if (projectFile != null) chooser.setCurrentDirectory(projectFile.getParentFile());
    int answer = save ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
    if (answer != JFileChooser.APPROVE_OPTION) return null;
    File file = chooser.getSelectedFile();
    if (save && !file.getName().toLowerCase(java.util.Locale.ROOT).endsWith("." + extension))
      file = new File(file.getParentFile(), file.getName() + "." + extension);
    return file;
  }

  // -------------------------------------------------------------- navigation

  private void select(String nodeId) {
    selectedId = nodeId;
    canvas.select(nodeId);
    Node node = nodeId == null ? null : document.page(0).rootNode.find(nodeId);
    inspector.show(document, document.page(0), node);
    updateBreadcrumb();
    showContrastFor(node);
  }

  /**
   * The figure mode's contrast panel, reused as-is. It edits the same ChannelConfig objects the
   * renderer reads, so B and C, LUT and inversion all work without a second implementation.
   */
  private void showContrastFor(Node node) {
    boolean image = node != null && node.content.kind == Content.Kind.SCIENTIFIC_IMAGE;
    String wanted = image ? node.id : null;
    // Rebuilding while a slider is being dragged would pull it out from under the pointer, so
    // the panel is only replaced when the selection actually moves.
    if (java.util.Objects.equals(wanted, contrastNodeId)) return;
    contrastNodeId = wanted;
    if (contrast != null) contrast.stop();
    contrastDock.removeAll();
    contrast = null;
    contrastDock.setVisible(image);
    if (image) {
      ScientificImageContent content = node.content.scientificImage;
      try {
        contrast = new org.microscopy.figure.ContrastPanel(content.figureConfig,
            library.managerFor(content.assetId, content.z, content.t),
            () -> relayout(true), true);
        // Each node carries its own configuration here, so the wording that says the settings
        // are shared across conditions would be wrong.
        contrast.setIndividual();
        contrastDock.add(contrast);
      } catch (RuntimeException ex) {
        contrastDock.add(new JLabel("Cannot show B&C: " + ex.getMessage()));
      }
    }
    contrastDock.revalidate();
    contrastDock.repaint();
  }

  private void selectParent() {
    if (document == null || selectedId == null) return;
    Node parent = document.page(0).rootNode.parentOf(selectedId);
    if (parent != null) select(parent.id);
  }

  private void selectFirstChild() {
    if (document == null || selectedId == null) return;
    Node node = document.page(0).rootNode.find(selectedId);
    if (node != null && !node.children.isEmpty()) select(node.children.get(0).id);
  }

  private void selectNextSibling() {
    if (document == null || selectedId == null) return;
    Node parent = document.page(0).rootNode.parentOf(selectedId);
    if (parent == null) return;
    List<Node> siblings = parent.children;
    for (int i = 0; i < siblings.size(); i++)
      if (siblings.get(i).id.equals(selectedId)) {
        select(siblings.get((i + 1) % siblings.size()).id);
        return;
      }
  }

  private void updateBreadcrumb() {
    breadcrumb.removeAll();
    if (document != null && selectedId != null) {
      List<Node> path = document.page(0).rootNode.pathTo(selectedId);
      for (int i = 0; i < path.size(); i++) {
        final Node step = path.get(i);
        if (i > 0) breadcrumb.add(new JLabel("▸"));
        JButton crumb = new JButton(step.name.isEmpty() ? step.id : step.name);
        crumb.setBorderPainted(false);
        crumb.setContentAreaFilled(false);
        crumb.setFocusPainted(false);
        crumb.setForeground(i == path.size() - 1 ? Color.BLACK : new Color(30, 110, 170));
        crumb.addActionListener(event -> select(step.id));
        breadcrumb.add(crumb);
      }
    }
    breadcrumb.revalidate();
    breadcrumb.repaint();
  }

  private void relayout(boolean keepSelection) {
    if (document == null) return;
    Page page = document.page(0);
    layout = new LayoutEngine().layout(document, page);
    canvas.show(document, page, layout);
    stylePanel.show(document);
    if (!keepSelection || selectedId == null || !layout.has(selectedId))
      selectedId = page.rootNode.id;
    select(selectedId);
    List<String> warnings = layout.warnings();
    ResolutionReport report = resolution();
    StringBuilder text = new StringBuilder();
    text.append(String.format("%.0f x %.0f mm", layout.pageBox().width, layout.pageBox().height));
    text.append("  |  ").append(report.summary());
    if (!warnings.isEmpty()) text.append("  |  ").append(warnings.size()).append(" layout warning")
        .append(warnings.size() == 1 ? "" : "s").append(": ").append(warnings.get(0));
    status.setText(text.toString());
  }

  private void zoomFit() {
    canvas.setZoom(canvas.fitZoom(new Dimension(getWidth() - 360, getHeight() - 200)));
  }

  /** Exposed so a document built in code can be shown, which is what the UI check does. */
  public void show(Document document, InputImageManager sourceImages) {
    library.adopt(sourceImages);
    library.use(document);
    this.document = document;
    this.projectFile = null;
    relayout(false);
    zoomFit();
  }

  public Document document() { return document; }

  public LayoutResult layoutResult() { return layout; }

  public String selectedNodeId() { return selectedId; }

  public String statusText() { return status.getText(); }

  /** Selects a node from code; the canvas, breadcrumb and inspector all follow. */
  public void selectNode(String nodeId) { select(nodeId); }

  public void parentOfSelection() { selectParent(); }

  public void firstChildOfSelection() { selectFirstChild(); }

  public void nextSiblingOfSelection() { selectNextSibling(); }

  /** Saves without a file dialog. Used by the UI check and available for scripting. */
  public PptxProjectWriter.Saved saveProjectTo(File file) throws java.io.IOException {
    require();
    PptxProjectWriter.Saved saved =
        new PptxProjectWriter().save(file, document, sources, new PptxProjectWriter.Options());
    projectFile = file;
    status.setText(String.format("Saved %s as a project (%.1f MB, pictures at preview quality).",
        file.getName(), saved.bytes / 1e6));
    return saved;
  }

  public void exportPngTo(File file, double dpi) throws java.io.IOException {
    require();
    RenderTarget target = new RenderTarget(dpi, RenderTarget.Background.WHITE);
    new DocumentRasterizer(sources).writePng(file, document, document.page(0), layout, target);
  }

  public void openProjectFile(File file) throws java.io.IOException {
    PptxProjectReader.Opened opened = new PptxProjectReader().open(file);
    if (!opened.editable()) throw new IllegalArgumentException(opened.message);
    document = opened.document;
    projectFile = file;
    library.use(document);
    selectedId = document.page(0).rootNode.id;
    relayout(true);
    status.setText(opened.message);
  }

  /**
   * Reads a Bio-Formats container, lets the user choose and group its series, and places the
   * result as a grid of panels under the current selection.
   */
  public void addImagesFromContainer() {
    File file = choose("Add images from a container", "lif",
        "Microscopy container (*.lif, *.nd2, *.oib, *.oir, *.czi, *.tif)", false);
    if (file == null) return;
    if (!BioFormatsReader.available())
      throw new IllegalArgumentException("Bio-Formats is not available. Run this from inside Fiji,"
          + " or convert the file to TIFF first.");
    ContainerImport.Plan plan = ContainerImportDialog.choose(this, file);
    if (plan == null) return;
    addImages(file, plan);
  }

  /** The same import without dialogs, so it can be driven from a script or a check. */
  public void addImages(File file, ContainerImport.Plan plan) {
    if (document == null) startEmptyDocument();
    library.use(document);
    setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
    try {
      List<Asset> placeable = new ContainerImport().apply(document, plan);
      place(placeable);
      status.setText("Added " + placeable.size() + " image(s) from " + file.getName() + ".");
    } finally {
      setCursor(java.awt.Cursor.getDefaultCursor());
    }
    relayout(true);
  }

  private void startEmptyDocument() {
    document = Document.empty();
    Page page = document.page(0);
    page.name = "Page 1";
    page.size = PagePresets.byName(PagePresets.JOURNAL_DOUBLE_COLUMN).toPageSize();
    page.margins = Margins.uniform(5);
    page.rootNode = Node.container("Page", 1, 1);
    projectFile = null;
    selectedId = page.rootNode.id;
  }

  /** Lays new panels out as a grid under the selected container, or under the page root. */
  private void place(List<Asset> assets) {
    Node target = document.page(0).rootNode.find(selectedId);
    if (target == null || target.content.kind != Content.Kind.NONE)
      target = document.page(0).rootNode;
    int existing = target.children.size();
    int total = existing + assets.size();
    int columns = Math.min(total, 4);
    int rows = (total + columns - 1) / columns;
    target.layout = LayoutSpec.grid(rows, columns);
    target.layout.columnGapMm = 4;
    target.layout.rowGapMm = 4;
    for (int i = 0; i < columns; i++) target.layout.columns.set(i, SizeExpr.fill());
    for (int i = 0; i < existing; i++) {
      Node child = target.children.get(i);
      child.placement.row = i / columns;
      child.placement.column = i % columns;
    }
    for (int i = 0; i < assets.size(); i++) {
      Asset asset = assets.get(i);
      Node node = Node.leaf(asset.seriesName == null || asset.seriesName.isEmpty()
          ? "Image " + (existing + i + 1) : asset.seriesName,
          Content.of(NewImageContent.forAsset(asset.id, library.source(asset.id, 1, 1))));
      node.size.width = SizeExpr.fill();
      // Aspect keeps the pixels square however the columns are later resized.
      node.size.height = SizeExpr.aspectRatio(asset.sizeY / (double) asset.sizeX);
      int index = existing + i;
      target.add(node, index / columns, index % columns);
    }
    document.validate();
    selectedId = target.id;
  }

  /** True when the B and C dock is showing controls for the selection. */
  public boolean contrastShown() { return contrast != null && contrastDock.isVisible(); }

  // ------------------------------------------------------------- direct edits

  /**
   * Right-click actions. Everything here is a structural edit, so it goes through DocumentEdits
   * and then a full re-layout rather than nudging the rendered picture.
   */
  private void showMenu(String nodeId, int x, int y) {
    if (document == null || nodeId == null) return;
    select(nodeId);
    final Node node = document.page(0).rootNode.find(nodeId);
    if (node == null) return;
    boolean isRoot = document.page(0).rootNode.parentOf(nodeId) == null;
    boolean container = !node.children.isEmpty();
    javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
    item(menu, "Split into a row", true, () -> DocumentEdits.split(page(), nodeId, true));
    item(menu, "Split into a column", true, () -> DocumentEdits.split(page(), nodeId, false));
    menu.addSeparator();
    item(menu, "Merge with the cell on the right", !isRoot,
        () -> DocumentEdits.expandSpan(page(), nodeId, true));
    item(menu, "Merge with the cell below", !isRoot,
        () -> DocumentEdits.expandSpan(page(), nodeId, false));
    menu.addSeparator();
    item(menu, "Wrap in a container", true, () -> DocumentEdits.wrapInContainer(page(), nodeId));
    item(menu, "Dissolve this container", container,
        () -> DocumentEdits.unwrap(page(), nodeId));
    item(menu, node.layout.mode == LayoutSpec.Mode.STACK ? "Back to a grid" : "Convert to overlay",
        container,
        () -> DocumentEdits.convertToStack(page(), nodeId,
            node.layout.mode != LayoutSpec.Mode.STACK));
    if (node.content.kind == Content.Kind.TEXT) {
      menu.addSeparator();
      item(menu, "Extend the text area to the right", !isRoot,
          () -> DocumentEdits.extendTextArea(page(), nodeId, true));
      item(menu, "Extend the text area downwards", !isRoot,
          () -> DocumentEdits.extendTextArea(page(), nodeId, false));
      item(menu, "Back to a single cell", node.content.text.flowRegion != null,
          () -> DocumentEdits.resetTextArea(page(), nodeId));
    }
    menu.addSeparator();
    item(menu, "Bring forward", !isRoot, () -> DocumentEdits.bringForward(page(), nodeId));
    item(menu, "Send backward", !isRoot, () -> DocumentEdits.sendBackward(page(), nodeId));
    menu.addSeparator();
    item(menu, "Delete", !isRoot, () -> deleteSelected());
    menu.show(canvas, x, y);
  }

  private void item(javax.swing.JPopupMenu menu, String title, boolean enabled,
      final Runnable action) {
    javax.swing.JMenuItem entry = new javax.swing.JMenuItem(title);
    entry.setEnabled(enabled);
    entry.addActionListener(event -> attempt(() -> {
      action.run();
      document.validate();
      relayout(true);
    }));
    menu.add(entry);
  }

  private Page page() { return document.page(0); }

  /** Removing a node never touches the source images, only the figure. */
  public void deleteSelected() {
    if (document == null || selectedId == null) return;
    Node parent = page().rootNode.parentOf(selectedId);
    if (parent == null) throw new IllegalArgumentException("The page root cannot be removed.");
    DocumentEdits.remove(page(), selectedId);
    selectedId = parent.id;
    relayout(true);
    status.setText("Removed the panel. The source images are untouched.");
  }

  /**
   * Text overflow is reported, not fixed. Shrinking type on the author's behalf would undo the
   * sizes they set, so the status bar names the offenders and the overflow policy is theirs.
   */
  private void reportOverflow(java.util.Set<String> nodeIds) {
    overflowing = nodeIds;
    if (nodeIds.isEmpty()) return;
    StringBuilder names = new StringBuilder();
    for (String id : nodeIds) {
      Node node = document == null ? null : page().rootNode.find(id);
      if (node == null) continue;
      if (names.length() > 0) names.append(", ");
      names.append(node.name.isEmpty() ? id : node.name);
    }
    status.setText(nodeIds.size() + " text block(s) do not fit: " + names
        + ". Enlarge the area, extend it into another cell, or change the overflow setting.");
  }

  public java.util.Set<String> overflowingNodes() { return overflowing; }

  /**
   * Dropping a style onto the canvas applies it to the node under the pointer. Dragging it to
   * the thing it should affect is more direct than selecting first and pressing a button.
   */
  private void installStyleDrops() {
    canvas.setTransferHandler(new javax.swing.TransferHandler() {
      public boolean canImport(TransferSupport support) {
        return support.isDrop()
            && support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor);
      }

      public boolean importData(TransferSupport support) {
        if (!canImport(support) || document == null) return false;
        try {
          String payload = (String) support.getTransferable()
              .getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
          if (!payload.startsWith(StyleManagerPanel.PREFIX)) return false;
          String styleId = payload.substring(StyleManagerPanel.PREFIX.length());
          String nodeId = canvas.nodeAt(support.getDropLocation().getDropPoint());
          if (nodeId == null) return false;
          stylePanel.apply(styleId, nodeId);
          select(nodeId);
          Node node = page().rootNode.find(nodeId);
          status.setText("Applied the style to " + (node == null || node.name.isEmpty()
              ? nodeId : node.name) + ".");
          return true;
        } catch (Exception ex) {
          return false;
        }
      }
    });
  }
}

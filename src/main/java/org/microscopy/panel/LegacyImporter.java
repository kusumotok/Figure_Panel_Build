package org.microscopy.panel;

import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.microscopy.figure.ChannelConfig;
import org.microscopy.figure.ConditionConfig;
import org.microscopy.figure.DisplayChannel;
import org.microscopy.figure.FigureConfiguration;
import org.microscopy.figure.InputImageManager;
import org.microscopy.figure.InsetCell;
import org.microscopy.figure.LabelColors;
import org.microscopy.figure.LabelRenderer;
import org.microscopy.figure.PanelLayoutEngine;
import org.microscopy.figure.ScaleBarConfig;
import org.microscopy.figure.ScaleBarRenderer;
import org.microscopy.figure.SettingsSerializer;

/**
 * Turns a version 1 figure settings file into a node document: the grid becomes a container of
 * one node per cell, and the label bands become text nodes. Structural rather than wholesale, so
 * an imported figure can actually be rearranged, and so the rendered result can be compared cell
 * by cell against the original engine.
 */
public final class LegacyImporter {
  /** Journal double column: a sensible physical size for a figure that had none. */
  public static final double DEFAULT_WIDTH_MM = 180;

  public static final class Imported {
    public final Document document;
    public final InputImageManager inputs;
    public final double mmPerPx;
    public final int legacyWidthPx, legacyHeightPx;

    Imported(Document document, InputImageManager inputs, double mmPerPx, Dimension legacy) {
      this.document = document; this.inputs = inputs; this.mmPerPx = mmPerPx;
      this.legacyWidthPx = legacy.width; this.legacyHeightPx = legacy.height;
    }

    /** Resolution the imported images reach at the chosen physical width. */
    public double effectiveDpi() { return Units.MM_PER_INCH / mmPerPx; }
  }

  public Imported load(File settings) throws IOException { return load(settings, DEFAULT_WIDTH_MM); }

  public Imported load(File settings, double targetWidthMm) throws IOException {
    SettingsSerializer.Loaded loaded = new SettingsSerializer().load(settings);
    return convert(loaded.configuration, loaded.inputs, targetWidthMm);
  }

  public Imported convert(FigureConfiguration config, InputImageManager inputs, double targetWidthMm) {
    if (!(targetWidthMm > 0) || targetWidthMm > Units.MAX_SLIDE_MM)
      throw new IllegalArgumentException(
          "Target width must be between 0 and " + Units.MAX_SLIDE_MM + " mm.");
    config.validate(inputs);
    Dimension legacy = new PanelLayoutEngine().dimensions(config, inputs);
    double mmPerPx = targetWidthMm / legacy.width;
    InputImageManager.Source ref = inputs.get(config.conditions.get(0).sourceId);

    Document document = new Document();
    document.title = "Imported figure";
    for (ConditionConfig condition : config.conditions) {
      InputImageManager.Source source = inputs.get(condition.sourceId);
      if (!document.assets.containsKey(source.id)) document.assets.put(source.id, asset(source));
    }

    LabelRenderer labels = new LabelRenderer();
    int rowBand = labels.rowBand(config.labels), columnBand = labels.columnBand(config.labels);
    int rows = config.rows(), columns = config.columns();
    double cellsWidthMm = mmPerPx * (columns * ref.width + (columns - 1) * config.horizontalGap);
    double cellsHeightMm = mmPerPx * (rows * ref.height + (rows - 1) * config.verticalGap);

    Node root = new Node("Figure");
    root.layout = new LayoutSpec();
    int cellColumn = rowBand > 0 && !config.labels.rowRight ? 1 : 0;
    int cellRow = columnBand > 0 && !config.labels.columnBottom ? 1 : 0;
    if (rowBand > 0) {
      double bandMm = mmPerPx * rowBand;
      root.layout.columns.add(SizeExpr.fixed(cellColumn == 1 ? bandMm : cellsWidthMm));
      root.layout.columns.add(SizeExpr.fixed(cellColumn == 1 ? cellsWidthMm : bandMm));
    } else {
      root.layout.columns.add(SizeExpr.fixed(cellsWidthMm));
    }
    if (columnBand > 0) {
      double bandMm = mmPerPx * columnBand;
      root.layout.rows.add(SizeExpr.fixed(cellRow == 1 ? bandMm : cellsHeightMm));
      root.layout.rows.add(SizeExpr.fixed(cellRow == 1 ? cellsHeightMm : bandMm));
    } else {
      root.layout.rows.add(SizeExpr.fixed(cellsHeightMm));
    }

    root.add(cells(config, inputs, ref, mmPerPx, rows, columns), cellRow, cellColumn);
    if (columnBand > 0)
      root.add(columnLabels(config, mmPerPx, columns), cellRow == 1 ? 0 : 1, cellColumn);
    if (rowBand > 0)
      root.add(rowLabels(config, mmPerPx, rows), cellRow, cellColumn == 1 ? 0 : 1);

    Page page = new Page("Figure");
    page.size = PageSize.fixed(targetWidthMm, mmPerPx * legacy.height);
    page.rootNode = root;
    document.pages.add(page);
    document.validate();
    return new Imported(document, inputs, mmPerPx, legacy);
  }

  private static Asset asset(InputImageManager.Source source) {
    Asset asset = new Asset();
    asset.id = source.id;
    asset.originalUri = source.path;
    asset.originalFormat = "TIFF";
    asset.sizeX = source.width;
    asset.sizeY = source.height;
    asset.sizeC = source.channels;
    asset.pixelWidth = source.pixelWidth > 0 ? source.pixelWidth : 1;
    asset.pixelHeight = source.pixelHeight > 0 ? source.pixelHeight : 1;
    asset.unit = source.unit == null ? "" : source.unit;
    asset.bitDepth = source.bitDepth;
    asset.fingerprint = AssetFingerprint.of(source.path);
    return asset;
  }

  private Node cells(FigureConfiguration config, InputImageManager inputs,
      InputImageManager.Source ref, double mmPerPx, int rows, int columns) {
    Node container = new Node("Panels");
    container.layout = new LayoutSpec();
    for (int i = 0; i < rows; i++) container.layout.rows.add(SizeExpr.fixed(mmPerPx * ref.height));
    for (int i = 0; i < columns; i++) container.layout.columns.add(SizeExpr.fixed(mmPerPx * ref.width));
    container.layout.columnGapMm = mmPerPx * config.horizontalGap;
    container.layout.rowGapMm = mmPerPx * config.verticalGap;
    for (int c = 0; c < config.conditions.size(); c++)
      for (int d = 0; d < config.displayChannels.size(); d++) {
        int row = config.rowsAreChannels ? d : c, column = config.rowsAreChannels ? c : d;
        Node cell = Node.leaf(
            config.conditions.get(c).label + " / "
                + config.displayLabel(config.displayChannels.get(d)),
            Content.of(image(config, inputs, c, d, row, column)));
        cell.size.width = SizeExpr.fill();
        cell.size.height = SizeExpr.fill();
        container.add(cell, row, column);
      }
    return container;
  }

  /** One cell's configuration: the shared figure narrowed to a single image display. */
  private ScientificImageContent image(FigureConfiguration config, InputImageManager inputs,
      int conditionIndex, int displayIndex, int row, int column) {
    FigureConfiguration narrowed = new SettingsSerializer().copy(config);
    ConditionConfig condition = narrowed.conditions.get(conditionIndex);
    InsetCell effective = config.insetCell(conditionIndex, displayIndex);
    condition.insets.clear();
    condition.insets.add(effective);
    condition.shareInsetRoi = false;
    condition.sharedRoiX = null;
    condition.sharedRoiY = null;
    narrowed.conditions.clear();
    narrowed.conditions.add(condition);
    DisplayChannel display = narrowed.displayChannels.get(displayIndex);
    narrowed.displayChannels.clear();
    narrowed.displayChannels.add(display);
    narrowed.labels.showRows = false;
    narrowed.labels.showColumns = false;
    narrowed.horizontalGap = 0;
    narrowed.verticalGap = 0;
    narrowed.automaticGrid = true;
    narrowed.rowsAreChannels = true;
    narrowed.scaleBar.show = new ScaleBarRenderer()
        .applies(config.scaleBar, config, conditionIndex, displayIndex, row, column);
    narrowed.scaleBar.scope = ScaleBarConfig.Scope.EVERY_CELL;
    narrowed.validate(inputs);

    ScientificImageContent content = new ScientificImageContent();
    content.assetId = condition.sourceId;
    content.figureConfig = narrowed;
    return content;
  }

  private Node columnLabels(FigureConfiguration config, double mmPerPx, int columns) {
    Node container = new Node("Column labels");
    container.layout = LayoutSpec.grid(1, columns);
    container.layout.columnGapMm = mmPerPx * config.horizontalGap;
    for (int column = 0; column < columns; column++) {
      DisplayChannel display = config.rowsAreChannels ? null : config.displayChannels.get(column);
      String text = display == null
          ? config.conditions.get(column).label : config.displayLabel(display);
      Node node = Node.leaf("Column label: " + text,
          Content.of(label(config, display, text, false, mmPerPx, TextContent.Rotation.NONE)));
      node.size.width = SizeExpr.fill();
      node.size.height = SizeExpr.fill();
      container.add(node, 0, column);
    }
    return container;
  }

  private Node rowLabels(FigureConfiguration config, double mmPerPx, int rows) {
    Node container = new Node("Row labels");
    container.layout = LayoutSpec.grid(rows, 1);
    container.layout.rowGapMm = mmPerPx * config.verticalGap;
    TextContent.Rotation rotation = config.labels.rowRight
        ? TextContent.Rotation.CLOCKWISE_90 : TextContent.Rotation.COUNTERCLOCKWISE_90;
    for (int row = 0; row < rows; row++) {
      DisplayChannel display = config.rowsAreChannels ? config.displayChannels.get(row) : null;
      String text = display == null
          ? config.conditions.get(row).label : config.displayLabel(display);
      Node node = Node.leaf("Row label: " + text,
          Content.of(label(config, display, text, true, mmPerPx, rotation)));
      node.size.width = SizeExpr.fill();
      node.size.height = SizeExpr.fill();
      container.add(node, row, 0);
    }
    return container;
  }

  /**
   * Merge labels keep one run per source channel in that channel's colour, matching what the
   * existing PPTX exporter writes.
   */
  private TextContent label(FigureConfiguration config, DisplayChannel display, String text,
      boolean row, double mmPerPx, TextContent.Rotation rotation) {
    double sizePt = Units.mmToPt(
        mmPerPx * (row ? config.labels.rowFontSize : config.labels.columnFontSize));
    Color neutral = LabelColors.neutral(config.labels, row);
    List<Run> runs = new ArrayList<Run>();
    if (display == null) {
      runs.add(run(text, neutral, sizePt));
    } else {
      for (int i = 0; i < display.channels.size(); i++) {
        if (i > 0) runs.add(run("/", neutral, sizePt));
        ChannelConfig channel = config.channel(display.channels.get(i));
        runs.add(run(channel.label, LabelColors.channel(channel.lut, config.labels, row), sizePt));
      }
    }
    TextContent content = new TextContent();
    Paragraph paragraph = new Paragraph();
    paragraph.runs = runs;
    paragraph.align = Align.CENTER;
    content.paragraphs.add(paragraph);
    content.rotation = rotation;
    return content;
  }

  private static Run run(String text, Color color, double sizePt) {
    Run run = new Run(text);
    run.colorHex = PropertyValue.hex(color);
    run.fontSizePt = sizePt;
    return run;
  }
}

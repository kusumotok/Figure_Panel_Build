package org.microscopy.panel;

import java.awt.Color;
import java.nio.file.Path;
import org.microscopy.figure.ChannelConfig;
import org.microscopy.figure.ConditionConfig;
import org.microscopy.figure.DisplayChannel;
import org.microscopy.figure.FigureConfiguration;
import org.microscopy.figure.InputImageManager;
import org.microscopy.figure.TestImageGenerator;

/** Shared synthetic documents for the panel tests. Never experimental data. */
final class PanelFixtures {
  static final String[] NAMES = {"Image A", "Image B", "Image C"};

  private PanelFixtures() {}

  /** A poster page with a title, a rows by columns panel grid and a caption. */
  static Document poster(Path folder, InputImageManager inputs, int rows, int columns)
      throws Exception {
    TestImageGenerator.generate(folder);
    Document document = Document.empty();
    Page page = document.page(0);
    page.name = "Poster";
    page.size = PagePresets.byName("A1 Portrait").toPageSize();
    page.margins = Margins.uniform(15);

    Node root = Node.container("Poster", 3, 1);
    root.layout.rows.set(0, SizeExpr.auto());
    root.layout.rows.set(1, SizeExpr.fraction(1));
    root.layout.rows.set(2, SizeExpr.auto());
    root.layout.rowGapMm = 10;
    root.appearance.set(Prop.FILL, PropertyValue.of(Color.WHITE));

    Node title = Node.leaf("Title", Content.of(TextContent.of("Panel grid")));
    title.size.height = SizeExpr.fixed(25);
    title.content.text.paragraphs.get(0).runs.get(0).fontSizePt = 48.0;
    title.content.text.paragraphs.get(0).runs.get(0).bold = Boolean.TRUE;
    title.content.text.paragraphs.get(0).align = Align.CENTER;
    root.add(title, 0, 0);

    Node panels = Node.container("Panels", rows, columns);
    panels.layout.rowGapMm = 6;
    panels.layout.columnGapMm = 6;
    for (int i = 0; i < columns; i++) panels.layout.columns.set(i, SizeExpr.fraction(1));
    for (int row = 0; row < rows; row++)
      for (int column = 0; column < columns; column++) {
        String name = NAMES[(row * columns + column) % NAMES.length];
        InputImageManager.Source source = inputs.load(folder.resolve(name + ".tif").toFile());
        document.assets.put(source.id, asset(source));
        boolean merge = (row + column) % 2 == 1;
        Node cell = Node.leaf(name + (merge ? " Merge" : " Green"),
            Content.of(image(source, merge)));
        cell.size.width = SizeExpr.fill();
        cell.size.height = SizeExpr.aspectRatio(source.height / (double) source.width);
        panels.add(cell, row, column);
      }
    root.add(panels, 1, 0);

    Node caption = Node.leaf("Caption", Content.of(TextContent.of("Scale bars 10 um.")));
    caption.size.height = SizeExpr.fixed(12);
    caption.content.text.paragraphs.get(0).runs.get(0).fontSizePt = 18.0;
    root.add(caption, 2, 0);
    page.rootNode = root;
    document.validate();
    return document;
  }

  static Asset asset(InputImageManager.Source source) {
    Asset asset = new Asset();
    asset.id = source.id;
    asset.originalUri = source.path;
    asset.originalFormat = "TIFF";
    asset.sizeX = source.width;
    asset.sizeY = source.height;
    asset.sizeC = source.channels;
    asset.bitDepth = source.bitDepth;
    asset.fingerprint = AssetFingerprint.of(source.path);
    return asset;
  }

  static ScientificImageContent image(InputImageManager.Source source, boolean merge) {
    FigureConfiguration config = new FigureConfiguration();
    config.conditions.add(new ConditionConfig("", source.id));
    config.channels.add(new ChannelConfig(1, "Green", ChannelConfig.Lut.Green));
    config.channels.add(new ChannelConfig(2, "Red", ChannelConfig.Lut.Red));
    config.channels.get(0).max = 2000;
    config.channels.get(1).max = 2000;
    config.displayChannels.add(merge
        ? new DisplayChannel("Merge", true, 1, 2) : new DisplayChannel("Green", false, 1));
    config.scaleBar.show = true;
    config.scaleBar.lengthUm = 10;
    ScientificImageContent content = new ScientificImageContent();
    content.assetId = source.id;
    content.figureConfig = config;
    return content;
  }
}

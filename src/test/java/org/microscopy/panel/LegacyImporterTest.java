package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.microscopy.figure.ChannelConfig;
import org.microscopy.figure.ConditionConfig;
import org.microscopy.figure.DisplayChannel;
import org.microscopy.figure.FigureConfiguration;
import org.microscopy.figure.InputImageManager;
import org.microscopy.figure.ScaleBarConfig;
import org.microscopy.figure.SettingsSerializer;

class LegacyImporterTest {
  private InputImageManager inputs;

  private FigureConfiguration threeByThree(Path folder) throws Exception {
    org.microscopy.figure.TestImageGenerator.generate(folder);
    inputs = new InputImageManager();
    FigureConfiguration config = new FigureConfiguration();
    for (String name : new String[] {"Image A", "Image B", "Image C"})
      config.conditions.add(
          new ConditionConfig(name, inputs.load(folder.resolve(name + ".tif").toFile()).id));
    config.channels.add(new ChannelConfig(1, "Green", ChannelConfig.Lut.Green));
    config.channels.add(new ChannelConfig(2, "Red", ChannelConfig.Lut.Red));
    config.channels.add(new ChannelConfig(3, "Blue", ChannelConfig.Lut.Blue));
    config.displayChannels.add(new DisplayChannel("Green", false, 1));
    config.displayChannels.add(new DisplayChannel("Red", false, 2));
    config.displayChannels.add(new DisplayChannel("Merge", true, 1, 2));
    config.labels.showRows = true;
    config.labels.showColumns = true;
    config.horizontalGap = 5;
    config.verticalGap = 5;
    return config;
  }

  @Test
  void theGridBecomesOneNodePerCellPlusLabelBands(@TempDir Path folder) throws Exception {
    FigureConfiguration config = threeByThree(folder);
    LegacyImporter.Imported imported = new LegacyImporter().convert(config, inputs, 180);
    Document document = imported.document;
    document.validate();

    assertEquals(3, document.assets.size());
    assertEquals(1, document.pages.size());
    // 256 x 192 cells, 5 px gaps and two 42 px label bands: 820 x 628 px in the old engine.
    assertEquals(820, imported.legacyWidthPx);
    assertEquals(628, imported.legacyHeightPx);
    assertEquals(180, document.page(0).size.widthMm, 1e-9);
    assertEquals(180.0 * 628 / 820, document.page(0).size.heightMm, 1e-9);

    Node root = document.page(0).rootNode;
    assertEquals(3, root.children.size());
    Node panels = named(root, "Panels");
    assertEquals(9, panels.children.size());
    assertEquals(3, panels.layout.rows.size());
    assertEquals(3, panels.layout.columns.size());
    assertEquals(180.0 * 256 / 820, panels.layout.columns.get(0).value, 1e-9);
    assertEquals(180.0 * 5 / 820, panels.layout.columnGapMm, 1e-9);
    assertEquals(3, named(root, "Row labels").children.size());
    assertEquals(3, named(root, "Column labels").children.size());
  }

  @Test
  void labelBandsSitOnTheSideTheFigureAskedFor(@TempDir Path folder) throws Exception {
    FigureConfiguration config = threeByThree(folder);
    Node root = new LegacyImporter().convert(config, inputs, 180).document.page(0).rootNode;
    // Default placement: row labels left, column labels top, so the panels start at (1, 1).
    assertEquals(1, named(root, "Panels").placement.row);
    assertEquals(1, named(root, "Panels").placement.column);
    assertEquals(TextContent.Rotation.COUNTERCLOCKWISE_90,
        named(root, "Row labels").children.get(0).content.text.rotation);

    config.labels.rowRight = true;
    config.labels.columnBottom = true;
    root = new LegacyImporter().convert(config, inputs, 180).document.page(0).rootNode;
    assertEquals(0, named(root, "Panels").placement.row);
    assertEquals(0, named(root, "Panels").placement.column);
    assertEquals(1, named(root, "Row labels").placement.column);
    assertEquals(1, named(root, "Column labels").placement.row);
    assertEquals(TextContent.Rotation.CLOCKWISE_90,
        named(root, "Row labels").children.get(0).content.text.rotation);
  }

  @Test
  void everyCellCarriesAConfigurationThatStillValidates(@TempDir Path folder) throws Exception {
    FigureConfiguration config = threeByThree(folder);
    Node panels = named(new LegacyImporter().convert(config, inputs, 180)
        .document.page(0).rootNode, "Panels");
    for (Node cell : panels.children) {
      ScientificImageContent content = cell.content.scientificImage;
      assertNotNull(content);
      assertEquals(1, content.figureConfig.conditions.size());
      assertEquals(1, content.figureConfig.displayChannels.size());
      assertEquals(0, content.figureConfig.horizontalGap);
      assertTrue(!content.figureConfig.labels.showRows && !content.figureConfig.labels.showColumns,
          "Labels became their own nodes, so the cell must not draw them again");
      assertEquals(ScientificImageContent.Resample.NEAREST, content.resample);
      content.figureConfig.validate(inputs);
    }
  }

  @Test
  void scaleBarScopeIsResolvedPerCellAtImportTime(@TempDir Path folder) throws Exception {
    FigureConfiguration config = threeByThree(folder);
    config.scaleBar.show = true;
    config.scaleBar.scope = ScaleBarConfig.Scope.FIGURE_ONCE;
    Node panels = named(new LegacyImporter().convert(config, inputs, 180)
        .document.page(0).rootNode, "Panels");
    int shown = 0;
    for (Node cell : panels.children) {
      if (!cell.content.scientificImage.figureConfig.scaleBar.show) continue;
      shown++;
      assertEquals(2, cell.placement.row);
      assertEquals(2, cell.placement.column);
    }
    assertEquals(1, shown, "Figure once means exactly one cell keeps the bar");
  }

  @Test
  void mergeLabelsKeepOneColouredRunPerChannel(@TempDir Path folder) throws Exception {
    FigureConfiguration config = threeByThree(folder);
    Node rowLabels = named(new LegacyImporter().convert(config, inputs, 180)
        .document.page(0).rootNode, "Row labels");
    Paragraph merge = rowLabels.children.get(2).content.text.paragraphs.get(0);
    assertEquals("Green/Red", merge.plainText());
    assertEquals(3, merge.runs.size());
    assertEquals("#00FF00", merge.runs.get(0).colorHex);
    assertEquals("#FF0000", merge.runs.get(2).colorHex);
  }

  @Test
  void aSettingsFileOnDiskImportsEndToEnd(@TempDir Path folder) throws Exception {
    FigureConfiguration config = threeByThree(folder);
    File settings = folder.resolve("settings.json").toFile();
    new SettingsSerializer().save(settings, config, inputs);
    LegacyImporter.Imported imported = new LegacyImporter().load(settings);
    assertEquals(9, named(imported.document.page(0).rootNode, "Panels").children.size());
    assertEquals(LegacyImporter.DEFAULT_WIDTH_MM, imported.document.page(0).size.widthMm, 1e-9);
    // 256 px across 56.2 mm is about 116 dpi, which the export dialog has to warn about.
    assertTrue(imported.effectiveDpi() < 150, "Expected a low-resolution warning case");
    assertTrue(new DocumentSerializer().toJson(imported.document).contains("scientificImage"));
  }

  private static Node named(Node parent, String name) {
    for (Node child : parent.children) if (name.equals(child.name)) return child;
    throw new AssertionError("No child named " + name);
  }
}

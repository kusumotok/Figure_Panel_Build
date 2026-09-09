package org.microscopy.panel;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import org.microscopy.figure.InputImageManager;
import org.microscopy.figure.TestImageGenerator;

/** Draws an L-shaped text region beside a figure, which is the case the flow engine exists for. */
public final class TextFlowValidation {
  private static final String BODY =
      "Confocal sections were acquired at 0.065 micrometres per pixel and merged without further "
          + "processing. Scale bars are ten micrometres throughout. Every panel in a row shares "
          + "one brightness range so that intensities stay comparable between fields, and no "
          + "channel was adjusted independently after acquisition. Regions of interest were "
          + "chosen before the display range was set, so the crop cannot flatter the result. "
          + "Counts are given as the mean of three biological replicates. Acquisition settings, "
          + "including laser power and detector gain, were held constant for every field in a "
          + "condition, and the same settings were reused for the paired control so that the "
          + "comparison does not depend on how the display range was later chosen.";

  public static void main(String[] args) throws Exception {
    Path folder = Paths.get("artifacts", "text-flow");
    Files.createDirectories(folder);
    TestImageGenerator.generate(folder);
    InputImageManager inputs = new InputImageManager();

    Document document = Document.empty();
    Page page = document.page(0);
    page.name = "Flow";
    page.size = PagePresets.byName(PagePresets.JOURNAL_DOUBLE_COLUMN).toPageSize();
    page.margins = Margins.uniform(10);

    Node root = Node.container("Figure", 2, 2);
    root.layout.columnGapMm = 5;
    root.layout.rowGapMm = 5;
    root.layout.columns.set(0, SizeExpr.fraction(1));
    root.layout.columns.set(1, SizeExpr.fraction(1));
    // The figure fixes the top band; whatever the caption cannot fit there runs on below it.
    root.layout.rows.set(0, SizeExpr.fixed(58));
    root.layout.rows.set(1, SizeExpr.fixed(30));
    root.appearance.set(Prop.FILL, PropertyValue.of(Color.WHITE));

    InputImageManager.Source source = inputs.load(folder.resolve("Image A.tif").toFile());
    Asset asset = PanelFixtures.asset(source);
    document.assets.put(asset.id, asset);
    Node image = Node.leaf("Figure 1", Content.of(PanelFixtures.image(source, true)));
    image.size.width = SizeExpr.fill();
    image.size.height = SizeExpr.aspectRatio(source.height / (double) source.width);
    root.add(image, 0, 1);

    // The text takes the left column and the whole bottom row: an L around the figure.
    TextContent text = TextContent.of(BODY);
    text.paragraphs.add(Paragraph.of(BODY));
    for (Paragraph paragraph : text.paragraphs) paragraph.runs.get(0).fontSizePt = 8.0;
    text.flowRegion = new FlowRegion();
    text.flowRegion.cells.add(new FlowRegion.Cell(0, 0));
    text.flowRegion.cells.add(new FlowRegion.Cell(1, 0));
    text.flowRegion.cells.add(new FlowRegion.Cell(1, 1));
    Node caption = Node.leaf("Caption", Content.of(text));
    root.add(caption, 0, 0);
    page.rootNode = root;
    document.validate();

    LayoutResult layout = new LayoutEngine().layout(document, page);
    System.out.printf("page    : %.0f x %.0f mm%n",
        layout.pageBox().width, layout.pageBox().height);
    System.out.printf("figure  : %s%n", layout.of(image.id));
    System.out.printf("caption : %s   <- the region bounds, not one cell%n",
        layout.of(caption.id));
    for (String warning : layout.warnings()) System.out.println("warn    : " + warning);

    DocumentRasterizer rasterizer = new DocumentRasterizer(new InputImageManagerSources(inputs));
    ImageIO.write(rasterizer.rasterizeWhole(document, page, layout,
        new RenderTarget(150, RenderTarget.Background.WHITE)), "png",
        folder.resolve("l-shaped-flow.png").toFile());
    System.out.println("wrote " + folder.resolve("l-shaped-flow.png").toAbsolutePath());
  }
}

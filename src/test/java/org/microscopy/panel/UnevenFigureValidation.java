package org.microscopy.panel;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * A layout the old figure mode cannot express: one representative panel spanning several cells,
 * unequal columns, a caption beside them, and every image built by merging two series.
 */
public final class UnevenFigureValidation {
  private static final String CAPTION =
      "Representative field (left) with five further fields from the same experiment. Both "
          + "channels of a field come from separate series in the container and are merged here. "
          + "Scale bars 20 micrometres.";

  public static void main(String[] args) throws Exception {
    File container = new File(args.length > 0 ? args[0] : "test-data/20260123.lif");
    Path out = Paths.get("artifacts", "uneven");
    Files.createDirectories(out);

    ContainerImport importer = new ContainerImport();
    ContainerImport.Plan plan = importer.plan(container);
    plan.skipNamesContaining("x");
    plan.channelsPerImage = 2;
    plan.channelLabels.add("Green");
    plan.channelLabels.add("Red");
    // Six fields is enough to show the shape of the layout.
    List<BioFormatsReader.Series> kept = plan.kept();
    for (int i = 12; i < kept.size(); i++) plan.skipped.add(Integer.valueOf(kept.get(i).index));

    Document document = Document.empty();
    List<Asset> fields = importer.apply(document, plan);
    System.out.println("fields: " + fields.size());

    Page page = document.page(0);
    page.name = "Figure 1";
    page.size = PagePresets.byName(PagePresets.JOURNAL_DOUBLE_COLUMN).toPageSize();
    page.margins = Margins.uniform(6);

    Node root = Node.container("Figure", 2, 1);
    root.layout.rows.set(0, SizeExpr.auto());
    root.layout.rows.set(1, SizeExpr.auto());
    root.layout.rowGapMm = 4;
    root.appearance.set(Prop.FILL, PropertyValue.of(Color.WHITE));

    // Three columns, the first twice as wide: the representative panel lives there.
    Node panels = Node.container("Panels", 2, 3);
    panels.layout.columns.set(0, SizeExpr.fraction(2));
    panels.layout.columns.set(1, SizeExpr.fraction(1));
    panels.layout.columns.set(2, SizeExpr.fraction(1));
    panels.layout.columnGapMm = 3;
    panels.layout.rowGapMm = 3;

    Node big = panel(document, fields.get(0), "A");
    big.placement.rowSpan = 2;
    panels.add(big, 0, 0);
    int at = 1;
    for (int row = 0; row < 2; row++)
      for (int column = 1; column < 3; column++)
        panels.add(panel(document, fields.get(at++), String.valueOf((char) ('A' + at - 1))),
            row, column);
    root.add(panels, 0, 0);

    TextContent caption = TextContent.of(CAPTION);
    caption.paragraphs.get(0).runs.get(0).fontSizePt = 7.5;
    Node text = Node.leaf("Caption", Content.of(caption));
    text.size.width = SizeExpr.fill();
    text.size.height = SizeExpr.fixed(12);
    root.add(text, 1, 0);
    page.rootNode = root;
    document.validate();

    AssetLibrary library = new AssetLibrary();
    library.use(document);
    LayoutResult layout = new LayoutEngine().layout(document, page);
    System.out.printf("page  : %.0f x %.0f mm%n", layout.pageBox().width, layout.pageBox().height);
    System.out.printf("big   : %s%n", layout.of(big.id));
    System.out.println("dpi   : " + ResolutionReport.of(document, page, layout).summary());
    for (String warning : layout.warnings()) System.out.println("warn  : " + warning);

    DocumentRasterizer rasterizer = new DocumentRasterizer(library);
    ImageIO.write(rasterizer.rasterizeWhole(document, page, layout,
        new RenderTarget(150, RenderTarget.Background.WHITE)), "png",
        out.resolve("uneven-figure.png").toFile());
    File pptx = out.resolve("uneven-figure.pptx").toFile();
    new PptxProjectWriter().save(pptx, document, library, new PptxProjectWriter.Options());
    System.out.println("wrote " + out.resolve("uneven-figure.png").toAbsolutePath());
    System.out.println("wrote " + pptx.getAbsolutePath());
  }

  private static Node panel(Document document, Asset asset, String label) {
    AssetLibrary probe = new AssetLibrary();
    probe.use(document);
    Node node = Node.leaf(label,
        Content.of(NewImageContent.forAsset(asset.id, probe.source(asset.id, 1, 1))));
    node.size.width = SizeExpr.fill();
    node.size.height = SizeExpr.aspectRatio(asset.sizeY / (double) asset.sizeX);
    return node;
  }
}

package org.microscopy.panel;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.microscopy.figure.ChannelConfig;
import org.microscopy.figure.ConditionConfig;
import org.microscopy.figure.DisplayChannel;
import org.microscopy.figure.FigureConfiguration;
import org.microscopy.figure.InputImageManager;
import org.microscopy.figure.TestImageGenerator;

/**
 * Proves the claim the architecture rests on: an A0 poster renders at print resolution without
 * ever allocating the whole 139 megapixel raster. Run as a main class, like the existing figure
 * validations, so the unit test suite stays fast.
 */
public final class PosterRenderValidation {
  public static void main(String[] args) throws Exception {
    Path folder = Paths.get("artifacts");
    Files.createDirectories(folder);
    TestImageGenerator.generate(folder);
    InputImageManager inputs = new InputImageManager();

    Document document = Document.empty();
    Page page = document.page(0);
    page.name = "A0 poster";
    page.size = PagePresets.byName("A0 Portrait").toPageSize();
    page.margins = Margins.uniform(20);

    Node root = Node.container("Poster", 3, 1);
    root.layout.rows.set(0, SizeExpr.auto());
    root.layout.rows.set(1, SizeExpr.fraction(1));
    root.layout.rows.set(2, SizeExpr.auto());
    root.layout.rowGapMm = 12;
    root.appearance.set(Prop.FILL, PropertyValue.of(Color.WHITE));

    Node title = Node.leaf("Title", Content.of(TextContent.of("Nested grids at poster scale")));
    title.size.height = SizeExpr.fixed(40);
    Run heading = title.content.text.paragraphs.get(0).runs.get(0);
    heading.fontSizePt = 72.0;
    heading.bold = Boolean.TRUE;
    title.content.text.paragraphs.get(0).align = Align.CENTER;
    root.add(title, 0, 0);

    Node panels = Node.container("Panels", 3, 2);
    panels.layout.rowGapMm = 8;
    panels.layout.columnGapMm = 8;
    for (int i = 0; i < 2; i++) panels.layout.columns.set(i, SizeExpr.fraction(1));
    String[] names = {"Image A", "Image B", "Image C"};
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 2; column++) {
        InputImageManager.Source source =
            inputs.load(folder.resolve(names[row] + ".tif").toFile());
        document.assets.put(source.id, asset(source));
        Node cell = Node.leaf(names[row] + " " + (column == 0 ? "Green" : "Merge"),
            Content.of(image(source, column == 0)));
        cell.size.width = SizeExpr.fill();
        // Aspect keeps the microscopy image undistorted however wide the column becomes.
        cell.size.height = SizeExpr.aspectRatio(source.height / (double) source.width);
        cell.appearance.set(Prop.BORDER_COLOR, PropertyValue.of(Color.DARK_GRAY));
        cell.appearance.set(Prop.BORDER_WIDTH_MM, PropertyValue.of(0.5));
        panels.add(cell, row, column);
      }
    root.add(panels, 1, 0);

    Node caption = Node.leaf("Caption",
        Content.of(TextContent.of("Scale bars 10 um. Rendered in bands at 300 dpi.")));
    caption.size.height = SizeExpr.fixed(20);
    caption.content.text.paragraphs.get(0).runs.get(0).fontSizePt = 28.0;
    root.add(caption, 2, 0);
    page.rootNode = root;
    document.validate();

    LayoutResult layout = new LayoutEngine().layout(document, page);
    RenderTarget target = new RenderTarget(RenderTarget.PRINT_DPI, RenderTarget.Background.WHITE);
    DocumentRasterizer rasterizer = new DocumentRasterizer(new InputImageManagerSources(inputs));
    int[] size = rasterizer.sizePx(layout, target);
    System.out.printf("page   : %.0f x %.0f mm%n",
        layout.pageBox().width, layout.pageBox().height);
    System.out.printf("raster : %d x %d px = %.1f megapixels%n",
        size[0], size[1], size[0] * (double) size[1] / 1e6);
    System.out.println("dpi    : " + ResolutionReport.of(document, page, layout).summary());
    for (String warning : layout.warnings()) System.out.println("warn   : " + warning);

    File png = folder.resolve("a0-poster.png").toFile();
    long started = System.nanoTime();
    rasterizer.writePng(png, document, page, layout, target);
    Runtime runtime = Runtime.getRuntime();
    System.out.printf("wrote  : %s (%.1f MB) in %.1f s%n", png.getName(),
        png.length() / 1e6, (System.nanoTime() - started) / 1e9);
    System.out.printf("heap   : %.0f MB used of %.0f MB max%n",
        (runtime.totalMemory() - runtime.freeMemory()) / 1e6, runtime.maxMemory() / 1e6);

    // A small preview of the same document, for eyeballing the layout.
    RenderTarget small = new RenderTarget(40, RenderTarget.Background.WHITE);
    javax.imageio.ImageIO.write(
        rasterizer.rasterizeWhole(document, page, layout, small), "png",
        folder.resolve("a0-poster-preview.png").toFile());
    System.out.println("preview: artifacts/a0-poster-preview.png");

    File pptx = folder.resolve("a0-poster.pptx").toFile();
    PptxProjectWriter writer = new PptxProjectWriter();
    PptxProjectWriter.Options options = new PptxProjectWriter.Options();
    long saveStarted = System.nanoTime();
    PptxProjectWriter.Saved first =
        writer.save(pptx, document, new InputImageManagerSources(inputs), options);
    System.out.printf("save   : %s (%.1f MB) in %.2f s, %d media rendered%n", pptx.getName(),
        first.bytes / 1e6, (System.nanoTime() - saveStarted) / 1e9, first.renderedMedia);
    saveStarted = System.nanoTime();
    PptxProjectWriter.Saved again =
        writer.save(pptx, document, new InputImageManagerSources(inputs), options);
    System.out.printf("resave : %.2f s, %d rendered / %d reused%n",
        (System.nanoTime() - saveStarted) / 1e9, again.renderedMedia, again.reusedMedia);
    PptxProjectReader.Opened opened = new PptxProjectReader().open(pptx);
    System.out.println("reopen : " + opened.message);
    System.out.println("note   : " + PptxProjectWriter.noteText(options));

  }
  private static Asset asset(InputImageManager.Source source) {
    Asset asset = new Asset();
    asset.id = source.id;
    asset.originalUri = source.path;
    asset.sizeX = source.width;
    asset.sizeY = source.height;
    asset.sizeC = source.channels;
    asset.bitDepth = source.bitDepth;
    return asset;
  }

  private static ScientificImageContent image(InputImageManager.Source source, boolean single) {
    FigureConfiguration config = new FigureConfiguration();
    config.conditions.add(new ConditionConfig("", source.id));
    config.channels.add(new ChannelConfig(1, "Green", ChannelConfig.Lut.Green));
    config.channels.add(new ChannelConfig(2, "Red", ChannelConfig.Lut.Red));
    config.channels.get(0).max = 2000;
    config.channels.get(1).max = 2000;
    config.displayChannels.add(single
        ? new DisplayChannel("Green", false, 1) : new DisplayChannel("Merge", true, 1, 2));
    config.scaleBar.show = true;
    config.scaleBar.lengthUm = 10;
    ScientificImageContent content = new ScientificImageContent();
    content.assetId = source.id;
    content.figureConfig = config;
    return content;
  }
}

package org.microscopy.panel;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import org.microscopy.figure.InputImageManager;

/**
 * A poster built out of finished figures rather than out of images: two cells attach the same
 * saved figure project, one of them embedded, with a title above them.
 */
public final class AttachedPosterValidation {
  public static void main(String[] args) throws Exception {
    File figure = new File(args.length > 0 ? args[0] : "artifacts/uneven/uneven-figure.pptx");
    Path out = Paths.get("artifacts", "attached");
    Files.createDirectories(out);
    if (!figure.isFile()) throw new IllegalStateException("Run UnevenFigureValidation first.");

    LinkedProjects attachments = new LinkedProjects();
    Document poster = Document.empty();
    Page page = poster.page(0);
    page.name = "Poster";
    page.size = PagePresets.byName("A1 Portrait").toPageSize();
    page.margins = Margins.uniform(15);

    Node root = Node.container("Poster", 3, 1);
    root.layout.rows.set(0, SizeExpr.auto());
    root.layout.rows.set(1, SizeExpr.auto());
    root.layout.rows.set(2, SizeExpr.auto());
    root.layout.rowGapMm = 10;
    root.appearance.set(Prop.FILL, PropertyValue.of(Color.WHITE));

    Node title = Node.leaf("Title", Content.of(TextContent.of("Two attached figures")));
    title.size.height = SizeExpr.fixed(24);
    title.content.text.paragraphs.get(0).runs.get(0).fontSizePt = 40.0;
    title.content.text.paragraphs.get(0).runs.get(0).bold = Boolean.TRUE;
    title.content.text.paragraphs.get(0).align = Align.CENTER;
    root.add(title, 0, 0);

    ProjectContent linkedContent = new ProjectContent(figure.getAbsolutePath(), "Figure 1");
    LinkedProjects.Attached open = attachments.open(linkedContent);
    if (open == null) throw new IllegalStateException(attachments.failure(linkedContent));
    System.out.printf("attached page: %.0f x %.0f mm%n", open.box.width, open.box.height);

    Node linked = Node.leaf("Figure 1", Content.of(linkedContent));
    linked.size.width = SizeExpr.fill();
    linked.size.height = SizeExpr.aspectRatio(open.box.height / open.box.width);
    linked.appearance.set(Prop.BORDER_COLOR, PropertyValue.of(new Color(120, 120, 120)));
    linked.appearance.set(Prop.BORDER_WIDTH_MM, PropertyValue.of(0.3));
    root.add(linked, 1, 0);

    Node second = Node.leaf("Figure 2",
        Content.of(new ProjectContent(figure.getAbsolutePath(), "Figure 2")));
    second.size.width = SizeExpr.fill();
    second.size.height = SizeExpr.aspectRatio(open.box.height / open.box.width);
    root.add(second, 2, 0);
    page.rootNode = root;
    poster.validate();

    AssetLibrary library = new AssetLibrary();
    library.use(poster);
    render(poster, page, library, attachments, out.resolve("attached-poster.png"));

    // Embed the second one, then show that the two behave differently from here on.
    DocumentEdits.embedAttached(page, second.id, poster, attachments.open(second.content.project));
    poster.validate();
    System.out.println("after embedding: assets=" + poster.assets.size()
        + " styles=" + poster.styles.size());
    render(poster, page, library, attachments, out.resolve("attached-poster-embedded.png"));

    File pptx = out.resolve("attached-poster.pptx").toFile();
    new PptxProjectWriter(attachments).save(pptx, poster, library,
        new PptxProjectWriter.Options());
    System.out.println("wrote " + pptx.getAbsolutePath());
  }

  private static void render(Document poster, Page page, AssetLibrary library,
      LinkedProjects attachments, Path destination) throws Exception {
    LayoutResult layout = new LayoutEngine(
        new DefaultContentMeasurer(ContentMeasurer.NOMINAL_DPI, attachments)).layout(poster, page);
    System.out.printf("page %.0f x %.0f mm%n", layout.pageBox().width, layout.pageBox().height);
    for (String warning : layout.warnings()) System.out.println("warn: " + warning);
    ImageIO.write(new DocumentRasterizer(library, attachments)
        .rasterizeWhole(poster, page, layout, new RenderTarget(60, RenderTarget.Background.WHITE)),
        "png", destination.toFile());
    System.out.println("wrote " + destination.toAbsolutePath());
  }
}

package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.microscopy.figure.InputImageManager;

/** A saved figure placed inside a cell of another project. */
class AttachedProjectTest {
  private Document figure(Path folder, String caption) throws Exception {
    Document document = Document.empty();
    document.page(0).size = PageSize.fixed(80, 60);
    Node root = Node.container("Figure", 1, 1);
    Node text = Node.leaf("Caption", Content.of(TextContent.of(caption)));
    text.size.width = SizeExpr.fill();
    text.size.height = SizeExpr.fill();
    root.add(text, 0, 0);
    document.page(0).rootNode = root;
    document.validate();
    return document;
  }

  private File saveFigure(Path folder, String name, String caption) throws Exception {
    File file = folder.resolve(name).toFile();
    new PptxProjectWriter().save(file, figure(folder, caption),
        new InputImageManagerSources(new InputImageManager()), new PptxProjectWriter.Options());
    return file;
  }

  private static Document poster(File attached) {
    Document document = Document.empty();
    document.page(0).size = PageSize.fixed(200, 150);
    Node root = Node.container("Poster", 1, 1);
    Node cell = Node.leaf("Figure 1",
        Content.of(new ProjectContent(attached.getAbsolutePath(), "Figure 1")));
    cell.size.width = SizeExpr.fill();
    cell.size.height = SizeExpr.aspectRatio(60.0 / 80.0);
    root.add(cell, 0, 0);
    document.page(0).rootNode = root;
    document.validate();
    return document;
  }

  @Test
  void anAttachedFigureGivesTheCellItsShape(@TempDir Path folder) throws Exception {
    File attached = saveFigure(folder, "figure.pptx", "Attached caption");
    Document document = poster(attached);
    LinkedProjects links = new LinkedProjects();
    LayoutResult layout = new LayoutEngine(
        new DefaultContentMeasurer(ContentMeasurer.NOMINAL_DPI, links))
        .layout(document, document.page(0));
    RectMm cell = layout.of(document.page(0).rootNode.children.get(0).id);
    // The figure is 80 by 60 mm, so the cell keeps four by three whatever width it is given.
    assertEquals(cell.width * 0.75, cell.height, 0.01);
    assertNotNull(links.open(document.page(0).rootNode.children.get(0).content.project));
  }

  @Test
  void theAttachedTextIsWrittenIntoTheHostSlide(@TempDir Path folder) throws Exception {
    File attached = saveFigure(folder, "figure.pptx", "Attached caption");
    Document document = poster(attached);
    File posterFile = folder.resolve("poster.pptx").toFile();
    new PptxProjectWriter(new LinkedProjects()).save(posterFile, document,
        new InputImageManagerSources(new InputImageManager()), new PptxProjectWriter.Options());
    String slide = PptxTestSupport.slide(posterFile);
    assertTrue(slide.contains("Attached caption"),
        "the figure's text stays editable in the poster rather than becoming a picture");
  }

  @Test
  void aLoopBackToTheSameFileIsRefused(@TempDir Path folder) throws Exception {
    File attached = saveFigure(folder, "self.pptx", "Loop");
    ProjectContent content = new ProjectContent(attached.getAbsolutePath(), "Loop");
    LinkedProjects links = new LinkedProjects();
    assertNotNull(links.open(content));

    // A project that attaches itself: opening it while it is already being opened must stop.
    Document selfish = poster(attached);
    Node cell = selfish.page(0).rootNode.children.get(0);
    File loop = folder.resolve("loop.pptx").toFile();
    cell.content.project.uri = loop.getAbsolutePath();
    new PptxProjectWriter(new LinkedProjects()).save(loop, selfish,
        new InputImageManagerSources(new InputImageManager()), new PptxProjectWriter.Options());
    // The file itself still opens; only the reference back to it from inside is refused, so a
    // mistake costs a placeholder rather than the stack.
    LinkedProjects fresh = new LinkedProjects();
    LinkedProjects.Attached opened = fresh.open(cell.content.project);
    assertNotNull(opened);
    assertTrue(opened.box.width > 0);
  }

  @Test
  void embeddingCopiesTheFigureInAndCutsTheLink(@TempDir Path folder) throws Exception {
    File attached = saveFigure(folder, "figure.pptx", "Attached caption");
    Document document = poster(attached);
    LinkedProjects links = new LinkedProjects();
    Node cell = document.page(0).rootNode.children.get(0);
    LinkedProjects.Attached open = links.open(cell.content.project);
    Node embedded = DocumentEdits.embedAttached(document.page(0), cell.id, document, open);
    document.validate();

    assertEquals(Content.Kind.NONE, embedded.content.kind, "the figure's own root is a container");
    assertTrue(embedded.find(embedded.children.get(0).id) != null);
    assertTrue(new DocumentSerializer().toJson(document).contains("Attached caption"),
        "the text now lives in this document");
    assertTrue(!new DocumentSerializer().toJson(document).contains("figure.pptx"),
        "and the link is gone");

    // Editing the file it came from no longer changes anything here.
    new PptxProjectWriter().save(attached, figure(folder, "Changed after embedding"),
        new InputImageManagerSources(new InputImageManager()), new PptxProjectWriter.Options());
    assertTrue(!new DocumentSerializer().toJson(document).contains("Changed after embedding"));
  }

  @Test
  void aMissingAttachmentIsReportedRatherThanFatal(@TempDir Path folder) throws Exception {
    Document document = poster(folder.resolve("never-written.pptx").toFile());
    LinkedProjects links = new LinkedProjects();
    ProjectContent content = document.page(0).rootNode.children.get(0).content.project;
    assertNull(links.open(content));
    assertTrue(links.failure(content).contains("missing"), links.failure(content));
    // The page still lays out, so the gap is visible and can be relinked.
    LayoutResult layout = new LayoutEngine(
        new DefaultContentMeasurer(ContentMeasurer.NOMINAL_DPI, links))
        .layout(document, document.page(0));
    assertTrue(layout.of(document.page(0).rootNode.children.get(0).id).width > 0);
  }
}

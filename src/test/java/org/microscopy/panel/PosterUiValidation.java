package org.microscopy.panel;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.microscopy.figure.InputImageManager;

/**
 * Drives the poster window in a separate process, the way the existing figure validations do.
 * Never touches the user's Fiji windows and never writes to the source TIFFs.
 */
public final class PosterUiValidation {
  private static final List<String> RESULTS = new ArrayList<String>();

  public static void main(String[] args) throws Exception {
    Path folder = Paths.get("artifacts", "poster-ui");
    Files.createDirectories(folder);
    InputImageManager inputs = new InputImageManager();
    final Document document = PanelFixtures.poster(folder, inputs, 3, 2);
    final String[] hashes = hashes(folder);

    final PosterFrame[] frame = new PosterFrame[1];
    SwingUtilities.invokeAndWait(() -> {
      frame[0] = new PosterFrame();
      frame[0].setVisible(true);
      frame[0].show(document, inputs);
    });
    pause();
    shot(frame[0], folder.resolve("01-opened.png"));
    check("Page laid out", frame[0].layoutResult() != null);
    check("Status reports size and resolution",
        frame[0].statusText().contains("mm") && frame[0].statusText().contains("dpi"));

    final Node panels = child(document.page(0).rootNode, "Panels");
    final Node firstPanel = panels.children.get(0);
    SwingUtilities.invokeAndWait(() -> frame[0].selectNode(firstPanel.id));
    pause();
    shot(frame[0], folder.resolve("02-panel-selected.png"));
    check("Selected the panel", firstPanel.id.equals(frame[0].selectedNodeId()));

    SwingUtilities.invokeAndWait(() -> frame[0].parentOfSelection());
    pause();
    check("Escape walks up to the container", panels.id.equals(frame[0].selectedNodeId()));
    SwingUtilities.invokeAndWait(() -> frame[0].firstChildOfSelection());
    pause();
    check("Enter walks back down", firstPanel.id.equals(frame[0].selectedNodeId()));
    SwingUtilities.invokeAndWait(() -> frame[0].nextSiblingOfSelection());
    pause();
    check("Tab moves along the siblings",
        panels.children.get(1).id.equals(frame[0].selectedNodeId()));
    shot(frame[0], folder.resolve("03-breadcrumb.png"));

    // Change the layout through the model the inspector edits, then confirm it took effect.
    double before = frame[0].layoutResult().of(firstPanel.id).width;
    SwingUtilities.invokeAndWait(() -> {
      panels.layout.columns.set(0, SizeExpr.fraction(2));
      frame[0].show(frame[0].document(), inputs);
      frame[0].selectNode(firstPanel.id);
    });
    pause();
    double after = frame[0].layoutResult().of(firstPanel.id).width;
    double ratio = after / before;
    RESULTS.add(String.format("     track 1fr -> 2fr: %.1f mm -> %.1f mm (x%.3f)", before, after, ratio));
    // Two equal tracks share the width 1:1; making one 2fr moves it to 2:3 of the same span.
    check("A 2fr track widens the panel to the expected share", Math.abs(ratio - 4.0 / 3.0) < 0.02);
    shot(frame[0], folder.resolve("04-two-fr.png"));

    File pptx = folder.resolve("poster.pptx").toFile();
    final PptxProjectWriter.Saved[] saved = new PptxProjectWriter.Saved[1];
    SwingUtilities.invokeAndWait(() -> {
      try {
        saved[0] = frame[0].saveProjectTo(pptx);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
    check("Saved a project PPTX", pptx.isFile() && saved[0].slides == 1);
    check("Save stayed at preview size", saved[0].bytes < 20000000L);

    File png = folder.resolve("poster-300dpi.png").toFile();
    SwingUtilities.invokeAndWait(() -> {
      try {
        frame[0].exportPngTo(png, RenderTarget.PRINT_DPI);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
    check("Exported a 300 dpi PNG", png.isFile() && png.length() > 0);

    SwingUtilities.invokeAndWait(() -> {
      try {
        frame[0].openProjectFile(pptx);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
    pause();
    shot(frame[0], folder.resolve("05-reopened.png"));
    check("Reopened the project from its own PPTX", frame[0].document() != null
        && child(frame[0].document().page(0).rootNode, "Panels").children.size() == 6);

    // Direct manipulation. Reopening replaced the document, so the nodes are looked up again.
    final Node livePanels = child(frame[0].document().page(0).rootNode, "Panels");
    final Node target = livePanels.children.get(0);
    LayoutResult.Tracks columnsBefore = frame[0].layoutResult().tracksOf(livePanels.id);
    final double span = columnsBefore.columnSize[0] + columnsBefore.columnSize[1];
    SwingUtilities.invokeAndWait(() -> {
      DocumentEdits.setBoundary(livePanels, true, 0, span * 0.8, span * 0.2);
      frame[0].show(frame[0].document(), inputs);
    });
    pause();
    double widthAfter = frame[0].layoutResult().of(target.id).width;
    RESULTS.add(String.format("     boundary drag: expected %.1f mm, got %.1f mm", span * 0.8, widthAfter));
    check("Dragging a boundary lands the column where it was dropped",
        Math.abs(widthAfter - span * 0.8) < 0.01);
    shot(frame[0], folder.resolve("07-boundary.png"));

    final Node other = livePanels.children.get(1);
    final int wasRow = target.placement.row, wasColumn = target.placement.column;
    SwingUtilities.invokeAndWait(() -> {
      DocumentEdits.moveTo(frame[0].document().page(0), target.id,
          other.placement.row, other.placement.column);
      frame[0].show(frame[0].document(), inputs);
    });
    pause();
    check("Moving onto an occupied cell swaps the two",
        other.placement.row == wasRow && other.placement.column == wasColumn);

    SwingUtilities.invokeAndWait(() -> {
      frame[0].selectNode(other.id);
      frame[0].deleteSelected();
    });
    pause();
    check("Delete removes the panel", livePanels.children.size() == 5);
    check("Source TIFFs survive a delete", java.util.Arrays.equals(hashes, hashes(folder)));

    // The figure mode contrast panel, reused for the selected image.
    SwingUtilities.invokeAndWait(() -> frame[0].selectNode(firstPanel.id));
    pause();
    check("B and C dock appears for an image", frame[0].contrastShown());
    shot(frame[0], folder.resolve("06-contrast.png"));
    SwingUtilities.invokeAndWait(() -> frame[0].selectNode(panels.id));
    pause();
    check("B and C dock hides for a container", !frame[0].contrastShown());

    File tiff = folder.resolve("poster.tif").toFile();
    SwingUtilities.invokeAndWait(() -> {
      try {
        frame[0].exportTiffTo(tiff, 120);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
    check("Exported a calibrated TIFF", tiff.isFile() && tiff.length() > 0);

    // Styles: create one, apply it to a node, and see it change the render.
    SwingUtilities.invokeAndWait(() -> {
      Document live = frame[0].document();
      live.tokens.put("body-size",
          new Token("body-size", "Body size", Token.Kind.FONT_SIZE_PT, "40"));
      Style heading = new Style("heading", "Heading");
      heading.set(Prop.FONT_SIZE_PT, PropertyValue.token("body-size"));
      heading.set(Prop.TEXT_COLOR, PropertyValue.of(java.awt.Color.RED));
      live.styles.put(heading.id, heading);
      Node title = live.page(0).rootNode.children.get(0);
      title.appearance.styleId = "heading";
      title.content.text.paragraphs.get(0).runs.get(0).fontSizePt = null;
      frame[0].show(live, inputs);
    });
    pause();
    Document styled = frame[0].document();
    StyleResolver resolver = new StyleResolver(styled);
    check("A style resolves through its token",
        Double.valueOf(40).equals(resolver.number(styled.page(0),
            styled.page(0).rootNode.children.get(0).id, Prop.FONT_SIZE_PT)));
    shot(frame[0], folder.resolve("08-styles.png"));

    check("Source TIFFs unchanged", java.util.Arrays.equals(hashes, hashes(folder)));
    SwingUtilities.invokeAndWait(() -> frame[0].dispose());

    boolean allPassed = true;
    for (String result : RESULTS) {
      System.out.println(result);
      if (result.startsWith("FAIL")) allPassed = false;
    }
    System.out.println(allPassed ? "ALL CHECKS PASSED" : "SOME CHECKS FAILED");
    System.out.println("screenshots: " + folder.toAbsolutePath());
    if (!allPassed) System.exit(1);
  }

  private static Node child(Node parent, String name) {
    for (Node node : parent.children) if (name.equals(node.name)) return node;
    throw new IllegalStateException("No child named " + name);
  }

  private static void check(String what, boolean passed) {
    RESULTS.add((passed ? "ok   " : "FAIL ") + what);
  }

  private static void pause() throws Exception {
    SwingUtilities.invokeAndWait(() -> {});
    Thread.sleep(350);
    SwingUtilities.invokeAndWait(() -> {});
  }

  /**
   * Paints the window into an image rather than grabbing the screen. A screen grab would also
   * capture whatever else happens to be in front of it, which is both unreliable and none of
   * this check's business.
   */
  private static void shot(final PosterFrame frame, Path destination) throws Exception {
    final BufferedImage image = new BufferedImage(Math.max(1, frame.getWidth()),
        Math.max(1, frame.getHeight()), BufferedImage.TYPE_INT_RGB);
    SwingUtilities.invokeAndWait(() -> {
      java.awt.Graphics2D g = image.createGraphics();
      try {
        frame.paint(g);
      } finally {
        g.dispose();
      }
    });
    ImageIO.write(image, "png", destination.toFile());
  }

  private static String[] hashes(Path folder) throws Exception {
    String[] result = new String[PanelFixtures.NAMES.length];
    for (int i = 0; i < result.length; i++) {
      byte[] bytes = Files.readAllBytes(folder.resolve(PanelFixtures.NAMES[i] + ".tif"));
      StringBuilder hex = new StringBuilder();
      for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
        hex.append(String.format("%02x", value));
      result[i] = hex.toString();
    }
    return result;
  }
}

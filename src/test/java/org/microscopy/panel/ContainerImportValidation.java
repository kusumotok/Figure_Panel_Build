package org.microscopy.panel;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.imageio.ImageIO;
import org.microscopy.figure.ChannelConfig;
import org.microscopy.figure.ConditionConfig;
import org.microscopy.figure.DisplayChannel;
import org.microscopy.figure.FigureConfiguration;
import org.microscopy.figure.InputImageManager;

/**
 * Reads a real Bio-Formats container. Needs Fiji's jars on the classpath, so it runs as a main
 * class rather than a unit test; the unit tests cover compositing with synthetic TIFFs instead.
 */
public final class ContainerImportValidation {
  public static void main(String[] args) throws Exception {
    File file = new File(args.length > 0 ? args[0] : "test-data/20260123.lif");
    Path out = Paths.get("artifacts", "container");
    Files.createDirectories(out);
    System.out.println("bio-formats available: " + BioFormatsReader.available());
    System.out.println("file: " + file.getAbsolutePath() + " (" + file.length() / 1000000 + " MB)");

    ContainerImport importer = new ContainerImport();
    long started = System.nanoTime();
    ContainerImport.Plan plan = importer.plan(file);
    System.out.printf("listed %d series in %.2f s%n",
        plan.series.size(), (System.nanoTime() - started) / 1e9);
    for (BioFormatsReader.Series series : plan.series)
      System.out.println("  " + series + String.format(" %.4f %s/px", series.pixelWidth, series.unit));

    int skipped = plan.skipNamesContaining("x");
    plan.channelsPerImage = 2;
    System.out.println("skipped by name: " + skipped + ", kept: " + plan.kept().size()
        + ", images: " + plan.imageCount() + ", left over: " + plan.remainder());

    // Build one composite out of the first kept pair and draw it.
    Document document = Document.empty();
    ContainerImport.Plan single = new ContainerImport.Plan(file, plan.series.subList(0, 2));
    single.channelsPerImage = 2;
    single.channelLabels.add("Green");
    single.channelLabels.add("Red");
    List<Asset> placeable = importer.apply(document, single);
    Asset composite = placeable.get(0);
    System.out.println("composite: " + composite.seriesName + " C=" + composite.sizeC
        + " " + composite.sizeX + "x" + composite.sizeY + " parts=" + composite.parts.size());

    AssetLibrary library = new AssetLibrary();
    library.use(document);
    started = System.nanoTime();
    InputImageManager.Source built = library.source(composite.id, 1, 1);
    System.out.printf("assembled %d channels in %.2f s, cache %d MB%n",
        built.channels, (System.nanoTime() - started) / 1e9, library.cachedBytes() / 1000000);

    ScientificImageContent content = new ScientificImageContent();
    content.assetId = composite.id;
    content.figureConfig = mergeConfig(built);
    started = System.nanoTime();
    BufferedImage rendered = new ScientificImageRenderer().render(content, built, 1024, 1024);
    System.out.printf("rendered 1024x1024 merge in %.2f s%n", (System.nanoTime() - started) / 1e9);
    ImageIO.write(rendered, "png", out.resolve("lif-merge.png").toFile());
    System.out.println("wrote " + out.resolve("lif-merge.png").toAbsolutePath());

    wholeFigure(file, out);
  }

  private static FigureConfiguration mergeConfig(InputImageManager.Source source) {
    FigureConfiguration config = new FigureConfiguration();
    config.conditions.add(new ConditionConfig("", source.id));
    ChannelConfig.Lut[] luts =
        {ChannelConfig.Lut.Green, ChannelConfig.Lut.Red, ChannelConfig.Lut.Blue};
    Integer[] ids = new Integer[source.channels];
    for (int c = 1; c <= source.channels; c++) {
      ChannelConfig channel =
          new ChannelConfig(c, source.channelNames.get(c - 1), luts[(c - 1) % luts.length]);
      float low = Float.MAX_VALUE, high = -Float.MAX_VALUE;
      for (int y = 0; y < source.height; y++)
        for (int x = 0; x < source.width; x++) {
          float value = source.value(c, x, y);
          if (!Float.isFinite(value)) continue;
          low = Math.min(low, value);
          high = Math.max(high, value);
        }
      channel.min = low;
      channel.max = high > low ? high : low + 1;
      config.channels.add(channel);
      ids[c - 1] = c;
    }
    config.displayChannels.add(new DisplayChannel("Merge", source.channels > 1, ids));
    config.scaleBar.show = true;
    config.scaleBar.lengthUm = 10;
    return config;
  }


  /** Places every grouped image on a page and writes the project and a print export. */
  static void wholeFigure(File file, Path out) throws Exception {
    ContainerImport importer = new ContainerImport();
    ContainerImport.Plan plan = importer.plan(file);
    plan.skipNamesContaining("x");
    plan.channelsPerImage = 2;
    plan.channelLabels.add("Green");
    plan.channelLabels.add("Red");
    // Drop whatever does not complete a group, which is what the dialog asks the user to resolve.
    while (plan.remainder() != 0) {
      List<BioFormatsReader.Series> kept = plan.kept();
      plan.skipped.add(Integer.valueOf(kept.get(kept.size() - 1).index));
    }
    System.out.println("placing " + plan.imageCount() + " images of " + plan.channelsPerImage + " channels");

    final PosterFrame[] frame = new PosterFrame[1];
    javax.swing.SwingUtilities.invokeAndWait(() -> frame[0] = new PosterFrame());
    long started = System.nanoTime();
    javax.swing.SwingUtilities.invokeAndWait(() -> frame[0].addImages(file, plan));
    System.out.printf("imported and laid out in %.1f s%n", (System.nanoTime() - started) / 1e9);
    // Test-only: make the two channels legible in the preview. The product keeps the
    // positional default and leaves colour choice to the channel settings.
    for (Node node : frame[0].document().page(0).rootNode.children) {
      if (node.content.kind != Content.Kind.SCIENTIFIC_IMAGE) continue;
      java.util.List<org.microscopy.figure.ChannelConfig> channels =
          node.content.scientificImage.figureConfig.channels;
      for (int i = 0; i < channels.size(); i++)
        channels.get(i).lut = i == 0
            ? org.microscopy.figure.ChannelConfig.Lut.Green
            : org.microscopy.figure.ChannelConfig.Lut.Red;
    }
    Document document = frame[0].document();
    System.out.println("assets: " + document.assets.size() + ", status: " + frame[0].statusText());

    File pptx = out.resolve("lif-figure.pptx").toFile();
    started = System.nanoTime();
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      try {
        frame[0].saveProjectTo(pptx);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
    System.out.printf("saved project in %.1f s (%.1f MB)%n",
        (System.nanoTime() - started) / 1e9, pptx.length() / 1e6);

    File png = out.resolve("lif-figure-300dpi.png").toFile();
    started = System.nanoTime();
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      try {
        frame[0].exportPngTo(png, RenderTarget.PRINT_DPI);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
    System.out.printf("exported 300 dpi PNG in %.1f s (%.1f MB)%n",
        (System.nanoTime() - started) / 1e9, png.length() / 1e6);
    File small = out.resolve("lif-figure-preview.png").toFile();
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      try {
        frame[0].exportPngTo(small, 50);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
    System.out.println("preview: " + small.getAbsolutePath());
    javax.swing.SwingUtilities.invokeAndWait(() -> frame[0].dispose());
  }
}

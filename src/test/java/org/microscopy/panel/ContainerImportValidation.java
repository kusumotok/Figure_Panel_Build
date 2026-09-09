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
    plan.channelsPerImage = 3;
    System.out.println("skipped by name: " + skipped + ", kept: " + plan.kept().size()
        + ", groups of 3: " + plan.imageCount() + ", left over: " + plan.remainder());

    // Build one composite out of the first three kept series and draw it.
    Document document = Document.empty();
    ContainerImport.Plan single = new ContainerImport.Plan(file, plan.series.subList(0, 3));
    single.channelsPerImage = 3;
    single.channelLabels.add("Green");
    single.channelLabels.add("Red");
    single.channelLabels.add("Blue");
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

}

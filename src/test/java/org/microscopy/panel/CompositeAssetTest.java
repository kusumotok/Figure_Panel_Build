package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.microscopy.figure.InputImageManager;
import org.microscopy.figure.TestImageGenerator;

/**
 * Acquisitions that write one series per fluorescence channel can only be merged if channels
 * from different sources can be bound into one image. These cover that binding.
 */
class CompositeAssetTest {
  private Document document;
  private Asset first, second;

  private AssetLibrary library(Path folder) throws Exception {
    TestImageGenerator.generate(folder);
    document = Document.empty();
    InputImageManager inputs = new InputImageManager();
    first = PanelFixtures.asset(inputs.load(folder.resolve("Image A.tif").toFile()));
    second = PanelFixtures.asset(inputs.load(folder.resolve("Image B.tif").toFile()));
    document.assets.put(first.id, first);
    document.assets.put(second.id, second);
    AssetLibrary library = new AssetLibrary();
    library.use(document);
    return library;
  }

  private Asset merge(String label, Asset a, int channelA, Asset b, int channelB) {
    Asset composite = new Asset();
    composite.id = label;
    composite.kind = Asset.Kind.COMPOSITE;
    composite.seriesName = label;
    composite.sizeX = a.sizeX;
    composite.sizeY = a.sizeY;
    composite.sizeC = 2;
    composite.bitDepth = a.bitDepth;
    composite.pixelWidth = a.pixelWidth;
    composite.pixelHeight = a.pixelHeight;
    composite.parts.add(new Asset.Part(a.id, channelA, "Green"));
    composite.parts.add(new Asset.Part(b.id, channelB, "Red"));
    document.assets.put(composite.id, composite);
    return composite;
  }

  @Test
  void aCompositeBindsOneChannelFromEachSource(@TempDir Path folder) throws Exception {
    AssetLibrary library = library(folder);
    Asset composite = merge("merged", first, 1, second, 2);
    document.validate();

    InputImageManager.Source built = library.source(composite.id, 1, 1);
    InputImageManager.Source a = library.source(first.id, 1, 1);
    InputImageManager.Source b = library.source(second.id, 1, 1);
    assertEquals(2, built.channels);
    assertEquals(a.width, built.width);
    assertEquals(a.height, built.height);
    for (int y = 0; y < built.height; y += 17)
      for (int x = 0; x < built.width; x += 13) {
        assertEquals(a.value(1, x, y), built.value(1, x, y), 0.0,
            "channel 1 must come from the first source at " + x + "," + y);
        assertEquals(b.value(2, x, y), built.value(2, x, y), 0.0,
            "channel 2 must come from the second source at " + x + "," + y);
      }
  }

  @Test
  void aCompositeRendersAsAnOrdinaryTwoChannelMerge(@TempDir Path folder) throws Exception {
    AssetLibrary library = library(folder);
    Asset composite = merge("merged", first, 1, second, 2);
    document.validate();
    InputImageManager.Source built = library.source(composite.id, 1, 1);

    ScientificImageContent content = PanelFixtures.image(built, true);
    content.assetId = composite.id;
    content.figureConfig.scaleBar.show = false;
    java.awt.image.BufferedImage rendered =
        new ScientificImageRenderer().render(content, built, built.width, built.height);
    assertEquals(built.width, rendered.getWidth());
    // The synthetic images put a bright square in the top left of every channel, so the merge of
    // a green and a red channel has to be yellow there.
    int corner = rendered.getRGB(4, 4);
    assertTrue(((corner >> 16) & 255) > 100, "red channel present");
    assertTrue(((corner >> 8) & 255) > 100, "green channel present");
  }

  @Test
  void impossibleCompositesAreRefusedByTheDocument(@TempDir Path folder) throws Exception {
    library(folder);
    Asset composite = merge("merged", first, 1, second, 2);
    document.validate();

    composite.parts.get(1).channel = 9;
    assertTrue(assertThrows(IllegalArgumentException.class, () -> document.validate())
        .getMessage().contains("asks for channel 9"));
    composite.parts.get(1).channel = 2;

    Asset nested = merge("nested", first, 1, second, 2);
    nested.parts.get(0).assetId = composite.id;
    assertTrue(assertThrows(IllegalArgumentException.class, () -> document.validate())
        .getMessage().contains("one level deep"));
    document.assets.remove("nested");

    second.sizeX = second.sizeX + 8;
    assertTrue(assertThrows(IllegalArgumentException.class, () -> document.validate())
        .getMessage().contains("cannot be overlaid"));
  }

  @Test
  void theCacheStaysWithinItsBudgetAndKeepsReturningTheRightPixels(@TempDir Path folder)
      throws Exception {
    TestImageGenerator.generate(folder);
    document = Document.empty();
    InputImageManager inputs = new InputImageManager();
    // Room for roughly one 256 x 192 three channel image at 16 bits.
    AssetLibrary library = new AssetLibrary(300 * 1024);
    library.use(document);
    Asset[] assets = new Asset[3];
    String[] names = {"Image A", "Image B", "Image C"};
    for (int i = 0; i < names.length; i++) {
      assets[i] = PanelFixtures.asset(inputs.load(folder.resolve(names[i] + ".tif").toFile()));
      document.assets.put(assets[i].id, assets[i]);
    }
    float[] expected = new float[names.length];
    for (int i = 0; i < names.length; i++)
      expected[i] = library.source(assets[i].id, 1, 1).value(1, 100, 80);
    assertTrue(library.cachedCount() < names.length,
        "The budget should have dropped something: " + library.cachedCount());
    assertTrue(library.cachedBytes() <= 300 * 1024 * 2L, "Cache stayed near its budget");
    for (int i = 0; i < names.length; i++)
      assertEquals(expected[i], library.source(assets[i].id, 1, 1).value(1, 100, 80), 0.0,
          "A dropped source must reload to the same pixels");
  }
}

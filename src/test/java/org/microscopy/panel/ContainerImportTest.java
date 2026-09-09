package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Grouping is the part a user has to get right, so it is worked out without touching a file.
 */
class ContainerImportTest {
  private static ContainerImport.Plan plan(String... names) {
    List<BioFormatsReader.Series> series = new ArrayList<BioFormatsReader.Series>();
    for (int i = 0; i < names.length; i++)
      series.add(new BioFormatsReader.Series(i, names[i], 8, 8, 1, 1, 1, 16, 1, "um"));
    ContainerImport.Plan plan = new ContainerImport.Plan(new File("container.lif"), series);
    plan.channelLabels.addAll(Arrays.asList("Green", "Red", "Blue"));
    return plan;
  }

  @Test
  void skippingByNameLeavesTheRestGrouped() {
    ContainerImport.Plan plan = plan("S1", "S2", "S3", "S4", "S5", "S6");
    plan.channelsPerImage = 3;
    assertEquals(6, plan.kept().size());
    assertEquals(2, plan.imageCount());
    assertEquals(0, plan.remainder());
    assertEquals(0, plan.skipNamesContaining(""));
  }

  @Test
  void aSingleFailedCaptureShiftsEveryGroupAfterIt() {
    // The real case that makes silent grouping dangerous: dropping one series of a triple moves
    // the next field's channels into the wrong slots.
    ContainerImport.Plan plan = plan("S1", "S2", "S3x", "S4", "S5", "S6", "S7", "S8", "S9");
    plan.channelsPerImage = 3;
    assertEquals(1, plan.skipNamesContaining("x"));
    assertEquals(8, plan.kept().size());
    assertEquals(2, plan.remainder());
    String[] grouping = ContainerImport.grouping(plan);
    assertNull(grouping[2], "the failed series is skipped");
    assertEquals("Image 1 / Green", grouping[0]);
    assertEquals("Image 1 / Red", grouping[1]);
    // S4 has become the blue channel of the first image rather than the green of the second.
    assertEquals("Image 1 / Blue", grouping[3]);
    assertEquals("left over", grouping[7]);
  }

  @Test
  void skippingAWholeFailedFieldKeepsThePhase() {
    ContainerImport.Plan plan =
        plan("S1", "S2", "S3", "S4x", "S5x", "S6x", "S7", "S8", "S9");
    plan.channelsPerImage = 3;
    plan.skipNamesContaining("x");
    assertEquals(6, plan.kept().size());
    assertEquals(0, plan.remainder());
    String[] grouping = ContainerImport.grouping(plan);
    assertEquals("Image 1 / Green", grouping[0]);
    assertEquals("Image 2 / Green", grouping[6]);
    assertEquals("Image 2 / Blue", grouping[8]);
  }

  @Test
  void singleSeriesImagesNeedNoChannelLabels() {
    ContainerImport.Plan plan = plan("S1", "S2");
    plan.channelsPerImage = 1;
    assertEquals(2, plan.imageCount());
    assertEquals("Image 1", ContainerImport.grouping(plan)[0]);
    assertEquals("Image 2", ContainerImport.grouping(plan)[1]);
  }
}

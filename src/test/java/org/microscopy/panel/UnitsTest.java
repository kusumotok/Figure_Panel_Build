package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnitsTest {
  @Test
  void millimetresMapToExactEmu() {
    assertEquals(36000L, Units.mmToEmu(1));
    assertEquals(914400L, Units.mmToEmu(Units.MM_PER_INCH));
    // A0 portrait stays A0 instead of being normalised to a fixed long edge.
    assertEquals(30276000L, Units.mmToEmu(841));
    assertEquals(42804000L, Units.mmToEmu(1189));
    assertEquals(1.0, Units.emuToMm(36000L), 1e-12);
  }

  @Test
  void pointsRoundTrip() {
    assertEquals(72.0, Units.mmToPt(Units.MM_PER_INCH), 1e-9);
    assertEquals(Units.MM_PER_INCH, Units.ptToMm(72), 1e-9);
    assertEquals(2800, Units.mmToCentipoints(Units.ptToMm(28)));
    assertEquals(100, Units.mmToCentipoints(0.0001));
  }

  @Test
  void pixelsFollowTheChosenResolution() {
    assertEquals(300, Units.mmToPx(Units.MM_PER_INCH, 300));
    // A0 at 300 dpi is far past the legacy 100 megapixel canvas limit, which is why the
    // document level rasteriser has to work in tiles.
    long pixels = (long) Units.mmToPx(841, 300) * Units.mmToPx(1189, 300);
    assertTrue(pixels > 100_000_000L, "A0 at 300 dpi should exceed 100 megapixels: " + pixels);
    assertEquals(Units.MM_PER_INCH, Units.pxToMm(300, 300), 1e-9);
    assertThrows(IllegalArgumentException.class, () -> Units.mmToPx(10, 0));
  }

  @Test
  void effectiveDpiReportsWhatAnImageActuallyReaches() {
    assertEquals(300.0, Units.effectiveDpi(300, Units.MM_PER_INCH), 1e-9);
    assertEquals(96.0, Units.effectiveDpi(1024, Units.pxToMm(1024, 96)), 1e-9);
    assertThrows(IllegalArgumentException.class, () -> Units.effectiveDpi(0, 10));
  }

  @Test
  void slideLimitIsTheRealPowerPointOne() {
    assertEquals(1422.4, Units.MAX_SLIDE_MM, 1e-9);
    assertEquals("", PageSize.fixed(841, 1189).slideSizeWarning(), "A0 fits in a PowerPoint slide");
    assertTrue(PageSize.fixed(1500, 1000).slideSizeWarning().startsWith("WARNING"));
  }
}

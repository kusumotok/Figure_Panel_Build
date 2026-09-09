package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FlowRegionGeometryTest {
  /** A 3 by 3 grid of 30 mm cells with 10 mm gaps, so cell n starts at 40n. */
  private static LayoutResult.Tracks grid() {
    double[] start = {0, 40, 80};
    double[] size = {30, 30, 30};
    return new LayoutResult.Tracks(start, size, start.clone(), size.clone(), 10, 10);
  }

  private static FlowRegion region(int... rowsAndColumns) {
    FlowRegion region = new FlowRegion();
    for (int i = 0; i < rowsAndColumns.length; i += 2)
      region.cells.add(new FlowRegion.Cell(rowsAndColumns[i], rowsAndColumns[i + 1]));
    return region;
  }

  @Test
  void anLShapeIsOneIslandAndWidensWhereTheFootBegins() {
    // Text column on the left, figure top right: cells (0,0), (1,0) and (1,1).
    FlowRegion region = region(0, 0, 1, 0, 1, 1);
    List<FlowRegionGeometry.Island> islands = FlowRegionGeometry.islands(grid(), region);
    assertEquals(1, islands.size());
    FlowRegionGeometry.Island island = islands.get(0);

    List<double[]> top = island.runsAt(5, 4);
    assertEquals(1, top.size());
    assertEquals(0, top.get(0)[0], 1e-9);
    assertEquals(30, top.get(0)[1], 1e-9);

    // Below the gap the second row contributes two cells, which merge into one run because the
    // gap between them is bridged by nothing; they stay separate runs.
    List<double[]> foot = island.runsAt(45, 4);
    assertEquals(2, foot.size());
    assertEquals(0, foot.get(0)[0], 1e-9);
    assertEquals(30, foot.get(0)[1], 1e-9);
    assertEquals(40, foot.get(1)[0], 1e-9);
    assertEquals(70, foot.get(1)[1], 1e-9);
  }

  @Test
  void aBandInTheGapHasNoRoom() {
    FlowRegion region = region(0, 0, 1, 0);
    FlowRegionGeometry.Island island = FlowRegionGeometry.islands(grid(), region).get(0);
    assertTrue(island.runsAt(32, 4).isEmpty(), "The 10 mm gap holds no text");
  }

  @Test
  void cellsThatDoNotTouchOnTheGridBecomeSeparateIslands() {
    FlowRegion region = region(0, 0, 2, 2);
    List<FlowRegionGeometry.Island> islands = FlowRegionGeometry.islands(grid(), region);
    assertEquals(2, islands.size());
  }

  @Test
  void anExclusionCanCutARegionInTwo() {
    FlowRegion region = region(0, 0, 0, 1, 0, 2);
    assertEquals(1, FlowRegionGeometry.islands(grid(), region).size());
    region.exclusions.add(new FlowRegion.Cell(0, 1));
    assertEquals(2, FlowRegionGeometry.islands(grid(), region).size());
  }

  @Test
  void islandOrderFollowsTheChosenReadingDirection() {
    FlowRegion region = region(0, 2, 2, 0);
    List<FlowRegionGeometry.Island> islands = FlowRegionGeometry.islands(grid(), region);
    assertEquals(2, islands.size());

    region.order = FlowRegion.Order.TOP_TO_BOTTOM;
    List<FlowRegionGeometry.Island> down = FlowRegionGeometry.ordered(islands, region);
    assertEquals(0, down.get(0).cells.get(0).row, "top first");

    region.order = FlowRegion.Order.LEFT_TO_RIGHT;
    List<FlowRegionGeometry.Island> across = FlowRegionGeometry.ordered(islands, region);
    assertEquals(0, across.get(0).cells.get(0).column, "leftmost first");

    region.order = FlowRegion.Order.RIGHT_TO_LEFT;
    assertEquals(2, FlowRegionGeometry.ordered(islands, region).get(0).cells.get(0).column);
  }

  @Test
  void manualOrderWinsAndMissingIslandsFollow() {
    FlowRegion region = region(0, 0, 0, 2, 2, 0);
    List<FlowRegionGeometry.Island> islands = FlowRegionGeometry.islands(grid(), region);
    assertEquals(3, islands.size());
    region.order = FlowRegion.Order.MANUAL;
    region.manualOrder.add(2);
    List<FlowRegionGeometry.Island> manual = FlowRegionGeometry.ordered(islands, region);
    assertEquals(islands.get(2), manual.get(0));
    assertEquals(3, manual.size(), "islands left out of the manual list still get a turn");
  }

  @Test
  void duplicateCellsDoNotDuplicateTheGeometry() {
    FlowRegion region = region(0, 0, 0, 0, 0, 1);
    FlowRegionGeometry.Island island = FlowRegionGeometry.islands(grid(), region).get(0);
    assertEquals(2, island.rectangles.size());
  }
}

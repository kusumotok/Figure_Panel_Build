package org.microscopy.panel;

import java.util.ArrayList;
import java.util.List;

/** Journal column widths matter more day to day than poster sizes, so both live in one list. */
public final class PagePresets {
  public static final class Preset {
    public final String name;
    public final double widthMm, heightMm;
    public final boolean widthOnly;

    Preset(String name, double widthMm, double heightMm, boolean widthOnly) {
      this.name = name; this.widthMm = widthMm; this.heightMm = heightMm; this.widthOnly = widthOnly;
    }

    public PageSize toPageSize() {
      PageSize size = widthOnly ? PageSize.fixedWidth(widthMm) : PageSize.fixed(widthMm, heightMm);
      size.presetName = name;
      return size;
    }
  }

  public static final String JOURNAL_DOUBLE_COLUMN = "Journal double column";

  private PagePresets() {}

  public static List<Preset> all() {
    List<Preset> presets = new ArrayList<Preset>();
    presets.add(new Preset("A0 Portrait", 841, 1189, false));
    presets.add(new Preset("A0 Landscape", 1189, 841, false));
    presets.add(new Preset("A1 Portrait", 594, 841, false));
    presets.add(new Preset("A1 Landscape", 841, 594, false));
    presets.add(new Preset("A2 Portrait", 420, 594, false));
    presets.add(new Preset("A4 Portrait", 210, 297, false));
    presets.add(new Preset("Journal single column", 85, 0, true));
    presets.add(new Preset("Journal 1.5 column", 114, 0, true));
    presets.add(new Preset(JOURNAL_DOUBLE_COLUMN, 180, 0, true));
    return presets;
  }

  public static Preset byName(String name) {
    for (Preset preset : all()) if (preset.name.equals(name)) return preset;
    throw new IllegalArgumentException("Unknown page preset: " + name);
  }
}

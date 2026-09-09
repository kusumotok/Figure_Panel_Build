package org.microscopy.panel;

import org.microscopy.figure.AppearanceDefaults;
import org.microscopy.figure.ChannelConfig;
import org.microscopy.figure.ConditionConfig;
import org.microscopy.figure.DisplayChannel;
import org.microscopy.figure.FigureConfiguration;
import org.microscopy.figure.InputImageManager;

/**
 * Builds the figure configuration a freshly placed image starts with: one display showing every
 * channel, each auto-ranged, with the appearance defaults the figure mode already uses.
 */
public final class NewImageContent {
  private NewImageContent() {}

  public static ScientificImageContent forAsset(String assetId, InputImageManager.Source source) {
    FigureConfiguration config = new FigureConfiguration();
    config.conditions.add(new ConditionConfig("", assetId));
    Integer[] ids = new Integer[source.channels];
    for (int c = 1; c <= source.channels; c++) {
      ChannelConfig.Lut lut = source.channels == 1
          ? ChannelConfig.Lut.Grayscale : ChannelConfig.defaultLut(c);
      ChannelConfig channel = new ChannelConfig(c, source.channelNames.get(c - 1), lut);
      double[] range = range(source, c);
      channel.min = range[0];
      channel.max = range[1];
      config.channels.add(channel);
      ids[c - 1] = c;
    }
    config.displayChannels.add(new DisplayChannel(
        source.channels > 1 ? "Merge" : source.channelNames.get(0), source.channels > 1, ids));
    AppearanceDefaults.initialize(config, source);
    // A bar can only be drawn when the acquisition recorded a pixel size.
    config.scaleBar.show = source.pixelWidth > 0 && source.unit != null
        && !source.unit.isEmpty() && !"pixel".equalsIgnoreCase(source.unit);
    ScientificImageContent content = new ScientificImageContent();
    content.assetId = assetId;
    content.figureConfig = config;
    return content;
  }

  /** Full range of one channel; the same policy as the figure mode's Auto. */
  static double[] range(InputImageManager.Source source, int channel) {
    double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
    for (int y = 0; y < source.height; y++)
      for (int x = 0; x < source.width; x++) {
        float value = source.value(channel, x, y);
        if (!Float.isFinite(value)) continue;
        min = Math.min(min, value);
        max = Math.max(max, value);
      }
    if (!Double.isFinite(min)) return new double[] {0, 1};
    return new double[] {min, max > min ? max : min + 1};
  }

}

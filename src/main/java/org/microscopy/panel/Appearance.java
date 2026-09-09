package org.microscopy.panel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Background, border, radius, padding and the rest. Available on every node at every depth:
 * a poster root, a section, a figure group and a caption box all use this same object.
 */
public final class Appearance {
  public String styleId;
  public Map<String, PropertyValue> overrides = new LinkedHashMap<String, PropertyValue>();

  public Appearance set(String key, PropertyValue value) { overrides.put(key, value); return this; }

  public void validate(String where) {
    if (overrides == null) throw new IllegalArgumentException(where + ": missing overrides map.");
    for (Map.Entry<String, PropertyValue> entry : overrides.entrySet()) {
      if (entry.getValue() == null)
        throw new IllegalArgumentException(where + ": null override for " + entry.getKey());
      entry.getValue().validate(where + "." + entry.getKey());
    }
  }
}

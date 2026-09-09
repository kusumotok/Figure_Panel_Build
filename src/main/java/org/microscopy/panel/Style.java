package org.microscopy.panel;

import java.util.LinkedHashMap;
import java.util.Map;

/** A named set of properties. Text, panel and figure styles are all just this. */
public final class Style {
  public String id;
  public String name = "";
  public Map<String, PropertyValue> properties = new LinkedHashMap<String, PropertyValue>();

  public Style() {}

  public Style(String id, String name) { this.id = id; this.name = name; }

  public Style set(String key, PropertyValue value) { properties.put(key, value); return this; }

  public void validate() {
    if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Style without an id.");
    if (properties == null) throw new IllegalArgumentException("Style " + id + ": missing properties.");
    for (Map.Entry<String, PropertyValue> entry : properties.entrySet()) {
      if (entry.getValue() == null)
        throw new IllegalArgumentException("Style " + id + ": null value for " + entry.getKey());
      entry.getValue().validate("Style " + id + "." + entry.getKey());
    }
  }
}

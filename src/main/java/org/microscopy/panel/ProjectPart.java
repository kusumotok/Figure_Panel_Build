package org.microscopy.panel;

/**
 * The project part inside a PPTX: the document JSON, held as the authoritative copy. The shape
 * tree beside it is a rendering of this, never the other way round.
 */
public final class ProjectPart {
  /** Fixed id so a file written by this tool can be recognised without guessing. */
  public static final String ITEM_ID = "{7C5B2A14-4E9D-4F3B-9A21-6F0E5D8B1C34}";
  public static final String NAMESPACE = "urn:org.microscopy.panel:project:1";
  public static final String ITEM_PATH = "customXml/item1.xml";
  public static final String ITEM_PROPS_PATH = "customXml/itemProps1.xml";

  private ProjectPart() {}

  public static String wrap(String json, String saveQuality) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<project xmlns=\"" + NAMESPACE + "\" schemaVersion=\"" + Document.SCHEMA_VERSION
        + "\" saveQuality=\"" + Ooxml.xml(saveQuality) + "\">"
        + "<json><![CDATA[" + json.replace("]]>", "]]]]><![CDATA[>") + "]]></json>"
        + "</project>";
  }

  /** Pulls the JSON back out. Deliberately tolerant of whitespace and attribute order. */
  public static String unwrap(String partXml) {
    int start = partXml.indexOf("<![CDATA[");
    int end = partXml.lastIndexOf("]]>");
    if (start < 0 || end < start)
      throw new IllegalArgumentException("The project part does not contain project data.");
    return partXml.substring(start + "<![CDATA[".length(), end).replace("]]]]><![CDATA[>", "]]>");
  }

  public static String saveQuality(String partXml) {
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("saveQuality=\"([^\"]*)\"").matcher(partXml);
    return matcher.find() ? matcher.group(1) : "";
  }

  public static String itemProps() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<ds:datastoreItem xmlns:ds=\"http://schemas.openxmlformats.org/officeDocument/2006/"
        + "customXml\" ds:itemID=\"" + ITEM_ID + "\">"
        + "<ds:schemaRefs><ds:schemaRef ds:uri=\"" + NAMESPACE + "\"/></ds:schemaRefs>"
        + "</ds:datastoreItem>";
  }
}

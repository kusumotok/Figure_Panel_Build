package org.microscopy.panel;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Small shared helpers for hand-written OOXML. */
final class Ooxml {
  static final String PML = "http://schemas.openxmlformats.org/presentationml/2006/main";
  static final String DML = "http://schemas.openxmlformats.org/drawingml/2006/main";
  static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

  private Ooxml() {}

  /** Escapes for XML and drops the control characters OOXML forbids. */
  static String xml(String text) {
    if (text == null) return "";
    StringBuilder out = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c < 0x20 && c != 0x09 && c != 0x0A && c != 0x0D) continue;
      if (c == '&') out.append("&amp;");
      else if (c == '<') out.append("&lt;");
      else if (c == '>') out.append("&gt;");
      else if (c == '"') out.append("&quot;");
      else out.append(c);
    }
    return out.toString();
  }

  /** Stable short hash used to name cacheable media parts. */
  static String hash(String key) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (int i = 0; i < 10; i++) hex.append(String.format("%02x", digest[i]));
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable.", ex);
    }
  }

  static String transform(RectMm rect, int rotationSixtyThousandths) {
    long[] emu = rect.toEmu();
    return "<a:xfrm rot=\"" + rotationSixtyThousandths + "\"><a:off x=\"" + emu[0] + "\" y=\""
        + emu[1] + "\"/><a:ext cx=\"" + Math.max(1, emu[2]) + "\" cy=\"" + Math.max(1, emu[3])
        + "\"/></a:xfrm>";
  }

  static String relationship(String id, String type, String target) {
    return "<Relationship Id=\"" + id + "\" Type=\"" + REL + "/" + type + "\" Target=\""
        + target + "\"/>";
  }

  static String override(String partName, String contentType) {
    return "<Override PartName=\"" + partName + "\" ContentType=\"" + contentType + "\"/>";
  }

  static byte[] utf8(String text) {
    return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }
}

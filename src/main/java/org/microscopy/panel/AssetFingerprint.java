package org.microscopy.panel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Size, modified time and a hash of the leading bytes. Enough to notice that a linked source
 * moved or changed without reading gigabytes, and cheap enough to compute on every save.
 */
public final class AssetFingerprint {
  private static final int HEAD_BYTES = 64 * 1024;

  private AssetFingerprint() {}

  public static String of(String path) {
    if (path == null || path.trim().isEmpty()) return "";
    File file = new File(path);
    if (!file.isFile()) return "";
    try {
      byte[] head = new byte[HEAD_BYTES];
      int read = 0;
      try (InputStream in = Files.newInputStream(file.toPath())) {
        int n;
        while (read < head.length && (n = in.read(head, read, head.length - read)) != -1) read += n;
      }
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(head, 0, read);
      StringBuilder hex = new StringBuilder();
      byte[] hash = digest.digest();
      for (int i = 0; i < 8; i++) hex.append(String.format("%02x", hash[i]));
      return file.length() + ":" + file.lastModified() + ":" + hex;
    } catch (IOException ex) {
      return "";
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable.", ex);
    }
  }

  /** True when the file on disk still matches a recorded fingerprint. */
  public static boolean matches(String path, String fingerprint) {
    if (fingerprint == null || fingerprint.isEmpty()) return false;
    return fingerprint.equals(of(path));
  }
}

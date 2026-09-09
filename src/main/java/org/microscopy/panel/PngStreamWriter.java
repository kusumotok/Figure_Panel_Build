package org.microscopy.panel;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.awt.image.BufferedImage;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Writes a PNG band by band. ImageIO wants the whole raster in memory at once, which an A0
 * poster at 300 dpi cannot supply, so the pixel data is deflated as it arrives and emitted as a
 * sequence of IDAT chunks; only one band is ever held.
 */
public final class PngStreamWriter implements Closeable {
  private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

  private final DataOutputStream out;
  private final int width, height;
  private final boolean alpha;
  private final Deflater deflater = new Deflater(Deflater.BEST_SPEED);
  private final byte[] deflateBuffer = new byte[64 * 1024];
  private int rowsWritten;
  private boolean closed;

  public PngStreamWriter(OutputStream out, int width, int height, boolean alpha) throws IOException {
    if (width < 1 || height < 1) throw new IllegalArgumentException("Invalid PNG size.");
    this.out = new DataOutputStream(out);
    this.width = width;
    this.height = height;
    this.alpha = alpha;
    this.out.write(SIGNATURE);
    ByteArrayOutputStream header = new ByteArrayOutputStream();
    DataOutputStream ihdr = new DataOutputStream(header);
    ihdr.writeInt(width);
    ihdr.writeInt(height);
    ihdr.writeByte(8);
    ihdr.writeByte(alpha ? 6 : 2);
    ihdr.writeByte(0);
    ihdr.writeByte(0);
    ihdr.writeByte(0);
    chunk("IHDR", header.toByteArray());
  }

  /** Appends the next rows. Bands must be full width and arrive in top to bottom order. */
  public void band(BufferedImage band) throws IOException {
    if (closed) throw new IllegalStateException("Writer is closed.");
    if (band.getWidth() != width)
      throw new IllegalArgumentException("Band width " + band.getWidth() + " does not match " + width);
    if (rowsWritten + band.getHeight() > height)
      throw new IllegalArgumentException("Bands exceed the declared image height.");
    int stride = alpha ? 4 : 3;
    byte[] row = new byte[1 + width * stride];
    int[] line = new int[width];
    ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    for (int y = 0; y < band.getHeight(); y++) {
      band.getRGB(0, y, width, 1, line, 0, width);
      int at = 1;
      for (int x = 0; x < width; x++) {
        int argb = line[x];
        row[at++] = (byte) (argb >> 16);
        row[at++] = (byte) (argb >> 8);
        row[at++] = (byte) argb;
        if (alpha) row[at++] = (byte) (argb >>> 24);
      }
      deflater.setInput(row);
      while (!deflater.needsInput()) {
        int n = deflater.deflate(deflateBuffer);
        if (n > 0) compressed.write(deflateBuffer, 0, n);
      }
    }
    rowsWritten += band.getHeight();
    if (compressed.size() > 0) chunk("IDAT", compressed.toByteArray());
  }

  @Override
  public void close() throws IOException {
    if (closed) return;
    closed = true;
    try {
      if (rowsWritten != height)
        throw new IOException("PNG declared " + height + " rows but received " + rowsWritten + ".");
      deflater.finish();
      ByteArrayOutputStream tail = new ByteArrayOutputStream();
      while (!deflater.finished()) {
        int n = deflater.deflate(deflateBuffer);
        if (n > 0) tail.write(deflateBuffer, 0, n);
      }
      if (tail.size() > 0) chunk("IDAT", tail.toByteArray());
      chunk("IEND", new byte[0]);
      out.flush();
    } finally {
      deflater.end();
    }
  }

  private void chunk(String type, byte[] data) throws IOException {
    byte[] name = type.getBytes("US-ASCII");
    out.writeInt(data.length);
    out.write(name);
    out.write(data);
    CRC32 crc = new CRC32();
    crc.update(name);
    crc.update(data);
    out.writeInt((int) crc.getValue());
  }
}

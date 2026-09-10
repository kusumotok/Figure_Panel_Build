package org.microscopy.panel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the projects attached to cells, once each, and refuses to follow a chain back to a file
 * already on it. Two posters attaching each other would otherwise recurse until the stack runs
 * out, and the honest answer is to say which file closes the loop.
 */
public final class LinkedProjects {
  public static final class Attached {
    public final Document document;
    public final Page page;
    /** Physical size of the attached page, which is what gives the cell its aspect. */
    public final RectMm box;

    /** Its own pixels: the host's library knows nothing about the attached document's assets. */
    public final AssetLibrary library;

    Attached(Document document, Page page, RectMm box) {
      this.document = document; this.page = page; this.box = box;
      this.library = new AssetLibrary();
      this.library.use(document);
    }
  }

  private final Map<String, Attached> cache = new HashMap<String, Attached>();
  private final Map<String, String> failures = new HashMap<String, String>();
  private final Deque<String> loading = new ArrayDeque<String>();

  /** The attached project, or null when it cannot be read; the reason is kept for the caller. */
  public Attached open(ProjectContent content) {
    String key = key(content);
    if (cache.containsKey(key)) return cache.get(key);
    if (failures.containsKey(key)) return null;
    String reason = load(content, key);
    if (reason != null) {
      failures.put(key, reason);
      return null;
    }
    return cache.get(key);
  }

  public String failure(ProjectContent content) { return failures.get(key(content)); }

  /** True when the file on disk is no longer the one that was attached. */
  public boolean changedOnDisk(ProjectContent content) {
    if (content.fingerprint == null || content.fingerprint.isEmpty()) return false;
    return !AssetFingerprint.matches(content.uri, content.fingerprint);
  }

  private String load(ProjectContent content, String key) {
    File file = new File(content.uri);
    if (!file.isFile()) return "the attached project is missing: " + file.getName();
    String canonical = canonical(file);
    if (loading.contains(canonical))
      return "attaching " + file.getName() + " here would loop back on itself";
    loading.push(canonical);
    try {
      PptxProjectReader.Opened opened = new PptxProjectReader().open(file);
      if (!opened.editable())
        return file.getName() + " has no project information, so it cannot be attached";
      Document document = opened.document;
      if (content.pageIndex >= document.pages.size())
        return file.getName() + " has no page " + (content.pageIndex + 1);
      Page page = document.pages.get(content.pageIndex);
      // Measuring the attached page must share this guard, or a self-reference recurses until
      // the stack runs out instead of being reported.
      LayoutResult layout = new LayoutEngine(
          new DefaultContentMeasurer(ContentMeasurer.NOMINAL_DPI, this)).layout(document, page);
      cache.put(key, new Attached(document, page, layout.pageBox()));
      // A self-reference inside recorded a failure under this same key while it was loading.
      failures.remove(key);
      return null;
    } catch (IOException ex) {
      return "cannot read " + file.getName() + ": " + ex.getMessage();
    } catch (RuntimeException ex) {
      return "cannot use " + file.getName() + ": " + ex.getMessage();
    } finally {
      loading.pop();
    }
  }

  private static String canonical(File file) {
    try {
      return file.getCanonicalPath();
    } catch (IOException ex) {
      return file.getAbsolutePath();
    }
  }

  private static String key(ProjectContent content) {
    return content.uri + "#" + content.pageIndex;
  }

  public void clear() {
    cache.clear();
    failures.clear();
  }
}

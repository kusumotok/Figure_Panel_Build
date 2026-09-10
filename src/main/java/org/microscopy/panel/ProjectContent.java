package org.microscopy.panel;

/**
 * A whole saved project placed inside one cell: a finished figure attached to a poster.
 *
 * <p>The link is kept rather than the contents, so correcting the figure and reopening the
 * poster shows the correction. It can be embedded later, which copies the figure in and cuts the
 * link; that is the same choice a page layout program offers for placed artwork.
 *
 * <p>Only a project written by this tool can be attached. An arbitrary PowerPoint file cannot be
 * laid out or drawn here, and pretending otherwise by pasting a thumbnail would not be an
 * attachment.
 */
public final class ProjectContent {
  public String uri;
  /** Which page of the attached project to show. */
  public int pageIndex;
  /** Noticed when the attached file changes on disk; shown, never acted on silently. */
  public String fingerprint = "";
  /** Remembered so a missing attachment can still be named on the canvas. */
  public String title = "";

  public ProjectContent() {}

  public ProjectContent(String uri, String title) {
    this.uri = uri;
    this.title = title;
    this.fingerprint = AssetFingerprint.of(uri);
  }

  public void validate(String where) {
    if (uri == null || uri.trim().isEmpty())
      throw new IllegalArgumentException(where + ": an attached project needs a file.");
    if (pageIndex < 0) throw new IllegalArgumentException(where + ": negative page index.");
  }
}

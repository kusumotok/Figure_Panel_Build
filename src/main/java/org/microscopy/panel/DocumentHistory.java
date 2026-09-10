package org.microscopy.panel;

import java.util.ArrayList;
import java.util.List;

/**
 * Undo for the layout editor, following the figure mode's approach: whole-document snapshots
 * taken as JSON. The document holds no pixels, so a snapshot is small, and comparing the text is
 * what lets every edit path be caught by one hook instead of remembering to record at each one.
 */
public final class DocumentHistory {
  private static final int LIMIT = 100;
  /** Edits of the same kind within this long are folded together, as dragging a slider is. */
  private static final long GROUP_NANOS = 600_000_000L;

  private final DocumentSerializer serializer = new DocumentSerializer();
  private final List<String> states = new ArrayList<String>();
  private int cursor = -1;
  private String lastGroup;
  private long lastEdit;

  /** Records the document if it differs from the current state. Returns true when it did. */
  public boolean record(Document document, String group) {
    String json = serializer.toJsonFragment(document);
    if (cursor >= 0 && states.get(cursor).equals(json)) return false;
    boolean atEnd = cursor == states.size() - 1;
    while (states.size() > cursor + 1) states.remove(states.size() - 1);
    long now = System.nanoTime();
    if (group != null && group.equals(lastGroup) && atEnd && cursor > 0
        && now - lastEdit < GROUP_NANOS) {
      states.set(cursor, json);
    } else {
      states.add(json);
      cursor++;
    }
    if (states.size() > LIMIT + 1) {
      states.remove(0);
      cursor--;
    }
    lastGroup = group;
    lastEdit = now;
    return true;
  }

  public void endGroup() { lastGroup = null; }

  /** Starts again, so opening a different project cannot be undone into the previous one. */
  public void clear() {
    states.clear();
    cursor = -1;
    lastGroup = null;
  }

  public boolean canUndo() { return cursor > 0; }

  public boolean canRedo() { return cursor + 1 < states.size(); }

  public Document undo() {
    if (!canUndo()) return null;
    endGroup();
    return serializer.fromJson(states.get(--cursor));
  }

  public Document redo() {
    if (!canRedo()) return null;
    endGroup();
    return serializer.fromJson(states.get(++cursor));
  }

  public int size() { return states.size(); }
}

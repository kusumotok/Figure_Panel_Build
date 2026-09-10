package org.microscopy.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DocumentHistoryTest {
  private static Document withPanels() {
    Document document = Document.empty();
    Node root = Node.container("Root", 1, 2);
    root.add(Node.leaf("First", Content.of(TextContent.of("one"))), 0, 0);
    root.add(Node.leaf("Second", Content.of(TextContent.of("two"))), 0, 1);
    document.page(0).rootNode = root;
    document.validate();
    return document;
  }

  @Test
  void anUnchangedDocumentIsNotRecordedTwice() {
    DocumentHistory history = new DocumentHistory();
    Document document = withPanels();
    assertTrue(history.record(document, null));
    assertFalse(history.record(document, null));
    assertEquals(1, history.size());
    assertFalse(history.canUndo(), "there is nothing before the first state");
  }

  @Test
  void deletingIsUndoneAndRedone() {
    DocumentHistory history = new DocumentHistory();
    Document document = withPanels();
    history.record(document, null);
    DocumentEdits.remove(document.page(0), document.page(0).rootNode.children.get(1).id);
    history.record(document, null);
    assertEquals(1, document.page(0).rootNode.children.size());

    Document undone = history.undo();
    assertEquals(2, undone.page(0).rootNode.children.size(), "the panel comes back");
    Document redone = history.redo();
    assertEquals(1, redone.page(0).rootNode.children.size());
  }

  @Test
  void aNewEditAfterUndoDropsTheRedoBranch() {
    DocumentHistory history = new DocumentHistory();
    Document document = withPanels();
    history.record(document, null);
    document.page(0).rootNode.name = "Renamed";
    history.record(document, null);
    Document undone = history.undo();
    assertTrue(history.canRedo());

    undone.page(0).rootNode.name = "Another name";
    history.record(undone, null);
    assertFalse(history.canRedo(), "the abandoned branch is gone");
    assertEquals("Root", history.undo().page(0).rootNode.name);
  }

  @Test
  void editsOfTheSameKindInQuickSuccessionFoldTogether() {
    DocumentHistory history = new DocumentHistory();
    Document document = withPanels();
    history.record(document, null);
    document.page(0).rootNode.name = "A";
    history.record(document, "rename");
    int after = history.size();
    document.page(0).rootNode.name = "B";
    history.record(document, "rename");
    assertEquals(after, history.size(), "a run of the same edit is one undo step");
    assertEquals("Root", history.undo().page(0).rootNode.name);
  }

  @Test
  void undoStopsAtTheOldestStateItKept() {
    DocumentHistory history = new DocumentHistory();
    Document document = withPanels();
    history.record(document, null);
    assertNull(history.undo());
  }
}

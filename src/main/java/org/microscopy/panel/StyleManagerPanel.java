package org.microscopy.panel;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;

/**
 * The style list. Styles are dragged onto the canvas or applied to the selection; there is no
 * separate list for text, panel or figure styles because they are all the same thing.
 */
public class StyleManagerPanel extends JPanel {
  /** Marks a drag as carrying a style id rather than arbitrary text. */
  public static final String PREFIX = "panel-style:";

  public interface Listener {
    String selectedNodeId();

    void documentChanged();
  }

  private final Listener listener;
  private final DefaultListModel<String> model = new DefaultListModel<String>();
  private final JList<String> list = new JList<String>(model);
  private Document document;

  public StyleManagerPanel(Listener listener) {
    this.listener = listener;
    setLayout(new BorderLayout(0, 4));
    setBorder(BorderFactory.createTitledBorder("Styles"));
    setPreferredSize(new Dimension(210, 260));
    list.setName("styleList");
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setDragEnabled(true);
    list.setTransferHandler(new TransferHandler() {
      public int getSourceActions(JComponent component) { return COPY; }

      protected Transferable createTransferable(JComponent component) {
        String id = selectedStyleId();
        return id == null ? null : new StringSelection(PREFIX + id);
      }
    });
    add(new JScrollPane(list), BorderLayout.CENTER);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    button(buttons, "New", "newStyle", new Runnable() {
      public void run() { create(); }
    });
    button(buttons, "Apply", "applyStyle", new Runnable() {
      public void run() { applyToSelection(); }
    });
    button(buttons, "Clear", "clearStyle", new Runnable() {
      public void run() { clearFromSelection(); }
    });
    add(buttons, BorderLayout.SOUTH);
  }

  private void button(JPanel parent, String title, String name, final Runnable action) {
    JButton button = new JButton(title);
    button.setName(name);
    button.setFocusPainted(false);
    button.addActionListener(event -> action.run());
    parent.add(button);
  }

  public void show(Document document) {
    this.document = document;
    String selected = selectedStyleId();
    model.clear();
    if (document != null)
      for (Style style : document.styles.values())
        model.addElement(style.id + "  " + (style.name == null ? "" : style.name));
    if (selected != null) select(selected);
  }

  public String selectedStyleId() {
    String entry = list.getSelectedValue();
    if (entry == null) return null;
    int space = entry.indexOf("  ");
    return space < 0 ? entry : entry.substring(0, space);
  }

  private void select(String styleId) {
    for (int i = 0; i < model.size(); i++)
      if (model.get(i).startsWith(styleId + "  ")) {
        list.setSelectedIndex(i);
        return;
      }
  }

  private void create() {
    if (document == null) return;
    String name = JOptionPane.showInputDialog(this, "Name for the new style:", "New style",
        JOptionPane.QUESTION_MESSAGE);
    if (name == null || name.trim().isEmpty()) return;
    Style style = new Style(UUID.randomUUID().toString().substring(0, 8), name.trim());
    document.styles.put(style.id, style);
    show(document);
    select(style.id);
    listener.documentChanged();
  }

  private void applyToSelection() {
    String styleId = selectedStyleId();
    String nodeId = listener.selectedNodeId();
    if (styleId == null || nodeId == null || document == null) return;
    apply(styleId, nodeId);
  }

  /** Applies a style to one node. Shared by the buttons and by a drop on the canvas. */
  public void apply(String styleId, String nodeId) {
    if (document == null || !document.styles.containsKey(styleId)) return;
    Node node = document.page(0).rootNode.find(nodeId);
    if (node == null) return;
    node.appearance.styleId = styleId;
    listener.documentChanged();
  }

  private void clearFromSelection() {
    String nodeId = listener.selectedNodeId();
    if (nodeId == null || document == null) return;
    Node node = document.page(0).rootNode.find(nodeId);
    if (node == null || node.appearance.styleId == null) return;
    node.appearance.styleId = null;
    listener.documentChanged();
  }
}

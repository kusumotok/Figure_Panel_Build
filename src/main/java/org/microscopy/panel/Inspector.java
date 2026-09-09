package org.microscopy.panel;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/**
 * The same four sections for every selection: Layout, Appearance, Typography, Content. Sections
 * that do not apply are hidden rather than replaced by a different panel, so there is never a
 * separate settings screen for a poster, a panel or a figure.
 */
public class Inspector extends JPanel {
  public interface Listener {
    void documentChanged();
  }

  private static final String[] SIZE_KINDS =
      {"Auto", "Fill", "Fixed", "Percent", "Same as", "Aspect"};

  private final Listener listener;
  private final JPanel layoutSection = section("Layout");
  private final JPanel appearanceSection = section("Appearance");
  private final JPanel typographySection = section("Typography");
  private final JPanel contentSection = section("Content");
  private Document document;
  private Page page;
  private Node node;
  private boolean loading;
  private boolean advanced;

  public Inspector(Listener listener) {
    this.listener = listener;
    setLayout(new GridBagLayout());
    setPreferredSize(new Dimension(290, 600));
    GridBagConstraints k = new GridBagConstraints();
    k.gridx = 0;
    k.fill = GridBagConstraints.HORIZONTAL;
    k.weightx = 1;
    k.insets = new Insets(6, 8, 0, 8);
    k.gridy = 0;
    add(layoutSection, k);
    k.gridy = 1;
    add(appearanceSection, k);
    k.gridy = 2;
    add(typographySection, k);
    k.gridy = 3;
    add(contentSection, k);
    k.gridy = 4;
    k.weighty = 1;
    add(Box.createGlue(), k);
    show(null, null, null);
  }

  private static JPanel section(String title) {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder(title));
    return panel;
  }

  public void show(Document document, Page page, Node node) {
    this.document = document;
    this.page = page;
    this.node = node;
    loading = true;
    try {
      rebuild();
    } finally {
      loading = false;
    }
    revalidate();
    repaint();
  }

  private void changed() {
    if (!loading) listener.documentChanged();
  }

  private void rebuild() {
    layoutSection.removeAll();
    appearanceSection.removeAll();
    typographySection.removeAll();
    contentSection.removeAll();
    boolean any = node != null;
    layoutSection.setVisible(any);
    appearanceSection.setVisible(any);
    typographySection.setVisible(any && node.content.kind == Content.Kind.TEXT);
    contentSection.setVisible(any);
    if (!any) return;
    buildLayout();
    buildAppearance();
    if (node.content.kind == Content.Kind.TEXT) buildTypography();
    buildContent();
  }

  private void buildLayout() {
    Rows rows = new Rows(layoutSection);
    rows.add("Width", sizeEditor(node.size.width, true));
    rows.add("Height", sizeEditor(node.size.height, false));
    if (advanced) {
      rows.add("Min width", clampField(node.size.width, true));
      rows.add("Max width", clampField(node.size.width, false));
      rows.add("Min height", clampField(node.size.height, true));
      rows.add("Max height", clampField(node.size.height, false));
    }
    if (!node.children.isEmpty()) {
      rows.add("Column gap", number(node.layout.columnGapMm, new DoubleSetter() {
        public void set(double value) { node.layout.columnGapMm = Math.max(0, value); }
      }));
      rows.add("Row gap", number(node.layout.rowGapMm, new DoubleSetter() {
        public void set(double value) { node.layout.rowGapMm = Math.max(0, value); }
      }));
      rows.add("Mode", mode());
      rows.add("Align", align());
    }
    rows.add("Padding", number(padding(), new DoubleSetter() {
      public void set(double value) {
        if (value > 0) node.appearance.set(Prop.PADDING_MM, PropertyValue.of(value));
        else node.appearance.overrides.remove(Prop.PADDING_MM);
      }
    }));
    JCheckBox toggle = new JCheckBox("Advanced", advanced);
    toggle.setName("advancedToggle");
    toggle.addActionListener(event -> {
      advanced = toggle.isSelected();
      show(document, page, node);
    });
    rows.add("", toggle);
  }

  /** One control for the size kind plus one for whatever that kind needs. */
  private JPanel sizeEditor(final SizeExpr expr, final boolean widthAxis) {
    JPanel row = new JPanel(new GridBagLayout());
    GridBagConstraints k = new GridBagConstraints();
    k.fill = GridBagConstraints.HORIZONTAL;
    k.weightx = 1;
    final JComboBox<String> kind = new JComboBox<String>(SIZE_KINDS);
    kind.setName(widthAxis ? "widthKind" : "heightKind");
    kind.setSelectedIndex(kindIndex(expr.kind));
    row.add(kind, k);
    Component detail = sizeDetail(expr, widthAxis);
    if (detail != null) {
      k.gridx = 1;
      row.add(detail, k);
    }
    kind.addActionListener(event -> {
      if (loading) return;
      applyKind(expr, widthAxis, kind.getSelectedIndex());
      show(document, page, node);
      changed();
    });
    return row;
  }

  private Component sizeDetail(final SizeExpr expr, boolean widthAxis) {
    if (expr.kind == SizeExpr.Kind.FIXED || expr.kind == SizeExpr.Kind.PERCENTAGE
        || expr.kind == SizeExpr.Kind.FRACTION || expr.kind == SizeExpr.Kind.ASPECT_RATIO)
      return number(expr.value, new DoubleSetter() {
        public void set(double value) { if (value > 0) expr.value = value; }
      });
    if (expr.kind != SizeExpr.Kind.SAME_AS) return null;
    List<Node> siblings = siblings();
    final List<String> ids = new ArrayList<String>();
    List<String> names = new ArrayList<String>();
    for (Node sibling : siblings) {
      if (sibling == node) continue;
      ids.add(sibling.id);
      names.add(sibling.name.isEmpty() ? sibling.id : sibling.name);
    }
    if (ids.isEmpty()) return new JLabel("no siblings");
    final JComboBox<String> picker = new JComboBox<String>(names.toArray(new String[0]));
    int current = ids.indexOf(expr.siblingId);
    if (current >= 0) picker.setSelectedIndex(current);
    else expr.siblingId = ids.get(0);
    picker.addActionListener(event -> {
      if (loading) return;
      expr.siblingId = ids.get(picker.getSelectedIndex());
      changed();
    });
    return picker;
  }

  private void applyKind(SizeExpr expr, boolean widthAxis, int index) {
    Double min = expr.minMm, max = expr.maxMm;
    SizeExpr.Kind kind = SizeExpr.Kind.values()[0];
    switch (index) {
      case 0: kind = SizeExpr.Kind.AUTO; break;
      case 1: kind = SizeExpr.Kind.FRACTION; break;
      case 2: kind = SizeExpr.Kind.FIXED; break;
      case 3: kind = SizeExpr.Kind.PERCENTAGE; break;
      case 4: kind = SizeExpr.Kind.SAME_AS; break;
      default: kind = SizeExpr.Kind.ASPECT_RATIO; break;
    }
    expr.kind = kind;
    expr.minMm = min;
    expr.maxMm = max;
    if (kind == SizeExpr.Kind.FRACTION && !(expr.value > 0)) expr.value = 1;
    if (kind == SizeExpr.Kind.FIXED && !(expr.value > 0)) expr.value = 40;
    if (kind == SizeExpr.Kind.PERCENTAGE && !(expr.value > 0)) expr.value = 100;
    if (kind == SizeExpr.Kind.ASPECT_RATIO && !(expr.value > 0)) expr.value = intrinsicRatio(widthAxis);
    if (kind == SizeExpr.Kind.SAME_AS) {
      List<Node> siblings = siblings();
      for (Node sibling : siblings)
        if (sibling != node) { expr.siblingId = sibling.id; break; }
      // Nothing to point at, so fall back rather than leaving an invalid document.
      if (expr.siblingId == null) expr.kind = SizeExpr.Kind.AUTO;
    }
    // Both edges as a ratio can never resolve; keep the other edge usable.
    if (kind == SizeExpr.Kind.ASPECT_RATIO) {
      SizeExpr other = widthAxis ? node.size.height : node.size.width;
      if (other.kind == SizeExpr.Kind.ASPECT_RATIO) other.kind = SizeExpr.Kind.FRACTION;
      if (other.kind == SizeExpr.Kind.FRACTION && !(other.value > 0)) other.value = 1;
    }
  }

  private double intrinsicRatio(boolean widthAxis) {
    if (node.content.kind == Content.Kind.SCIENTIFIC_IMAGE) {
      Asset asset = document.asset(node.content.scientificImage.assetId);
      double ratio = asset.sizeY / (double) asset.sizeX;
      return widthAxis ? 1 / ratio : ratio;
    }
    return 1;
  }

  private static int kindIndex(SizeExpr.Kind kind) {
    switch (kind) {
      case FRACTION: return 1;
      case FIXED: return 2;
      case PERCENTAGE: return 3;
      case SAME_AS: return 4;
      case ASPECT_RATIO: return 5;
      default: return 0;
    }
  }

  private List<Node> siblings() {
    Node parent = page == null ? null : page.rootNode.parentOf(node.id);
    return parent == null ? new ArrayList<Node>() : parent.children;
  }

  private void buildAppearance() {
    Rows rows = new Rows(appearanceSection);
    rows.add("Fill", colorButton(Prop.FILL));
    rows.add("Border", colorButton(Prop.BORDER_COLOR));
    rows.add("Border width", number(literal(Prop.BORDER_WIDTH_MM), new DoubleSetter() {
      public void set(double value) { setNumber(Prop.BORDER_WIDTH_MM, value); }
    }));
    rows.add("Corner radius", number(literal(Prop.CORNER_RADIUS_MM), new DoubleSetter() {
      public void set(double value) { setNumber(Prop.CORNER_RADIUS_MM, value); }
    }));
    final JCheckBox visible = new JCheckBox("Visible", visible());
    visible.addActionListener(event -> {
      if (visible.isSelected()) node.appearance.overrides.remove(Prop.VISIBLE);
      else node.appearance.set(Prop.VISIBLE, PropertyValue.of(false));
      changed();
    });
    rows.add("", visible);
  }

  private void buildTypography() {
    Rows rows = new Rows(typographySection);
    final TextContent text = node.content.text;
    final Run first = firstRun(text);
    if (first != null) {
      rows.add("Size (pt)", number(first.fontSizePt == null ? 12 : first.fontSizePt,
          new DoubleSetter() {
            public void set(double value) { if (value > 0) applyToRuns(text, value); }
          }));
      rows.add("Latin font", textField(first.latinFamily == null ? "" : first.latinFamily,
          new StringSetter() {
            public void set(String value) {
              for (Paragraph paragraph : text.paragraphs)
                for (Run run : paragraph.runs) run.latinFamily = value.isEmpty() ? null : value;
            }
          }));
      rows.add("CJK font", textField(first.eaFamily == null ? "" : first.eaFamily,
          new StringSetter() {
            public void set(String value) {
              for (Paragraph paragraph : text.paragraphs)
                for (Run run : paragraph.runs) run.eaFamily = value.isEmpty() ? null : value;
            }
          }));
      final JCheckBox bold = new JCheckBox("Bold", Boolean.TRUE.equals(first.bold));
      bold.addActionListener(event -> {
        for (Paragraph paragraph : text.paragraphs)
          for (Run run : paragraph.runs) run.bold = bold.isSelected() ? Boolean.TRUE : null;
        changed();
      });
      rows.add("", bold);
    }
    final JComboBox<String> align =
        new JComboBox<String>(new String[] {"Left", "Centre", "Right"});
    Align current = text.paragraphs.isEmpty() ? Align.START : text.paragraphs.get(0).align;
    align.setSelectedIndex(current == Align.CENTER ? 1 : current == Align.END ? 2 : 0);
    align.addActionListener(event -> {
      if (loading) return;
      Align chosen = align.getSelectedIndex() == 1 ? Align.CENTER
          : align.getSelectedIndex() == 2 ? Align.END : Align.START;
      for (Paragraph paragraph : text.paragraphs) paragraph.align = chosen;
      changed();
    });
    rows.add("Align", align);
    final JComboBox<String> overflow = new JComboBox<String>(
        new String[] {"Warn", "Auto shrink", "Expand region", "Clip"});
    overflow.setSelectedIndex(text.overflow.ordinal());
    overflow.addActionListener(event -> {
      if (loading) return;
      text.overflow = TextContent.Overflow.values()[overflow.getSelectedIndex()];
      changed();
    });
    rows.add("Overflow", overflow);
  }

  private void applyToRuns(TextContent text, double sizePt) {
    for (Paragraph paragraph : text.paragraphs)
      for (Run run : paragraph.runs) run.fontSizePt = sizePt;
  }

  private static Run firstRun(TextContent text) {
    for (Paragraph paragraph : text.paragraphs)
      if (!paragraph.runs.isEmpty()) return paragraph.runs.get(0);
    return null;
  }

  private void buildContent() {
    Rows rows = new Rows(contentSection);
    rows.add("Kind", new JLabel(String.valueOf(node.content.kind)));
    rows.add("Name", textField(node.name, new StringSetter() {
      public void set(String value) { node.name = value; }
    }));
    if (node.content.kind == Content.Kind.SCIENTIFIC_IMAGE) {
      ScientificImageContent content = node.content.scientificImage;
      Asset asset = document.asset(content.assetId);
      rows.add("Source", new JLabel(shorten(asset.originalUri)));
      rows.add("Pixels", new JLabel(asset.sizeX + " x " + asset.sizeY));
      rows.add("Z / T", new JLabel(content.z + " / " + content.t
          + "  (of " + asset.sizeZ + " / " + asset.sizeT + ")"));
      final JComboBox<String> resample =
          new JComboBox<String>(new String[] {"Nearest", "Area average"});
      resample.setSelectedIndex(content.resample.ordinal());
      resample.addActionListener(event -> {
        if (loading) return;
        content.resample = ScientificImageContent.Resample.values()[resample.getSelectedIndex()];
        changed();
      });
      rows.add("Resample", resample);
    }
    if (!node.children.isEmpty()) rows.add("Children", new JLabel(node.children.size() + ""));
  }

  private static String shorten(String path) {
    if (path == null) return "(none)";
    int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf(java.io.File.separatorChar));
    return slash < 0 ? path : path.substring(slash + 1);
  }

  // -------------------------------------------------------------- small parts

  private interface DoubleSetter {
    void set(double value);
  }

  private interface StringSetter {
    void set(String value);
  }

  /** Two column label/field stacker, so every section looks the same. */
  private static final class Rows {
    private final JPanel panel;
    private int row;

    Rows(JPanel panel) { this.panel = panel; }

    void add(String label, Component field) {
      GridBagConstraints k = new GridBagConstraints();
      k.gridy = row++;
      k.insets = new Insets(2, 2, 2, 2);
      k.gridx = 0;
      k.anchor = GridBagConstraints.WEST;
      JLabel caption = new JLabel(label, SwingConstants.LEFT);
      caption.setPreferredSize(new Dimension(88, 20));
      panel.add(caption, k);
      k.gridx = 1;
      k.fill = GridBagConstraints.HORIZONTAL;
      k.weightx = 1;
      panel.add(field, k);
    }
  }

  private double padding() {
    return literal(Prop.PADDING_MM);
  }

  private double literal(String key) {
    PropertyValue value = node.appearance.overrides.get(key);
    return value == null || value.kind != PropertyValue.Kind.LITERAL ? 0 : value.asDouble();
  }

  private boolean visible() {
    PropertyValue value = node.appearance.overrides.get(Prop.VISIBLE);
    return value == null || value.kind != PropertyValue.Kind.LITERAL || value.asBoolean();
  }

  private void setNumber(String key, double value) {
    if (value > 0) node.appearance.set(key, PropertyValue.of(value));
    else node.appearance.overrides.remove(key);
  }

  private JTextField number(double value, final DoubleSetter setter) {
    final JTextField field = new JTextField(trim(value), 6);
    field.addActionListener(event -> commit(field, setter));
    field.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent event) { commit(field, setter); }
    });
    return field;
  }

  private void commit(JTextField field, DoubleSetter setter) {
    if (loading) return;
    try {
      setter.set(Double.parseDouble(field.getText().trim()));
      changed();
    } catch (NumberFormatException ex) {
      // Leave the model alone and let the field show what it had.
      changed();
    }
  }

  private static String trim(double value) {
    return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
  }

  private JTextField textField(String value, final StringSetter setter) {
    final JTextField field = new JTextField(value, 10);
    field.addActionListener(event -> commitText(field, setter));
    field.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent event) { commitText(field, setter); }
    });
    return field;
  }

  private void commitText(JTextField field, StringSetter setter) {
    if (loading) return;
    setter.set(field.getText());
    changed();
  }

  private JTextField clampField(final SizeExpr expr, final boolean minimum) {
    Double current = minimum ? expr.minMm : expr.maxMm;
    return number(current == null ? 0 : current, new DoubleSetter() {
      public void set(double value) {
        Double set = value > 0 ? Double.valueOf(value) : null;
        if (minimum) expr.minMm = set;
        else expr.maxMm = set;
      }
    });
  }

  private JComboBox<String> mode() {
    final JComboBox<String> mode = new JComboBox<String>(new String[] {"Grid", "Flow", "Stack"});
    mode.setSelectedIndex(node.layout.mode.ordinal());
    mode.addActionListener(event -> {
      if (loading) return;
      node.layout.mode = LayoutSpec.Mode.values()[mode.getSelectedIndex()];
      changed();
    });
    return mode;
  }

  private JComboBox<String> align() {
    final JComboBox<String> align =
        new JComboBox<String>(new String[] {"Stretch", "Start", "Centre", "End"});
    align.setSelectedIndex(node.layout.alignX == Align.STRETCH ? 0
        : node.layout.alignX == Align.START ? 1 : node.layout.alignX == Align.CENTER ? 2 : 3);
    align.addActionListener(event -> {
      if (loading) return;
      Align chosen = align.getSelectedIndex() == 0 ? Align.STRETCH
          : align.getSelectedIndex() == 1 ? Align.START
              : align.getSelectedIndex() == 2 ? Align.CENTER : Align.END;
      node.layout.alignX = chosen;
      node.layout.alignY = chosen;
      changed();
    });
    return align;
  }

  private JButton colorButton(final String key) {
    PropertyValue value = node.appearance.overrides.get(key);
    final Color current =
        value != null && value.kind == PropertyValue.Kind.LITERAL ? value.asColor() : null;
    final JButton button = new JButton(current == null ? "none" : PropertyValue.hex(current));
    if (current != null) button.setBackground(current);
    button.addActionListener(event -> {
      Color chosen = JColorChooser.showDialog(Inspector.this, "Choose a colour",
          current == null ? Color.WHITE : current);
      if (chosen == null) node.appearance.overrides.remove(key);
      else node.appearance.set(key, PropertyValue.of(chosen));
      show(document, page, node);
      changed();
    });
    return button;
  }
}

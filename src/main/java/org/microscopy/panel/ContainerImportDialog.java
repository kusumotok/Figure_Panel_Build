package org.microscopy.panel;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;

/**
 * Chooses which series of a container to import and how to group them.
 *
 * <p>Grouping is offered rather than guessed: whether consecutive series are channels of one
 * field or separate fields is a property of how the microscope was driven, not of the file.
 */
public class ContainerImportDialog extends JDialog {
  private final ContainerImport.Plan plan;
  private final SeriesModel model;
  private final JLabel summary = new JLabel();
  private final JSpinner perImage;
  private final JTextField skipText = new JTextField("x", 6);
  private final JTextField labels = new JTextField("Green, Red, Blue", 18);
  private boolean confirmed;

  public ContainerImportDialog(Frame owner, ContainerImport.Plan plan) {
    super(owner, "Import from " + plan.file.getName(), true);
    this.plan = plan;
    this.model = new SeriesModel();
    this.perImage = new JSpinner(new SpinnerNumberModel(1, 1, 16, 1));

    JTable table = new JTable(model);
    table.setName("seriesTable");
    table.getColumnModel().getColumn(0).setMaxWidth(60);
    table.getColumnModel().getColumn(2).setPreferredWidth(150);
    table.setRowHeight(20);

    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    controls.add(new JLabel("Skip names containing"));
    controls.add(skipText);
    JButton skip = new JButton("Apply");
    skip.setName("applySkip");
    skip.addActionListener(event -> {
      plan.skipNamesContaining(skipText.getText().trim());
      model.fireTableDataChanged();
      refresh();
    });
    controls.add(skip);
    controls.add(new JLabel("   Series per image"));
    perImage.setName("seriesPerImage");
    perImage.addChangeListener(event -> { refresh(); model.fireTableDataChanged(); });
    controls.add(perImage);
    controls.add(new JLabel("   Channel names"));
    controls.add(labels);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton cancel = new JButton("Cancel");
    cancel.addActionListener(event -> dispose());
    JButton ok = new JButton("Import");
    ok.setName("importSeries");
    ok.addActionListener(event -> {
      apply();
      if (plan.remainder() != 0) {
        summary.setText(summaryText() + "   Adjust the grouping before importing.");
        return;
      }
      confirmed = true;
      dispose();
    });
    buttons.add(cancel);
    buttons.add(ok);
    getRootPane().setDefaultButton(ok);

    summary.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
    setLayout(new BorderLayout());
    add(controls, BorderLayout.NORTH);
    add(new JScrollPane(table), BorderLayout.CENTER);
    JPanel south = new JPanel(new BorderLayout());
    south.add(summary, BorderLayout.WEST);
    south.add(buttons, BorderLayout.EAST);
    add(south, BorderLayout.SOUTH);
    refresh();
    setSize(680, 460);
    setLocationRelativeTo(owner);
  }

  private void apply() {
    plan.channelsPerImage = (Integer) perImage.getValue();
    plan.channelLabels.clear();
    for (String label : labels.getText().split(",")) {
      String trimmed = label.trim();
      if (!trimmed.isEmpty()) plan.channelLabels.add(trimmed);
    }
  }

  private void refresh() {
    apply();
    summary.setText(summaryText());
  }

  private String summaryText() {
    int left = plan.remainder();
    String text = plan.kept().size() + " of " + plan.series.size() + " series kept, "
        + plan.imageCount() + " image(s) of " + plan.channelsPerImage + " channel(s)";
    return left == 0 ? text + "." : text + ", " + left + " left over.";
  }

  public boolean confirmed() { return confirmed; }

  public ContainerImport.Plan plan() { return plan; }

  private final class SeriesModel extends AbstractTableModel {
    private final String[] columns = {"Import", "Series", "Becomes", "Size", "C / Z / T"};

    public int getRowCount() { return plan.series.size(); }

    public int getColumnCount() { return columns.length; }

    public String getColumnName(int column) { return columns[column]; }

    public Class<?> getColumnClass(int column) { return column == 0 ? Boolean.class : String.class; }

    public boolean isCellEditable(int row, int column) { return column == 0; }

    public Object getValueAt(int row, int column) {
      BioFormatsReader.Series series = plan.series.get(row);
      switch (column) {
        case 0:
          return Boolean.valueOf(!plan.skipped.contains(Integer.valueOf(series.index)));
        case 1:
          return series.name;
        case 2:
          String[] grouping = ContainerImport.grouping(plan);
          return grouping[row] == null ? "skipped" : grouping[row];
        case 3:
          return series.sizeX + " x " + series.sizeY + "  " + series.bitDepth + "-bit";
        default:
          return series.sizeC + " / " + series.sizeZ + " / " + series.sizeT;
      }
    }

    public void setValueAt(Object value, int row, int column) {
      if (column != 0) return;
      Integer index = Integer.valueOf(plan.series.get(row).index);
      if (Boolean.TRUE.equals(value)) plan.skipped.remove(index);
      else if (!plan.skipped.contains(index)) plan.skipped.add(index);
      fireTableDataChanged();
      refresh();
    }
  }

  /** Convenience used by the poster window. */
  public static ContainerImport.Plan choose(Frame owner, File file) {
    ContainerImport.Plan plan = new ContainerImport().plan(file);
    ContainerImportDialog dialog = new ContainerImportDialog(owner, plan);
    dialog.setVisible(true);
    return dialog.confirmed() ? dialog.plan() : null;
  }
}

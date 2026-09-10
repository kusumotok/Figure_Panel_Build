package org.microscopy.panel;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * Choosing an output. Resolution is the decision that matters, so the dialog shows what each
 * choice costs in pixels and which pictures fall short before anything is written.
 */
public class ExportDialog extends JDialog {
  public enum Format { PNG, TIFF }

  private final JComboBox<String> format =
      new JComboBox<String>(new String[] {"PNG (banded, any size)", "TIFF (one piece)"});
  private final JComboBox<String> background =
      new JComboBox<String>(new String[] {"White", "Black", "Transparent"});
  private final JTextField dpi = new JTextField("300", 6);
  private final JLabel size = new JLabel();
  private final JTextArea resolutions = new JTextArea(8, 44);
  private final LayoutResult layout;
  private final ResolutionReport report;
  private boolean confirmed;

  public ExportDialog(Frame owner, Document document, Page page, LayoutResult layout) {
    super(owner, "Export", true);
    this.layout = layout;
    this.report = ResolutionReport.of(document, page, layout);

    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    controls.add(new JLabel("Format"));
    format.setName("exportFormat");
    controls.add(format);
    controls.add(new JLabel("   Resolution (dpi)"));
    dpi.setName("exportDpi");
    controls.add(dpi);
    controls.add(new JLabel("   Background"));
    controls.add(background);

    dpi.addCaretListener(event -> refresh());
    format.addActionListener(event -> refresh());

    resolutions.setEditable(false);
    resolutions.setName("resolutionTable");
    size.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton cancel = new JButton("Cancel");
    cancel.addActionListener(event -> dispose());
    JButton ok = new JButton("Export");
    ok.setName("confirmExport");
    ok.addActionListener(event -> {
      if (dpiValue() <= 0) return;
      confirmed = true;
      dispose();
    });
    buttons.add(cancel);
    buttons.add(ok);
    getRootPane().setDefaultButton(ok);

    setLayout(new BorderLayout());
    add(controls, BorderLayout.NORTH);
    add(new JScrollPane(resolutions), BorderLayout.CENTER);
    JPanel south = new JPanel(new BorderLayout());
    south.add(size, BorderLayout.WEST);
    south.add(buttons, BorderLayout.EAST);
    add(south, BorderLayout.SOUTH);
    refresh();
    setSize(new Dimension(620, 380));
    setLocationRelativeTo(owner);
  }

  private void refresh() {
    double value = dpiValue();
    if (value <= 0) {
      size.setText("Enter a resolution.");
      return;
    }
    int width = Units.mmToPx(layout.pageBox().width, value);
    int height = Units.mmToPx(layout.pageBox().height, value);
    long pixels = (long) width * height;
    StringBuilder text = new StringBuilder(String.format("%d x %d px, %.0f megapixels.",
        width, height, pixels / 1e6));
    if (format() == Format.TIFF && pixels > DocumentRasterizer.TIFF_PIXEL_LIMIT)
      text.append("  TIFF is written in one piece and this is too large; choose PNG.");
    size.setText(text.toString());

    StringBuilder table = new StringBuilder(report.summary()).append("\n\n");
    for (ResolutionReport.Entry entry : report.entries())
      table.append(entry.level == ResolutionReport.Level.OK ? "     " : "  !  ")
          .append(entry).append('\n');
    resolutions.setText(table.toString());
    resolutions.setCaretPosition(0);
  }

  private double dpiValue() {
    try {
      double value = Double.parseDouble(dpi.getText().trim());
      return value > 0 && value <= 4800 ? value : 0;
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  public boolean confirmed() { return confirmed; }

  public Format format() { return format.getSelectedIndex() == 1 ? Format.TIFF : Format.PNG; }

  public double dpi() { return dpiValue(); }

  public RenderTarget target() {
    RenderTarget.Background chosen = background.getSelectedIndex() == 1
        ? RenderTarget.Background.BLACK
        : background.getSelectedIndex() == 2
            ? RenderTarget.Background.TRANSPARENT : RenderTarget.Background.WHITE;
    return new RenderTarget(dpiValue(), chosen);
  }
}

package org.microscopy.panel;

import javax.swing.SwingUtilities;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

/** Separate entry point: the existing Figure Panel Builder menu item is untouched. */
@Plugin(type = Command.class, menuPath = "Plugins>Poster / Layout Builder")
public class PosterCommand implements Command {
  public void run() {
    SwingUtilities.invokeLater(() -> new PosterFrame().setVisible(true));
  }
}

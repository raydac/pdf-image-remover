package com.igormaznitsa.pdfimgremover;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Application {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            new MainFrame().setVisible(true);
        });
    }
}

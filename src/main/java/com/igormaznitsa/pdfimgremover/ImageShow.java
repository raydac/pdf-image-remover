/*
 * Copyright (C) 2022 Igor Maznitsa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.igormaznitsa.pdfimgremover;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

public class ImageShow extends JPanel {

    private static File lastSavedFile = null;
    private final BufferedImage loadedImage;
    private final ScalableImage scalableImage;

    public ImageShow(final PageTreeModel.PageItem item) {
        initComponents();
        this.scalableImage = new ScalableImage();
        this.labelTop.setText(String.format("Image: %s, %dx%d", item.name.getName(), item.pdImage.getWidth(), item.pdImage.getHeight()));
        BufferedImage image = null;
        try {
            image = item.pdImage.getImage();
            this.mainScroll.setViewportView(this.scalableImage);
        } catch (Exception ex) {
            ex.printStackTrace();
            this.mainScroll.setViewportView(new JLabel("ERROR!!!"));
        }
        this.loadedImage = image;
        this.scalableImage.setImage(this.loadedImage, true);
        this.buttonSaveAs.setEnabled(this.loadedImage != null);
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        mainScroll = new javax.swing.JScrollPane();
        topPanel = new javax.swing.JPanel();
        labelTop = new javax.swing.JLabel();
        buttonSaveAs = new javax.swing.JButton();
        topPanelMidleFiller = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));

        setLayout(new java.awt.BorderLayout());
        add(mainScroll, java.awt.BorderLayout.CENTER);

        topPanel.setLayout(new java.awt.GridBagLayout());

        labelTop.setText(".............");
        topPanel.add(labelTop, new java.awt.GridBagConstraints());

        buttonSaveAs.setText("Save as...");
        buttonSaveAs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonSaveAsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        topPanel.add(buttonSaveAs, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 1000.0;
        topPanel.add(topPanelMidleFiller, gridBagConstraints);

        add(topPanel, java.awt.BorderLayout.PAGE_START);
    }// </editor-fold>//GEN-END:initComponents


    private void buttonSaveAsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonSaveAsActionPerformed
        final JFileChooser fileChooser = new JFileChooser(lastSavedFile);
        fileChooser.setFileFilter(MainFrame.FILEFILTER_PNG);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setDialogTitle("Export image");

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File targetFile = fileChooser.getSelectedFile();
            lastSavedFile = targetFile;

            if (!targetFile.getName().contains(".")) {
                targetFile = new File(targetFile.getParentFile(), targetFile.getName() + ".png");
            }

            if (targetFile.exists() && JOptionPane.showConfirmDialog(this, "Override file " + targetFile.getName() + "?", "File exists", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.CANCEL_OPTION) {
                return;
            }
            try {
                ImageIO.write(this.loadedImage, "png", targetFile);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error during file save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_buttonSaveAsActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton buttonSaveAs;
    private javax.swing.JLabel labelTop;
    private javax.swing.JScrollPane mainScroll;
    private javax.swing.JPanel topPanel;
    private javax.swing.Box.Filler topPanelMidleFiller;
    // End of variables declaration//GEN-END:variables
}

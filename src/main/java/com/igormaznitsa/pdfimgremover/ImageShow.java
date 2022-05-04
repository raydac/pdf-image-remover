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
import javax.swing.ImageIcon;
import javax.swing.JLabel;

public class ImageShow extends javax.swing.JPanel {

    public ImageShow(final PageTreeModel.PageItem item) {
        initComponents();
        this.labelTop.setText(String.format("Image: %s, %dx%d", item.name.getName(), item.pdImage.getWidth(), item.pdImage.getHeight()));
        try {
            final BufferedImage image = item.pdImage.getImage();
            this.mainScroll.setViewportView(new JLabel(new ImageIcon(image)));
        }catch(Exception ex){
            ex.printStackTrace();
            this.mainScroll.setViewportView(new JLabel("ERROR!!!"));
        }
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        labelTop = new javax.swing.JLabel();
        mainScroll = new javax.swing.JScrollPane();

        setLayout(new java.awt.BorderLayout());

        labelTop.setText(".............");
        add(labelTop, java.awt.BorderLayout.PAGE_START);
        add(mainScroll, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel labelTop;
    private javax.swing.JScrollPane mainScroll;
    // End of variables declaration//GEN-END:variables
}

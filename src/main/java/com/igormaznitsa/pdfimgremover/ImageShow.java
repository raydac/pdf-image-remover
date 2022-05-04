package com.igormaznitsa.pdfimgremover;

import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

public class ImageShow extends javax.swing.JPanel {

    public ImageShow(final PageTreeModel.PageItem item) {
        initComponents();
        this.labelTop.setText(String.format("Image: %s, %dX%d", item.name, item.pdImage.getWidth(), item.pdImage.getHeight()));
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

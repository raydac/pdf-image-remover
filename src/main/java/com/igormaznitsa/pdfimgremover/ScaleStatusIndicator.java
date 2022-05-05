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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.JLabel;

public class ScaleStatusIndicator extends JLabel {

    private final AtomicReference<Scalable> observableObject = new AtomicReference<>();
    private final ActionListener scaleListener = (ActionEvent e) -> {
        updateTextForScale();
    };

    public void setScalable(final Scalable scalable) {
        var prev = this.observableObject.getAndSet(scalable);
        if (prev != null) {
            prev.removeScaleListener(scaleListener);
        }
        if (scalable != null) {
            scalable.addScaleListener(scaleListener);
        }
    }

    public Scalable getScalable() {
        return this.observableObject.get();
    }

    public ScaleStatusIndicator() {
        super();
        this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        this.setToolTipText("Reset scale");
        this.setBackground(new Color(0xf7ffc8));
        this.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        this.setForeground(Color.BLACK);
        this.setOpaque(false);

        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                var target = observableObject.get();
                if (target != null) {
                    target.doZoomReset();
                }
            }
        });

        updateTextForScale();
    }

    public void doZoomIn() {
        var target = observableObject.get();
        if (target != null) {
            target.doZoomIn();
        }
    }

    public void doZoomOut() {
        var target = observableObject.get();
        if (target != null) {
            target.doZoomOut();
        }
    }

    public void doZoomReset() {
        var target = observableObject.get();
        if (target != null) {
            target.doZoomReset();
        }
    }

    @Override
    public void paintComponent(final Graphics g) {
        final Dimension size = this.getSize();
        g.setColor(this.getBackground());
        size.width--;
        size.height--;
        final int radius = size.height / 2;
        g.fillRoundRect(0, 0, size.width, size.height, radius, radius);
        g.setColor(this.getBackground().darker().darker());
        g.drawRoundRect(0, 0, size.width, size.height, radius, radius);
        super.paintComponent(g);
    }

    private void updateTextForScale() {
        var target = observableObject.get();
        if (target == null) {
            this.setText("<html><b>...</b></html>");
        } else {
            final float scale = target.getScale();
            this.setText(
                    String.format("<html><b>&nbsp;Scale: %d%%&nbsp;</b></html>", Math.round(scale * 100.0f)));
        }
        this.repaint();
    }

    public interface Scalable {

        float getScale();

        void doZoomIn();

        void doZoomOut();

        void doZoomReset();

        void setScale(float scale);

        void addScaleListener(ActionListener scaleListener);

        void removeScaleListener(ActionListener scaleListener);
    }

}

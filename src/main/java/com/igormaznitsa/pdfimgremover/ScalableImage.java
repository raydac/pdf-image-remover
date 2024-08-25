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

import com.igormaznitsa.pdfimgremover.ScaleStatusIndicator.Scalable;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

final class ScalableImage extends JComponent implements Scalable {

    private static final float SCALE_STEP = 0.05f;
    private BufferedImage image;
    private float scale = 1.0f;

    public static final int IMG_UNIT_INCREMENT = 16;
    public static final int IMG_BLOCK_INCREMENT = IMG_UNIT_INCREMENT * 8;

    private Point dragOrigin;

    private static final float MIN_SCALE = 0.2f;
    private static final float MAX_SCALE = 10.0f;

    private final java.util.List<ActionListener> scalableListeners = new CopyOnWriteArrayList<>();

    public ScalableImage() {
        super();
        final ScalableImage theInstance = this;

        final MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseWheelMoved(final MouseWheelEvent e) {
                if (!e.isConsumed() && ((e.getModifiers() & MouseWheelEvent.CTRL_MASK) == MouseWheelEvent.CTRL_MASK)) {
                    e.consume();
                    final float oldScale = scale;
                    scale = Math.max(MIN_SCALE, Math.min(scale + (SCALE_STEP * -e.getWheelRotation()), MAX_SCALE));

                    final JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, theInstance);

                    final Dimension size = getPreferredSize();

                    if (viewport != null) {
                        final Dimension extentSize = viewport.getExtentSize();

                        final Rectangle viewPos = viewport.getViewRect();
                        if (extentSize.width < size.width || extentSize.height < size.height) {
                            final Point mousePoint = e.getPoint();
                            final int dx = mousePoint.x - viewPos.x;
                            final int dy = mousePoint.y - viewPos.y;

                            final double scaleRelation = scale / oldScale;

                            final int newMouseX = (int) (Math.round(mousePoint.x * scaleRelation));
                            final int newMouseY = (int) (Math.round(mousePoint.y * scaleRelation));

                            viewPos.x = Math.max(0, newMouseX - dx);
                            viewPos.y = Math.max(0, newMouseY - dy);
                            viewport.setView(ScalableImage.this);

                            scrollRectToVisible(viewPos);
                        } else {
                            viewPos.x = 0;
                            viewPos.y = 0;
                            scrollRectToVisible(viewPos);
                        }

                        final Container scrollPane = viewport.getParent();
                        if (scrollPane != null) {
                            scrollPane.revalidate();
                            scrollPane.repaint();
                        }
                    } else {
                        revalidate();
                        repaint();
                    }

                    fireScaleListeners();
                }
                if (!e.isConsumed()) {
                    sendEventToParent(e);
                }
            }

            @Override
            public void mouseClicked(final MouseEvent e) {
                sendEventToParent(e);
            }

            @Override
            public void mousePressed(final MouseEvent e) {
                if (!e.isConsumed()) {
                    e.consume();
                    dragOrigin = e.getPoint();
                }
                sendEventToParent(e);
            }

            @Override
            public void mouseReleased(final MouseEvent e) {
                if (!e.isConsumed()) {
                    e.consume();
                    dragOrigin = null;
                }
                sendEventToParent(e);
            }

            @Override
            public void mouseEntered(final MouseEvent e) {
                sendEventToParent(e);
            }

            @Override
            public void mouseExited(final MouseEvent e) {
                sendEventToParent(e);
            }

            @Override
            public void mouseDragged(final MouseEvent e) {
                if (!e.isConsumed() && dragOrigin != null) {
                    e.consume();
                    final JViewport viewPort = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, theInstance);
                    if (viewPort != null) {
                        int deltaX = dragOrigin.x - e.getX();
                        int deltaY = dragOrigin.y - e.getY();

                        final Rectangle view = viewPort.getViewRect();
                        view.x += deltaX;
                        view.y += deltaY;
                        scrollRectToVisible(view);

                        viewPort.revalidate();
                        viewPort.repaint();
                    }
                }
                if (!e.isConsumed()) {
                    sendEventToParent(e);
                }
            }

            @Override
            public void mouseMoved(final MouseEvent e) {
                sendEventToParent(e);
            }
        };
        this.addMouseWheelListener(adapter);
        this.addMouseListener(adapter);
        this.addMouseMotionListener(adapter);
    }

    private void fireScaleListeners() {
        final ActionEvent event = new ActionEvent(ScalableImage.this, 0, "scaled");
        for (final ActionListener a : scalableListeners) {
            a.actionPerformed(event);
        }
    }

    private void sendEventToParent(final MouseEvent e) {
        final Container parent = this.getParent();
        if (parent != null) {
            parent.dispatchEvent(e);
        }
    }

    @Override
    public void doZoomIn() {
        this.setScale(this.scale + SCALE_STEP);
    }

    @Override
    public void doZoomOut() {
        this.setScale(this.scale - SCALE_STEP);
    }

    @Override
    public void doZoomReset() {
        this.setScale(1.0f);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getPreferredSize() {
        if (image == null) {
            return new Dimension(320, 280);
        } else {
            return new Dimension(Math.round(this.image.getWidth() * this.scale), Math.round(this.image.getHeight() * this.scale));
        }
    }

    @Override
    public float getScale() {
        return this.scale;
    }

    @Override
    public void paintComponent(final Graphics g) {
        final Graphics2D gfx = (Graphics2D) g;

        final Rectangle bounds = this.getBounds();
        if (this.image == null) {
            gfx.setColor(Color.WHITE);
            gfx.fillRect(0, 0, bounds.width, bounds.height);
            gfx.setColor(Color.BLACK);
            final String text = "No document";
            gfx.drawString(text, (bounds.width - gfx.getFontMetrics().stringWidth(text)) / 2, (bounds.height - gfx.getFontMetrics().getMaxAscent()) / 2);
        } else {
            final Dimension size = getPreferredSize();
            gfx.drawImage(this.image, Math.max(0, (bounds.width - size.width) / 2), Math.max(0, (bounds.height - size.height) / 2), size.width, size.height, null);
        }
    }

    public BufferedImage getImage() {
        return this.image;
    }

    public void setImage(final BufferedImage image, final boolean resetZoom) {
        this.image = image;
        if (resetZoom) {
            setScale(1.0f);
        }
        invalidate();
        repaint();
    }

    @Override
    public void setScale(final float scale) {
        this.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        revalidate();
        repaint();
        fireScaleListeners();
    }

    @Override
    public void addScaleListener(final ActionListener scaleListener) {
        this.scalableListeners.add(scaleListener);
    }

    @Override
    public void removeScaleListener(final ActionListener scaleListener) {
        this.scalableListeners.remove(scaleListener);
    }

}

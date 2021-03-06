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
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

public class PageTreeModel implements TreeModel {

    public static class PageImageRenderer extends DefaultTreeCellRenderer {

        public PageImageRenderer() {
            super();
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            final Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof PageItem && result instanceof JLabel) {
                ((JLabel)result).setIcon(new ImageIcon(((PageItem)value).icon));
            }
            return result;
        }
    }
    
    public static class PageItem {

        public final COSName name;
        public final PDImageXObject pdImage;
        public final Image icon;

        private PageItem(COSName name, PDImageXObject pdImage) {
            this.name = name;
            this.pdImage = pdImage;
            this.icon = makeIcon(16, 16, this.pdImage);
        }

        private static Image makeIcon(final int width, final int height, final PDImageXObject pdImage) {
            final BufferedImage icon = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g = icon.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(pdImage.getImage(), 0, 0, width, height, null);
            } catch (Exception ex) {
                g.setColor(Color.RED);
                g.fillRect(0, 0, width, height);
            } finally {
                g.dispose();
            }
            return icon;
        }

        private boolean isLeaf() {
            return true;
        }

        @Override
        public String toString() {
            return String.format("%s (%dx%d)", (this.name == null ? "" : this.name.getName()), this.pdImage.getWidth(), this.pdImage.getHeight());
        }

        private int getChildCount() {
            return 0;
        }

        private int getIndexOfChild(Object child) {
            return -1;
        }

        private Object getChild(int i) {
            return null;
        }

    }

    private final PDPage page;
    private final List<TreeModelListener> listeners = new CopyOnWriteArrayList<>();
    private final List<PageItem> pageItems;

    public PageTreeModel(final PDDocument document, final PDPage page) {
        this.page = page;
        if (this.page == null) {
            this.pageItems = List.of();
        } else {
            this.pageItems = new ArrayList<>();
            try {
                final Map<COSName, ImageFinderStreamEngine.FoundImage> images = new ImageFinderStreamEngine().findImages(page);
                images.entrySet().stream().map(x -> x.getValue()).sorted().forEach(i -> {
                    this.pageItems.add(new PageItem(i.name, i.image));
                });
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public Object getRoot() {
        return this;
    }

    @Override
    public Object getChild(Object o, int i) {
        if (o == this) {
            return this.pageItems.get(i);
        } else {
            return ((PageItem) o).getChild(i);
        }
    }

    @Override
    public int getChildCount(Object o) {
        if (this == o) {
            return this.pageItems.size();
        } else {
            return ((PageItem) o).getChildCount();
        }
    }

    @Override
    public boolean isLeaf(Object o) {
        return o != this && ((PageItem) o).isLeaf();
    }

    @Override
    public void valueForPathChanged(TreePath tp, Object o) {
    }

    @Override
    public int getIndexOfChild(final Object parent, final Object child) {
        if (parent == this) {
            return this.pageItems.indexOf(child);
        } else {
            return ((PageItem) parent).getIndexOfChild(child);
        }
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        this.listeners.add(l);
    }

    @Override
    public void removeTreeModelListener(final TreeModelListener l) {
        this.listeners.remove(l);
    }

    @Override
    public String toString() {
        return "Page";
    }

}

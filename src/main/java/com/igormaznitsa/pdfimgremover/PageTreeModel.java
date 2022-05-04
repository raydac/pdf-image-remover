package com.igormaznitsa.pdfimgremover;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

public class PageTreeModel implements TreeModel {

    public static class PageItem {

        public final COSName name;
        public final PDImageXObject pdImage;
        
        private PageItem(COSName name, PDImageXObject pdImage) {
            this.name = name;
            this.pdImage = pdImage;
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

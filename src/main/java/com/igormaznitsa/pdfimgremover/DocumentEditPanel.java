/*
 * Copyright 2025 igormaznitsa.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.igormaznitsa.pdfimgremover;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.swing.AbstractListModel;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import static javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;
import javax.swing.TransferHandler;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;

public class DocumentEditPanel extends javax.swing.JPanel {

    private static final class PdfPageListModel extends AbstractListModel<PdfPageItem> {

        private final List<PdfPageItem> list = new ArrayList<>();
        private final boolean sorted;
        
        public PdfPageListModel(final boolean sorted) {
            this.sorted = sorted;
        }

        public Stream<PdfPageItem> stream() {
            return this.list.stream();
        }

        public boolean moveUp(final int index) {
            if (index <= 0) {
                return false;
            }
            final PdfPageItem a = this.list.remove(index);
            this.list.add(index - 1, a);
            this.fireContentsChanged(this, index - 1, index);
            return true;
        }

        public boolean moveDown(final int index) {
            if (index >= this.list.size() - 1) {
                return false;
            }
            final PdfPageItem a = this.list.remove(index);
            this.list.add(index + 1, a);
            this.fireContentsChanged(this, index, index + 1);
            return true;
        }

        public void removeAll(final Collection<PdfPageItem> items) {
            this.list.removeAll(items);
            this.fireContentsChanged(this, 0, list.size() - 1);
        }

        public void add(final PdfPageItem page) {
            this.list.add(page);
            if (this.sorted) {
                Collections.sort(this.list);
                this.fireContentsChanged(this, 0, list.size() - 1);
            } else {
                this.fireContentsChanged(this, list.size() - 1, list.size() - 1);
            }
        }

        public void add(final Collection<PdfPageItem> pages) {
            this.list.addAll(pages);
            if (this.sorted) {
                Collections.sort(this.list);
            }
            this.fireContentsChanged(this, 0, list.size() - 1);
        }

        @Override
        public int getSize() {
            return this.list.size();
        }

        @Override
        public PdfPageItem getElementAt(int index) {
            return this.list.get(index);
        }

        public void remove(int index) {
            this.list.remove(index);
            this.fireContentsChanged(this, index, index);
        }

        public void add(int index, PdfPageItem item) {
            this.list.add(index, item);
            if (this.sorted) {
                Collections.sort(this.list);
                this.fireContentsChanged(this, index, index);
            } else {
                this.fireContentsChanged(this, index, index);
            }
        }

    }

    private static String getTextOrNull(final JTextField field) {
        final String text = field.getText().trim();
        return text.isEmpty() ? null : text;
    }

    public Optional<PDDocument> makeDocument() throws IOException {
        if (this.listTargetPages.getModel().getSize() == 0) {
            return Optional.empty();
        }
        
        Stream.concat(((PdfPageListModel) this.listSourcePages.getModel()).stream(),
                ((PdfPageListModel) this.listTargetPages.getModel()).stream())
                .forEach(x -> this.document.removePage(x.page));
        ((PdfPageListModel) this.listTargetPages.getModel()).stream().forEach(x -> {
            this.document.addPage(x.page);
        });

        final PDDocumentInformation info = this.document.getDocumentInformation();
        info.setAuthor(getTextOrNull(this.textDocumentAuthor));
        info.setCreator(getTextOrNull(this.textDocumentCreator));
        info.setKeywords(getTextOrNull(this.textKeywords));
        info.setTitle(getTextOrNull(this.textTitle));

        return Optional.of(this.document);
    }

    public void dispose() {

    }

    public static PDDocument cloneDocument(PDDocument sourceDoc, boolean clonePages) throws IOException {
        if (!clonePages) {
            PDDocument clonedDoc = new PDDocument();
            if (sourceDoc.getDocumentInformation() != null) {
                clonedDoc.setDocumentInformation(sourceDoc.getDocumentInformation());
            }
            if (sourceDoc.getDocumentCatalog().getLanguage() != null) {
                clonedDoc.getDocumentCatalog().setLanguage(
                        sourceDoc.getDocumentCatalog().getLanguage()
                );
            }

            return clonedDoc;
        } else {
            return deepCloneDocument(sourceDoc);
        }
    }

    private static PDDocument deepCloneDocument(final PDDocument sourceDoc) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sourceDoc.setAllSecurityToBeRemoved(true);
        sourceDoc.save(baos);
        return Loader.loadPDF(baos.toByteArray());
    }

    private static class PdfPageItem implements Comparable<PdfPageItem> {

        private final int origIndex;
        private final PDPage page;

        public PdfPageItem(final int origIndex, final PDPage page) {
            this.origIndex = origIndex;
            this.page = page;
        }

        @Override
        public int compareTo(final PdfPageItem o) {
            return Integer.compare(this.origIndex, o.origIndex);
        }

        @Override
        public String toString() {
            return "Page #" + (this.origIndex + 1);
        }

        @Override
        public int hashCode() {
            return this.origIndex;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof PdfPageItem) {
                return ((PdfPageItem) obj).origIndex == this.origIndex;
            }
            return false;
        }

    }

    private final PDDocument document;
    private final PDFRenderer renderer;

    private BufferedImage renderAsImage(final PdfPageItem item) throws IOException {
        return this.renderer.renderImageWithDPI(item.origIndex, Toolkit.getDefaultToolkit().getScreenResolution());
    }

    public DocumentEditPanel(
            final PDDocument document) throws IOException {
        initComponents();

        this.document = cloneDocument(document, true);
        final PDDocumentInformation info = this.document.getDocumentInformation();
        this.textDocumentAuthor.setText(Objects.requireNonNullElse(info.getAuthor(), ""));
        this.textDocumentCreator.setText(Objects.requireNonNullElse(info.getCreator(), ""));
        this.textTitle.setText(Objects.requireNonNullElse(info.getTitle(), ""));
        this.textKeywords.setText(Objects.requireNonNullElse(info.getKeywords(), ""));

        this.renderer = new PDFRenderer(this.document);

        final PdfPageListModel modelIn = new PdfPageListModel(true);
        final PdfPageListModel modelOut = new PdfPageListModel(false);

        final AtomicInteger counter = new AtomicInteger();
        final Set<PdfPageItem> pages = new HashSet<>();
        this.document.getPages().forEach(x -> {
            final PdfPageItem item = new PdfPageItem(counter.getAndIncrement(), x);
            pages.add(item);
        });
        modelIn.add(pages);

        this.listSourcePages.setModel(modelIn);
        this.listTargetPages.setModel(modelOut);

        this.listSourcePages.setPrototypeCellValue(new PdfPageItem(99999, null));
        this.listTargetPages.setPrototypeCellValue(new PdfPageItem(99999, null));

        this.buttonPageDown.setEnabled(false);
        this.buttonPageUp.setEnabled(false);
        this.buttonPageToSource.setEnabled(false);
        this.buttonPageToTarget.setEnabled(false);

        this.listTargetPages.setSelectionMode(MULTIPLE_INTERVAL_SELECTION);
        this.listTargetPages.setDragEnabled(true);
        this.listTargetPages.setDropMode(DropMode.INSERT);
        this.listTargetPages.setTransferHandler(new PdfPageListTransferHandler());
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jScrollPane1 = new javax.swing.JScrollPane();
        listSourcePages = new javax.swing.JList<>();
        panelButtons = new javax.swing.JPanel();
        buttonPageToTarget = new javax.swing.JButton();
        buttonPageToSource = new javax.swing.JButton();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767));
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767));
        buttonPageUp = new javax.swing.JButton();
        buttonPageDown = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        listTargetPages = new javax.swing.JList<>();
        panelDocumentInfo = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        textDocumentAuthor = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        textDocumentCreator = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        textTitle = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        textKeywords = new javax.swing.JTextField();

        setLayout(new java.awt.GridBagLayout());

        jScrollPane1.setBorder(javax.swing.BorderFactory.createTitledBorder("Source document"));

        listSourcePages.setModel(new DefaultListModel<PdfPageItem>());
        listSourcePages.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                listSourcePagesMouseClicked(evt);
            }
        });
        listSourcePages.addListSelectionListener(this::listSourcePagesValueChanged);
        jScrollPane1.setViewportView(listSourcePages);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1000.0;
        gridBagConstraints.weighty = 1.0;
        add(jScrollPane1, gridBagConstraints);

        panelButtons.setLayout(new java.awt.GridBagLayout());

        buttonPageToTarget.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/arrow_right.png"))); // NOI18N
        buttonPageToTarget.setToolTipText("Move selected pages to the target");
        buttonPageToTarget.addActionListener(this::buttonPageToTargetActionPerformed);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        panelButtons.add(buttonPageToTarget, gridBagConstraints);

        buttonPageToSource.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/arrow_left.png"))); // NOI18N
        buttonPageToSource.setToolTipText("Return the selected pages to the source document");
        buttonPageToSource.addActionListener(this::buttonPageToSourceActionPerformed);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        panelButtons.add(buttonPageToSource, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.weighty = 1000.0;
        panelButtons.add(filler1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.weighty = 1000.0;
        panelButtons.add(filler2, gridBagConstraints);

        buttonPageUp.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/arrow_up.png"))); // NOI18N
        buttonPageUp.setToolTipText("Decrease position of selected page in the target");
        buttonPageUp.addActionListener(this::buttonPageUpActionPerformed);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        panelButtons.add(buttonPageUp, gridBagConstraints);

        buttonPageDown.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/arrow_down.png"))); // NOI18N
        buttonPageDown.setToolTipText("Increase position of selected page in the target");
        buttonPageDown.addActionListener(this::buttonPageDownActionPerformed);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        panelButtons.add(buttonPageDown, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 0, 0);
        add(panelButtons, gridBagConstraints);

        jScrollPane2.setBorder(javax.swing.BorderFactory.createTitledBorder("Target document"));

        listTargetPages.setModel(new DefaultListModel<PdfPageItem>());
        listTargetPages.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                listTargetPagesMouseClicked(evt);
            }
        });
        listTargetPages.addListSelectionListener(this::listTargetPagesValueChanged);
        jScrollPane2.setViewportView(listTargetPages);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1000.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 0, 0);
        add(jScrollPane2, gridBagConstraints);

        panelDocumentInfo.setLayout(new java.awt.GridBagLayout());

        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabel1.setText("Author:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 16, 0, 0);
        panelDocumentInfo.add(jLabel1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1000.0;
        panelDocumentInfo.add(textDocumentAuthor, gridBagConstraints);

        jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabel2.setText("Creator:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 16, 0, 0);
        panelDocumentInfo.add(jLabel2, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1000.0;
        panelDocumentInfo.add(textDocumentCreator, gridBagConstraints);

        jLabel3.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabel3.setText("Titls:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 16, 0, 0);
        panelDocumentInfo.add(jLabel3, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1000.0;
        panelDocumentInfo.add(textTitle, gridBagConstraints);

        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabel4.setText("Keywords:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(0, 16, 0, 0);
        panelDocumentInfo.add(jLabel4, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1000.0;
        panelDocumentInfo.add(textKeywords, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        add(panelDocumentInfo, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    private void listSourcePagesValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_listSourcePagesValueChanged
        this.buttonPageToTarget.setEnabled(!this.listSourcePages.getSelectedValuesList().isEmpty());
    }//GEN-LAST:event_listSourcePagesValueChanged

    private void listTargetPagesValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_listTargetPagesValueChanged
        final int selectedItems = this.listTargetPages.getSelectedValuesList().size();
        this.buttonPageToSource.setEnabled(selectedItems != 0);
        this.buttonPageDown.setEnabled(selectedItems == 1);
        this.buttonPageUp.setEnabled(selectedItems == 1);
    }//GEN-LAST:event_listTargetPagesValueChanged

    private void buttonPageToTargetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonPageToTargetActionPerformed
        final List<PdfPageItem> selected = this.listSourcePages.getSelectedValuesList();
        ((PdfPageListModel) this.listSourcePages.getModel()).removeAll(selected);
        ((PdfPageListModel) this.listTargetPages.getModel()).add(selected);
        this.listSourcePages.setSelectedIndices(new int[0]);
        this.listTargetPages.setSelectedIndices(new int[0]);
    }//GEN-LAST:event_buttonPageToTargetActionPerformed

    private void buttonPageToSourceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonPageToSourceActionPerformed
        final List<PdfPageItem> selected = this.listTargetPages.getSelectedValuesList();
        ((PdfPageListModel) this.listTargetPages.getModel()).removeAll(selected);
        ((PdfPageListModel) this.listSourcePages.getModel()).add(selected);
        this.listSourcePages.setSelectedIndices(new int[0]);
        this.listTargetPages.setSelectedIndices(new int[0]);
    }//GEN-LAST:event_buttonPageToSourceActionPerformed

    private void buttonPageUpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonPageUpActionPerformed
        int index = this.listTargetPages.getSelectedIndex();
        if (index >= 1) {
            if (((PdfPageListModel) this.listTargetPages.getModel()).moveUp(index)) {
                this.listTargetPages.setSelectedIndex(index - 1);
            }
        }
    }//GEN-LAST:event_buttonPageUpActionPerformed

    private void buttonPageDownActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonPageDownActionPerformed
        int index = this.listTargetPages.getSelectedIndex();
        if (index >= 0) {
            if (((PdfPageListModel) this.listTargetPages.getModel()).moveDown(index)) {
                this.listTargetPages.setSelectedIndex(index + 1);
            }
        }
    }//GEN-LAST:event_buttonPageDownActionPerformed

    private void listSourcePagesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_listSourcePagesMouseClicked
        if (evt.getClickCount() >= 2) {
            final int index = ((JList) evt.getSource()).locationToIndex(evt.getPoint());
            if (index >= 0) {
                final PdfPageItem item = ((JList<PdfPageItem>) evt.getSource()).getModel().getElementAt(index);
                this.showPage(item);
            }
        }
    }//GEN-LAST:event_listSourcePagesMouseClicked

    private void listTargetPagesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_listTargetPagesMouseClicked
        if (evt.getClickCount() >= 2) {
            final int index = ((JList) evt.getSource()).locationToIndex(evt.getPoint());
            if (index >= 0) {
                final PdfPageItem item = ((JList<PdfPageItem>) evt.getSource()).getModel().getElementAt(index);
                this.showPage(item);
            }
        }
    }//GEN-LAST:event_listTargetPagesMouseClicked

    private void showPage(final PdfPageItem item) {
        try {
            final BufferedImage image = renderAsImage(item);
            final ImageIcon icon = new ImageIcon(image);
            final JScrollPane scroll = new JScrollPane(new JLabel(icon));
            UiUtils.makeOwningDialogResizable(scroll);
            JOptionPane.showMessageDialog(this, scroll, item.toString(), JOptionPane.PLAIN_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    static final class PdfPageListTransferHandler extends TransferHandler {

        private static final DataFlavor PDF_PAGE_FLAVOR
                = new DataFlavor(List.class, "PdfPageItemList");

        private int[] draggedIndices = null;
        private int dropIndex = -1;

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            JList<PdfPageItem> list = (JList<PdfPageItem>) c;
            draggedIndices = list.getSelectedIndices();

            List<PdfPageItem> items = new ArrayList<>();
            for (int index : draggedIndices) {
                items.add(list.getModel().getElementAt(index));
            }

            return new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{PDF_PAGE_FLAVOR};
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return PDF_PAGE_FLAVOR.equals(flavor);
                }

                @Override
                public Object getTransferData(DataFlavor flavor)
                        throws UnsupportedFlavorException, IOException {
                    if (!isDataFlavorSupported(flavor)) {
                        throw new UnsupportedFlavorException(flavor);
                    }
                    return items;
                }
            };
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop() || !support.isDataFlavorSupported(PDF_PAGE_FLAVOR)) {
                return false;
            }

            support.setShowDropLocation(true);
            return true;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
            dropIndex = dl.getIndex();

            try {
                @SuppressWarnings("unchecked")
                List<PdfPageItem> items
                        = (List<PdfPageItem>) support.getTransferable()
                                .getTransferData(PDF_PAGE_FLAVOR);

                JList<PdfPageItem> list = (JList<PdfPageItem>) support.getComponent();
                PdfPageListModel model
                        = (PdfPageListModel) list.getModel();

                int adjustedDropIndex = dropIndex;
                for (int draggedIndex : draggedIndices) {
                    if (draggedIndex < dropIndex) {
                        adjustedDropIndex--;
                    }
                }

                for (int i = draggedIndices.length - 1; i >= 0; i--) {
                    model.remove(draggedIndices[i]);
                }

                for (int i = 0; i < items.size(); i++) {
                    model.add(adjustedDropIndex + i, items.get(i));
                }

                list.setSelectionInterval(adjustedDropIndex,
                        adjustedDropIndex + items.size() - 1);

                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void exportDone(JComponent c, Transferable data, int action) {
            draggedIndices = null;
            dropIndex = -1;
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton buttonPageDown;
    private javax.swing.JButton buttonPageToSource;
    private javax.swing.JButton buttonPageToTarget;
    private javax.swing.JButton buttonPageUp;
    private javax.swing.Box.Filler filler1;
    private javax.swing.Box.Filler filler2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JList<PdfPageItem> listSourcePages;
    private javax.swing.JList<PdfPageItem> listTargetPages;
    private javax.swing.JPanel panelButtons;
    private javax.swing.JPanel panelDocumentInfo;
    private javax.swing.JTextField textDocumentAuthor;
    private javax.swing.JTextField textDocumentCreator;
    private javax.swing.JTextField textKeywords;
    private javax.swing.JTextField textTitle;
    // End of variables declaration//GEN-END:variables
}

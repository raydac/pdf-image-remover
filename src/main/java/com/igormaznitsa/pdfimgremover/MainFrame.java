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

import com.igormaznitsa.pdfimgremover.ImageFinderStreamEngine.FoundImage;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.TreePath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

public class MainFrame extends javax.swing.JFrame {

    private boolean saveRequired = false;

    private final Image applicationIcon;
    private final ScalableImage scalableImage;
    private File lastImportedImageFile;

    private final PrintWriter logWriter;

    public static final FileFilter FILEFILTER_PDF = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return file.isDirectory() || file.getName().toLowerCase(Locale.ENGLISH).endsWith(".pdf");
        }

        @Override
        public String getDescription() {
            return "PDF documents (*.pdf)";
        }
    };

    public static final FileFilter FILEFILTER_PNG = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return file.isDirectory() || file.getName().toLowerCase(Locale.ENGLISH).endsWith(".png");
        }

        @Override
        public String getDescription() {
            return "PNG image (*.png)";
        }
    };

    public void log(final String logLine, final Throwable... errors) {
        if (this.logWriter == null) {
            System.out.println(logLine);
            for (final Throwable t : errors) {
                t.printStackTrace();
            }
        } else {
            synchronized (this.logWriter) {
                try {
                    this.logWriter.println(logLine);
                    for (final Throwable t : errors) {
                        t.printStackTrace(this.logWriter);
                    }
                    this.logWriter.flush();
                } catch (Exception ex) {
                    System.err.println("can't write log for error: " + ex.getMessage());
                }
            }
        }
    }

    public MainFrame() {
        initComponents();

        PrintWriter writer = null;

        try {
            writer = new PrintWriter(new File(".", "image-remover-log.txt"), StandardCharsets.UTF_8);
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    if (logWriter != null) {
                        try {
                            logWriter.close();
                        } catch (Exception ex) {
                        }
                    }
                }
            }));
        } catch (Exception ex) {
            System.err.println("Can't create log file");
            ex.printStackTrace();
        }
        this.logWriter = writer;

        final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focusManager.addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                boolean result = false;
                if (!e.isConsumed() && e.getModifiersEx() == 0) {
                    final boolean released = e.getID() == KeyEvent.KEY_RELEASED;
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_PAGE_DOWN: {
                            if (released) {
                                try {
                                    spinnerPage.setValue(((SpinnerNumberModel) spinnerPage.getModel()).getNextValue());
                                } catch (Exception ex) {
                                    // ignore
                                }
                            }
                            e.consume();
                            result = true;
                        }
                        break;
                        case KeyEvent.VK_END: {
                            if (released && spinnerPage.isEnabled()) {
                                try {
                                    spinnerPage.setValue(((SpinnerNumberModel) spinnerPage.getModel()).getMaximum());
                                } catch (Exception ex) {
                                    // ignore
                                }
                            }
                            e.consume();
                            result = true;
                        }
                        break;
                        case KeyEvent.VK_HOME: {
                            if (released && spinnerPage.isEnabled()) {
                                try {
                                    spinnerPage.setValue(((SpinnerNumberModel) spinnerPage.getModel()).getMinimum());
                                } catch (Exception ex) {
                                    // ignore
                                }
                            }
                            e.consume();
                            result = true;
                        }
                        break;
                        case KeyEvent.VK_PAGE_UP: {
                            if (released) {
                                try {
                                    spinnerPage.setValue(((SpinnerNumberModel) spinnerPage.getModel()).getPreviousValue());
                                } catch (Exception ex) {
                                    // ignore
                                }
                            }
                            e.consume();
                            result = true;
                        }
                        break;
                    }
                }
                return result;
            }
        });

        this.pageTree.setCellRenderer(new PageTreeModel.PageImageRenderer());
        this.scalableImage = new ScalableImage();
        this.scaleStatusIndicator.setScalable(this.scalableImage);
        this.mainScrollPane.setViewportView(this.scalableImage);

        Image icon = null;
        try (final InputStream image = MainFrame.class.getResourceAsStream("/icon.png")) {
            icon = ImageIO.read(image);
            this.setIconImage(icon);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        this.applicationIcon = icon;

        this.setSize(640, 480);
        this.splitPane.setDividerLocation(300);
        this.updateTitle();
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                if (saveRequired) {
                    if (JOptionPane.showConfirmDialog(MainFrame.this, "There is unsaved file. Dou you really want close application?", "Close application", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.CANCEL_OPTION) {
                        return;
                    }
                }
                try {
                    if (document != null) {
                        document.close();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                System.exit(0);
            }

        });

        final JPanel glassPanel = new JPanel(new GridLayout(3, 3));

        glassPanel.setBackground(new Color(213, 33, 255, 123));
        glassPanel.setVisible(false);

        this.progressBar = new JProgressBar(0, 100);
        this.progressBar.setStringPainted(true);

        glassPanel.add(Box.createGlue());
        glassPanel.add(Box.createGlue());
        glassPanel.add(Box.createGlue());

        glassPanel.add(Box.createGlue());
        glassPanel.add(this.progressBar);
        glassPanel.add(Box.createGlue());

        glassPanel.add(Box.createGlue());
        glassPanel.add(Box.createGlue());
        glassPanel.add(Box.createGlue());

        this.setGlassPane(glassPanel);
    }

    private final JProgressBar progressBar;

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        splitPane = new javax.swing.JSplitPane();
        scrollPanelTree = new javax.swing.JScrollPane();
        pageTree = new javax.swing.JTree();
        mainScrollPane = new javax.swing.JScrollPane();
        toolpanelPages = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        spinnerPage = new javax.swing.JSpinner();
        labelPageNumber = new javax.swing.JLabel();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        scaleStatusIndicator = new com.igormaznitsa.pdfimgremover.ScaleStatusIndicator();
        mainMenu = new javax.swing.JMenuBar();
        menuFile = new javax.swing.JMenu();
        menuFileOpen = new javax.swing.JMenuItem();
        menuFileSaveAs = new javax.swing.JMenuItem();
        menuFileSeparator = new javax.swing.JPopupMenu.Separator();
        menuFileExit = new javax.swing.JMenuItem();
        menuEdit = new javax.swing.JMenu();
        menuEditShowImage = new javax.swing.JMenuItem();
        menuEditReplaceByFileForName = new javax.swing.JMenuItem();
        menuEditReplaceByFileForImage = new javax.swing.JMenuItem();
        menuEditHidePictureForName = new javax.swing.JMenuItem();
        menuEditHidePictureForImage = new javax.swing.JMenuItem();
        menuHelp = new javax.swing.JMenu();
        menuHelpAbout = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);

        scrollPanelTree.setBorder(javax.swing.BorderFactory.createTitledBorder("Detected images on the page"));

        pageTree.setModel(null);
        pageTree.setRootVisible(false);
        pageTree.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pageTreeMouseClicked(evt);
            }
        });
        scrollPanelTree.setViewportView(pageTree);

        splitPane.setLeftComponent(scrollPanelTree);
        splitPane.setRightComponent(mainScrollPane);

        getContentPane().add(splitPane, java.awt.BorderLayout.CENTER);

        toolpanelPages.setLayout(new java.awt.GridBagLayout());

        jLabel1.setText("Page:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 16, 0, 0);
        toolpanelPages.add(jLabel1, gridBagConstraints);

        spinnerPage.setEnabled(false);
        spinnerPage.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spinnerPageStateChanged(evt);
            }
        });
        toolpanelPages.add(spinnerPage, new java.awt.GridBagConstraints());

        labelPageNumber.setText(" / ---");
        toolpanelPages.add(labelPageNumber, new java.awt.GridBagConstraints());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 1000.0;
        toolpanelPages.add(filler1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 16);
        toolpanelPages.add(scaleStatusIndicator, gridBagConstraints);

        getContentPane().add(toolpanelPages, java.awt.BorderLayout.PAGE_START);

        menuFile.setText("File");
        menuFile.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuCanceled(javax.swing.event.MenuEvent evt) {
            }
            public void menuDeselected(javax.swing.event.MenuEvent evt) {
            }
            public void menuSelected(javax.swing.event.MenuEvent evt) {
                menuFileMenuSelected(evt);
            }
        });

        menuFileOpen.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/file_extension_pdf.png"))); // NOI18N
        menuFileOpen.setText("Open");
        menuFileOpen.setToolTipText("Open PDF document");
        menuFileOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuFileOpenActionPerformed(evt);
            }
        });
        menuFile.add(menuFileOpen);

        menuFileSaveAs.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/file_save_as.png"))); // NOI18N
        menuFileSaveAs.setText("Save as...");
        menuFileSaveAs.setToolTipText("Save current state of PDF document as a file");
        menuFileSaveAs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuFileSaveAsActionPerformed(evt);
            }
        });
        menuFile.add(menuFileSaveAs);
        menuFile.add(menuFileSeparator);

        menuFileExit.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, java.awt.event.InputEvent.ALT_DOWN_MASK));
        menuFileExit.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/door_in.png"))); // NOI18N
        menuFileExit.setText("Exit");
        menuFileExit.setToolTipText("Close application");
        menuFileExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuFileExitActionPerformed(evt);
            }
        });
        menuFile.add(menuFileExit);

        mainMenu.add(menuFile);

        menuEdit.setText("Edit");
        menuEdit.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuCanceled(javax.swing.event.MenuEvent evt) {
            }
            public void menuDeselected(javax.swing.event.MenuEvent evt) {
            }
            public void menuSelected(javax.swing.event.MenuEvent evt) {
                menuEditMenuSelected(evt);
            }
        });

        menuEditShowImage.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/image.png"))); // NOI18N
        menuEditShowImage.setText("Show image");
        menuEditShowImage.setToolTipText("Show focused image");
        menuEditShowImage.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuEditShowImageActionPerformed(evt);
            }
        });
        menuEdit.add(menuEditShowImage);

        menuEditReplaceByFileForName.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/table_replace.png"))); // NOI18N
        menuEditReplaceByFileForName.setText("Replace by name");
        menuEditReplaceByFileForName.setToolTipText("Find images by names and replace by new image");
        menuEditReplaceByFileForName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuEditReplaceByFileForNameActionPerformed(evt);
            }
        });
        menuEdit.add(menuEditReplaceByFileForName);

        menuEditReplaceByFileForImage.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/table_replace_slow.png"))); // NOI18N
        menuEditReplaceByFileForImage.setText("Replace by image");
        menuEditReplaceByFileForImage.setToolTipText("Find images by graphics data and replace by new images (can be slow)");
        menuEditReplaceByFileForImage.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuEditReplaceByFileForImageActionPerformed(evt);
            }
        });
        menuEdit.add(menuEditReplaceByFileForImage);

        menuEditHidePictureForName.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/emotion_ghost.png"))); // NOI18N
        menuEditHidePictureForName.setText("Find by name and hide");
        menuEditHidePictureForName.setToolTipText("Find images by names and replace by transparent areas");
        menuEditHidePictureForName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuEditHidePictureForNameActionPerformed(evt);
            }
        });
        menuEdit.add(menuEditHidePictureForName);

        menuEditHidePictureForImage.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/emotion_ghost_slow.png"))); // NOI18N
        menuEditHidePictureForImage.setText("Find by image and hide");
        menuEditHidePictureForImage.setToolTipText("Find images by graphics data and replace by transparent areas (can be slow)");
        menuEditHidePictureForImage.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuEditHidePictureForImageActionPerformed(evt);
            }
        });
        menuEdit.add(menuEditHidePictureForImage);

        mainMenu.add(menuEdit);

        menuHelp.setText("Help");
        menuHelp.setToolTipText("Show info about application");

        menuHelpAbout.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/information.png"))); // NOI18N
        menuHelpAbout.setText("About");
        menuHelpAbout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuHelpAboutActionPerformed(evt);
            }
        });
        menuHelp.add(menuHelpAbout);

        mainMenu.add(menuHelp);

        setJMenuBar(mainMenu);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private File lastOpenedFile;
    private File lastSavedFile;
    private File documentFile;
    private PDDocument document;
    private PDFRenderer renderer;

    private void updateVisiblePdfPage() {
        Integer pageNumber = (Integer) ((SpinnerNumberModel) this.spinnerPage.getModel()).getValue() - 1;
        if (this.document == null) {
            this.mainScrollPane.removeAll();
            this.mainScrollPane.invalidate();
            this.mainScrollPane.repaint();
            this.pageTree.setModel(new PageTreeModel(null, null));
        } else {
            try {
                scalableImage.setImage(this.renderer.renderImage(pageNumber), true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Can't render page", "Error", JOptionPane.ERROR_MESSAGE);
            } finally {
                this.pageTree.setModel(new PageTreeModel(this.document, this.document.getPage(pageNumber)));
            }
        }
    }

    private void activateProgress() {
        this.updateProgressBar(0);
        this.getGlassPane().setVisible(true);
        this.mainMenu.setEnabled(false);
    }

    private void deactivateProgress() {
        this.progressBar.setValue(0);
        this.getGlassPane().setVisible(false);
        this.mainMenu.setEnabled(true);
    }

    private void menuFileOpenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuFileOpenActionPerformed
        this.log("Pressed menuFileOpenActionPerformed");

        try {
            if (this.saveRequired && JOptionPane.showConfirmDialog(this, "You have unsaved result, open new file?", "Unsaved result", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.CANCEL_OPTION) {
                return;
            }

            final JFileChooser fileOpenDialog = new JFileChooser(this.lastOpenedFile);
            fileOpenDialog.setFileFilter(FILEFILTER_PDF);
            fileOpenDialog.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileOpenDialog.setMultiSelectionEnabled(false);
            fileOpenDialog.setDialogTitle("Open PDF document");

            if (fileOpenDialog.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                this.lastOpenedFile = fileOpenDialog.getSelectedFile();
                try {
                    if (this.document != null) {
                        this.document.close();
                    }

                    this.document = Loader.loadPDF(this.lastOpenedFile);
                    this.renderer = new PDFRenderer(this.document);
                    this.spinnerPage.setModel(new SpinnerNumberModel(1, 1, document.getNumberOfPages(), 1));
                    this.spinnerPage.setEnabled(true);
                    this.labelPageNumber.setText("/ " + document.getNumberOfPages());
                    this.documentFile = this.lastOpenedFile;
                    this.saveRequired = false;
                    this.updateTitle();
                    this.log("Loaded file: " + this.lastOpenedFile.getName());
                } catch (IOException ex) {
                    this.log("Error load file: " + this.lastOpenedFile.getName(), ex);
                    JOptionPane.showMessageDialog(this, "Can't load file for error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    this.updateVisiblePdfPage();
                }
            }
        } catch (Exception ex) {
            this.log("Error during menuFileOpenActionPerformed", ex);
        }
    }//GEN-LAST:event_menuFileOpenActionPerformed

    private void spinnerPageStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spinnerPageStateChanged
        this.updateVisiblePdfPage();
    }//GEN-LAST:event_spinnerPageStateChanged

    private static BufferedImage extractRawImage(final PDImageXObject ximage) throws IOException {
        if (ximage == null) return null;
        BufferedImage result = ximage.getRawImage();
        if (result == null) {
            result = ximage.getColorSpace().toRGBImage(ximage.getRawRaster());
        }
        return result;
    }
    
    private static class ImageNamePair {

        private final COSName name;
        private final PDImageXObject image;
        private final BufferedImage rawImage;
        private PDImageXObject targetImage;

        ImageNamePair(final COSName name, final PDImageXObject image) throws IOException {
            this.name = name;
            this.image = image;
            this.targetImage = null;
            this.rawImage = extractRawImage(image);
        }

        boolean isSimilarToImage(final PDImageXObject thatImage) throws IOException {
            final BufferedImage thatRawImage = extractRawImage(thatImage);
            if (thatRawImage != null && this.rawImage != null) {

                return thatRawImage == this.rawImage
                        || (thatRawImage.getType() == this.rawImage.getType()
                        && thatRawImage.getWidth() == this.rawImage.getWidth()
                        && thatRawImage.getHeight() == this.rawImage.getHeight()
                        && isDataBufferEquals(thatRawImage.getData().getDataBuffer(), this.rawImage.getData().getDataBuffer()));
            }
            return false;
        }

        @Override
        public String toString() {
            return "ImageNamePair{" + "name=" + name + ", image=" + image + ", rawImage=" + rawImage + '}';
        }
        
    }

    private static boolean isDataBufferEquals(final DataBuffer one, final DataBuffer two) {
        if (one == two) {
            return true;
        }

        if (one.getClass().equals(two.getClass())
                && one.getNumBanks() == two.getNumBanks()
                && one.getSize() == two.getSize()) {
            
            final int size = one.getSize();
            final int[] offsets1 = one.getOffsets();
            final int[] offsets2 = two.getOffsets();

            if (one instanceof DataBufferInt) {
                return Arrays.equals(((DataBufferInt) one).getData(), ((DataBufferInt) two).getData());
            } else if (one instanceof DataBufferFloat) {
                return Arrays.equals(((DataBufferFloat) one).getData(), ((DataBufferFloat) two).getData());
            } else if (one instanceof DataBufferDouble) {
                return Arrays.equals(((DataBufferDouble) one).getData(), ((DataBufferDouble) two).getData());
            } else if (one instanceof DataBufferByte) {
                return Arrays.equals(((DataBufferByte) one).getData(), ((DataBufferByte) two).getData());
            } else if (one instanceof DataBufferShort) {
                return Arrays.equals(((DataBufferShort) one).getData(), ((DataBufferShort) two).getData());
            } else {

                for (int i = 0; i < one.getNumBanks(); i++) {
                    if (offsets1[i] != offsets2[i]) {
                        return false;
                    }
                    for (int x = 0; x < size; x++) {
                        if (Double.compare(one.getElemDouble(i, x), two.getElemDouble(i, x)) != 0) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    private SwingWorker<Integer, Integer> makeSwingWorkerReplaceImage(
            final PDDocument document,
            final List<Integer> pageIndexes,
            final List<ImageNamePair> images,
            final BufferedImage image,
            final boolean byImage,
            final Consumer<Integer> progressConsumer,
            final Consumer<List<Integer>> publishConsumer,
            final BiConsumer<Throwable, Integer> doneConsumer) throws IOException {
        final SwingWorker<Integer, Integer> result = new SwingWorker<Integer, Integer>() {

            private volatile Throwable error = null;
            private volatile int counter = 0;

            @Override
            protected Integer doInBackground() throws Exception {
                try {
                    log("Request replaceImage for " + images.size() + " pair(s) and for " + pageIndexes.size() + " page(s), by image: " + byImage);

                    final ImageFinderStreamEngine finder = new ImageFinderStreamEngine();

                    for (final ImageNamePair p : images) {
                        p.targetImage = LosslessFactory.createFromImage(document, image == null ? new BufferedImage(p.image.getWidth(), p.image.getHeight(), BufferedImage.TYPE_INT_ARGB) : image);
                    }

                    final AtomicInteger detectedErrors = new AtomicInteger();
                    int processed = 0;

                    final Set<COSName> replaced = new HashSet<>();
                    for (final Integer pageIndex : pageIndexes) {
                        PDPage page = document.getPage(pageIndex);
                        final Map<COSName, ImageFinderStreamEngine.FoundImage> foundImages = finder.findImages(page);
                        if (byImage) {
                            replaced.clear();
                            for (final ImageNamePair pair : images) {
                                foundImages.entrySet()
                                        .stream()
                                        .filter(x -> !replaced.contains(x.getKey()))
                                        .forEach(e -> {
                                            try {
                                                if (pair.isSimilarToImage(e.getValue().image)) {
                                                    e.getValue().resources.put(e.getKey(), pair.targetImage);
                                                    replaced.add(e.getKey());
                                                    counter++;
                                                }
                                            } catch (IOException ex) {
                                                detectedErrors.incrementAndGet();
                                            }
                                        });
                            }
                        } else {
                            for (final ImageNamePair pair : images) {
                                FoundImage foundImageOnPage = foundImages.get(pair.name);
                                if (foundImageOnPage != null && foundImageOnPage.image.getWidth() == pair.image.getWidth() && foundImageOnPage.image.getHeight() == pair.image.getHeight()) {
                                    foundImageOnPage.resources.put(pair.name, pair.targetImage);
                                    counter++;
                                }
                            }
                        }
                        processed++;
                        this.publish(processed);
                        this.setProgress(Math.min(100, Math.round(((float) processed / (float) pageIndexes.size()) * 100.0f)));
                    }

                    log("replaceImage found name of requested image(s) on " + counter + " page(s) (from " + processed + ')' + " detected " + detectedErrors.get() + " error(s) ");
                    if (detectedErrors.get() > 0) {
                        throw new IOException("Detected " + detectedErrors.get() + " during image replace");
                    }
                } catch (Exception ex) {
                    this.error = ex;
                }
                return counter;
            }

            @Override
            protected void process(final List<Integer> chunks) {
                publishConsumer.accept(chunks);
            }

            @Override
            protected void done() {
                doneConsumer.accept(this.error, this.counter);
            }

        };

        result.getPropertyChangeSupport().addPropertyChangeListener("progress", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                progressConsumer.accept((Integer) evt.getNewValue());
            }
        });

        return result;
    }

    private void updateTitle() {
        if (this.documentFile == null) {
            this.setTitle("No document");
        } else {
            this.setTitle(this.documentFile.getName() + (this.saveRequired ? "*" : ""));
        }
    }

    private void doSearchAndReplacement(final boolean byImage) {
        final int choose = JOptionPane.showConfirmDialog(this,
                "Search and hide on all pages?",
                "Find and replace by " + (byImage ? " image data" : "name"), JOptionPane.YES_NO_CANCEL_OPTION);
        if (choose == JOptionPane.CANCEL_OPTION) {
            return;
        }

        final List<Integer> pages = choose == JOptionPane.YES_OPTION ? IntStream.range(0, this.document.getNumberOfPages()).boxed().collect(Collectors.toList()) : List.of(((Integer) this.spinnerPage.getValue()) - 1);
        final List<ImageNamePair> pairs = new ArrayList<>();

        try {
            for (final TreePath path : this.pageTree.getSelectionPaths()) {
                Object last = path.getLastPathComponent();
                if (last instanceof PageTreeModel.PageItem) {
                    final PageTreeModel.PageItem i = (PageTreeModel.PageItem) last;
                    pairs.add(new ImageNamePair(i.name, i.pdImage));
                }
            }
        } catch (Exception ex) {
            this.log("Detected error during image replace, prepare image error", ex);
            JOptionPane.showMessageDialog(this, "Can't prepare image(s): " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            final SwingWorker<Integer, Integer> worker = makeSwingWorkerReplaceImage(document, pages, pairs, null, byImage,
                    this::updateProgressBar,
                    (list) -> {

                    },
                    (error, counter) -> {
                        this.deactivateProgress();
                        if (error == null) {
                            this.saveRequired |= counter != 0;
                            this.updateTitle();
                            JOptionPane.showMessageDialog(this, "Managed to find and hide " + counter + " image(s)", "Completed", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            this.log("Detected error during image replace", error);
                            error.printStackTrace();
                            JOptionPane.showMessageDialog(this, "Can't hide image(s): " + error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                        this.updateVisiblePdfPage();
                    });

            this.activateProgress();
            worker.execute();
        } catch (IOException ex) {
            this.deactivateProgress();
            this.log("Unexpected error", ex);
            JOptionPane.showMessageDialog(this, "Unexpected error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    private void menuEditHidePictureForNameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuEditHidePictureForNameActionPerformed
        this.log("Pressed menuEditMakeTransparentActionPerformed");
        this.doSearchAndReplacement(false);
    }//GEN-LAST:event_menuEditHidePictureForNameActionPerformed

    private void menuFileSaveAsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuFileSaveAsActionPerformed
        this.log("Pressed menuFileSaveAsActionPerformed");

        try {
            final JFileChooser fileSaveDialog = new JFileChooser(this.lastSavedFile);
            fileSaveDialog.setFileFilter(FILEFILTER_PDF);
            fileSaveDialog.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileSaveDialog.setMultiSelectionEnabled(false);
            fileSaveDialog.setDialogTitle("Save PDF document");

            if (fileSaveDialog.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File targetFile = fileSaveDialog.getSelectedFile();
                if (!targetFile.getName().contains(".")) {
                    targetFile = new File(targetFile.getParentFile(), targetFile.getName() + ".pdf");
                }
                this.lastSavedFile = targetFile;

                if (targetFile.exists() && JOptionPane.showConfirmDialog(this, "Override file " + targetFile.getName() + "?", "File exists", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.CANCEL_OPTION) {
                    return;
                }

                try {
                    this.document.save(targetFile);
                    this.saveRequired = false;
                    this.updateTitle();
                    this.log("Saved file: " + targetFile.getName());
                } catch (IOException ex) {
                    this.log("Error save file: " + targetFile.getName(), ex);
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Can't save file for error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } catch (Exception ex) {
            this.log("Error during menuFileSaveAsActionPerformed", ex);
        }
    }//GEN-LAST:event_menuFileSaveAsActionPerformed

    private void menuEditMenuSelected(javax.swing.event.MenuEvent evt) {//GEN-FIRST:event_menuEditMenuSelected
        final long selectedImages = this.pageTree.getSelectionPaths() == null ? 0L : Stream.of(this.pageTree.getSelectionPaths()).map(x -> x.getLastPathComponent()).filter(x -> x instanceof PageTreeModel.PageItem).count();

        this.menuEditShowImage.setEnabled(selectedImages == 1);

        this.menuEditHidePictureForName.setEnabled(selectedImages > 0);
        this.menuEditHidePictureForImage.setEnabled(selectedImages > 0);

        this.menuEditReplaceByFileForName.setEnabled(selectedImages > 0);
        this.menuEditReplaceByFileForImage.setEnabled(selectedImages > 0);
    }//GEN-LAST:event_menuEditMenuSelected

    private void menuHelpAboutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuHelpAboutActionPerformed
        final JHtmlLabel label = new JHtmlLabel("<html><b>PDF image remover</b><br><b>Version:</b> 1.1.1<br><b>Author:</b> Igor Maznitsa<br><a href=\"https://github.com/raydac/pdf-image-remover\">https://github.com/raydac/pdf-image-remover</a></html>");
        label.addLinkListener((JHtmlLabel source, String link) -> {
            try {
                Desktop.getDesktop().browse(new URI(link));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        JOptionPane.showMessageDialog(this, label, "About", JOptionPane.PLAIN_MESSAGE, new ImageIcon(this.applicationIcon));
    }//GEN-LAST:event_menuHelpAboutActionPerformed

    private void menuFileMenuSelected(javax.swing.event.MenuEvent evt) {//GEN-FIRST:event_menuFileMenuSelected
        this.menuFileSaveAs.setEnabled(this.document != null);
    }//GEN-LAST:event_menuFileMenuSelected

    private void menuFileExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuFileExitActionPerformed
        for (final WindowListener l : this.getWindowListeners()) {
            l.windowClosing(new WindowEvent(this, 1));
        }
    }//GEN-LAST:event_menuFileExitActionPerformed

    private void pageTreeMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pageTreeMouseClicked
        if (!evt.isPopupTrigger() && evt.getClickCount() > 1) {
            final TreePath path = this.pageTree.getPathForLocation(evt.getX(), evt.getY());
            if (path != null && path.getLastPathComponent() instanceof PageTreeModel.PageItem) {
                final PageTreeModel.PageItem selectedItem = (PageTreeModel.PageItem) path.getLastPathComponent();
                JOptionPane.showMessageDialog(this, new ImageShow(selectedItem), "Image", JOptionPane.PLAIN_MESSAGE);
            }
        }
    }//GEN-LAST:event_pageTreeMouseClicked

    private void menuEditShowImageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuEditShowImageActionPerformed
        final TreePath path = this.pageTree.getSelectionPath();
        if (path != null && path.getLastPathComponent() instanceof PageTreeModel.PageItem) {
            final PageTreeModel.PageItem selectedItem = (PageTreeModel.PageItem) path.getLastPathComponent();
            JOptionPane.showMessageDialog(this, new ImageShow(selectedItem), "Image", JOptionPane.PLAIN_MESSAGE);
        }
    }//GEN-LAST:event_menuEditShowImageActionPerformed

    private void updateProgressBar(final int progress) {
        this.progressBar.setString("Processed " + progress + '%');
        this.progressBar.setValue(progress);
    }
    
    private void commonFindAndReplace(final boolean byImage) throws IOException {
        final JFileChooser fileChooser = new JFileChooser(this.lastImportedImageFile);
        fileChooser.setFileFilter(MainFrame.FILEFILTER_PNG);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setDialogTitle("Load image");

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            final File sourceFile = fileChooser.getSelectedFile();
            this.lastImportedImageFile = sourceFile;

            BufferedImage loadedImage = null;
            try {
                loadedImage = ImageIO.read(sourceFile);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Can't load file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            final int choose = JOptionPane.showConfirmDialog(this, "Replace for all pages?", "Replace by " + (byImage ? "image" : "name"), JOptionPane.YES_NO_CANCEL_OPTION);
            if (choose == JOptionPane.CANCEL_OPTION) {
                return;
            }

            final List<Integer> pages = choose == JOptionPane.YES_OPTION ? IntStream.range(0, this.document.getNumberOfPages()).boxed().collect(Collectors.toList()) : List.of(((Integer) this.spinnerPage.getValue()) - 1);
            final List<ImageNamePair> pairs = new ArrayList<>();

            for (final TreePath path : this.pageTree.getSelectionPaths()) {
                Object last = path.getLastPathComponent();
                if (last instanceof PageTreeModel.PageItem) {
                    final PageTreeModel.PageItem i = (PageTreeModel.PageItem) last;
                    pairs.add(new ImageNamePair(i.name, i.pdImage));
                }
            }

            final SwingWorker<Integer, Integer> worker = makeSwingWorkerReplaceImage(this.document, pages, pairs, loadedImage, byImage,
                    this::updateProgressBar,
                    (list) -> {
                    },
                    (error, counter) -> {
                        this.deactivateProgress();
                        if (error == null) {
                            this.saveRequired |= counter != 0;
                            this.updateTitle();
                            JOptionPane.showMessageDialog(this, "Managed to find and replace " + counter + " image(s)", "Completed", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            error.printStackTrace();
                            JOptionPane.showMessageDialog(this, "Can't replace image(s): " + error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                        this.updateVisiblePdfPage();
                    });
            this.activateProgress();
            worker.execute();
        }
    }


    private void menuEditReplaceByFileForNameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuEditReplaceByFileForNameActionPerformed
        this.log("pressed menuEditReplaceByFileForNameActionPerformed");
        try {
            this.commonFindAndReplace(false);
        } catch (Exception ex) {
            this.log("ERROR during menuEditReplaceByFileForNameActionPerformed", ex);
        }
    }//GEN-LAST:event_menuEditReplaceByFileForNameActionPerformed

    private void menuEditHidePictureForImageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuEditHidePictureForImageActionPerformed
        this.log("Pressed menuEditHidePictureForImageActionPerformed");
        this.doSearchAndReplacement(true);
    }//GEN-LAST:event_menuEditHidePictureForImageActionPerformed

    private void menuEditReplaceByFileForImageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuEditReplaceByFileForImageActionPerformed
        this.log("pressed menuEditReplaceByFileForImageActionPerformed");
        try {
            this.commonFindAndReplace(true);
        } catch (Exception ex) {
            this.log("ERROR during menuEditReplaceByFileForImageActionPerformed", ex);
        }
    }//GEN-LAST:event_menuEditReplaceByFileForImageActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.Box.Filler filler1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel labelPageNumber;
    private javax.swing.JMenuBar mainMenu;
    private javax.swing.JScrollPane mainScrollPane;
    private javax.swing.JMenu menuEdit;
    private javax.swing.JMenuItem menuEditHidePictureForImage;
    private javax.swing.JMenuItem menuEditHidePictureForName;
    private javax.swing.JMenuItem menuEditReplaceByFileForImage;
    private javax.swing.JMenuItem menuEditReplaceByFileForName;
    private javax.swing.JMenuItem menuEditShowImage;
    private javax.swing.JMenu menuFile;
    private javax.swing.JMenuItem menuFileExit;
    private javax.swing.JMenuItem menuFileOpen;
    private javax.swing.JMenuItem menuFileSaveAs;
    private javax.swing.JPopupMenu.Separator menuFileSeparator;
    private javax.swing.JMenu menuHelp;
    private javax.swing.JMenuItem menuHelpAbout;
    private javax.swing.JTree pageTree;
    private com.igormaznitsa.pdfimgremover.ScaleStatusIndicator scaleStatusIndicator;
    private javax.swing.JScrollPane scrollPanelTree;
    private javax.swing.JSpinner spinnerPage;
    private javax.swing.JSplitPane splitPane;
    private javax.swing.JPanel toolpanelPages;
    // End of variables declaration//GEN-END:variables
}

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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

public class ImageFinderStreamEngine extends PDFStreamEngine {

    public static class FoundImage implements Comparable<FoundImage> {

        public final COSName name;
        public final PDImageXObject image;
        public final PDResources resources;

        private FoundImage(COSName name, PDImageXObject image, PDResources resources) {
            this.name = name;
            this.image = image;
            this.resources = resources;
        }

        @Override
        public int compareTo(final FoundImage o) {
            return this.name.getName().compareTo(o.name.getName());
        }
    }
    private final Map<COSName, FoundImage> foundImages = new HashMap<>();

    public Map<COSName, FoundImage> findImages(final PDPage page) throws IOException {
        try {
            this.foundImages.clear();
            this.processPage(page);
            return new HashMap<>(this.foundImages);
        } finally {
            this.foundImages.clear();
        }
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        if ("Do".equals(operator.getName())) {
            COSName objectName = (COSName) operands.get(0);
            PDXObject xobject = getResources().getXObject(objectName);
            if (xobject instanceof PDImageXObject) {
                PDImageXObject image = (PDImageXObject) xobject;
                this.foundImages.put(objectName, new FoundImage(objectName, image, this.getResources()));
            } else if (xobject instanceof PDFormXObject) {
                PDFormXObject form = (PDFormXObject) xobject;
                showForm(form);
            }
        } else {
            super.processOperator(operator, operands);
        }
    }

}

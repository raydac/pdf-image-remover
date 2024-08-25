/*
 * Copyright 2024 igormaznitsa.com.
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

import java.awt.Window;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.event.HierarchyListener;
import java.awt.event.HierarchyEvent;
import javax.swing.SwingUtilities;

public final class UiUtils {
    private UiUtils(){
        
    }
    
    public static void makeOwningDialogResizable(final Component component, final Runnable... extraActions) {
        final HierarchyListener listener = new HierarchyListener() {
            @Override
            public void hierarchyChanged(final HierarchyEvent e) {
                final Window window = SwingUtilities.getWindowAncestor(component);
                if (window instanceof Dialog) {
                    final Dialog dialog = (Dialog) window;
                    if (!dialog.isResizable()) {
                        dialog.setResizable(true);
                        component.removeHierarchyListener(this);

                        for (final Runnable r : extraActions) {
                            r.run();
                        }
                    }
                }
            }
        };
        component.addHierarchyListener(listener);
    }

}

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

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Application {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(ApplicationPreferences.INSTANCE.getKey(ApplicationPreferences.PROPERTY_LOOK_AND_FEEL, UIManager.getSystemLookAndFeelClassName()));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            new MainFrame().setVisible(true);
        });
    }
}

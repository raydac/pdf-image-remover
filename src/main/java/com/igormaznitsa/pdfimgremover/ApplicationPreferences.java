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

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class ApplicationPreferences {

    public static final String PROPERTY_LOOK_AND_FEEL = "lookAndFeel";
    
    public static final ApplicationPreferences INSTANCE = new ApplicationPreferences();

    private final Preferences preferences;
    
    private ApplicationPreferences() {
        this.preferences = Preferences.userNodeForPackage(ApplicationPreferences.class);
    }

    public synchronized String getKey(final String key, final String defaultValue) {
        return this.preferences.get(key, defaultValue);
    }

    public synchronized void setKey(final String key, final String value) {
        try {
            if (value == null) {
                this.preferences.remove(key);
            } else {
                this.preferences.put(key, value);
            }
            this.preferences.flush();
        } catch (BackingStoreException ex) {
            ex.printStackTrace();
        }
    }
}

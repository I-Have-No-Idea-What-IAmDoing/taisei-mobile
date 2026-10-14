package org.taiseiproject;

import org.libsdl.app.SDLActivity;

/**
 * Main Activity for Taisei Project on Android.
 * Inherits lifecycle, surface management, and audio integration from SDLActivity.
 */
public class TaiseiActivity extends SDLActivity {

    @Override
    protected String[] getLibraries() {
        return new String[] {
            "c++_shared",
            "main"
        };
    }
}

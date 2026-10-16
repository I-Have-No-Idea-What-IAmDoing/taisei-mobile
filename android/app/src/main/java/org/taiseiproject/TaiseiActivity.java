package org.taiseiproject;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import org.libsdl.app.SDLActivity;

/**
 * Main Activity for Taisei Project on Android.
 * Inherits lifecycle, surface management, and audio integration from SDLActivity.
 */
public class TaiseiActivity extends SDLActivity {

    private static final String TAG = "TaiseiActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate: enforcing SCREEN_ORIENTATION_FULL_SENSOR");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume: enforcing SCREEN_ORIENTATION_FULL_SENSOR");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
    }

    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        Log.i(TAG, "setOrientationBis: w=" + w + ", h=" + h + ", resizable=" + resizable + ", hint=" + hint);
        if (hint != null && !hint.isEmpty() && (!hint.contains("Landscape") || !hint.contains("Portrait"))) {
            super.setOrientationBis(w, h, resizable, hint);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }

    @Override
    protected String[] getLibraries() {
        return new String[] {
            "c++_shared",
            "main"
        };
    }
}

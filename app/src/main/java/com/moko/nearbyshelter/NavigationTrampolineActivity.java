package com.moko.nearbyshelter;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;

public class NavigationTrampolineActivity extends Activity {
    private static final String TAG = "NavTrampoline";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Set window flags to wake up and show over lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        // 2. Dismiss keyguard if possible
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, null);
        }

        // 3. Start checking for screen wakeup before launching navigation
        checkScreenAndLaunch();
    }

    private void checkScreenAndLaunch() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isInteractive = pm != null && pm.isInteractive();

        if (isInteractive) {
            Log.i(TAG, "Screen is interactive, launching navigation...");
            launchNavigation();
        } else if (retryCount < 20) { // Try for 2 seconds (20 * 100ms)
            retryCount++;
            Log.d(TAG, "Screen not interactive yet, retry " + retryCount);
            handler.postDelayed(this::checkScreenAndLaunch, 100);
        } else {
            Log.w(TAG, "Timed out waiting for screen, launching anyway.");
            launchNavigation();
        }
    }

    private void launchNavigation() {
        String destinationUri = getIntent().getStringExtra("destination");
        ShelterHelper.launchNearestShelter(this, destinationUri);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}

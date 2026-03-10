package com.moko.nearbyshelter;

import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AlertListener extends NotificationListenerService {
    private static final String TAG = "AlertListener";

    private static final Set<String> PIKUD_HAOREF_PACKAGES = new HashSet<>(Arrays.asList(
            "com.idatsoft.hfc.alerts.native",
            "com.alert.meserhadash"
    ));

    private static final String[] ALERT_KEYWORDS = {
            "rocket and missile fire",
            "ירי רקטות וטילים",
            "צבע אדום",
            "חדירת כלי טיס עוין",
            "hostile aircraft intrusion"
    };

    private static final String[] WARNING_KEYWORDS = {
            "news flash",
            "התרעה",
            "דיווח"
    };

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (!PIKUD_HAOREF_PACKAGES.contains(pkg)) return;

        String title = "";
        String text = "";
        if (sbn.getNotification().extras != null) {
            title = sbn.getNotification().extras.getCharSequence("android.title", "").toString();
            text = sbn.getNotification().extras.getCharSequence("android.text", "").toString();
        }

        String content = (title + " " + text).toLowerCase();
        Log.d(TAG, "Notification received: " + content);

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        int mode = prefs.getInt(MainActivity.MODE_KEY, MainActivity.MODE_OFF);
        if (mode == MainActivity.MODE_OFF) return;

        boolean isAlert = containsAny(content, ALERT_KEYWORDS);
        boolean isWarning = containsAny(content, WARNING_KEYWORDS);

        if (mode == MainActivity.MODE_ALERT_ONLY && !isAlert) return;
        if (mode == MainActivity.MODE_WARNING_AND_ALERT && !(isAlert || isWarning)) return;

        Log.i(TAG, "Alert detected! Launching navigation flow via ShelterHelper.");
        ShelterHelper.startNavigationFlow(this);
    }

    private boolean containsAny(String content, String[] keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) return true;
        }
        return false;
    }
}

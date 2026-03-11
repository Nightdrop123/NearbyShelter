package com.moko.nearbyshelter;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ShelterHelper {
    private static final String TAG = "ShelterHelper";
    private static List<Shelter> cachedShelters = null;

    public synchronized static List<Shelter> loadSheltersFromCsv(Context context) {
        if (cachedShelters != null) return cachedShelters;
        List<Shelter> shelters = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("shelters.csv")))) {
            String line;
            reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    try {
                        double lat = Double.parseDouble(parts[1].trim());
                        double lng = Double.parseDouble(parts[2].trim());
                        String address = parts.length > 3 ? parts[3].trim() : "Unknown";
                        shelters.add(new Shelter(lat, lng, address));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Skipping invalid CSV line: " + line);
                    }
                }
            }
            cachedShelters = shelters;
        } catch (Exception e) {
            Log.e(TAG, "Error loading shelters from CSV", e);
        }
        return shelters;
    }

    public static Shelter findNearest(Location currentLoc, List<Shelter> shelters) {
        if (shelters == null || shelters.isEmpty() || currentLoc == null) return null;
        Shelter nearest = null;
        float minDistance = Float.MAX_VALUE;
        for (Shelter s : shelters) {
            float[] results = new float[1];
            Location.distanceBetween(currentLoc.getLatitude(), currentLoc.getLongitude(), s.lat, s.lng, results);
            if (results[0] < minDistance) {
                minDistance = results[0];
                nearest = s;
            }
        }
        return nearest;
    }

    public static String getNearestShelterUri(Context context, Location location) {
        if (location != null) {
            List<Shelter> shelters = loadSheltersFromCsv(context);
            Shelter nearest = findNearest(location, shelters);
            if (nearest != null) {
                Log.i(TAG, "Found target: " + nearest.lat + "," + nearest.lng);
                return "google.navigation:q=" + nearest.lat + "," + nearest.lng + "&mode=w";
            }
        }
        return "google.navigation:q=shelter&mode=w";
    }

    public static void startNavigationFlow(Context context) {
        // Use a wakelock to ensure the CPU stays alive while fetching location
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NearbyShelter:LocationLock");
        wl.acquire(10000);

        fetchNearestShelterUri(context, uri -> {
            try {
                Intent intent = new Intent(context, NavigationTrampolineActivity.class);
                intent.putExtra("destination", uri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(intent);
            } finally {
                if (wl.isHeld()) wl.release();
            }
        });
    }

    public static void launchNearestShelter(Activity activity, String destinationUri) {
        if (destinationUri != null) {
            performLaunch(activity, destinationUri);
        } else {
            fetchNearestShelterUri(activity, uri -> performLaunch(activity, uri));
        }
    }

    private static void performLaunch(Activity activity, String uriString) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // Ensure screen wakes up and CPU stays alive during the sequence
                PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "NearbyShelter:IntentLaunchLock");
                wl.acquire(5000);

                Log.i(TAG, "Starting 3-step launch sequence for: " + uriString);

                // STEP 1: Bring Google Maps app to foreground
                Intent step1 = activity.getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps");
                if (step1 != null) {
                    step1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(step1);
                }

                // STEP 2: Pre-center the map on the coordinates
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        String coords = uriString.replace("google.navigation:q=", "").split("&")[0];
                        Log.i(TAG, "Step 2: Pre-centering on " + coords);
                        Intent step2 = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + coords + "?z=17"));
                        step2.setPackage("com.google.android.apps.maps");
                        step2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(step2);
                    } catch (Exception e) { Log.e(TAG, "Step 2 failed", e); }
                }, 600);

                // STEP 3: Start the actual turn-by-turn navigation
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        Log.i(TAG, "Step 3: Delivering Navigation Intent");
                        Intent step3 = new Intent(Intent.ACTION_VIEW, Uri.parse(uriString));
                        step3.setPackage("com.google.android.apps.maps");
                        step3.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(step3);
                        
                        if (wl.isHeld()) wl.release();
                        // Delay finishing the bridge activity
                        new Handler(Looper.getMainLooper()).postDelayed(activity::finish, 3000);
                    } catch (Exception e) {
                        Log.e(TAG, "Step 3 failed", e);
                        if (wl.isHeld()) wl.release();
                        activity.finish();
                    }
                }, 1200);

            } catch (Exception e) {
                Log.e(TAG, "Handover sequence crashed", e);
                activity.finish();
            }
        });
    }

    private static void fetchNearestShelterUri(Context context, UriCallback callback) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                callback.onUriReady("google.navigation:q=shelter&mode=w");
                return;
            }

            FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
            CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(30000)
                    .build();

            client.getCurrentLocation(request, new CancellationTokenSource().getToken()).addOnCompleteListener(task -> {
                new Thread(() -> {
                    try {
                        Location location = task.isSuccessful() ? task.getResult() : null;
                        if (location == null) {
                            client.getLastLocation().addOnSuccessListener(lastLoc -> callback.onUriReady(getNearestShelterUri(context, lastLoc)));
                        } else {
                            callback.onUriReady(getNearestShelterUri(context, location));
                        }
                    } catch (Exception e) {
                        callback.onUriReady("google.navigation:q=shelter&mode=w");
                    }
                }).start();
            });
        } catch (Exception e) {
            callback.onUriReady("google.navigation:q=shelter&mode=w");
        }
    }

    private interface UriCallback {
        void onUriReady(String uri);
    }

    public static class Shelter {
        public double lat;
        public double lng;
        public String address;
        public Shelter(double lat, double lng, String address) {
            this.lat = lat;
            this.lng = lng;
            this.address = address;
        }
    }
}

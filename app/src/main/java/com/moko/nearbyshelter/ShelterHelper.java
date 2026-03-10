package com.moko.nearbyshelter;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
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
    public static final String DEFAULT_NAVIGATION_URI = "google.navigation:q=shelter";
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
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    try {
                        double lat = Double.parseDouble(parts[1]);
                        double lng = Double.parseDouble(parts[2]);
                        String address = parts.length > 3 ? parts[3] : "Unknown";
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
                return "google.navigation:q=" + nearest.lat + "," + nearest.lng;
            }
        }
        return DEFAULT_NAVIGATION_URI;
    }

    public static void startNavigationFlow(Context context) {
        fetchNearestShelterUri(context, uri -> {
            Intent intent = new Intent(context, NavigationTrampolineActivity.class);
            intent.putExtra("destination", uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        });
    }

    public static void launchNearestShelter(Activity activity, String fallbackUri) {
        fetchNearestShelterUri(activity, uri -> {
            String finalUri = (uri.equals(DEFAULT_NAVIGATION_URI) && fallbackUri != null) ? fallbackUri : uri;

            Uri navUri = Uri.parse(finalUri);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, navUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                activity.startActivity(mapIntent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch maps", e);
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=shelter"));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(fallback);
            }
            activity.finish();
        });
    }

    private static void fetchNearestShelterUri(Context context, UriCallback callback) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NearbyShelter:WakeLock");
            wakeLock.acquire(15000);
        }

        try {
            boolean hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean hasBackgroundLocation = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hasBackgroundLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
            }

            if (!hasFineLocation || !hasBackgroundLocation) {
                Log.e(TAG, "Permissions missing. Fine: " + hasFineLocation + ", Background: " + hasBackgroundLocation);
                callback.onUriReady(DEFAULT_NAVIGATION_URI);
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                return;
            }

            FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
            CancellationTokenSource cts = new CancellationTokenSource();
            CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(10000)
                    .build();

            PowerManager.WakeLock finalWakeLock = wakeLock;
            client.getCurrentLocation(request, cts.getToken()).addOnCompleteListener(task -> {
                new Thread(() -> {
                    try {
                        Location location = task.isSuccessful() ? task.getResult() : null;
                        String uri = getNearestShelterUri(context, location);
                        callback.onUriReady(uri);
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing location", e);
                        callback.onUriReady(DEFAULT_NAVIGATION_URI);
                    } finally {
                        if (finalWakeLock != null && finalWakeLock.isHeld()) {
                            finalWakeLock.release();
                        }
                    }
                }).start();
            });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException", e);
            callback.onUriReady(DEFAULT_NAVIGATION_URI);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error", e);
            callback.onUriReady(DEFAULT_NAVIGATION_URI);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
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

package com.example.android_camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnTokenCanceledListener;

public class LocationHandler {

    private float length = 0;
    private float width = 0;
    private Location mCurrentLocation;
    private FusedLocationProviderClient fusedLocationClient;
    private Context myContext;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private TextView coordinates;

    LocationHandler(Context thisContext, FusedLocationProviderClient locationClient, TextView coordinates) {
        fusedLocationClient = locationClient;
        myContext = thisContext;

        this.coordinates = coordinates;

        locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(Priority.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    String string = "Lat: " + location.getLatitude() + ", Long: " + location.getLongitude();
                    Log.println(Log.INFO, "LOCATION", string);
                    coordinates.setText(string); //set text for text view
                }
            }
        };

    }

    public void setLength(float new_len) {
        length = new_len;
        return;
    }

    public void setWidth(float new_width) {
        width = new_width;
        return;
    }

    @SuppressLint("MissingPermission")
    public void enableLocation() {
        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper());
        Log.d("LOCATION", "hi");
        return;
    }

    public void disableLocation() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        return;
    }
    @SuppressLint("MissingPermission")
    public void setReferenceLocation() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, new CancellationToken() {
                    @NonNull
                    @Override
                    public CancellationToken onCanceledRequested(@NonNull OnTokenCanceledListener onTokenCanceledListener) {
                        return null;
                    }

                    @Override
                    public boolean isCancellationRequested() {
                        return false;
                    }
                })
                .addOnSuccessListener( new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            mCurrentLocation = location;
                        }
                    }
                });
        return;
    }
}

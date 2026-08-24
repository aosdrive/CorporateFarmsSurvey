package com.gop.survey.corporatefarm.common

import android.Manifest
import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.MapView
import com.gop.survey.corporatefarm.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationTracker(
    private val activity: Activity,
    private val mapView: MapView,
    private val sharedPreferences: SharedPreferences,
    private val onMockLocationDetected: () -> Unit = {},
    private val onLocationChanged: (Location) -> Unit = {}
) {

    companion object {
        private const val TAG = "LocationTracker"
        private const val UPDATE_INTERVAL_MS = 2000L
        private const val FASTEST_INTERVAL_MS = 1000L
        private const val MAX_DELAY_MS = 3000L
        private const val MIN_DISTANCE_M = 5f
        private const val MAX_ACCEPTED_ACCURACY_M = 100f
        private const val FRESH_FIX_TIMEOUT_MS = 10_000L
        private const val ZOOM_SCALE = 1000.0
    }

    val overlay = GraphicsOverlay()

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(activity)

    private var marker: Graphic? = null
    private var isTracking = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { handleUpdate(it) }
        }
    }

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        activity, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    fun start(): Boolean {
        if (isTracking) return true
        if (!hasPermission()) return false
        if (!Utility.checkGPS(activity)) return false

        return try {
            fusedClient.requestLocationUpdates(
                buildRequest(), locationCallback, Looper.getMainLooper()
            )
            isTracking = true
            Log.d(TAG, "Location updates started")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Could not start updates: ${e.message}", e)
            false
        }
    }

    fun stop() {
        if (!isTracking) return
        fusedClient.removeLocationUpdates(locationCallback)
        isTracking = false
        Log.d(TAG, "Location updates stopped")
    }

    fun zoomToCurrentLocation(
        onStarted: () -> Unit = {},
        onFinished: () -> Unit = {},
        onUnavailable: (String) -> Unit = {}
    ) {
        if (!hasPermission()) {
            onUnavailable("Location permission is required.")
            return
        }
        if (!Utility.checkGPS(activity)) {
            Utility.buildAlertMessageNoGps(activity)
            return
        }

        onStarted()

        try {
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null && isUsable(location)) {
                        onFinished()
                        showOnMap(location)
                        centreOn(location)
                        start()
                    } else {
                        requestFreshFix(onFinished, onUnavailable)
                    }
                }
                .addOnFailureListener { requestFreshFix(onFinished, onUnavailable) }
        } catch (e: SecurityException) {
            onFinished()
            onUnavailable("Location permission is required.")
        }
    }

    private fun buildRequest(): LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .apply {
                setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
                setMaxUpdateDelayMillis(MAX_DELAY_MS)
                setWaitForAccurateLocation(false)
                setMinUpdateDistanceMeters(MIN_DISTANCE_M)
            }.build()

    private fun requestFreshFix(
        onFinished: () -> Unit,
        onUnavailable: (String) -> Unit
    ) {
        val requiredAccuracy = sharedPreferences.getInt(
            Constants.SHARED_PREF_METER_ACCURACY,
            Constants.SHARED_PREF_DEFAULT_ACCURACY
        )

        var settled = false

        val oneShot = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                if (settled) return

                if (location.accuracy < requiredAccuracy &&
                    isUsable(location) && !isMock(location)
                ) {
                    settled = true
                    fusedClient.removeLocationUpdates(this)
                    onFinished()
                    showOnMap(location)
                    centreOn(location)
                    start()
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                activity, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onFinished()
            onUnavailable("Location permission is required.")
            return
        }

        fusedClient.requestLocationUpdates(buildRequest(), oneShot, Looper.getMainLooper())

        mapView.postDelayed({
            if (!settled) {
                settled = true
                fusedClient.removeLocationUpdates(oneShot)
                onFinished()
                onUnavailable("Could not get an accurate location. Please try again.")
            }
        }, FRESH_FIX_TIMEOUT_MS)
    }

    private fun handleUpdate(location: Location) {
        try {
            if (!isUsable(location)) return

            if (isMock(location)) {
                stop()
                onMockLocationDetected()
                return
            }

            showOnMap(location)
            onLocationChanged(location)
        } catch (e: Exception) {
            Log.e(TAG, "handleUpdate failed: ${e.message}", e)
        }
    }

    private fun isUsable(location: Location): Boolean =
        location.latitude in -90.0..90.0 &&
                location.longitude in -180.0..180.0 &&
                location.accuracy < MAX_ACCEPTED_ACCURACY_M

    private fun isMock(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock
        else @Suppress("DEPRECATION") location.isFromMockProvider

    private fun showOnMap(location: Location) {
        try {
            val point = Point(
                location.longitude, location.latitude, GeometryUtils.wgs84
            )

            marker?.let { overlay.graphics.remove(it) }

            val symbol = MapSymbols.currentLocation(
                ContextCompat.getColor(activity, R.color.current_location)
            )

            marker = Graphic(point, symbol)

            if (!mapView.graphicsOverlays.contains(overlay)) {
                mapView.graphicsOverlays.add(overlay)
            }
            overlay.graphics.add(marker)
        } catch (e: Exception) {
            Log.e(TAG, "showOnMap failed: ${e.message}", e)
        }
    }

    private fun centreOn(location: Location) {
        try {
            val point = Point(location.longitude, location.latitude, GeometryUtils.wgs84)
            mapView.setViewpointAsync(Viewpoint(point, ZOOM_SCALE))
        } catch (e: Exception) {
            Log.e(TAG, "centreOn failed: ${e.message}", e)
        }
    }
}
package com.gop.survey.corporatefarm.common

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.PointCollection
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.PolygonBuilder
import com.esri.arcgisruntime.geometry.SpatialReference
import com.gop.survey.corporatefarm.R
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.system.exitProcess


class Utility {

    companion object {

        fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val theta = Math.toRadians(lon1 - lon2)
            var dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(theta)
            dist = Math.acos(dist)
            dist = Math.toDegrees(dist)
            dist *= 60.0 * 1.1515
            dist *= 1.609344
            return dist * 1000
        }

        // Nullable, and always cleared on dismiss. A non-null value here is a static
        // reference to the hosting Activity, so it must not outlive the dialog.
        private var dialog: AlertDialog? = null

        fun showProgressAlertDialog(mAct: Context, message: String) {

            // Never orphan a previous dialog: overwriting the field would leave it
            // on screen with no way to dismiss it.
            dismissProgressAlertDialog()

            val builder = AlertDialog.Builder(mAct)
            val inflater = LayoutInflater.from(mAct)
            val dialogView: View =
                inflater.inflate(R.layout.progress_indeterminate_layout, null)
            val tvMessage = dialogView.findViewById<TextView>(R.id.tv_message)
            tvMessage.text = message

            builder.setView(dialogView)
            builder.setCancelable(false) // Prevent dismissing the dialog by tapping outside

            // Create the dialog and set its style
            dialog = builder.create().also { it.show() }
        }

        fun dismissProgressAlertDialog() {
            val current = dialog ?: return
            dialog = null // drop the static reference first, even if dismiss() fails
            try {
                if (current.isShowing)
                    current.dismiss()
            } catch (e: Exception) {
                Log.w("Utility", "Progress dialog dismiss failed", e)
            }
        }

        fun dialog(activity: Context?, message: String?, title: String?) {
            val alertDialog = AlertDialog.Builder(activity)
            alertDialog.setMessage(message)
            alertDialog.setTitle(title)
            alertDialog.setCancelable(false)
            alertDialog.setPositiveButton("OK", null)
            alertDialog.create()
            alertDialog.show()
        }

        fun exitApplication(title: String?, message: String?, activity: Activity) {
            try {
                val builder = AlertDialog.Builder(activity)
                if (title != null) {
                    builder.setTitle(title)
                }
                builder.setMessage(message)
                builder.setPositiveButton(
                    "OK"
                ) { _, _ -> killApplicationProcess(activity) }
                builder.create()
                builder.show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun killApplicationProcess(activity: Activity) {
            try {
                activity.finishAffinity()
                Process.killProcess(Process.myPid())
                exitProcess(0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun checkGPS(activity: Activity): Boolean {
            val manager: LocationManager =
                activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }

        fun buildAlertMessageNoGps(activity: Activity) {
            val builder = AlertDialog.Builder(activity)
            builder.setTitle("Alert!")
            builder.setMessage(activity.resources.getString(com.gop.survey.corporatefarm.R.string.app_name) + " wants you to enable the GPS Sensor!")
                .setCancelable(false)
                .setPositiveButton(
                    "Enable"
                ) { _, _ -> activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .setNegativeButton(
                    "Cancel"
                ) { dialog, _ ->
                    dialog.cancel()
                    activity.finish()
                }
            val alert = builder.create()
            alert.show()
        }

        fun closeKeyBoard(activity: Activity?) {
            try {
                if (activity != null) {
                    val view = activity.currentFocus
                    if (view != null) {
                        val inputMethodManager =
                            activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                        inputMethodManager.hideSoftInputFromWindow(
                            activity.currentFocus!!.windowToken,
                            0
                        )
                    } else {
                        Log.w("Utility", "No view is currently in focus")
                    }
                } else {
                    Log.e("Utility", "Activity is null")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun checkInternetConnection(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }

        fun convertStringToDate(inputDate: String): String {
            try {
                val date: Date = Constants.dateFormat.parse(inputDate) ?: Date()

                return Constants.dateFormatPresentable.format(date)
            } catch (e: ParseException) {
                e.printStackTrace()
                // Handle the ParseException if necessary
                return ""
            }
        }

        fun convertStringToDateOnly(inputDate: String): String {
            try {
                val date: Date = Constants.newDateFormat.parse(inputDate) ?: Date()

                return Constants.newDateFormatPresentable.format(date)
            } catch (e: ParseException) {
                e.printStackTrace()
                // Handle the ParseException if necessary
                return ""
            }
        }

        fun writeJsonToFile(context: Context, fileName: String, jsonString: String) {
            val cacheDir = File(context.cacheDir, "cachefiles")

            // Create the cache directory if it doesn't exist
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // Create the file
            val file = File(cacheDir, fileName)

            try {
                // Write the JSON string to the file
                FileWriter(file).use { writer ->
                    writer.write(jsonString)
                }
                // Optionally, you may want to notify that the file has been written successfully
            } catch (e: IOException) {
                // Handle IOException (e.g., log an error)
                e.printStackTrace()
            }
        }

        fun convertFloorNumber(priority: Int): String {
            return when (priority) {
                1 -> "Ground Floor"
                2 -> "1st Floor"
                3 -> "2nd Floor"
                4 -> "3rd Floor"
                else -> "${priority - 1}th Floor"
            }
        }

        fun convertGpsTimeToString(gpsTime: Long): String {
            return Constants.dateFormat.format(Date(gpsTime))
        }

        fun checkTimeZone(context: Context): Boolean {
            return if (TimeZone.getDefault().id != "Asia/Karachi") {
                Toast.makeText(
                    context,
                    "Your device's time zone is not set to Pakistan Standard Time (PST). Please update your time zone to avoid issues.",
                    Toast.LENGTH_SHORT
                ).show()
                false
            } else {
                true
            }
        }

        fun simplifyPolygon(polygon: Polygon): Polygon {
            return try {
                // buffer(0.0) cleans self-intersections without distorting the shape
                val cleaned = GeometryEngine.buffer(polygon, 0.0)
                if (cleaned is Polygon && !cleaned.isEmpty) cleaned else polygon
            } catch (e: Exception) {
                Log.w("Utility", "Failed to clean polygon, returning original: ${e.message}")
                polygon
            }
        }

        fun parseWktToPolygon(wkt: String, sr: SpatialReference): Polygon? {
            return try {
                if (wkt.isBlank()) return null

                val rings = extractRingsFromWkt(wkt)
                if (rings.isEmpty()) {
                    Log.w("WKT_PARSE", "No rings extracted from: ${wkt.take(80)}")
                    return null
                }

                val builder = PolygonBuilder(sr)  // use PolygonBuilder instead of PartCollection

                for (ringCoords in rings) {
                    val points = PointCollection(sr)
                    for (coordPair in ringCoords) {
                        val tokens = coordPair.trim()
                            .split(Regex("""\s+"""))
                            .filter { it.isNotEmpty() }
                        if (tokens.size >= 2) {
                            val x = tokens[0].toDoubleOrNull()
                            val y = tokens[1].toDoubleOrNull()
                            if (x != null && y != null) {
                                points.add(Point(x, y, sr))
                            }
                        }
                    }
                    if (points.size >= 3) {
                        builder.addPart(points)  // ✅ PolygonBuilder.addPart() accepts PointCollection
                    }
                }

                val polygon = builder.toGeometry()
                if (polygon.isEmpty) null else polygon

            } catch (e: Exception) {
                Log.e("WKT_PARSE", "parseWktToPolygon failed: ${e.message}", e)
                null
            }
        }

        private fun extractRingsFromWkt(wkt: String): List<List<String>> {
            val rings = mutableListOf<List<String>>()
            val firstParen = wkt.indexOf('(')
            if (firstParen < 0) return rings

            var depth = 0
            var ringStart = -1
            var i = firstParen
            while (i < wkt.length) {
                val c = wkt[i]
                when (c) {
                    '(' -> {
                        depth++
                        ringStart = i + 1
                    }
                    ')' -> {
                        if (ringStart >= 0) {
                            val ringText = wkt.substring(ringStart, i)
                            // Only capture leaf rings (no nested parens)
                            if (!ringText.contains('(') && !ringText.contains(')')) {
                                val coords = ringText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                if (coords.isNotEmpty()) rings.add(coords)
                            }
                            ringStart = -1
                        }
                        depth--
                    }
                }
                i++
            }
            return rings
        }

        fun getMultiPolygonFromString(wkt: String, sr: SpatialReference): List<Polygon> {
            val polygon = parseWktToPolygon(wkt, sr) ?: return emptyList()
            val result = mutableListOf<Polygon>()

            for (i in 0 until polygon.parts.size) {
                val part = polygon.parts[i]

                // Extract points from the part into a PointCollection
                val points = PointCollection(sr)
                for (j in 0 until part.pointCount) {
                    points.add(part.getPoint(j))
                }

                if (points.size >= 3) {
                    val builder = PolygonBuilder(sr)
                    builder.addPart(points)
                    val subPolygon = builder.toGeometry()
                    if (!subPolygon.isEmpty) {
                        result.add(subPolygon)
                    }
                }
            }

            return result
        }

        fun getPolygonFromString(wkt: String, sr: SpatialReference): Polygon? =
            parseWktToPolygon(wkt, sr)

        fun getPolyFromString(wkt: String, sr: SpatialReference): Polygon? =
            parseWktToPolygon(wkt, sr)


        fun formatArea(acres: Double): String = when {
            acres <= 0.0        -> "—"
            acres >= 1000       -> String.format("%,.0f acres", acres)
            else                -> String.format("%.2f acres", acres)
        }

    }
}
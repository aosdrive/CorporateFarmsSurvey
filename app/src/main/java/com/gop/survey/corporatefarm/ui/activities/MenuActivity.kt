package com.gop.survey.corporatefarm.ui.activities

import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.withTransaction
import com.esri.arcgisruntime.geometry.Envelope
import com.esri.arcgisruntime.geometry.Geometry
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.gop.survey.corporatefarm.MyApplication
import com.gop.survey.corporatefarm.R
import com.gop.survey.corporatefarm.adapter.BoundaryAdapter
import com.gop.survey.corporatefarm.adapter.BoundaryItem
import com.gop.survey.corporatefarm.common.Constants
import com.gop.survey.corporatefarm.common.Resource
import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.common.TileManager
import com.gop.survey.corporatefarm.common.Utility
import com.gop.survey.corporatefarm.data.local.AppDatabase
import com.gop.survey.corporatefarm.data.remote.ServerApi
import com.gop.survey.corporatefarm.databinding.ActivityMenuBinding
import com.gop.survey.corporatefarm.domain.model.ActiveParcelEntity
import com.gop.survey.corporatefarm.domain.model.AoiBoundaryEntity
import com.gop.survey.corporatefarm.domain.use_case.auth.LogoutUseCase
import com.gop.survey.corporatefarm.presentation.base.BaseActivity
import com.gop.survey.corporatefarm.presentation.menu.MenuViewModel
import com.gop.survey.corporatefarm.presentation.util.ToastUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan


@Suppress("DEPRECATION")
@AndroidEntryPoint
class MenuActivity : BaseActivity() {

    private lateinit var binding: ActivityMenuBinding
    private val viewModel: MenuViewModel by viewModels()
    private lateinit var context: Context
    private lateinit var progressDialog: ProgressDialog
    private lateinit var progressDialogTwo: ProgressDialog

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var serverApi: ServerApi

    @Inject
    lateinit var logoutUseCase: LogoutUseCase

    private val logoutScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var job: Job? = null
    private lateinit var progressAlertDialog: AlertDialog
    private lateinit var tileManager: TileManager
    private var downloadComplete: Boolean? = null

    private val wgs84 by lazy { SpatialReferences.getWgs84() }

    companion object {
        const val TILE_FOLDER_KEY = "corporate_parcels"
        const val MIN_ZOOM = 10          // z7-z9 covers the whole country; not needed here
        private const val BATCH_SIZE = 16
        private const val UI_UPDATE_INTERVAL = 25
        private const val KB_PER_TILE = 25
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPreferences = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        applySavedTheme()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        context = this@MenuActivity
        setupActionBar()
        supportActionBar?.title = supportActionBar?.title?.toString()?.uppercase()

        binding.heroSection.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.slide_in_fade)
        )
        binding.scrollView.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.scale_in)
        )

        binding.apply {
            tvFooter.text =
                tvFooter.text.toString().replace("Version", "Version ${Constants.VERSION_NAME}")

            cvSyncData.setOnClickListener {


                if (!Utility.checkTimeZone(this@MenuActivity)) return@setOnClickListener

                if (!Utility.checkInternetConnection(this@MenuActivity)) {
                    Utility.dialog(
                        context,
                        "Please make sure you are connected to the internet and try again.",
                        "No Internet!"
                    )
                    return@setOnClickListener
                }
                fetchAndShowTehsilDialog()
            }

            // My Boundaries — switch between downloaded tehsils
            cvMyBoundaries.setOnClickListener { showBoundariesDialog() }

            cvStartSurvey.setOnClickListener {
                if (!Utility.checkTimeZone(this@MenuActivity)) return@setOnClickListener
                Intent(this@MenuActivity, SurveyFormActivity::class.java).apply {
                    startActivity(this)
                }
            }

            cvPropertyList.setOnClickListener {
                if (!Utility.checkTimeZone(this@MenuActivity)) return@setOnClickListener
                Intent(this@MenuActivity, SurveyListActivity::class.java).apply {
                    startActivity(this)
                }
            }

            cvUploadRecords.setOnClickListener {
                if (!Utility.checkTimeZone(this@MenuActivity)) return@setOnClickListener

                lifecycleScope.launch {
                    val totalPendingRecords = database.newSurveyNewDao().totalPendingCount()
                    if (totalPendingRecords > 0) {
                        Intent(this@MenuActivity, NewSavedRecordsActivity::class.java).apply {
                            startActivity(this)
                        }
                    } else {
                        ToastUtil.showShort(
                            this@MenuActivity,
                            "There are no saved records yet."
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkParcelsAndUpdateUI()
    }

    override fun onPause() {
        super.onPause()
        if (::progressAlertDialog.isInitialized && progressAlertDialog.isShowing)
            progressAlertDialog.dismiss()
        if (::progressDialog.isInitialized && progressDialog.isShowing)
            progressDialog.dismiss()
        stopLoadingParcels()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::progressDialogTwo.isInitialized && progressDialogTwo.isShowing)
            progressDialogTwo.dismiss()
        logoutScope.cancel()
    }

    private fun stopLoadingParcels() {
        job?.cancel()
        job = null
    }

    private fun fetchAndShowTehsilDialog() {
        lifecycleScope.launch {
            try {
                Utility.showProgressAlertDialog(this@MenuActivity, "Loading tehsils...")

                val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
                val response = serverApi.getTehsilNames("Bearer $token", "")

                Utility.dismissProgressAlertDialog()

                if (!response.isSuccessful) {
                    ToastUtil.showShort(
                        this@MenuActivity,
                        "Could not load tehsils. The server responded with ${response.code()}."
                    )
                    return@launch
                }

                val tehsils = response.body() ?: emptyList()
                if (tehsils.isEmpty()) {
                    ToastUtil.showShort(this@MenuActivity, "No tehsils are available.")
                    return@launch
                }

                showTehsilSelectionDialog(tehsils)
            } catch (e: Exception) {
                Utility.dismissProgressAlertDialog()
                Log.e("SYNC", "Error fetching tehsils: ${e.message}", e)
                ToastUtil.showShort(
                    this@MenuActivity,
                    "Something went wrong while loading tehsils: ${e.message}"
                )
            }
        }
    }

    private fun showTehsilSelectionDialog(tehsils: List<String>) {
        val view = LayoutInflater.from(this@MenuActivity)
            .inflate(R.layout.dialog_mouza_list, null)

        val spinner = view.findViewById<Spinner>(R.id.spn_kachi_abadis)
        spinner.adapter = ArrayAdapter(
            this@MenuActivity, R.layout.spinner_item_drop_down, tehsils
        )

        val dialog = AlertDialog.Builder(this@MenuActivity)
            .setTitle(getString(R.string.select_a_tehsil))
            .setView(view)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.download_boundary)) { _, _ ->
                val selectedTehsil = tehsils[spinner.selectedItemPosition]
                Log.d("SYNC", "Selected tehsil: $selectedTehsil")
                sharedPreferences.edit().putString("selected_tehsil", selectedTehsil).apply()
                downloadAoiBoundaryAndTiles(selectedTehsil)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun downloadAoiBoundaryAndTiles(tehsil: String) {
        lifecycleScope.launch {
            try {
                Utility.showProgressAlertDialog(
                    this@MenuActivity, "Downloading boundary for $tehsil..."
                )
                val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
                val result = fetchAndStoreAoiBoundary(token, tehsil)
                Utility.dismissProgressAlertDialog()

                if (result is Resource.Error) {
                    ToastUtil.showShort(
                        this@MenuActivity,
                        result.message ?: "The boundary could not be downloaded."
                    )
                    return@launch
                }

                selectTehsil(tehsil)
                Utility.showProgressAlertDialog(this@MenuActivity, "Restoring surveyed parcels...")
                val restored = syncSurveyedParcelsForTehsil(token, tehsil)
                Utility.dismissProgressAlertDialog()
                when {
                    restored > 0 -> ToastUtil.showShort(
                        this@MenuActivity, "$restored surveyed parcel(s) restored."
                    )
                    restored == 0 -> ToastUtil.showShort(
                        this@MenuActivity, "No parcels have been surveyed in this tehsil yet."
                    )
                }
                prepareTileDownload(tehsil)
            } catch (e: Exception) {
                Utility.dismissProgressAlertDialog()
                Log.e("SYNC", "Error: ${e.message}", e)
                ToastUtil.showShort(this@MenuActivity, "Something went wrong: ${e.message}")
            }
        }
    }

    private suspend fun syncSurveyedParcelsForTehsil(
        token: String,
        tehsil: String
    ): Int = withContext(Dispatchers.IO) {
        try {
            val response = serverApi.getSurveyedParcelsByTehsil("Bearer $token", tehsil)

            if (!response.isSuccessful) {
                Log.w("PARCEL_SYNC", "Server responded ${response.code()}")
                return@withContext -1
            }

            val text = response.body()?.string().orEmpty()

            // Id || Parcel_No || SubParcelNo || S_Status || AttachedSurveyId || IsDrawn || WKT
            //  0        1             2             3              4              5        6
            val entities = mutableListOf<ActiveParcelEntity>()
            text.split("\n").forEach { line ->
                if (line.isBlank()) return@forEach
                val p = line.split("||")
                if (p.size < 7) return@forEach

                val id = p[0].trim().toLongOrNull() ?: return@forEach
                val wkt = p[6].trim()
                if (wkt.isBlank()) return@forEach

                val status = p[3].trim()
                val surveyId = p[4].trim().takeIf {
                    it.isNotBlank() && it != "00000000-0000-0000-0000-000000000000"
                }

                entities.add(
                    ActiveParcelEntity(
                        pkid = 0,
                        id = id,
                        parcelNo = p[1].trim(),
                        subParcelNo = p[2].trim(),
                        mauzaId = 0L,
                        mauzaName = tehsil,
                        khewatInfo = "0",
                        areaAssigned = tehsil,
                        geomWKT = wkt,
                        centroid = "",
                        surveyStatusCode = if (status.equals("Surveyed", true)) 2 else 1,
                        surveyId = surveyId,
                        isActivate = true,
                        tehsil = tehsil,
                        isLocallyDrawn = p[5].trim() == "1"
                    )
                )
            }

            // Keep anything that has not been uploaded yet
            val pendingIds = database.newSurveyNewDao().getPendingParcelIds()

            database.withTransaction {
                database.activeParcelDao().deleteParcelsForTehsilExcept(tehsil, pendingIds)
                if (entities.isNotEmpty()) {
                    database.activeParcelDao().insertActiveParcels(entities)
                }
            }

            Log.d(
                "PARCEL_SYNC",
                "Restored ${entities.size} surveyed parcel(s) for $tehsil " +
                        "(kept ${pendingIds.size} pending)"
            )
            entities.size
        } catch (e: Exception) {
            Log.e("PARCEL_SYNC", "Error: ${e.message}", e)
            -1
        }
    }


    private fun selectTehsil(tehsil: String) {
        sharedPreferences.edit()
            .putString(Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, tehsil)
            .putString(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME, tehsil)
            .putString(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, tehsil)
            .putString(Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name, tehsil)
            .putLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0L)
            .apply()
    }

    private suspend fun fetchAndStoreAoiBoundary(
        token: String,
        tehsil: String
    ): SimpleResource = withContext(Dispatchers.IO) {
        try {
            val response = serverApi.getAOIByTehsil("Bearer $token", tehsil)

            if (!response.isSuccessful) {
                return@withContext Resource.Error(
                    if (response.code() == 404)
                        "No boundary is available on the server for this tehsil."
                    else
                        "Server error: ${response.code()}"
                )
            }

            val text = response.body()?.string().orEmpty()
            if (text.isBlank())
                return@withContext Resource.Error("The server returned an empty boundary.")

            // Format: Tehsil||The_Id||District||Area||WKT
            val entities = mutableListOf<AoiBoundaryEntity>()
            text.split("\n").forEach { line ->
                if (line.isBlank()) return@forEach
                val p = line.split("||")
                if (p.size < 5) return@forEach

                val wkt = p[4].trim()
                if (wkt.isBlank() || !wkt.contains("POLYGON", ignoreCase = true)) return@forEach

                entities.add(
                    AoiBoundaryEntity(
                        tehsil = p[0].trim().ifBlank { tehsil },
                        theId = p[1].trim(),
                        district = p[2].trim(),
                        area = p[3].trim().toDoubleOrNull() ?: 0.0,
                        geomWKT = wkt
                    )
                )
            }

            if (entities.isEmpty())
                return@withContext Resource.Error("No valid boundary geometry was found.")

            database.withTransaction {
                database.aoiBoundaryDao().deleteByTehsil(tehsil)
                database.aoiBoundaryDao().insertAll(entities)
            }
            Log.d("AOI_BOUNDARY", "Saved ${entities.size} boundary part(s) for $tehsil")

            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e("AOI_BOUNDARY", "Error: ${e.message}", e)
            Resource.Error("The boundary download failed: ${e.localizedMessage}")
        }
    }


    private fun prepareTileDownload(tehsil: String) {
        lifecycleScope.launch {
            Utility.showProgressAlertDialog(this@MenuActivity, "Calculating map size...")

            val boundary = withContext(Dispatchers.IO) { loadBoundaryUnion(tehsil) }
            if (boundary == null) {
                Utility.dismissProgressAlertDialog()
                ToastUtil.showShort(
                    this@MenuActivity,
                    "No valid boundary geometry was found for this tehsil."
                )
                return@launch
            }

            // Real tile count for each quality option
            val options = withContext(Dispatchers.Default) {
                listOf(14, 15, 16).map { maxZ ->
                    maxZ to buildTileList(boundary, MIN_ZOOM, maxZ).size
                }
            }
            Utility.dismissProgressAlertDialog()

            val labels = options.map { (maxZ, count) ->
                val quality = when (maxZ) {
                    14 -> "Basic"
                    15 -> "Good  (recommended)"
                    else -> "Best"
                }
                val mb = (count * KB_PER_TILE) / 1024
                "$quality\n$count tiles  ·  about $mb MB"
            }.toTypedArray()

            var selected = 1   // default = zoom 15

            val dialog = AlertDialog.Builder(this@MenuActivity)
                .setTitle("Map Detail Level")
                .setCancelable(false)
                .setSingleChoiceItems(labels, selected) { _, which -> selected = which }
                .setPositiveButton("Download") { d, _ ->
                    d.dismiss()
                    val maxZoom = options[selected].first
                    sharedPreferences.edit()
                        .putInt(Constants.SHARED_PREF_MAP_MIN_SCALE, MIN_ZOOM)
                        .putInt(Constants.SHARED_PREF_MAP_MAX_SCALE, maxZoom)
                        .apply()
                    startTileDownload(tehsil, boundary, MIN_ZOOM, maxZoom)
                }
                .setNegativeButton("Cancel", null)
                .create()

            dialog.show()
            styleDialogButtons(dialog)
        }
    }

    private suspend fun loadBoundaryUnion(tehsil: String): Geometry? =
        withContext(Dispatchers.IO) {
            val rows = database.aoiBoundaryDao().getByTehsil(tehsil)
            if (rows.isEmpty()) return@withContext null

            val polys = mutableListOf<Polygon>()
            rows.forEach { b ->
                try {
                    when {
                        b.geomWKT.contains("MULTIPOLYGON") ->
                            Utility.getMultiPolygonFromString(b.geomWKT, wgs84)
                                .mapTo(polys) { Utility.simplifyPolygon(it) }

                        b.geomWKT.contains("POLYGON ((") ->
                            Utility.getPolygonFromString(b.geomWKT, wgs84)
                                ?.let { polys.add(Utility.simplifyPolygon(it)) }

                        b.geomWKT.contains("POLYGON") ->
                            Utility.getPolyFromString(b.geomWKT, wgs84)
                                ?.let { polys.add(Utility.simplifyPolygon(it)) }
                    }
                } catch (e: Exception) {
                    Log.e("TileDownload", "Boundary geometry error: ${e.message}")
                }
            }

            if (polys.isEmpty()) null else GeometryEngine.union(polys)
        }


    private fun startTileDownload(
        tehsil: String,
        boundary: Geometry,
        minZoom: Int,
        maxZoom: Int
    ) {
        downloadComplete = false
        progressAlertDialog = AlertDialog.Builder(this@MenuActivity)
            .setTitle("Please wait")
            .setMessage("Preparing download...")
            .setCancelable(false)
            .create()
        tileManager = TileManager(this)
        progressAlertDialog.show()

        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val tileList = buildTileList(boundary, minZoom, maxZoom)
                val total = tileList.size
                Log.d("TileDownload", "Clipped tiles: $total  (z$minZoom-$maxZoom)")

                withContext(Dispatchers.Main) {
                    progressAlertDialog.setMessage("Downloading 0 of $total tiles...")
                }

                if (!Utility.checkInternetConnection(this@MenuActivity)) {
                    withContext(Dispatchers.Main) {
                        progressAlertDialog.dismiss()
                        Utility.dialog(
                            context,
                            "Please check your internet connection and try again.",
                            "No Internet!"
                        )
                    }
                    return@launch
                }

                val outputFolder = File(context.filesDir, "MapTiles/$TILE_FOLDER_KEY")
                val done = AtomicInteger(0)
                var lastReported = 0

                tileList.chunked(BATCH_SIZE).forEach { batch ->
                    batch.map { (z, x, y) ->
                        launch {
                            tileManager.downloadAndSaveTileDirect(
                                z, y, x, outputFolder, minZoom, maxZoom
                            )
                            val cur = done.incrementAndGet()
                            if (cur - lastReported >= UI_UPDATE_INTERVAL || cur == total) {
                                lastReported = cur
                                withContext(Dispatchers.Main) {
                                    if (!isFinishing && !isDestroyed)
                                        progressAlertDialog.setMessage(
                                            "Downloading $cur of $total tiles..."
                                        )
                                }
                            }
                        }
                    }.forEach { it.join() }
                }

                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    progressAlertDialog.dismiss()

                    if (done.get() == total && total > 0) {
                        sharedPreferences.edit()
                            .putInt(
                                Constants.SHARED_PREF_SYNC_STATUS,
                                Constants.SYNC_STATUS_SUCCESS
                            )
                            .apply()

                        binding.apply {
                            cvStartSurvey.isEnabled = true
                            cvPropertyList.isEnabled = true
                            cvStartSurvey.alpha = 1.0f
                            cvPropertyList.alpha = 1.0f
                            tvSelectedAbadiName.text = tehsil
                            llSelectedAbadi.visibility = View.VISIBLE
                        }

                        Toast.makeText(
                            context,
                            "Sync complete. You can now start the survey.",
                            Toast.LENGTH_LONG
                        ).show()
                        downloadComplete = true
                    } else {
                        downloadComplete = false
                        ToastUtil.showShort(
                            this@MenuActivity,
                            "Download incomplete. Please try again — tiles that were already downloaded will be skipped."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("TileDownload", "Error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (::progressAlertDialog.isInitialized && progressAlertDialog.isShowing)
                        progressAlertDialog.dismiss()
                    ToastUtil.showShort(
                        this@MenuActivity,
                        "The map download failed: ${e.message}"
                    )
                }
            }
        }
    }


    private fun tileLon(x: Int, z: Int): Double =
        x.toDouble() / (1 shl z).toDouble() * 360.0 - 180.0

    private fun tileLat(y: Int, z: Int): Double {
        val n = Math.PI - 2.0 * Math.PI * y.toDouble() / (1 shl z).toDouble()
        return Math.toDegrees(atan(sinh(n)))
    }

    private fun tileEnvelope(z: Int, x: Int, y: Int): Envelope = Envelope(
        tileLon(x, z), tileLat(y + 1, z), tileLon(x + 1, z), tileLat(y, z), wgs84
    )


    private fun buildTileList(
        boundary: Geometry,
        minZoom: Int,
        maxZoom: Int
    ): List<Triple<Int, Int, Int>> {

        // Grow the boundary slightly (~150 m) so edge tiles are not missed
        val testGeom = try {
            GeometryEngine.buffer(boundary, 0.0015) ?: boundary
        } catch (e: Exception) {
            boundary
        }

        val extent = testGeom.extent
        val tiles = mutableListOf<Triple<Int, Int, Int>>()

        for (z in minZoom..maxZoom) {
            val minX = minOf(getTileX(extent.xMin, z), getTileX(extent.xMax, z))
            val maxX = maxOf(getTileX(extent.xMin, z), getTileX(extent.xMax, z))
            val minY = minOf(getTileY(extent.yMin, z), getTileY(extent.yMax, z))
            val maxY = maxOf(getTileY(extent.yMin, z), getTileY(extent.yMax, z))

            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    // At low zooms a tile is larger than the boundary, so the check is pointless
                    if (z <= 11) {
                        tiles.add(Triple(z, x, y))
                    } else if (GeometryEngine.intersects(testGeom, tileEnvelope(z, x, y))) {
                        tiles.add(Triple(z, x, y))
                    }
                }
            }
        }
        return tiles
    }

    private fun getTileX(lon: Double, zoom: Int): Int {
        var x = floor((lon + 180) / 360 * (1 shl zoom)).toInt()
        if (x < 0) x = 0
        if (x >= (1 shl zoom)) x = (1 shl zoom) - 1
        return x
    }

    private fun getTileY(lat: Double, zoom: Int): Int {
        var y = floor(
            (1 - ln(tan(Math.toRadians(lat)) + 1 / cos(Math.toRadians(lat))) / Math.PI) / 2 *
                    (1 shl zoom)
        ).toInt()
        if (y < 0) y = 0
        if (y >= (1 shl zoom)) y = (1 shl zoom) - 1
        return y
    }


    private fun showBoundariesDialog() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val all = database.aoiBoundaryDao().getAll()
                all.groupBy { it.tehsil }.map { (tehsil, rows) ->
                    BoundaryItem(
                        tehsil = tehsil,
                        district = rows.firstOrNull()?.district.orEmpty(),
                        parts = rows.size,
                        area = rows.sumOf { it.area },
                        parcelCount = database.activeParcelDao()
                            .getActiveParcelsByAoi(tehsil).size
                    )
                }.sortedBy { it.tehsil }
            }

            if (items.isEmpty()) {
                val d = AlertDialog.Builder(this@MenuActivity)
                    .setTitle("No Boundaries")
                    .setMessage("No boundaries have been downloaded yet. Use \"Download Data\" to download a tehsil boundary first.")
                    .setPositiveButton("OK", null)
                    .create()
                d.show()
                styleDialogButtons(d)
                return@launch
            }

            val view = LayoutInflater.from(this@MenuActivity)
                .inflate(R.layout.dialog_boundaries, null)

            val dialog = AlertDialog.Builder(this@MenuActivity)
                .setView(view)
                .create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            view.findViewById<TextView>(R.id.tv_dialog_subtitle).text =
                "${items.size} tehsil${if (items.size > 1) "s" else ""} downloaded"

            view.findViewById<TextView>(R.id.btn_dialog_close)
                .setOnClickListener { dialog.dismiss() }

            val current = sharedPreferences.getString(
                Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, ""
            ) ?: ""

            val rv = view.findViewById<RecyclerView>(R.id.rv_boundaries)
            rv.layoutManager = LinearLayoutManager(this@MenuActivity)
            rv.adapter = BoundaryAdapter(items, current) { picked ->
                selectTehsil(picked.tehsil)
                dialog.dismiss()

                lifecycleScope.launch {
                    if (Utility.checkInternetConnection(this@MenuActivity)) {
                        Utility.showProgressAlertDialog(this@MenuActivity, "Refreshing parcels...")
                        val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
                        syncSurveyedParcelsForTehsil(token, picked.tehsil)
                        Utility.dismissProgressAlertDialog()
                    }
                    ToastUtil.showShort(this@MenuActivity, "Selected: ${picked.tehsil}")
                    checkParcelsAndUpdateUI()
                }
            }

            dialog.show()
        }
    }


    private fun checkParcelsAndUpdateUI() {
        lifecycleScope.launch {
            try {
                checkDatabaseAndFileStatus()
            } catch (e: Exception) {
                Log.e("MenuActivity", "Status check failed: ${e.message}", e)
                ToastUtil.showShort(context, "Could not refresh the menu: ${e.message}")
            }
        }
    }

    private suspend fun checkDatabaseAndFileStatus() {

        val loginStatus = sharedPreferences.getInt(
            Constants.SHARED_PREF_LOGIN_STATUS, Constants.LOGIN_STATUS_INACTIVE
        )

        if (loginStatus == Constants.LOGIN_STATUS_INACTIVE) {
            Intent(this@MenuActivity, AuthActivity::class.java).apply {
                startActivity(this)
                finish()
            }
            return
        }

        val userName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_NAME, Constants.SHARED_PREF_DEFAULT_STRING
        )

        val selectedTehsil = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, ""
        ) ?: ""

        // If the selected tehsil has no boundary, fall back to the first one available
        val activeTehsil = withContext(Dispatchers.IO) {
            when {
                selectedTehsil.isNotBlank() &&
                        database.aoiBoundaryDao().getByTehsil(selectedTehsil).isNotEmpty() ->
                    selectedTehsil

                else -> database.aoiBoundaryDao().getAllTehsils().firstOrNull()?.also { fallback ->
                    sharedPreferences.edit()
                        .putString(Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, fallback)
                        .putString(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, fallback)
                        .apply()
                    Log.d("MenuActivity", "Auto-selected boundary: $fallback")
                }
            }
        }

        val boundaryCount = withContext(Dispatchers.IO) {
            database.aoiBoundaryDao().getAllTehsils().size
        }

        val drawnCount = withContext(Dispatchers.IO) {
            if (activeTehsil.isNullOrBlank()) 0
            else database.activeParcelDao().getActiveParcelsByAoi(activeTehsil).size
        }

        val aoiArea = withContext(Dispatchers.IO) {
            if (activeTehsil.isNullOrBlank()) 0.0
            else database.aoiBoundaryDao().getByTehsil(activeTehsil).sumOf { it.area }
        }

        val tileFolder = File(context.filesDir, "MapTiles/$TILE_FOLDER_KEY")
        val ready = !activeTehsil.isNullOrBlank() && tileFolder.exists()

        withContext(Dispatchers.Main) {
            binding.apply {
                tvUserName.text = userName

                tvBoundariesSubtitle.text = if (boundaryCount > 0)
                    "$boundaryCount downloaded — tap to switch"
                else
                    "No boundaries downloaded yet"

                if (ready) {

                    if (aoiArea > 0) {
                        tvAoiArea.text = "· ${Utility.formatArea(aoiArea)}"
                        tvAoiArea.visibility = View.VISIBLE
                    } else {
                        tvAoiArea.visibility = View.GONE
                    }

                    tvSelectedAbadiName.text = when (drawnCount) {
                        0 -> activeTehsil
                        1 -> "$activeTehsil (1 parcel)"
                        else -> "$activeTehsil ($drawnCount parcels)"
                    }
                    llSelectedAbadi.visibility = View.VISIBLE

                    cvStartSurvey.isEnabled = true
                    cvStartSurvey.isFocusable = true
                    cvPropertyList.isEnabled = true
                    cvPropertyList.isFocusable = true
                } else {
                    llSelectedAbadi.visibility = View.INVISIBLE

                    cvStartSurvey.isEnabled = false
                    cvStartSurvey.isFocusable = false
                    cvPropertyList.isEnabled = false
                    cvPropertyList.isFocusable = false
                }
                cvStartSurvey.alpha = 1.0f
                cvPropertyList.alpha = 1.0f
            }
        }
    }


    private fun startLogout() {
        val gray = Color.GRAY
        val yesText = SpannableString("Yes").apply {
            setSpan(ForegroundColorSpan(gray), 0, length, 0)
        }
        val noText = SpannableString("No").apply {
            setSpan(ForegroundColorSpan(gray), 0, length, 0)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Log Out")
            .setCancelable(false)
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton(yesText) { _, _ ->
                Utility.showProgressAlertDialog(this, "Logging out, please wait...")
                performLogout()
            }
            .setNegativeButton(noText, null)
            .create()

        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun performLogout() {
        val userId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_ID, Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        if (userId == Constants.SHARED_PREF_DEFAULT_INT.toLong()) {
            Utility.dismissProgressAlertDialog()
            clearLocalDataAndNavigate()
            return
        }

        logoutScope.launch {
            try {
                logoutUseCase(userId, "Android").collect { result ->
                    when (result) {
                        is Resource.Success<*> -> {
                            Utility.dismissProgressAlertDialog()
                            ToastUtil.showShort(this@MenuActivity, "You have been logged out.")
                            delay(800)
                            clearLocalDataAndNavigate()
                        }

                        is Resource.Error<*> -> {
                            Utility.dismissProgressAlertDialog()
                            Toast.makeText(
                                this@MenuActivity,
                                "Could not reach the server, so you were logged out on this device only.",
                                Toast.LENGTH_SHORT
                            ).show()
                            clearLocalDataAndNavigate()
                        }

                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                Utility.dismissProgressAlertDialog()
                Log.e("LOGOUT", "Logout exception: ${e.message}", e)
                ToastUtil.showShort(this@MenuActivity, "Your session has ended.")
                delay(800)
                clearLocalDataAndNavigate()
            }
        }
    }

    private fun clearLocalDataAndNavigate() {
        sharedPreferences.edit()
            .putInt(Constants.SHARED_PREF_LOGIN_STATUS, Constants.LOGIN_STATUS_INACTIVE)
            .putLong(Constants.SHARED_PREF_USER_ID, Constants.SHARED_PREF_DEFAULT_INT.toLong())
            .putString(Constants.SHARED_PREF_USER_CNIC, Constants.SHARED_PREF_DEFAULT_STRING)
            .putString(Constants.SHARED_PREF_USER_NAME, Constants.SHARED_PREF_DEFAULT_STRING)
            .putString(Constants.SHARED_PREF_TOKEN, Constants.SHARED_PREF_DEFAULT_STRING)
            .apply()

        Intent(this, AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(this)
            finish()
        }
    }

    private fun styleDialogButtons(dialog: AlertDialog) {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return try {
            menuInflater.inflate(R.menu.item_options_menu, menu)
            menu?.let { updateMenuIconColors(it) }
            true
        } catch (e: Exception) {
            Log.e("MenuActivity", "Error creating options menu: ${e.message}")
            super.onCreateOptionsMenu(menu)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this@MenuActivity, SettingsActivity::class.java))
                true
            }

            R.id.action_theme -> {
                showThemeDialog()
                true
            }

            R.id.action_logout -> {
                startLogout()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showThemeDialog() {
        try {
            val options = arrayOf("Light", "Dark", "System Default")
            val currentMode = when (AppCompatDelegate.getDefaultNightMode()) {
                AppCompatDelegate.MODE_NIGHT_NO -> 0
                AppCompatDelegate.MODE_NIGHT_YES -> 1
                else -> 2
            }

            AlertDialog.Builder(this)
                .setTitle("Choose Theme")
                .setSingleChoiceItems(options, currentMode) { dialog, which ->
                    try {
                        val newMode = when (which) {
                            0 -> AppCompatDelegate.MODE_NIGHT_NO
                            1 -> AppCompatDelegate.MODE_NIGHT_YES
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                        MyApplication.updateTheme(this, newMode)
                        dialog.dismiss()
                        recreateWithDelay()
                    } catch (e: Exception) {
                        Log.e("MenuActivity", "Error applying theme: ${e.message}")
                        dialog.dismiss()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("MenuActivity", "Error showing theme dialog: ${e.message}")
        }
    }

    private fun recreateWithDelay() {
        Handler(Looper.getMainLooper()).postDelayed({ recreate() }, 100)
    }

    private fun applySavedTheme() {
        AppCompatDelegate.setDefaultNightMode(
            sharedPreferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        )
    }

    private fun setupActionBar() {
        try {
            supportActionBar?.apply {
                setDisplayShowTitleEnabled(true)
                elevation = 4f
                title = title?.toString()?.uppercase()
            }
            setupStatusBar()
        } catch (e: Exception) {
            Log.e("MenuActivity", "Error setting up action bar: ${e.message}")
        }
    }

    private fun setupStatusBar() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

                window.statusBarColor = if (isDarkModeActive())
                    ContextCompat.getColor(this, R.color.dark_primary)
                else
                    ContextCompat.getColor(this, R.color.forest_green)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val flags = window.decorView.systemUiVisibility
                    window.decorView.systemUiVisibility =
                        flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }
        } catch (e: Exception) {
            Log.e("MenuActivity", "Error setting up status bar: ${e.message}")
        }
    }

    private fun isDarkModeActive(): Boolean = try {
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
    } catch (e: Exception) {
        false
    }

    private fun updateMenuIconColors(menu: Menu) {
        val iconColor = ContextCompat.getColor(this, R.color.white)
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon?.let { icon ->
                val wrapped = DrawableCompat.wrap(icon)
                DrawableCompat.setTint(wrapped, iconColor)
                menu.getItem(i).icon = wrapped
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupStatusBar()
        invalidateOptionsMenu()
    }
}
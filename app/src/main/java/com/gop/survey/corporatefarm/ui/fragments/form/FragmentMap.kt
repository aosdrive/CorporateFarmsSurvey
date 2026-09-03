package com.gop.survey.corporatefarm.ui.fragments.form

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.geometry.Envelope
import com.esri.arcgisruntime.geometry.Geometry
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.Callout
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.mapping.view.ViewpointChangedListener
import com.gop.survey.corporatefarm.R
import com.gop.survey.corporatefarm.common.Constants
import com.gop.survey.corporatefarm.common.CustomTileLayer
import com.gop.survey.corporatefarm.common.DialogUtils
import com.gop.survey.corporatefarm.common.DownloadType
import com.gop.survey.corporatefarm.common.DrawResult
import com.gop.survey.corporatefarm.common.GeometryUtils
import com.gop.survey.corporatefarm.common.LocationTracker
import com.gop.survey.corporatefarm.common.MapSymbols
import com.gop.survey.corporatefarm.common.MapTileInfoFactory
import com.gop.survey.corporatefarm.common.ParcelDrawController
import com.gop.survey.corporatefarm.common.TileManager
import com.gop.survey.corporatefarm.common.Utility
import com.gop.survey.corporatefarm.data.local.AppDatabase
import com.gop.survey.corporatefarm.databinding.FragmentMapBinding
import com.gop.survey.corporatefarm.domain.model.ActiveParcelEntity
import com.gop.survey.corporatefarm.presentation.form.SharedFormViewModel
import com.gop.survey.corporatefarm.presentation.util.SnapUtils
import com.gop.survey.corporatefarm.presentation.util.ToastUtil
import com.gop.survey.corporatefarm.ui.activities.MenuActivity
import com.gop.survey.corporatefarm.ui.activities.SurveyActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class FragmentMap : Fragment() {

    companion object {
        private const val TAG = "FragmentMap"
        private const val TILE_FOLDER_KEY = "corporate_parcels"
        private const val LABEL_VISIBLE_BELOW_SCALE = 5000.0
        private const val PERMISSION_REQUEST_CODE = 200
        private const val LICENSE = "runtimelite,1000,rud5883837740,none,ZZ0RJAY3FLCB0YRJD136"
    }

    @Inject
    lateinit var sharedPreferences: SharedPreferences
    @Inject
    lateinit var database: AppDatabase
    private val viewModel: SharedFormViewModel by activityViewModels()
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var refreshReceiver: BroadcastReceiver
    private var loadJob: Job? = null
    // ===== Overlays =====
    private lateinit var parcelOverlay: GraphicsOverlay
    private lateinit var labelOverlay: GraphicsOverlay
    private val boundaryOverlay = GraphicsOverlay()
    // ===== Location =====
    private var locationTracker: LocationTracker? = null
    // ===== Callout =====
    private lateinit var callout: Callout
    private lateinit var calloutView: View
    private lateinit var viewpointListener: ViewpointChangedListener
    private lateinit var drawController: ParcelDrawController
    private var boundaryUnion: Geometry? = null
    private var snapTargets: List<Polygon> = emptyList()
    private var snapTargetsSr: com.esri.arcgisruntime.geometry.SpatialReference? = null
    private val snapToleranceDp = 24.0
    private fun currentTehsil(): String =
        sharedPreferences.getString(Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, "") ?: ""

    // ==================================================================
    // LIFECYCLE
    // ==================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = goToMenu()
            }
        )

        setHeaderText()
        setupLocationTracker()

        drawController = ParcelDrawController(
            mapView = binding.parcelMapview,
            isInsideExistingParcel = { point -> isPointInsideExistingParcel(point) },
            snapPoint = { point -> snapPoint(point) }
        )
    }

    override fun onResume() {
        super.onResume()

        if (!Utility.checkInternetConnection(requireContext())) {
            ToastUtil.showShort(requireContext(), "Offline mode: showing locally saved parcels.")
        }

        closeCallout()
        registerRefreshReceiver()
        Utility.closeKeyBoard(requireActivity())

        try {
            viewpointListener = ViewpointChangedListener { toggleLabelsForZoom() }
            loadMap(showLabels = false)
            _binding?.parcelMapview?.resume()
            locationTracker?.start()
        } catch (e: Exception) {
            Log.e(TAG, "onResume failed: ${e.message}", e)
            ToastUtil.showLong(requireContext(), "Please restart the map screen.")
        }
    }

    override fun onPause() {
        _binding?.parcelMapview?.pause()
        super.onPause()

        try {
            LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(refreshReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Receiver unregister failed: ${e.message}")
        }

        locationTracker?.stop()
        cancelLoad()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.parcelMapview?.dispose()
        _binding = null
    }

    private fun goToMenu() {
        Intent(requireContext(), MenuActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(this)
            requireActivity().finish()
        }
    }

    private fun cancelLoad() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun registerRefreshReceiver() {
        refreshReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "REFRESH_MAP") refreshMap()
            }
        }
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(refreshReceiver, IntentFilter("REFRESH_MAP"))
    }

    private fun toggleLabelsForZoom() {
        if (_binding == null || !::labelOverlay.isInitialized) return
        val overlays = binding.parcelMapview.graphicsOverlays
        val shouldShow = binding.parcelMapview.mapScale < LABEL_VISIBLE_BELOW_SCALE

        if (shouldShow && !overlays.contains(labelOverlay)) overlays.add(labelOverlay)
        if (!shouldShow && overlays.contains(labelOverlay)) overlays.remove(labelOverlay)
    }

    // ==================================================================
    // MAP LOADING
    // ==================================================================

    private fun loadMap(showLabels: Boolean) {
        if (Constants.MAP_DOWNLOAD_TYPE == DownloadType.TILES) loadMapTiles(showLabels)
    }

    @SuppressLint("SetTextI18n")
    private fun loadMapTiles(showLabels: Boolean) {
        if (_binding == null) return

        parcelOverlay = GraphicsOverlay()
        labelOverlay = GraphicsOverlay()
        binding.parcelMapview.graphicsOverlays.clear()

        showLoading(true)

        // Use the same zoom range that was actually downloaded
        val minZoom = sharedPreferences.getInt(Constants.SHARED_PREF_MAP_MIN_SCALE, 10)
        val maxZoom = sharedPreferences.getInt(Constants.SHARED_PREF_MAP_MAX_SCALE, 15)
        val tehsil = currentTehsil()

        loadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Loading map for tehsil=$tehsil (z$minZoom-$maxZoom)")

                val parcels = if (tehsil.isBlank()) emptyList()
                else database.activeParcelDao().getActiveParcelsByAoi(tehsil)

                val parcelPolygons = renderParcels(parcels)
                val boundaryPolygons = loadBoundaryPolygons(tehsil)

                val extentSource =
                    if (boundaryPolygons.isNotEmpty()) boundaryPolygons else parcelPolygons

                if (extentSource.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext
                        showLoading(false)
                        ToastUtil.showLong(
                            requireContext(),
                            "No boundary found. Please download a boundary from Download Data first."
                        )
                    }
                    return@launch
                }

                val extent = buildWebMercatorExtent(extentSource)

                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    showCounts(parcels)
                    attachBasemap(extent, minZoom, maxZoom)
                    attachOverlays(showLabels)
                    drawBoundary(boundaryPolygons)
                    setupMapInteractions()
                    showLoading(false)
                    Log.d(TAG, "Map ready: ${parcels.size} parcel(s)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Map load failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    showLoading(false)
                    ToastUtil.showLong(requireContext(), "Map error: ${e.message}")
                }
            }
        }
    }

    /** Draws each parcel and returns the polygons for extent calculation. */
    private suspend fun renderParcels(parcels: List<ActiveParcelEntity>): List<Polygon> {
        val polygons = mutableListOf<Polygon>()

        parcels.forEach { parcel ->
            GeometryUtils.parseAndSimplify(parcel.geomWKT).forEach { polygon ->
                polygons.add(polygon)
                withContext(Dispatchers.Main) { addParcelGraphics(parcel, polygon) }
            }
        }
        return polygons
    }

    private suspend fun loadBoundaryPolygons(tehsil: String): List<Polygon> {
        if (tehsil.isBlank()) return emptyList()
        return database.aoiBoundaryDao().getByTehsil(tehsil)
            .flatMap { GeometryUtils.parsePolygons(it.geomWKT) }
    }

    private fun buildWebMercatorExtent(polygons: List<Polygon>): Envelope {
        val combined = GeometryEngine.union(polygons)
        val buffered = GeometryEngine.buffer(combined.extent, 0.0001) as Polygon
        return GeometryEngine.project(buffered.extent, GeometryUtils.webMercator) as Envelope
    }

    private fun attachBasemap(extent: Envelope, minZoom: Int, maxZoom: Int) {
        val map = try {
            val layer = CustomTileLayer(
                MapTileInfoFactory.create(),
                extent,
                TileManager(requireContext()),
                TILE_FOLDER_KEY,
                minZoom,
                maxZoom
            )
            ArcGISMap(Basemap(layer))
        } catch (e: Exception) {
            Log.e(TAG, "Tile layer failed, falling back to a blank map: ${e.message}")
            ArcGISMap()
        }

        map.initialViewpoint = Viewpoint(extent)
        ArcGISRuntimeEnvironment.setLicense(LICENSE)

        binding.parcelMapview.map = map
        binding.parcelMapview.isAttributionTextVisible = false
    }

    private fun attachOverlays(showLabels: Boolean) {
        with(binding.parcelMapview) {
            if (showLabels) {
                graphicsOverlays.add(labelOverlay)
                removeViewpointChangedListener(viewpointListener)
            } else {
                removeViewpointChangedListener(viewpointListener)
                addViewpointChangedListener(viewpointListener)
            }

            // Bottom to top: boundary, parcels, in-progress drawing, location marker
            if (!graphicsOverlays.contains(boundaryOverlay)) graphicsOverlays.add(0, boundaryOverlay)
            graphicsOverlays.add(parcelOverlay)
            if (!graphicsOverlays.contains(drawController.overlay))
                graphicsOverlays.add(drawController.overlay)

            locationTracker?.overlay?.let {
                if (!graphicsOverlays.contains(it)) graphicsOverlays.add(it)
            }
        }

        // outside the `with` block, so it assigns the fragment's field
        callout = binding.parcelMapview.callout
    }

    @SuppressLint("SetTextI18n")
    private fun showCounts(parcels: List<ActiveParcelEntity>) {
        val surveyed = parcels.count { it.surveyStatusCode == 2 }
        binding.tvParcelCount.text = "Parcel Count: ${parcels.size}"
        binding.tvSurveyedParcelCount.text = "($surveyed)"
        binding.tvUnsurveyedParcelCount.text = "(${parcels.size - surveyed})"
    }

    private fun showLoading(loading: Boolean) = with(binding) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        layoutInfo.visibility = if (loading) View.GONE else View.VISIBLE
        fab.visibility = if (loading) View.GONE else View.VISIBLE
        fabStartDraw.visibility = if (loading) View.GONE else View.VISIBLE
        parcelMapview.visibility = if (loading) View.GONE else View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMapInteractions() = with(binding) {
        try {
            parcelMapview.onTouchListener =
                MapTouchListener(requireContext(), parcelMapview, parcelOverlay)
        } catch (e: Exception) {
            ToastUtil.showShort(requireContext(), "Please restart the map screen.")
        }

        fab.setOnClickListener { onMyLocationClicked() }
        fabStartDraw.setOnClickListener { enterDrawMode() }
        btnDoneDraw.setOnClickListener { saveDrawnPolygon() }
        btnCancelDraw.setOnClickListener { exitDrawMode() }
        btnUndoDrawPoint.setOnClickListener { undoLastPoint() }
    }

    private fun addParcelGraphics(parcel: ActiveParcelEntity, polygon: Polygon) {
        val areaSqFt = GeometryUtils.areaSqFt(polygon)
        val isSurveyed = parcel.surveyStatusCode == 2

        val parcelGraphic = Graphic(polygon, MapSymbols.parcelFor(parcel.surveyStatusCode))
        parcelGraphic.attributes.apply {
            put("parcel_id", parcel.id)
            put("parcel_no", parcel.parcelNo)
            put("surveyStatusCode", parcel.surveyStatusCode)
            put("area", areaSqFt)
            put("khewatInfo", parcel.khewatInfo)
            put("is_drawn", parcel.isLocallyDrawn)
        }
        parcelOverlay.graphics.add(parcelGraphic)

        val labelColor = ContextCompat.getColor(
            requireContext(),
            if (isSurveyed) R.color.parcel_green else R.color.parcel_red
        )
        val labelText = "${parcel.parcelNo}\nID: ${parcel.id}"
        val label = Graphic(
            polygon.extent.center,
            MapSymbols.parcelLabel(labelText, labelColor)
        )
        label.attributes["parcel_id"] = parcel.id
        labelOverlay.graphics.add(label)
    }

    private fun drawBoundary(polygons: List<Polygon>) {
        boundaryOverlay.graphics.clear()

        if (polygons.isEmpty()) {
            boundaryUnion = null
            drawController.boundary = null
            Log.w(TAG, "No boundary polygon available")
            return
        }

        boundaryUnion = GeometryUtils.unionOf(polygons)
        drawController.boundary = boundaryUnion

        val symbol = MapSymbols.aoiBoundary()
        polygons.forEach { boundaryOverlay.graphics.add(Graphic(it, symbol)) }
        Log.d(TAG, "Boundary drawn (${polygons.size} part(s))")
    }

    private fun enterDrawMode() {
        when (drawController.start()) {
            is DrawResult.NoBoundary -> ToastUtil.showLong(
                requireContext(),
                "The boundary has not loaded. Please download it from Download Data first."
            )

            else -> {
                binding.layoutDrawControls.visibility = View.VISIBLE
                binding.fabStartDraw.visibility = View.GONE
                closeCallout()
                ToastUtil.showLong(
                    requireContext(),
                    "Draw mode: tap inside the boundary to add points."
                )
            }
        }
    }

    private fun exitDrawMode() {
        drawController.cancel()
        binding.layoutDrawControls.visibility = View.GONE
        binding.fabStartDraw.visibility = View.VISIBLE
    }

    private fun undoLastPoint() {
        if (!drawController.undoLastPoint()) {
            ToastUtil.showShort(requireContext(), "There are no points to undo.")
        }
    }

    private fun addDrawPoint(mapPoint: Point) {
        when (val result = drawController.addPoint(mapPoint)) {
            is DrawResult.PointAdded ->
                ToastUtil.showShort(requireContext(), "Point ${result.pointCount} added")

            is DrawResult.OutsideBoundary -> ToastUtil.showShort(
                requireContext(),
                "That point is outside the boundary. Please tap inside it."
            )

            is DrawResult.InsideExistingParcel -> ToastUtil.showLong(
                requireContext(),
                "You cannot start a parcel inside an existing one."
            )

            else -> Unit
        }
    }

    private fun isPointInsideExistingParcel(mapPoint: Point): Boolean {
        if (!::parcelOverlay.isInitialized) return false
        return parcelOverlay.graphics.any { graphic ->
            (graphic.geometry as? Polygon)
                ?.let { GeometryUtils.containsDeepInside(it, mapPoint) } == true
        }
    }

    private fun saveDrawnPolygon() {
        when (val check = drawController.validate()) {
            is DrawResult.TooFewPoints -> {
                ToastUtil.showShort(
                    requireContext(),
                    "A parcel needs at least ${check.required} points."
                )
                return
            }

            is DrawResult.MapNotReady -> {
                ToastUtil.showShort(requireContext(), "The map is not ready yet.")
                return
            }

            else -> Unit
        }

        val polygon = drawController.buildPolygon() ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            persistDrawnPolygon(polygon, nextParcelNo())
        }
    }

    private suspend fun nextParcelNo(): String = withContext(Dispatchers.IO) {
        val tehsil = currentTehsil()
        if (tehsil.isBlank()) return@withContext "1"

        val maxNo = database.activeParcelDao()
            .getActiveParcelsByAoi(tehsil)
            .mapNotNull { it.parcelNo.toIntOrNull() }
            .maxOrNull() ?: 0

        (maxNo + 1).toString()
    }

    private suspend fun persistDrawnPolygon(polygon: Polygon, parcelNo: String) {
        try {
            var poly = GeometryUtils.toWgs84(polygon)

            val clipped = withContext(Dispatchers.Default) {
                GeometryUtils.clipToBoundary(boundaryUnion, poly)
            }
            if (clipped == null || clipped.isEmpty) {
                ToastUtil.showLong(
                    requireContext(),
                    "The parcel falls outside the boundary. Please draw inside it."
                )
                return
            }
            poly = clipped

            if (withContext(Dispatchers.Default) { overlapsExistingParcel(poly) }) {
                ToastUtil.showLong(
                    requireContext(),
                    "This parcel overlaps an existing one. Please draw on empty space."
                )
                return
            }

            val wkt = GeometryUtils.toWkt(poly)
            if (!GeometryUtils.isValidWkt(wkt)) {
                ToastUtil.showLong(requireContext(), "Invalid geometry. Please try again.")
                return
            }

            withContext(Dispatchers.IO) { insertParcel(parcelNo, wkt, poly) }

            ToastUtil.showShort(requireContext(), "Parcel $parcelNo saved")
            exitDrawMode()
            refreshMap()
        } catch (e: Exception) {
            Log.e(TAG, "Save failed: ${e.message}", e)
            ToastUtil.showLong(requireContext(), "Could not save the parcel: ${e.message}")
        }
    }

    private suspend fun overlapsExistingParcel(polygon: Polygon): Boolean {
        val tehsil = currentTehsil()
        if (tehsil.isBlank()) return false

        return database.activeParcelDao().getActiveParcelsByAoi(tehsil).any { parcel ->
            GeometryUtils.parsePolygons(parcel.geomWKT).any { existing ->
                GeometryUtils.overlaps(polygon, existing)
            }
        }
    }

    private suspend fun insertParcel(parcelNo: String, wkt: String, polygon: Polygon) {
        val tehsil = currentTehsil()
        val dao = database.activeParcelDao()
        val newId = (dao.getMaxParcelId() ?: 0L) + 1

        val template = dao.getActiveParcelsByAoi(tehsil).firstOrNull()

        val parcel = template?.copy(
            pkid = 0,
            id = newId,
            parcelNo = parcelNo,
            subParcelNo = "",
            geomWKT = wkt,
            centroid = GeometryUtils.centroidWkt(polygon),
            surveyStatusCode = 1,
            surveyId = null,
            isActivate = true,
            khewatInfo = "0",
            areaAssigned = tehsil,
            isLocallyDrawn = true
        ) ?: ActiveParcelEntity(
            pkid = 0,
            id = newId,
            parcelNo = parcelNo,
            subParcelNo = "",
            mauzaId = 0L,
            mauzaName = tehsil,
            khewatInfo = "0",
            areaAssigned = tehsil,
            geomWKT = wkt,
            centroid = GeometryUtils.centroidWkt(polygon),
            surveyStatusCode = 1,
            surveyId = null,
            isActivate = true,
            tehsil = tehsil,
            isLocallyDrawn = true
        )

        dao.insertActiveParcels(listOf(parcel))
        Log.d(TAG, "Saved parcel $parcelNo (id=$newId) in $tehsil")
    }

    private inner class MapTouchListener(
        context: Context?,
        mapView: MapView,
        private val overlay: GraphicsOverlay
    ) : DefaultMapViewOnTouchListener(context, mapView) {

        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
        ): Boolean = e1 != null

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())

            if (drawController.isActive) {
                mMapView.screenToLocation(screenPoint)?.let { addDrawPoint(it) }
                return true
            }

            val future = mMapView.identifyGraphicsOverlayAsync(overlay, screenPoint, 10.0, false)
            future.addDoneListener {
                try {
                    future.get().graphics.firstOrNull()?.let { graphic ->
                        closeCallout()
                        showParcelCallout(graphic, screenPoint)
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Identify failed: ${ex.message}")
                }
            }
            return true
        }
    }

    // ==================================================================
    // CALLOUT
    // ==================================================================

    private fun showParcelCallout(graphic: Graphic, screenPoint: android.graphics.Point) {
        if (binding.parcelMapview.map == null) return

        val mapPoint = try {
            binding.parcelMapview.screenToLocation(screenPoint) ?: return
        } catch (e: Exception) {
            Log.e(TAG, "screenToLocation failed: ${e.message}")
            return
        }

        val attr = graphic.attributes
        val parcelId = attr["parcel_id"]?.toString()?.toLongOrNull() ?: 0L
        val parcelNo = attr["parcel_no"]?.toString().orEmpty()
        val statusCode = attr["surveyStatusCode"]?.toString()?.toIntOrNull() ?: 1
        val isDrawn = attr["is_drawn"] as? Boolean == true
        val areaSqFt = attr["area"]?.toString()?.toDoubleOrNull() ?: 0.0

        calloutView = layoutInflater.inflate(R.layout.cardview_map_info_new, null)
        hideUnusedCalloutSections()

        calloutView.findViewById<TextView>(R.id.tv_parcel_no_value)?.text = parcelNo
        calloutView.findViewById<TextView>(R.id.tv_parcel_no_uni_value)?.text =
            attr["khewatInfo"]?.toString() ?: "0"
        calloutView.findViewById<TextView>(R.id.tv_parcel_area_value)?.text =
            GeometryUtils.formatAcres(areaSqFt)

        setupStartSurveyButton(parcelId, parcelNo, statusCode, areaSqFt)
        setupDeleteButton(parcelId, parcelNo, isDrawn, statusCode)
        setupNavigationButtons(mapPoint)

        calloutView.findViewById<Button>(R.id.btn_cancel)?.setOnClickListener { closeCallout() }

        centreCalloutOn(mapPoint)
        callout.style = Callout.Style(requireContext()).apply {
            borderColor = R.color.primaryColor
            borderWidth = 2
        }
        callout.location = mapPoint
        callout.content = calloutView
        callout.show()
    }

    private fun hideUnusedCalloutSections() = with(calloutView) {
        findViewById<RadioGroup>(R.id.rg_parcel)?.visibility = View.GONE
        findViewById<LinearLayout>(R.id.layout_parcel)?.visibility = View.GONE
        findViewById<LinearLayout>(R.id.layout_split_parcel)?.visibility = View.GONE
        findViewById<LinearLayout>(R.id.layout_merge_parcel)?.visibility = View.GONE
        findViewById<Button>(R.id.btn_revisit_survey)?.visibility = View.GONE
        findViewById<Button>(R.id.btn_retake_pictures_survey)?.visibility = View.GONE
    }

    private fun setupStartSurveyButton(
        parcelId: Long, parcelNo: String, statusCode: Int, areaSqFt: Double
    ) {
        val button = calloutView.findViewById<Button>(R.id.btn_start_survey) ?: return
        button.visibility = if (statusCode == 2) View.GONE else View.VISIBLE

        button.setOnClickListener {
            viewModel.parcelId = parcelId
            viewModel.parcelNo = parcelNo
            viewModel.subParcelNo = ""
            viewModel.parcelOperation = "New"
            viewModel.parcelOperationValue = "Drawn"

            closeCallout()
            startActivity(Intent(requireContext(), SurveyActivity::class.java).apply {
                putExtra("parcelId", parcelId)
                putExtra("parcelNo", parcelNo)
                putExtra("subParcelNo", "")
                putExtra("parcelArea", areaSqFt.toString())
                putExtra("khewatInfo", "0")
                putExtra("parcelOperation", "New")
                putExtra("parcelOperationValue", "Drawn")
                putExtra("isDrawnParcel", true)
                putExtra("mauzaName", currentTehsil())
            })
        }
    }

    private fun setupDeleteButton(
        parcelId: Long, parcelNo: String, isDrawn: Boolean, statusCode: Int
    ) {
        val button = calloutView.findViewById<ImageView>(R.id.delete) ?: return
        val canDelete = isDrawn && statusCode != 2
        button.visibility = if (canDelete) View.VISIBLE else View.GONE

        button.setOnClickListener {
            DialogUtils.confirm(
                context = requireContext(),
                title = "Delete Parcel",
                message = "Delete parcel $parcelNo?\n" +
                        "You drew this parcel and it has not been surveyed yet.",
                confirmText = "Delete"
            ) { deleteDrawnParcel(parcelId, parcelNo) }
        }
    }

    private fun setupNavigationButtons(mapPoint: Point) {
        calloutView.findViewById<ImageView>(R.id.mapLocaton)?.setOnClickListener {
            closeCallout()
            val geo = GeometryUtils.toLatLon(mapPoint)
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:${geo.y},${geo.x}?q=${geo.y},${geo.x}(Parcel)")
                )
            )
        }

        calloutView.findViewById<ImageView>(R.id.directions)?.setOnClickListener {
            closeCallout()
            val geo = GeometryUtils.toLatLon(mapPoint)
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("http://maps.google.com/maps?daddr=${geo.y},${geo.x}")
                ).apply { setPackage("com.google.android.apps.maps") }
            )
        }
    }

    private fun deleteDrawnParcel(parcelId: Long, parcelNo: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) {
                    database.activeParcelDao().deleteLocalDrawnParcelById(parcelId)
                }
                closeCallout()

                if (rows > 0) {
                    ToastUtil.showShort(requireContext(), "Parcel $parcelNo deleted")
                    refreshMap()
                } else {
                    ToastUtil.showLong(
                        requireContext(),
                        "This parcel cannot be deleted — it belongs to the server or has already been surveyed."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed: ${e.message}", e)
                ToastUtil.showLong(requireContext(), "Could not delete the parcel: ${e.message}")
            }
        }
    }

    private fun closeCallout() {
        if (::callout.isInitialized && callout.isShowing) callout.dismiss()
    }

    private fun centreCalloutOn(mapPoint: Point) {
        try {
            val visible = binding.parcelMapview.visibleArea.extent
            val offset = (visible.yMax - visible.center.y) / 2
            binding.parcelMapview.setViewpointCenterAsync(
                Point(mapPoint.x, mapPoint.y + offset, binding.parcelMapview.spatialReference)
            )
        } catch (e: Exception) {
            Log.e(TAG, "centreCalloutOn failed: ${e.message}")
        }
    }

    private fun refreshMap() {
        snapTargets = emptyList()
        snapTargetsSr = null
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (::parcelOverlay.isInitialized) parcelOverlay.graphics.clear()
                if (::labelOverlay.isInitialized) labelOverlay.graphics.clear()
                boundaryOverlay.graphics.clear()
                drawController.cancel()

                delay(200)
                cancelLoad()

                val showLabels = ::labelOverlay.isInitialized &&
                        binding.parcelMapview.graphicsOverlays.contains(labelOverlay)
                loadMap(showLabels)
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed: ${e.message}", e)
                ToastUtil.showShort(requireContext(), "Could not refresh the map: ${e.message}")
            }
        }
    }

    private fun setupLocationTracker() {
        locationTracker = LocationTracker(
            activity = requireActivity(),
            mapView = binding.parcelMapview,
            sharedPreferences = sharedPreferences,
            onMockLocationDetected = {
                Utility.exitApplication(
                    "Warning!",
                    "Please disable mock or fake location. The application will close now.",
                    requireActivity()
                )
            },
            onLocationChanged = { location ->
                viewModel.currentLocation = Utility.convertGpsTimeToString(location.time)
            }
        )
    }

    private fun onMyLocationClicked() {
        val tracker = locationTracker ?: return

        if (!tracker.hasPermission()) {
            requestLocationPermission()
            return
        }

        tracker.zoomToCurrentLocation(
            onStarted = {
                Utility.showProgressAlertDialog(requireContext(), "Getting location...")
            },
            onFinished = { Utility.dismissProgressAlertDialog() },
            onUnavailable = { message -> ToastUtil.showShort(requireContext(), message) }
        )
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST_CODE) return

        val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED

        when {
            granted -> onMyLocationClicked()

            !ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION
            ) -> DialogUtils.permissionSettings(requireContext(), "Location")

            else -> DialogUtils.okCancel(
                requireContext(),
                "Location permission is needed to show your position on the map."
            ) { _, _ -> requestLocationPermission() }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setHeaderText() {
        binding.tvHeader.text = "Tehsil: ${currentTehsil()}"
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_fragment_map, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            goToMenu()
            true
        }

        R.id.action_refresh_map -> {
            closeCallout()
            if (drawController.isActive) exitDrawMode()
            refreshMap()
            ToastUtil.showShort(requireContext(), "Refreshing map...")
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun snapTargetsFor(sr: com.esri.arcgisruntime.geometry.SpatialReference): List<Polygon> {
        if (snapTargetsSr == sr && snapTargets.isNotEmpty()) return snapTargets

        if (!::parcelOverlay.isInitialized) return emptyList()

        snapTargets = parcelOverlay.graphics.mapNotNull { g ->
            val poly = g.geometry as? Polygon ?: return@mapNotNull null
            if (poly.spatialReference == sr) poly
            else GeometryEngine.project(poly, sr) as? Polygon
        }
        snapTargetsSr = sr
        Log.d(TAG, "Snap targets built: ${snapTargets.size}")
        return snapTargets
    }

    private fun snapPoint(raw: Point): Point {
        val sr = raw.spatialReference ?: return raw
        val tolerance = binding.parcelMapview.unitsPerDensityIndependentPixel * snapToleranceDp
        if (tolerance <= 0.0) return raw

        drawController.placedPoints.forEach { p ->
            if (GeometryEngine.distanceBetween(p, raw) <= tolerance) {
                return p
            }
        }

        return SnapUtils.snap(raw, snapTargetsFor(sr), tolerance)
    }
}
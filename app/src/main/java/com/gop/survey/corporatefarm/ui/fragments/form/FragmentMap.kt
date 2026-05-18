package com.gop.survey.corporatefarm.ui.fragments.form

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.geometry.AreaUnit
import com.esri.arcgisruntime.geometry.AreaUnitId
import com.esri.arcgisruntime.geometry.Envelope
import com.esri.arcgisruntime.geometry.GeodeticCurveType
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.Polyline
import com.esri.arcgisruntime.geometry.PolylineBuilder
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.layers.ArcGISTiledLayer
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.Callout
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.mapping.view.ViewpointChangedListener
import com.esri.arcgisruntime.symbology.SimpleFillSymbol
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import com.esri.arcgisruntime.symbology.Symbol
import com.esri.arcgisruntime.symbology.TextSymbol
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.gop.survey.corporatefarm.R
import com.gop.survey.corporatefarm.ui.activities.MenuActivity
import com.gop.survey.corporatefarm.ui.activities.SurveyActivity
import com.gop.survey.corporatefarm.common.Constants
import com.gop.survey.corporatefarm.common.CustomTileLayer
import com.gop.survey.corporatefarm.common.DownloadType
import com.gop.survey.corporatefarm.common.RejectedSubParcel
import com.gop.survey.corporatefarm.common.TileManager
import com.gop.survey.corporatefarm.common.Utility
import com.gop.survey.corporatefarm.data.local.AppDatabase
import com.gop.survey.corporatefarm.data.remote.response.SubParcelStatus
import com.gop.survey.corporatefarm.data.repository.NewSurveyRepositoryImpl
import com.gop.survey.corporatefarm.databinding.FragmentMapBinding
import com.gop.survey.corporatefarm.domain.model.ActiveParcelEntity
import com.gop.survey.corporatefarm.domain.model.ParcelStatus
import com.gop.survey.corporatefarm.presentation.form.SharedFormViewModel
import com.gop.survey.corporatefarm.presentation.util.ToastUtil
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class FragmentMap : Fragment() {
    @Inject
    lateinit var newSurveyRepository: NewSurveyRepositoryImpl
    private lateinit var refreshReceiver: BroadcastReceiver
    private val viewModel: SharedFormViewModel by activityViewModels()

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var database: AppDatabase
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var context: Context
    private var sdCardRoot: File? = null
    private val wgs84 by lazy {
        SpatialReferences.getWgs84()
    }
    private var mTiledLayer: ArcGISTiledLayer? = null
    private var arcGISMap: ArcGISMap? = null
    private lateinit var surveyParcelsGraphics: GraphicsOverlay
    private lateinit var surveyLabelGraphics: GraphicsOverlay
    private lateinit var currentLocationGraphicOverlay: GraphicsOverlay
    private lateinit var unSurveyedBlocks: SimpleFillSymbol
    private lateinit var surveyedBlocks: SimpleFillSymbol
    private lateinit var lockedBlocks: SimpleFillSymbol
    private lateinit var revisitBlocks: SimpleFillSymbol
    lateinit var mCallOut: Callout
    private val permissionRequestCode = 200
    private var ids: ArrayList<Long> = arrayListOf()
    private var enableNewPoint = true
    private lateinit var tvParcelNo: TextView
    private lateinit var tvParcelNoUni: TextView
    private lateinit var dialogView: View
    private lateinit var tvMergeParcel: TextView
    private lateinit var tvMergeParcelHi: TextView
    private val originalGraphicSymbols = mutableMapOf<Long, Symbol>()
    private val originalLabelGraphics = mutableMapOf<Long, Graphic>()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentLocationGraphic: Graphic? = null

    private lateinit var graphicCentoid: Graphic
    private lateinit var viewpointChangedListener: ViewpointChangedListener
    private var job: Job? = null
    private var lm: LocationManager? = null
    private val redLine = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 1f)

    // Merge mode properties
    private var isMergeMode = false
    private val selectedMergeParcels = LinkedHashMap<String, String>()
    private val defaultParcelSymbol = SimpleFillSymbol(
        SimpleFillSymbol.Style.SOLID,
        Color.argb(50, 255, 255, 255),
        SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 1f)
    )

    private val greenLine = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 3f)

    private var isMultiMergeMode = false
    private val selectedMultiMergeParcels = LinkedHashMap<Long, Graphic>()
    private var multiMergeBaseParcelId: Long = 0L

    // Helper data class for task parcels
    data class ParcelInfo(
        val parcelId: Long,
        val parcelNo: String,
        val subParcelNo: String,
        val area: String,
        val khewatInfo: String,
        val unitId: Long,
        val groupId: Long
    )

    private fun stopLoadingParcels() {
        job?.cancel()
        job = null
    }

    // Parcel splitting properties
    private var isSplitMode = false
    private var splitLine: Graphic? = null
    private val splitPoints = mutableListOf<Point>()
    private lateinit var splitOverlay: GraphicsOverlay
    private var splitTargetGraphic: Graphic? = null

    private val selectedParcelGraphics = mutableListOf<Graphic>()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root

    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up the action bar
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
            }
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Intent(context, MenuActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(this)
                    requireActivity().finish()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)


        // Set header text
        setHeaderText()
        context = requireContext()
        lm = context.getSystemService(AppCompatActivity.LOCATION_SERVICE) as LocationManager

        // Merge mode buttons
        binding.btnDoneMerge.setOnClickListener {
            Log.d("MultiMerge", "=== btnDoneMerge clicked ===")
            Log.d("MultiMerge", "isMultiMergeMode = $isMultiMergeMode")
            Log.d("MultiMerge", "isMergeMode = $isMergeMode")
            Log.d(
                "MultiMerge",
                "selectedMultiMergeParcels.size = ${selectedMultiMergeParcels.size}"
            )
            Log.d("MultiMerge", "selectedMergeParcels.size = ${selectedMergeParcels.size}")

            if (isMultiMergeMode) {
                Log.d("MultiMerge", "→ entering multi-merge branch")
                if (selectedMultiMergeParcels.size < 2) {
                    Toast.makeText(
                        requireContext(),
                        "Select at least 2 parcels to merge",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                handleMultiMergeDone()
                return@setOnClickListener
            }

            if (selectedMergeParcels.size < 1) {
                Toast.makeText(
                    requireContext(),
                    "Select at least 1 parcel to merge",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            isMergeMode = false
            binding.mergeControlBar.visibility = View.GONE
            dialogView.findViewById<View?>(R.id.card_root)?.visibility = View.VISIBLE

            Toast.makeText(
                requireContext(),
                "Merged parcels: ${selectedMergeParcels.values.joinToString(", ")}",
                Toast.LENGTH_SHORT
            ).show()

            binding.parcelMapview.onTouchListener =
                DefaultMapViewOnTouchListener(requireContext(), binding.parcelMapview)
        }

        binding.btnCancelMerge.setOnClickListener {

            if (isMultiMergeMode) {
                isMultiMergeMode = false
                selectedMultiMergeParcels.clear()
                restoreOriginalGraphics()
                binding.mergeControlBar.visibility = View.GONE
                dialogView.findViewById<View?>(R.id.card_root)?.visibility = View.VISIBLE
                binding.parcelMapview.onTouchListener =
                    DefaultMapViewOnTouchListener(requireContext(), binding.parcelMapview)
                return@setOnClickListener
            }


            isMergeMode = false
            selectedMergeParcels.clear()
            restoreOriginalGraphics()

            binding.mergeControlBar.visibility = View.GONE
            dialogView.findViewById<View?>(R.id.card_root)?.visibility = View.VISIBLE
            viewModel.parcelOperationValue = ""
            tvMergeParcel.text = ""
            tvMergeParcelHi.text = ""

            binding.parcelMapview.onTouchListener =
                DefaultMapViewOnTouchListener(requireContext(), binding.parcelMapview)
        }

        // Task Assign Mode buttons
//        binding.btnDoneTaskAssign.setOnClickListener {
//            if (selectedTaskParcels.isEmpty()) {
//                Toast.makeText(requireContext(), "No parcels selected", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//            exitTaskAssignMode()
//        }
//
//        binding.btnCancelTaskAssign.setOnClickListener {
//            exitTaskAssignMode()
//        }

        // Add split mode setup
        setupSplitOverlay()
        setupSplitModeListeners()
    }


//    @SuppressLint("ClickableViewAccessibility")
//    private fun exitTaskAssignMode() {
//        isTaskAssignMode = false
//        selectedTaskParcels.clear()
//
//        // Restore original graphics
//        restoreOriginalGraphics()
//
//        // Hide control bar
//        binding.taskAssignControlBar.visibility = View.GONE
//
//        // Restore default touch listener
//        try {
//            IdentifyFeatureLayerTouchListener(
//                context,
//                binding.parcelMapview,
//                this@FragmentMap.surveyParcelsGraphics
//            ).also { binding.parcelMapview.onTouchListener = it }
//        } catch (e: Exception) {
//            Log.e("TaskAssign", "Error restoring touch listener: ${e.message}")
//        }
//
//        Toast.makeText(requireContext(), "Task assign mode cancelled", Toast.LENGTH_SHORT).show()
//    }
//
//    private fun updateTaskAssignSelectionCount() {
//        binding.tvTaskAssignCount.text = "${selectedTaskParcels.size} parcel(s) selected"
//    }

    private fun setupSplitOverlay() {
        splitOverlay = GraphicsOverlay()
        binding.parcelMapview.graphicsOverlays.add(splitOverlay)
    }

    private fun setupSplitModeListeners() {

        binding.btnApplySplit.setOnClickListener {
            applySplit()
        }

        binding.btnCancelSplit.setOnClickListener {
            exitSplitMode()
        }
    }

    private fun enterSplitMode(graphic: Graphic) {
        if (isSplitMode) {
            exitSplitMode()
        }

        isSplitMode = true
        splitTargetGraphic = graphic
        splitPoints.clear()

        if (!selectedParcelGraphics.contains(graphic)) {
            selectedParcelGraphics.add(graphic)
        }

        val splitHighlightSymbol = SimpleFillSymbol(
            SimpleFillSymbol.Style.SOLID,
            Color.argb(150, 0, 255, 0),
            SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 4f)
        )
        graphic.symbol = splitHighlightSymbol

        showSplitModeUI()

        Toast.makeText(
            context,
            "Split Mode: Tap points to draw a line across the parcel",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun exitSplitMode() {
        if (!isSplitMode) return

        isSplitMode = false
        splitPoints.clear()
        splitLine = null

        splitTargetGraphic?.let { graphic ->
            val surveyStatus = graphic.attributes["surveyStatusCode"] as? Int ?: 1
            graphic.symbol = getSymbolForSurveyStatus(surveyStatus)
        }

        splitOverlay.graphics.clear()
        hideSplitModeUI()
        splitTargetGraphic = null
    }

    private fun showSplitModeUI() {
        binding.layoutSplitControls.visibility = View.VISIBLE
        binding.btnApplySplit.visibility = View.VISIBLE
        binding.btnCancelSplit.visibility = View.VISIBLE
        binding.mergeControlBar.visibility = View.GONE
    }

    private fun hideSplitModeUI() {
        binding.layoutSplitControls.visibility = View.GONE
        binding.btnApplySplit.visibility = View.GONE
        binding.btnCancelSplit.visibility = View.GONE
    }

    private fun applySplit() {
        if (splitPoints.size < 2) {
            Toast.makeText(
                context,
                "Please draw a complete line with at least 2 points",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val targetGraphic = splitTargetGraphic
        if (targetGraphic == null) {
            Toast.makeText(context, "No target parcel selected", Toast.LENGTH_SHORT).show()
            return
        }

        val originalPolygon = targetGraphic.geometry as? Polygon
        if (originalPolygon == null) {
            Toast.makeText(context, "Invalid parcel geometry", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Toast.makeText(context, "Processing split...", Toast.LENGTH_SHORT).show()

                withContext(Dispatchers.IO) {
                    val splitLineGeometry = createSplitLineGeometry()
                    if (splitLineGeometry == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Failed to create split line geometry",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@withContext
                    }

                    val splitPolygons = performPolygonSplit(originalPolygon, splitLineGeometry)

                    withContext(Dispatchers.Main) {
                        when {
                            splitPolygons.isEmpty() -> {
                                Toast.makeText(
                                    context,
                                    "Split operation failed. Please ensure the line completely crosses the parcel.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            splitPolygons.size == 1 -> {
                                Toast.makeText(
                                    context,
                                    "Split line does not completely divide the parcel. Try drawing a line that fully crosses it.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            splitPolygons.size == 2 -> {
                                createSplitParcels(targetGraphic, splitPolygons)
                                Toast.makeText(
                                    context,
                                    "Parcel split successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                exitSplitMode()
                                refreshMapDisplay()
                            }

                            else -> {
                                Toast.makeText(
                                    context,
                                    "Split produced ${splitPolygons.size} parts. Only 2-way splits are currently supported.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error splitting parcel: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("ParcelSplit", "Error during split operation", e)
                }
            }
        }
    }

    private fun createSplitLineGeometry(): Polyline? {
        try {
            if (splitPoints.size < 2) {
                Log.e("ParcelSplit", "Insufficient points for line creation: ${splitPoints.size}")
                return null
            }

            val selectedGraphic = selectedParcelGraphics.firstOrNull()
            if (selectedGraphic == null) {
                Log.e("ParcelSplit", "No selected graphic found")
                return null
            }

            val originalPolygon = selectedGraphic.geometry as? Polygon
            if (originalPolygon == null) {
                Log.e("ParcelSplit", "Selected graphic is not a polygon")
                return null
            }

            val spatialRef = originalPolygon.spatialReference
            val builder = PolylineBuilder(spatialRef)

            for (point in splitPoints) {
                val projectedPoint = if (point.spatialReference != spatialRef) {
                    GeometryEngine.project(point, spatialRef) as Point
                } else {
                    point
                }
                builder.addPoint(projectedPoint)
            }

            val line = builder.toGeometry()

            if (line.isEmpty || line.parts.isEmpty()) {
                Log.e("ParcelSplit", "Created line is empty or invalid")
                return null
            }

            Log.d("ParcelSplit", "Created split line with ${splitPoints.size} points")
            return line

        } catch (e: Exception) {
            Log.e("ParcelSplit", "Error creating split line: ${e.message}")
            return null
        }
    }

    private fun performPolygonSplit(
        polygon: Polygon,
        splitLine: Polyline
    ): List<Polygon> {
        try {
            if (polygon.isEmpty || splitLine.isEmpty) {
                Log.e("ParcelSplit", "Empty geometry provided")
                return emptyList()
            }

            val targetSR = polygon.spatialReference
            val projectedLine = if (splitLine.spatialReference != targetSR) {
                GeometryEngine.project(splitLine, targetSR) as? Polyline
            } else {
                splitLine
            }

            if (projectedLine == null) {
                Log.e("ParcelSplit", "Failed to project split line")
                return emptyList()
            }

            if (!GeometryEngine.intersects(polygon, projectedLine)) {
                Log.e("ParcelSplit", "Split line does not intersect the polygon")
                return emptyList()
            }

            val extendedLine = extendLineToPolygonBounds(projectedLine, polygon)

            if (extendedLine.isEmpty) {
                Log.e("ParcelSplit", "Failed to create extended line")
                return emptyList()
            }

            val cutResult = try {
                GeometryEngine.cut(polygon, extendedLine)
            } catch (e: Exception) {
                Log.e("ParcelSplit", "GeometryEngine.cut failed: ${e.message}")
                return performPolygonSplitWithBuffer(polygon, extendedLine)
            }

            val polygonResults = cutResult.mapNotNull { geometry ->
                when {
                    geometry is Polygon && !geometry.isEmpty -> geometry
                    else -> {
                        Log.w(
                            "ParcelSplit",
                            "Invalid result geometry: ${geometry?.javaClass?.simpleName}"
                        )
                        null
                    }
                }
            }

            Log.d("ParcelSplit", "Split operation produced ${polygonResults.size} polygons")
            return polygonResults

        } catch (e: Exception) {
            Log.e("ParcelSplit", "Error in performPolygonSplit: ${e.message}", e)
            return emptyList()
        }
    }

    private fun performPolygonSplitWithBuffer(
        polygon: Polygon,
        splitLine: Polyline
    ): List<Polygon> {
        try {
            val bufferDistance = 0.00001
            val bufferPolygon = GeometryEngine.buffer(splitLine, bufferDistance)
                ?: return emptyList()

            val difference = GeometryEngine.difference(polygon, bufferPolygon)

            return when (difference) {
                is Polygon -> listOf(difference)
                else -> {
                    Log.w("ParcelSplit", "Buffer method produced non-polygon result")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("ParcelSplit", "Buffer split method failed: ${e.message}")
            return emptyList()
        }
    }

    private fun extendLineToPolygonBounds(
        line: Polyline,
        polygon: Polygon
    ): Polyline {
        try {
            if (line.parts.isEmpty() || line.parts[0].pointCount < 2) {
                Log.e("ParcelSplit", "Invalid line geometry for extension")
                return line
            }

            val part = line.parts[0]
            val startPoint = part.getPoint(0)
            val endPoint = part.getPoint(part.pointCount - 1)

            val dx = endPoint.x - startPoint.x
            val dy = endPoint.y - startPoint.y
            val length = kotlin.math.sqrt(dx * dx + dy * dy)

            if (length == 0.0) {
                Log.e("ParcelSplit", "Zero-length line cannot be extended")
                return line
            }

            val normalizedDx = dx / length
            val normalizedDy = dy / length

            val envelope = polygon.extent
            val maxDimension = maxOf(envelope.width, envelope.height)
            val extensionDistance = maxDimension * 2.0

            val extendedStart = Point(
                startPoint.x - normalizedDx * extensionDistance,
                startPoint.y - normalizedDy * extensionDistance,
                line.spatialReference
            )

            val extendedEnd = Point(
                endPoint.x + normalizedDx * extensionDistance,
                endPoint.y + normalizedDy * extensionDistance,
                line.spatialReference
            )

            val builder = PolylineBuilder(line.spatialReference)
            builder.addPoint(extendedStart)

            for (i in 0 until part.pointCount) {
                builder.addPoint(part.getPoint(i))
            }

            builder.addPoint(extendedEnd)

            val extendedLine = builder.toGeometry()

            if (extendedLine.isEmpty) {
                Log.e("ParcelSplit", "Failed to create valid extended line")
                return line
            }

            Log.d(
                "ParcelSplit",
                "Successfully extended line from ${line.parts[0].pointCount} to ${extendedLine.parts[0].pointCount} points"
            )
            return extendedLine

        } catch (e: Exception) {
            Log.e("ParcelSplit", "Error extending line: ${e.message}")
            return line
        }
    }

    @SuppressLint("DefaultLocale")
    private fun createSplitParcels(originalGraphic: Graphic, splitPolygons: List<Polygon>) {
        val originalAttributes = originalGraphic.attributes
        val originalParcelNo = originalAttributes["parcel_no"]?.toString() ?: return
        val originalSubParcelNo = originalAttributes["sub_parcel_no"]?.toString() ?: ""
        val originalUnitId = originalAttributes["unit_id"]?.toString()?.toLongOrNull() ?: 0L
        val originalGroupId = originalAttributes["group_id"]?.toString()?.toLongOrNull() ?: 0L

        try {
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val originalParcelId =
                            originalAttributes["parcel_id"] as? Long ?: return@withContext

                        Log.d("SPLIT_DEBUG", "=== SPLIT OPERATION STARTED ===")
                        Log.d("SPLIT_DEBUG", "Original parcel ID: $originalParcelId")

                        val originalParcel =
                            database.activeParcelDao().getParcelById(originalParcelId)

                        if (originalParcel == null) {
                            Log.e("SPLIT_DEBUG", "❌ Original parcel not found")
                            return@withContext
                        }

                        Log.d("SPLIT_DEBUG", "Found original parcel: ${originalParcel.id}")

                        // Deactivate original parcel
                        database.activeParcelDao()
                            .updateParcelActivationStatus(originalParcel.id, false)
                        Log.d("SPLIT_DEBUG", "Deactivated original parcel")

                        cleanupOriginalGraphicsAfterSplit(originalParcelId)

                        // Get max ID for new parcels
                        val maxId = database.activeParcelDao().getMaxParcelId() ?: 0L
                        Log.d("SPLIT_DEBUG", "Current max parcel ID: $maxId")

                        val newParcels = mutableListOf<ActiveParcelEntity>()

                        // Create split parcels with geometry
                        for (i in splitPolygons.indices) {
                            val newSubParcelNo =
                                if (originalSubParcelNo.isBlank() || originalSubParcelNo == "0") {
                                    (i + 1).toString()
                                } else {
                                    "${originalSubParcelNo}_${i + 1}"
                                }

                            // CRITICAL: Convert polygon to WKT
                            val newGeomWKT = convertPolygonToWkt(splitPolygons[i])

                            // VALIDATION: Check geometry
                            if (!validateWkt(newGeomWKT)) {
                                Log.e("SPLIT_DEBUG", "❌ Invalid geometry for split parcel ${i + 1}")
                                Log.e("SPLIT_DEBUG", "   GeomWKT: $newGeomWKT")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "Invalid geometry for split parcel ${i + 1}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                return@withContext
                            }

                            Log.d("SPLIT_DEBUG", "✅ Valid geometry for split parcel ${i + 1}:")
                            Log.d("SPLIT_DEBUG", "   GeomWKT length: ${newGeomWKT.length}")
                            Log.d("SPLIT_DEBUG", "   GeomWKT preview: ${newGeomWKT.take(100)}...")

                            val newCentroid = splitPolygons[i].extent.center
                            val centroidWKT =
                                String.format("POINT(%.8f %.8f)", newCentroid.x, newCentroid.y)

                            val newUniqueId = maxId + i + 1

                            val newParcel = originalParcel.copy(
                                pkid = 0,
                                id = newUniqueId,
                                parcelNo = originalParcelNo,
                                subParcelNo = newSubParcelNo,
                                geomWKT = newGeomWKT,
                                centroid = centroidWKT,
                                surveyStatusCode = 1,
                                surveyId = null,
                                isActivate = true,
                                unitId = originalUnitId,
                                groupId = originalGroupId,
                                multiMergeParcelNos = originalParcel.multiMergeParcelNos

                            )

                            newParcels.add(newParcel)

                            Log.d("SPLIT_DEBUG", "Created split parcel ${i + 1}:")
                            Log.d("SPLIT_DEBUG", "   New ID: $newUniqueId")
                            Log.d("SPLIT_DEBUG", "   ParcelNo: $originalParcelNo")
                            Log.d("SPLIT_DEBUG", "   SubParcelNo: $newSubParcelNo")
                            Log.d(
                                "SPLIT_DEBUG",
                                "   GeomWKT: ${if (newParcel.geomWKT.isEmpty()) "❌ EMPTY" else "✅ Present (${newParcel.geomWKT.length} chars)"}"
                            )
                            Log.d("SPLIT_DEBUG", "   Centroid: ${newParcel.centroid}")
                        }

                        // ✅ Insert new split parcels
                        database.activeParcelDao().insertActiveParcels(newParcels)
                        Log.d("SPLIT_DEBUG", "✅ Inserted ${newParcels.size} split parcels")

                        // ✅ VERIFICATION: Check that parcels were inserted with geometry
                        newParcels.forEach { newParcel ->
                            val verifyParcel =
                                database.activeParcelDao().getParcelById(newParcel.id)
                            if (verifyParcel != null) {
                                Log.d("SPLIT_DEBUG", "✅ Verify parcel ID ${newParcel.id}:")
                                Log.d("SPLIT_DEBUG", "   Exists: true")
                                Log.d("SPLIT_DEBUG", "   isActivate: ${verifyParcel.isActivate}")
                                Log.d(
                                    "SPLIT_DEBUG",
                                    "   GeomWKT: ${if (verifyParcel.geomWKT.isEmpty()) "❌ EMPTY/NULL" else "✅ Present (${verifyParcel.geomWKT.length} chars)"}"
                                )
                                Log.d(
                                    "SPLIT_DEBUG",
                                    "   Centroid: ${verifyParcel.centroid ?: "❌ NULL"}"
                                )

                                if (verifyParcel.geomWKT.isEmpty()) {
                                    Log.e(
                                        "SPLIT_DEBUG",
                                        "❌❌ CRITICAL: Split parcel ${verifyParcel.id} has NO geometry in database!"
                                    )
                                }
                            } else {
                                Log.e(
                                    "SPLIT_DEBUG",
                                    "❌ Parcel ID ${newParcel.id} NOT FOUND after insert!"
                                )
                            }
                        }

                        Log.d("SPLIT_DEBUG", "=== SPLIT OPERATION COMPLETED ===")

                    } catch (e: Exception) {
                        Log.e("SPLIT_DEBUG", "❌ Database error: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Database error: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    try {
                        surveyParcelsGraphics.graphics.remove(originalGraphic)
                        selectedParcelGraphics.remove(originalGraphic)

                        Toast.makeText(
                            context,
                            "Parcel $originalParcelNo split into ${splitPolygons.size} parts",
                            Toast.LENGTH_SHORT
                        ).show()

                        refreshMapDisplay()
                    } catch (e: Exception) {
                        Log.e("SPLIT_DEBUG", "❌ UI update error: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SPLIT_DEBUG", "❌ Error in createSplitParcels: ${e.message}", e)
        }
    }

    private fun validateWkt(wkt: String?): Boolean {
        if (wkt.isNullOrBlank()) return false

        return when {
            wkt.startsWith("POLYGON", ignoreCase = true) && wkt.contains("((") -> true
            wkt.startsWith("POINT", ignoreCase = true) && wkt.contains("(") -> true
            wkt.startsWith("MULTIPOLYGON", ignoreCase = true) && wkt.contains("(((") -> true
            else -> false
        }
    }

    private fun cleanupOriginalGraphicsAfterSplit(originalParcelId: Long) {
        originalGraphicSymbols.remove(originalParcelId)
        originalLabelGraphics.remove(originalParcelId)

        Log.d("CLEANUP_DEBUG", "Removed original symbols for parcel ID: $originalParcelId")
    }

    private fun convertPolygonToWkt(polygon: Polygon): String {
        val stringBuilder = StringBuilder("POLYGON(")

        val exteriorRing = polygon.parts[0]
        stringBuilder.append("(")

        for (i in 0 until exteriorRing.pointCount) {
            val point = exteriorRing.getPoint(i)
            if (i > 0) stringBuilder.append(",")
            stringBuilder.append("${point.x} ${point.y}")
        }

        val firstPoint = exteriorRing.getPoint(0)
        val lastPoint = exteriorRing.getPoint(exteriorRing.pointCount - 1)
        if (firstPoint.x != lastPoint.x || firstPoint.y != lastPoint.y) {
            stringBuilder.append(",${firstPoint.x} ${firstPoint.y}")
        }

        stringBuilder.append(")")

        for (i in 1 until polygon.parts.size) {
            stringBuilder.append(",(")
            val interiorRing = polygon.parts[i]

            for (j in 0 until interiorRing.pointCount) {
                val point = interiorRing.getPoint(j)
                if (j > 0) stringBuilder.append(",")
                stringBuilder.append("${point.x} ${point.y}")
            }

            val firstInteriorPoint = interiorRing.getPoint(0)
            val lastInteriorPoint = interiorRing.getPoint(interiorRing.pointCount - 1)
            if (firstInteriorPoint.x != lastInteriorPoint.x || firstInteriorPoint.y != lastInteriorPoint.y) {
                stringBuilder.append(",${firstInteriorPoint.x} ${firstInteriorPoint.y}")
            }

            stringBuilder.append(")")
        }

        stringBuilder.append(")")
        return stringBuilder.toString()
    }

    private fun updateSplitLineVisual() {
        if (!::splitOverlay.isInitialized) return

        splitOverlay.graphics.clear()

        if (splitPoints.size >= 2) {
            val builder = PolylineBuilder(binding.parcelMapview.spatialReference)
            splitPoints.forEach { builder.addPoint(it) }
            val lineGeometry = builder.toGeometry()

            val lineSymbol = SimpleLineSymbol(
                SimpleLineSymbol.Style.SOLID,
                Color.RED,
                3f
            )

            splitLine = Graphic(lineGeometry, lineSymbol)
            splitOverlay.graphics.add(splitLine!!)
        }

        val pointSymbol = SimpleMarkerSymbol(
            SimpleMarkerSymbol.Style.CIRCLE,
            Color.RED,
            8f
        ).apply {
            outline = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.WHITE, 2f)
        }

        splitPoints.forEach { point ->
            splitOverlay.graphics.add(Graphic(point, pointSymbol))
        }
    }

    private fun refreshMapDisplay() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("MapRefresh", "Starting map refresh...")

                // Store grower codes before clearing
                val growerCodesMap = mutableMapOf<Long, String>()
                for (graphic in surveyLabelGraphics.graphics) {
                    val parcelId = graphic.attributes["parcel_id"] as? Long
                    val growerCodes = graphic.attributes["growerCodes"]?.toString()
                    if (parcelId != null && !growerCodes.isNullOrEmpty()) {
                        growerCodesMap[parcelId] = growerCodes
                    }
                }

                withContext(Dispatchers.Main) {
                    surveyParcelsGraphics.graphics.clear()
                    surveyLabelGraphics.graphics.clear()
                    selectedParcelGraphics.clear()
                    if (::splitOverlay.isInitialized) {
                        splitOverlay.graphics.clear()
                    }
                }

                delay(200)
                stopLoadingParcels()

                withContext(Dispatchers.Main) {
                    val currentShowLabels =
                        binding.parcelMapview.graphicsOverlays.contains(surveyLabelGraphics)
                    Log.d("MapRefresh", "Reloading map with current filter")

                    loadMap(currentShowLabels)

                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(1000)

                        // Restore grower codes after map loads
                        for (graphic in surveyLabelGraphics.graphics) {
                            val parcelId = graphic.attributes["parcel_id"] as? Long
                            if (parcelId != null && growerCodesMap.containsKey(parcelId)) {
                                graphic.attributes["growerCodes"] = growerCodesMap[parcelId]
                            }
                        }
                    }
                }

                Log.d("MapRefresh", "Map refresh completed")
            } catch (e: Exception) {
                Log.e("MapRefresh", "Error refreshing map display: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error refreshing map: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("LoadMap", "🔥 onResume() CALLED")

        val isOnline = Utility.checkInternetConnection(requireContext())
        if (!isOnline) {
            Toast.makeText(
                context,
                "Offline mode: showing locally saved parcels.",
                Toast.LENGTH_SHORT
            ).show()
        }


        closeCallOut()

        refreshReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "REFRESH_MAP") {
                    Log.d("FragmentMap", "Received map refresh broadcast")
                    refreshMapData()
                }
            }
        }
        val filter = IntentFilter("REFRESH_MAP")
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(refreshReceiver, filter)

        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }

        try {
            binding.apply {
                viewpointChangedListener = ViewpointChangedListener {
                    val currentZoomLevel = parcelMapview.mapScale
                    if (currentZoomLevel < 5000) {
                        if (!parcelMapview.graphicsOverlays.contains(surveyLabelGraphics)) {
                            parcelMapview.graphicsOverlays.add(surveyLabelGraphics)
                        }
                    } else {
                        if (parcelMapview.graphicsOverlays.contains(surveyLabelGraphics)) {
                            parcelMapview.graphicsOverlays.remove(surveyLabelGraphics)
                        }
                    }
                }
            }

            context = requireContext()

            sdCardRoot = requireContext().filesDir
            currentLocationGraphicOverlay = GraphicsOverlay()

            sdCardRoot?.let {
                loadMap(false)
            } ?: run {
                Toast.makeText(context, "Internal Storage not accessible", Toast.LENGTH_LONG).show()
            }
            _binding?.parcelMapview?.resume()

            // Start continuous location updates
            startContinuousLocationUpdates()

        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Restart the map screen.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMap() {
        ToastUtil.showLong(context, "Please wait while parcels are being loaded...")
        with(binding) {

            setupSplitOverlay()
            setupSplitModeListeners()

            try {
                IdentifyFeatureLayerTouchListener(
                    context,
                    parcelMapview,
                    this@FragmentMap.surveyParcelsGraphics
                ).also { parcelMapview.onTouchListener = it }
            } catch (e: Exception) {
                ToastUtil.showShort(context, "Restart the map screen.")
            }

            fab.setOnClickListener {
                handleFabClick()
            }
        }
    }

    private fun loadMap(showLabels: Boolean) {
        Log.d("LoadMap", "🔥 loadMap() CALLED - type: ${Constants.MAP_DOWNLOAD_TYPE}")  // ADD THIS
        when (Constants.MAP_DOWNLOAD_TYPE) {
            DownloadType.TPK -> {
            }

            DownloadType.TILES -> {
                loadMapTiles(showLabels)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun loadMapTiles(showLabels: Boolean) {
        Log.d("LoadMap", "🔥 loadMapTiles() CALLED")

        if (_binding == null) {
            Log.e("LoadMap", "Binding is null")
            return
        }



        with(binding) {
            surveyParcelsGraphics = GraphicsOverlay()
            surveyLabelGraphics = GraphicsOverlay()
            parcelMapview.graphicsOverlays.clear()

            progressBar.visibility = View.VISIBLE
            layoutInfo.visibility = View.GONE
            layoutRejected.visibility = View.GONE
            fab.visibility = View.GONE
            parcelMapview.visibility = View.GONE

            // Initialize symbols on Main thread
            val parcelBlueColor = try {
                ContextCompat.getColor(requireContext(), R.color.parcel_blue)
            } catch (e: Exception) {
                Color.BLUE
            }

            unSurveyedBlocks = SimpleFillSymbol(SimpleFillSymbol.Style.NULL, Color.WHITE, redLine)
            surveyedBlocks = SimpleFillSymbol(SimpleFillSymbol.Style.NULL, Color.WHITE, greenLine)
            revisitBlocks = SimpleFillSymbol(
                SimpleFillSymbol.Style.SOLID,
                Color.argb(
                    80,
                    Color.red(Color.MAGENTA),
                    Color.green(Color.MAGENTA),
                    Color.blue(Color.MAGENTA)
                ),
                SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.MAGENTA, 1.5f)
            )
            lockedBlocks = SimpleFillSymbol(
                SimpleFillSymbol.Style.SOLID,
                Color.argb(
                    80,
                    Color.red(Color.BLUE),
                    Color.green(Color.BLUE),
                    Color.blue(Color.BLUE)
                ),
                SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, parcelBlueColor, 1.5f)
            )

//            val minZoomLevel = sharedPreferences.getInt(
//                Constants.SHARED_PREF_MAP_MIN_SCALE,
//                Constants.SHARED_PREF_DEFAULT_MIN_SCALE
//            )

            val minZoomLevel = 7
            val maxZoomLevel = 16

            job = CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("LoadMap", "🔥 Coroutine started")

                    val folderKey = "corporate_parcels"

                    val parcels = database.activeParcelDao().getAllActiveParcels()
                    Log.d("LoadMap", "Loaded ${parcels.size} parcels from Room DB")

                    if (parcels.isEmpty()) {
                        Log.e("LoadMap", "No parcels in DB - please sync first")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "No parcels found. Please sync data first.",
                                Toast.LENGTH_LONG
                            ).show()
                            progressBar.visibility = View.GONE
                        }
                        return@launch
                    }

                    // Process geometries
                    val polygonsList = mutableListOf<Polygon>()
                    var processedCount = 0
                    var skipCount = 0
                    val gson = Gson()

                    for (parcel in parcels) {
                        try {
                            val parcelGeom = parcel.geomWKT
                            if (parcelGeom.isBlank()) {
                                skipCount++
                                continue
                            }
                            Log.d("GeomDebug", "Parcel ${parcel.parcelNo}:")
                            Log.d("GeomDebug", "  WKT length: ${parcelGeom.length}")
                            Log.d("GeomDebug", "  Starts with: ${parcelGeom.take(50)}")
                            Log.d(
                                "GeomDebug",
                                "  Contains MULTIPOLYGON: ${parcelGeom.contains("MULTIPOLYGON")}"
                            )

                            val polygons = when {
                                parcelGeom.contains("MULTIPOLYGON") ->
                                    Utility.getMultiPolygonFromString(parcelGeom, wgs84)

                                parcelGeom.contains("POLYGON ((") -> {
                                    val p = Utility.getPolygonFromString(parcelGeom, wgs84)
                                    if (p != null) listOf(p) else emptyList()
                                }

                                parcelGeom.contains("POLYGON") -> {
                                    val p = Utility.getPolyFromString(parcelGeom, wgs84)
                                    if (p != null) listOf(p) else emptyList()
                                }

                                else -> emptyList()
                            }

                            if (polygons.isNotEmpty()) {
                                for (polygon in polygons) {
                                    val simplified = Utility.simplifyPolygon(polygon)
                                    if (!simplified.isEmpty) {
                                        polygonsList.add(simplified)
                                        addGraphics(
                                            parcel = parcel,
                                            polygon = simplified,
                                            gson = gson
                                        )
                                        processedCount++
                                    }
                                }
                            } else {
                                skipCount++
                            }
                        } catch (e: Exception) {
                            Log.e("LoadMap", "Error on parcel ${parcel.parcelNo}: ${e.message}")
                            skipCount++
                        }
                    }

                    Log.d("LoadMap", "Processed: $processedCount, Skipped: $skipCount")

                    if (polygonsList.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "No valid geometries found", Toast.LENGTH_LONG)
                                .show()
                            progressBar.visibility = View.GONE
                        }
                        return@launch
                    }

                    // Calculate map extent
                    val combinedGeometry = GeometryEngine.union(polygonsList)
                    val combinedExtent = combinedGeometry.extent
                    val bufferedGeometry = GeometryEngine.buffer(combinedExtent, 0.0001) as Polygon
                    val bufferedExtent = bufferedGeometry.extent
                    val webMercatorEnvelope = GeometryEngine.project(
                        bufferedExtent,
                        SpatialReferences.getWebMercator()
                    ) as Envelope

                    // Count surveyed/unsurveyed
                    var surveyedCount = 0
                    var unSurveyedCount = 0
                    for (parcel in parcels) {
                        when (parcel.surveyStatusCode) {
                            2 -> surveyedCount++
                            else -> unSurveyedCount++
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext

                        binding.tvParcelCount.text = "Parcel Count: ${parcels.size}"
                        binding.tvSurveyedParcelCount.text = "($surveyedCount)"
                        binding.tvUnsurveyedParcelCount.text = "($unSurveyedCount)"

                        val tileManager = TileManager(requireContext())

                        val levelsOfDetail = listOf(
                            com.esri.arcgisruntime.arcgisservices.LevelOfDetail(
                                7,
                                1222.992452561855,
                                591657527.591555
                            ),
                            com.esri.arcgisruntime.arcgisservices.LevelOfDetail(
                                8,
                                611.4962262809275,
                                295828763.7957775
                            ),
                            com.esri.arcgisruntime.arcgisservices.LevelOfDetail(
                                9,
                                305.7481131404638,
                                147914381.89788872
                            ),
                            com.esri.arcgisruntime.arcgisservices.LevelOfDetail(
                                10,
                                152.8740565702319,
                                73957190.94894436
                            ),
                            com.esri.arcgisruntime.arcgisservices.LevelOfDetail(
                                11,
                                76.43702828511594,
                                36978595.47447218
                            ),
                            com.esri.arcgisruntime.arcgisservices.LevelOfDetail(
                                12,
                                38.21851414255798,
                                18489297.73723609
                            ),
                            com.esri.arcgisruntime.arcgisservices.LevelOfDetail(
                                13,
                                19.10925707127899,
                                9244648.868618045
                            ),
                            com.esri.arcgisruntime.arcgisservices.LevelOfDetail(
                                14,
                                9.554628535639495,
                                4622324.4343090225
                            ),
                            com.esri.arcgisruntime.arcgisservices.LevelOfDetail(
                                15,
                                4.777314267819747,
                                2311162.2171545113
                            ),
                            com.esri.arcgisruntime.arcgisservices.LevelOfDetail(
                                16,
                                2.3886571339098737,
                                1155581.1085772556
                            ),
                            com.esri.arcgisruntime.arcgisservices.LevelOfDetail(
                                17,
                                1.1943285669549368,
                                577790.5542886278
                            ),
                            com.esri.arcgisruntime.arcgisservices.LevelOfDetail(
                                18,
                                0.5971642834774684,
                                288895.2771443139
                            ),
                            com.esri.arcgisruntime.arcgisservices.LevelOfDetail(
                                19,
                                0.2985821417387342,
                                144447.63857215695
                            ),
                            com.esri.arcgisruntime.arcgisservices.LevelOfDetail(
                                20,
                                0.1492910708693671,
                                72223.81928607848
                            )
                        )

                        val tileInfo = com.esri.arcgisruntime.arcgisservices.TileInfo(
                            96,
                            com.esri.arcgisruntime.arcgisservices.TileInfo.ImageFormat.PNG24,
                            levelsOfDetail,
                            Point(
                                -20037508.3427892,
                                20037508.3427892,
                                SpatialReferences.getWebMercator()
                            ),
                            SpatialReferences.getWebMercator(),
                            256,
                            256
                        )

                        val maxZoomLevel = 16

                        val map = try {
                            val customTileLayer = CustomTileLayer(
                                tileInfo,
                                webMercatorEnvelope,
                                tileManager,
                                folderKey,
                                minZoomLevel,
                                maxZoomLevel
                            )
                            ArcGISMap(Basemap(customTileLayer))
                        } catch (e: Exception) {
                            ArcGISMap()
                        }

                        map.initialViewpoint = Viewpoint(webMercatorEnvelope)

                        ArcGISRuntimeEnvironment.setLicense("runtimelite,1000,rud5883837740,none,ZZ0RJAY3FLCB0YRJD136")
                        binding.parcelMapview.map = map
                        binding.parcelMapview.isAttributionTextVisible = false

                        if (showLabels) {
                            parcelMapview.graphicsOverlays.add(surveyLabelGraphics)
                            parcelMapview.removeViewpointChangedListener(viewpointChangedListener)
                        } else {
                            parcelMapview.removeViewpointChangedListener(viewpointChangedListener)
                            parcelMapview.addViewpointChangedListener(viewpointChangedListener)
                        }

                        parcelMapview.graphicsOverlays.add(surveyParcelsGraphics)
                        setupMap()
                        mCallOut = parcelMapview.callout

                        try {
                            IdentifyFeatureLayerTouchListener(
                                context,
                                parcelMapview,
                                this@FragmentMap.surveyParcelsGraphics
                            ).also { parcelMapview.onTouchListener = it }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Restart the map screen.", Toast.LENGTH_LONG)
                                .show()
                        }

                        progressBar.visibility = View.GONE
                        layoutInfo.visibility = View.VISIBLE
                        layoutRejected.visibility = View.VISIBLE
                        fab.visibility = View.VISIBLE
                        parcelMapview.visibility = View.VISIBLE

                        Log.d("LoadMap", "Map setup completed successfully")
                    }

                } catch (e: Exception) {
                    Log.e("LoadMap", "Coroutine crashed: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        if (_binding != null) {
                            Toast.makeText(context, "Map error: ${e.message}", Toast.LENGTH_LONG)
                                .show()
                            progressBar.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun addGraphics(parcel: ActiveParcelEntity, polygon: Polygon, gson: Gson) {
        val areaSqFt = GeometryEngine.areaGeodetic(
            polygon,
            AreaUnit(AreaUnitId.SQUARE_FEET),
            GeodeticCurveType.NORMAL_SECTION
        ).roundToInt()
        val areaAcres = areaSqFt / 43560.0
        val myPolygonCenterLatLon = polygon.extent.center
        var isRejected = 0
        val symbol: SimpleFillSymbol
        val highlightColor: Int
        val textColor: Int
        val isHarvested = isParcelHarvested(parcel.id)

        when (parcel.surveyStatusCode) {
            1 -> {
                symbol = unSurveyedBlocks
                textColor = ContextCompat.getColor(context, R.color.parcel_red)
            }

            2 -> {
                if (isHarvested) {
                    symbol = SimpleFillSymbol(
                        SimpleFillSymbol.Style.SOLID,
                        Color.WHITE,
                        SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLACK, 2f)
                    )
                    highlightColor = Color.WHITE
                    textColor = Color.BLACK
                } else {
                    symbol = surveyedBlocks
                    highlightColor = Color.BLACK
                    textColor = ContextCompat.getColor(context, R.color.parcel_green)
                }
            }

            else -> {
                symbol = unSurveyedBlocks
                highlightColor = Color.YELLOW
                textColor = ContextCompat.getColor(context, R.color.parcel_red)
            }
        }

        val parcelGraphic = Graphic(polygon, symbol)
        val attr = parcelGraphic.attributes
        attr["parcel_id"] = parcel.id
        attr["pkid"] = parcel.pkid
        attr["parcel_no"] = parcel.parcelNo
        attr["sub_parcel_no"] = parcel.subParcelNo
        attr["surveyStatusCode"] = parcel.surveyStatusCode
        attr["area"] = areaSqFt
        attr["geomWKT"] = parcel.geomWKT
        attr["centroid"] = parcel.centroid
        attr["isRejected"] = isRejected
        attr["unit_id"] = parcel.unitId ?: 0L
        attr["group_id"] = parcel.groupId ?: 0L

        val displayParcelNo = if (parcel.subParcelNo.isNotBlank() && parcel.subParcelNo != "0") {
            "${parcel.parcelNo}/${parcel.subParcelNo}"
        } else {
            parcel.parcelNo.toString()
        }
        val labelText = "$displayParcelNo\n${parcel.khewatInfo}"
        val polyLabelSymbol = TextSymbol().apply {
            text = labelText
            size = 16f
            color = textColor
            horizontalAlignment = TextSymbol.HorizontalAlignment.CENTER
            verticalAlignment = TextSymbol.VerticalAlignment.MIDDLE
            haloWidth = 1f
            fontWeight = TextSymbol.FontWeight.BOLD
        }

        val labelGraphic = Graphic(myPolygonCenterLatLon, polyLabelSymbol)
        val parcelId = parcel.pkid

        // Store initial symbol and label
        originalGraphicSymbols[parcelId] = symbol

        val attrLabel = labelGraphic.attributes
        attrLabel["parcel_id"] = parcel.id
        attrLabel["pkid"] = parcel.pkid
        attrLabel["parcel_no"] = parcel.parcelNo
        attrLabel["sub_parcel_no"] = parcel.subParcelNo
        attrLabel["khewatInfo"] = parcel.khewatInfo
        attrLabel["surveyStatusCode"] = parcel.surveyStatusCode
        attrLabel["area"] = areaSqFt
        attrLabel["geomWKT"] = parcel.geomWKT
        attrLabel["centroid"] = parcel.centroid
        attrLabel["isRejected"] = isRejected
        attrLabel["unit_id"] = parcel.unitId ?: 0L
        attrLabel["group_id"] = parcel.groupId ?: 0L
        attrLabel["growerCodes"] = ""

        // Add graphics to overlays first
        surveyParcelsGraphics.graphics.add(parcelGraphic)
        surveyLabelGraphics.graphics.add(labelGraphic)

        // Load grower codes for surveyed parcels
        if (parcel.surveyStatusCode == 2) {
            CoroutineScope(Dispatchers.IO).launch {
                val surveyId = parcel.surveyId
                val codes = if (!surveyId.isNullOrEmpty()) {
                    try {

                        val persons = database.personDao().getPersonsBySurveyId(surveyId)
                        persons.mapNotNull { it.growerCode.takeIf { code -> code.isNotBlank() } }
                    } catch (e: Exception) {
                        Log.e("AddGraphics", "Error loading grower codes: ${e.message}")
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                val growerText = if (codes.isNotEmpty()) {
                    codes.joinToString(", ")
                } else {
                    ""
                }

                withContext(Dispatchers.Main) {
                    // SAFE: Use find instead of indexOfFirst to avoid index issues
                    val existingLabel = surveyLabelGraphics.graphics.firstOrNull {
                        it.attributes["parcel_id"] == parcel.id
                    }

                    if (existingLabel != null) {
                        try {
                            // Store grower codes in attributes
                            existingLabel.attributes["growerCodes"] = growerText

                            // Create updated label text
                            val displayParcelNo = if (parcel.subParcelNo.isNotBlank() && parcel.subParcelNo != "0") {
                                "${parcel.parcelNo}/${parcel.subParcelNo}"
                            } else {
                                parcel.parcelNo.toString()
                            }
                            val updatedLabelText = if (growerText.isNotEmpty()) {
                                "$displayParcelNo\n${parcel.khewatInfo}\n$growerText"
                            } else {
                                "$displayParcelNo\n${parcel.khewatInfo}"
                            }

                            val updatedPolyLabelSymbol = TextSymbol().apply {
                                text = updatedLabelText
                                size = 16f
                                color = textColor
                                horizontalAlignment = TextSymbol.HorizontalAlignment.CENTER
                                verticalAlignment = TextSymbol.VerticalAlignment.MIDDLE
                                haloWidth = 1f
                                fontWeight = TextSymbol.FontWeight.BOLD
                            }

                            val myPolygonCenterLatLon = polygon.extent.center
                            val updatedLabelGraphic =
                                Graphic(myPolygonCenterLatLon, updatedPolyLabelSymbol)

                            // Copy all attributes including grower codes
                            existingLabel.attributes.forEach { (key, value) ->
                                updatedLabelGraphic.attributes[key] = value
                            }

                            // SAFE: Remove and add only if label still exists in the list
                            val currentIndex = surveyLabelGraphics.graphics.indexOf(existingLabel)
                            if (currentIndex >= 0 && currentIndex < surveyLabelGraphics.graphics.size) {
                                surveyLabelGraphics.graphics.remove(existingLabel)
                                surveyLabelGraphics.graphics.add(currentIndex, updatedLabelGraphic)

                                // Update the stored original label
                                originalLabelGraphics[parcelId] = updatedLabelGraphic

                                Log.d(
                                    "AddGraphics",
                                    "Updated grower codes for parcel ${parcel.id}: $growerText"
                                )
                            } else {
                                Log.w(
                                    "AddGraphics",
                                    "Label index out of bounds, skipping update for parcel ${parcel.id}"
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(
                                "AddGraphics",
                                "Error updating label for parcel ${parcel.id}: ${e.message}",
                                e
                            )
                        }
                    } else {
                        Log.w("AddGraphics", "Label not found for parcel ${parcel.id}")
                    }
                }
            }
        } else {
            // For unsurveyed parcels, store the label immediately
            originalLabelGraphics[parcelId] = labelGraphic
        }
    }

    private fun restoreOriginalGraphics() {
        Log.d("RESTORE_DEBUG", "Starting graphics restoration...")

        // Restore polygon symbols
        for (graphic in surveyParcelsGraphics.graphics) {
            val parcelId = graphic.attributes["parcel_id"] as? Long ?: continue
            Log.d("RESTORE_DEBUG", "Processing graphic with parcel_id: $parcelId")

            val originalSymbol = originalGraphicSymbols[parcelId]
            if (originalSymbol != null) {
                graphic.symbol = originalSymbol
                Log.d("RESTORE_DEBUG", "Restored symbol for parcel_id: $parcelId")
            } else {
                val surveyStatus = graphic.attributes["surveyStatusCode"] as? Int ?: 1
                val correctSymbol = getSymbolForSurveyStatus(surveyStatus)
                graphic.symbol = correctSymbol
                originalGraphicSymbols[parcelId] = correctSymbol
                Log.d(
                    "RESTORE_DEBUG",
                    "Generated new symbol for parcel_id: $parcelId, status: $surveyStatus"
                )
            }
        }

        // Clear and restore labels
        surveyLabelGraphics.graphics.clear()

        for (graphic in surveyParcelsGraphics.graphics) {
            val parcelId = graphic.attributes["parcel_id"] as? Long ?: continue

            // Try to get the stored original label first
            val originalLabel = originalLabelGraphics[parcelId]

            if (originalLabel != null) {
                // Use the stored label which already has grower codes
                surveyLabelGraphics.graphics.add(originalLabel)
                Log.d("RESTORE_DEBUG", "Restored original label for parcel_id: $parcelId")
            } else {
                // Create new label if no original exists
                val parcelNo = graphic.attributes["parcel_no"]?.toString() ?: ""
                val subParcelNo = graphic.attributes["sub_parcel_no"]?.toString() ?: ""
                val khewatInfo = graphic.attributes["khewatInfo"]?.toString() ?: ""
                val surveyStatus = graphic.attributes["surveyStatusCode"] as? Int ?: 1
                // Try to get stored grower codes from attributes
                val storedGrowerCodes = graphic.attributes["growerCodes"]?.toString() ?: ""

                val displayText = if (subParcelNo.isBlank() || subParcelNo == "0") {
                    parcelNo
                } else {
                    "$parcelNo-$subParcelNo"
                }

                val textColor = when (surveyStatus) {
                    2 -> ContextCompat.getColor(context, R.color.parcel_green)
                    else -> ContextCompat.getColor(context, R.color.parcel_red)
                }

                val highlightColor = when (surveyStatus) {
                    2 -> Color.BLACK
                    else -> Color.YELLOW
                }

                // Include stored grower codes if available
                val labelText = if (storedGrowerCodes.isNotEmpty()) {
                    "$displayText\n$khewatInfo\n$storedGrowerCodes"
                } else {
                    "$displayText\n$khewatInfo"
                }

                val polyLabelSymbol = TextSymbol().apply {
                    text = labelText
                    size = 10f
                    color = textColor
                    horizontalAlignment = TextSymbol.HorizontalAlignment.CENTER
                    verticalAlignment = TextSymbol.VerticalAlignment.MIDDLE
                    haloColor = highlightColor
                    haloWidth = 1f
                    fontWeight = TextSymbol.FontWeight.BOLD
                }

                val geometry = graphic.geometry
                val centerPoint = if (geometry is Polygon) {
                    geometry.extent.center
                } else {
                    geometry.extent.center
                }

                val newLabel = Graphic(centerPoint, polyLabelSymbol)

                // Copy all attributes including grower codes
                graphic.attributes.forEach { (key, value) ->
                    newLabel.attributes[key] = value
                }

                surveyLabelGraphics.graphics.add(newLabel)
                originalLabelGraphics[parcelId] = newLabel

                Log.d(
                    "RESTORE_DEBUG",
                    "Created new label for parcel_id: $parcelId with grower codes: $storedGrowerCodes"
                )
            }
        }

        Log.d("RESTORE_DEBUG", "Graphics restoration completed")
    }

    private fun handleFabClick() {
        if (checkPermission()) {
            getInitialLocation(true)
        } else {
            requestPermission()
        }
    }

    private fun getInitialLocation(enableZoom: Boolean = true) {
        if (!checkPermission()) {
            requestPermission()
            return
        }

        if (!Utility.checkGPS(requireActivity())) {
            Utility.buildAlertMessageNoGps(requireActivity())
            return
        }

        try {
            Utility.showProgressAlertDialog(requireContext(), "Getting location...")

            // Try to get last known location first
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermission()
                return
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && isLocationValid(location)) {
                    Utility.dismissProgressAlertDialog()
                    handleInitialLocation(location, enableZoom)
                } else {
                    // Request fresh location
                    requestFreshLocation(enableZoom)
                }
            }.addOnFailureListener {
                requestFreshLocation(enableZoom)
            }

        } catch (e: SecurityException) {
            Utility.dismissProgressAlertDialog()
            requestPermission()
        }
    }

    private fun requestFreshLocation(enableZoom: Boolean) {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        ).apply {
            setMinUpdateIntervalMillis(1000L)
            setMaxUpdateDelayMillis(3000L)
            setWaitForAccurateLocation(false)
            setMinUpdateDistanceMeters(5f)
        }.build()

        val freshLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val meterAccuracy = sharedPreferences.getInt(
                        Constants.SHARED_PREF_METER_ACCURACY,
                        Constants.SHARED_PREF_DEFAULT_ACCURACY
                    )

                    if (location.accuracy < meterAccuracy && isLocationValid(location) && !isMockLocation(
                            location
                        )
                    ) {
                        Utility.dismissProgressAlertDialog()
                        fusedLocationClient.removeLocationUpdates(this)
                        handleInitialLocation(location, enableZoom)
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            freshLocationCallback,
            Looper.getMainLooper()
        )

        // Timeout after 10 seconds
        viewLifecycleOwner.lifecycleScope.launch {
            delay(10000)
            fusedLocationClient.removeLocationUpdates(freshLocationCallback)
            Utility.dismissProgressAlertDialog()

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    "Unable to get accurate location. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handleInitialLocation(location: Location, enableZoom: Boolean) {
        updateLocationOnMap(location)

        if (enableZoom) {
            val point = Point(
                location.longitude,
                location.latitude,
                SpatialReferences.getWgs84()
            )
            binding.parcelMapview.setViewpointAsync(Viewpoint(point, 1000.0))
        }
        startContinuousLocationUpdates()
    }

    private fun startContinuousLocationUpdates() {
        if (!checkPermission()) {
            return
        }

        if (!Utility.checkGPS(requireActivity())) {
            return
        }

        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2000L // Update every 2 seconds
            ).apply {
                setMinUpdateIntervalMillis(1000L) // At least 1 second between updates
                setMaxUpdateDelayMillis(3000L)
                setWaitForAccurateLocation(false)
                setMinUpdateDistanceMeters(5f) // Update when moved 5 meters
            }.build()

            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            Log.d("Location", "Started continuous location updates")

        } catch (e: Exception) {
            Log.e("Location", "Error starting location updates: ${e.message}", e)
        }
    }

    private fun handleLocationUpdate(location: Location) {
        try {
            // Validate location
            if (!isLocationValid(location)) {
                Log.w("Location", "Invalid location received")
                return
            }

            // Check for mock location
            if (isMockLocation(location)) {
                stopLocationUpdates()
                Utility.exitApplication(
                    "Warning!",
                    "Please disable mock/fake location. The application will exit now.",
                    requireActivity()
                )
                return
            }

            // Update location on map
            updateLocationOnMap(location)

            // Store current location
            viewModel.currentLocation = Utility.convertGpsTimeToString(location.time)

            Log.d(
                "Location",
                "Location updated: ${location.latitude}, ${location.longitude}, Accuracy: ${location.accuracy}m"
            )

        } catch (e: Exception) {
            Log.e("Location", "Error handling location update: ${e.message}", e)
        }
    }

    // Check if location is valid
    private fun isLocationValid(location: Location): Boolean {
        return location.latitude in -90.0..90.0 &&
                location.longitude in -180.0..180.0 &&
                location.accuracy < 100f // Reject very inaccurate locations
    }

    private fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }

    // Update marker on map with smooth animation
    private fun updateLocationOnMap(location: Location) {
        try {
            val point = Point(
                location.longitude,
                location.latitude,
                SpatialReferences.getWgs84()
            )

            // Remove old marker
            currentLocationGraphic?.let {
                currentLocationGraphicOverlay.graphics.remove(it)
            }

            // Create marker symbol
            val markerSymbol = SimpleMarkerSymbol(
                SimpleMarkerSymbol.Style.CIRCLE,
                ContextCompat.getColor(requireContext(), R.color.current_location),
                22f
            ).apply {
                outline = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.WHITE, 2f)
            }

            // Add new marker
            currentLocationGraphic = Graphic(point, markerSymbol)

            if (!binding.parcelMapview.graphicsOverlays.contains(currentLocationGraphicOverlay)) {
                binding.parcelMapview.graphicsOverlays.add(currentLocationGraphicOverlay)
            }

            currentLocationGraphicOverlay.graphics.add(currentLocationGraphic)

            Log.d("Location", "Marker updated at: ${location.latitude}, ${location.longitude}")

        } catch (e: Exception) {
            Log.e("Location", "Error updating location marker: ${e.message}", e)
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("Location", "Stopped location updates")
        }
    }

    private fun startLocationProcess(enableZoom: Boolean) {
        try {
            if (Utility.checkGPS(requireActivity())) {
                Utility.showProgressAlertDialog(context, "Please wait, fetching location...")
                getInitialLocation(enableZoom)
            } else {
                Utility.buildAlertMessageNoGps(requireActivity())
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Location Exception: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setHeaderText() {
        val mauzaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME,
            Constants.SHARED_PREF_DEFAULT_STRING
        )

        val areaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
            Constants.SHARED_PREF_DEFAULT_STRING
        )

        if (areaName != null) {
            binding.tvHeader.text = "Tehsil: $mauzaName ($areaName)"
        } else {
            binding.tvHeader.text = "$mauzaName (Map)"
        }
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            permissionRequestCode
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            permissionRequestCode -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationProcess(true)
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION
                    )
                ) {
                    showSettingsDialog("Location")
                } else {
                    showMessageOKCancel(
                        "You need to allow location permission"
                    ) { _, _ ->
                        requestPermission()
                    }
                }
            }
        }
    }

    private fun showSettingsDialog(value: String) {
        val builder = AlertDialog.Builder(context)
            .setMessage("You have denied $value permission permanently. Please go to settings to enable it.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", context.packageName, null)
                intent.data = uri
                startActivity(intent)
            }.setNegativeButton("Cancel", null)

        val dialog = builder.create()
        dialog.show()

        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

        positiveButton.textSize = 16f
        positiveButton.typeface = android.graphics.Typeface.DEFAULT_BOLD

        negativeButton.textSize = 16f
        negativeButton.typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun showMessageOKCancel(message: String, okListener: DialogInterface.OnClickListener) {
        val builder =
            AlertDialog.Builder(context).setMessage(message).setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)

        val dialog = builder.create()
        dialog.show()

        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

        positiveButton.textSize = 16f
        positiveButton.typeface = android.graphics.Typeface.DEFAULT_BOLD

        negativeButton.textSize = 16f
        negativeButton.typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    override fun onPause() {
        _binding?.parcelMapview?.pause()
        super.onPause()
        try {
            LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(refreshReceiver)
        } catch (e: Exception) {
            Log.e("FragmentMap", "Error unregistering receiver: ${e.message}")
        }
        stopLocationUpdates()
        stopLoadingParcels()
    }

    private fun refreshMapData() {
        Log.d("FragmentMap", "Refreshing map data...")
        viewLifecycleOwner.lifecycleScope.launch {
            surveyParcelsGraphics.graphics?.clear()
            surveyLabelGraphics.graphics?.clear()
            originalGraphicSymbols.clear()
            originalLabelGraphics.clear()
            delay(300)
            loadMap(ids.isNotEmpty())
        }
    }

    private inner class IdentifyFeatureLayerTouchListener(
        context: Context?, mapView: MapView, private val go: GraphicsOverlay
    ) : DefaultMapViewOnTouchListener(context, mapView) {

        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
        ): Boolean {
            return e1 != null
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // Handle split mode touches
            if (isSplitMode) {
                handleSplitModeTouch(e)
                return true
            }

            // Handle task assign mode touches
//            if (isTaskAssignMode) {
//                handleTaskAssignModeTouch(e)
//                return true
//            }

            val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())

            val identifyFuture = mMapView.identifyGraphicsOverlayAsync(go, screenPoint, 10.0, false)
            identifyFuture.addDoneListener {
                try {
                    val result = identifyFuture.get()
                    if (result.graphics.isNotEmpty()) {
                        val graphic = result.graphics[0]

                        mCallOut.dismiss()

                        showSurveyedCallOutNew(graphic, screenPoint)
                    }
                } catch (ex: Exception) {
                    Log.e("IdentifyTouch", "Error: ${ex.message}")
                }
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            super.onLongPress(e)

            val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())

            val identifyFuture = mMapView.identifyGraphicsOverlayAsync(go, screenPoint, 10.0, false)
            identifyFuture.addDoneListener {
                try {
                    val result = identifyFuture.get()
                    if (result.graphics.isNotEmpty()) {
                        val graphic = result.graphics[0]

                        val surveyStatus = graphic.attributes["surveyStatusCode"] as? Int ?: 1

                        if (surveyStatus == 2) {
                            // Parcel is surveyed - don't allow splitting
                            Toast.makeText(
                                context,
                                "Surveyed parcels cannot be split",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@addDoneListener
                        }

                        mCallOut.dismiss()

                        enterSplitMode(graphic)

                        Toast.makeText(
                            context,
                            "Split mode enabled for parcel ${graphic.attributes["parcel_no"]}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "No parcel found at this location",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (ex: Exception) {
                    Log.e("LongPress", "Error identifying parcel: ${ex.message}")
                    Toast.makeText(
                        context,
                        "Error selecting parcel for split",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        private fun handleSplitModeTouch(e: MotionEvent) {
            if (!isSplitMode || splitTargetGraphic == null) return
            if (!::splitOverlay.isInitialized) return

            val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())
            val mapPoint = mMapView.screenToLocation(screenPoint)
            if (splitPoints.isNotEmpty() && splitPoints.last() == mapPoint) return

            splitPoints.add(mapPoint)
            updateSplitLineVisual()

            Toast.makeText(
                context,
                "Added point ${splitPoints.size}. Tap to add more points or Apply Split.",
                Toast.LENGTH_SHORT
            ).show()
        }

//        private fun handleTaskAssignModeTouch(e: MotionEvent) {
//            if (!isTaskAssignMode) return
//
//            val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())
//
//            val identifyFuture = mMapView.identifyGraphicsOverlayAsync(go, screenPoint, 10.0, false)
//
//            identifyFuture.addDoneListener {
//                try {
//                    val result = identifyFuture.get()
//                    if (result.graphics.isNotEmpty()) {
//                        val selectedGraphic = result.graphics[0]
//                        val parcelId = selectedGraphic.attributes["parcel_id"] as? Long
//                            ?: return@addDoneListener
//
//                        if (selectedTaskParcels.containsKey(parcelId)) {
//                            // Unselect
//                            selectedTaskParcels.remove(parcelId)
//
//                            // Restore original symbol
//                            val surveyStatus =
//                                selectedGraphic.attributes["surveyStatusCode"] as? Int ?: 1
//                            selectedGraphic.symbol = getSymbolForSurveyStatus(surveyStatus)
//
//                            Toast.makeText(
//                                context,
//                                "Parcel deselected (${selectedTaskParcels.size} selected)",
//                                Toast.LENGTH_SHORT
//                            ).show()
//                        } else {
//                            // Select
//                            val parcelInfo = ParcelInfo(
//                                parcelId = parcelId,
//                                parcelNo = selectedGraphic.attributes["parcel_no"].toString(),
//                                subParcelNo = selectedGraphic.attributes["sub_parcel_no"].toString(),
//                                area = selectedGraphic.attributes["area"].toString(),
//                                khewatInfo = selectedGraphic.attributes["khewatInfo"].toString(),
//                                unitId = selectedGraphic.attributes["unit_id"]?.toString()
//                                    ?.toLongOrNull() ?: 0L,
//                                groupId = selectedGraphic.attributes["group_id"]?.toString()
//                                    ?.toLongOrNull() ?: 0L
//                            )
//
//                            selectedTaskParcels[parcelId] = parcelInfo
//
//                            // Highlight selected parcel
//                            selectedGraphic.symbol = SimpleFillSymbol(
//                                SimpleFillSymbol.Style.SOLID,
//                                Color.argb(100, 0, 150, 255),
//                                SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 3f)
//                            )
//
//                            Toast.makeText(
//                                context,
//                                "Parcel selected (${selectedTaskParcels.size} total)",
//                                Toast.LENGTH_SHORT
//                            ).show()
//                        }
//
////                        updateTaskAssignSelectionCount()
//                    }
//                } catch (e: Exception) {
//                    Log.e("TaskAssign", "Error selecting parcel: ${e.message}")
//                }
//            }
//        }
    }

    private fun getSymbolForSurveyStatus(status: Int): SimpleFillSymbol {
        return when (status) {
            1 -> unSurveyedBlocks
            2 -> surveyedBlocks
            3 -> revisitBlocks
            4 -> lockedBlocks
            else -> unSurveyedBlocks
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showSurveyedCallOutNew(
        graphics: Graphic,
        screenPoint: android.graphics.Point
    ) {
        val inflater = this.layoutInflater
        dialogView = inflater.inflate(R.layout.cardview_map_info_new, null)

        val attr = graphics.attributes

        tvParcelNo = dialogView.findViewById(R.id.tv_parcel_no_value)
        tvParcelNoUni = dialogView.findViewById(R.id.tv_parcel_no_uni_value)
        val tvParcelArea = dialogView.findViewById<TextView>(R.id.tv_parcel_area_value)
        val lParcel = dialogView.findViewById<LinearLayout>(R.id.layout_parcel)
        val btnStartSurvey = dialogView.findViewById<Button>(R.id.btn_start_survey)
        val btnRevisitSurvey = dialogView.findViewById<Button>(R.id.btn_revisit_survey)
        val btnRetakePicturesSurvey =
            dialogView.findViewById<Button>(R.id.btn_retake_pictures_survey)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)


        val delete = dialogView.findViewById<ImageView>(R.id.delete)
        val mapLocation = dialogView.findViewById<ImageView>(R.id.mapLocaton)
        val directions = dialogView.findViewById<ImageView>(R.id.directions)

        val lSplitParcel = dialogView.findViewById<LinearLayout>(R.id.layout_split_parcel)
        val etSplitParcel = dialogView.findViewById<EditText>(R.id.et_split_parcel)

        val lMergeParcel = dialogView.findViewById<LinearLayout>(R.id.layout_merge_parcel)
        tvMergeParcel = dialogView.findViewById(R.id.tv_merge_parcel)
        tvMergeParcelHi = dialogView.findViewById(R.id.tv_merge_parcel_hi)

        val rgParcel = dialogView.findViewById<RadioGroup>(R.id.rg_parcel)

        rgParcel.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_same -> {
                    lSplitParcel.visibility = View.GONE
                    lMergeParcel.visibility = View.GONE
                }

                R.id.rb_merge -> {
                    lSplitParcel.visibility = View.GONE
                    lMergeParcel.visibility = View.VISIBLE
                }

                R.id.rb_merge_multi -> {
                    lSplitParcel.visibility = View.GONE
                    lMergeParcel.visibility = View.GONE
                }
            }
        }

        tvParcelNo.text = attr["parcel_no"].toString()
        tvParcelNoUni.text = attr["khewatInfo"].toString()
        val areaSqFt = attr["area"].toString().toDoubleOrNull() ?: 0.0
        val areaKanal = areaSqFt / 5445.0
        tvParcelArea.text = String.format(Locale.US, "%.2f Kanal", areaKanal)

        if (binding.parcelMapview.map == null) {
            Toast.makeText(context, "MapView does not have a valid map", Toast.LENGTH_LONG).show()
            return
        }

        val mapPoint: Point? = try {
            binding.parcelMapview.screenToLocation(screenPoint)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "screenToLocation threw an exception: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (mapPoint == null) {
            Toast.makeText(
                context,
                "screenToLocation returned null for screenPoint: $screenPoint",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        when (attr["surveyStatusCode"].toString().toInt()) {
            1 -> {
                lParcel.visibility = View.VISIBLE
                btnStartSurvey.visibility = View.VISIBLE
                btnRevisitSurvey.visibility = View.GONE
                btnRetakePicturesSurvey.visibility = View.GONE
                delete.visibility = View.GONE
            }

            2 -> {
                lParcel.visibility = View.GONE
                lSplitParcel.visibility = View.GONE
                lMergeParcel.visibility = View.GONE
                btnStartSurvey.visibility = View.GONE
                btnRevisitSurvey.visibility = View.GONE
                delete.visibility = View.GONE
            }

            else -> {
            }
        }

        attr["parcel_id"]?.toString()?.toLongOrNull() ?: 0L


        tvMergeParcel.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Tap on parcels to select for merge",
                Toast.LENGTH_SHORT
            ).show()

            isMergeMode = true
            selectedMergeParcels.clear()

            dialogView.findViewById<View>(R.id.card_root).visibility = View.GONE
            binding.mergeControlBar.visibility = View.VISIBLE

            for (graphic in surveyParcelsGraphics.graphics) {
                graphic.symbol = defaultParcelSymbol
            }

            val baseParcelId = graphics.attributes["parcel_id"].toString()

            graphics.symbol = SimpleFillSymbol(
                SimpleFillSymbol.Style.SOLID,
                Color.argb(100, 255, 140, 0),
                SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 3f)
            )

            val touchListener =
                object :
                    DefaultMapViewOnTouchListener(requireContext(), binding.parcelMapview) {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (!isMergeMode) return super.onSingleTapConfirmed(e)

                        val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())

                        val identifyFuture =
                            binding.parcelMapview.identifyGraphicsOverlayAsync(
                                surveyParcelsGraphics,
                                screenPoint,
                                10.0,
                                false
                            )

                        identifyFuture.addDoneListener {
                            try {
                                val result = identifyFuture.get()
                                if (result.graphics.isNotEmpty()) {
                                    val selectedGraphic = result.graphics[0]
                                    val parcelId =
                                        selectedGraphic.attributes["parcel_id"].toString()
                                    val parcelNo =
                                        selectedGraphic.attributes["parcel_no"].toString()
                                    val surveyStatusCode =
                                        selectedGraphic.attributes["surveyStatusCode"].toString()
                                            .toInt()

                                    if (parcelId == baseParcelId) {
                                        Toast.makeText(
                                            requireContext(),
                                            "Base parcel cannot be selected",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@addDoneListener
                                    }

                                    if (surveyStatusCode == 2) {
                                        Toast.makeText(
                                            requireContext(),
                                            "Surveyed parcel cannot be selected",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@addDoneListener
                                    }

                                    if (selectedMergeParcels.containsKey(parcelId)) {
                                        selectedMergeParcels.remove(parcelId)
                                        selectedGraphic.symbol = defaultParcelSymbol
                                        Toast.makeText(
                                            requireContext(),
                                            "Parcel unselected",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        selectedMergeParcels[parcelId] = parcelNo
                                        selectedGraphic.symbol = SimpleFillSymbol(
                                            SimpleFillSymbol.Style.SOLID,
                                            Color.argb(80, 30, 144, 255),
                                            SimpleLineSymbol(
                                                SimpleLineSymbol.Style.SOLID,
                                                Color.BLUE,
                                                2f
                                            )
                                        )
                                    }

                                    val parcelNos =
                                        selectedMergeParcels.values.joinToString(", ")
                                    val parcelIds =
                                        selectedMergeParcels.keys.joinToString(", ")

                                    tvMergeParcel.text = parcelNos
                                    tvMergeParcelHi.text = parcelIds
                                    viewModel.parcelOperationValue = parcelNos
                                } else {
                                    Toast.makeText(
                                        requireContext(),
                                        "No parcel found at this location",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    requireContext(),
                                    "Error identifying parcel",
                                    Toast.LENGTH_SHORT
                                ).show()
                                e.printStackTrace()
                            }
                        }

                        return true
                    }
                }

            binding.parcelMapview.onTouchListener = touchListener
        }

        btnStartSurvey.setOnClickListener {
            val radioButton: RadioButton =
                dialogView.findViewById(rgParcel.checkedRadioButtonId)

            viewModel.parcelOperation = radioButton.text.toString()
            viewModel.parcelId = attr["parcel_id"].toString().toLong()
            viewModel.parcelNo = attr["parcel_no"].toString()
            viewModel.subParcelNo = attr["sub_parcel_no"].toString()

            // ✅ Read MultiMerge state from DB (not SharedPreferences)
            viewLifecycleOwner.lifecycleScope.launch {
                // Read from DB
                val parcelIdLong = attr["parcel_id"].toString().toLong()
                val parcelFromDb = withContext(Dispatchers.IO) {
                    database.activeParcelDao().getParcelById(parcelIdLong)
                }
                val multiMergeParcels = parcelFromDb?.multiMergeParcelNos
                val subParcelNo = parcelFromDb?.subParcelNo ?: ""
                val isSplitChild = subParcelNo.isNotBlank() && subParcelNo != "0"

                // ✅ Sirf survivor parcel ke liye MultiMerge set karo, split children ke liye nahi
                if (!multiMergeParcels.isNullOrBlank() && !isSplitChild) {
                    Log.d("MultiMerge", "✅ MultiMerge survivor detected: $multiMergeParcels")
                    viewModel.parcelOperation = "MultiMerge"
                    viewModel.parcelOperationValue = multiMergeParcels
                } else if (!multiMergeParcels.isNullOrBlank() && isSplitChild) {
                    Log.d("MultiMerge", "ℹ️ Split child of MultiMerge — keeping operation as ${viewModel.parcelOperation}")
                    // parcelOperation ko override nahi karna — uploadSurvey() khud detect karega
                }


                when (rgParcel.checkedRadioButtonId) {
                    R.id.rb_same -> {
                        Log.d("TaskAssign", "=== RB_SAME OPERATION STARTED ===")

                        // ✅ Only clear parcelOperationValue if NOT a MultiMerge
                        if (viewModel.parcelOperation != "MultiMerge") {
                            viewModel.parcelOperationValue = ""
                        } else {
                            Log.d("TaskAssign", "Preserving MultiMerge parcelOperationValue: ${viewModel.parcelOperationValue}")
                        }
                        viewModel.imageTaken = 0
                        viewModel.discrepancyPicturePath = ""

                        Log.d("TaskAssign", "All attributes in graphic:")
                        attr.forEach { (key, value) ->
                            Log.d("TaskAssign", "  $key = $value")
                        }

                        val parcelId = attr["parcel_id"].toString().toLong()
                        Log.d("TaskAssign", "parcelId: $parcelId")

                        val parcelNo = attr["parcel_no"].toString()
                        Log.d("TaskAssign", "parcelNo: $parcelNo")

                        val subParcelNo = attr["sub_parcel_no"].toString()
                        Log.d("TaskAssign", "subParcelNo: $subParcelNo")

                        val area = attr["area"].toString()
                        Log.d("TaskAssign", "area: $area")

                        val khewatInfo = attr["khewatInfo"].toString()
                        Log.d("TaskAssign", "khewatInfo: $khewatInfo")

                        val parcelOperation = viewModel.parcelOperation
                        Log.d("TaskAssign", "parcelOperation: $parcelOperation")

                        val parcelOperationValue = viewModel.parcelOperationValue
                        Log.d("TaskAssign", "parcelOperationValue: $parcelOperationValue")

                        val parcelOperationValueHi = tvMergeParcelHi.text.toString().trim()
                        Log.d("TaskAssign", "parcelOperationValueHi: $parcelOperationValueHi")

                        val unitIdRaw = attr["unit_id"]
                        Log.d(
                            "TaskAssign",
                            "unit_id (raw): $unitIdRaw (Type: ${unitIdRaw?.javaClass?.simpleName})"
                        )

                        val groupIdRaw = attr["group_id"]
                        Log.d(
                            "TaskAssign",
                            "group_id (raw): $groupIdRaw (Type: ${groupIdRaw?.javaClass?.simpleName})"
                        )

                        val unitId = try {
                            attr["unit_id"]?.toString()?.toLongOrNull() ?: 0L
                        } catch (e: Exception) {
                            Log.e("TaskAssign", "Error converting unit_id: ${e.message}")
                            0L
                        }
                        Log.d("TaskAssign", "unitId (converted): $unitId")

                        val groupId = try {
                            attr["group_id"]?.toString()?.toLongOrNull() ?: 0L
                        } catch (e: Exception) {
                            Log.e("TaskAssign", "Error converting group_id: ${e.message}")
                            0L
                        }
                        Log.d("TaskAssign", "groupId (converted): $groupId")

                        val context = requireContext()
                        val intent = Intent(context, SurveyActivity::class.java).apply {
                            putExtra("parcelId", viewModel.parcelId)
                            putExtra("parcelNo", viewModel.parcelNo)
                            putExtra("subParcelNo", viewModel.subParcelNo)
                            putExtra("parcelArea", attr["area"].toString())
                            putExtra("khewatInfo", attr["khewatInfo"].toString())
                            putExtra(
                                "parcelOperation",
                                viewModel.parcelOperation
                            )        // now "MultiMerge" if applicable
                            putExtra(
                                "parcelOperationValue",
                                viewModel.parcelOperationValue
                            ) // now the CSV
                            putExtra(
                                "parcelOperationValueHi",
                                tvMergeParcelHi.text.toString().trim()
                            )
                            putExtra("unitId", attr["unit_id"]?.toString()?.toLongOrNull() ?: 0L)
                            putExtra("groupId", attr["group_id"]?.toString()?.toLongOrNull() ?: 0L)
                        }

                        Log.d("TaskAssign", "Intent extras being passed:")
                        Log.d("TaskAssign", "  parcelId: $parcelId")
                        Log.d("TaskAssign", "  parcelNo: $parcelNo")
                        Log.d("TaskAssign", "  subParcelNo: $subParcelNo")
                        Log.d("TaskAssign", "  parcelArea: $area")
                        Log.d("TaskAssign", "  khewatInfo: $khewatInfo")
                        Log.d("TaskAssign", "  parcelOperation: $parcelOperation")
                        Log.d("TaskAssign", "  parcelOperationValue: $parcelOperationValue")
                        Log.d("TaskAssign", "  parcelOperationValueHi: $parcelOperationValueHi")
                        Log.d("TaskAssign", "  unitId: $unitId")
                        Log.d("TaskAssign", "  groupId: $groupId")

                        Log.d("TaskAssign", "Starting SurveyActivity...")
                        startActivity(intent)
//                    dialog.dismiss()
                        Log.d("TaskAssign", "=== RB_SAME OPERATION COMPLETED ===")
                    }

//                R.id.rb_split -> {
//                    if (etSplitParcel.text.toString().trim().isEmpty()) {
//                        etSplitParcel.apply {
//                            setText("")
//                            error = "Field cannot be empty"
//                            requestFocus()
//                        }
//                        return@setOnClickListener
//                    }
//
//                    if (etSplitParcel.text.toString().trim().toInt() < 2) {
//                        etSplitParcel.apply {
//                            setText("")
//                            error = "Enter valid number of parcels"
//                            requestFocus()
//                        }
//                        return@setOnClickListener
//                    }
//
//                    viewModel.parcelOperationValue = etSplitParcel.text.toString().trim()
//                    viewModel.subParcelList.clear()
//
//                    if (viewModel.subParcelList.isEmpty()) {
//                        val totalParts: Int = viewModel.parcelOperationValue.toInt()
//                        val subParcels = ArrayList<SubParcel>()
//                        for (i in 1..totalParts) {
//                            subParcels.add(SubParcel(id = i))
//                        }
//                        viewModel.subParcelList = subParcels
//                    }
//
//                    val bundle = Bundle().apply {
//                        putLong("parcelId", attr["parcel_id"].toString().toLong())
//                        putString("parcelNo", attr["parcel_no"].toString())
//                        putString("subParcelNo", attr["sub_parcel_no"].toString())
//                        putString("parcelArea", attr["area"].toString())
//                        putString("khewatInfo", attr["khewatInfo"].toString())
//                        putString("parcelOperation", viewModel.parcelOperation)
//                        putString("parcelOperationValue", viewModel.parcelOperationValue)
//                        putString(
//                            "parcelOperationValueHi",
//                            tvMergeParcelHi.text.toString().trim()
//                        )
//                        putLong("unitId", attr["unit_id"].toString().toLong())
//                        putLong("groupId", attr["group_id"].toString().toLong())
//                    }
//
//                    findNavController().navigate(
//                        R.id.action_fragmentMap_to_fragmentSubParcelList,
//                        bundle
//                    )
////                    dialog.dismiss()
//                }

                    R.id.rb_merge -> {
                        if (tvMergeParcel.text.toString().trim().isEmpty()) {
                            Toast.makeText(
                                requireContext(),
                                "Merge parcel field is empty",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }

                        val context = requireContext()
                        val intent = Intent(context, SurveyActivity::class.java).apply {
                            putExtra("parcelId", attr["parcel_id"].toString().toLong())
                            putExtra("parcelNo", attr["parcel_no"].toString())
                            putExtra("subParcelNo", attr["sub_parcel_no"].toString())
                            putExtra("parcelArea", attr["area"].toString())
                            putExtra("khewatInfo", attr["khewatInfo"].toString())
                            putExtra("parcelOperation", viewModel.parcelOperation)
                            putExtra("parcelOperationValue", viewModel.parcelOperationValue)
                            putExtra(
                                "parcelOperationValueHi",
                                tvMergeParcelHi.text.toString().trim()
                            )
                            putExtra("unitId", attr["unit_id"].toString().toLong())
                            putExtra("groupId", attr["group_id"].toString().toLong())
                        }
                        startActivity(intent)
//                    dialog.dismiss()
                    }


                    R.id.rb_merge_multi -> {
                        // Enter multi-merge selection mode
                        val baseParcelId = attr["parcel_id"].toString().toLong()
                        val baseParcelNo = attr["parcel_no"].toString()

                        multiMergeBaseParcelId = baseParcelId
                        isMultiMergeMode = true
                        selectedMultiMergeParcels.clear()

                        // Add base parcel to selection
                        selectedMultiMergeParcels[baseParcelId] = graphics

                        // Hide callout, show merge control bar
                        dialogView.findViewById<View>(R.id.card_root).visibility = View.GONE
                        binding.mergeControlBar.visibility = View.VISIBLE

                        // Highlight all parcels in default style
                        for (graphic in surveyParcelsGraphics.graphics) {
                            graphic.symbol = defaultParcelSymbol
                        }

                        // Highlight base parcel
                        graphics.symbol = SimpleFillSymbol(
                            SimpleFillSymbol.Style.SOLID,
                            Color.argb(100, 255, 140, 0),
                            SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 3f)
                        )

                        Toast.makeText(
                            requireContext(),
                            "Tap adjacent parcels to add to merge. Base: $baseParcelNo",
                            Toast.LENGTH_LONG
                        ).show()

                        // Set touch listener for multi-merge selection
                        binding.parcelMapview.onTouchListener = object :
                            DefaultMapViewOnTouchListener(requireContext(), binding.parcelMapview) {
                            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                if (!isMultiMergeMode) return super.onSingleTapConfirmed(e)

                                val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())
                                val identifyFuture =
                                    binding.parcelMapview.identifyGraphicsOverlayAsync(
                                        surveyParcelsGraphics, screenPoint, 10.0, false
                                    )

                                identifyFuture.addDoneListener {
                                    try {
                                        val result = identifyFuture.get()
                                        if (result.graphics.isNotEmpty()) {
                                            val selectedGraphic = result.graphics[0]
                                            val parcelId =
                                                selectedGraphic.attributes["parcel_id"] as? Long
                                                    ?: return@addDoneListener
                                            val parcelNo =
                                                selectedGraphic.attributes["parcel_no"].toString()
                                            val surveyStatus =
                                                selectedGraphic.attributes["surveyStatusCode"] as? Int
                                                    ?: 1

                                            if (surveyStatus == 2) {
                                                Toast.makeText(
                                                    requireContext(),
                                                    "Surveyed parcels cannot be merged",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@addDoneListener
                                            }

                                            if (selectedMultiMergeParcels.containsKey(parcelId)) {
                                                // Deselect - but not base parcel
                                                if (parcelId == multiMergeBaseParcelId) {
                                                    Toast.makeText(
                                                        requireContext(),
                                                        "Cannot deselect base parcel",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    return@addDoneListener
                                                }
                                                selectedMultiMergeParcels.remove(parcelId)
                                                selectedGraphic.symbol = defaultParcelSymbol
                                                Toast.makeText(
                                                    requireContext(),
                                                    "Parcel $parcelNo deselected (${selectedMultiMergeParcels.size} selected)",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                selectedMultiMergeParcels[parcelId] =
                                                    selectedGraphic
                                                selectedGraphic.symbol = SimpleFillSymbol(
                                                    SimpleFillSymbol.Style.SOLID,
                                                    Color.argb(80, 30, 144, 255),
                                                    SimpleLineSymbol(
                                                        SimpleLineSymbol.Style.SOLID,
                                                        Color.BLUE,
                                                        2f
                                                    )
                                                )
                                                Toast.makeText(
                                                    requireContext(),
                                                    "Parcel $parcelNo added (${selectedMultiMergeParcels.size} selected)",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    } catch (ex: Exception) {
                                        Log.e("MultiMerge", "Error: ${ex.message}")
                                    }
                                }
                                return true
                            }
                        }
                    }
                }
            }
        }

        btnRevisitSurvey.setOnClickListener {
            graphicCentoid = graphics

            closeCallOut()

            if (checkPermission()) {
                if (Utility.checkGPS(requireActivity())) {
                    Utility.showProgressAlertDialog(
                        context,
                        "Please wait, fetching location..."
                    )
//                    getCurrentLocation()
                } else {
                    Utility.buildAlertMessageNoGps(requireActivity())
                }
            } else {
                requestPermission()
            }
        }

        btnRetakePicturesSurvey.setOnClickListener {
            graphicCentoid = graphics

            val gson = Gson()

            val subParcelsList: List<SubParcelStatus> = try {
                gson.fromJson(
                    attr["subParcelsStatusList"].toString(),
                    Array<SubParcelStatus>::class.java
                ).toList()
            } catch (e: Exception) {
                emptyList()
            }

            var action = 0

            if (subParcelsList.isNotEmpty()) {

                when (subParcelsList.size) {
                    1 -> {
                        val subParcelStatus: SubParcelStatus = subParcelsList.first()

                        if (subParcelStatus.pictureRevisitRequired) {
                            viewModel.parcelOperation = "Same"
                            viewModel.parcelOperationValue = ""
                            viewModel.imageTaken = 0
                            viewModel.discrepancyPicturePath = ""
                            viewModel.newStatusId = attr["newStatusId"].toString().toInt()
                            val gson = Gson()
                            val rejectedSubParcel = RejectedSubParcel(
                                id = subParcelStatus.subParcelNo.toInt(),
                                fieldRecordId = subParcelStatus.fieldRecordId,
                                subParcelNoAction = if (subParcelStatus.fullRevisitRequired) {
                                    "Revisit"
                                } else {
                                    "Retake Picture"
                                },
                                pictureRevisitRequired = true,
                                fullRevisitRequired = subParcelStatus.fullRevisitRequired,
                                position = 0
                            )
                            viewModel.subParcelsStatusList = gson.toJson(rejectedSubParcel)
                        }

                        action = R.id.action_fragmentMap_to_fragmentFormRemarks
                    }

                    else -> {
                        viewModel.rejectedSubParcelsList.clear()

                        val rejectedSubParcel = arrayListOf<RejectedSubParcel>()
                        for (item in subParcelsList) {
                            if (item.fullRevisitRequired || item.pictureRevisitRequired) {
                                rejectedSubParcel.add(
                                    RejectedSubParcel(
                                        id = item.subParcelNo.toInt(),
                                        fieldRecordId = item.fieldRecordId,
                                        subParcelNoAction = if (item.fullRevisitRequired) {
                                            "Revisit"
                                        } else {
                                            "Retake Picture"
                                        },
                                        pictureRevisitRequired = item.pictureRevisitRequired,
                                        fullRevisitRequired = item.fullRevisitRequired,
                                        position = 0
                                    )
                                )
                            }
                        }
                        viewModel.rejectedSubParcelsList = rejectedSubParcel

                        viewModel.parcelOperation = "Split"
                        viewModel.parcelOperationValue = "${rejectedSubParcel.size}"
                        viewModel.imageTaken = 0
                        viewModel.discrepancyPicturePath = ""

                        action = R.id.action_fragmentMap_to_fragmentRejectedSubParcelList
                    }
                }

                viewModel.parcelPkId = attr["parcel_pkid"].toString().toLong()
                viewModel.parcelId = attr["parcel_id"].toString().toLong()
                viewModel.parcelNo = attr["parcel_no"].toString()
                viewModel.subParcelNo = attr["sub_parcel_no"].toString()
                viewModel.parcelStatus = Constants.Parcel_SAME
                viewModel.geom = attr["geom"].toString()
                viewModel.centroid = attr["centroid"].toString()
                viewModel.isRevisit = 1

                viewModel.performCriticalOperation()

                closeCallOut()
                if (isAdded && action != 0) {
                    findNavController().navigate(action)
                } else {
                    Toast.makeText(context, "Action Undefined", Toast.LENGTH_SHORT).show()
                }
            }
        }

        delete.setOnClickListener {
            graphicCentoid = graphics

            val builder = AlertDialog.Builder(requireContext())
                .setTitle("Confirm!")
                .setCancelable(false)
                .setMessage("Are you sure, you want to delete this record.")

            builder.setPositiveButton("Proceed") { dialog, _ ->
                dialog.dismiss()

                viewLifecycleOwner.lifecycleScope.launch {
                    val centroid = attr["centroid"].toString()

                    val listOfNAHRecords = database.notAtHomeSurveyFormDao()
                        .getAllSurveyFormWrtCentroid(centroid)

                    if (listOfNAHRecords.isNotEmpty()) {
                        listOfNAHRecords.forEach { survey ->

                            when (survey.parcelOperation) {
                                "Split" -> {
                                    val recordsList = database.notAtHomeSurveyFormDao()
                                        .getRecord(survey.parcelNo, survey.uniqueId)
                                    for (record in recordsList) {
                                        database.parcelDao().updateParcelSurveyStatus(
                                            record.newStatusId,
                                            ParcelStatus.DEFAULT,
                                            record.centroidGeom
                                        )
                                        database.surveyDao()
                                            .updateSurveyStatus(false, record.surveyId)
                                    }
                                    database.notAtHomeSurveyFormDao()
                                        .deleteSavedRecord(survey.parcelNo, survey.uniqueId)
                                }

                                "Merge" -> {
                                    if (survey.parcelOperationValue.contains(",")) {
                                        val parcelNos =
                                            survey.parcelOperationValue.split(",")
                                                .toMutableList()
                                        for (parcelNo in parcelNos) {

                                            val newStatusId =
                                                database.parcelDao().getNewStatusId(
                                                    parcelNo.toLong(),
                                                    survey.kachiAbadiId
                                                )

                                            database.parcelDao()
                                                .updateParcelSurveyStatusWrtParcelId(
                                                    newStatusId,
                                                    ParcelStatus.DEFAULT,
                                                    parcelNo.toLong()
                                                )
                                        }
                                    } else {
                                        val newStatusId =
                                            database.parcelDao().getNewStatusId(
                                                survey.parcelOperationValue.toLong(),
                                                survey.kachiAbadiId
                                            )

                                        database.parcelDao()
                                            .updateParcelSurveyStatusWrtParcelId(
                                                newStatusId,
                                                ParcelStatus.DEFAULT,
                                                survey.parcelOperationValue.toLong()
                                            )
                                    }

                                    val recordsList = database.notAtHomeSurveyFormDao()
                                        .getRecord(survey.parcelNo, survey.uniqueId)
                                    for (record in recordsList) {
                                        database.parcelDao().updateParcelSurveyStatus(
                                            record.newStatusId,
                                            ParcelStatus.DEFAULT,
                                            record.centroidGeom
                                        )
                                        database.surveyDao()
                                            .updateSurveyStatus(false, record.surveyId)
                                    }
                                    database.notAtHomeSurveyFormDao()
                                        .deleteSavedRecord(survey.parcelNo, survey.uniqueId)
                                }

                                else -> {
                                    val recordsList = database.notAtHomeSurveyFormDao()
                                        .getRecord(survey.parcelNo, survey.uniqueId)
                                    for (record in recordsList) {
                                        database.parcelDao().updateParcelSurveyStatus(
                                            record.newStatusId,
                                            ParcelStatus.DEFAULT,
                                            record.centroidGeom
                                        )
                                        database.surveyDao()
                                            .updateSurveyStatus(false, record.surveyId)
                                    }
                                    database.notAtHomeSurveyFormDao()
                                        .deleteSavedRecord(survey.parcelNo, survey.uniqueId)
                                }
                            }
                        }

                        closeCallOut()
                        ids.clear()
                        enableNewPoint = true
                        loadMap(false)
                    }
                }
            }

            builder.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }

            val dialog = builder.create()
            dialog.show()

            val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

            positiveButton.textSize = 16f
            positiveButton.typeface = android.graphics.Typeface.DEFAULT_BOLD
            negativeButton.textSize = 16f
            negativeButton.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        mapLocation.setOnClickListener {
            closeCallOut()
            val projectedPoint =
                GeometryEngine.project(
                    mapPoint,
                    SpatialReferences.getWebMercator()
                ) as Point

            val geoPoint =
                GeometryEngine.project(
                    projectedPoint,
                    SpatialReferences.getWgs84()
                ) as Point

            val latitude = geoPoint.y
            val longitude = geoPoint.x

            println("latitude77=$latitude")
            println("longitude77=$longitude")

            val uri = String.format(
                Locale.ENGLISH,
                "geo:%f,%f?q=%f,%f(Label)",
                latitude,
                longitude,
                latitude,
                longitude
            )
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            context.startActivity(intent)
            mCallOut.dismiss()
        }

        directions.setOnClickListener {
            closeCallOut()
            val projectedPoint =
                GeometryEngine.project(
                    mapPoint,
                    SpatialReferences.getWebMercator()
                ) as Point

            val geoPoint =
                GeometryEngine.project(
                    projectedPoint,
                    SpatialReferences.getWgs84()
                ) as Point

            val latitude = geoPoint.y
            val longitude = geoPoint.x

            println("latitude77=$latitude")
            println("longitude77=$longitude")

            val uri = "http://maps.google.com/maps?daddr=$latitude,$longitude"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            intent.setPackage("com.google.android.apps.maps")
            startActivity(intent)
        }

        btnCancel.setOnClickListener {
            closeCallOut()
        }

        setCalloutDisplayLocation(mapPoint)
        val callOutStyle = Callout.Style(context)
        callOutStyle.borderColor = R.color.primaryColor
        callOutStyle.borderWidth = 2
        mCallOut.style = callOutStyle
        mCallOut.location = mapPoint
        mCallOut.content = dialogView
        mCallOut.show()
    }

    private fun getHarvestedParcelsFromPreferences(): Set<String> {
        try {
            val harvestedParcels =
                sharedPreferences.getStringSet("harvested_parcels", emptySet()) ?: emptySet()
            Log.d(
                "Harvested",
                "Retrieved ${harvestedParcels.size} harvested parcels: $harvestedParcels"
            )
            return harvestedParcels
        } catch (e: Exception) {
            Log.e("Harvested", "Error getting harvested parcels: ${e.message}", e)
            return emptySet()
        }
    }

    private fun isParcelHarvested(parcelId: Long): Boolean {
        val harvestedParcels = getHarvestedParcelsFromPreferences()
        val isHarvested = harvestedParcels.contains(parcelId.toString())
        Log.d("Harvested", "Checking parcel ID $parcelId: isHarvested = $isHarvested")
        return isHarvested
    }

    private fun closeCallOut() {
        if (::mCallOut.isInitialized && mCallOut.isShowing) {
            mCallOut.dismiss()
        }
    }

    private fun setCalloutDisplayLocation(mapPoint: Point) {
        val envelope: Envelope = binding.parcelMapview.visibleArea.extent
        val centerPoint = envelope.center
        val x = mapPoint.x
        val factor = (envelope.yMax - centerPoint.y) / 2
        val y = mapPoint.y + factor
        val lastZoomPoint = Point(x, y, binding.parcelMapview.spatialReference)
        binding.parcelMapview.setViewpointCenterAsync(lastZoomPoint)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.parcelMapview?.dispose()
        _binding = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(requireContext())

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Intent(context, MenuActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(this)
                    requireActivity().finish()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_fragment_map, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Back button
                val intent = Intent(requireContext(), MenuActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                requireActivity().finish()
                true
            }

            R.id.action_refresh_map -> {
                // Refresh button (same as your iv_reset functionality)
                closeCallOut()
                viewLifecycleOwner.lifecycleScope.launch {
                    ids.clear()
                    enableNewPoint = true
                    loadMap(false)
                }
                Toast.makeText(requireContext(), "Refreshing map...", Toast.LENGTH_SHORT).show()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun mergePolygonsToSingleGeometry(polygons: List<Polygon>): Polygon? {
        return try {
            if (polygons.isEmpty()) return null
            if (polygons.size == 1) return polygons[0]

            var merged = polygons[0] as com.esri.arcgisruntime.geometry.Geometry
            for (i in 1 until polygons.size) {
                merged = GeometryEngine.union(merged, polygons[i])
            }
            merged as? Polygon
        } catch (e: Exception) {
            Log.e("MultiMerge", "Error merging polygons: ${e.message}")
            null
        }
    }

    private fun handleMultiMergeDone() {
        Log.d("MultiMerge", "=== handleMultiMergeDone() called ===")
        Log.d("MultiMerge", "Selected parcels count: ${selectedMultiMergeParcels.size}")

        if (selectedMultiMergeParcels.size < 2) {
            Toast.makeText(
                requireContext(),
                "Select at least 2 parcels to merge",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // ✅ Collect parcel numbers of all selected parcels
        val selectedParcelNos = selectedMultiMergeParcels.values.mapNotNull {
            it.attributes["parcel_no"]?.toString()
        }.distinct()

        Log.d("MultiMerge", "Selected parcel_nos: $selectedParcelNos")

        if (selectedParcelNos.isEmpty()) {
            Toast.makeText(requireContext(), "No valid parcel numbers found", Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (selectedParcelNos.size < 2) {
            Toast.makeText(
                requireContext(),
                "Need at least 2 different parcel numbers",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // ✅ Show dialog with radio buttons
        val parcelNosArray = selectedParcelNos.toTypedArray()
        val selectedIndex = intArrayOf(0)  // use array so inner lambda can modify

        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Choose Parcel Number to Keep")
        builder.setCancelable(false)

        builder.setSingleChoiceItems(parcelNosArray, 0) { _, which ->
            selectedIndex[0] = which
            Log.d("MultiMerge", "User selected index $which → parcel_no ${parcelNosArray[which]}")
        }

        builder.setPositiveButton("Merge") { dialog, _ ->
            val chosenParcelNo = parcelNosArray[selectedIndex[0]]
            val allParcelNosCsv = selectedParcelNos.joinToString(",")
            Log.d(
                "MultiMerge",
                "✅ User confirmed: chose parcel_no=$chosenParcelNo from [$allParcelNosCsv]"
            )
            dialog.dismiss()
            performMultiMerge(chosenParcelNo, allParcelNosCsv)
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
            Log.d("MultiMerge", "User cancelled merge")
        }

        val dialog = builder.create()
        dialog.show()

        // ✅ Style buttons for better visibility
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.apply {
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        Log.d("MultiMerge", "Dialog shown with ${parcelNosArray.size} options")
    }

    private fun performMultiMerge(chosenParcelNo: String, allParcelNosCsv: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Toast.makeText(
                    requireContext(),
                    "Merging ${selectedMultiMergeParcels.size} parcels into parcel $chosenParcelNo...",
                    Toast.LENGTH_SHORT
                ).show()

                val parcelIds = selectedMultiMergeParcels.keys.toList()
                val graphics = selectedMultiMergeParcels.values.toList()

                // Collect all polygons
                val polygons = graphics.mapNotNull { it.geometry as? Polygon }

                if (polygons.size != parcelIds.size) {
                    Toast.makeText(
                        requireContext(),
                        "Error reading parcel geometries",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Merge all polygons into one
                val mergedPolygon = mergePolygonsToSingleGeometry(polygons)
                if (mergedPolygon == null) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to merge geometries",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Convert merged polygon to WKT
                val mergedWKT = convertPolygonToWkt(mergedPolygon)
                val centroid = mergedPolygon.extent.center
                val centroidWKT = String.format("POINT(%.8f %.8f)", centroid.x, centroid.y)

                Log.d("MultiMerge", "Chosen parcel no: $chosenParcelNo")
                Log.d("MultiMerge", "All parcel nos: $allParcelNosCsv")
                Log.d("MultiMerge", "Merged WKT length: ${mergedWKT.length}")

                withContext(Dispatchers.IO) {
                    // ✅ Find the parcel whose parcel_no matches chosenParcelNo — this is the SURVIVOR
                    val survivorParcel = selectedMultiMergeParcels.values.firstOrNull {
                        it.attributes["parcel_no"]?.toString() == chosenParcelNo
                    }?.let { graphic ->
                        val survivorId = graphic.attributes["parcel_id"] as? Long
                        if (survivorId != null) database.activeParcelDao()
                            .getParcelById(survivorId) else null
                    }

                    if (survivorParcel == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                "Survivor parcel not found",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@withContext
                    }

                    // ✅ Update survivor parcel with merged geometry
                    val updatedSurvivor = survivorParcel.copy(
                        geomWKT = mergedWKT,
                        centroid = centroidWKT,
                        surveyStatusCode = 1,    // still unsurveyed locally — surveyor needs to collect data
                        isActivate = true,
                        multiMergeParcelNos = allParcelNosCsv
                    )
                    database.activeParcelDao().insertActiveParcels(listOf(updatedSurvivor))
                    Log.d(
                        "MultiMerge",
                        "✅ Updated survivor parcel ID=${survivorParcel.id}, ParcelNo=$chosenParcelNo with merged geometry"
                    )

                    // ✅ Deactivate all OTHER parcels (not the survivor)
                    parcelIds.forEach { parcelId ->
                        if (parcelId != survivorParcel.id) {
                            database.activeParcelDao().updateParcelActivationStatus(parcelId, false)
                            Log.d("MultiMerge", "Deactivated parcel ID: $parcelId")
                        }
                    }

                    // ✅ Store operation metadata on survivor for later upload
                    // We use parcelOperation + parcelOperationValue to remember this is a multi-merge
                    // These will be read when the surveyor uploads
                }

                withContext(Dispatchers.Main) {
                    isMultiMergeMode = false
                    selectedMultiMergeParcels.clear()
                    binding.mergeControlBar.visibility = View.GONE
                    binding.parcelMapview.onTouchListener =
                        DefaultMapViewOnTouchListener(requireContext(), binding.parcelMapview)

                    // ✅ Save MultiMerge metadata to SharedPreferences or viewModel
                    // so when user surveys this parcel, we know to send it as MultiMerge
//                    sharedPreferences.edit()
//                        .putString("multimerge_${chosenParcelNo}", allParcelNosCsv)
//                        .apply()

                    Toast.makeText(
                        requireContext(),
                        "✅ ${parcelIds.size} parcels merged → Parcel $chosenParcelNo",
                        Toast.LENGTH_LONG
                    ).show()

                    refreshMapDisplay()
                }

            } catch (e: Exception) {
                Log.e("MultiMerge", "Error performing multi-merge: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Merge failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
// SurveyActivity.kt
package com.gop.survey.corporatefarm.ui.activities

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.gop.survey.corporatefarm.R
import com.gop.survey.corporatefarm.common.Constants
import com.gop.survey.corporatefarm.data.local.AppDatabase
import com.gop.survey.corporatefarm.data.local.SurveyFormViewModel
import com.gop.survey.corporatefarm.data.local.SurveyImageAdapter
import com.gop.survey.corporatefarm.data.remote.ServerApi
import com.gop.survey.corporatefarm.data.repository.DropdownRepository
import com.gop.survey.corporatefarm.data.repository.LessorRepository
import com.gop.survey.corporatefarm.data.repository.Resource
import com.gop.survey.corporatefarm.databinding.ActivitySurveyNewBinding
import com.gop.survey.corporatefarm.domain.model.BlockEntity
import com.gop.survey.corporatefarm.domain.model.DivisionEntity
import com.gop.survey.corporatefarm.domain.model.FarmEntity
import com.gop.survey.corporatefarm.domain.model.LessorEntity
import com.gop.survey.corporatefarm.domain.model.NewSurveyNewEntity
import com.gop.survey.corporatefarm.domain.model.PlotEntity
import com.gop.survey.corporatefarm.domain.model.SectionEntity
import com.gop.survey.corporatefarm.domain.model.SurveyImage
import com.gop.survey.corporatefarm.domain.model.SurveyLessorEntity
import com.gop.survey.corporatefarm.domain.model.TempSurveyLogEntity
import com.gop.survey.corporatefarm.domain.model.ZoneEntity
import com.gop.survey.corporatefarm.presentation.util.ToastUtil
import com.gop.survey.corporatefarm.ui.dialogs.LessorSelectionDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject


@AndroidEntryPoint
class SurveyActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivitySurveyNewBinding
    private lateinit var context: Context
    private lateinit var imageAdapter: SurveyImageAdapter
    private var tempImageUri: Uri? = null
    private var tempImagePath: String? = null
    private var currentImageType: String = ""
    private val viewModel: SurveyFormViewModel by viewModels()
    @Inject lateinit var sharedPreferences: SharedPreferences
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var serverApi: ServerApi
    @Inject lateinit var dropdownRepository: DropdownRepository
    @Inject lateinit var lessorRepository: LessorRepository
    private var cropList = mutableListOf<String>()
    private var cropTypeList = mutableListOf<String>()
    private var varietyList = mutableListOf<String>()
    private var selectedCropType: String? = null
    private var selectedVariety: String? = null
    private var isSettingSpinnerProgrammatically = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var currentBearing: Float = 0f
    private var selectedSowingDate: String? = null
    // Cascade dropdowns
    private var allZones = mutableListOf<ZoneEntity>()
    private var allDivisions = mutableListOf<DivisionEntity>()
    private var allSections = mutableListOf<SectionEntity>()
    private var allBlocks = mutableListOf<BlockEntity>()
    private var allFarms = mutableListOf<FarmEntity>()
    private var allPlots = mutableListOf<PlotEntity>()
    private var selectedZone: ZoneEntity? = null
    private var selectedDivision: DivisionEntity? = null
    private var selectedSection: SectionEntity? = null
    private var selectedBlock: BlockEntity? = null
    private var selectedFarm: FarmEntity? = null
    private var selectedPlot: PlotEntity? = null
    private var selectedLessorType: String = "Single"
    private val allLessors = mutableListOf<LessorEntity>()
    private val selectedLessors = mutableListOf<LessorEntity>()
    private var selectedIrrigationQuantity: String? = null
    private var propertyTypeList = mutableListOf<String>()
    private var allIrrigationSources = mutableListOf<String>()
    private val selectedIrrigation = linkedMapOf<String, String>()
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("tempImagePath", tempImagePath)
        outState.putString("tempImageUri", tempImageUri?.toString())
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        tempImagePath = savedInstanceState.getString("tempImagePath")
        tempImageUri = savedInstanceState.getString("tempImageUri")?.let { Uri.parse(it) }
    }

    companion object {
        private const val SELECT_BLOCK = "-- Select Block --"
        private const val SELECT_PLOT = "-- Select Plot --"
        private const val SELECT_CROP_TYPE = "-- Select Crop Type --"
        private const val SELECT_VARIETY = "-- Select Variety --"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurveyNewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        context = this@SurveyActivity
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupSensors()
        setupSowingDatePicker()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.etYear.setText("2026")

        val parcelId = intent.getLongExtra("parcelId", 0L)
        val parcelNo = intent.getStringExtra("parcelNo") ?: ""
        val subParcelNo = intent.getStringExtra("subParcelNo") ?: ""
        val parcelArea = intent.getStringExtra("parcelArea") ?: ""
        val khewatInfo = intent.getStringExtra("khewatInfo") ?: ""
        val parcelOperation = intent.getStringExtra("parcelOperation") ?: ""
        val parcelOperationValue = intent.getStringExtra("parcelOperationValue") ?: ""

        val parcelInfoText = SpannableStringBuilder()
        val parcelLabel = SpannableString("P/N:")
        parcelLabel.setSpan(StyleSpan(Typeface.BOLD), 0, parcelLabel.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        parcelInfoText.append(parcelLabel)
        val areaAcresText = parcelArea.toDoubleOrNull()?.let {
            String.format(Locale.US, "%.4f Acres", it / 43560.0)
        } ?: parcelArea
        parcelInfoText.append("$parcelNo/$subParcelNo\t\t GC= $khewatInfo \t\tArea: $areaAcresText\t\tID: $parcelId\t\tOperation: $parcelOperation\t\tparcelOperationValue: $parcelOperationValue")
        binding.tvParcelInfo.text = parcelInfoText

        setupSpinners()
        setupLessorSection()
        setupImageSection()
        setupSubmit(parcelId, parcelNo, subParcelNo)
        loadSharedMouzaData()
        syncUnsyncedData()
        setupCascadeDropdowns()
        loadLessorsFromServer()
    }

    // ============================================
// ✅ LESSOR SECTION (UPDATED)
// ============================================
    private fun loadLessorsFromServer() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                lessorRepository.fetchAndCacheLessors(forceRefresh = true)
            }
            when (result) {
                is Resource.Success -> {
                    allLessors.clear()
                    allLessors.addAll(result.data)
                    Log.d("SurveyActivity", "Loaded ${allLessors.size} lessors")
                }
                is Resource.Error -> {
                    val cached = withContext(Dispatchers.IO) { lessorRepository.getCachedLessors() }
                    allLessors.clear()
                    allLessors.addAll(cached)
                    if (cached.isEmpty()) {
                        ToastUtil.showShort(context, "Could not load lessors: ${result.message}")
                    }
                }
                is Resource.Loading -> { }
            }
        }
    }

    private fun setupLessorSection() {
        // Lessor Type dropdown — informational only
        val lessorTypes = listOf("Single", "Multiple")
        binding.spinnerLessorType.adapter = makeAdapter(lessorTypes)
        binding.spinnerLessorType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedLessorType = lessorTypes[position]
                Log.d("SurveyActivity", "Lessor type changed to: $selectedLessorType")
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.btnSelectLessor.setOnClickListener {
            if (selectedLessors.isNotEmpty()) {
                showAlreadyAddedAlert()
                return@setOnClickListener
            }
            if (allLessors.isEmpty()) {
                lifecycleScope.launch {
                    val cached = withContext(Dispatchers.IO) { lessorRepository.getCachedLessors() }
                    if (cached.isEmpty()) {
                        ToastUtil.showShort(context, "No lessors available. Add one first.")
                    } else {
                        allLessors.clear()
                        allLessors.addAll(cached)
                        showLessorPicker()
                    }
                }
            } else {
                showLessorPicker()
            }
        }

        binding.btnAddNewLessor.setOnClickListener {
            if (selectedLessors.isNotEmpty()) {
                showAlreadyAddedAlert()
                return@setOnClickListener
            }
            showAddLessorDialog()
        }

        refreshSelectedLessorsUi()
    }

    private fun showAlreadyAddedAlert() {
        AlertDialog.Builder(this)
            .setTitle("Lessor Already Added")
            .setMessage("Only one lessor can be added. Please remove the current lessor first if you want to choose another.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLessorPicker() {
        val currentSelectedId = selectedLessors.firstOrNull()?.id
        LessorSelectionDialog(this, allLessors, currentSelectedId) { picked ->
            // Always replace — only 1 lessor allowed
            selectedLessors.clear()
            selectedLessors.add(picked)
            refreshSelectedLessorsUi()
        }.show()
    }

    private fun showAddLessorDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = EditText(this).apply {
            hint = "Lessor name *"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val codeInput = EditText(this).apply {
            hint = "Code (optional)"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        container.addView(nameInput)
        container.addView(codeInput)

        AlertDialog.Builder(this)
            .setTitle("Add New Lessor")
            .setView(container)
            .setPositiveButton("Add") { dialog, _ ->
                val name = nameInput.text.toString().trim()
                val code = codeInput.text.toString().trim()

                if (name.isBlank()) {
                    ToastUtil.showShort(this, "Name is required")
                    return@setPositiveButton
                }

                val adHocLessor = LessorEntity(
                    id = 0,
                    name = name,
                    code = code.ifBlank { null },
                    zoneId = null,
                    isDeleted = false
                )

                // Always replace — only 1 lessor allowed
                selectedLessors.clear()
                selectedLessors.add(adHocLessor)
                refreshSelectedLessorsUi()
                ToastUtil.showShort(this, "Lessor added: $name")
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshSelectedLessorsUi() {
        binding.layoutSelectedLessors.removeAllViews()

        if (selectedLessors.isEmpty()) {
            binding.layoutNoLessors.visibility = View.VISIBLE
            return
        }
        binding.layoutNoLessors.visibility = View.GONE

        val inflater = layoutInflater
        selectedLessors.forEach { lessor ->
            val card = inflater.inflate(
                R.layout.item_selected_lessor,
                binding.layoutSelectedLessors,
                false
            )

            val tvAvatarLetter = card.findViewById<TextView>(R.id.tvAvatarLetter)
            val tvLessorName = card.findViewById<TextView>(R.id.tvLessorName)
            val tvLessorCode = card.findViewById<TextView>(R.id.tvLessorCode)
            val tvBadge = card.findViewById<TextView>(R.id.tvBadge)
            val btnRemove = card.findViewById<ImageButton>(R.id.btnRemoveLessor)

            tvAvatarLetter.text = lessor.name.firstOrNull()?.uppercase() ?: "?"
            tvLessorName.text = lessor.name

            if (!lessor.code.isNullOrBlank()) {
                tvLessorCode.visibility = View.VISIBLE
                tvLessorCode.text = "Code: ${lessor.code}"
            } else {
                tvLessorCode.visibility = View.GONE
            }

            if (lessor.id == 0) {
                tvBadge.text = "NEW"
                tvBadge.setBackgroundResource(R.drawable.badge_new)
            } else {
                tvBadge.text = "LIST"
                tvBadge.setBackgroundResource(R.drawable.badge_master)
            }

            btnRemove.setOnClickListener {
                selectedLessors.remove(lessor)
                refreshSelectedLessorsUi()
            }

            binding.layoutSelectedLessors.addView(card)
        }
    }

    // ============================================
    // SOWING DATE PICKER
    // ============================================
    private fun setupSowingDatePicker() {
        binding.etSowingDate.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                this,
                { _, year, month, day ->
                    val formatted = String.format("%04d-%02d-%02d", year, month + 1, day)
                    binding.etSowingDate.setText(formatted)
                    selectedSowingDate = formatted
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    // ============================================
    // CASCADE DROPDOWNS
    // ============================================
    private fun setupCascadeDropdowns() {
        binding.spinnerBlock.adapter = makeAdapter(listOf(SELECT_BLOCK))
        binding.spinnerPlot.adapter = makeAdapter(listOf(SELECT_PLOT))
        loadZones()
    }

    private fun loadZones() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    dropdownRepository.fetchZonesFromServer()
                }
                val zones = when (result) {
                    is Resource.Success -> result.data
                    is Resource.Error -> {
                        ToastUtil.showShort(context, "Could not load zones: ${result.message}")
                        emptyList()
                    }
                    is Resource.Loading -> emptyList()
                }

                allZones.clear()
                allZones.addAll(zones)

                val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item,
                    zones.map { it.zoneName }.ifEmpty { listOf("No zones available") })
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerZone.adapter = adapter

                binding.spinnerZone.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        if (position < allZones.size) {
                            selectedZone = allZones[position]
                            selectedDivision = null; selectedSection = null
                            selectedFarm = null; selectedBlock = null; selectedPlot = null
                            loadDivisions(selectedZone!!.zoneId)
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
            } catch (e: Exception) {
                Log.e("CascadeDropdown", "Error loading zones: ${e.message}")
            }
        }
    }

    private fun loadDivisions(zoneId: Long) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    dropdownRepository.fetchDivisionsByZone(zoneId)
                }
                val divisions = when (result) {
                    is Resource.Success -> result.data
                    is Resource.Error -> emptyList()
                    is Resource.Loading -> emptyList()
                }
                allDivisions.clear(); allDivisions.addAll(divisions)

                val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item,
                    divisions.map { it.divisionName }.ifEmpty { listOf("No divisions available") })
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerDivision.adapter = adapter

                binding.spinnerDivision.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        if (position < allDivisions.size) {
                            selectedDivision = allDivisions[position]
                            selectedSection = null; selectedFarm = null
                            selectedBlock = null; selectedPlot = null
                            loadSections(selectedDivision!!.divisionId)
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
            } catch (e: Exception) {
                Log.e("CascadeDropdown", "Error loading divisions: ${e.message}")
            }
        }
    }

    private fun loadSections(divisionId: Long) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    dropdownRepository.fetchSectionsByDivision(divisionId)
                }
                val sections = when (result) {
                    is Resource.Success -> result.data
                    is Resource.Error -> emptyList()
                    is Resource.Loading -> emptyList()
                }
                allSections.clear(); allSections.addAll(sections)

                val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item,
                    sections.map { it.sectionName }.ifEmpty { listOf("No sections available") })
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerSection.adapter = adapter

                binding.spinnerSection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        if (position < allSections.size) {
                            selectedSection = allSections[position]
                            selectedFarm = null; selectedBlock = null; selectedPlot = null
                            loadFarms(selectedSection!!.sectionId)
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
            } catch (e: Exception) {
                Log.e("CascadeDropdown", "Error loading sections: ${e.message}")
            }
        }
    }

    private fun loadFarms(sectionId: Long) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    dropdownRepository.fetchFarmsBySection(sectionId)
                }
                val farms = when (result) {
                    is Resource.Success -> result.data
                    is Resource.Error -> emptyList()
                    is Resource.Loading -> emptyList()
                }
                allFarms.clear(); allFarms.addAll(farms)

                val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item,
                    farms.map { it.farmName }.ifEmpty { listOf("No farms available") })
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerFarm.adapter = adapter

                binding.spinnerFarm.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        if (position < allFarms.size) {
                            selectedFarm = allFarms[position]
                            selectedBlock = null; selectedPlot = null
                            loadBlocks(selectedFarm!!.farmId)
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
            } catch (e: Exception) {
                Log.e("CascadeDropdown", "Error loading farms: ${e.message}")
            }
        }
    }

    private fun loadBlocks(farmId: Long) {
        clearPlotSpinner()
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    dropdownRepository.fetchBlocksByFarm(farmId)
                }
                val blocks = when (result) {
                    is Resource.Success -> result.data
                    else -> emptyList()
                }
                allBlocks.clear(); allBlocks.addAll(blocks)

                // placeholder sab se pehle
                val items = mutableListOf(SELECT_BLOCK)
                items.addAll(blocks.map { it.blockName })

                binding.spinnerBlock.onItemSelectedListener = null
                binding.spinnerBlock.adapter = makeAdapter(items)
                binding.spinnerBlock.setSelection(0)   // ← kuch bhi selected nahi
                selectedBlock = null

                binding.spinnerBlock.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        selectedPlot = null
                        if (position == 0) {
                            selectedBlock = null
                            clearPlotSpinner()
                        } else {
                            selectedBlock = allBlocks[position - 1]
                            loadPlots(selectedBlock!!.blockId)
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
            } catch (e: Exception) {
                Log.e("CascadeDropdown", "Error loading blocks: ${e.message}")
            }
        }
    }

    private fun loadPlots(blockId: Long) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    dropdownRepository.fetchPlotsByBlock(blockId)
                }
                val plots = when (result) {
                    is Resource.Success -> result.data
                    else -> emptyList()
                }
                allPlots.clear(); allPlots.addAll(plots)

                val items = mutableListOf(SELECT_PLOT)
                items.addAll(plots.map { it.plotName })

                binding.spinnerPlot.onItemSelectedListener = null
                binding.spinnerPlot.adapter = makeAdapter(items)
                binding.spinnerPlot.setSelection(0)
                selectedPlot = null

                binding.spinnerPlot.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        selectedPlot = if (position == 0) null else allPlots[position - 1]
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
            } catch (e: Exception) {
                Log.e("CascadeDropdown", "Error loading plots: ${e.message}")
            }
        }
    }

    private fun clearPlotSpinner() {
        allPlots.clear()
        selectedPlot = null
        binding.spinnerPlot.onItemSelectedListener = null
        binding.spinnerPlot.adapter = makeAdapter(listOf(SELECT_PLOT))
        binding.spinnerPlot.setSelection(0)
    }

    fun getSelectedCascadeValues(): Map<String, String> = mapOf(
        "zone" to (selectedZone?.zoneName ?: ""),
        "division" to (selectedDivision?.divisionName ?: ""),
        "section" to (selectedSection?.sectionName ?: ""),
        "farm" to (selectedFarm?.farmName ?: ""),
        "block" to (selectedBlock?.blockName ?: ""),
        "plot" to (selectedPlot?.plotName ?: "")
    )

    // ============================================
    // SENSORS
    // ============================================
    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // ============================================
    // CAMERA & LOCATION
    // ============================================
    private fun requestCameraPermissionAndCapture() {
        val cameraGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        when {
            cameraGranted && locationGranted -> getCurrentLocationAndCaptureImage()
            cameraGranted && !locationGranted -> locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            !cameraGranted && locationGranted -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            else -> requestPermissions(arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED)
                getCurrentLocationAndCaptureImage()
            else ToastUtil.showShort(this, "Camera permission is required")
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) getCurrentLocationAndCaptureImage() else captureImage()
    }

    private fun getCurrentLocationAndCaptureImage() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            captureImage(); return
        }
        try {
            val token = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                .addOnSuccessListener { location -> currentLocation = location; captureImage() }
                .addOnFailureListener {
                    ToastUtil.showShort(this, "Could not get location")
                    captureImage()
                }
        } catch (e: Exception) { captureImage() }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val hasLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasLocation) getCurrentLocationAndCaptureImage() else captureImage()
        } else ToastUtil.showShort(this, "Camera permission is required")
    }

    // ============================================
    // SPINNERS
    // ============================================
    private fun setupSpinners() {
        val ownershipStatusList = listOf("Owned", "Leased")
//        val propertyTypeList = listOf("Farm Survey", "Builtup", "Solar", "Water Channel", "Farm Roads", "Tubewell", "Turbine", "Other")
        val imageTypeList = listOf("Property", "CNIC", "Other Document", "Discrepancy Pic")

        binding.spinnerOwnershipStatus.adapter = makeAdapter(ownershipStatusList)
//        binding.spinnerPropertyStatus.adapter = makeAdapter(propertyTypeList)
        binding.spinnerImageType.adapter = makeAdapter(imageTypeList)

        binding.spinnerPropertyStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selected = parent.getItemAtPosition(position).toString()
                toggleFarmSurveyFields(selected == "Crop Survey")
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        loadPropertyTypesFromLocalDb()
        loadCropsFromLocalDb()
        loadCropTypesFromLocalDb()
        loadVarietiesFromLocalDb()
        loadIrrigationSourcesFromLocalDb()

    }
    private fun loadPropertyTypesFromLocalDb() {
        lifecycleScope.launch {
            try {
                val types = withContext(Dispatchers.IO) {
                    dropdownRepository.getPropertyTypes(forceRefresh = false)
                }
                propertyTypeList.clear()
                propertyTypeList.addAll(types)

                binding.spinnerPropertyStatus.adapter = makeAdapter(propertyTypeList)

                // ⚠️ Listener adapter ke BAAD lagana zaroori hai (async load ki wajah se)
                binding.spinnerPropertyStatus.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>, view: View?, position: Int, id: Long
                        ) {
                            val selected = parent.getItemAtPosition(position).toString()
                            toggleFarmSurveyFields(selected == "Crop Survey")
                        }
                        override fun onNothingSelected(parent: AdapterView<*>) {}
                    }

                // Default selection: Farm Survey
                val pos = propertyTypeList.indexOf("Crop Survey")
                if (pos != -1) binding.spinnerPropertyStatus.setSelection(pos)

            } catch (e: Exception) {
                ToastUtil.showShort(context, "Error loading property types")
            }
        }
    }

    private fun makeAdapter(list: List<String>): ArrayAdapter<String> {
        val a = ArrayAdapter(context, android.R.layout.simple_spinner_item, list)
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return a
    }

    private fun toggleFarmSurveyFields(show: Boolean) {
        binding.layoutFarmSurveyFields.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) {
            selectedCropType = null
            selectedVariety = null
            selectedSowingDate = null
            selectedIrrigationQuantity = null
            binding.etSowingDate.setText("")
            isSettingSpinnerProgrammatically = true
            if (binding.etCropType.adapter != null) binding.etCropType.setSelection(0)
            if (binding.etVariety.adapter != null) binding.etVariety.setSelection(0)
            isSettingSpinnerProgrammatically = false
            selectedIrrigation.clear()
            refreshIrrigationRows()
        }
    }

    private fun loadCropsFromLocalDb() {
        lifecycleScope.launch {
            try {
                val crops = withContext(Dispatchers.IO) { dropdownRepository.getCrops(forceRefresh = false) }
                cropList.clear();
                cropList.addAll(crops)
                binding.etCrop.adapter = makeAdapter(cropList)
                val pos = cropList.indexOf("Sugarcane")
                binding.etCrop.setSelection(if (pos != -1) pos else 0)
            } catch (e: Exception) { ToastUtil.showShort(context, "Error loading crops") }
        }
    }

    private fun loadCropTypesFromLocalDb() {
        lifecycleScope.launch {
            try {
                val cropTypes = withContext(Dispatchers.IO) { dropdownRepository.getCropTypes(forceRefresh = false) }
                cropTypeList.clear()
                cropTypeList.add(SELECT_CROP_TYPE)      // ← index 0
                cropTypeList.addAll(cropTypes)
                binding.etCropType.adapter = makeAdapter(cropTypeList)
                isSettingSpinnerProgrammatically = true
                binding.etCropType.setSelection(0)
                isSettingSpinnerProgrammatically = false
                selectedCropType = null
                setupCropTypeListener()
            } catch (e: Exception) { ToastUtil.showShort(context, "Error loading crop types") }
        }
    }

    private fun loadVarietiesFromLocalDb() {
        lifecycleScope.launch {
            try {
                val varieties = withContext(Dispatchers.IO) { dropdownRepository.getVarieties(forceRefresh = false) }
                varietyList.clear()
                varietyList.add(SELECT_VARIETY)         // ← index 0
                varietyList.addAll(varieties)
                varietyList.add("Other")
                binding.etVariety.adapter = makeAdapter(varietyList)
                isSettingSpinnerProgrammatically = true
                binding.etVariety.setSelection(0)
                isSettingSpinnerProgrammatically = false
                selectedVariety = null
                setupVarietyListener()
            } catch (e: Exception) { ToastUtil.showShort(context, "Error loading varieties") }
        }
    }

    private fun loadIrrigationSourcesFromLocalDb() {
        lifecycleScope.launch {
            try {
                val sources = withContext(Dispatchers.IO) {
                    dropdownRepository.getIrrigationSources(forceRefresh = false)
                }
                allIrrigationSources.clear()
                allIrrigationSources.addAll(sources)
            } catch (e: Exception) {
                ToastUtil.showShort(context, "Error loading irrigation sources")
            }
        }

        binding.btnSelectIrrigation.setOnClickListener { showIrrigationPicker() }
        refreshIrrigationRows()
    }

    private fun showIrrigationPicker() {
        if (allIrrigationSources.isEmpty()) {
            ToastUtil.showShort(context, "No irrigation sources available")
            return
        }

        val items = allIrrigationSources.toTypedArray()
        val checked = BooleanArray(items.size) { selectedIrrigation.containsKey(items[it]) }

        AlertDialog.Builder(this)
            .setTitle("Irrigation Sources")
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Done") { _, _ ->
                // Purani quantities preserve karein
                val previous = LinkedHashMap(selectedIrrigation)
                selectedIrrigation.clear()
                items.forEachIndexed { i, name ->
                    if (checked[i]) selectedIrrigation[name] = previous[name].orEmpty()
                }
                refreshIrrigationRows()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshIrrigationRows() {
        binding.layoutIrrigationRows.removeAllViews()

        binding.btnSelectIrrigation.text = if (selectedIrrigation.isEmpty())
            "Select irrigation source(s)"
        else
            "${selectedIrrigation.size} source(s) selected"

        selectedIrrigation.keys.toList().forEach { source ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 0)
            }

            val label = TextView(this).apply {
                text = source
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.parcel_green))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val qty = EditText(this).apply {
                hint = "Qty *"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                textSize = 14f
                setText(selectedIrrigation[source])
                layoutParams = LinearLayout.LayoutParams(220, LinearLayout.LayoutParams.WRAP_CONTENT)
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        selectedIrrigation[source] = s?.toString()?.trim().orEmpty()
                    }
                })
            }

            row.addView(label)
            row.addView(qty)
            binding.layoutIrrigationRows.addView(row)
        }
    }

    private fun setupCropTypeListener() {
        binding.etCropType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (isSettingSpinnerProgrammatically) return
                if (position == 0) { selectedCropType = null; return }
                val selected = parent.getItemAtPosition(position).toString()
                if (selected == "Other") showCustomCropInputDialog() else selectedCropType = selected
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupVarietyListener() {
        binding.etVariety.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (isSettingSpinnerProgrammatically) return
                if (position == 0) { selectedVariety = null; return }
                val selected = parent.getItemAtPosition(position).toString()
                if (selected == "Other") showCustomVarietyInputDialog() else selectedVariety = selected
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun showCustomVarietyInputDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "Enter variety name"
        }
        AlertDialog.Builder(this)
            .setTitle("Enter Variety")
            .setMessage("Please enter the variety name.")
            .setView(input)
            .setPositiveButton("OK") { dialog, _ ->
                val custom = input.text.toString().trim()
                if (custom.isNotBlank()) {
                    selectedVariety = custom
                    ToastUtil.showShort(this, "Custom variety: $custom")
                    dialog.dismiss()
                } else showCustomVarietyInputDialog()
            }
            .setNegativeButton("Cancel") { _, _ -> resetVarietySpinner() }
            .setCancelable(false)
            .show()
    }

    private fun showCustomCropInputDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "Enter crop type name"
        }
        AlertDialog.Builder(this)
            .setTitle("Enter Crop Type")
            .setView(input)
            .setPositiveButton("OK") { dialog, _ ->
                val custom = input.text.toString().trim()
                if (custom.isNotBlank()) {
                    selectedCropType = custom
                    ToastUtil.showShort(this, "Custom crop type: $custom")
                    dialog.dismiss()
                } else showCustomCropInputDialog()
            }
            .setNegativeButton("Cancel") { _, _ ->
                selectedCropType = null
                isSettingSpinnerProgrammatically = true
                binding.etCropType.setSelection(0)
                isSettingSpinnerProgrammatically = false
            }
            .setCancelable(false)
            .show()
    }

    private fun syncUnsyncedData() {
        lifecycleScope.launch {
            try {
                val syncedCount = withContext(Dispatchers.IO) { dropdownRepository.syncUnsyncedVarieties() }
                if (syncedCount > 0) loadVarietiesFromLocalDb()
            } catch (e: Exception) { Log.e("SurveyActivity", "Sync error: ${e.message}") }
        }
    }

    private fun resetVarietySpinner() {
        selectedVariety = null
        isSettingSpinnerProgrammatically = true
        binding.etVariety.setSelection(0)
        isSettingSpinnerProgrammatically = false
    }

    // ============================================
    // IMAGE SECTION
    // ============================================
    private fun setupImageSection() {
        imageAdapter = SurveyImageAdapter { imageToRemove ->
            viewModel.removeImage(imageToRemove)
            imageAdapter.submitList(viewModel.surveyImages.value!!.toList())
            updateImageSectionUI()
        }

        binding.recyclerPictures.apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = imageAdapter
        }

        binding.btnAddImage.setOnClickListener {
            currentImageType = binding.spinnerImageType.selectedItem.toString()
            imagePickerLauncher.launch("image/*")
        }

        binding.btnTakePhoto.setOnClickListener {
            currentImageType = binding.spinnerImageType.selectedItem.toString()
            requestCameraPermissionAndCapture()
        }

        updateImageSectionUI()
    }

    private fun updateImageSectionUI() {
        val hasImages = viewModel.surveyImages.value?.isNotEmpty() == true
        binding.recyclerPictures.visibility = if (hasImages) View.VISIBLE else View.GONE
        binding.layoutEmptyState.visibility = if (hasImages) View.GONE else View.VISIBLE
        binding.layoutLoadingState.visibility = View.GONE
    }

    private fun showImageLoading() {
        binding.layoutEmptyState.visibility = View.GONE
        binding.recyclerPictures.visibility = View.GONE
        binding.layoutLoadingState.visibility = View.VISIBLE
    }

    private fun hideImageLoading() {
        binding.layoutLoadingState.visibility = View.GONE
        updateImageSectionUI()
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            showImageLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val savedPath = savePickedImageToInternalStorage(uri)
                    val image = SurveyImage(uri = savedPath, type = currentImageType)
                    withContext(Dispatchers.Main) {
                        viewModel.addImage(image)
                        imageAdapter.submitList(viewModel.surveyImages.value!!.toList())
                        hideImageLoading()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        hideImageLoading()
                        ToastUtil.showShort(context, "Error loading image: ${e.message}")
                    }
                }
            }
        }
    }

    private fun savePickedImageToInternalStorage(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Can't open image stream")
        val fileName = "img_${System.currentTimeMillis()}.jpg"
        val file = File(filesDir, fileName)
        inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        return file.absolutePath
    }

    private fun captureImage() {
        try {
            val timestamp = System.currentTimeMillis()
            val photoFile = File(filesDir, "survey_img_${timestamp}.jpg")
            tempImagePath = photoFile.absolutePath
            photoFile.parentFile?.mkdirs()
            tempImageUri = FileProvider.getUriForFile(this, "${packageName}.fileProvider", photoFile)
            cameraLauncher.launch(tempImageUri!!)
        } catch (e: Exception) {
            ToastUtil.showShort(context, "Error setting up camera: ${e.message}")
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (!success) {
            ToastUtil.showShort(context, "Photo capture was cancelled")
            return@registerForActivityResult
        }
        showImageLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var finalFile: File? = null
                if (!tempImagePath.isNullOrEmpty()) {
                    val f = File(tempImagePath!!)
                    if (f.exists() && f.length() > 0) finalFile = f
                }
                if (finalFile == null && tempImageUri != null) {
                    val input = contentResolver.openInputStream(tempImageUri!!)
                    if (input != null) {
                        val fallbackFile = File(filesDir, "fallback_${System.currentTimeMillis()}.jpg")
                        fallbackFile.outputStream().use { output -> input.copyTo(output) }
                        if (fallbackFile.exists() && fallbackFile.length() > 0) finalFile = fallbackFile
                    }
                }
                if (finalFile == null) {
                    withContext(Dispatchers.Main) {
                        hideImageLoading()
                        ToastUtil.showShort(context, "Failed to get photo")
                    }
                    return@launch
                }

                val compressedFile = compressImageFile(finalFile, 300) ?: finalFile
                val timestamp = System.currentTimeMillis()
                val image = SurveyImage(
                    uri = compressedFile.absolutePath,
                    type = currentImageType,
                    latitude = currentLocation?.latitude,
                    longitude = currentLocation?.longitude,
                    timestamp = timestamp,
                    locationAddress = null,
                    bearing = currentBearing
                )

                withContext(Dispatchers.Main) {
                    viewModel.addImage(image)
                    database.imageDao().insertImage(image)
                    imageAdapter.submitList(viewModel.surveyImages.value!!.toList())
                    hideImageLoading()

                    val dateTime = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.getDefault()).format(Date(timestamp))
                    val locationInfo = if (currentLocation != null) {
                        val lat = String.format("%.6f", currentLocation!!.latitude)
                        val lng = String.format("%.6f", currentLocation!!.longitude)
                        "\nCoordinates: $lat, $lng"
                    } else "\nLocation: Not available"
                    val directionInfo = if (currentBearing != 0f)
                        "\nDirection: ${getDirectionFromBearing(currentBearing)} (${currentBearing.toInt()}°)"
                    else ""

                    ToastUtil.showShort(context, "Photo added\n$dateTime$locationInfo$directionInfo")
                    currentLocation = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideImageLoading()
                    ToastUtil.showShort(context, "Error: ${e.message}")
                }
            }
        }
    }

    private fun compressImageFile(inputFile: File, targetKB: Int = 300): File? {
        try {
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath) ?: return null
            var quality = 100
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            while (stream.size() / 1024 > targetKB && quality > 10) {
                stream.reset()
                quality -= 5
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }
            val compressedFile = File(filesDir, "compressed_${inputFile.name}")
            compressedFile.writeBytes(stream.toByteArray())
            if (compressedFile.exists()) inputFile.delete()
            return compressedFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // ============================================
    // SUBMIT (UPDATED for lessors)
    // ============================================
    private fun setupSubmit(parcelId: Long, parcelNo: String, subParcelNo: String) {
        binding.btnSubmitSurvey.setOnClickListener {

            // ===== PREVENT DOUBLE SUBMISSION =====
            // Re-enable only if a validation fails; on success finish() closes the screen.
            binding.btnSubmitSurvey.isEnabled = false

            // ===== VALIDATION: Property type must be loaded =====
            val propertyType = binding.spinnerPropertyStatus.selectedItem?.toString().orEmpty()
            if (propertyType.isBlank()) {
                binding.btnSubmitSurvey.isEnabled = true
                ToastUtil.showShort(this, "Property types are still loading. Please wait a moment.")
                return@setOnClickListener
            }
            val isFarmSurvey = propertyType == "Crop Survey"

            // ===== VALIDATION: Sowing date (crop survey only) =====
            if (isFarmSurvey && selectedSowingDate.isNullOrBlank()) {
                binding.btnSubmitSurvey.isEnabled = true
                AlertDialog.Builder(this)
                    .setTitle("Sowing Date Required")
                    .setMessage("Please select a sowing date before submitting.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

//            if (isFarmSurvey && selectedCropType.isNullOrBlank()) {
//                binding.btnSubmitSurvey.isEnabled = true
//                ToastUtil.showShort(this, "Please select Crop Type")
//                return@setOnClickListener
//            }
//            if (isFarmSurvey && selectedVariety.isNullOrBlank()) {
//                binding.btnSubmitSurvey.isEnabled = true
//                ToastUtil.showShort(this, "Please select Variety")
//                return@setOnClickListener
//            }

            // ===== VALIDATION: Exactly one lessor =====
            if (selectedLessors.isEmpty()) {
                binding.btnSubmitSurvey.isEnabled = true
                AlertDialog.Builder(this)
                    .setTitle("Lessor Required")
                    .setMessage("Please select or add a lessor before submitting.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            if (selectedLessors.size > 1) {
                binding.btnSubmitSurvey.isEnabled = true
                AlertDialog.Builder(this)
                    .setTitle("Invalid Selection")
                    .setMessage("Only one lessor is allowed.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            val ownershipStatus = binding.spinnerOwnershipStatus.selectedItem.toString()

            // ===== VALIDATION: Irrigation (crop survey only) =====
            if (isFarmSurvey) {
                if (selectedIrrigation.isEmpty()) {
                    binding.btnSubmitSurvey.isEnabled = true
                    AlertDialog.Builder(this)
                        .setTitle("Irrigation Source Required")
                        .setMessage("Please select at least one irrigation source.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setOnClickListener
                }

                val missing = selectedIrrigation.filterValues { it.isBlank() }.keys
                if (missing.isNotEmpty()) {
                    binding.btnSubmitSurvey.isEnabled = true
                    AlertDialog.Builder(this)
                        .setTitle("Quantity Required")
                        .setMessage("Please enter a quantity for: ${missing.joinToString(", ")}")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setOnClickListener
                }
            }

            // ===== VALIDATION: At least one image =====
//            if (viewModel.surveyImages.value.isNullOrEmpty()) {
//                binding.btnSubmitSurvey.isEnabled = true
//                AlertDialog.Builder(this)
//                    .setTitle("Image Required")
//                    .setMessage("Please add at least one image before submitting the survey.")
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener
//            }

            // ===== ALL VALIDATIONS PASSED =====
            // Every parcel in this app is drawn by the surveyor, so the operation is
            // always "New" and the server creates a fresh Corperate_Parcel row.
            val tehsil = sharedPreferences.getString(
                Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
                Constants.SHARED_PREF_DEFAULT_STRING
            ).orEmpty()

            val cascadeValues = getSelectedCascadeValues()

            val survey = NewSurveyNewEntity(
                parcelId = parcelId,
                parcelNo = parcelNo,
                subParcelNo = "",
                propertyType = propertyType,
                ownershipStatus = ownershipStatus,
                variety = if (isFarmSurvey) selectedVariety.orEmpty() else "",
                crop = if (isFarmSurvey) binding.etCrop.selectedItem.toString() else "",
                cropType = if (isFarmSurvey) selectedCropType.orEmpty() else "",
                year = binding.etYear.text.toString(),
                irrigationSource = if (isFarmSurvey)
                    selectedIrrigation.keys.joinToString(", ") else null,
                irrigationSourceQuantity = if (isFarmSurvey)
                    selectedIrrigation.entries.joinToString(", ") { "${it.key}:${it.value}" } else null,
                parcelOperation = "New",
                parcelOperationValue = "Drawn",
                mauzaId = 0L,
                areaName = tehsil,
                sowingDate = if (isFarmSurvey) selectedSowingDate else null,
                zone = cascadeValues["zone"],
                division = cascadeValues["division"],
                section = cascadeValues["section"],
                farm = cascadeValues["farm"],
                block = cascadeValues["block"],
                plot = cascadeValues["plot"]
            )

            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val surveyId = database.newSurveyNewDao().insertSurvey(survey)
                        Log.d("SurveyActivity", "=== SAVING SURVEY $surveyId ===")

                        // Save the selected lessor
                        selectedLessors.forEach { lessor ->
                            database.surveyLessorDao().insert(
                                SurveyLessorEntity(
                                    surveyId = surveyId,
                                    parcelId = parcelId,
                                    parcelNo = parcelNo,
                                    subParcelNo = "",
                                    lessorId = lessor.id,
                                    lessorName = lessor.name,
                                    lessorCode = lessor.code ?: "",
                                    lessorType = selectedLessorType
                                )
                            )
                            Log.d("SurveyActivity", "Saved lessor: ${lessor.name} (id=${lessor.id})")
                        }

                        // Save images
                        viewModel.surveyImages.value?.forEach {
                            it.surveyId = surveyId
                            database.imageDao().insertImage(it)
                        }

                        // Mark the drawn parcel as surveyed
                        database.activeParcelDao().updateParcelSurveyStatus(2, surveyId, parcelId)

                        database.tempSurveyLogDao().insertLog(
                            TempSurveyLogEntity(
                                parcelId = parcelId,
                                parcelNo = parcelNo,
                                subParcelNo = ""
                            )
                        )
                    }

                    ToastUtil.showShort(context, "Survey saved on this device.")
                    finish()
                } catch (e: Exception) {
                    Log.e("SurveyActivity", "Error saving survey: ${e.message}", e)
                    binding.btnSubmitSurvey.isEnabled = true
                    ToastUtil.showShort(context, "The survey could not be saved: ${e.message}")
                }
            }
        }
    }

    private fun loadSharedMouzaData() {
        val mauzaName = sharedPreferences.getLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0)
        val areaName = sharedPreferences.getString(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, "")
        ToastUtil.showShort(context, "MauzaID: $mauzaName ($areaName)")
    }

    // ============================================
    // SENSOR CALLBACKS
    // ============================================
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> System.arraycopy(it.values, 0, accelerometerReading, 0, accelerometerReading.size)
                Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(it.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }
            updateBearing()
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

    private fun updateBearing() {
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            var degrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            if (degrees < 0) degrees += 360f
            currentBearing = degrees
        }
    }

    private fun getDirectionFromBearing(bearing: Float): String = when {
        bearing >= 337.5 || bearing < 22.5 -> "North"
        bearing >= 22.5 && bearing < 67.5 -> "North-East"
        bearing >= 67.5 && bearing < 112.5 -> "East"
        bearing >= 112.5 && bearing < 157.5 -> "South-East"
        bearing >= 157.5 && bearing < 202.5 -> "South"
        bearing >= 202.5 && bearing < 247.5 -> "South-West"
        bearing >= 247.5 && bearing < 292.5 -> "West"
        bearing >= 292.5 && bearing < 337.5 -> "North-West"
        else -> "Unknown"
    }
}
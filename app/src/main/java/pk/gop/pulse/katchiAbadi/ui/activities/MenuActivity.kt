package pk.gop.pulse.katchiAbadi.ui.activities

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
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.esri.arcgisruntime.geometry.Envelope
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.SpatialReferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.MyApplication
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.DownloadFileTask
import pk.gop.pulse.katchiAbadi.common.DownloadType
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.ResourceSealed
import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.common.TileManager
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.databinding.ActivityMenuBinding
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.LogoutUseCase
import pk.gop.pulse.katchiAbadi.presentation.base.BaseActivity
import pk.gop.pulse.katchiAbadi.presentation.menu.MenuViewModel
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
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


//    @Inject
//    lateinit var retrofit: Retrofit

    private var job: Job? = null
    private lateinit var progressAlertDialog: AlertDialog
    private lateinit var tileManager: TileManager
    private val wgs84 by lazy {
        SpatialReferences.getWgs84()
    }

    private var downloadComplete: Boolean? = null

    private var downloadFileTask: DownloadFileTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        sharedPreferences = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        applySavedTheme()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // Initialize SharedPreferences for theme persistence
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        context = this@MenuActivity
        setupActionBar()
        // Set ActionBar title to uppercase
        supportActionBar?.title = supportActionBar?.title?.toString()?.uppercase()


        val tileFolder = File(filesDir, "MapTiles/corporate_parcels")
        val exists = tileFolder.exists()

        if (exists) {
            val summary = StringBuilder("Tile structure:\n")
            summary.append("Root: ${tileFolder.absolutePath}\n")

            // List everything at root level
            val rootContents = tileFolder.listFiles()
            summary.append("Root contents (${rootContents?.size ?: 0} items):\n")
            rootContents?.take(5)?.forEach {
                summary.append("  ${if (it.isDirectory) "[DIR]" else "[FILE]"} ${it.name}\n")
            }

            // Get 3 sample file paths from anywhere in the tree
            val sampleFiles = tileFolder.walkTopDown().filter { it.isFile }.take(3).toList()
            summary.append("\nSample file paths (relative):\n")
            sampleFiles.forEach { file ->
                val relative = file.absolutePath.substringAfter(tileFolder.absolutePath)
                summary.append("  $relative\n")
            }

            Log.e("TILE_CHECK", summary.toString())
        }

        lifecycleScope.launch {

            val count = withContext(Dispatchers.IO) {
                database.parcelDao().checkDataSaved()
            }

            if (count > 0) {

                val updateDatabase = sharedPreferences.getInt(
                    Constants.SHARED_PREF_UPDATE_DATABASE,
                    Constants.SHARED_PREF_DEFAULT_INT
                )

                if (updateDatabase == 0) {

                    // Create and show the ProgressDialog
                    progressDialogTwo = ProgressDialog(this@MenuActivity).apply {
                        setMessage("Updating database, please wait...")
                        setCancelable(false)
                        show()
                    }

                    try {
                        // Perform the database updates on the IO dispatcher
                        withContext(Dispatchers.IO) {
                            database.parcelDao().updateAllTablesInTransaction(
                                database.surveyFormDao(),
                                database.tempSurveyFormDao(),
                                database.notAtHomeSurveyFormDao()
                            )
                        }

                        sharedPreferences.edit()
                            .putInt(Constants.SHARED_PREF_UPDATE_DATABASE, 1)
                            .apply()
                        sharedPreferences.edit().putInt(
                            Constants.SHARED_PREF_LOGIN_STATUS,
                            Constants.LOGIN_STATUS_INACTIVE
                        ).putLong(
                            Constants.SHARED_PREF_USER_ID,
                            Constants.SHARED_PREF_DEFAULT_INT.toLong()
                        ).putString(
                            Constants.SHARED_PREF_USER_CNIC,
                            Constants.SHARED_PREF_DEFAULT_STRING
                        ).putString(
                            Constants.SHARED_PREF_USER_NAME,
                            Constants.SHARED_PREF_DEFAULT_STRING
                        ).apply()

                        // Transition to the AuthActivity
                        withContext(Dispatchers.Main) {
                            Intent(this@MenuActivity, AuthActivity::class.java).apply {
                                startActivity(this)
                                finish()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace() // Handle any errors here
                        // Dismiss the ProgressDialog in the finally block
                        if (progressDialog.isShowing) {
                            progressDialog.dismiss()
                        }
                    }
                } else {
                    sharedPreferences.edit()
                        .putInt(Constants.SHARED_PREF_UPDATE_DATABASE, 1)
                        .apply()
                }
            } else {
                sharedPreferences.edit()
                    .putInt(Constants.SHARED_PREF_UPDATE_DATABASE, 1)
                    .apply()
            }
        }

        binding.apply {

//            tvFooter.text =
//                tvFooter.text.toString().replace("Version", "Version ${Constants.VERSION_NAME}")

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

                // Step 1: Fetch tehsils and show selection dialog
                fetchAndShowTehsilDialog()
            }

            cvStartSurvey.setOnClickListener {
                if (!Utility.checkTimeZone(this@MenuActivity)) return@setOnClickListener
                Intent(this@MenuActivity, SurveyFormActivity::class.java).apply {
                    startActivity(this)
                }
//                if (Utility.checkInternetConnection(this@MenuActivity)) {
//
//                } else {
//                    Utility.dialog(
//                        context,
//                        "Please make sure you are connected to the internet and try again.",
//                        "No Internet!"
//                    )
//                }
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
//                    database.surveyFormDao().u1pdateSurveyStatusUnSent() // TODO delete this
                    val totalPendingRecords = database.newSurveyNewDao().totalPendingCount()
                    if (totalPendingRecords > 0) {
                        Intent(this@MenuActivity, NewSavedRecordsActivity::class.java).apply {
                            startActivity(this)
                        }
                    } else {
                        ToastUtil.showShort(
                            this@MenuActivity,
                            "No saved record available yet."
                        )
                    }
                }
            }

        }

        lifecycleScope.launch {
            viewModel.sync.collect {
                when (it) {
                    is Resource.Loading<*> -> {
                        Utility.showProgressAlertDialog(
                            context, "Please wait! downloading data..."
                        )
                    }

                    is Resource.Success<*> -> {
                        Utility.dismissProgressAlertDialog()
                        downloadMapNew()
                    }

                    is Resource.Error<*> -> {
                        Utility.dismissProgressAlertDialog()

                        it.message?.let { msg ->
                            if (msg.contains("401")) {
                                sharedPreferences.edit().putInt(
                                    Constants.SHARED_PREF_LOGIN_STATUS,
                                    Constants.LOGIN_STATUS_INACTIVE
                                ).putString(
                                    Constants.SHARED_PREF_USER_NAME,
                                    Constants.SHARED_PREF_DEFAULT_STRING
                                ).apply()

                                Intent(this@MenuActivity, AuthActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    startActivity(this)
                                    finish()
                                }

                                // Session has expired

                                ToastUtil.showShort(
                                    this@MenuActivity,
                                    "Session expired"
                                )
                            } else {
                                ToastUtil.showShort(
                                    context,
                                    msg
                                )
                            }
                        }

                    }

                    else -> Unit

                }
            }
        }



        lifecycleScope.launch {
            viewModel.mauzaNew.collect { it ->
                when (it) {
                    is ResourceSealed.Loading -> {
                        Utility.showProgressAlertDialog(
                            context, "Please wait! Getting assigned mauzas..."
                        )
                    }

                    is ResourceSealed.Success -> {
                        Utility.dismissProgressAlertDialog()

                        val mauzaList = it.data ?: emptyList()
                        val settings = it.info

                        if (mauzaList.isEmpty()) {
                            ToastUtil.showShort(
                                context,
                                "No Mauzas assigned to this user."
                            )
                            return@collect
                        }

                        // Sort Mauzas alphabetically
                        val sortedMauzas = mauzaList.sortedBy { mauza -> mauza.mauzaName }

                        val view = LayoutInflater.from(this@MenuActivity)
                            .inflate(R.layout.dialog_mouza_list, null)

                        val spinner = view.findViewById<Spinner>(R.id.spn_kachi_abadis)
                        val adapter = ArrayAdapter(
                            this@MenuActivity,
                            R.layout.spinner_item_drop_down,
                            sortedMauzas.map { it.mauzaName } // display names
                        )
                        spinner.adapter = adapter

                        val builder = AlertDialog.Builder(this@MenuActivity)
                            .setView(view)
                            .setCancelable(false)
                            .setTitle("Select a Mauza")
                            .setPositiveButton("Next") { _, _ ->
                                val selectedIndex = spinner.selectedItemPosition
                                val selectedMauza = sortedMauzas[selectedIndex]

                                // Save Mauza Info
                                sharedPreferences.edit()
                                    .putLong(
                                        Constants.SHARED_PREF_USER_ASSIGNED_MOUZA,
                                        selectedMauza.mauzaId
                                    )
                                    .putInt(
                                        Constants.SHARED_PREF_USER_ASSIGNED_MOUZA_FeetPerMarla,
                                        selectedMauza.unit
                                    )
                                    .putString(
                                        Constants.SHARED_PREF_USER_ASSIGNED_MOUZA_NAME,
                                        selectedMauza.mauzaName
                                    )
                                    .putInt(
                                        Constants.SHARED_PREF_METER_DISTANCE,
                                        settings?.meterDistance?.toIntOrNull()
                                            ?: Constants.SHARED_PREF_DEFAULT_DISTANCE
                                    )
                                    .putInt(
                                        Constants.SHARED_PREF_METER_ACCURACY,
                                        settings?.meterAccuracy?.toIntOrNull()
                                            ?: Constants.SHARED_PREF_DEFAULT_ACCURACY
                                    )
                                    .putInt(
                                        Constants.SHARED_PREF_ALLOWED_DOWNLOADABLE_AREAS,
                                        settings?.allowedDownloadableAreas?.toIntOrNull()
                                            ?: Constants.SHARED_PREF_DEFAULT_DOWNLOADABLE_AREAS
                                    )
                                    .putInt(
                                        Constants.SHARED_PREF_MAP_MIN_SCALE,
                                        settings?.minScaleTiles?.toIntOrNull()
                                            ?: Constants.SHARED_PREF_DEFAULT_MIN_SCALE
                                    )
                                    .putInt(
                                        Constants.SHARED_PREF_MAP_MAX_SCALE,
                                        settings?.maxScaleTiles?.toIntOrNull()
                                            ?: Constants.SHARED_PREF_DEFAULT_MAX_SCALE
                                    )
                                    .putInt(Constants.SHARED_PREF_ALLOW_DOWNLOAD_SAVED_DATA, 0)
                                    .apply()

                                // 👇 Now call API to get Area List using selected Mauza ID
                                val token =
                                    sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "")
                                        ?: ""
//                                val retrofit = Retrofit.Builder()
//                                    .baseUrl(Constants.BASE_URL)
//                                    .addConverterFactory(GsonConverterFactory.create())
//                                    .build()

//                                val api = retrofit.create(ServerApi::class.java)
//


                                lifecycleScope.launch {
                                    try {
                                        Utility.showProgressAlertDialog(
                                            this@MenuActivity,
                                            "Fetching areas..."
                                        )
                                        Log.d(
                                            "API_CALL",
                                            "Calling getAreasByMauzaId with mauzaId: ${selectedMauza.mauzaId}"
                                        )
                                        Log.d(
                                            "API_URL",
                                            "Fetching areas → mauzaId: ${selectedMauza.mauzaId}, token: Bearer $token"
                                        )

                                        val areaResponse = serverApi.getAreasByMauzaId(
                                            selectedMauza.mauzaId,
                                            "Bearer $token"
                                        )

                                        Log.d("API_RESPONSE", "Raw response: $areaResponse")
                                        Log.d(
                                            "API_RESPONSE",
                                            "Response areas field: ${areaResponse.areas}"
                                        )
                                        Log.d(
                                            "API_RESPONSE",
                                            "Areas type: ${areaResponse.areas::class.java}"
                                        )


                                        Utility.dismissProgressAlertDialog()

                                        val areaList = areaResponse.areas

                                        val validAreas = areaList.filter { area ->
                                            area.isNotBlank() && !area.matches(Regex("^\\d+$")) // Remove empty and numeric-only strings
                                        }

                                        Log.d("AREA_FILTER", "Original areas: $areaList")
                                        Log.d("AREA_FILTER", "Filtered valid areas: $validAreas")


                                        if (areaList.isEmpty()) {
                                            ToastUtil.showShort(
                                                this@MenuActivity,
                                                "No areas found for this Mauza."
                                            )
                                            return@launch
                                        }

                                        // 👇 Show area selection dialog
//                                        showAreaSelectionDialog()

                                    } catch (e: Exception) {
                                        Utility.dismissProgressAlertDialog()

                                        ToastUtil.showShort(
                                            this@MenuActivity,
                                            "Error fetching areas: ${e.message}"
                                        )
                                    }
                                }
                            }
                            .setNegativeButton("Cancel", null)

                        val dialog = builder.create()
                        dialog.show()

                        // Set font styles
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                            textSize = 16f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        }
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                            textSize = 16f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        }
                    }

                    is ResourceSealed.Error -> {
                        Utility.dismissProgressAlertDialog()
                        ToastUtil.showShort(
                            context,
                            it.message ?: "Something went wrong"
                        )
                    }

                    else -> Unit
                }
            }
        }

    }


    // ============================================
// STEP 1: Fetch Tehsils and Show Selection Dialog
// ============================================
    private fun fetchAndShowTehsilDialog() {
        lifecycleScope.launch {
            try {
                Utility.showProgressAlertDialog(this@MenuActivity, "Loading tehsils...")

                val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
                val response = serverApi.getTehsilNames("Bearer $token", "")

                Utility.dismissProgressAlertDialog()

                if (!response.isSuccessful) {
                    ToastUtil.showShort(this@MenuActivity, "Failed to load tehsils: ${response.code()}")
                    return@launch
                }

                val tehsils = response.body() ?: emptyList()

                if (tehsils.isEmpty()) {
                    ToastUtil.showShort(this@MenuActivity, "No tehsils available.")
                    return@launch
                }

                showTehsilSelectionDialog(tehsils)

            } catch (e: Exception) {
                Utility.dismissProgressAlertDialog()
                Log.e("SYNC", "Error fetching tehsils: ${e.message}", e)
                ToastUtil.showShort(this@MenuActivity, "Error: ${e.message}")
            }
        }
    }

    private fun showTehsilSelectionDialog(tehsils: List<String>) {
        val view = LayoutInflater.from(this@MenuActivity)
            .inflate(R.layout.dialog_mouza_list, null)

        val spinner = view.findViewById<Spinner>(R.id.spn_kachi_abadis)
        val adapter = ArrayAdapter(
            this@MenuActivity,
            R.layout.spinner_item_drop_down,
            tehsils
        )
        spinner.adapter = adapter

        val dialog = AlertDialog.Builder(this@MenuActivity)
            .setTitle("Select a Tehsil")
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("Next") { _, _ ->
                val selectedTehsil = tehsils[spinner.selectedItemPosition]
                Log.d("SYNC", "Selected tehsil: $selectedTehsil")

                // Save selected tehsil
                sharedPreferences.edit()
                    .putString("selected_tehsil", selectedTehsil)
                    .apply()

                // Step 2: Fetch AOIs for this tehsil
                fetchAndShowAOIDialog(selectedTehsil)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
    }

    // ============================================
// STEP 2: Fetch AOIs by Tehsil and Show Selection
// ============================================
    private fun fetchAndShowAOIDialog(tehsil: String) {
        lifecycleScope.launch {
            try {
                Utility.showProgressAlertDialog(this@MenuActivity, "Loading AOIs for $tehsil...")

                val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
                val response = serverApi.getAOINames("Bearer $token", tehsil)

                Utility.dismissProgressAlertDialog()

                if (!response.isSuccessful) {
                    ToastUtil.showShort(this@MenuActivity, "Failed to load AOIs: ${response.code()}")
                    return@launch
                }

                val aois = response.body() ?: emptyList()

                if (aois.isEmpty()) {
                    ToastUtil.showShort(this@MenuActivity, "No AOIs found for $tehsil.")
                    return@launch
                }

                showAOISelectionDialog(tehsil, aois)

            } catch (e: Exception) {
                Utility.dismissProgressAlertDialog()
                Log.e("SYNC", "Error fetching AOIs: ${e.message}", e)
                ToastUtil.showShort(this@MenuActivity, "Error: ${e.message}")
            }
        }
    }

    private fun showAOISelectionDialog(tehsil: String, aois: List<String>) {
        val view = LayoutInflater.from(this@MenuActivity)
            .inflate(R.layout.dialog_mouza_list, null)

        val spinner = view.findViewById<Spinner>(R.id.spn_kachi_abadis)
        val adapter = ArrayAdapter(
            this@MenuActivity,
            R.layout.spinner_item_drop_down,
            aois
        )
        spinner.adapter = adapter

        val dialog = AlertDialog.Builder(this@MenuActivity)
            .setTitle("Select AOI in $tehsil")
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("Download Parcels") { _, _ ->
                val selectedAOI = aois[spinner.selectedItemPosition]
                Log.d("SYNC", "Selected AOI: $selectedAOI")

                // Save selected AOI
                sharedPreferences.edit()
                    .putString("selected_aoi", selectedAOI)
                    .apply()

                // Step 3: Download parcels
                downloadParcelsForAOI(selectedAOI, tehsil)
            }
            .setNegativeButton("Back") { _, _ ->
                // Go back to tehsil selection
                fetchAndShowTehsilDialog()
            }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
    }

    // ============================================
// STEP 3: Download Parcels for Selected AOI
// ============================================
    private fun downloadParcelsForAOI(aoi: String, tehsil: String) {
        lifecycleScope.launch {
            try {
                Utility.showProgressAlertDialog(
                    this@MenuActivity,
                    "Downloading parcels for $aoi..."
                )

                val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""

                val result = fetchAndStoreParcelsByAOI(token, aoi, tehsil)

                Utility.dismissProgressAlertDialog()

                when (result) {
                    is Resource.Success -> {
                        ToastUtil.showShort(
                            this@MenuActivity,
                            "Parcels downloaded successfully!"
                        )
                        // Continue to download map tiles
                        downloadMapTilesNew()
                    }
                    is Resource.Error -> {
                        ToastUtil.showShort(
                            this@MenuActivity,
                            result.message ?: "Download failed"
                        )
                    }
                    else -> Unit
                }

            } catch (e: Exception) {
                Utility.dismissProgressAlertDialog()
                Log.e("SYNC", "Error downloading parcels: ${e.message}", e)
                ToastUtil.showShort(this@MenuActivity, "Error: ${e.message}")
            }
        }
    }

    // ============================================
// STEP 4: Fetch & Parse Parcels (Pipe-Delimited)
// ============================================
    private suspend fun fetchAndStoreParcelsByAOI(
        token: String,
        aoi: String,
        tehsil: String
    ): SimpleResource {
        return try {
            Log.d("FETCH_PARCELS", "=== Fetching parcels for AOI: $aoi ===")

            val response = serverApi.getParcelsByAOI("Bearer $token", aoi)

            if (!response.isSuccessful) {
                Log.e("FETCH_PARCELS", "API error: ${response.code()}")
                return Resource.Error("Server error: ${response.code()}")
            }

            // Response body is text/plain, pipe-delimited
            val responseText = response.body()?.string() ?: ""
            Log.d("FETCH_PARCELS", "Response size: ${responseText.length} chars")

            if (responseText.isBlank()) {
                return Resource.Error("No parcels found for this AOI")
            }

            // Parse each line
            val lines = responseText.split("\n").filter { it.isNotBlank() }
            Log.d("FETCH_PARCELS", "Parsing ${lines.size} parcel lines")

            val entities = mutableListOf<ActiveParcelEntity>()
            var parsedCount = 0
            var skippedCount = 0

            for (line in lines) {
                val entity = parseParcelLine(line)
                if (entity != null) {
                    entities.add(entity)
                    parsedCount++
                } else {
                    skippedCount++
                }
            }

            Log.d("FETCH_PARCELS", "Parsed: $parsedCount, Skipped: $skippedCount")

            if (entities.isEmpty()) {
                return Resource.Error("No valid parcels could be parsed")
            }

            // Save to DB in transaction
            database.withTransaction {
                database.activeParcelDao().deleteAllParcels()
                Log.d("FETCH_PARCELS", "Cleared old parcels")

                database.activeParcelDao().insertActiveParcels(entities)
                Log.d("FETCH_PARCELS", "✅ Inserted ${entities.size} parcels")
            }

            // Save selection info
            sharedPreferences.edit()
                .putString(Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name, aoi)
                .putLong(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID, 0L)
                .putString(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME, tehsil)
                .putString(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, aoi)
                .putLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0L)
                .putString(Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, tehsil)
                .apply()

            Resource.Success(Unit)

        } catch (e: Exception) {
            Log.e("FETCH_PARCELS", "❌ Error: ${e.message}", e)
            Resource.Error("Failed: ${e.localizedMessage}")
        }
    }

    // ============================================
// Parse a single pipe-delimited parcel line
// ============================================
    private fun parseParcelLine(line: String): ActiveParcelEntity? {
        return try {
            val parts = line.split("||")
            if (parts.size < 15) {
                Log.w("ParcelParser", "Invalid line (expected 15 parts, got ${parts.size})")
                return null
            }

            // Format from controller:
            // Id||Plot_Id||Parcel_No||Name||CNIC||Crop_Name||Crop_Area||Zone||Farm||Block||Plot||Area||Tehsil||District||GeomWKT
            val id = parts[0].trim().toLongOrNull() ?: 0L
            val plotId = parts[1].trim()
            val parcelNo = parts[2].trim()
            val name = parts[3].trim()
            val cnic = parts[4].trim()
            val cropName = parts[5].trim()
            val cropArea = parts[6].trim().toDoubleOrNull()
            val zone = parts[7].trim()
            val farm = parts[8].trim()
            val block = parts[9].trim()
            val plot = parts[10].trim()
            val area = parts[11].trim().toDoubleOrNull()
            val tehsil = parts[12].trim()
            val district = parts[13].trim()
            val geomWKT = parts[14].trim()

            // Basic validation
            if (geomWKT.isBlank() || id <= 0) {
                Log.w("ParcelParser", "Skipping invalid parcel: id=$id, geom=${geomWKT.isBlank()}")
                return null
            }

            ActiveParcelEntity(
                pkid = 0,
                id = id,
                parcelNo = parcelNo,        // String now
                subParcelNo = "",
                mauzaId = 0L,
                mauzaName = tehsil,          // Use tehsil as mauzaName for display
                khewatInfo = name,           // Owner name shown as label on map
                areaAssigned = area?.toString() ?: "",
                geomWKT = geomWKT,
                centroid = "",
                distance = 0,
                parcelType = "",
                parcelAreaKMF = null,
                parcelAreaAbadiDeh = null,
                surveyStatusCode = 1,
                surveyId = null,
                isActivate = true,
                unitId = 0L,
                groupId = 0L,
                // Extra fields from new table
                plotId = plotId,
                ownerName = name,
                cnic = cnic,
                cropName = cropName,
                cropArea = cropArea,
                zone = zone,
                farm = farm,
                block = block,
                plot = plot,
                area = area,
                tehsil = tehsil,
                district = district
            )
        } catch (e: Exception) {
            Log.e("ParcelParser", "Error parsing line: ${e.message}")
            null
        }
    }

//    private fun showAreaSelectionDialog() {
//        lifecycleScope.launch {
//            Utility.showProgressAlertDialog(this@MenuActivity, "Downloading parcels...")
//            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
//
//            val result = fetchAndStoreActiveParcels(token)
//
//            Utility.dismissProgressAlertDialog()
//
//            when (result) {
//                is Resource.Success -> {
//                    downloadMapTilesNew()
//                    ToastUtil.showShort(
//                        this@MenuActivity,
//                        "Download successful!"
//                    )
//                }
//
//                is Resource.Error -> {
//                    ToastUtil.showShort(
//                        this@MenuActivity,
//                        result.message ?: "Download failed."
//                    )
//                }
//
//                is Resource.Loading -> Unit
//                is Resource.Unspecified -> Unit
//            }
//        }
//    }

//    private suspend fun fetchAndStoreActiveParcels(token: String): SimpleResource {
//        return try {
//            Log.d("FETCH_PARCELS", "=== Fetching ALL corporate parcels ===")
//
//            val response = serverApi.getCorporateParcels("Bearer $token")
//
//            if (!response.isSuccessful) {
//                Log.e("FETCH_PARCELS", "API error: ${response.code()}")
//                return Resource.Error("Server error: ${response.code()}")
//            }
//
//            val parcels = response.body()?.data ?: emptyList()
//            Log.d("FETCH_PARCELS", "Fetched ${parcels.size} parcels from API")
//
//            if (parcels.isEmpty()) {
//                return Resource.Error("No parcels found on server")
//            }
//
//            // ✅ Clear ALL old parcels and insert new ones
//            database.withTransaction {
//                database.activeParcelDao().deleteAllParcels()
//                Log.d("FETCH_PARCELS", "Cleared all old parcels")
//
//                val entities = parcels.map { parcel ->
//                    ActiveParcelEntity(
//                        pkid = 0,
//                        id = parcel.id,
//                        parcelNo = parcel.parcelNo.toString(),
//                        subParcelNo = parcel.subParcelNo ?: "",
//                        mauzaId = parcel.mauzaId ?: 0L,
//                        mauzaName = "",
//                        khewatInfo = parcel.khewatInfo?: "",
//                        areaAssigned = parcel.areaAssigned ?: "",
//                        geomWKT = parcel.geomWKT?: "",
//                        centroid = "",
//                        distance = parcel.distance?.toInt() ?:0,
//                        parcelType = parcel.parcelType?: "",
//                        parcelAreaKMF = parcel.parcelAreaKMF,
//                        parcelAreaAbadiDeh = parcel.parcelAreaAbadiDeh?.toString(),
//                        surveyStatusCode = parcel.surveyStatusCode,
//                        surveyId = parcel.attachedSurveyId,
//                        isActivate = true,
//                        unitId = 0L,
//                        groupId = 0L,
//                        zone = parcel.zone,
//                        division = parcel.division,
//                        section = parcel.section,
//                        farm = parcel.farm,
//                        block = parcel.block,
//                        plot = parcel.plot,
//                        ownershipStatus = parcel.ownershipStatus,
//                        lessorName = parcel.lessorName,
//                        plotBifurcation = parcel.plotBifurcation,
//                        plotSizeAcres = parcel.plotSizeAcres,
//                        calculatedArea = parcel.calculatedArea?.toDoubleOrNull(),
//                        year = parcel.year
//                    )
//                }
//
//                database.activeParcelDao().insertActiveParcels(entities)
//                Log.d("FETCH_PARCELS", "✅ Inserted ${entities.size} parcels into DB")
//            }
//
//            // ✅ Save a fixed folderKey to SharedPreferences
//            sharedPreferences.edit()
//                .putString(Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name, "corporate")
//                .putLong(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID, 0L)
//                .putString(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME, "Corporate")
//                .putString(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, "corporate")
//                .putLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0L)
//                .putString(Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, "Corporate")
//                .apply()
//
//            Log.d("FETCH_PARCELS", "=== COMPLETED SUCCESSFULLY ===")
//            Resource.Success(Unit)
//
//        } catch (e: Exception) {
//            Log.e("FETCH_PARCELS", "❌ Error: ${e.message}", e)
//            Resource.Error("Failed: ${e.localizedMessage}")
//        }
//    }

    private fun startLogout() {
        val grayColor = Color.GRAY

        val yesText = SpannableString("Yes").apply {
            setSpan(ForegroundColorSpan(grayColor), 0, length, 0)
        }

        val noText = SpannableString("No").apply {
            setSpan(ForegroundColorSpan(grayColor), 0, length, 0)
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Exit!")
            .setCancelable(false)
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton(yesText) { _, _ ->
                Utility.showProgressAlertDialog(this, "Please wait...")
                performLogout()
            }
            .setNegativeButton(noText, null)

        val dialog = builder.create()
        dialog.show()

        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

        positiveButton.textSize = 16f
        positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)

        negativeButton.textSize = 16f
        negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
    }

    private fun performLogout() {
        val userId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
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
                            ToastUtil.showShort(this@MenuActivity, "Logged out successfully")
                            delay(800)
                            clearLocalDataAndNavigate()
                        }

                        is Resource.Error<*> -> {
                            Utility.dismissProgressAlertDialog()
                            // Still logout locally even if server call fails
                            Toast.makeText(
                                this@MenuActivity,
                                "Logged out locally",
                                Toast.LENGTH_SHORT
                            ).show()
                            clearLocalDataAndNavigate()
                        }

                        is Resource.Loading<*> -> {
                            // Progress dialog is already showing
                        }

                        is Resource.Unspecified<*> -> TODO()
                    }
                }
            } catch (e: Exception) {
                Utility.dismissProgressAlertDialog()
                Log.e("LOGOUT", "Logout exception: ${e.message}", e)
                ToastUtil.showShort(this@MenuActivity, "Session ended")
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

    override fun onDestroy() {
        super.onDestroy()
        if (::progressDialogTwo.isInitialized) {
            if (progressDialogTwo.isShowing) {
                progressDialogTwo.dismiss()
            }
        }
        logoutScope.cancel() // Add this line

    }

    private fun downloadMapNew() {
        when (Constants.MAP_DOWNLOAD_TYPE) {
            DownloadType.TPK -> {
//                startDownloadingTPK()
            }

            DownloadType.TILES -> {
                downloadMapTilesNew()
            }
        }
    }

    private fun downloadMapTilesNew() {
        downloadComplete = false

        progressAlertDialog = AlertDialog.Builder(this@MenuActivity)
            .setTitle("Please wait!")
            .setMessage("Preparing download...")
            .setCancelable(false)
            .create()

        tileManager = TileManager(this)
        progressAlertDialog.show()

        val folderKey = "corporate_parcels"
        val BATCH_SIZE = 8          // concurrent downloads at once
        val UI_UPDATE_INTERVAL = 10 // update dialog every N tiles

        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                // ✅ Only fetch WKT strings, not full entities
                val geomStrings = database.activeParcelDao().getAllActiveParcelGeometries()

                if (geomStrings.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        progressAlertDialog.dismiss()
                        ToastUtil.showShort(this@MenuActivity, "No parcels found")
                    }
                    return@launch
                }

                // Build polygon list
                val polygonsList = mutableListOf<Polygon>()
                for (geomString in geomStrings) {
                    try {
                        when {
                            geomString.contains("MULTIPOLYGON") ->
                                Utility.getMultiPolygonFromString(geomString, wgs84)
                                    .mapTo(polygonsList) { Utility.simplifyPolygon(it) }
                            geomString.contains("POLYGON ((") ->
                                Utility.getPolygonFromString(geomString, wgs84)?.let {
                                    polygonsList.add(Utility.simplifyPolygon(it))
                                }
                            geomString.contains("POLYGON") ->
                                Utility.getPolyFromString(geomString, wgs84)?.let {
                                    polygonsList.add(Utility.simplifyPolygon(it))
                                }
                        }
                    } catch (e: Exception) {
                        Log.e("TileDownload", "Geometry error: ${e.message}")
                    }
                }

                if (polygonsList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        progressAlertDialog.dismiss()
                        ToastUtil.showShort(this@MenuActivity, "No valid geometries")
                    }
                    return@launch
                }

                // Build tile set (same logic as before)
                val tilesToDownloadSet = mutableSetOf<Triple<Int, Int, Int>>()
                val combinedGeometry = GeometryEngine.union(polygonsList)
                val bufferedExtent = GeometryEngine.buffer(combinedGeometry.extent, 0.0001) as Polygon

                for (zoomLevel in 7..12) {
                    val extent = bufferedExtent.extent
                    val minX = minOf(getTileX(extent.xMin, zoomLevel), getTileX(extent.xMax, zoomLevel))
                    val maxX = maxOf(getTileX(extent.xMin, zoomLevel), getTileX(extent.xMax, zoomLevel))
                    val minY = minOf(getTileY(extent.yMin, zoomLevel), getTileY(extent.yMax, zoomLevel))
                    val maxY = maxOf(getTileY(extent.yMin, zoomLevel), getTileY(extent.yMax, zoomLevel))
                    for (x in minX..maxX) for (y in minY..maxY)
                        tilesToDownloadSet.add(Triple(zoomLevel, x, y))
                }

                for (polygon in polygonsList) {
                    val parcelExtent = GeometryEngine.buffer(polygon.extent, 0.0002) as Polygon
                    val extent = parcelExtent.extent
                    for (zoomLevel in 13..16) {
                        val minX = minOf(getTileX(extent.xMin, zoomLevel), getTileX(extent.xMax, zoomLevel))
                        val maxX = maxOf(getTileX(extent.xMin, zoomLevel), getTileX(extent.xMax, zoomLevel))
                        val minY = minOf(getTileY(extent.yMin, zoomLevel), getTileY(extent.yMax, zoomLevel))
                        val maxY = maxOf(getTileY(extent.yMin, zoomLevel), getTileY(extent.yMax, zoomLevel))
                        for (x in minX..maxX) for (y in minY..maxY)
                            tilesToDownloadSet.add(Triple(zoomLevel, x, y))
                    }
                }

                val tileList = tilesToDownloadSet.toList()
                val totalTiles = tileList.size
                Log.d("TileDownload", "Total unique tiles: $totalTiles")

                withContext(Dispatchers.Main) {
                    progressAlertDialog.setMessage("Downloading 0 of $totalTiles tiles...")
                }

                // ✅ Check internet once, not per tile
                if (!Utility.checkInternetConnection(this@MenuActivity)) {
                    withContext(Dispatchers.Main) {
                        progressAlertDialog.dismiss()
                        Utility.dialog(context, "No internet connection.", "No Internet!")
                    }
                    return@launch
                }

                // ✅ Write directly to filesDir — no copy step needed
                val outputFolder = File(context.filesDir, "MapTiles/$folderKey")

                val downloadedCount = AtomicInteger(0)
                var lastReportedCount = 0

                // ✅ Process in concurrent batches
                tileList.chunked(BATCH_SIZE).forEach { batch ->
                    val jobs = batch.map { (zoomLevel, tileX, tileY) ->
                        launch {
                            tileManager.downloadAndSaveTileDirect(
                                zoomLevel, tileY, tileX, outputFolder, 7, 16
                            )
                            val current = downloadedCount.incrementAndGet()

                            // ✅ Only update UI every N tiles to avoid main-thread hammering
                            if (current - lastReportedCount >= UI_UPDATE_INTERVAL || current == totalTiles) {
                                lastReportedCount = current
                                withContext(Dispatchers.Main) {
                                    if (!isFinishing && !isDestroyed) {
                                        progressAlertDialog.setMessage(
                                            "Downloading $current of $totalTiles tiles..."
                                        )
                                    }
                                }
                            }
                        }
                    }
                    jobs.forEach { it.join() }
                }

                val finalCount = downloadedCount.get()
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext

                    if (finalCount == totalTiles && totalTiles > 0) {
                        sharedPreferences.edit()
                            .putInt(Constants.SHARED_PREF_SYNC_STATUS, Constants.SYNC_STATUS_SUCCESS)
                            .apply()

                        binding.apply {
                            cvStartSurvey.isEnabled = true
                            cvPropertyList.isEnabled = true
                            llStartSurvey.setBackgroundColor(Color.WHITE)
                            llPropertyList.setBackgroundColor(Color.WHITE)
                            tvSelectedAbadiName.text = "Corporate Parcels"
                            llSelectedAbadi.visibility = View.VISIBLE
                        }

                        progressAlertDialog.dismiss()
                        Toast.makeText(context, "Sync complete! You can now start survey.", Toast.LENGTH_LONG).show()
                        downloadComplete = true
                    } else {
                        progressAlertDialog.dismiss()
                        outputFolder.deleteRecursively()
                        downloadComplete = false
                        ToastUtil.showShort(this@MenuActivity, "Download incomplete")
                    }
                }

            } catch (e: Exception) {
                Log.e("TileDownload", "Error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    progressAlertDialog.dismiss()
                    ToastUtil.showShort(this@MenuActivity, "Download error: ${e.message}")
                }
            }
        }
    }

    private fun downloadMapTiles() {

        downloadComplete = false

        // Initialize the ProgressDialog
        progressAlertDialog = AlertDialog.Builder(this@MenuActivity)
            .setTitle("Please wait!")
            .setMessage("Initializing download...")
            .setCancelable(false)
            .create()

        tileManager = TileManager(this)

        progressAlertDialog.show()

        val mauzaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        val areaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_DOWNLOADED_AREA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        val minZoomLevel = sharedPreferences.getInt(
            Constants.SHARED_PREF_MAP_MIN_SCALE,
            Constants.SHARED_PREF_DEFAULT_MIN_SCALE
        )

        val maxZoomLevel = sharedPreferences.getInt(
            Constants.SHARED_PREF_MAP_MAX_SCALE,
            Constants.SHARED_PREF_DEFAULT_MAX_SCALE
        )

        var currentTile = 0
        var relevantMaxZoom = 0
        var tilesToDownload = 0

        job = CoroutineScope(Dispatchers.IO).launch {
            try {

                val parcels = database.parcelDao().getAllParcelsWithAreaId(areaId)

                val polygonsList = mutableListOf<Polygon>()

                for (parcel in parcels) {
                    val parcelGeom = parcel.geom
                    if (parcelGeom.contains("MULTIPOLYGON (((")) {
                        val polygons = Utility.getMultiPolygonFromString(parcelGeom, wgs84)
                        for (polygon in polygons) {
                            val simplifiedPolygon = Utility.simplifyPolygon(polygon)
                            polygonsList.add(simplifiedPolygon)
                        }
                    } else if (parcelGeom.contains("POLYGON ((")) {
                        Utility.getPolygonFromString(parcelGeom, wgs84)?.let { parsedPolygon ->
                            val polygon = Utility.simplifyPolygon(parsedPolygon)
                            polygonsList.add(polygon)
                        } ?: Log.w(
                            "downloadMapTiles",
                            "Skipped invalid POLYGON WKT: $parcelGeom"
                        )
                    } else {
                        Utility.getPolyFromString(parcelGeom, wgs84)?.let { parsedPolygon ->
                            val polygon = Utility.simplifyPolygon(parsedPolygon)
                            polygonsList.add(polygon)
                        } ?: Log.w(
                            "downloadMapTiles",
                            "Skipped invalid malformed geometry: $parcelGeom"
                        )
                    }
                }

                // Union all polygons into a single geometry
                val combinedGeometry = GeometryEngine.union(polygonsList)

                // Get the extent (bounding box) of the combined geometry
                val combinedExtent = combinedGeometry.extent

                // Apply a 10-meter buffer to the extent in WGS84
                val bufferDistance = 0.0001 // Roughly 50 meters in latitude/longitude
                val bufferedGeometry =
                    GeometryEngine.buffer(combinedExtent, bufferDistance) as Polygon

                // Get the extent of the buffered polygon
                val bufferedExtent = bufferedGeometry.extent

//                 Get minX, minY, maxX, and maxY from the buffered envelope in WGS84
//                val minX = bufferedExtent.xMin
//                val minY = bufferedExtent.yMin
//                val maxX = bufferedExtent.xMax
//                val maxY = bufferedExtent.xMax

//                Log.d("MapExtent", "$minX, $minY, $maxX, $maxY")

                val result =
                    calculateTilesToDownload(bufferedExtent, minZoomLevel, maxZoomLevel)
                relevantMaxZoom = result.first
                tilesToDownload = result.second


                // Add these logs
                Log.d("TileDownload", "=== TILE DOWNLOAD STARTED ===")
                Log.d("TileDownload", "Min Zoom Level: $minZoomLevel")
                Log.d("TileDownload", "Max Zoom Level: $maxZoomLevel")
                Log.d("TileDownload", "Relevant Max Zoom: $relevantMaxZoom")
                Log.d("TileDownload", "Total Tiles to Download: $tilesToDownload")
                Log.d(
                    "TileDownload",
                    "Buffered Extent: ${bufferedExtent.xMin}, ${bufferedExtent.yMin}, ${bufferedExtent.xMax}, ${bufferedExtent.yMax}"
                )
                // Now use relevantMaxZoom and tilesToDownload in your download logic
                println("Max Zoom Level: $relevantMaxZoom")
                println("Total Tiles to Download: $tilesToDownload")

                // Update the progress dialog with the total number of tiles
                withContext(Dispatchers.Main) {
                    progressAlertDialog.setMessage("Total tiles to download: $tilesToDownload")
                }

                // Download the tiles
                for (zoomLevel in minZoomLevel..relevantMaxZoom) {
                    val x1: Int = getTileX(bufferedExtent.xMin, zoomLevel)
                    val y1: Int = getTileY(bufferedExtent.yMin, zoomLevel)
                    val x2: Int = getTileX(bufferedExtent.xMax, zoomLevel)
                    val y2: Int = getTileY(bufferedExtent.yMax, zoomLevel)

                    var minX = x1
                    var maxX = x2
                    var minY = y1
                    var maxY = y2 // Assume the min , max values

                    if (minX > maxX) {
                        val temp = minX
                        minX = maxX
                        maxX = temp
                    }

                    if (minY > maxY) {
                        val temp = minY
                        minY = maxY
                        maxY = temp
                    }

                    val tilesForThisZoom = (maxX - minX + 1) * (maxY - minY + 1)
                    Log.d("TileDownload", "--- Zoom Level $zoomLevel ---")
                    Log.d("TileDownload", "Tile range: X($minX-$maxX), Y($minY-$maxY)")
                    Log.d("TileDownload", "Tiles for zoom $zoomLevel: $tilesForThisZoom")

                    for (tileX in minX..maxX) {
                        for (tileY in minY..maxY) {
                            if (Utility.checkInternetConnection(this@MenuActivity)) {
                                currentTile++
                                Log.v(
                                    "TileDownload",
                                    "Downloading tile $currentTile/$tilesToDownload - Zoom:$zoomLevel, X:$tileX, Y:$tileY"
                                )
                                tileManager.downloadAndCacheTile(
                                    zoomLevel,
                                    tileY,
                                    tileX,
                                    areaId,
                                    "cached",
                                    minZoomLevel,
                                    relevantMaxZoom,
                                )
                                // Update progress dialog
                                withContext(Dispatchers.Main) {
                                    progressAlertDialog.setMessage("Downloading tile $currentTile of $tilesToDownload")
                                }
                            }
                        }
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    // Hide the progress dialog when done
                    if (currentTile == tilesToDownload) {

                        if (!(isFinishing || isDestroyed)) {

                            val sourceFile = File(context.cacheDir, "MapTiles/${areaId}")
                            val destinationFile = File(context.filesDir, "MapTiles/${areaId}")

                            copyFolder(sourceFile, destinationFile)

                            sourceFile.deleteRecursively()

                            binding.apply {
                                cvStartSurvey.isEnabled = true
                                cvPropertyList.isEnabled = true

                                llStartSurvey.setBackgroundColor(Color.WHITE)
                                llPropertyList.setBackgroundColor(Color.WHITE)
                            }

                            sharedPreferences.edit().putInt(
                                Constants.SHARED_PREF_SYNC_STATUS, Constants.SYNC_STATUS_SUCCESS
                            ).apply()

                            val selectedAreaId = sharedPreferences.getLong(
                                Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
                                Constants.SHARED_PREF_DEFAULT_INT.toLong()
                            )

                            progressAlertDialog.setMessage("Download complete")
                            progressAlertDialog.dismiss()

                            if (selectedAreaId == Constants.SHARED_PREF_DEFAULT_INT.toLong() || selectedAreaId == areaId) {
                                val downloadedMauzaId = sharedPreferences.getLong(
                                    Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID,
                                    Constants.SHARED_PREF_DEFAULT_INT.toLong()
                                )

                                val downloadedMauzaName = sharedPreferences.getString(
                                    Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME,
                                    Constants.SHARED_PREF_DEFAULT_STRING
                                )

                                val downloadedAreaId = sharedPreferences.getLong(
                                    Constants.SHARED_PREF_USER_DOWNLOADED_AREA_ID,
                                    Constants.SHARED_PREF_DEFAULT_INT.toLong()
                                )

                                val downloadedAreaName = sharedPreferences.getString(
                                    Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name,
                                    Constants.SHARED_PREF_DEFAULT_STRING
                                )

                                sharedPreferences.edit().putLong(
                                    Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
                                    downloadedMauzaId
                                ).apply()

                                sharedPreferences.edit().putString(
                                    Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME,
                                    downloadedMauzaName
                                ).apply()

                                sharedPreferences.edit().putLong(
                                    Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
                                    downloadedAreaId
                                ).apply()

                                sharedPreferences.edit().putString(
                                    Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
                                    downloadedAreaName
                                ).apply()

                                //visible the selected mauza area header
                                binding.apply {
                                    if (downloadedMauzaName != "" && downloadedAreaName != "") {
                                        tvSelectedAbadiName.text =
                                            "$downloadedMauzaName\n($downloadedAreaName)"
                                        llSelectedAbadi.visibility = View.VISIBLE
                                    } else {
                                        llSelectedAbadi.visibility = View.INVISIBLE
                                    }
                                }
                                ToastUtil.showShort(
                                    context,
                                    "Data downloaded successfully"
                                )
                            } else {
                                val builder = AlertDialog.Builder(this@MenuActivity)
                                    .setMessage("Do you want to select the current downloaded area to start survey?")
                                    .setPositiveButton("YES") { dialog, _ ->
                                        val downloadedMauzaId = sharedPreferences.getLong(
                                            Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID,
                                            Constants.SHARED_PREF_DEFAULT_INT.toLong()
                                        )

                                        val downloadedMauzaName = sharedPreferences.getString(
                                            Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME,
                                            Constants.SHARED_PREF_DEFAULT_STRING
                                        )

                                        val downloadedAreaId = sharedPreferences.getLong(
                                            Constants.SHARED_PREF_USER_DOWNLOADED_AREA_ID,
                                            Constants.SHARED_PREF_DEFAULT_INT.toLong()
                                        )

                                        val downloadedAreaName = sharedPreferences.getString(
                                            Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name,
                                            Constants.SHARED_PREF_DEFAULT_STRING
                                        )

                                        sharedPreferences.edit().putLong(
                                            Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
                                            downloadedMauzaId
                                        ).apply()

                                        sharedPreferences.edit().putString(
                                            Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME,
                                            downloadedMauzaName
                                        ).apply()

                                        sharedPreferences.edit().putLong(
                                            Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
                                            downloadedAreaId
                                        ).apply()

                                        sharedPreferences.edit().putString(
                                            Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
                                            downloadedAreaName
                                        ).apply()

                                        //visible the selected mauza area header
                                        binding.apply {
                                            if (downloadedMauzaName != "" && downloadedAreaName != "") {
                                                tvSelectedAbadiName.text =
                                                    "$downloadedMauzaName\n($downloadedAreaName)"
                                                llSelectedAbadi.visibility = View.VISIBLE
                                            } else {
                                                llSelectedAbadi.visibility = View.INVISIBLE
                                            }
                                        }
                                        ToastUtil.showShort(
                                            context,
                                            "Data downloaded successfully"
                                        )
                                        dialog.dismiss()
                                    }.setNegativeButton("No") { dialog, _ ->
                                        ToastUtil.showShort(
                                            context,
                                            "Data downloaded successfully"
                                        )
                                        dialog.dismiss()
                                    }

                                val dialog = builder.create()
                                dialog.show()

                                val positiveButton =
                                    dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                                val negativeButton =
                                    dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

                                positiveButton.textSize = 16f
                                positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)

                                negativeButton.textSize = 16f
                                negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                            }
                            downloadComplete = true
                        }

                    } else {

                        if (!(isFinishing || isDestroyed)) {
                            progressAlertDialog.setMessage("Download Incomplete")
                            progressAlertDialog.dismiss()
                        }

                        val filesDir = File(context.filesDir, "MapTiles/${areaId}")
                        // Recursively delete all files and subdirectories within filesDir, then delete filesDir itself
                        filesDir.deleteRecursively()

                        downloadComplete = false

//                        deleteUnSyncedData()

                        ToastUtil.showShort(
                            this@MenuActivity,
                            "Data Incomplete"
                        )
                    }

                }
            }
        }
    }

    private fun calculateTilesToDownload(
        bufferedExtent: Envelope,
        minZoomLevel: Int,
        maxZoomLevel: Int
    ): Pair<Int, Int> { // Return type is Pair<Int, Int>
        var tilesToDownload: Int
        var currentMaxZoom = maxZoomLevel

        do {
            tilesToDownload = 0

            // Calculate tiles for the current max zoom level
            for (zoomLevel in minZoomLevel..currentMaxZoom) {
                val x1: Int = getTileX(bufferedExtent.xMin, zoomLevel)
                val y1: Int = getTileY(bufferedExtent.yMin, zoomLevel)
                val x2: Int = getTileX(bufferedExtent.xMax, zoomLevel)
                val y2: Int = getTileY(bufferedExtent.yMax, zoomLevel)

                var minX = x1
                var maxX = x2
                var minY = y1
                var maxY = y2 // Assume the min, max values

                // Ensure min/max values are in correct order
                if (minX > maxX) {
                    val temp = minX
                    minX = maxX
                    maxX = temp
                }
                if (minY > maxY) {
                    val temp = minY
                    minY = maxY
                    maxY = temp
                }

                // Calculate the number of tiles to download
                tilesToDownload += (maxX - minX + 1) * (maxY - minY + 1)
            }

            // Check the number of tiles to download
            if (tilesToDownload <= 2000 || currentMaxZoom <= 16) {
                break // Exit if under the limit or at max zoom level
            }

            // Reduce max zoom level
            currentMaxZoom--
        } while (true)

        return Pair(currentMaxZoom, tilesToDownload) // Return max zoom level and tile count
    }

    private fun copyFolder(sourceFolder: File, destinationFolder: File) {
        // Create the destination folder if it doesn't exist
        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs()
        }

        // Loop through each file and folder inside the source folder
        sourceFolder.listFiles()?.forEach { file ->
            val destinationFile = File(destinationFolder, file.name)
            if (file.isDirectory) {
                // Recursively copy sub-folders
                copyFolder(file, destinationFile)
            } else {
                // Copy files using InputStream and OutputStream
                file.inputStream().use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun getTileX(lon: Double, zoom: Int): Int {
        var xtile =
            floor((lon + 180) / 360 * (1 shl zoom)).toInt()

        if (xtile < 0) xtile = 0

        if (xtile >= (1 shl zoom)) xtile = ((1 shl zoom) - 1)

        return xtile
    }

    private fun getTileY(lat: Double, zoom: Int): Int {
        var ytile =
            floor((1 - ln(tan(Math.toRadians(lat)) + 1 / cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 shl zoom))
                .toInt()

        if (ytile < 0) ytile = 0

        if (ytile >= (1 shl zoom)) ytile = ((1 shl zoom) - 1)

        return ytile
    }


    override fun onPause() {
        super.onPause()
        if (::progressAlertDialog.isInitialized) {
            if (progressAlertDialog.isShowing) {
                progressAlertDialog.dismiss()
            }
        }
        if (::progressDialog.isInitialized) {
            if (progressDialog.isShowing) {
                progressDialog.dismiss()
            }
        }
        stopLoadingParcels()

//        deleteUnSyncedData()
    }

    private fun stopLoadingParcels() {
        job?.cancel()
        job = null
    }

    override fun onResume() {
        super.onResume()
        // Call the function to check parcels and update UI
        checkParcelsAndUpdateUI()
    }

    private fun checkParcelsAndUpdateUI() {
        // Enable or disable the survey button based on database and file checks
        lifecycleScope.launch {
            try {
                checkDatabaseAndFileStatus(1) // Retry up to 1 time
            } catch (e: Exception) {
                ToastUtil.showShort(
                    context,
                    "Resume Error: ${e.message}"
                )
            }
        }
    }

    private suspend fun checkDatabaseAndFileStatus(retries: Int) {

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

        val mouzaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_ASSIGNED_MOUZA_NAME, Constants.SHARED_PREF_DEFAULT_STRING
        )

        // ✅ Check parcels from DB and tiles from fixed folder
        val parcelsCount = withContext(Dispatchers.IO) {
            database.activeParcelDao().getAllActiveParcels().size
        }

        val tileFolder = File(context.filesDir, "MapTiles/corporate_parcels")

        // Update UI
        withContext(Dispatchers.Main) {
            binding.apply {
                tvUserName.text = userName

//                if (mouzaName.isNullOrEmpty()) {
//                    tvMouzaCaption.visibility = View.GONE
//                    tvMouzaName.visibility = View.GONE
//                } else {
//                    tvMouzaCaption.visibility = View.VISIBLE
//                    tvMouzaName.text = mouzaName
//                    tvMouzaName.visibility = View.VISIBLE
//                }

                // ✅ Show parcel count if synced
                if (parcelsCount > 0 && tileFolder.exists()) {
                    tvSelectedAbadiName.text = "Corporate Parcels ($parcelsCount)"
                    llSelectedAbadi.visibility = View.VISIBLE

                    cvStartSurvey.isEnabled = true
                    cvStartSurvey.isFocusable = true
                    cvPropertyList.isEnabled = true
                    cvPropertyList.isFocusable = true
                    llStartSurvey.setBackgroundColor(Color.WHITE)
                    llPropertyList.setBackgroundColor(Color.WHITE)
                } else {
                    llSelectedAbadi.visibility = View.INVISIBLE

                    cvStartSurvey.isEnabled = false
                    cvStartSurvey.isFocusable = false
                    cvPropertyList.isEnabled = false
                    cvPropertyList.isFocusable = false
                    llStartSurvey.setBackgroundColor(Color.LTGRAY)
                    llPropertyList.setBackgroundColor(Color.LTGRAY)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return try {
            val inflater = menuInflater
            inflater.inflate(R.menu.item_options_menu, menu)
            // Tint menu icons based on current theme
            menu?.let { updateMenuIconColors(it) }
            true
        } catch (e: Exception) {
            Log.e("MenuActivity", "Error creating options menu: ${e.message}")
            super.onCreateOptionsMenu(menu)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> {
                Intent(this@MenuActivity, SettingsActivity::class.java).apply {
                    startActivity(this)
                }
                return true
            }

            R.id.action_theme -> {
                showThemeDialog()
                return true
            }

            R.id.action_logout -> {
                startLogout()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
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

                        // Use the Application helper method to update theme
                        MyApplication.updateTheme(this, newMode)

                        dialog.dismiss()

                        // Recreate activity to apply theme immediately
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
        // Small delay to ensure theme is applied
        Handler(Looper.getMainLooper()).postDelayed({
            recreate()
        }, 100)
    }

    private fun applySavedTheme() {
        val savedTheme =
            sharedPreferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)
    }

    private fun setupActionBar() {
        try {
            supportActionBar?.apply {
                setDisplayShowTitleEnabled(true)
                elevation = 4f
                // Set ActionBar title to uppercase
                title = title?.toString()?.uppercase()
            }

            // Setup status bar
            setupStatusBar()
        } catch (e: Exception) {
            Log.e("MenuActivity", "Error setting up action bar: ${e.message}")
        }
    }

    private fun setupStatusBar() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

                val isDarkMode = isDarkModeActive()

                // Set status bar color based on theme
                window.statusBarColor = if (isDarkMode) {
                    ContextCompat.getColor(this, R.color.dark_primary)
                } else {
                    ContextCompat.getColor(this, R.color.forest_green)
                }

                // Set status bar icon color (always light for both themes)
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

    private fun isDarkModeActive(): Boolean {
        return try {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> true
                Configuration.UI_MODE_NIGHT_NO -> false
                else -> false
            }
        } catch (e: Exception) {
            Log.e("MenuActivity", "Error checking dark mode: ${e.message}")
            false
        }
    }


    private fun updateMenuIconColors(menu: Menu) {
        val isDarkMode = isDarkModeActive()
        val iconColor = if (isDarkMode) {
            ContextCompat.getColor(this, R.color.white)
        } else {
            ContextCompat.getColor(this, R.color.white)
        }

        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            menuItem.icon?.let { icon ->
                val wrappedIcon = DrawableCompat.wrap(icon)
                DrawableCompat.setTint(wrappedIcon, iconColor)
                menuItem.icon = wrappedIcon
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Update UI elements when configuration changes (like system theme change)
        setupStatusBar()
        invalidateOptionsMenu() // This will call onCreateOptionsMenu again
    }


}
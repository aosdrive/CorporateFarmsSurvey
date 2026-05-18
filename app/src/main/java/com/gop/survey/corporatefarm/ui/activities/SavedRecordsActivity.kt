package com.gop.survey.corporatefarm.ui.activities

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gop.survey.corporatefarm.R
import com.gop.survey.corporatefarm.adapter.SavedAdapter
import com.gop.survey.corporatefarm.common.Constants
import com.gop.survey.corporatefarm.common.Resource
import com.gop.survey.corporatefarm.common.SavedItemClickListener
import com.gop.survey.corporatefarm.common.Utility
import com.gop.survey.corporatefarm.data.local.AppDatabase
import com.gop.survey.corporatefarm.databinding.ActivitySavedRecordsBinding
import com.gop.survey.corporatefarm.domain.model.SurveyMergeDetails
import com.gop.survey.corporatefarm.presentation.saved.SavedViewModel
import com.gop.survey.corporatefarm.presentation.util.ToastUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SavedRecordsActivity : AppCompatActivity(), SavedItemClickListener {

    private val viewModel: SavedViewModel by viewModels()
    private val savedAdapter = SavedAdapter(this)
    private lateinit var context: Context
    private lateinit var binding: ActivitySavedRecordsBinding

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private var uploadType: String = Constants.UPLOAD_SINGLE_RECORD

    // Hold full list for filtering
    private var fullList: List<SurveyMergeDetails> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySavedRecordsBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        context = this

        // ============ Premium header — hide ActionBar ============
        supportActionBar?.hide()

        // ============ Match status bar with header ============
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.parseColor("#1B5E20")
        }

        setupRecyclerView()
        setupSearch()
        setupActionButtons()
        observeViewModel()
        observeCounts()
    }

    // ============================================
    // SETUP — RecyclerView
    // ============================================
    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@SavedRecordsActivity)
            adapter = savedAdapter
        }
    }

    // ============================================
    // SETUP — Search bar
    // ============================================
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterRecords(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterRecords(query: String) {
        if (query.isBlank()) {
            savedAdapter.submitList(fullList)
            return
        }
        val q = query.trim().lowercase()
        val filtered = fullList.filter { item ->
            // Adjust these fields based on what SurveyMergeDetails actually has
            val parcelMatch = item.parcelNo.toString().contains(q, ignoreCase = true)
            val uniqueIdMatch = item.uniqueId?.lowercase()?.contains(q) == true
            parcelMatch || uniqueIdMatch
        }
        savedAdapter.submitList(filtered)
    }

    // ============================================
    // SETUP — Filter button + FAB
    // ============================================
    private fun setupActionButtons() {
        binding.btnFilter.setOnClickListener {
            // Aap yahan filter dialog show kar sakte hain
            // For now, just a placeholder
            ToastUtil.showShort(context, "Filter options coming soon")
        }

        binding.fabAddSurvey.setOnClickListener {
            // Navigate to new survey or parcel selection screen
            // Adjust target activity per your app flow
            try {
                // Example — replace with your actual screen:
                // startActivity(Intent(this, ParcelSelectionActivity::class.java))
                finish() // for now go back to previous screen where survey starts
            } catch (e: Exception) {
                ToastUtil.showShort(context, "Could not open new survey")
            }
        }
    }

    // ============================================
    // OBSERVE — Counts + visibility
    // ============================================
    private fun observeCounts() {
        database.surveyFormDao().liveTotalPendingCount().observe(this) { totalPendingRecords ->
            val total = totalPendingRecords ?: 0

            // Update both stat cards
            binding.tvTotalCount.text = total.toString()
            binding.tvPendingCount.text = total.toString()  // All saved = pending in your case

            // Update details strip
            val areaName = sharedPreferences.getString(
                Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, ""
            ).orEmpty()
            binding.tvDetails.text = if (areaName.isNotBlank())
                "Area: $areaName  •  Pending: $total"
            else
                "Pending Records: $total"

            // Toggle visibility
            if (total > 0) {
                binding.detailsCard.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.VISIBLE
                binding.noRecordLayout.visibility = View.GONE
            } else {
                binding.detailsCard.visibility = View.GONE
                binding.recyclerView.visibility = View.GONE
                binding.noRecordLayout.visibility = View.VISIBLE
            }
        }
    }

    // ============================================
    // OBSERVE — Surveys list + upload + delete
    // ============================================
    private fun observeViewModel() {
        viewModel.surveys.observe(this) { surveys ->
            fullList = surveys.orEmpty()
            val currentQuery = binding.etSearch.text?.toString().orEmpty()
            if (currentQuery.isBlank()) {
                savedAdapter.submitList(fullList)
            } else {
                filterRecords(currentQuery)
            }
        }

        lifecycleScope.launch {
            viewModel.deleted.collect {
                when (it) {
                    is Resource.Loading -> Utility.showProgressAlertDialog(context, "Data deleting...")
                    is Resource.Success -> {
                        Utility.dismissProgressAlertDialog()
                        ToastUtil.showShort(context, "Record Deleted")
                    }
                    is Resource.Error -> {
                        Utility.dismissProgressAlertDialog()
                        Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
        }

        lifecycleScope.launch {
            viewModel.uploaded.collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        if (uploadType.equals(Constants.UPLOAD_SINGLE_RECORD, ignoreCase = true)) {
                            Utility.showProgressAlertDialog(context, "Data uploading...")
                        }
                    }

                    is Resource.Success -> {
                        if (uploadType.equals(Constants.UPLOAD_SINGLE_RECORD, ignoreCase = true)) {
                            Utility.dismissProgressAlertDialog()
                            ToastUtil.showShort(context, "Data Uploaded Successfully")
                        } else {
                            postAllSavedData()
                        }
                    }

                    is Resource.Error -> {
                        Utility.dismissProgressAlertDialog()

                        result.message?.let { msg ->
                            if (msg.contains("401")) {
                                sharedPreferences.edit()
                                    .putInt(
                                        Constants.SHARED_PREF_LOGIN_STATUS,
                                        Constants.LOGIN_STATUS_INACTIVE
                                    )
                                    .putString(
                                        Constants.SHARED_PREF_USER_NAME,
                                        Constants.SHARED_PREF_DEFAULT_STRING
                                    )
                                    .apply()

                                Intent(this@SavedRecordsActivity, AuthActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    startActivity(this)
                                    finish()
                                }
                                ToastUtil.showShort(this@SavedRecordsActivity, "Session expired")
                            } else {
                                ToastUtil.showShort(context, msg)
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    // ============================================
    // SavedItemClickListener callbacks
    // ============================================
    override fun onUploadItemClicked(survey: SurveyMergeDetails, uploadButton: Button) {
        uploadButton.isEnabled = false
        uploadType = Constants.UPLOAD_SINGLE_RECORD
        postRecord(survey, uploadButton)
    }

    private fun postRecord(survey: SurveyMergeDetails, uploadButton: Button?) {
        if (Utility.checkInternetConnection(this)) {
            viewModel.postData(survey, uploadButton)
        } else {
            Utility.dialog(
                context,
                "Please make sure you are connected to the internet and try again.",
                "No Internet!"
            )
            uploadButton?.isEnabled = true
        }
    }

    override fun onDeleteItemClicked(survey: SurveyMergeDetails) {
        val builder = AlertDialog.Builder(this)
            .setTitle("Confirm!")
            .setCancelable(false)
            .setMessage("Are you sure, you want to delete this record.")
            .setPositiveButton("Proceed") { _, _ -> viewModel.deleteData(survey) }
            .setNegativeButton("Cancel", null)

        val dialog = builder.create()
        dialog.show()

        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

        positiveButton.textSize = 16f
        positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        negativeButton.textSize = 16f
        negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
    }

    override fun onViewItemClicked(survey: SurveyMergeDetails) {
        Intent(context, ViewRecordActivity::class.java).apply {
            val bundle = Bundle()
            bundle.putLong("parcelNo", survey.parcelNo)
            bundle.putString("uniqueId", survey.uniqueId)
            putExtra("bundle_data", bundle)
            startActivity(this)
        }
    }

    // ============================================
    // OPTIONS MENU — upload all
    // ============================================
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Since we hid the action bar, the menu won't show automatically.
        // The user can trigger "Upload All" via the filter button or a long-press on FAB if needed.
        // But we'll keep this for completeness.
        menuInflater.inflate(R.menu.upload_all_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.upload_all -> {
                handleUploadAll()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun handleUploadAll() {
        if (Utility.checkInternetConnection(this@SavedRecordsActivity)) {
            lifecycleScope.launch {
                val totalPendingRecords = database.surveyFormDao().totalPendingCount()
                if (totalPendingRecords > 0) {
                    val builder = AlertDialog.Builder(this@SavedRecordsActivity)
                        .setTitle("Confirm!")
                        .setCancelable(false)
                        .setMessage("Are you sure, you want to upload all records.")
                        .setPositiveButton("Proceed") { _, _ ->
                            Utility.showProgressAlertDialog(context, "Data uploading...")
                            postAllSavedData()
                        }
                        .setNegativeButton("Cancel", null)

                    val dialog = builder.create()
                    dialog.show()

                    val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

                    positiveButton.textSize = 16f
                    positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                    negativeButton.textSize = 16f
                    negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                } else {
                    ToastUtil.showShort(context, "No record found.")
                }
            }
        } else {
            Utility.dialog(
                context,
                "Please make sure you are connected to the internet and try again.",
                "No Internet!"
            )
        }
    }

    private fun postAllSavedData() {
        lifecycleScope.launch {
            try {
                val survey = database.surveyFormDao().getListSavedRecordsDetailsByLimit()
                uploadType = Constants.UPLOAD_ALL
                postRecord(survey, null)
            } catch (e: Exception) {
                e.printStackTrace()
                Utility.dismissProgressAlertDialog()
            }
        }
    }
}
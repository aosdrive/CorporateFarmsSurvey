package com.gop.survey.corporatefarm.data.repository

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.gop.survey.corporatefarm.common.Constants
import com.gop.survey.corporatefarm.data.local.AddVarietyRequest
import com.gop.survey.corporatefarm.data.local.AppDatabase
import com.gop.survey.corporatefarm.data.remote.ServerApi
import com.gop.survey.corporatefarm.domain.model.BlockEntity
import com.gop.survey.corporatefarm.domain.model.CropEntity
import com.gop.survey.corporatefarm.domain.model.CropTypeEntity
import com.gop.survey.corporatefarm.domain.model.CropVarietyEntity
import com.gop.survey.corporatefarm.domain.model.DivisionEntity
import com.gop.survey.corporatefarm.domain.model.FarmEntity
import com.gop.survey.corporatefarm.domain.model.IrrigationSourceEntity
import com.gop.survey.corporatefarm.domain.model.PlotEntity
import com.gop.survey.corporatefarm.domain.model.SectionEntity
import com.gop.survey.corporatefarm.domain.model.ZoneEntity
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DropdownRepository"
private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours

@Singleton
class DropdownRepository @Inject constructor(
    private val database: AppDatabase,
    private val serverApi: ServerApi,
    private val sharedPreferences: SharedPreferences
) {

    // ========== CROPS ==========
    suspend fun getCrops(forceRefresh: Boolean = false): List<String> {
        return try {
            val localCount = database.cropDao().getCropCount()
            if (forceRefresh || localCount == 0) {
                val response = serverApi.getCrops()
                if (response.isSuccessful && response.body() != null) {
                    val serverCrops = response.body()!!.map {
                        CropEntity(value = it.value, isSynced = true)
                    }
                    database.cropDao().deleteAllCrops()
                    database.cropDao().insertAllCrops(serverCrops)
                    Log.d("DropdownRepo", "Crops synced from server: ${serverCrops.size}")
                }
            }
            val crops = database.cropDao().getAllCrops()
            crops.map { it.value }
        } catch (e: Exception) {
            Log.e("DropdownRepo", "Error fetching crops: ${e.message}")
            database.cropDao().getAllCrops().map { it.value }
        }
    }

    // ========== CROP TYPES ==========
    suspend fun getCropTypes(forceRefresh: Boolean = false): List<String> {
        return try {
            val localCount = database.cropTypeDao().getCropTypeCount()
            if (forceRefresh || localCount == 0) {
                val response = serverApi.getCropTypes()
                if (response.isSuccessful && response.body() != null) {
                    val serverCropTypes = response.body()!!.map {
                        CropTypeEntity(value = it.value, isSynced = true)
                    }
                    database.cropTypeDao().deleteAllCropTypes()
                    database.cropTypeDao().insertAllCropTypes(serverCropTypes)
                    Log.d("DropdownRepo", "Crop types synced from server: ${serverCropTypes.size}")
                }
            }
            val cropTypes = database.cropTypeDao().getAllCropTypes()
            cropTypes.map { it.value }
        } catch (e: Exception) {
            Log.e("DropdownRepo", "Error fetching crop types: ${e.message}")
            database.cropTypeDao().getAllCropTypes().map { it.value }
        }
    }

    // ========== VARIETIES ==========
    suspend fun getVarieties(forceRefresh: Boolean = false): List<String> {
        return try {
            val localCount = database.cropVarietyDao().getVarietyCount()
            if (forceRefresh || localCount == 0) {
                val response = serverApi.getCropVarieties()
                if (response.isSuccessful && response.body() != null) {
                    val serverVarieties = response.body()!!.map {
                        CropVarietyEntity(value = it.value, isSynced = true)
                    }
                    val unsyncedVarieties = database.cropVarietyDao().getUnsyncedVarieties()
                    database.cropVarietyDao().deleteAllVarieties()
                    database.cropVarietyDao().insertAllVarieties(serverVarieties)
                    unsyncedVarieties.forEach {
                        database.cropVarietyDao().insertVariety(it)
                    }
                    Log.d("DropdownRepo", "Varieties synced from server: ${serverVarieties.size}")
                }
            }
            val varieties = database.cropVarietyDao().getAllVarieties()
            varieties.map { it.value }
        } catch (e: Exception) {
            Log.e("DropdownRepo", "Error fetching varieties: ${e.message}")
            database.cropVarietyDao().getAllVarieties().map { it.value }
        }
    }

    suspend fun addVariety(varietyName: String): AddVarietyResult {
        return try {
            val existing = database.cropVarietyDao().getVarietyByValue(varietyName)
            if (existing != null) {
                return AddVarietyResult.AlreadyExists
            }
            try {
                val response = serverApi.addCropVariety(AddVarietyRequest(varietyName))
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    if (result.alreadyExists == true) {
                        val variety = CropVarietyEntity(value = varietyName, isSynced = true)
                        database.cropVarietyDao().insertVariety(variety)
                        return AddVarietyResult.AlreadyExists
                    } else {
                        val variety = CropVarietyEntity(value = varietyName, isSynced = true)
                        database.cropVarietyDao().insertVariety(variety)
                        return AddVarietyResult.Success
                    }
                } else {
                    val variety = CropVarietyEntity(value = varietyName, isSynced = false)
                    database.cropVarietyDao().insertVariety(variety)
                    return AddVarietyResult.SavedOffline
                }
            } catch (e: Exception) {
                Log.e("DropdownRepo", "Network error adding variety: ${e.message}")
                val variety = CropVarietyEntity(value = varietyName, isSynced = false)
                database.cropVarietyDao().insertVariety(variety)
                return AddVarietyResult.SavedOffline
            }
        } catch (e: Exception) {
            Log.e("DropdownRepo", "Error adding variety: ${e.message}")
            AddVarietyResult.Error(e.message ?: "Unknown error")
        }
    }

    // ========== IRRIGATION SOURCES ==========
    suspend fun getIrrigationSources(forceRefresh: Boolean = false): List<String> {
        return try {
            val localCount = database.irrigationSourceDao().getSourceCount()
            if (forceRefresh || localCount == 0) {
                val response = serverApi.getIrrigationSources()
                if (response.isSuccessful && response.body() != null) {
                    val serverSources = response.body()!!.map {
                        IrrigationSourceEntity(value = it.value, isSynced = true)
                    }
                    database.irrigationSourceDao().deleteAllSources()
                    database.irrigationSourceDao().insertAllSources(serverSources)
                    Log.d("DropdownRepo", "Irrigation sources synced from server: ${serverSources.size}")
                }
            }
            val sources = database.irrigationSourceDao().getAllSources()
            sources.map { it.value }
        } catch (e: Exception) {
            Log.e("DropdownRepo", "Error fetching irrigation sources: ${e.message}")
            database.irrigationSourceDao().getAllSources().map { it.value }
        }
    }

    suspend fun syncUnsyncedVarieties(): Int {
        var syncedCount = 0
        try {
            val unsyncedVarieties = database.cropVarietyDao().getUnsyncedVarieties()
            unsyncedVarieties.forEach { variety ->
                try {
                    val response = serverApi.addCropVariety(AddVarietyRequest(variety.value))
                    if (response.isSuccessful) {
                        val updated = variety.copy(isSynced = true)
                        database.cropVarietyDao().updateVariety(updated)
                        syncedCount++
                    }
                } catch (e: Exception) {
                    Log.e("DropdownRepo", "Failed to sync variety: ${variety.value}")
                }
            }
        } catch (e: Exception) {
            Log.e("DropdownRepo", "Error syncing varieties: ${e.message}")
        }
        return syncedCount
    }

    // ============================================
// FETCH ZONES FROM SERVER
// ============================================
    suspend fun fetchZonesFromServer(): Resource<List<ZoneEntity>> = withContext(Dispatchers.IO) {
        try {
            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            if (token.isEmpty()) return@withContext Resource.Error("No auth token")

            Log.d(TAG, "🔄 Fetching zones from server...")
            val response = serverApi.getZones("Bearer $token")

            if (response.isSuccessful && response.body() != null) {
                val zoneEntities = response.body()!!.map { dto ->
                    ZoneEntity(zoneId = dto.zoneId, zoneName = dto.zoneName)
                }
                // Cache locally for offline use
                database.zoneDao().insertZones(zoneEntities)
                Log.d(TAG, "✅ Fetched ${zoneEntities.size} zones from server")
                Resource.Success(zoneEntities)
            } else {
                Log.w(TAG, "⚠️ Server returned ${response.code()}, using cached zones")
                Resource.Success(database.zoneDao().getAllZones())
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching zones: ${e.message}, falling back to cache")
            // Fallback to local DB on network error
            try {
                Resource.Success(database.zoneDao().getAllZones())
            } catch (dbEx: Exception) {
                Resource.Error(dbEx.message ?: "Failed to load zones")
            }
        }
    }

    // ============================================
// FETCH DIVISIONS BY ZONE FROM SERVER
// ============================================
    suspend fun fetchDivisionsByZone(zoneId: Long): Resource<List<DivisionEntity>> = withContext(Dispatchers.IO) {
        try {
            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            if (token.isEmpty()) return@withContext Resource.Error("No auth token")

            Log.d(TAG, "🔄 Fetching divisions for zoneId=$zoneId...")
            val response = serverApi.getDivisions("Bearer $token", zoneId)

            if (response.isSuccessful && response.body() != null) {
                // ✅ SAFETY: Make sure parent zone exists before inserting children
                val zoneExists = database.zoneDao().getZoneById(zoneId) != null
                if (!zoneExists) {
                    Log.w(TAG, "⚠️ Zone $zoneId not in DB, skipping cache insert")
                    // Return server data without caching (still works for the UI)
                    val divisionEntities = response.body()!!.map { dto ->
                        DivisionEntity(
                            divisionId = dto.divisionId,
                            zoneId = dto.zoneId,
                            divisionName = dto.divisionName
                        )
                    }
                    return@withContext Resource.Success(divisionEntities)
                }

                val divisionEntities = response.body()!!.map { dto ->
                    DivisionEntity(
                        divisionId = dto.divisionId,
                        zoneId = dto.zoneId,
                        divisionName = dto.divisionName
                    )
                }
                database.divisionDao().insertDivisions(divisionEntities)
                Log.d(TAG, "✅ Fetched ${divisionEntities.size} divisions")
                Resource.Success(divisionEntities)
            } else {
                Log.w(TAG, "⚠️ Server returned ${response.code()}, using cached")
                Resource.Success(database.divisionDao().getDivisionsByZone(zoneId))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching divisions: ${e.message}, falling back to cache")
            try {
                Resource.Success(database.divisionDao().getDivisionsByZone(zoneId))
            } catch (dbEx: Exception) {
                Resource.Error(dbEx.message ?: "Failed to load divisions")
            }
        }
    }
    // ============================================
// FETCH SECTIONS BY DIVISION FROM SERVER
// ============================================
    suspend fun fetchSectionsByDivision(divisionId: Long): Resource<List<SectionEntity>> = withContext(Dispatchers.IO) {
        try {
            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            if (token.isEmpty()) return@withContext Resource.Error("No auth token")

            Log.d(TAG, "🔄 Fetching sections for divisionId=$divisionId...")
            val response = serverApi.getSections("Bearer $token", divisionId)

            if (response.isSuccessful && response.body() != null) {
                val sectionEntities = response.body()!!.map { dto ->
                    SectionEntity(
                        sectionId = dto.sectionId,
                        divisionId = dto.divisionId,
                        sectionName = dto.sectionName
                    )
                }
                database.sectionDao().insertSections(sectionEntities)
                Log.d(TAG, "✅ Fetched ${sectionEntities.size} sections")
                Resource.Success(sectionEntities)
            } else {
                Log.w(TAG, "⚠️ Server returned ${response.code()}, using cached")
                Resource.Success(database.sectionDao().getSectionsByDivision(divisionId))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching sections: ${e.message}")
            try {
                Resource.Success(database.sectionDao().getSectionsByDivision(divisionId))
            } catch (dbEx: Exception) {
                Resource.Error(dbEx.message ?: "Failed to load sections")
            }
        }
    }

    // ============================================
// FETCH FARMS BY SECTION FROM SERVER
// ============================================
    suspend fun fetchFarmsBySection(sectionId: Long): Resource<List<FarmEntity>> = withContext(Dispatchers.IO) {
        try {
            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            if (token.isEmpty()) return@withContext Resource.Error("No auth token")

            Log.d(TAG, "🔄 Fetching farms for sectionId=$sectionId...")
            val response = serverApi.getFarms("Bearer $token", sectionId)

            if (response.isSuccessful && response.body() != null) {
                val farmEntities = response.body()!!.map { dto ->
                    FarmEntity(
                        farmId = dto.farmId,
                        sectionId = dto.sectionId,
                        farmName = dto.farmName
                    )
                }
                database.farmDao().insertFarms(farmEntities)
                Log.d(TAG, "✅ Fetched ${farmEntities.size} farms")
                Resource.Success(farmEntities)
            } else {
                Log.w(TAG, "⚠️ Server returned ${response.code()}, using cached")
                Resource.Success(database.farmDao().getFarmsBySection(sectionId))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching farms: ${e.message}")
            try {
                Resource.Success(database.farmDao().getFarmsBySection(sectionId))
            } catch (dbEx: Exception) {
                Resource.Error(dbEx.message ?: "Failed to load farms")
            }
        }
    }

    // ============================================
// FETCH BLOCKS BY FARM FROM SERVER
// ============================================
    suspend fun fetchBlocksByFarm(farmId: Long): Resource<List<BlockEntity>> = withContext(Dispatchers.IO) {
        try {
            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            if (token.isEmpty()) return@withContext Resource.Error("No auth token")

            Log.d(TAG, "🔄 Fetching blocks for farmId=$farmId...")
            val response = serverApi.getBlocks("Bearer $token", farmId)

            if (response.isSuccessful && response.body() != null) {
                val blockEntities = response.body()!!.map { dto ->
                    BlockEntity(
                        blockId = dto.blockId,
                        farmId = dto.farmId,
                        blockName = dto.blockName
                    )
                }
                database.blockDao().insertBlocks(blockEntities)
                Log.d(TAG, "✅ Fetched ${blockEntities.size} blocks")
                Resource.Success(blockEntities)
            } else {
                Log.w(TAG, "⚠️ Server returned ${response.code()}, using cached")
                Resource.Success(database.blockDao().getBlocksByFarm(farmId))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching blocks: ${e.message}")
            try {
                Resource.Success(database.blockDao().getBlocksByFarm(farmId))
            } catch (dbEx: Exception) {
                Resource.Error(dbEx.message ?: "Failed to load blocks")
            }
        }
    }

    // ============================================
// FETCH PLOTS BY BLOCK FROM SERVER
// ============================================
    suspend fun fetchPlotsByBlock(blockId: Long): Resource<List<PlotEntity>> = withContext(Dispatchers.IO) {
        try {
            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            if (token.isEmpty()) return@withContext Resource.Error("No auth token")

            Log.d(TAG, "🔄 Fetching plots for blockId=$blockId...")
            val response = serverApi.getPlots("Bearer $token", blockId)

            if (response.isSuccessful && response.body() != null) {
                val plotEntities = response.body()!!.map { dto ->
                    PlotEntity(
                        plotId = dto.plotId,
                        blockId = dto.blockId,
                        plotName = dto.plotName
                    )
                }
                database.plotDao().insertPlots(plotEntities)
                Log.d(TAG, "✅ Fetched ${plotEntities.size} plots")
                Resource.Success(plotEntities)
            } else {
                Log.w(TAG, "⚠️ Server returned ${response.code()}, using cached")
                Resource.Success(database.plotDao().getPlotsByBlock(blockId))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching plots: ${e.message}")
            try {
                Resource.Success(database.plotDao().getPlotsByBlock(blockId))
            } catch (dbEx: Exception) {
                Resource.Error(dbEx.message ?: "Failed to load plots")
            }
        }
    }
}

sealed class AddVarietyResult {
    object Success : AddVarietyResult()
    object AlreadyExists : AddVarietyResult()
    object SavedOffline : AddVarietyResult()
    data class Error(val message: String) : AddVarietyResult()
}

sealed class Resource<T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error<T>(val message: String) : Resource<T>()
    class Loading<T> : Resource<T>()
}
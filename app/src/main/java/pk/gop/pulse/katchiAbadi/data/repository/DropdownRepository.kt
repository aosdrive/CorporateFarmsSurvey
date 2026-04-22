package pk.gop.pulse.katchiAbadi.data.repository

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.data.local.AddVarietyRequest
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.domain.model.BlockEntity
import pk.gop.pulse.katchiAbadi.domain.model.CropEntity
import pk.gop.pulse.katchiAbadi.domain.model.CropTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.CropVarietyEntity
import pk.gop.pulse.katchiAbadi.domain.model.DivisionEntity
import pk.gop.pulse.katchiAbadi.domain.model.FarmEntity
import pk.gop.pulse.katchiAbadi.domain.model.PlotEntity
import pk.gop.pulse.katchiAbadi.domain.model.SectionEntity
import pk.gop.pulse.katchiAbadi.domain.model.ZoneEntity
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

    // ===== ZONES =====
    suspend fun getZones(): List<String> = withContext(Dispatchers.IO) {
        try { database.zoneDao().getAllZones().map { it.zoneName } }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting zones: ${e.message}"); emptyList() }
    }

    suspend fun getZoneEntities(): List<ZoneEntity> = withContext(Dispatchers.IO) {
        try { database.zoneDao().getAllZones() }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting zone entities: ${e.message}"); emptyList() }
    }

    suspend fun getZoneById(zoneId: Long): ZoneEntity? = withContext(Dispatchers.IO) {
        try { database.zoneDao().getZoneById(zoneId) }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting zone: ${e.message}"); null }
    }

    suspend fun getZoneByName(name: String): ZoneEntity? = withContext(Dispatchers.IO) {
        try { database.zoneDao().getAllZones().firstOrNull { it.zoneName == name } }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting zone by name: ${e.message}"); null }
    }

    // ===== DIVISIONS =====
    suspend fun getDivisionsByZone(zoneId: Long): List<String> = withContext(Dispatchers.IO) {
        try { database.divisionDao().getDivisionsByZone(zoneId).map { it.divisionName } }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting divisions: ${e.message}"); emptyList() }
    }

    suspend fun getDivisionEntitiesByZone(zoneId: Long): List<DivisionEntity> = withContext(Dispatchers.IO) {
        try { database.divisionDao().getDivisionsByZone(zoneId) }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting division entities: ${e.message}"); emptyList() }
    }

    suspend fun getDivisionById(divisionId: Long): DivisionEntity? = withContext(Dispatchers.IO) {
        try { database.divisionDao().getDivisionById(divisionId) }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting division: ${e.message}"); null }
    }

    suspend fun getDivisionByName(name: String): DivisionEntity? = withContext(Dispatchers.IO) {
        try { database.divisionDao().getAllDivisions().firstOrNull { it.divisionName == name } }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting division by name: ${e.message}"); null }
    }

    // ===== SECTIONS =====
    suspend fun getSectionsByDivision(divisionId: Long): List<String> = withContext(Dispatchers.IO) {
        try { database.sectionDao().getSectionsByDivision(divisionId).map { it.sectionName } }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting sections: ${e.message}"); emptyList() }
    }

    suspend fun getSectionEntitiesByDivision(divisionId: Long): List<SectionEntity> = withContext(Dispatchers.IO) {
        try { database.sectionDao().getSectionsByDivision(divisionId) }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting section entities: ${e.message}"); emptyList() }
    }

    suspend fun getSectionById(sectionId: Long): SectionEntity? = withContext(Dispatchers.IO) {
        try { database.sectionDao().getSectionById(sectionId) }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting section: ${e.message}"); null }
    }

    suspend fun getSectionByName(name: String): SectionEntity? = withContext(Dispatchers.IO) {
        try { database.sectionDao().getAllSections().firstOrNull { it.sectionName == name } }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting section by name: ${e.message}"); null }
    }

    // ===== FARMS =====
    suspend fun getFarmEntitiesBySection(sectionId: Long): List<FarmEntity> = withContext(Dispatchers.IO) {
        try { database.farmDao().getFarmsBySection(sectionId) }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting farms: ${e.message}"); emptyList() }
    }

    suspend fun getFarmById(farmId: Long): FarmEntity? = withContext(Dispatchers.IO) {
        try { database.farmDao().getFarmById(farmId) }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting farm: ${e.message}"); null }
    }

    // ===== BLOCKS =====
    suspend fun getBlocksByFarm(farmId: Long): List<String> = withContext(Dispatchers.IO) {
        try { database.blockDao().getBlocksByFarm(farmId).map { it.blockName } }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting blocks: ${e.message}"); emptyList() }
    }

    suspend fun getBlockEntitiesByFarm(farmId: Long): List<BlockEntity> = withContext(Dispatchers.IO) {
        try { database.blockDao().getBlocksByFarm(farmId) }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting block entities: ${e.message}"); emptyList() }
    }

    suspend fun getBlockById(blockId: Long): BlockEntity? = withContext(Dispatchers.IO) {
        try { database.blockDao().getBlockById(blockId) }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting block: ${e.message}"); null }
    }

    suspend fun getBlockByName(name: String): BlockEntity? = withContext(Dispatchers.IO) {
        try { database.blockDao().getAllBlocks().firstOrNull { it.blockName == name } }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting block by name: ${e.message}"); null }
    }

    // ===== PLOTS =====
    suspend fun getPlotEntitiesByBlock(blockId: Long): List<PlotEntity> = withContext(Dispatchers.IO) {
        try { database.plotDao().getPlotsByBlock(blockId) }
        catch (e: Exception) { Log.e(TAG, "❌ Error getting plots: ${e.message}"); emptyList() }
    }

    // ============================================
    // CASCADE DROPDOWN SYNC - ✅ FIXED
    // ============================================
    suspend fun syncDropdownData(): Resource<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            if (token.isEmpty()) {
                Log.e(TAG, "❌ No authentication token available")
                return@withContext Resource.Error("No authentication token available")
            }

            Log.d(TAG, "🔄 Starting cascade dropdown sync from server...")

            val response = serverApi.getZoneDivisionSectionBlocks("Bearer $token")

            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!

                Log.d(TAG, "✅ Server Response Received:")
                Log.d(TAG, "   Zones: ${data.zones.size}")
                Log.d(TAG, "   Divisions: ${data.divisions.size}")
                Log.d(TAG, "   Sections: ${data.sections.size}")
                Log.d(TAG, "   Farms: ${data.farms.size}")
                Log.d(TAG, "   Blocks: ${data.blocks.size}")
                Log.d(TAG, "   Plots: ${data.plots.size}")

                try {
                    // ===== INSERT ZONES =====
                    val zoneEntities = data.zones.map { dto ->
                        Log.d(TAG, "Zone DTO: zoneId=${dto.zoneId}, zoneName=${dto.zoneName}")
                        ZoneEntity(
                            zoneId = dto.zoneId,
                            zoneName = dto.zoneName
                        )
                    }
                    database.zoneDao().insertZones(zoneEntities)
                    Log.d(TAG, "✅ Inserted ${zoneEntities.size} zones")

                    // ===== INSERT DIVISIONS =====
                    val divisionEntities = data.divisions.mapNotNull { dto ->
                        Log.d(TAG, "Division DTO: divisionId=${dto.divisionId}, zoneId=${dto.zoneId}, divisionName=${dto.divisionName}")

                        // ✅ FIX: Verify parent zone exists by server ID
                        val zoneExists = database.zoneDao().getZoneById(dto.zoneId) != null

                        if (zoneExists) {
                            DivisionEntity(
                                divisionId = dto.divisionId,
                                zoneId = dto.zoneId,  // ✅ Use server zone ID
                                divisionName = dto.divisionName
                            ).also {
                                Log.d(TAG, "   ✓ Created division: ${it.divisionId}")
                            }
                        } else {
                            Log.w(TAG, "⚠️ Zone not found for division: ${dto.divisionName} (zoneId: ${dto.zoneId})")
                            null
                        }
                    }
                    database.divisionDao().insertDivisions(divisionEntities)
                    Log.d(TAG, "✅ Inserted ${divisionEntities.size} divisions")

                    // ===== INSERT SECTIONS =====
                    val sectionEntities = data.sections.mapNotNull { dto ->
                        Log.d(TAG, "Section DTO: sectionId=${dto.sectionId}, divisionId=${dto.divisionId}, sectionName=${dto.sectionName}")

                        // ✅ FIX: Verify parent division exists by server ID
                        val divisionExists = database.divisionDao().getDivisionById(dto.divisionId) != null

                        if (divisionExists) {
                            SectionEntity(
                                sectionId = dto.sectionId,
                                divisionId = dto.divisionId,  // ✅ Use server division ID
                                sectionName = dto.sectionName
                            ).also {
                                Log.d(TAG, "   ✓ Created section: ${it.sectionId}")
                            }
                        } else {
                            Log.w(TAG, "⚠️ Division not found for section: ${dto.sectionName} (divisionId: ${dto.divisionId})")
                            null
                        }
                    }
                    database.sectionDao().insertSections(sectionEntities)
                    Log.d(TAG, "✅ Inserted ${sectionEntities.size} sections")

                    // ===== INSERT FARMS =====
                    val farmEntities = data.farms.mapNotNull { dto ->
                        Log.d(TAG, "Farm DTO: farmId=${dto.farmId}, sectionId=${dto.sectionId}, farmName=${dto.farmName}")

                        // ✅ FIX: Verify parent section exists by server ID
                        val sectionExists = database.sectionDao().getSectionById(dto.sectionId) != null

                        if (sectionExists) {
                            FarmEntity(
                                farmId = dto.farmId,
                                sectionId = dto.sectionId,  // ✅ Use server section ID
                                farmName = dto.farmName
                            ).also {
                                Log.d(TAG, "   ✓ Created farm: ${it.farmId}")
                            }
                        } else {
                            Log.w(TAG, "⚠️ Section not found for farm: ${dto.farmName} (sectionId: ${dto.sectionId})")
                            null
                        }
                    }
                    database.farmDao().insertFarms(farmEntities)
                    Log.d(TAG, "✅ Inserted ${farmEntities.size} farms")

                    // ===== INSERT BLOCKS =====
                    val blockEntities = data.blocks.mapNotNull { dto ->
                        Log.d(TAG, "Block DTO: blockId=${dto.blockId}, farmId=${dto.farmId}, blockName=${dto.blockName}")

                        // ✅ FIX: Verify parent farm exists by server ID
                        val farmExists = database.farmDao().getFarmById(dto.farmId) != null

                        if (farmExists) {
                            BlockEntity(
                                blockId = dto.blockId,
                                farmId = dto.farmId,  // ✅ Use server farm ID
                                blockName = dto.blockName
                            ).also {
                                Log.d(TAG, "   ✓ Created block: ${it.blockId}")
                            }
                        } else {
                            Log.w(TAG, "⚠️ Farm not found for block: ${dto.blockName} (farmId: ${dto.farmId})")
                            null
                        }
                    }
                    database.blockDao().insertBlocks(blockEntities)
                    Log.d(TAG, "✅ Inserted ${blockEntities.size} blocks")

                    // ===== INSERT PLOTS =====
                    val plotEntities = data.plots.mapNotNull { dto ->
                        Log.d(TAG, "Plot DTO: plotId=${dto.plotId}, blockId=${dto.blockId}, plotName=${dto.plotName}")

                        // ✅ FIX: Verify parent block exists by server ID
                        val blockExists = database.blockDao().getBlockById(dto.blockId) != null

                        if (blockExists) {
                            PlotEntity(
                                plotId = dto.plotId,
                                blockId = dto.blockId,  // ✅ Use server block ID
                                plotName = dto.plotName
                            ).also {
                                Log.d(TAG, "   ✓ Created plot: ${it.plotId}")
                            }
                        } else {
                            Log.w(TAG, "⚠️ Block not found for plot: ${dto.plotName} (blockId: ${dto.blockId})")
                            null
                        }
                    }
                    database.plotDao().insertPlots(plotEntities)
                    Log.d(TAG, "✅ Inserted ${plotEntities.size} plots")

                    // Update last sync time
                    sharedPreferences.edit()
                        .putLong(Constants.SHARED_PREF_DROPDOWN_LAST_SYNC, System.currentTimeMillis())
                        .apply()

                    Log.d(TAG, "✅✅✅ Cascade dropdown sync completed successfully! ✅✅✅")
                    Resource.Success("Dropdown data synced successfully")

                } catch (dbException: Exception) {
                    Log.e(TAG, "❌ Database insert error: ${dbException.message}", dbException)
                    Resource.Error("Database error: ${dbException.message}")
                }

            } else {
                val errorCode = response.code()
                val errorBody = response.errorBody()?.string() ?: "No error body"
                Log.e(TAG, "❌ Server error: $errorCode - $errorBody")
                Resource.Error("Server error: $errorCode")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Sync error: ${e.message}", e)
            Resource.Error(e.message ?: "Unknown error during sync")
        }
    }

    fun needsDropdownSync(): Boolean {
        val lastSync = sharedPreferences.getLong(Constants.SHARED_PREF_DROPDOWN_LAST_SYNC, 0L)
        val now = System.currentTimeMillis()
        val needsSync = (now - lastSync) > CACHE_DURATION_MS
        Log.d(TAG, "Cache check: lastSync=$lastSync, now=$now, needsSync=$needsSync")
        return needsSync
    }

    suspend fun syncIfNeeded(): Resource<String> {
        return if (needsDropdownSync()) {
            Log.d(TAG, "⏰ Cache expired, syncing cascade dropdown data...")
            syncDropdownData()
        } else {
            Log.d(TAG, "✅ Cache still valid, using local data")
            Resource.Success("Using cached data")
        }
    }

    data class DropdownData(
        val zones: List<ZoneEntity> = emptyList(),
        val divisions: List<DivisionEntity> = emptyList(),
        val sections: List<SectionEntity> = emptyList(),
        val blocks: List<BlockEntity> = emptyList()
    )

    suspend fun loadAllDropdownData(): DropdownData = withContext(Dispatchers.IO) {
        try {
            val zones = database.zoneDao().getAllZones()
            val divisions = database.divisionDao().getAllDivisions()
            val sections = database.sectionDao().getAllSections()
            val blocks = database.blockDao().getAllBlocks()

            DropdownData(zones, divisions, sections, blocks)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading all dropdown data: ${e.message}")
            DropdownData()
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
package com.gop.survey.corporatefarm.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.esri.arcgisruntime.geometry.AreaUnit
import com.esri.arcgisruntime.geometry.AreaUnitId
import com.esri.arcgisruntime.geometry.GeodeticCurveType
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.gop.survey.corporatefarm.common.Constants
import com.gop.survey.corporatefarm.common.Resource
import com.gop.survey.corporatefarm.common.Utility
import com.gop.survey.corporatefarm.data.local.ActiveParcelDao
import com.gop.survey.corporatefarm.data.local.SowingPersonDao
import com.gop.survey.corporatefarm.data.local.SurveyLessorDao
import com.gop.survey.corporatefarm.data.remote.ServerApi
import com.gop.survey.corporatefarm.data.remote.post.Pictures
import com.gop.survey.corporatefarm.data.remote.response.NewSurveyNewDao
import com.gop.survey.corporatefarm.data.remote.response.SurveyImageDao
import com.gop.survey.corporatefarm.data.remote.response.SurveyPersonDao
import com.gop.survey.corporatefarm.domain.model.ActiveParcelEntity
import com.gop.survey.corporatefarm.domain.model.CorporateParcelPost
import com.gop.survey.corporatefarm.domain.model.LessorPostDto
import com.gop.survey.corporatefarm.domain.model.NewSurveyNewEntity
import com.gop.survey.corporatefarm.domain.model.SowingPersonPostDto
import com.gop.survey.corporatefarm.domain.model.SurveyImage
import com.gop.survey.corporatefarm.domain.model.SurveyPersonEntity
import com.gop.survey.corporatefarm.domain.model.SurveyPersonPost
import com.gop.survey.corporatefarm.domain.repository.NewSurveyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import javax.inject.Inject

private const val TAG = "SurveyRepository"

/**
 * Every parcel in this app is drawn by the surveyor, so every upload is a "New" parcel.
 * Split / Merge / MultiMerge no longer exist.
 */
class NewSurveyRepositoryImpl @Inject constructor(
    private val dao: NewSurveyNewDao,
    private val imageDao: SurveyImageDao,
    private val personDao: SurveyPersonDao,
    private val sowingPersonDao: SowingPersonDao,
    private val lessorDao: SurveyLessorDao,
    private val api: ServerApi,
    private val sharedPreferences: SharedPreferences,
    private val activeParcelDao: ActiveParcelDao
) : NewSurveyRepository {

    private val wgs84 by lazy { SpatialReferences.getWgs84() }

    // ==================================================================
    // READS
    // ==================================================================

    override suspend fun getAllSurveys(): List<NewSurveyNewEntity> = dao.getAllSurveys()

    override suspend fun getActiveParcelById(parcelId: Long): ActiveParcelEntity? =
        activeParcelDao.getParcelById(parcelId)

    override fun getAllPendingSurveys(): Flow<List<NewSurveyNewEntity>> =
        dao.getAllPendingSurveys()

    override fun getTotalPendingCount(): Flow<Int> = dao.liveTotalPendingCount()

    override suspend fun getOnePendingSurvey(): NewSurveyNewEntity? = dao.getOnePendingSurvey()

    override suspend fun getSurveyById(id: Long): NewSurveyNewEntity? = try {
        dao.getSurveyById(id)
    } catch (e: Exception) {
        null
    }

    override suspend fun getPersonsForSurvey(surveyId: Long): List<SurveyPersonEntity> =
        personDao.getPersonsForSurvey(surveyId)

    // ==================================================================
    // DELETE
    // ==================================================================

    override suspend fun deleteSurvey(survey: NewSurveyNewEntity): Resource<Unit> {
        return try {
            Log.d(TAG, "Deleting survey: parcelId=${survey.parcelId}, parcelNo=${survey.parcelNo}")

            dao.deleteSurvey(survey)

            // If no survey is left for this parcel, mark it unsurveyed again
            val remaining = dao.getSurveysByParcelId(survey.parcelId)
            if (remaining.isEmpty()) {
                activeParcelDao.updateSurveyStatus(survey.parcelId, 1)
                Log.d(TAG, "Reset parcel ${survey.parcelId} to unsurveyed")
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed: ${e.message}", e)
            Resource.Error(e.message ?: "The survey could not be deleted.")
        }
    }

    // ==================================================================
    // UPLOAD
    // ==================================================================

    override suspend fun uploadSurvey(
        context: Context,
        survey: NewSurveyNewEntity
    ): Resource<Unit> {
        return try {
            Log.d(TAG, "=== uploadSurvey START ===")
            Log.d(TAG, "parcelId=${survey.parcelId}, parcelNo=${survey.parcelNo}")

            // Every parcel is locally drawn -> always "New"
            val toUpload = survey.copy(
                parcelOperation = "New",
                parcelOperationValue = "Drawn"
            )

            val posts = withContext(Dispatchers.IO) {
                buildCorporateSurveyPosts(context, toUpload)
            }

            // Geometry is mandatory for a new parcel
            val invalid = posts.firstOrNull { it.geomWKT.isNullOrBlank() }
            if (invalid != null) {
                return Resource.Error(
                    "Parcel ${invalid.parcelNo} has no geometry, so it cannot be uploaded."
                )
            }

            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            Log.d(TAG, "Uploading ${posts.size} record(s)")

            val response = api.postCorporateSurveyData("Bearer $token", posts)

            val errorBodyStr = try {
                if (!response.isSuccessful) response.errorBody()?.string() ?: "" else ""
            } catch (e: Exception) {
                "Could not read error body: ${e.message}"
            }

            when {
                response.isSuccessful && response.body() != null -> {
                    Log.i(TAG, "Upload successful")

                    withContext(Dispatchers.IO) {
                        if (survey.pkId > 0) {
                            dao.markAsUploaded(survey.pkId)
                            lessorDao.markSurveyLessorsSynced(survey.pkId)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        try {
                            androidx.localbroadcastmanager.content.LocalBroadcastManager
                                .getInstance(context)
                                .sendBroadcast(android.content.Intent("REFRESH_MAP"))
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to send refresh broadcast: ${e.message}")
                        }
                    }

                    Resource.Success(Unit)
                }

                response.code() == 401 -> handleUnauthorizedResponse(errorBodyStr)

                else -> {
                    Log.e(TAG, "Upload failed (${response.code()}): $errorBodyStr")
                    Resource.Error("Server error (${response.code()}): $errorBodyStr")
                }
            }
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: e.message()
            Log.e(TAG, "HttpException ${e.code()}: $errorBody", e)
            Resource.Error(
                if (errorBody.contains("isUpdateRequired", ignoreCase = true))
                    "APP_VERSION_OUTDATED: Please update your app."
                else
                    "Server error: $errorBody"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception during upload", e)
            Resource.Error(e.localizedMessage ?: "An unexpected error occurred during upload.")
        }
    }

    // ==================================================================
    // BUILD POST
    // ==================================================================

    private suspend fun buildCorporateSurveyPosts(
        context: Context,
        survey: NewSurveyNewEntity
    ): List<CorporateParcelPost> {

        val pkId = survey.pkId
        val isSurveyed = pkId > 0 && survey.propertyType.isNotBlank()

        val images = if (isSurveyed) imageDao.getImagesBySurvey(pkId) else emptyList()
        val pictures = convertSurveyImagesToPictures(context, images)

        val lessorEntities = if (isSurveyed) lessorDao.getLessorsForSurvey(pkId) else emptyList()
        val lessorDtos = lessorEntities.map { l ->
            LessorPostDto(
                lessorId = l.lessorId,
                lessorName = l.lessorName,
                lessorCode = l.lessorCode,
                lessorType = l.lessorType
            )
        }

        val sowingPersonDtos =
            (if (isSurveyed) sowingPersonDao.getBySurveyId(pkId) else emptyList()).map { sp ->
                SowingPersonPostDto(
                    name = sp.name,
                    CNIC = sp.cnic,
                    growerCode = sp.growerCode ?: ""
                )
            }

        val parcel = activeParcelDao.getParcelById(survey.parcelId)
        val geomWKT = parcel?.geomWKT.orEmpty()
        val centroid = parcel?.centroid.orEmpty()
        val calculatedArea = calculateAreaFromGeometry(geomWKT)

        // Tehsil is stored in areaAssigned for locally drawn parcels
        val tehsil = parcel?.areaAssigned?.takeIf { it.isNotBlank() }
            ?: survey.areaName

        Log.d(TAG, "=== POST DATA ===")
        Log.d(TAG, "ParcelNo=${survey.parcelNo}, Tehsil=$tehsil, Area=$calculatedArea Acres")
        Log.d(TAG, "Geometry: ${if (geomWKT.isEmpty()) "MISSING" else "${geomWKT.length} chars"}")
        Log.d(TAG, "Lessors=${lessorDtos.size}, Pictures=${pictures.size}")

        val post = CorporateParcelPost(
            parcelId = 0L,                       // server creates a new row
            parcelNo = survey.parcelNo,
            subParcelNo = "",
            mauzaId = survey.mauzaId,
            parcelOperation = "New",
            parcelOperationValue = "Drawn",      // tells the server this is a drawn parcel
            geomWKT = geomWKT.ifEmpty { null },
            centriod = centroid.ifEmpty { null },
            calculatedArea = calculatedArea,
            isGeometryCorrect = survey.isGeometryCorrect,
            distance = 100,
            khewatInfo = "0",
            areaAssigned = tehsil,
            parcelAreaKMF = "",
            areaName = tehsil,                   // server uses this as the Tehsil name
            propertyType = survey.propertyType,
            ownershipStatus = survey.ownershipStatus,
            variety = survey.variety,
            cropType = survey.cropType,
            crop = survey.crop,
            year = survey.year,
            area = survey.area,
            remarks = survey.remarks,
            sowingStatus = survey.sowingStatus ?: "No",
            sowingDate = survey.sowingDate,
            zone = survey.zone,
            division = survey.division,
            section = survey.section,
            farm = survey.farm,
            block = survey.block,
            plot = survey.plot,
            lessorName = lessorDtos.firstOrNull()?.lessorName,
            persons = emptyList<SurveyPersonPost>(),
            pictures = pictures,
            sowingPersons = sowingPersonDtos,
            lessors = lessorDtos,
            irrigationSource = survey.irrigationSource,
            irrigationSourceQuantity = survey.irrigationSourceQuantity
        )

        return listOf(post)
    }

    // ==================================================================
    // HELPERS
    // ==================================================================

    private suspend fun calculateAreaFromGeometry(geomWKT: String?): String =
        withContext(Dispatchers.Default) {
            try {
                if (geomWKT.isNullOrEmpty()) return@withContext ""

                val polygon = when {
                    geomWKT.contains("MULTIPOLYGON") ->
                        Utility.getMultiPolygonFromString(geomWKT, wgs84).firstOrNull()

                    geomWKT.contains("POLYGON") ->
                        Utility.getPolygonFromString(geomWKT, wgs84)

                    else -> null
                }

                if (polygon != null && !polygon.isEmpty) {
                    val areaSqFt = GeometryEngine.areaGeodetic(
                        polygon,
                        AreaUnit(AreaUnitId.SQUARE_FEET),
                        GeodeticCurveType.NORMAL_SECTION
                    )
                    String.format(Locale.US, "%.4f", areaSqFt / 43560.0)
                } else {
                    Log.e(TAG, "Failed to parse geometry for area calculation")
                    ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating area: ${e.message}", e)
                ""
            }
        }

    private fun convertSurveyImagesToPictures(
        context: Context,
        images: List<SurveyImage>
    ): List<Pictures> = images.map { img ->
        val base64Encoded = try {
            val uri = Uri.parse(img.uri)
            val inputStream = when (uri.scheme) {
                "content" -> context.contentResolver.openInputStream(uri)
                "file" -> File(uri.path ?: "").inputStream()
                null -> File(img.uri).inputStream()
                else -> null
            }

            inputStream?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { bmp ->
                    ByteArrayOutputStream().use { os ->
                        var quality = 100
                        do {
                            os.reset()
                            bmp.compress(Bitmap.CompressFormat.JPEG, quality, os)
                            quality -= 5
                        } while (os.size() / 1024 > 200 && quality > 75)
                        Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP)
                    }
                } ?: "Image decoding failed"
            } ?: "InputStream not found"
        } catch (e: Exception) {
            "Image not found: ${e.localizedMessage}"
        }

        Pictures(
            Number = img.id.toInt(),
            Type = img.type,
            PicData = base64Encoded,
            OtherType = img.type,
            Latitude = img.latitude,
            Longitude = img.longitude,
            Timestamp = img.timestamp,
            LocationAddress = img.locationAddress,
            Bearing = img.bearing
        )
    }

    private fun handleUnauthorizedResponse(errorBody: String): Resource<Unit> {
        Log.e(TAG, "401 Unauthorized: $errorBody")

        try {
            val errorMap = com.google.gson.Gson().fromJson(errorBody, Map::class.java)
            val isUpdateRequired = errorMap["isUpdateRequired"] as? Boolean ?: false
            val shouldLogout = errorMap["shouldLogout"] as? Boolean ?: false
            val message = errorMap["message"] as? String ?: ""

            if (isUpdateRequired || shouldLogout) {
                sharedPreferences.edit().clear().apply()
                return Resource.Error("APP_VERSION_OUTDATED: $message")
            }
        } catch (parseError: Exception) {
            Log.e(TAG, "Error parsing 401 response: ${parseError.message}")
        }

        if (errorBody.contains("outdated version", ignoreCase = true) ||
            errorBody.contains("isUpdateRequired", ignoreCase = true) ||
            errorBody.contains("shouldLogout", ignoreCase = true) ||
            errorBody.contains("App version header", ignoreCase = true)
        ) {
            sharedPreferences.edit().clear().apply()
            return Resource.Error(
                "APP_VERSION_OUTDATED: You are using an outdated version. Please contact the administrator for the latest app."
            )
        }

        return Resource.Error("Unauthorized: $errorBody")
    }
}
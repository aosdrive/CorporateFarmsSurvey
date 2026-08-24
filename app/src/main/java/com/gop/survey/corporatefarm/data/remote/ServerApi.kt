package com.gop.survey.corporatefarm.data.remote

import AreaResponse
import okhttp3.ResponseBody
import com.gop.survey.corporatefarm.data.local.AddVarietyRequest
import com.gop.survey.corporatefarm.data.local.AddVarietyResponse
import com.gop.survey.corporatefarm.data.local.DropdownItem
import com.gop.survey.corporatefarm.data.remote.post.RetakePicturesPost
import com.gop.survey.corporatefarm.data.remote.post.SurveyPost
import com.gop.survey.corporatefarm.data.remote.request.LoginRequest
import com.gop.survey.corporatefarm.data.remote.response.BasicApiDto
import com.gop.survey.corporatefarm.data.remote.response.BasicInfoDto
import com.gop.survey.corporatefarm.data.remote.response.Info
import com.gop.survey.corporatefarm.data.remote.response.KachiAbadiDto
import com.gop.survey.corporatefarm.data.remote.response.KatchiAbadiApiDto
import com.gop.survey.corporatefarm.data.remote.response.LoginDto
import com.gop.survey.corporatefarm.data.remote.response.LoginSurveyorResponse
import com.gop.survey.corporatefarm.data.remote.response.LogoutResponse
import com.gop.survey.corporatefarm.data.remote.response.MauzaSyncResponse
import com.gop.survey.corporatefarm.data.remote.response.MouzaAssignedDto
import com.gop.survey.corporatefarm.data.remote.response.BlockDto
import com.gop.survey.corporatefarm.data.remote.response.DivisionDto
import com.gop.survey.corporatefarm.data.remote.response.FarmDto
import com.gop.survey.corporatefarm.data.remote.response.PlotDto
import com.gop.survey.corporatefarm.data.remote.response.PostApiDto
import com.gop.survey.corporatefarm.data.remote.response.ResponseDto
import com.gop.survey.corporatefarm.data.remote.response.SectionDto
import com.gop.survey.corporatefarm.data.remote.response.VersionCheckResponse
import com.gop.survey.corporatefarm.data.remote.response.ZoneDivisionSectionBlockResponse
import com.gop.survey.corporatefarm.data.remote.response.ZoneDto
import com.gop.survey.corporatefarm.domain.model.CorporateParcelPost
import com.gop.survey.corporatefarm.domain.model.LessorEntity
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface ServerApi {

    @POST
    suspend fun login(
        @Url url: String,
        @Body json: LoginRequest
    ): BasicApiDto<LoginDto>

    @POST
    suspend fun loginSurveyor(
        @Url url: String,
        @Body json: LoginRequest
    ): LoginSurveyorResponse

    @POST
    suspend fun forgotPassword(
        @Url url: String,
        @Query("userName") userName: String,
    ): ResponseDto

    @GET
    suspend fun otpVerification(
        @Url url: String,
        @Query("userName") userName: String,
        @Query("userProvidedTotp") userProvidedTotp: String,
    ): ResponseDto

    @PUT
    suspend fun updatePassword(
        @Url url: String,
        @Query("userName") userName: String,
        @Query("updatedPassword") updatedPassword: String,
    ): ResponseDto

    @POST
    @Headers("Content-Type: application/json")
    suspend fun mouzaAssignedData(
        @Url url: String,
        @Body id: Long
    ): BasicInfoDto<MouzaAssignedDto, Info>

    @GET
    @Headers("Content-Type: application/json")
    suspend fun getMauzaSyncInfo(
        @Url url: String,
        @Header("Authorization") token: String
    ): MauzaSyncResponse

    @POST
    @Headers("Content-Type: application/json")
    suspend fun syncData(
        @Url url: String,
        @Body id: Long
    ): KatchiAbadiApiDto<KachiAbadiDto>

    @POST
    @Headers("Content-Type: application/json")
    suspend fun postSurveyData(

        @Url url: String,
        @Body json: SurveyPost
    ): PostApiDto

    @POST("api/MobileDataCorporate/AddCorporateSurveyData")
    @Headers("Content-Type: application/json")
    suspend fun postCorporateSurveyData(
        @Header("Authorization") token: String,
        @Body json: List<CorporateParcelPost>
    ): Response<PostApiDto>

    @POST
    @Headers("Content-Type: application/json")
    suspend fun postSurveyRevisitData(
        @Url url: String,
        @Body json: SurveyPost
    ): PostApiDto

    @POST
    @Headers("Content-Type: application/json")
    suspend fun postSurveyRetakePicturesData(
        @Url url: String,
        @Body json: RetakePicturesPost
    ): PostApiDto

    @GET("api/MobileData/GetAreaListByMauzaId/{mauzaId}")
    suspend fun getAreasByMauzaId(
        @Path("mauzaId") mauzaId: Long,
        @Header("Authorization") token: String
    ): AreaResponse

    @GET("api/MobileDataCorporate/GetCorporateCrops")
    suspend fun getCrops(): Response<List<DropdownItem>>
    @GET("api/MobileDataCorporate/GetCorporateCropTypes")
    suspend fun getCropTypes(): Response<List<DropdownItem>>
    @GET("api/MobileDataCorporate/GetCorporateCropVarieties")
    suspend fun getCropVarieties(): Response<List<DropdownItem>>
    @GET("api/MobileDataCorporate/GetIrrigationSources")
    suspend fun getIrrigationSources(): Response<List<DropdownItem>>
    @POST("api/MobileDataCorporate/AddCorporateCropVariety")
    suspend fun addCropVariety(@Body request: AddVarietyRequest): Response<AddVarietyResponse>

    @POST("api/Account/logoutUser")
    suspend fun logoutUser(
        @Query("userId") userId: Long,
        @Query("Mode") mode: String = "Android",
    ): Response<LogoutResponse>

    @GET
    suspend fun checkAppVersion(
        @Url url: String,
        @Query("appVersion") appVersion: String
    ): Response<VersionCheckResponse>

    // Get list of Tehsil names for dropdown
    @GET("api/CorporateShapefile/GetTehsilNames")
    suspend fun getTehsilNames(
        @Header("Authorization") token: String,
        @Query("district") district: String = ""
    ): Response<List<String>>

    // Get list of AOI names filtered by Tehsil
    @GET("api/CorporateShapefile/GetAOINames")
    suspend fun getAOINames(
        @Header("Authorization") token: String,
        @Query("tehsil") tehsil: String
    ): Response<List<String>>

    // Get parcels for a specific AOI (returns pipe-delimited text)
    @GET("api/CorporateShapefile/GetParcelsByAOI")
    suspend fun getParcelsByAOI(
        @Header("Authorization") token: String,
        @Query("aoi") aoi: String
    ): Response<ResponseBody>

    @GET("api/MobileDataCorporate/zones")
    suspend fun getZones(
        @Header("Authorization") token: String
    ): Response<List<ZoneDto>>

    @GET("api/MobileDataCorporate/divisions/{zoneId}")
    suspend fun getDivisions(
        @Header("Authorization") token: String,
        @Path("zoneId") zoneId: Long
    ): Response<List<DivisionDto>>

    @GET("api/MobileDataCorporate/sections/{divisionId}")
    suspend fun getSections(
        @Header("Authorization") token: String,
        @Path("divisionId") divisionId: Long
    ): Response<List<SectionDto>>

    @GET("api/MobileDataCorporate/Farms/{sectionId}")
    suspend fun getFarms(
        @Header("Authorization") token: String,
        @Path("sectionId") sectionId: Long
    ): Response<List<FarmDto>>

    @GET("api/MobileDataCorporate/blocks/{farmId}")
    suspend fun getBlocks(
        @Header("Authorization") token: String,
        @Path("farmId") farmId: Long
    ): Response<List<BlockDto>>

    @GET("api/MobileDataCorporate/plots/{blockId}")
    suspend fun getPlots(
        @Header("Authorization") token: String,
        @Path("blockId") blockId: Long
    ): Response<List<PlotDto>>


    @GET("api/CorporateManagement/GetAllLessors")
    suspend fun getAllLessors(): Response<List<LessorEntity>>
    @GET("api/MobileDataCorporate/GetCorporatePropertyTypes")
    suspend fun getPropertyTypes(): Response<List<DropdownItem>>
    @GET("api/CorporateShapefile/GetAOIByTehsil")
    suspend fun getAOIByTehsil(
        @Header("Authorization") token: String,
        @Query("tehsil") tehsil: String
    ): Response<ResponseBody>
    @GET("api/CorporateShapefile/GetSurveyedParcelsByTehsil")
    suspend fun getSurveyedParcelsByTehsil(
        @Header("Authorization") token: String,
        @Query("tehsil") tehsil: String
    ): Response<ResponseBody>

}
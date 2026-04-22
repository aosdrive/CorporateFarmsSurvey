package pk.gop.pulse.katchiAbadi.data.remote

import ActiveParcelResponse
import AreaResponse
import OwnerResponse
import okhttp3.ResponseBody
import pk.gop.pulse.katchiAbadi.data.local.AddVarietyRequest
import pk.gop.pulse.katchiAbadi.data.local.AddVarietyResponse
import pk.gop.pulse.katchiAbadi.data.local.DropdownItem
import pk.gop.pulse.katchiAbadi.data.local.TaskSubmitDto
import pk.gop.pulse.katchiAbadi.data.local.TaskUpdateDto
import pk.gop.pulse.katchiAbadi.data.local.TaskUpdateResponse
import pk.gop.pulse.katchiAbadi.data.remote.post.RetakePicturesPost
import pk.gop.pulse.katchiAbadi.data.remote.post.SurveyPost
import pk.gop.pulse.katchiAbadi.data.remote.request.LoginRequest
import pk.gop.pulse.katchiAbadi.data.remote.request.OnboardingResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.BasicApiDto
import pk.gop.pulse.katchiAbadi.data.remote.response.BasicInfoDto
import pk.gop.pulse.katchiAbadi.data.remote.response.Info
import pk.gop.pulse.katchiAbadi.data.remote.response.KachiAbadiDto
import pk.gop.pulse.katchiAbadi.data.remote.response.KatchiAbadiApiDto
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginDto
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginSurveyorResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.LogoutResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.MauzaSyncResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.MouzaAssignedDto
import pk.gop.pulse.katchiAbadi.data.remote.request.OnboardingUploadDto
import pk.gop.pulse.katchiAbadi.data.remote.response.BlockDto
import pk.gop.pulse.katchiAbadi.data.remote.response.CorporateParcelResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.DivisionDto
import pk.gop.pulse.katchiAbadi.data.remote.response.PostApiDto
import pk.gop.pulse.katchiAbadi.data.remote.response.ResponseDto
import pk.gop.pulse.katchiAbadi.data.remote.response.SectionDto
import pk.gop.pulse.katchiAbadi.data.remote.response.TaskListResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.VersionCheckResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.ZoneDivisionSectionBlockResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.ZoneDto
import pk.gop.pulse.katchiAbadi.domain.model.CorporateParcelPost
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

//   @POST("api/MobileData/AddSurveyData")
//    @Headers("Content-Type: application/json")
//    suspend fun postSurveyDataNew(
//       @Header("Authorization") token: String,
//        @Body json: SurveyPostNew
//    ): PostApiDto

    @POST("api/MobileData/AddSurveyData")
    @Headers("Content-Type: application/json")
    suspend fun postSurveyDataNew(
        @Header("Authorization") token: String,
        @Body json: List<CorporateParcelPost>
    ): Response<PostApiDto>

    @POST("api/MobileData/AddCorporateSurveyData")
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


    @GET("api/MobileData/GetCrops")
    suspend fun getCrops(): Response<List<DropdownItem>>

    @GET("api/MobileData/GetCropTypes")
    suspend fun getCropTypes(): Response<List<DropdownItem>>

    @GET("api/MobileData/GetCropVarieties")
    suspend fun getCropVarieties(): Response<List<DropdownItem>>

    @POST("api/MobileData/AddCropVariety")
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

//    @GET("api/Parcel/GetAllCorporateParcels")
//    suspend fun getCorporateParcels(
//        @Header("Authorization") token: String
//    ): Response<CorporateParcelResponse>


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
    ): Response<ResponseBody>   // ← Use ResponseBody because server returns text/plain

    // ============================================
    // CASCADE DROPDOWN ENDPOINTS
    // ============================================

    /**
     * Fetch all zones, divisions, sections, and blocks in a single request
     * Most efficient for initial app launch
     */
    @GET("api/MobileData/zones-divisions-sections-farms-blocks-plots")
    suspend fun getZoneDivisionSectionBlocks(
        @Header("Authorization") token: String
    ): Response<ZoneDivisionSectionBlockResponse>

    /**
     * Fetch only zones
     */
    @GET("api/MobileData/zones")
    suspend fun getZones(
        @Header("Authorization") token: String
    ): Response<List<ZoneDto>>

    /**
     * Fetch divisions filtered by zone ID
     * Call after user selects a zone (if not using pre-fetched data)
     */
    @GET("api/MobileData/divisions/{zoneId}")
    suspend fun getDivisions(
        @Header("Authorization") token: String,
        @Path("zoneId") zoneId: Long
    ): Response<List<DivisionDto>>

    /**
     * Fetch sections filtered by division ID
     * Call after user selects a division (if not using pre-fetched data)
     */
    @GET("api/MobileData/sections/{divisionId}")
    suspend fun getSections(
        @Header("Authorization") token: String,
        @Path("divisionId") divisionId: Long
    ): Response<List<SectionDto>>

    /**
     * Fetch blocks filtered by section ID
     * Call after user selects a section (if not using pre-fetched data)
     */
    @GET("api/MobileData/blocks/{sectionId}")
    suspend fun getBlocks(
        @Header("Authorization") token: String,
        @Path("sectionId") sectionId: Long
    ): Response<List<BlockDto>>
}
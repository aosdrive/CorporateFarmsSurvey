package pk.gop.pulse.katchiAbadi.domain.model

import pk.gop.pulse.katchiAbadi.data.remote.post.Pictures


// For persons
data class SurveyPersonPost(
    val personId: Long,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val relation: String,
    val religion: String,
    val mobile: String,
    val nic: String,
    val growerCode: String,
    val personArea: String,
    val ownershipType: String,
    val address: String,
    val extra1: String,
    val extra2: String,
    val mauzaId: Long,
    val mauzaName: String
)

data class CorporateParcelPost(
    // Core identification — matches CorporateParcelDto exactly
    val parcelId: Long,
    val parcelNo: String,
    val subParcelNo: String,
    val mauzaId: Long,
    val parcelOperation: String,
    val parcelOperationValue: String,

    // Geometry
    val geomWKT: String? = null,
    val centriod: String? = null,
    val calculatedArea: String? = null,
    val isGeometryCorrect: Boolean = false,
    val distance: Int = 100,

    // Parcel metadata
    val khewatInfo: String? = null,
    val areaAssigned: String? = null,
    val parcelAreaKMF: String? = null,
    val areaName: String? = null,

    // Survey fields — same names as SurveyPostNew
    val propertyType: String? = null,
    val ownershipStatus: String? = null,
    val variety: String? = null,
    val cropType: String? = null,
    val crop: String? = null,
    val year: String? = null,
    val area: String? = null,
    val remarks: String? = null,
    val sowingStatus: String = "No",
    val sowingDate: String? = null,

    // Corporate-specific fields from ActiveParcelEntity
    val zone: String? = null,
    val division: String? = null,
    val section: String? = null,
    val farm: String? = null,
    val block: String? = null,
    val plot: String? = null,
    val lessorName: String? = null,
    val plotBifurcation: String? = null,
    val plotSizeAcres: String? = null,

    val persons: List<SurveyPersonPost> = emptyList(),
    val pictures: List<Pictures> = emptyList(),
    val sowingPersons: List<SowingPersonPostDto>? = null
)




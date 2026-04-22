package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_parcels")
data class ActiveParcelEntity(
    @PrimaryKey(autoGenerate = true)
    val pkid: Long = 0,
    val id: Long,
    val parcelNo: String,       // ⚠️ CHANGED from Long to String
    val subParcelNo: String = "",
    val mauzaId: Long = 0L,
    val mauzaName: String = "",
    val khewatInfo: String = "",
    val areaAssigned: String = "",
    val geomWKT: String = "",
    val centroid: String = "",
    val distance: Int = 0,
    val parcelType: String = "",
    val parcelAreaKMF: String? = null,
    val parcelAreaAbadiDeh: String? = null,
    val surveyStatusCode: Int = 1,
    val surveyId: String? = null,
    val isActivate: Boolean = true,
    val unitId: Long? = 0L,
    val groupId: Long? = 0L,

    // NEW FIELDS from Corperate_parcel
    val plotId: String? = null,
    val ownerName: String? = null,
    val cnic: String? = null,
    val mobileNo: String? = null,
    val cropName: String? = null,
    val cropArea: Double? = null,
    val tehsil: String? = null,
    val district: String? = null,

    // Location hierarchy (existing)
    val zone: String? = null,
    val division: String? = null,
    val section: String? = null,
    val farm: String? = null,
    val block: String? = null,
    val plot: String? = null,
    val area: Double? = null,

    // Existing fields
    val ownershipStatus: String? = null,
    val lessorName: String? = null,
    val plotBifurcation: String? = null,
    val plotSizeAcres: Double? = null,
    val calculatedArea: Double? = null,
    val year: String? = null
)

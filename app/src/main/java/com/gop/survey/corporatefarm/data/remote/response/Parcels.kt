package com.gop.survey.corporatefarm.data.remote.response

data class Parcels(
    val id: Long,
    val geom: String,
    val centroid: String,
    val distance: Double,
    val parcelNo: Long,
    val subParcelNo: String,
    val newStatusId: Int,
    val subParcelsList: List<SubParcelStatus>
)

data class SubParcelStatus(
    val fieldRecordId: String,
    val subParcelNo: String,
    val pictureRevisitRequired: Boolean,
    val fullRevisitRequired: Boolean
)

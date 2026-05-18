package com.gop.survey.corporatefarm.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crops")
data class CropEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val value: String,
    val isSynced: Boolean = true
)

package com.gop.survey.corporatefarm.domain.repository

import androidx.lifecycle.LiveData
import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.domain.model.NewSurveyNewEntity
import com.gop.survey.corporatefarm.domain.model.SurveyFormEntity
import com.gop.survey.corporatefarm.domain.model.SurveyMergeDetails

interface SavedRepository {
    fun getSurveyFormList(): LiveData<List<SurveyMergeDetails>>
    suspend fun deleteSavedRecord(survey: SurveyMergeDetails): SimpleResource
    fun getSavedRecordByStatusAndLimit(statusBit: Int): LiveData<SurveyMergeDetails>
    suspend fun postSavedData(parcelNo: Long, uniqueId: String): SimpleResource
//    suspend fun postAllSavedData(): SimpleResource
    suspend fun viewSavedData(parcelNo: Long, uniqueId: String): List<SurveyFormEntity>
    suspend fun viewSavedDataNew(parcelId: Long): List<NewSurveyNewEntity>
}
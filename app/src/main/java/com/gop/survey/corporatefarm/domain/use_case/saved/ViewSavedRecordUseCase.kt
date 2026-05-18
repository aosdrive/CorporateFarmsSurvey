package com.gop.survey.corporatefarm.domain.use_case.saved

import com.gop.survey.corporatefarm.domain.model.NewSurveyNewEntity
import com.gop.survey.corporatefarm.domain.model.SurveyFormEntity
import com.gop.survey.corporatefarm.domain.repository.SavedRepository
import javax.inject.Inject

class ViewSavedRecordUseCase @Inject constructor(
    private val repository: SavedRepository
) {
    suspend operator fun invoke(parcelNo: Long, uniqueId: String): List<SurveyFormEntity> {
        return repository.viewSavedData(parcelNo, uniqueId)
    }
    suspend operator fun invoke(parcelId: Long): List<NewSurveyNewEntity> {
        return repository.viewSavedDataNew(parcelId)
    }
}

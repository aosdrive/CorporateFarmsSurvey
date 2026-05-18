package com.gop.survey.corporatefarm.domain.use_case.saved

import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.domain.model.SurveyMergeDetails
import com.gop.survey.corporatefarm.domain.repository.SavedRepository
import javax.inject.Inject

class DeleteSavedRecordUseCase @Inject constructor(
    private val repository: SavedRepository
) {
    suspend operator fun invoke(survey: SurveyMergeDetails): SimpleResource {
        return repository.deleteSavedRecord(survey)
    }
}

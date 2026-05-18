package com.gop.survey.corporatefarm.domain.use_case.saved

import androidx.lifecycle.LiveData
import com.gop.survey.corporatefarm.domain.model.SurveyMergeDetails
import com.gop.survey.corporatefarm.domain.repository.SavedRepository
import javax.inject.Inject

class GetAllSavedFormsUseCase @Inject constructor(
    private val repository: SavedRepository
) {
    operator fun invoke(): LiveData<List<SurveyMergeDetails>> {
        return repository.getSurveyFormList()
    }
}

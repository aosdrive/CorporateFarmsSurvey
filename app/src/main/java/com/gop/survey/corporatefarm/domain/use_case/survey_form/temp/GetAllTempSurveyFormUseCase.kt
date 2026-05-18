package com.gop.survey.corporatefarm.domain.use_case.survey_form.temp

import com.gop.survey.corporatefarm.domain.model.TempSurveyFormEntity
import com.gop.survey.corporatefarm.domain.repository.SurveyFormRepository
import javax.inject.Inject

class GetAllTempSurveyFormUseCase @Inject constructor(
    private val repository: SurveyFormRepository
) {
    suspend operator fun invoke(parcelNo: Long): List<TempSurveyFormEntity> {
        return repository.getAllTempData(parcelNo)
    }
}

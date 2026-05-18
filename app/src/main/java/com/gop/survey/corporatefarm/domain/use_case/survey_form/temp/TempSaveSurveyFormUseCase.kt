package com.gop.survey.corporatefarm.domain.use_case.survey_form.temp

import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.domain.model.TempSurveyFormEntity
import com.gop.survey.corporatefarm.domain.repository.SurveyFormRepository
import javax.inject.Inject

class TempSaveSurveyFormUseCase @Inject constructor(
    private val repository: SurveyFormRepository
) {
    suspend operator fun invoke(tempSurveyFormEntity: TempSurveyFormEntity): SimpleResource {
        return repository.saveTempData(tempSurveyFormEntity)
    }
}
package com.gop.survey.corporatefarm.domain.use_case.survey_form.main

import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.domain.model.SurveyFormEntity
import com.gop.survey.corporatefarm.domain.repository.SurveyFormRepository
import javax.inject.Inject

class SaveSurveyFormUseCase @Inject constructor(
    private val repository: SurveyFormRepository
) {
    suspend operator fun invoke(surveyFormEntity: SurveyFormEntity): SimpleResource {
        return repository.saveData(surveyFormEntity)
    }
}
package com.gop.survey.corporatefarm.domain.use_case.survey_form.main

import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.domain.model.SurveyFormEntity
import com.gop.survey.corporatefarm.domain.repository.SurveyFormRepository
import javax.inject.Inject

class SaveAllSurveyFormUseCase @Inject constructor(
    private val repository: SurveyFormRepository
) {
    suspend operator fun invoke(surveyFormEntityList: List<SurveyFormEntity>): SimpleResource {
        return repository.saveAllData(surveyFormEntityList)
    }
}
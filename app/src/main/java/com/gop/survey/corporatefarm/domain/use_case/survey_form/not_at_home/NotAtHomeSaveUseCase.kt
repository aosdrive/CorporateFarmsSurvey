package com.gop.survey.corporatefarm.domain.use_case.survey_form.not_at_home

import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.domain.model.NotAtHomeSurveyFormEntity
import com.gop.survey.corporatefarm.domain.repository.SurveyFormRepository
import javax.inject.Inject

class NotAtHomeSaveUseCase @Inject constructor(
    private val repository: SurveyFormRepository
) {
    suspend operator fun invoke(notAtHomeSurveyFormEntity: NotAtHomeSurveyFormEntity): SimpleResource {
        return repository.saveNotAtHomeData(notAtHomeSurveyFormEntity)
    }
}
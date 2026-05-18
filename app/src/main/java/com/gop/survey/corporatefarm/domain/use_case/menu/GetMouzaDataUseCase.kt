package com.gop.survey.corporatefarm.domain.use_case.menu

import com.gop.survey.corporatefarm.common.ResourceSealed
import com.gop.survey.corporatefarm.data.remote.response.Info
import com.gop.survey.corporatefarm.data.remote.response.MouzaAssignedDto
import com.gop.survey.corporatefarm.domain.repository.MenuRepository
import javax.inject.Inject

class GetMouzaDataUseCase @Inject constructor(
    private val repository: MenuRepository
) {
    suspend operator fun invoke(): ResourceSealed<MouzaAssignedDto, Info> {
        return repository.mouzaAssignedData()
    }
}
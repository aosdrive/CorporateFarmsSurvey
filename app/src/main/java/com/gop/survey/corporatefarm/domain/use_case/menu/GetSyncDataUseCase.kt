package com.gop.survey.corporatefarm.domain.use_case.menu

import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.domain.repository.MenuRepository
import javax.inject.Inject

class GetSyncDataUseCase @Inject constructor(
    private val repository: MenuRepository
) {
    suspend operator fun invoke(
        mauzaId: Long,
        abadiId: Long,
        mauzaName: String,
        abadiName: String
    ): SimpleResource {
        return repository.syncAndSaveData(mauzaId, abadiId, mauzaName, abadiName)
    }
}
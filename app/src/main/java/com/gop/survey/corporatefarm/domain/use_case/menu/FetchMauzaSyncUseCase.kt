package com.gop.survey.corporatefarm.domain.use_case.menu

import com.gop.survey.corporatefarm.common.ResourceSealed
import com.gop.survey.corporatefarm.data.remote.response.MauzaDetail
import com.gop.survey.corporatefarm.data.remote.response.Settings
import com.gop.survey.corporatefarm.domain.repository.MenuRepository
import javax.inject.Inject

class FetchMauzaSyncUseCase @Inject constructor(
    private val repository: MenuRepository
) {
    suspend operator fun invoke(): ResourceSealed<List<MauzaDetail>, Settings> {
        return repository.fetchMauzaSyncData()
    }
}
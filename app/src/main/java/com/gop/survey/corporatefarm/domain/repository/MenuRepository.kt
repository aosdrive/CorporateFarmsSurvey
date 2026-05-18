package com.gop.survey.corporatefarm.domain.repository

import com.gop.survey.corporatefarm.common.ResourceSealed
import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.data.remote.response.Info
import com.gop.survey.corporatefarm.data.remote.response.MauzaDetail
import com.gop.survey.corporatefarm.data.remote.response.MouzaAssignedDto
import com.gop.survey.corporatefarm.data.remote.response.Settings

interface MenuRepository {
    suspend fun syncAndSaveData(
        mauzaId: Long,
        abadiId: Long,
        mauzaName: String,
        abadiName: String
    ): SimpleResource

    suspend fun mouzaAssignedData(): ResourceSealed<MouzaAssignedDto, Info>
    suspend fun fetchMauzaSyncData(): ResourceSealed<List<MauzaDetail>, Settings>
}
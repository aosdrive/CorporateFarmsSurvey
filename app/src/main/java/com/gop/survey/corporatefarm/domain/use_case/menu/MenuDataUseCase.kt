package com.gop.survey.corporatefarm.domain.use_case.menu

data class MenuDataUseCase(
    val getSyncDataUseCase: GetSyncDataUseCase,
    val getMouzaDataUseCase: GetMouzaDataUseCase,
    val fetchMauzaSyncUseCase: FetchMauzaSyncUseCase,
)

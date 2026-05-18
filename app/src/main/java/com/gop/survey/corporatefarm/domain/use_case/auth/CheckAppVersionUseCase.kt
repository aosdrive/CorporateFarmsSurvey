package com.gop.survey.corporatefarm.domain.use_case.auth

import com.gop.survey.corporatefarm.common.Resource
import com.gop.survey.corporatefarm.data.remote.response.VersionCheckResponse
import com.gop.survey.corporatefarm.domain.repository.AuthRepository
import javax.inject.Inject

class CheckAppVersionUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(appVersion: String): Resource<VersionCheckResponse> {
        return try {
            if (appVersion.isBlank()) {
                return Resource.Error("App version cannot be empty")
            }

            // Validate version format (e.g., "1.0.5")
            val versionPattern = Regex("^\\d+\\.\\d+\\.\\d+$")
            if (!versionPattern.matches(appVersion)) {
                return Resource.Error("Invalid version format. Expected format: x.y.z")
            }

            authRepository.checkAppVersion(appVersion)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Version check failed")
        }
    }
}
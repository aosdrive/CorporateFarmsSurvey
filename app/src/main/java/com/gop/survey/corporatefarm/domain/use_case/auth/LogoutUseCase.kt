package com.gop.survey.corporatefarm.domain.use_case.auth

import com.gop.survey.corporatefarm.common.Resource
import com.gop.survey.corporatefarm.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.gop.survey.corporatefarm.data.remote.response.LogoutResponse
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    operator fun invoke(userId: Long, mode: String = "Android"): Flow<Resource<LogoutResponse>> = flow {
        try {
            emit(Resource.Loading())
            val response = repository.logoutUser(userId, mode)
            emit(Resource.Success(response))
        } catch (e: HttpException) {
            emit(Resource.Error(e.localizedMessage ?: "An unexpected error occurred"))
        } catch (e: IOException) {
            emit(Resource.Error("Couldn't reach server. Check your internet connection."))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "An unexpected error occurred"))
        }
    }
}
// data/repository/LessorRepository.kt
package com.gop.survey.corporatefarm.data.repository

import android.util.Log
import com.gop.survey.corporatefarm.data.local.LessorDao
import com.gop.survey.corporatefarm.data.remote.ServerApi
import com.gop.survey.corporatefarm.domain.model.LessorEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LessorRepository @Inject constructor(
    private val api: ServerApi,
    private val dao: LessorDao
) {

    suspend fun fetchAndCacheLessors(forceRefresh: Boolean = false): Resource<List<LessorEntity>> {
        return try {
            val cached = dao.getAllLessors()
            if (!forceRefresh && cached.isNotEmpty()) {
                return Resource.Success(cached)
            }

            val response = api.getAllLessors()
            if (response.isSuccessful) {
                val list = response.body().orEmpty()
                dao.clearAll()
                dao.insertAll(list)
                Log.d("LessorRepo", "Fetched ${list.size} lessors from server")
                Resource.Success(list)
            } else {
                Log.w("LessorRepo", "Server error: ${response.code()}")
                if (cached.isNotEmpty()) Resource.Success(cached)
                else Resource.Error("Server error: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("LessorRepo", "Exception: ${e.message}")
            val cached = dao.getAllLessors()
            if (cached.isNotEmpty()) Resource.Success(cached)
            else Resource.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getCachedLessors(): List<LessorEntity> = dao.getAllLessors()
}
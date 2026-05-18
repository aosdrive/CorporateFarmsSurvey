package com.gop.survey.corporatefarm.presentation.survey_list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.gop.survey.corporatefarm.common.Results
import com.gop.survey.corporatefarm.domain.model.SurveyEntity
import com.gop.survey.corporatefarm.domain.use_case.survey_list.SurveyListDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SurveyViewModel @Inject constructor(
    private val surveyListDataUseCase: SurveyListDataUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _surveysState = MutableStateFlow<Results<List<SurveyEntity>>>(Results.Loading)
    val surveysState: StateFlow<Results<List<SurveyEntity>>> = _surveysState

    private val viewModelJob = Job()
    private val viewModelScope = CoroutineScope(Dispatchers.IO + viewModelJob)

    var isSearched: Boolean
        get() = savedStateHandle.get<Boolean>("isSearched") ?: false
        set(value) = savedStateHandle.set("isSearched", value)

    init {
        fetchSurveys()
    }

    private fun fetchSurveys() {
        viewModelScope.launch {
            try {
                val surveys = surveyListDataUseCase.getAllSurveysUseCase(true) // show all survey
                surveys.collect { survey ->
                    _surveysState.value = Results.Success(survey)
                }
            } catch (e: Exception) {
                _surveysState.value = Results.Error(e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    fun getFilterList(enteredText: String) {
        viewModelScope.launch {
            try {
                val surveys = surveyListDataUseCase.getAllFilteredSurveysUseCase(enteredText, true) // show all survey
                surveys.collect { survey ->
                    _surveysState.value = Results.Success(survey)
                }
            } catch (e: Exception) {
                _surveysState.value = Results.Error(e)
            }
        }
    }
}

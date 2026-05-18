package com.gop.survey.corporatefarm.common

import android.widget.Button
import com.gop.survey.corporatefarm.domain.model.SurveyMergeDetails

interface SavedItemClickListener {
    fun onUploadItemClicked(survey: SurveyMergeDetails, uploadButton: Button)
    fun onDeleteItemClicked(survey: SurveyMergeDetails)
    fun onViewItemClicked(survey: SurveyMergeDetails)
}
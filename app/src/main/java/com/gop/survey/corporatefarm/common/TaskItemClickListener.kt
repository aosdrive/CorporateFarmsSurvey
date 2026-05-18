package com.gop.survey.corporatefarm.common

import com.gop.survey.corporatefarm.domain.model.TaskEntity

interface TaskItemClickListener {
    fun onUploadTaskClicked(task: TaskEntity)
    fun onDeleteTaskClicked(task: TaskEntity)
    fun onViewTaskClicked(task: TaskEntity)
}
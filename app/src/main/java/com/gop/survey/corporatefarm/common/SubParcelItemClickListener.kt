package com.gop.survey.corporatefarm.common

interface SubParcelItemClickListener {
    fun onSelectItemClicked(item: SubParcel)
}

interface RejectedSubParcelItemClickListener {
    fun onSelectItemClicked(item: RejectedSubParcel)
}

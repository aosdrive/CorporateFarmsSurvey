package com.gop.survey.corporatefarm.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gop.survey.corporatefarm.R
import com.gop.survey.corporatefarm.data.local.SurveyWithKhewat
import com.gop.survey.corporatefarm.domain.model.NewSurveyNewEntity

class NewSurveyAdapter(
    private val listener: OnItemClickListener
) : ListAdapter<SurveyWithKhewat, NewSurveyAdapter.NewSurveyViewHolder>(DiffCallback()) {

    interface OnItemClickListener {
        fun onUploadClicked(survey: NewSurveyNewEntity)
        fun onItemClicked(survey: NewSurveyNewEntity)
        fun onDeleteClicked(survey: NewSurveyNewEntity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewSurveyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_new_survey, parent, false)
        return NewSurveyViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewSurveyViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NewSurveyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvParcelNo: TextView = itemView.findViewById(R.id.tvParcelNo)
        private val tvPropertyType: TextView = itemView.findViewById(R.id.tvPropertyType)
        private val tvOwnershipStatus: TextView = itemView.findViewById(R.id.tvOwnershipStatus)
        private val tvKhewatInfo: TextView = itemView.findViewById(R.id.tvKhewatInfo)

        private val statusContainer: View = itemView.findViewById(R.id.statusContainer)
        private val statusDot: View = itemView.findViewById(R.id.statusDot)
        private val tvStatusLabel: TextView = itemView.findViewById(R.id.tvStatusLabel)

        private val btnUpload: View = itemView.findViewById(R.id.btnUpload)
        private val btnView: View = itemView.findViewById(R.id.btnView)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)

        fun bind(surveyWithKhewat: SurveyWithKhewat) {
            val survey = surveyWithKhewat.survey
            val ctx = itemView.context

            val subParcel = if (survey.subParcelNo.isNullOrBlank() || survey.subParcelNo == "0")
                "" else "-${survey.subParcelNo}"
            tvParcelNo.text = "P/N ${survey.parcelNo}$subParcel"

            tvPropertyType.text = survey.propertyType.ifBlank { "—" }
            tvOwnershipStatus.text = survey.ownershipStatus.ifBlank { "—" }
            tvKhewatInfo.text = surveyWithKhewat.khewatInfo.ifBlank { "—" }

            // Status — adjust the field check to whatever your entity uses
            val isUploaded = false /* survey.isUploaded == true */
            if (isUploaded) {
                tvStatusLabel.text = "UPLOADED"
                tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_green_dark))
                statusDot.setBackgroundResource(R.drawable.status_dot_uploaded_glow)
                statusContainer.setBackgroundResource(R.drawable.status_uploaded_glow)
            } else {
                tvStatusLabel.text = "PENDING"
                tvStatusLabel.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_orange_dark))
                statusDot.setBackgroundResource(R.drawable.status_dot_pending_glow)
                statusContainer.setBackgroundResource(R.drawable.status_pending_glow)
            }

            btnUpload.setOnClickListener { listener.onUploadClicked(survey) }
            btnView.setOnClickListener { listener.onItemClicked(survey) }
            btnDelete.setOnClickListener { listener.onDeleteClicked(survey) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SurveyWithKhewat>() {
        override fun areItemsTheSame(oldItem: SurveyWithKhewat, newItem: SurveyWithKhewat): Boolean =
            oldItem.survey.pkId == newItem.survey.pkId

        override fun areContentsTheSame(oldItem: SurveyWithKhewat, newItem: SurveyWithKhewat): Boolean =
            oldItem == newItem
    }
}
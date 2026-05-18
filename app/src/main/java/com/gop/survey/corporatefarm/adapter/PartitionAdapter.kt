package com.gop.survey.corporatefarm.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gop.survey.corporatefarm.data.remote.post.Partition
import com.gop.survey.corporatefarm.databinding.CardViewPartitionBinding

class PartitionAdapter :
    ListAdapter<Partition, PartitionAdapter.PartitionViewHolder>(PartitionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartitionViewHolder {
        val binding =
            CardViewPartitionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return PartitionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PartitionViewHolder, position: Int) {
        val partition = getItem(position)
        holder.bind(partition)
    }

    class PartitionViewHolder(private val binding: CardViewPartitionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(partition: Partition) {
            binding.apply {
                tvPartitionHeader.text = "Division: ${partition.PartitionNumber}"
                tvLanduseType.text = partition.Landuse
                tvOccupancyType.text = partition.Occupancy

                when (partition.Landuse) {
                    "Commercial" -> {
                        tvCommercialActivityTypeCaption.visibility = View.VISIBLE
                        tvCommercialActivityType.visibility = View.VISIBLE
                        tvCommercialActivityType.text = partition.CommercialActivity
                    }

                    else -> {
                        tvCommercialActivityTypeCaption.visibility = View.GONE
                        tvCommercialActivityType.visibility = View.GONE
                    }
                }
                }
            }
    }

    class PartitionDiffCallback : DiffUtil.ItemCallback<Partition>() {
        override fun areItemsTheSame(oldItem: Partition, newItem: Partition): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Partition, newItem: Partition): Boolean {
            return oldItem == newItem
        }
    }
}

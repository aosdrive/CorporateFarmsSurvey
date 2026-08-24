package com.gop.survey.corporatefarm.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gop.survey.corporatefarm.R

data class BoundaryItem(
    val tehsil: String,
    val district: String,
    val parts: Int,
    val area: Double,
    val parcelCount: Int
)

class BoundaryAdapter(
    private val items: List<BoundaryItem>,
    private val currentTehsil: String,
    private val onPick: (BoundaryItem) -> Unit
) : RecyclerView.Adapter<BoundaryAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.boundary_root)
        val tvName: TextView = view.findViewById(R.id.tv_boundary_name)
        val tvSub: TextView = view.findViewById(R.id.tv_boundary_sub)
        val tvBadge: TextView = view.findViewById(R.id.tv_boundary_badge)
        val ivCheck: ImageView = view.findViewById(R.id.iv_boundary_check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_boundary, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val isSelected = item.tehsil.equals(currentTehsil, ignoreCase = true)

        holder.tvName.text = item.tehsil

        val subParts = buildList {
            if (item.district.isNotBlank()) add(item.district)
            if (item.parts > 1) add("${item.parts} parts")
        }
        holder.tvSub.text = if (subParts.isEmpty()) "Boundary downloaded"
        else subParts.joinToString("  ·  ")

        holder.tvBadge.text = if (item.parcelCount > 0)
            "${item.parcelCount} parcels" else "No parcels yet"

        holder.ivCheck.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        holder.root.alpha = if (isSelected) 1.0f else 0.85f

        holder.root.setOnClickListener { onPick(item) }
    }
}
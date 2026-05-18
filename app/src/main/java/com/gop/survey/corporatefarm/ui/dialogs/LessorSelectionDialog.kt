// ui/dialogs/LessorSelectionDialog.kt
package com.gop.survey.corporatefarm.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import com.gop.survey.corporatefarm.domain.model.LessorEntity

class LessorSelectionDialog(
    private val context: Context,
    private val lessors: List<LessorEntity>,
    private val currentSelectedId: Int?,
    private val onConfirm: (LessorEntity) -> Unit
) {
    fun show() {
        if (lessors.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("No Lessors")
                .setMessage("No lessors available. Please add one using 'Add New Lessor'.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val names = lessors.map {
            "${it.name}${if (!it.code.isNullOrBlank()) "  (${it.code})" else ""}"
        }.toTypedArray()

        val selectedIndex = lessors.indexOfFirst { it.id == currentSelectedId }
            .coerceAtLeast(0)

        AlertDialog.Builder(context)
            .setTitle("Select Lessor")
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                onConfirm(lessors[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
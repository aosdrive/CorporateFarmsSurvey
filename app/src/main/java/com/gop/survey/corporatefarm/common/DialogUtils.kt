package com.gop.survey.corporatefarm.common

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

object DialogUtils {

    fun styleButtons(dialog: AlertDialog) {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    fun info(context: Context, title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .create()
            .also { it.show(); styleButtons(it) }
    }

    fun confirm(
        context: Context,
        title: String,
        message: String,
        confirmText: String = "OK",
        cancelText: String = "Cancel",
        cancelable: Boolean = false,
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(cancelable)
            .setPositiveButton(confirmText) { d, _ ->
                d.dismiss()
                onConfirm()
            }
            .setNegativeButton(cancelText) { d, _ -> d.dismiss() }
            .create()
            .also { it.show(); styleButtons(it) }
    }

    fun permissionSettings(context: Context, permissionName: String) {
        AlertDialog.Builder(context)
            .setTitle("$permissionName Permission")
            .setMessage(
                "You have permanently denied the $permissionName permission. " +
                        "Please enable it in the app settings."
            )
            .setPositiveButton("Settings") { _, _ ->
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .create()
            .also { it.show(); styleButtons(it) }
    }

    fun okCancel(
        context: Context,
        message: String,
        onOk: DialogInterface.OnClickListener
    ) {
        AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton("OK", onOk)
            .setNegativeButton("Cancel", null)
            .create()
            .also { it.show(); styleButtons(it) }
    }

    fun singleChoice(
        context: Context,
        title: String,
        options: Array<String>,
        preselected: Int = 0,
        confirmText: String = "OK",
        onPicked: (Int) -> Unit
    ) {
        var selected = preselected
        AlertDialog.Builder(context)
            .setTitle(title)
            .setCancelable(false)
            .setSingleChoiceItems(options, preselected) { _, which -> selected = which }
            .setPositiveButton(confirmText) { d, _ ->
                d.dismiss()
                onPicked(selected)
            }
            .setNegativeButton("Cancel", null)
            .create()
            .also { it.show(); styleButtons(it) }
    }
}
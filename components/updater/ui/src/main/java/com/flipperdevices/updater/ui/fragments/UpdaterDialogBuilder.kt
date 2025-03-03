package com.flipperdevices.updater.ui.fragments

import android.app.AlertDialog
import android.os.Build
import android.text.Html
import android.text.Spanned
import com.flipperdevices.bridge.synchronization.api.SynchronizationApi
import com.flipperdevices.core.activityholder.CurrentActivityHolder
import com.flipperdevices.core.navigation.global.CiceroneGlobal
import com.flipperdevices.info.shared.getNameByChannel
import com.flipperdevices.updater.model.VersionFiles
import com.flipperdevices.updater.ui.R
import com.github.terrakok.cicerone.androidx.FragmentScreen

class UpdaterDialogBuilder(
    private val globalCicerone: CiceroneGlobal,
    private val synchronizationApi: SynchronizationApi
) {
    fun showDialog(versionFiles: VersionFiles?) {
        val activity = CurrentActivityHolder.getCurrentActivity() ?: return
        val isSyncRunning = synchronizationApi.isSynchronizationRunning()

        lateinit var dialog: AlertDialog

        val titleId = if (isSyncRunning) {
            R.string.update_dialog_sync_title
        } else R.string.update_dialog_title
        val description = if (isSyncRunning) {
            activity.getString(R.string.update_dialog_sync_desc)
        } else {
            val versionNameId = versionFiles?.version?.channel?.let { getNameByChannel(it) }
            val versionText = if (versionNameId != null) {
                "<b>${activity.getString(versionNameId)} ${versionFiles.version.version}</b>"
            } else ""
            activity.getString(R.string.update_dialog_desc, versionText)
        }

        dialog = AlertDialog.Builder(activity)
            .setTitle(titleId)
            .setMessage(fromHtml(description))
            .setPositiveButton(R.string.update_dialog_yes) { _, _ ->
                globalCicerone.getRouter().newRootScreen(
                    FragmentScreen {
                        UpdaterFragment.getInstance(versionFiles)
                    }
                )
                dialog.dismiss()
            }
            .setNegativeButton(R.string.update_dialog_no) { _, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun fromHtml(text: String): Spanned {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(text)
        }
    }
}

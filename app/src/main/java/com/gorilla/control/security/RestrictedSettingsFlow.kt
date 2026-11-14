package com.gorilla.control.security

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * Opens Android's own Restricted Settings explanation for this app.
 *
 * This does NOT grant Accessibility. It only asks the OS to show its
 * restricted-setting flow; the user must still approve the setting in System Settings.
 */
object RestrictedSettingsFlow {
    private const val ACTION_ANDROID_16 =
        "android.settings.SHOW_RESTRICTED_SETTING_DIALOG"
    private const val ACTION_ECM_COMPAT =
        "android.app.ecm.action.SHOW_ECM_RESTRICTED_SETTING_DIALOG"
    private const val ACCESSIBILITY_OP =
        "android:bind_accessibility_service"
    private const val EXTRA_PACKAGE_NAME =
        "android.intent.extra.PACKAGE_NAME"
    private const val EXTRA_SUBJECT =
        "android.intent.extra.SUBJECT"

    fun openSystemExplanation(context: Context): Boolean {
        val uid = context.applicationInfo.uid
        val actions = listOf(ACTION_ANDROID_16, ACTION_ECM_COMPAT)

        for (action in actions) {
            val intent = Intent(action).apply {
                putExtra(EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(Intent.EXTRA_UID, uid)
                putExtra(EXTRA_SUBJECT, ACCESSIBILITY_OP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Try the compatibility action supported by some Android builds.
            } catch (_: SecurityException) {
                // OEM firmware may reserve this system flow for its Settings app.
            }
        }
        return false
    }
}

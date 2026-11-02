package com.xyrospace.expensetracker.sms

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class SmsPermissionStatus {
    GRANTED,
    NOT_GRANTED,
    DENIED_PERMANENTLY,
    RESTRICTED,
}

/** Narrow contract used by the receiver pipeline so permission checks stay replaceable in tests. */
fun interface SmsPermissionChecker {
    fun isGranted(): Boolean
}

/**
 * Reads the live Android permission state. A persisted "monitoring enabled" preference is never
 * treated as a substitute for the operating system's answer.
 */
@Singleton
class SmsPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsPermissionChecker {

    override fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * [requestAttempted] distinguishes "never asked" from "the system will no longer show the
     * dialog". [activity] is required only to consult the rationale flag.
     */
    fun status(activity: Activity?, requestAttempted: Boolean): SmsPermissionStatus {
        if (isGranted()) return SmsPermissionStatus.GRANTED
        if (!supportsSms()) return SmsPermissionStatus.RESTRICTED
        if (requestAttempted && activity != null && !shouldShowRationale(activity)) {
            return SmsPermissionStatus.DENIED_PERMANENTLY
        }
        return SmsPermissionStatus.NOT_GRANTED
    }

    fun shouldShowRationale(activity: Activity): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.RECEIVE_SMS,
        )

    fun appSettingsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun supportsSms(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
}

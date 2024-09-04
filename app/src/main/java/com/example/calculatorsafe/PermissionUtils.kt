package com.example.calculatorsafe

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionUtils {

    // Define your permission request code here
    const val REQUEST_CODE_PERMISSIONS = 100

    /**
     * Checks and requests permissions.
     *
     * @param context The context of the calling activity.
     * @param permissions An array of permissions to check.
     * @param requestCode The request code for permissions.
     * @return true if all permissions are granted, false otherwise.
     */
    fun checkAndRequestPermissions(context: Context, permissions: Array<String>, requestCode: Int): Boolean {
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        return if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(context as Activity, permissionsToRequest, requestCode)
            false
        } else {
            true
        }
    }
}
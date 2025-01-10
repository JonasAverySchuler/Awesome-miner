package com.example.calculatorsafe.helpers

import android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {
    private const val TAG = "PermissionsHelper"
    const val REQUEST_CODE_READ_MEDIA = 1001

    fun checkAndRequestPermissions(context: Context, accessUserImages: () -> Unit, manageStoragePermissionLauncher: ActivityResultLauncher<Intent> ) {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  // Android 13 (API 33) or above
            // Request media permissions separately
            if (ContextCompat.checkSelfPermission(context, READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(context, READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(READ_MEDIA_VIDEO)
            }
        } else // Android 11 (API 30) and Android 12 (API 31)
            // Request MANAGE_EXTERNAL_STORAGE permission if not granted
            if (!Environment.isExternalStorageManager()) {
                permissionsNeeded.add(MANAGE_EXTERNAL_STORAGE)
            }

        // Check if any permissions are needed
        if (permissionsNeeded.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: $permissionsNeeded")

            if (permissionsNeeded.contains(MANAGE_EXTERNAL_STORAGE)) {
                // If we need MANAGE_EXTERNAL_STORAGE permission, go to settings
                if (isManageStoragePermissionAvailable(context)) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    manageStoragePermissionLauncher.launch(intent)
                    return // Prevent further processing if permission is being requested
                } else {
                    // If the permission screen is not available, guide the user to app settings
                    DialogHelper.showConfirmationDialog(
                        context,
                        "Permission Required",
                        "This app needs access to all files in your device to function properly. Please grant the necessary permissions in the settings.",
                        "Go to Settings",
                        "Cancel",
                        {   val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            settingsIntent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(settingsIntent) })
                    return
                }
            } else {
                // For other permissions, request them
                if (context is Activity) {
                    ActivityCompat.requestPermissions(
                        context,
                        permissionsNeeded.toTypedArray(),
                        REQUEST_CODE_READ_MEDIA
                    )
                } else {
                    Log.e(TAG, "Context is not an Activity, cannot request permissions")
                }
            }
        } else {
            // Permissions are already granted
            accessUserImages()
        }
    }

    private fun isManageStoragePermissionAvailable(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return activities.isNotEmpty()
    }


    //Logging function to show all permissions denied and granted
    fun checkAllGrantedPermissions(context: Context) {
        try {
            val packageManager = context.packageManager
            val packageName = context.packageName
            val packageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS
            )

            val permissions = packageInfo.requestedPermissions
            val grantedPermissions = mutableListOf<String>()
            val deniedPermissions = mutableListOf<String>()

            if (permissions != null) {
                for (i in permissions.indices) {
                    val permission = permissions[i]
                    val isGranted = (packageInfo.requestedPermissionsFlags!![i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0

                    if (isGranted) {
                        grantedPermissions.add(permission)
                    } else {
                        deniedPermissions.add(permission)
                    }
                }
            }

            Log.e("PermissionsChecker", "Granted Permissions: $grantedPermissions")
            Log.e("PermissionsChecker", "Denied Permissions: $deniedPermissions")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PermissionsChecker", "Error checking permissions: ${e.message}")
        }
    }
}
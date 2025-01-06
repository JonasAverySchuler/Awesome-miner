package com.example.calculatorsafe.helpers

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log

object PermissionHelper {

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
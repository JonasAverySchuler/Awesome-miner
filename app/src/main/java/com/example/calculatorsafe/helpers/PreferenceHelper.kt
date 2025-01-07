package com.example.calculatorsafe.helpers

import android.content.Context

object PreferenceHelper {

    private const val PREFS_NAME = "PicVaultPrefs"
    private const val PREF_PASSCODE_SET = "passcode_set"
    private const val PREF_PASSCODE = "passcode"
    private const val IS_FIRST_RUN = "is_first_run"


    fun isPasscodeSet(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(PREF_PASSCODE_SET, false)
    }

    fun setPasscodeSet(context: Context, passcodeSet: Boolean) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean(PREF_PASSCODE_SET, passcodeSet).apply()
    }

    fun setPasscode(context: Context, passcodeString: String) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(PREF_PASSCODE, passcodeString).apply()
        setPasscodeSet(context, true)
    }

    fun getPasscode(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_PASSCODE,null)
    }

    fun saveAlbumMetadata(context: Context, albumName: String, albumId: String) {
        val prefs = context.getSharedPreferences("album_metadata", Context.MODE_PRIVATE)
        prefs.edit().putString(albumName, albumId).apply()
    }

    fun getAlbumId(context: Context, albumName: String): String? {
        val prefs = context.getSharedPreferences("album_metadata", Context.MODE_PRIVATE)
        return prefs.getString(albumName, null)
    }

    fun isFirstRun(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(IS_FIRST_RUN, true)
    }

    fun setFirstRun(context: Context, isFirstRun: Boolean) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean(IS_FIRST_RUN, isFirstRun).apply()
    }
}

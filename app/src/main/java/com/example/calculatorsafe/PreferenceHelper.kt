package com.example.calculatorsafe

import android.content.Context

object PreferenceHelper {

    private const val PREFS_NAME = "PicVaultPrefs"
    private const val PREF_PASSCODE_SET = "passcode_set"
    private const val PREF_PASSCODE = "passcode"


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
}

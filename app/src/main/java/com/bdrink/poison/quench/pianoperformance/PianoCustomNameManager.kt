package com.bdrink.poison.quench.pianoperformance

import android.content.Context
import android.content.SharedPreferences
import android.util.Log


class PianoCustomNameManager(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREF_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val PREF_NAME = "piano_custom_names"
        private const val TAG = "PianoCustomNameManager"
    }

    fun saveCustomName(filePath: String, customName: String) {
        try {
            sharedPreferences.edit()
                .putString(filePath, customName)
                .apply()
            Log.d(TAG, "Save a custom name: $filePath -> $customName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom name", e)
        }
    }

    fun getCustomName(filePath: String): String? {
        return try {
            val customName = sharedPreferences.getString(filePath, null)
            customName
        } catch (e: Exception) {
            null
        }
    }




    fun cleanupNonExistentFiles(existingFilePaths: Set<String>) {
        try {
            val allKeys = sharedPreferences.all.keys
            val keysToRemove = allKeys - existingFilePaths

            if (keysToRemove.isNotEmpty()) {
                val editor = sharedPreferences.edit()
                keysToRemove.forEach { key ->
                    editor.remove(key)
                }
                editor.apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean up records", e)
        }
    }
}
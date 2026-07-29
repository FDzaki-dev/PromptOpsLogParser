package com.fdzaki.promptopslogparser.ai

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple local storage for the user's own Anthropic API key.
 * The key never leaves the device except in the Authorization header of the
 * user-initiated analysis request itself — it is not bundled, hardcoded,
 * or sent anywhere else.
 */
object ApiKeyStore {

    private const val PREFS_NAME = "promptops_secure_prefs"
    private const val KEY_API_KEY = "anthropic_api_key"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getApiKey(context: Context): String? {
        val value = prefs(context).getString(KEY_API_KEY, null)
        return if (value.isNullOrBlank()) null else value
    }

    fun saveApiKey(context: Context, apiKey: String) {
        prefs(context).edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun clearApiKey(context: Context) {
        prefs(context).edit().remove(KEY_API_KEY).apply()
    }
}

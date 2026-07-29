package com.fdzaki.promptopslogparser.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Local storage for the user's own Anthropic API key.
 * The key never leaves the device except in the Authorization header of the
 * user-initiated analysis request itself — it is not bundled, hardcoded,
 * or sent anywhere else.
 *
 * Backed by Jetpack Security's [EncryptedSharedPreferences] (AES256-GCM, key wrapped
 * by the Android Keystore) so the key is not readable in plain text even on a
 * rooted device or via ADB backup. Falls back to a regular (unencrypted)
 * SharedPreferences file only if the Android Keystore is unavailable/broken on
 * the device (rare OEM bugs) — this keeps the "Analisis AI" feature usable
 * instead of crashing, while still preferring encryption whenever possible.
 */
object ApiKeyStore {

    private const val TAG = "ApiKeyStore"
    private const val PREFS_NAME = "promptops_secure_prefs"
    private const val FALLBACK_PREFS_NAME = "promptops_secure_prefs_fallback"
    private const val KEY_API_KEY = "anthropic_api_key"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        synchronized(this) {
            cachedPrefs?.let { return it }
            val created = try {
                buildEncryptedPrefs(context)
            } catch (e: Exception) {
                // Keystore corrupted/unavailable on this device — degrade gracefully
                // instead of crashing the whole app on launch.
                Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plain prefs", e)
                context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
            }
            cachedPrefs = created
            return created
        }
    }

    private fun buildEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also {
            migrateLegacyPlainKeyIfNeeded(context, it)
        }
    }

    /**
     * One-time migration: versi lama aplikasi menyimpan API key dalam
     * SharedPreferences biasa (plain text) di bawah nama file yang sama.
     * Karena EncryptedSharedPreferences memakai nama file baru secara internal,
     * kita salin key lama (jika ada) ke penyimpanan terenkripsi lalu hapus jejaknya.
     */
    private fun migrateLegacyPlainKeyIfNeeded(context: Context, encrypted: SharedPreferences) {
        if (encrypted.getString(KEY_API_KEY, null) != null) return
        val legacy = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legacyKey = legacy.getString(KEY_API_KEY, null)
        if (!legacyKey.isNullOrBlank()) {
            encrypted.edit().putString(KEY_API_KEY, legacyKey.trim()).apply()
            legacy.edit().remove(KEY_API_KEY).apply()
        }
    }

    fun getApiKey(context: Context): String? {
        return try {
            val value = prefs(context).getString(KEY_API_KEY, null)
            if (value.isNullOrBlank()) null else value
        } catch (e: Exception) {
            // Terjadi mis. jika file terenkripsi dipulihkan (restore) ke device lain
            // dengan Android Keystore berbeda. Anggap key hilang, jangan crash —
            // pengguna akan diminta memasukkan API key lagi.
            Log.w(TAG, "Gagal membaca API key terenkripsi, minta input ulang", e)
            null
        }
    }

    fun saveApiKey(context: Context, apiKey: String) {
        prefs(context).edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun clearApiKey(context: Context) {
        prefs(context).edit().remove(KEY_API_KEY).apply()
    }

    /** True if the key is currently protected by Android Keystore-backed encryption. */
    fun isEncrypted(context: Context): Boolean = prefs(context) is EncryptedSharedPreferences
}

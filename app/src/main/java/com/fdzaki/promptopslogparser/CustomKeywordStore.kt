package com.fdzaki.promptopslogparser

import android.content.Context

/**
 * Stores the user's own custom keywords (beyond the built-in error/exception/warning rules)
 * so lines matching project-specific terms get their own distinct highlight color.
 * Purely local — SharedPreferences, no network involved.
 */
object CustomKeywordStore {

    private const val PREFS_NAME = "promptops_custom_keywords"
    private const val KEY_KEYWORDS_CSV = "keywords_csv"

    fun getKeywords(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_KEYWORDS_CSV, "") ?: ""
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun saveKeywords(context: Context, keywords: List<String>) {
        val csv = keywords.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_KEYWORDS_CSV, csv)
            .apply()
    }
}

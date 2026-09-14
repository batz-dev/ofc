package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class SearchHistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val jsonStr = prefs.getString(KEY_SEARCH_HISTORY, null) ?: ""
        if (jsonStr.isBlank()) {
            _recentSearches.value = emptyList()
            return
        }
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i, "").trim()
                if (item.isNotEmpty() && !list.contains(item)) {
                    list.add(item)
                }
            }
            _recentSearches.value = list
        } catch (e: Exception) {
            _recentSearches.value = emptyList()
        }
    }

    private fun saveHistory(list: List<String>) {
        val arr = JSONArray()
        list.take(MAX_ITEMS).forEach { arr.put(it) }
        prefs.edit().putString(KEY_SEARCH_HISTORY, arr.toString()).apply()
        _recentSearches.value = list.take(MAX_ITEMS)
    }

    fun addSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        val current = _recentSearches.value.toMutableList()
        // Remove existing occurrence to place it at top
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        saveHistory(current)
    }

    fun removeSearch(query: String) {
        val current = _recentSearches.value.toMutableList()
        current.removeAll { it.equals(query.trim(), ignoreCase = true) }
        saveHistory(current)
    }

    fun clearAll() {
        saveHistory(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "movie_stream_search_history"
        private const val KEY_SEARCH_HISTORY = "recent_search_terms"
        private const val MAX_ITEMS = 15

        @Volatile
        private var INSTANCE: SearchHistoryManager? = null

        fun getInstance(context: Context): SearchHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SearchHistoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

package com.jarvis.assistant.core.memory

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

data class JarvisMemoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val key: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MemoryManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_memories", Context.MODE_PRIVATE)

    private val _memories = MutableStateFlow<List<JarvisMemoryItem>>(emptyList())
    val memories: StateFlow<List<JarvisMemoryItem>> = _memories.asStateFlow()

    private val _isLongTermMemoryEnabled = MutableStateFlow(true)
    val isLongTermMemoryEnabled: StateFlow<Boolean> = _isLongTermMemoryEnabled.asStateFlow()

    init {
        loadMemories()
    }

    private fun loadMemories() {
        val jsonStr = prefs.getString("memories_json", "[]") ?: "[]"
        val list = mutableListOf<JarvisMemoryItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    JarvisMemoryItem(
                        id = obj.getString("id"),
                        key = obj.getString("key"),
                        value = obj.getString("value"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
        } catch (e: Exception) {
            // Error loading
        }
        _memories.value = list
    }

    fun saveMemory(key: String, value: String) {
        if (!_isLongTermMemoryEnabled.value) return
        val updated = _memories.value.toMutableList()
        updated.add(JarvisMemoryItem(key = key, value = value))
        _memories.value = updated
        persist(updated)
    }

    fun deleteMemory(id: String) {
        val updated = _memories.value.filter { it.id != id }
        _memories.value = updated
        persist(updated)
    }

    fun clearAllMemories() {
        _memories.value = emptyList()
        persist(emptyList())
    }

    fun setLongTermMemoryEnabled(enabled: Boolean) {
        _isLongTermMemoryEnabled.value = enabled
        if (!enabled) {
            clearAllMemories()
        }
    }

    private fun persist(list: List<JarvisMemoryItem>) {
        val jsonArray = JSONArray()
        for (item in list) {
            jsonArray.put(
                org.json.JSONObject().apply {
                    put("id", item.id)
                    put("key", item.key)
                    put("value", item.value)
                    put("timestamp", item.timestamp)
                }
            )
        }
        prefs.edit().putString("memories_json", jsonArray.toString()).apply()
    }
}

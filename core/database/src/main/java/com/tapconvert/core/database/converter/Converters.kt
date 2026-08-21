package com.tapconvert.core.database.converter

import androidx.room.TypeConverter

class Converters {

    private val delimiterItem = "\u001F"
    private val delimiterEntry = "\u001E"

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        if (list.isNullOrEmpty()) return ""
        return list.joinToString(delimiterItem)
    }

    @TypeConverter
    fun toStringList(data: String?): List<String> {
        if (data.isNullOrBlank()) return emptyList()
        return data.split(delimiterItem).filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun fromStringMap(map: Map<String, String>?): String {
        if (map.isNullOrEmpty()) return ""
        return map.entries.joinToString(delimiterEntry) { "${it.key}$delimiterItem${it.value}" }
    }

    @TypeConverter
    fun toStringMap(data: String?): Map<String, String> {
        if (data.isNullOrBlank()) return emptyMap()
        return data.split(delimiterEntry)
            .filter { it.contains(delimiterItem) }
            .associate {
                val parts = it.split(delimiterItem, limit = 2)
                parts[0] to parts.getOrElse(1) { "" }
            }
    }
}

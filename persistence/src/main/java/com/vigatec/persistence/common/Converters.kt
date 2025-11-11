package com.vigatec.persistence.common

import androidx.room.TypeConverter
import com.vigatec.persistence.entities.KeyConfiguration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromKeyConfigurationList(value: List<KeyConfiguration>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toKeyConfigurationList(value: String): List<KeyConfiguration> {
        val listType = object : TypeToken<List<KeyConfiguration>>() {}.type
        return gson.fromJson(value, listType)
    }
} 
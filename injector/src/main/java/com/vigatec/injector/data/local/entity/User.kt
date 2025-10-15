package com.vigatec.injector.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val username: String,
    val pass: String,
    val role: String = "USER",  // "ADMIN" o "USER"
    val fullName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
) 
package com.vigatec.injector.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "permissions")
data class Permission(
    @PrimaryKey val id: String,  // e.g., "manage_users", "view_logs"
    val name: String,             // e.g., "Gestionar Usuarios"
    val description: String
)



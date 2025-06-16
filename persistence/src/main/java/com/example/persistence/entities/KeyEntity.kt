package com.example.persistence.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.persistence.common.Identifiable

@Entity(tableName = "key")
data class KeyEntity(
    @PrimaryKey(autoGenerate = true)
    override val id: Long = 0L,
    val keyValue: String, // El valor de la llave, podría ser un token, etc.
    val description: String?, // Descripción opcional de la llave
    val createdByAdminId: Long, // ID del administrador que creó la llave
    val creationDate: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
): Identifiable
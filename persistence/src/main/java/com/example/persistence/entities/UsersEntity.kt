package com.example.persistence.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.persistence.common.Identifiable
import com.vigatec.utils.enums.Role

@Entity(tableName = "user")
data class UsersEntity(
    @PrimaryKey(autoGenerate = true)
    override val id: Long = 0L,
    val name: String,
    val email: String, // Debe ser único
    val passwordHash: String, // Almacenar el hash de la contraseña, no la contraseña en texto plano
    val role: String = Role.USER.name // Por defecto, los nuevos usuarios son USER
): Identifiable
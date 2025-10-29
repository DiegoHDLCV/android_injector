package com.example.persistence.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String,
    val applicationType: String,
    val keyConfigurations: List<KeyConfiguration>,

    // Configuración de cifrado KEK
    val useKEK: Boolean = false,          // Activar cifrado con KEK
    val selectedKEKKcv: String = "",       // KCV de la KEK seleccionada

    // Configuración del dispositivo/fabricante
    val deviceType: String = "AISINO"     // AISINO, NEWPOS, etc.
)

data class KeyConfiguration(
    val id: Long,
    val usage: String,
    val keyType: String,
    val slot: String,
    val selectedKey: String,
    val injectionMethod: String,
    val ksn: String = "" // KSN para llaves DUKPT (20 caracteres hex)
) 
package com.vigatec.persistence.entities

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

    // Configuración de KTK - OBLIGATORIO
    val useKTK: Boolean = true,            // Activar cifrado con KTK (siempre true, required)
    val selectedKTKKcv: String = "",       // KCV de la KTK seleccionada (obligatorio)

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
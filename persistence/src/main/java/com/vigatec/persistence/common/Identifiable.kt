package com.vigatec.persistence.common

interface Identifiable {
    // Cambiado de Any? a Long para mayor especificidad
    // Asegúrate que todas las entidades que la implementan usan Long como PK con este nombre.
    val id: Long
}
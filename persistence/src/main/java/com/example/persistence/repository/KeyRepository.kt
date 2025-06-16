package com.example.persistence.repository

import com.example.persistence.dao.KeyDao
import com.example.persistence.entities.KeyEntity
import com.vigatec.utils.enums.Role
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class KeyRepository @Inject constructor(
    private val keyDao: KeyDao,
) {

    suspend fun createKey(
        keyValue: String,
        description: String?
        // Quita: adminUserId: Long
    ): KeyEntity { // Devuelve KeyEntity directamente si el ID se autogenera
        // Ya no necesitas verificar el rol del admin aquí
        val newKey = KeyEntity(
            keyValue = keyValue,
            description = description,
            // Necesitas decidir qué poner en createdByAdminId si ya no pasas el ID.
            // ¿Quizás un valor por defecto, o quitar el campo si ya no es relevante?
            // Por ahora, lo pongo como 0 o un valor dummy, pero debes definir esto.
            createdByAdminId = 0L // O un ID de sistema/admin por defecto si tienes uno
        )
        val id = keyDao.insertKey(newKey)
        return newKey.copy(id = id) // Room actualiza el ID en el objeto insertado si es Long
    }

    fun getKeysCreatedByAdmin(adminUserId: Long): Flow<List<KeyEntity>> {
        // Podrías añadir una verificación aquí también si el adminUserId corresponde a un admin,
        // pero la lógica de quién puede llamar a este método usualmente está en el ViewModel/UseCase.
        return keyDao.getKeysByAdmin(adminUserId)
    }

    fun getAllActiveKeys(): Flow<List<KeyEntity>> {
        return keyDao.getAllActiveKeys()
    }

    fun getAllKeys(requestingUserId: Long): Flow<List<KeyEntity>>? {
        // Esta función debería ser solo para administradores
        // Necesitamos verificar el rol del `requestingUserId` antes de devolver los datos.
        // Esto es una simplificación. En un caso real, esta lógica estaría en un UseCase
        // que primero obtiene el usuario, verifica su rol, y luego llama al repositorio.
        // Por ahora, devolvemos null si no es admin, o considera lanzar una excepción.
        // Esta es una implementación SÍNCRONA de la verificación, lo cual no es ideal.
        // Debería ser suspend o el ViewModel/UseCase manejaría la obtención del usuario asíncronamente.

        // val user = runBlocking { userDao.getUserById(requestingUserId) } // ¡NO HACER ESTO EN CÓDIGO REAL!
        // if (user?.role != Role.ADMIN.name) {
        //     return null
        // }
        // Por ahora, asumimos que la verificación de rol se hace antes de llamar a este método.
        return keyDao.getAllKeys()
    }


    suspend fun getKeyById(keyId: Long): KeyEntity? {
        return keyDao.getKeyById(keyId)
    }

    suspend fun updateKeyStatus(keyId: Long, isActive: Boolean): Boolean {
        // Ya no se necesita verificar el admin
        val key = keyDao.getKeyById(keyId)
        return if (key != null) {
            keyDao.updateKey(key.copy(isActive = isActive))
            true
        } else {
            false
        }
    }

    suspend fun deleteKey(keyId: Long): Boolean {
        // Ya no se necesita verificar el admin
        keyDao.deleteKeyById(keyId)
        return true // O verifica si la fila fue afectada si el DAO lo permite
    }
}
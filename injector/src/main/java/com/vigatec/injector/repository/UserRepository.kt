package com.vigatec.injector.repository

import com.vigatec.injector.data.local.dao.UserDao
import com.vigatec.injector.data.local.entity.User
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(private val userDao: UserDao) {

    suspend fun login(username: String, pass: String): User? {
        val user = userDao.findByUsername(username)
        return if (user != null && user.pass == pass && user.isActive) {
            user
        } else {
            null
        }
    }

    suspend fun insertUser(user: User): Long {
        return userDao.insertUser(user)
    }

    suspend fun updateUser(user: User) {
        userDao.updateUser(user)
    }

    suspend fun deleteUser(user: User) {
        userDao.deleteUser(user)
    }

    suspend fun findByUsername(username: String): User? {
        return userDao.findByUsername(username)
    }

    suspend fun findById(userId: Int): User? {
        return userDao.findById(userId)
    }

    fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers()
    }

    fun getActiveUsers(): Flow<List<User>> {
        return userDao.getActiveUsers()
    }

    fun getUsersByRole(role: String): Flow<List<User>> {
        return userDao.getUsersByRole(role)
    }

    /**
     * Obtiene el total de usuarios en el sistema.
     */
    suspend fun getUserCount(): Int {
        return userDao.getUserCount()
    }

    suspend fun getUserCountByRole(role: String): Int {
        return userDao.getUserCountByRole(role)
    }

    suspend fun getAdminCount(): Int {
        return userDao.getAdminCount()
    }

    suspend fun updateUserActiveStatus(userId: Int, isActive: Boolean) {
        userDao.updateUserActiveStatus(userId, isActive)
    }

    suspend fun updateUserPassword(userId: Int, newPassword: String) {
        userDao.updateUserPassword(userId, newPassword)
    }

    suspend fun deleteUserById(userId: Int) {
        userDao.deleteUserById(userId)
    }

    suspend fun isUserAdmin(username: String): Boolean {
        val user = userDao.findByUsername(username)
        return user?.role == "ADMIN"
    }
} 
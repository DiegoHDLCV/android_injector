package com.vigatec.injector.repository

import com.vigatec.injector.data.local.dao.UserDao
import com.vigatec.injector.data.local.entity.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(private val userDao: UserDao) {

    suspend fun login(username: String, pass: String): Boolean {
        val user = userDao.findByUsername(username)
        return user != null && user.pass == pass
    }
} 
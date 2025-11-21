package com.vigatec.injector.di

import android.content.Context
import android.util.Log
import com.vigatec.injector.data.local.preferences.SessionManager
import com.vigatec.injector.data.local.preferences.UserPreferencesManager
import com.vigatec.injector.data.local.preferences.CustodianTimeoutPreferencesManager

import com.vigatec.injector.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val TAG = "AppModule"



    @Provides
    @Singleton
    fun provideUserPreferencesManager(@ApplicationContext context: Context): UserPreferencesManager {
        return UserPreferencesManager(context)
    }



    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager {
        return SessionManager(context)
    }

    @Provides
    @Singleton
    fun provideCustodianTimeoutPreferencesManager(@ApplicationContext context: Context): CustodianTimeoutPreferencesManager {
        return CustodianTimeoutPreferencesManager(context)
    }


}
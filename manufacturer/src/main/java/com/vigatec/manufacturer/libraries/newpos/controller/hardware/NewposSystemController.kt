package com.vigatec.manufacturer.libraries.newpos.controller.hardware

import android.content.Context
import android.util.Log
import com.vigatec.manufacturer.base.controllers.system.ISystemController

class NewposSystemController : ISystemController {
    private val TAG = "NewposSystemController"
    private lateinit var context: Context

    override fun initialize(context: Context) {
        this.context = context
        Log.d(TAG, "initialize called")
    }

    override suspend fun silentUninstall(packageName: String): Boolean {
        Log.d(TAG, "silentUninstall: $packageName")
        return false
    }

    override suspend fun silentInstall(apkPath: String): Boolean {
        Log.d(TAG, "silentInstall: $apkPath")
        return false
    }

    override suspend fun reboot() {
        Log.d(TAG, "reboot called")
    }

    override suspend fun shutdown() {
        Log.d(TAG, "shutdown called")
    }

    override suspend fun setAppEnabled(packageName: String, enabled: Boolean): Boolean {
        Log.d(TAG, "setAppEnabled: $packageName enabled=$enabled")
        return false
    }

    override suspend fun grantPermission(packageName: String, permission: String): Boolean {
        Log.d(TAG, "grantPermission: $packageName permission=$permission")
        return false
    }

    override suspend fun setStatusBarDisabled(disabled: Boolean): Boolean {
        Log.d(TAG, "setStatusBarDisabled: $disabled")
        return false
    }

    override suspend fun setNavigationBarVisible(visible: Boolean): Boolean {
        Log.d(TAG, "setNavigationBarVisible: $visible")
        return false
    }

    override suspend fun setHomeRecentKeysEnabled(homeEnabled: Boolean, recentEnabled: Boolean): Boolean {
        Log.d(TAG, "setHomeRecentKeysEnabled: home=$homeEnabled recent=$recentEnabled")
        return false
    }

    override suspend fun setPowerKeyLongPressIntercept(intercept: Boolean): Boolean {
        Log.d(TAG, "setPowerKeyLongPressIntercept: $intercept")
        return false
    }

    override suspend fun setAppUninstallDisabled(packageName: String, disabled: Boolean): Boolean {
        Log.d(TAG, "setAppUninstallDisabled: $packageName disabled=$disabled")
        return false
    }
}

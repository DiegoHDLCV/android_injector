package com.vigatec.manufacturer.libraries.aisino.workflow.hardware

import android.content.Context
import android.util.Log
import com.vigatec.manufacturer.base.controllers.system.ISystemController

class AisinoSystemController : ISystemController {
    private val TAG = "AisinoSystemController"
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
}

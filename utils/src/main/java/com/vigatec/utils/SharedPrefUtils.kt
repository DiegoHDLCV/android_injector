package com.vigatec.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SharedPrefUtils @Inject constructor(@ApplicationContext private val appContext: Context) {
    private val prefs by lazy {
        appContext.getSharedPreferences("injector_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_KIOSK_ENABLED = "kiosk_enabled"
        private const val KEY_KIOSK_DISABLE_STATUS_BAR = "kiosk_disable_status_bar"
        private const val KEY_KIOSK_HIDE_NAV_BAR = "kiosk_hide_nav_bar"
        private const val KEY_KIOSK_DISABLE_HOME_KEY = "kiosk_disable_home_key"
        private const val KEY_KIOSK_DISABLE_RECENT_KEY = "kiosk_disable_recent_key"
        private const val KEY_KIOSK_INTERCEPT_POWER_KEY = "kiosk_intercept_power_key"
        private const val KEY_KIOSK_DISABLE_SAFE_MODE = "kiosk_disable_safe_mode"
        private const val KEY_KIOSK_PREVENT_UNINSTALL = "kiosk_prevent_uninstall"
        private const val KEY_KIOSK_SET_DEFAULT_LAUNCHER = "kiosk_set_default_launcher"
        private const val KEY_KIOSK_LAUNCH_ON_BOOT = "kiosk_launch_on_boot"
    }

    fun setKioskModeEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_KIOSK_ENABLED, enabled).apply()
    fun isKioskModeEnabled() = prefs.getBoolean(KEY_KIOSK_ENABLED, false)

    fun setKioskDisableStatusBar(enabled: Boolean) = prefs.edit().putBoolean(KEY_KIOSK_DISABLE_STATUS_BAR, enabled).apply()
    fun isKioskDisableStatusBar() = prefs.getBoolean(KEY_KIOSK_DISABLE_STATUS_BAR, false)

    fun setKioskHideNavigationBar(enabled: Boolean) = prefs.edit().putBoolean(KEY_KIOSK_HIDE_NAV_BAR, enabled).apply()
    fun isKioskHideNavigationBar() = prefs.getBoolean(KEY_KIOSK_HIDE_NAV_BAR, false)

    fun setKioskDisableHomeKey(enabled: Boolean) = prefs.edit().putBoolean(KEY_KIOSK_DISABLE_HOME_KEY, enabled).apply()
    fun isKioskDisableHomeKey() = prefs.getBoolean(KEY_KIOSK_DISABLE_HOME_KEY, false)

    fun setKioskDisableRecentKey(enabled: Boolean) = prefs.edit().putBoolean(KEY_KIOSK_DISABLE_RECENT_KEY, enabled).apply()
    fun isKioskDisableRecentKey() = prefs.getBoolean(KEY_KIOSK_DISABLE_RECENT_KEY, false)

    fun setKioskInterceptPowerKey(enabled: Boolean) = prefs.edit().putBoolean(KEY_KIOSK_INTERCEPT_POWER_KEY, enabled).apply()
    fun isKioskInterceptPowerKey() = prefs.getBoolean(KEY_KIOSK_INTERCEPT_POWER_KEY, false)

    fun setKioskDisableSafeMode(enabled: Boolean) = prefs.edit().putBoolean(KEY_KIOSK_DISABLE_SAFE_MODE, enabled).apply()
    fun isKioskDisableSafeMode() = prefs.getBoolean(KEY_KIOSK_DISABLE_SAFE_MODE, false)

    fun setKioskPreventUninstall(enabled: Boolean) = prefs.edit().putBoolean(KEY_KIOSK_PREVENT_UNINSTALL, enabled).apply()
    fun isKioskPreventUninstall() = prefs.getBoolean(KEY_KIOSK_PREVENT_UNINSTALL, false)

    fun setKioskSetDefaultLauncher(enabled: Boolean) = prefs.edit().putBoolean(KEY_KIOSK_SET_DEFAULT_LAUNCHER, enabled).apply()
    fun isKioskSetDefaultLauncher() = prefs.getBoolean(KEY_KIOSK_SET_DEFAULT_LAUNCHER, false)

    fun setKioskLaunchOnBoot(enabled: Boolean) = prefs.edit().putBoolean(KEY_KIOSK_LAUNCH_ON_BOOT, enabled).apply()
    fun isKioskLaunchOnBoot() = prefs.getBoolean(KEY_KIOSK_LAUNCH_ON_BOOT, false)
}
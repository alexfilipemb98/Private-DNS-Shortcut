package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log

object DnsHelper {
    private const val TAG = "DnsHelper"

    // System Settings.Global keys for Private DNS
    const val SETTING_PRIVATE_DNS_MODE = "private_dns_mode"
    const val SETTING_PRIVATE_DNS_SPECIFIER = "private_dns_specifier"

    const val MODE_OFF = "off"
    const val MODE_OPPORTUNISTIC = "opportunistic"
    const val MODE_HOSTNAME = "hostname"

    // Default hostname as requested (AdGuard DNS)
    const val DEFAULT_HOSTNAME = "dns.adguard.com"

    private const val PREFS_NAME = "dns_tile_preferences"
    private const val KEY_CUSTOM_HOSTNAME = "pref_custom_hostname"

    /**
     * Checks if android.permission.WRITE_SECURE_SETTINGS is granted to this application.
     */
    fun hasPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Reads the current Private DNS mode ("off", "opportunistic", "hostname", or "unknown").
     */
    fun getCurrentMode(context: Context): String {
        return try {
            Settings.Global.getString(context.contentResolver, SETTING_PRIVATE_DNS_MODE) ?: MODE_OFF
        } catch (e: Exception) {
            Log.e(TAG, "Error reading private_dns_mode", e)
            MODE_OFF
        }
    }

    /**
     * Reads the current Private DNS hostname specifier configured in the system.
     */
    fun getCurrentSpecifier(context: Context): String? {
        return try {
            Settings.Global.getString(context.contentResolver, SETTING_PRIVATE_DNS_SPECIFIER)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading private_dns_specifier", e)
            null
        }
    }

    /**
     * Retrieves the preferred hostname saved by the user, falling back to the system specifier or default.
     */
    fun getConfiguredHostname(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CUSTOM_HOSTNAME, null)?.trim()
        if (!saved.isNullOrEmpty()) return cleanHostname(saved)

        val currentSystem = getCurrentSpecifier(context)?.trim()
        if (!currentSystem.isNullOrEmpty()) return cleanHostname(currentSystem)

        return DEFAULT_HOSTNAME
    }

    /**
     * Saves user's preferred hostname in SharedPreferences.
     */
    fun setConfiguredHostname(context: Context, hostname: String) {
        val cleaned = cleanHostname(hostname)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_HOSTNAME, cleaned).apply()
    }

    /**
     * Strips protocol prefixes (e.g., "https://", "://") if present, since Android Private DNS
     * expects a raw domain name (e.g., "dns.adguard.com").
     */
    fun cleanHostname(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("://")) {
            clean = clean.substring(3)
        } else if (clean.startsWith("http://")) {
            clean = clean.substring(7)
        } else if (clean.startsWith("https://")) {
            clean = clean.substring(8)
        } else if (clean.startsWith("tls://")) {
            clean = clean.substring(6)
        }
        return clean.trimEnd('/')
    }

    /**
     * Turns Private DNS ON with the configured or provided hostname.
     * Returns true on success, false on error / missing permission.
     */
    fun turnOn(context: Context, hostname: String? = null): Boolean {
        if (!hasPermission(context)) return false
        val target = cleanHostname(hostname ?: getConfiguredHostname(context))
        return try {
            Settings.Global.putString(context.contentResolver, SETTING_PRIVATE_DNS_SPECIFIER, target)
            Settings.Global.putString(context.contentResolver, SETTING_PRIVATE_DNS_MODE, MODE_HOSTNAME)
            requestTileUpdate(context)
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: WRITE_SECURE_SETTINGS not granted", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error turning on Private DNS", e)
            false
        }
    }

    /**
     * Turns Private DNS OFF.
     * Returns true on success, false on error / missing permission.
     */
    fun turnOff(context: Context): Boolean {
        if (!hasPermission(context)) return false
        return try {
            Settings.Global.putString(context.contentResolver, SETTING_PRIVATE_DNS_MODE, MODE_OFF)
            requestTileUpdate(context)
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: WRITE_SECURE_SETTINGS not granted", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error turning off Private DNS", e)
            false
        }
    }

    /**
     * Toggles between ON (hostname mode) and OFF.
     */
    fun toggle(context: Context): Boolean {
        val currentMode = getCurrentMode(context)
        return if (currentMode == MODE_HOSTNAME) {
            turnOff(context)
        } else {
            turnOn(context)
        }
    }

    /**
     * Requests the Android System to refresh the Quick Settings Tile state.
     */
    fun requestTileUpdate(context: Context) {
        try {
            TileService.requestListeningState(
                context,
                ComponentName(context, DnsTileService::class.java)
            )
        } catch (e: Exception) {
            Log.d(TAG, "Tile requestListeningState not supported or failed", e)
        }
    }

    /**
     * Generates the exact ADB grant command for this app.
     */
    fun getAdbCommand(context: Context): String {
        return "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }
}

package com.example

import android.app.PendingIntent
import android.content.Intent
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast

class DnsTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())
    private var contentObserver: ContentObserver? = null

    companion object {
        private const val TAG = "DnsTileService"
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "onTileAdded called")
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "onStartListening called")

        // Immediately update visual state of the tile
        updateTileState()

        // Register observer to detect manual changes made in Android Settings
        registerDnsObserver()
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "onStopListening called")
        unregisterDnsObserver()
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "onClick called")

        if (!DnsHelper.hasPermission(this)) {
            // Permission is not yet granted via ADB
            Toast.makeText(
                this,
                "Permissão WRITE_SECURE_SETTINGS necessária via ADB.",
                Toast.LENGTH_LONG
            ).show()

            openMainActivity()
            updateTileState()
            return
        }

        // Toggle Private DNS state
        val success = DnsHelper.toggle(this)
        if (!success) {
            Toast.makeText(
                this,
                "Falha ao alternar DNS Privado. Verifique as permissões.",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Update the tile to reflect the new state immediately
        updateTileState()
    }

    /**
     * Reads current DNS state from Settings.Global and updates the Quick Settings Tile.
     */
    private fun updateTileState() {
        val tile = qsTile ?: return

        tile.icon = Icon.createWithResource(this, R.drawable.ic_dns_tile)
        tile.label = getString(R.string.tile_name)

        if (!DnsHelper.hasPermission(this)) {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.tile_subtitle_no_perm)
            }
            tile.updateTile()
            return
        }

        val mode = DnsHelper.getCurrentMode(this)
        val specifier = DnsHelper.getCurrentSpecifier(this)

        if (mode == DnsHelper.MODE_HOSTNAME) {
            // DNS is ON
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hostname = if (!specifier.isNullOrBlank()) {
                    DnsHelper.cleanHostname(specifier)
                } else {
                    DnsHelper.getConfiguredHostname(this)
                }
                tile.subtitle = hostname
            }
        } else {
            // DNS is OFF (or opportunistic)
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (mode == DnsHelper.MODE_OPPORTUNISTIC) {
                    "Automático"
                } else {
                    getString(R.string.tile_subtitle_off)
                }
            }
        }

        tile.updateTile()
    }

    /**
     * Registers a ContentObserver on private_dns_mode and private_dns_specifier
     * so that if the user edits DNS manually in Android Settings, the Tile adapts instantly.
     */
    private fun registerDnsObserver() {
        if (contentObserver != null) return

        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.d(TAG, "DNS setting changed in system: $uri")
                updateTileState()
            }
        }

        try {
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(DnsHelper.SETTING_PRIVATE_DNS_MODE),
                false,
                contentObserver!!
            )
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(DnsHelper.SETTING_PRIVATE_DNS_SPECIFIER),
                false,
                contentObserver!!
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error registering ContentObserver", e)
        }
    }

    private fun unregisterDnsObserver() {
        contentObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering ContentObserver", e)
            }
            contentObserver = null
        }
    }

    /**
     * Collapses notification shade and opens MainActivity with setup guidance.
     */
    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterDnsObserver()
    }
}

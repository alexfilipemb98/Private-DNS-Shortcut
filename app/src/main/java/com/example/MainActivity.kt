package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val dnsState = mutableStateOf(DnsUiState())
    private val handler = Handler(Looper.getMainLooper())
    private var contentObserver: ContentObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        refreshDnsState()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        DnsTopAppBar(
                            hasPermission = dnsState.value.hasPermission,
                            onRefresh = { refreshDnsState() }
                        )
                    }
                ) { innerPadding ->
                    DnsMainScreen(
                        state = dnsState.value,
                        modifier = Modifier.padding(innerPadding),
                        onToggle = {
                            toggleDns()
                        },
                        onSaveHostname = { newHostname ->
                            DnsHelper.setConfiguredHostname(this, newHostname)
                            // If DNS is currently ON, update the specifier immediately
                            if (dnsState.value.isHostnameMode && dnsState.value.hasPermission) {
                                DnsHelper.turnOn(this, newHostname)
                            }
                            refreshDnsState()
                            Toast.makeText(this, getString(R.string.msg_dns_saved), Toast.LENGTH_SHORT).show()
                        },
                        onCopyAdbCommand = {
                            copyToClipboard(DnsHelper.getAdbCommand(this))
                        },
                        onOpenSystemSettings = {
                            openNetworkSettings()
                        },
                        onRefresh = {
                            refreshDnsState()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDnsState()
        registerDnsObserver()
    }

    override fun onPause() {
        super.onPause()
        unregisterDnsObserver()
    }

    private fun refreshDnsState() {
        val hasPerm = DnsHelper.hasPermission(this)
        val mode = DnsHelper.getCurrentMode(this)
        val specifier = DnsHelper.getCurrentSpecifier(this)
        val configured = DnsHelper.getConfiguredHostname(this)

        dnsState.value = DnsUiState(
            hasPermission = hasPerm,
            mode = mode,
            specifier = specifier,
            configuredHostname = configured,
            adbCommand = DnsHelper.getAdbCommand(this)
        )
    }

    private fun toggleDns() {
        if (!dnsState.value.hasPermission) {
            Toast.makeText(
                this,
                getString(R.string.msg_adb_required),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val success = DnsHelper.toggle(this)
        if (!success) {
            Toast.makeText(this, getString(R.string.msg_dns_change_failed), Toast.LENGTH_SHORT).show()
        }
        refreshDnsState()
    }

    private fun registerDnsObserver() {
        if (contentObserver != null) return

        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                refreshDnsState()
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
        } catch (_: Exception) {}
    }

    private fun unregisterDnsObserver() {
        contentObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {}
            contentObserver = null
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.label_adb_command), text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.msg_adb_copied), Toast.LENGTH_SHORT).show()
    }

    private fun openNetworkSettings() {
        try {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.msg_settings_open_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

data class DnsUiState(
    val hasPermission: Boolean = false,
    val mode: String = DnsHelper.MODE_OFF,
    val specifier: String? = null,
    val configuredHostname: String = DnsHelper.DEFAULT_HOSTNAME,
    val adbCommand: String = ""
) {
    val isHostnameMode: Boolean get() = mode == DnsHelper.MODE_HOSTNAME
    val isOffMode: Boolean get() = mode == DnsHelper.MODE_OFF
    val isOpportunistic: Boolean get() = mode == DnsHelper.MODE_OPPORTUNISTIC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsTopAppBar(
    hasPermission: Boolean,
    onRefresh: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (hasPermission) stringResource(R.string.top_bar_tile_ready) else stringResource(R.string.top_bar_adb_required),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasPermission) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        actions = {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh_state)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DnsMainScreen(
    state: DnsUiState,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onSaveHostname: (String) -> Unit,
    onCopyAdbCommand: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    val scrollState = rememberScrollState()
    var hostnameInput by remember(state.configuredHostname) {
        mutableStateOf(state.configuredHostname)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Current State Card (Hero Card)
        StatusHeroCard(
            state = state,
            onToggle = onToggle
        )

        // 2. ADB Permission Card (Important setup step)
        AdbPermissionCard(
            hasPermission = state.hasPermission,
            adbCommand = state.adbCommand,
            onCopyAdb = onCopyAdbCommand,
            onCheckPermission = onRefresh
        )

        // 3. DNS Hostname Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.title_dns_address),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.desc_dns_address),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = hostnameInput,
                    onValueChange = { hostnameInput = it },
                    label = { Text(stringResource(R.string.label_hostname)) },
                    placeholder = { Text(stringResource(R.string.placeholder_hostname)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hostname_input"),
                    trailingIcon = {
                        if (hostnameInput.trim() != state.configuredHostname) {
                            IconButton(
                                onClick = { onSaveHostname(hostnameInput) },
                                modifier = Modifier.testTag("save_hostname_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.cd_save_address),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.title_popular_presets),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val presets = listOf(
                        "dns.adguard.com" to stringResource(R.string.preset_adguard_default),
                        "family.adguard-dns.com" to stringResource(R.string.preset_adguard_family),
                        "one.one.one.one" to stringResource(R.string.preset_cloudflare),
                        "dns.quad9.net" to stringResource(R.string.preset_quad9)
                    )

                    presets.forEach { (presetHost, presetLabel) ->
                        val isSelected = hostnameInput.trim() == presetHost
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                hostnameInput = presetHost
                                onSaveHostname(presetHost)
                            },
                            label = { Text(presetLabel, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onSaveHostname(hostnameInput) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apply_button"),
                    enabled = hostnameInput.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_save_apply))
                }
            }
        }

        // 4. Instructions for Quick Settings Tile
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.title_how_to_add),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                TileStepItem(
                    stepNumber = "1",
                    title = stringResource(R.string.step1_title),
                    description = stringResource(R.string.step1_desc)
                )

                TileStepItem(
                    stepNumber = "2",
                    title = stringResource(R.string.step2_title),
                    description = stringResource(R.string.step2_desc)
                )

                TileStepItem(
                    stepNumber = "3",
                    title = stringResource(R.string.step3_title),
                    description = stringResource(R.string.step3_desc)
                )

                TileStepItem(
                    stepNumber = "4",
                    title = stringResource(R.string.step4_title),
                    description = stringResource(R.string.step4_desc)
                )
            }
        }

        // 5. System Settings Shortcut
        OutlinedButton(
            onClick = onOpenSystemSettings,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("system_settings_button")
        ) {
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.btn_open_network_settings))
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun StatusHeroCard(
    state: DnsUiState,
    onToggle: () -> Unit
) {
    val isActive = state.isHostnameMode
    val containerColor by animateColorAsState(
        targetValue = when {
            !state.hasPermission -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            isActive -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "hero_bg"
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("status_hero_card"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.tile_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                isActive -> stringResource(R.string.status_active_hostname)
                                state.isOpportunistic -> stringResource(R.string.status_automatic)
                                else -> stringResource(R.string.status_disabled)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Interactive Switch inside the card
                Switch(
                    checked = isActive,
                    onCheckedChange = { onToggle() },
                    enabled = state.hasPermission,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("dns_toggle_switch")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subtitle / Detail info row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.label_server_in_use),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isActive) (state.specifier ?: state.configuredHostname) else stringResource(R.string.status_none_inactive),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun AdbPermissionCard(
    hasPermission: Boolean,
    adbCommand: String,
    onCopyAdb: () -> Unit,
    onCheckPermission: () -> Unit
) {
    if (hasPermission) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2E7D32).copy(alpha = 0.12f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.perm_active_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20)
                    )
                    Text(
                        text = stringResource(R.string.perm_active_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.perm_required_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.perm_required_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Monospace Code Block
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .clickable { onCopyAdb() }
                        .padding(12.dp)
                ) {
                    Text(
                        text = adbCommand,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF38BDF8),
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCopyAdb,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_adb_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_copy_command))
                    }

                    OutlinedButton(
                        onClick = onCheckPermission,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("check_permission_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_verify))
                    }
                }
            }
        }
    }
}

@Composable
fun TileStepItem(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Kept for test compatibility
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        DnsMainScreen(
            state = DnsUiState(hasPermission = true, mode = DnsHelper.MODE_HOSTNAME),
            onToggle = {},
            onSaveHostname = {},
            onCopyAdbCommand = {},
            onOpenSystemSettings = {},
            onRefresh = {}
        )
    }
}

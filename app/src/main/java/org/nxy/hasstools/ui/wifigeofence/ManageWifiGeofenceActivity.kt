package org.nxy.hasstools.ui.wifigeofence

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.nxy.hasstools.objects.WifiGeofence
import org.nxy.hasstools.objects.WifiGeofenceViewModel
import org.nxy.hasstools.ui.components.PrimarySwitchCard
import org.nxy.hasstools.ui.components.SettingsBackgroundColor
import org.nxy.hasstools.ui.components.SettingsItemBackgroundColor
import org.nxy.hasstools.ui.components.SettingsItemShape
import org.nxy.hasstools.ui.components.asSettingsContainer
import org.nxy.hasstools.ui.permission.WifiGeofenceGrantPermissionActivity
import org.nxy.hasstools.ui.theme.AppTheme
import org.nxy.hasstools.utils.Json
import org.nxy.hasstools.utils.checkGroupPermissions
import org.nxy.hasstools.utils.wifiGeofencePermissionGroup

class ManageWifiGeofenceActivity : ComponentActivity() {
    private val wifiGeofenceViewModel: WifiGeofenceViewModel by viewModels()

    private val editWifiGeofenceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent = result.data ?: return@registerForActivityResult

                val item = data.getStringExtra("item") ?: return@registerForActivityResult
                val type = data.getIntExtra("type", -1)
                val zoneId = data.getStringExtra("zone_id") ?: ""

                val updatedGeofence = Json.decodeFromString<WifiGeofence>(item)

                println("Updated geofence: $updatedGeofence, type: $type")

                when (type) {
                    -2 -> {
                        wifiGeofenceViewModel.removeGeofenceItem(updatedGeofence)
                    }

                    -1 -> {
                        wifiGeofenceViewModel.addGeofenceItem(updatedGeofence)
                    }

                    else -> {
                        wifiGeofenceViewModel.updateGeofenceItem(zoneId, updatedGeofence)
                    }
                }
            } else {
                println("result: $result")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                WifiGeofenceManagePage(wifiGeofenceViewModel)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun WifiGeofenceManagePage(viewModel: WifiGeofenceViewModel) {
        val config by viewModel.wifiGeofenceConfig.collectAsState()

        var selectedForDeletion by remember { mutableStateOf<String?>(null) }

        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = SettingsBackgroundColor,
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            "地理围栏",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SettingsBackgroundColor
                    )
                )
            },
            floatingActionButton = {
                if (!config.enabled) {
                    return@Scaffold
                }
                FloatingActionButton(onClick = {
                    editWifiGeofenceLauncher.launch(
                        Intent(this, EditWifiGeofenceActivity::class.java).apply {
                            putExtra("type", -1)
                        }
                    )
                }) {
                    Icon(Icons.Rounded.Add, contentDescription = "添加")
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "利用区域内的 Wi-Fi 信号进行辅助定位，能有效规避因定位偏移导致的离开判定，并降低进入判定的延迟。如果你正在使用 device_tracker 的状态触发器（例如“当 XXX 从 在家 变为 任意状态”），请尽可能地为该区域启用地理围栏。",
                    style = MaterialTheme.typography.bodyMedium
                )

                // 全局启用/禁用开关
                PrimarySwitchCard(
                    title = "启用地理围栏",
                    modifier = Modifier.fillMaxWidth(),
                    checked = config.enabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            val allPermissionGranted =
                                checkGroupPermissions(this@ManageWifiGeofenceActivity, wifiGeofencePermissionGroup)

                            if (!allPermissionGranted) {
                                startActivity(
                                    Intent(
                                        this@ManageWifiGeofenceActivity,
                                        WifiGeofenceGrantPermissionActivity::class.java
                                    )
                                )
                                return@PrimarySwitchCard
                            }
                        }
                        viewModel.setEnabled(enabled)
                    }
                )

                println("Config items: ${config.items}")
                if (config.enabled && config.items.isNotEmpty()) {
                    // 地理围栏列表
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .asSettingsContainer(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        config.items.forEach { item ->
                            WifiGeofenceRow(
                                item = item,
                                onItemSelected = { selectedItem ->
                                    editWifiGeofenceLauncher.launch(
                                        Intent(
                                            this@ManageWifiGeofenceActivity, EditWifiGeofenceActivity::class.java
                                        ).apply {
                                            putExtra("type", 0)
                                            putExtra("zone_id", selectedItem.zoneId)
                                            putExtra("item", Json.encodeToString(selectedItem))
                                        }
                                    )
                                }
                            )
                        }
                    }
                } else if (config.enabled) {
                    Text(
                        text = "暂无地理围栏",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }
        }

        // 删除确认对话框
        if (selectedForDeletion != null) {
            val item = config.items.find { it.geofenceId == selectedForDeletion }
            if (item != null) {
                AlertDialog(
                    onDismissRequest = { selectedForDeletion = null },
                    title = { Text("删除地理围栏") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text("确定要删除「${item.geofenceName}」吗？")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.removeGeofenceItem(item)
                            selectedForDeletion = null
                        }) {
                            Text("删除")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedForDeletion = null }) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }

    @Composable
    private fun WifiGeofenceRow(
        item: WifiGeofence,
        onItemSelected: (WifiGeofence) -> Unit = {}
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = { onItemSelected(item) }
                ),
            shape = SettingsItemShape,
            colors = CardDefaults.cardColors(
                containerColor = SettingsItemBackgroundColor
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.geofenceName.ifBlank { "未命名地理围栏" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (item.enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    val enabledFunctions = buildList {
                        if (item.functions.fastEnter) add("快速进入")
                        if (item.functions.leaveProtection) add("离开保护")
                    }

                    Text(
                        text = if (enabledFunctions.isEmpty()) {
                            "未启用任何功能"
                        } else {
                            enabledFunctions.joinToString("、")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = item.enabled,
                    onCheckedChange = { isChecked ->
                        wifiGeofenceViewModel.setGeofenceItemEnabled(
                            item.geofenceId,
                            isChecked
                        )
                    }
                )
            }
        }
    }
}

package org.nxy.hasstools.ui.wifigeofence

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import org.nxy.hasstools.R
import org.nxy.hasstools.objects.GeofenceFunction
import org.nxy.hasstools.objects.UserViewModel
import org.nxy.hasstools.objects.WifiGeofence
import org.nxy.hasstools.objects.WifiGeofenceWifiInfo
import org.nxy.hasstools.ui.components.CommonPage
import org.nxy.hasstools.ui.components.IconDialog
import org.nxy.hasstools.ui.components.TitleCard
import org.nxy.hasstools.ui.theme.AppTheme
import org.nxy.hasstools.utils.Json

internal class EditWifiGeofenceViewModel : ViewModel() {
    val geofenceId = mutableStateOf("")
    val geofenceName = mutableStateOf("")
    val effectUserIds = mutableStateListOf<String>()
    val zoneId = mutableStateOf("")
    val enabled = mutableStateOf(true)
    val fastEnterEnabled = mutableStateOf(true)
    val leaveProtectionEnabled = mutableStateOf(true)
    val ssidItems = mutableStateListOf<WifiGeofenceWifiInfo>()
    val bssidItems = mutableStateListOf<WifiGeofenceWifiInfo>()

    val hasInvalidUserId = mutableStateOf(false)

    val deleteDialogVisible = mutableStateOf(false)
    val exitDialogVisible = mutableStateOf(false)
    val saveDialogVisible = mutableStateOf(false)

    val rawWifiGeofence = mutableStateOf(WifiGeofence())

    fun canSave(): Boolean {
        return geofenceName.value.trim().isNotEmpty() && zoneId.value.trim().isNotEmpty()
    }

    fun hasEdited(): Boolean {
        val rawItem = rawWifiGeofence.value
        return geofenceName.value != rawItem.geofenceName ||
                effectUserIds.toSet() != rawItem.effectUserIds ||
                zoneId.value != rawItem.zoneId ||
                enabled.value != rawItem.enabled ||
                fastEnterEnabled.value != rawItem.functions.fastEnter ||
                leaveProtectionEnabled.value != rawItem.functions.leaveProtection ||
                ssidItems.toSet() != rawItem.ssidList ||
                bssidItems.toSet() != rawItem.bssidList
    }
}

class EditWifiGeofenceActivity : ComponentActivity() {
    private var type = -1

    private var zoneId = ""

    private val userViewModel: UserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel by viewModels<EditWifiGeofenceViewModel>()

        type = intent.getIntExtra("type", -1)

        zoneId = intent.getStringExtra("zone_id") ?: ""

        val wifiGeofence = intent.getStringExtra("item")?.let {
            Json.decodeFromString<WifiGeofence>(it)
        }

        wifiGeofence?.let { item ->
            viewModel.rawWifiGeofence.value = item

            viewModel.geofenceId.value = item.geofenceId
            viewModel.geofenceName.value = item.geofenceName
            viewModel.effectUserIds.addAll(item.effectUserIds)
            viewModel.zoneId.value = item.zoneId
            viewModel.enabled.value = item.enabled
            viewModel.fastEnterEnabled.value = item.functions.fastEnter
            viewModel.leaveProtectionEnabled.value = item.functions.leaveProtection
            viewModel.ssidItems.addAll(item.ssidList)
            viewModel.bssidItems.addAll(item.bssidList)

            if (viewModel.effectUserIds.size != item.effectUserIds.size) {
                viewModel.hasInvalidUserId.value = true
            }
        }

        setContent {
            EditWifiGeofenceUI(viewModel)
        }

        onBackPressedDispatcher.addCallback(this) {
            if (!viewModel.hasEdited()) {
                finish()
            } else if (viewModel.canSave()) {
                viewModel.saveDialogVisible.value = true
            } else {
                viewModel.exitDialogVisible.value = true
            }
        }
    }

    @Composable
    private fun DeleteDialog(viewModel: EditWifiGeofenceViewModel) {
        IconDialog(
            "永久删除", icon = Icons.Default.Delete,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
            iconBackground = MaterialTheme.colorScheme.errorContainer,
            buttons = {
                Row(
                    horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = { viewModel.deleteDialogVisible.value = false }
                    ) {
                        Text("取消")
                    }
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        onClick = {
                            deleteWifiGeofenceItem(viewModel)
                            finish()
                        }
                    ) {
                        Text("确定")
                    }
                }
            }
        ) { viewModel.deleteDialogVisible.value = false }
    }

    @Composable
    private fun ExitDialog(viewModel: EditWifiGeofenceViewModel) {
        IconDialog(
            "放弃修改", icon = R.drawable.ic_edit_off,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
            iconBackground = MaterialTheme.colorScheme.errorContainer,
            buttons = {
                Row(
                    horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = { viewModel.exitDialogVisible.value = false }
                    ) {
                        Text("取消")
                    }
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        onClick = { finish() }
                    ) {
                        Text("确定")
                    }
                }
            }
        ) { viewModel.exitDialogVisible.value = false }
    }

    @Composable
    private fun SaveDialog(viewModel: EditWifiGeofenceViewModel) {
        IconDialog(
            "保存修改", icon = R.drawable.ic_save,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            iconBackground = MaterialTheme.colorScheme.primaryContainer,
            buttons = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        onClick = { finish() }
                    ) {
                        Text("不保存")
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { viewModel.saveDialogVisible.value = false }
                        ) {
                            Text("取消")
                        }
                        TextButton(
                            onClick = {
                                saveWifiGeofenceItem(viewModel)
                                finish()
                            }
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        ) { }
    }

    @Composable
    private fun MultiSelectPairList(itemSet: MutableSet<Pair<String, String>>, onRemove: (Set<String>) -> Unit = {}) {
        var selectedItems by remember { mutableStateOf(emptySet<String>()) }

        val listItems = remember(itemSet) { itemSet.toMutableList() }

        val isAllSelected = listItems.isNotEmpty() && listItems.size == selectedItems.size

        Column(
            modifier = Modifier.heightIn(max = 400.dp)
        ) {
            AnimatedVisibility(
                visible = selectedItems.isNotEmpty(),
                enter = expandIn(expandFrom = Alignment.CenterStart, clip = false) + fadeIn(),
                exit = shrinkOut(shrinkTowards = Alignment.CenterStart) + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedItems.isNotEmpty()) {
                        Text(
                            text = "已选中 ${selectedItems.size} 项",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            modifier = Modifier
                                .height(36.dp)
                                .widthIn(min = 72.dp),
                            contentPadding = PaddingValues(8.dp),
                            enabled = listItems.isNotEmpty(),
                            onClick = {
                                selectedItems = if (isAllSelected) {
                                    emptySet()
                                } else {
                                    listItems.map { it.first }.toSet()
                                }
                            }) {
                            Text(if (isAllSelected) "全不选" else "全选")
                        }

                        Button(
                            modifier = Modifier
                                .height(36.dp)
                                .padding(start = 8.dp)
                                .widthIn(min = 72.dp),
                            contentPadding = PaddingValues(8.dp),
                            onClick = {
                                onRemove(selectedItems)
                                selectedItems = emptySet()
                            },
                            enabled = selectedItems.isNotEmpty(),
                        ) {
                            Text("删除")
                        }
                    }
                }
            }

            LazyColumn {
                items(listItems.size) { index ->
                    val isSelected = selectedItems.contains(listItems[index].first)

                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { -40 }),
                        exit = slideOutVertically(targetOffsetY = { 40 })
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable {
                                    selectedItems = if (isSelected) {
                                        selectedItems - listItems[index].first
                                    } else {
                                        selectedItems + listItems[index].first
                                    }
                                }, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                modifier = Modifier.padding(8.dp), checked = isSelected, onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = listItems[index].first, style = MaterialTheme.typography.bodyLarge)
                                if (listItems[index].second.isNotEmpty()) {
                                    Text(text = listItems[index].second, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun EditWifiGeofenceUI(viewModel: EditWifiGeofenceViewModel) {
        val geofenceName = viewModel.geofenceName

        val effectUserIds = viewModel.effectUserIds

        val zoneId = viewModel.zoneId

        val enabledState = viewModel.enabled

        val fastEnterState = viewModel.fastEnterEnabled

        val leaveProtectionState = viewModel.leaveProtectionEnabled

        val ssidItems = viewModel.ssidItems

        val bssidItems = viewModel.bssidItems

        AppTheme {
            CommonPage {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp, 0.dp)
                ) {
                    Text(
                        text = if (type == -1) "创建地理围栏" else "修改地理围栏",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(24.dp)
                    )

                    TitleCard(
                        title = "基本信息"
                    ) {
                        OutlinedTextField(
                            value = geofenceName.value,
                            onValueChange = {
                                geofenceName.value = it
                            },
                            label = { Text("地理围栏名称") },
                            placeholder = { Text("foo") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = zoneId.value,
                            onValueChange = {
                                zoneId.value = it
                            },
                            label = { Text("区域 ID") },
                            placeholder = { Text("zone.foo_bar") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    enabledState.value = !enabledState.value
                                }
                        ) {
                            Switch(
                                checked = enabledState.value,
                                onCheckedChange = {
                                    enabledState.value = it
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (enabledState.value) "已启用" else "已禁用")
                            }
                        }
                    }

                    TitleCard(
                        title = "功能",
                        introduction = "选择此地理围栏启用的辅助能力。"
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    fastEnterState.value = !fastEnterState.value
                                }
                        ) {
                            Switch(
                                checked = fastEnterState.value,
                                onCheckedChange = {
                                    fastEnterState.value = it
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("快速进入")
                                Text(
                                    text = "检测到目标 Wi-Fi 时立即视为进入区域。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    leaveProtectionState.value = !leaveProtectionState.value
                                }
                        ) {
                            Switch(
                                checked = leaveProtectionState.value,
                                onCheckedChange = {
                                    leaveProtectionState.value = it
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("离开保护")
                                Text(
                                    text = "位置偏移时继续保持当前区域，避免误报离开。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    TitleCard(
                        title = "生效用户", introduction = "此地理围栏将对选中的用户生效。"
                    ) {
                        val userConfig by userViewModel.userConfig.collectAsState()

                        FlowRow {
                            FilterChip(
                                selected = effectUserIds.isEmpty(),
                                onClick = {
                                    if (effectUserIds.isNotEmpty()) {
                                        effectUserIds.clear()
                                    }
                                },
                                leadingIcon = {
                                    AnimatedVisibility(
                                        visible = effectUserIds.isEmpty(),
                                        enter = expandIn(expandFrom = Alignment.Center) + fadeIn(),
                                        exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Done,
                                            contentDescription = "已选中",
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                },
                                label = { Text("所有用户", maxLines = 1) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp, 0.dp)
                            )

                            userConfig.items.forEach { user ->
                                FilterChip(
                                    selected = effectUserIds.contains(user.userId),
                                    onClick = {
                                        if (effectUserIds.contains(user.userId)) {
                                            effectUserIds.remove(user.userId)
                                        } else {
                                            effectUserIds.add(user.userId)
                                        }
                                    },
                                    leadingIcon = {
                                        AnimatedVisibility(
                                            visible = effectUserIds.contains(user.userId),
                                            enter = expandIn(expandFrom = Alignment.Center) + fadeIn(),
                                            exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Done,
                                                contentDescription = "已选中",
                                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                                            )
                                        }
                                    },
                                    label = { Text(user.userName, maxLines = 1) },
                                    modifier = Modifier.padding(4.dp, 0.dp)
                                )
                            }

                            viewModel.effectUserIds.filter { userId ->
                                userConfig.items.none { user -> user.userId == userId }
                            }.forEach { invalidUserId ->
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        effectUserIds.remove(invalidUserId)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Done,
                                            contentDescription = "已选中",
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "未知用户",
                                            maxLines = 1,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                textDecoration = TextDecoration.LineThrough
                                            )
                                        )
                                    },
                                    modifier = Modifier.padding(4.dp, 0.dp)
                                )
                            }
                        }
                    }

                    TitleCard(
                        title = "附近的 Wi-Fi 名称",
                        introduction = "根据列表中的 Wi-Fi 名称进行区域定位。请确保这些设备仅存在于目标区域内，以避免误判。"
                    ) {
                        MultiSelectPairList(
                            itemSet = ssidItems.map { it.ssid to it.bssid }.toMutableSet(),
                            onRemove = { removedItems ->
                                ssidItems.removeAll { removedItems.contains(it.ssid) }
                            })

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WifiSsidScanner(
                                modifier = Modifier.weight(1f)
                            ) { wifiInfo ->
                                if (ssidItems.none { it.ssid == wifiInfo.ssid }) {
                                    ssidItems.add(wifiInfo)
                                }
                            }

                            ManualSsidInput { ssid, remark ->
                                if (ssidItems.none { it.ssid == ssid }) {
                                    ssidItems.add(WifiGeofenceWifiInfo(ssid, remark))
                                }
                            }
                        }
                    }

                    TitleCard(
                        title = "附近的 Wi-Fi 标识符",
                        introduction = "根据列表中的 Wi-Fi 标识符进行区域定位。"
                    ) {
                        MultiSelectPairList(
                            itemSet = bssidItems.map { it.bssid to it.ssid }.toMutableSet(),
                            onRemove = { removedItems ->
                                bssidItems.removeAll { removedItems.contains(it.bssid) }
                            })

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WifiBssidScanner(
                                modifier = Modifier.weight(1f)
                            ) { wifiInfo ->
                                if (bssidItems.none { it.bssid == wifiInfo.bssid }) {
                                    bssidItems.add(wifiInfo)
                                }
                            }

                            ManualBssidInput { bssid, remark ->
                                if (bssidItems.none { it.bssid == bssid }) {
                                    bssidItems.add(WifiGeofenceWifiInfo(remark, bssid))
                                }
                            }
                        }
                    }

                    if (type != -1) {
                        ElevatedButton(
                            onClick = { viewModel.deleteDialogVisible.value = true },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp)
                                .height(48.dp)
                        ) {
                            Text("永久删除")
                        }
                    }
                }
            }

            SaveFabButton(viewModel, viewModel.canSave() && viewModel.hasEdited())

            if (viewModel.deleteDialogVisible.value) {
                DeleteDialog(viewModel)
            }

            if (viewModel.exitDialogVisible.value) {
                ExitDialog(viewModel)
            }

            if (viewModel.saveDialogVisible.value) {
                SaveDialog(viewModel)
            }
        }
    }

    @Composable
    private fun SaveFabButton(viewModel: EditWifiGeofenceViewModel, visible: Boolean = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(align = Alignment.BottomEnd)
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        saveWifiGeofenceItem(viewModel)
                        finish()
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(24.dp)
                        .navigationBarsPadding()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_save), contentDescription = "保存"
                    )
                }
            }
        }
    }

    @Composable
    private fun WifiSsidScanner(
        modifier: Modifier = Modifier,
        onWifiSelected: (WifiGeofenceWifiInfo) -> Unit
    ) {
        var showDialog by remember { mutableStateOf(false) }
        var groupedWifiList by remember { mutableStateOf<Map<String, List<ScanResult>>>(emptyMap()) }

        Column(
            modifier = modifier
        ) {
            FilledTonalButton(
                onClick = {
                    val rawWifiList =
                        scanWifi(getSystemService(WifiManager::class.java)).filter { it.wifiSsid.toString() != "" }
                            .sortedByDescending { it.level }

                    groupedWifiList = rawWifiList.groupBy {
                        it.wifiSsid.toString().removeSurrounding("\"")
                    }
                    showDialog = true
                }, modifier = Modifier.fillMaxWidth()
            ) {
                Text("扫描并添加")
            }
        }

        if (showDialog) {
            WifiSelectionDialog(
                wifiList = groupedWifiList,
                onDismiss = { showDialog = false },
                onRefresh = {
                    val rawWifiList =
                        scanWifi(getSystemService(WifiManager::class.java)).filter { it.wifiSsid.toString() != "" }
                            .sortedByDescending { it.level }

                    groupedWifiList = rawWifiList.groupBy {
                        it.wifiSsid.toString().removeSurrounding("\"")
                    }
                },
                onWifiSelected = { ssid, scanResults ->
                    val firstResult = scanResults.first()
                    if (scanResults.size > 1) {
                        onWifiSelected(
                            WifiGeofenceWifiInfo(
                                ssid, firstResult.BSSID + " 等 ${scanResults.size} 个"
                            )
                        )
                    } else {
                        onWifiSelected(
                            WifiGeofenceWifiInfo(
                                ssid, firstResult.BSSID
                            )
                        )
                    }
                    showDialog = false
                }
            )
        }
    }

    @Composable
    private fun WifiBssidScanner(
        modifier: Modifier = Modifier,
        onWifiSelected: (WifiGeofenceWifiInfo) -> Unit
    ) {
        var showDialog by remember { mutableStateOf(false) }
        var wifiList by remember { mutableStateOf<List<ScanResult>>(emptyList()) }

        Column(
            modifier = modifier,
        ) {
            FilledTonalButton(
                onClick = {
                    wifiList = scanWifi(getSystemService(WifiManager::class.java)).sortedByDescending { it.level }
                    showDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("扫描并添加")
            }
        }

        if (showDialog) {
            WifiBssidSelectionDialog(
                wifiList = wifiList,
                onDismiss = { showDialog = false },
                onRefresh = {
                    wifiList = scanWifi(getSystemService(WifiManager::class.java)).sortedByDescending { it.level }
                },
                onWifiSelected = { result ->
                    onWifiSelected(
                        WifiGeofenceWifiInfo(
                            result.wifiSsid.toString().removeSurrounding("\""), result.BSSID
                        )
                    )
                    showDialog = false
                }
            )
        }
    }

    @Composable
    private fun ManualSsidInput(onSsidAdded: (String, String) -> Unit) {
        var showDialog by remember { mutableStateOf(false) }
        var ssidText by remember { mutableStateOf("") }
        var remarkText by remember { mutableStateOf("") }

        OutlinedButton(
            onClick = {
                ssidText = ""
                remarkText = ""
                showDialog = true
            },
            modifier = Modifier.width(80.dp)
        ) {
            Text("手动")
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("手动添加 Wi-Fi 名称") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = ssidText,
                            onValueChange = { ssidText = it },
                            label = { Text("SSID (Wi-Fi 名称)") },
                            placeholder = { Text("例如：MyWiFi") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = remarkText,
                            onValueChange = { remarkText = it },
                            label = { Text("备注 (可选)") },
                            placeholder = { Text("例如：家里的WiFi") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { showDialog = false }) {
                            Text("取消")
                        }
                        Button(
                            onClick = {
                                if (ssidText.trim().isNotEmpty()) {
                                    onSsidAdded(ssidText.trim(), remarkText.trim())
                                    showDialog = false
                                }
                            },
                            enabled = ssidText.trim().isNotEmpty()
                        ) {
                            Text("确定")
                        }
                    }
                }
            )
        }
    }

    @Composable
    private fun ManualBssidInput(onBssidAdded: (String, String) -> Unit) {
        var showDialog by remember { mutableStateOf(false) }
        var bssidText by remember { mutableStateOf("") }
        var remarkText by remember { mutableStateOf("") }

        OutlinedButton(
            onClick = {
                bssidText = ""
                remarkText = ""
                showDialog = true
            },
            modifier = Modifier.width(80.dp)
        ) {
            Text("手动")
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("手动添加 Wi-Fi 标识符") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = bssidText,
                            onValueChange = { bssidText = it },
                            label = { Text("BSSID (Wi-Fi 标识符)") },
                            placeholder = { Text("例如：aa:bb:cc:dd:ee:ff") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = remarkText,
                            onValueChange = { remarkText = it },
                            label = { Text("备注 (可选)") },
                            placeholder = { Text("例如：客厅路由器") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { showDialog = false }) {
                            Text("取消")
                        }
                        Button(
                            onClick = {
                                if (bssidText.trim().isNotEmpty()) {
                                    onBssidAdded(bssidText.trim(), remarkText.trim())
                                    showDialog = false
                                }
                            },
                            enabled = bssidText.trim().isNotEmpty()
                        ) {
                            Text("确定")
                        }
                    }
                }
            )
        }
    }

    private fun scanWifi(wifiManager: WifiManager): List<ScanResult> {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        return wifiManager.scanResults
    }

    @Preview(showBackground = true)
    @Composable
    private fun PreviewEditWifiGeofenceUI() {
        val viewModel = EditWifiGeofenceViewModel().apply {
            zoneId.value = "zone-1"
            ssidItems.addAll(
                listOf(
                    WifiGeofenceWifiInfo("SSID-1", "BSSID-1"),
                    WifiGeofenceWifiInfo("SSID-2", "BSSID-2"),
                    WifiGeofenceWifiInfo("SSID-3", "BSSID-3"),
                )
            )
            bssidItems.addAll(
                listOf(
                    WifiGeofenceWifiInfo("SSID-4", "BSSID-4"),
                    WifiGeofenceWifiInfo("SSID-5", "BSSID-5"),
                    WifiGeofenceWifiInfo("SSID-6", "BSSID-6"),
                )
            )
        }

        EditWifiGeofenceUI(viewModel)
    }

    private fun saveWifiGeofenceItem(viewModel: EditWifiGeofenceViewModel) {
        val geofenceId = viewModel.geofenceId.value
        val geofenceName = viewModel.geofenceName.value
        val effectUserIds = viewModel.effectUserIds.toSet()
        var zoneId = viewModel.zoneId.value.trim()
        val enabled = viewModel.enabled.value
        val functions = GeofenceFunction(
            fastEnter = viewModel.fastEnterEnabled.value,
            leaveProtection = viewModel.leaveProtectionEnabled.value,
        )
        val ssidList = viewModel.ssidItems.toSet()
        val bssidList = viewModel.bssidItems.toSet()

        if (!zoneId.startsWith("zone.")) {
            zoneId = "zone.$zoneId"
        }

        println("zoneId: $zoneId")
        println("ssidList: $ssidList")
        println("bssidList: $bssidList")

        // 通过activityResult返回，然后关闭当前页面
        val intent = Intent()
        intent.putExtra("type", type)
        intent.putExtra("zone_id", this@EditWifiGeofenceActivity.zoneId)

        // 新建地理围栏
        intent.putExtra(
            "item",
            Json.encodeToString(
                if (type == -1) {
                    WifiGeofence(
                        geofenceName = geofenceName,
                        effectUserIds = effectUserIds,
                        zoneId = zoneId,
                        enabled = enabled,
                        functions = functions,
                        ssidList = ssidList,
                        bssidList = bssidList,
                    )
                } else {
                    WifiGeofence(
                        geofenceId = geofenceId,
                        geofenceName = geofenceName,
                        effectUserIds = effectUserIds,
                        zoneId = zoneId,
                        enabled = enabled,
                        functions = functions,
                        ssidList = ssidList,
                        bssidList = bssidList,
                    )
                }
            )
        )

        setResult(RESULT_OK, intent)
    }

    private fun deleteWifiGeofenceItem(viewModel: EditWifiGeofenceViewModel) {
        val intent = Intent()
        intent.putExtra("type", -2)
        intent.putExtra("zone_id", zoneId)
        intent.putExtra("item", Json.encodeToString(viewModel.rawWifiGeofence.value))
        setResult(RESULT_OK, intent)
    }

    @Composable
    private fun WifiSelectionDialog(
        wifiList: Map<String, List<ScanResult>>,
        onDismiss: () -> Unit,
        onRefresh: () -> Unit,
        onWifiSelected: (String, List<ScanResult>) -> Unit
    ) {
        AlertDialog(onDismissRequest = onDismiss, title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("选择 Wi-Fi 名称")
                    Text(
                        text = "共 ${wifiList.size} 个网络",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .height(36.dp)
                        .width(36.dp),
                    contentPadding = PaddingValues()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }, text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(wifiList.size) { index ->
                    val (ssid, scanResults) = wifiList.toList()[index]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onWifiSelected(ssid, scanResults) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_wifi),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    ssid,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                scanResults.forEach { result ->
                                    Text(
                                        result.BSSID,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }, confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        })
    }

    @Composable
    private fun WifiBssidSelectionDialog(
        wifiList: List<ScanResult>, onDismiss: () -> Unit, onRefresh: () -> Unit, onWifiSelected: (ScanResult) -> Unit
    ) {
        AlertDialog(onDismissRequest = onDismiss, title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("添加 Wi-Fi 标识符")
                    Text(
                        text = "共 ${wifiList.size} 个设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .height(36.dp)
                        .width(36.dp),
                    contentPadding = PaddingValues()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }, text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(wifiList.size) { index ->
                    val result = wifiList[index]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onWifiSelected(result) }, colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_wifi),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    result.wifiSsid.toString().removeSurrounding("\""),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    result.BSSID,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }, confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        })
    }
}

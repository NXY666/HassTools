package org.nxy.hasstools.ui.network

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType.Companion.PrimaryNotEditable
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import org.nxy.hasstools.objects.CommonNetworkPreference
import org.nxy.hasstools.objects.DefaultNetworkPreference
import org.nxy.hasstools.objects.NetworkPreference
import org.nxy.hasstools.objects.NetworkRequirement
import org.nxy.hasstools.objects.NetworkViewModel
import org.nxy.hasstools.objects.ProxyRequirement
import org.nxy.hasstools.objects.TransportType
import org.nxy.hasstools.objects.VpnNetworkPreference
import org.nxy.hasstools.ui.components.PrimarySwitchCard
import org.nxy.hasstools.ui.components.SettingsBackgroundColor
import org.nxy.hasstools.ui.components.SettingsItemBackgroundColor
import org.nxy.hasstools.ui.components.SettingsItemShape
import org.nxy.hasstools.ui.components.SwitchSettingsItem
import org.nxy.hasstools.ui.components.asSettingsContainer
import org.nxy.hasstools.ui.theme.AppTheme
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableListItemScope

internal class EditNetworkPreferenceViewModel : ViewModel() {
    val networkId = mutableStateOf("")

    // 通用网络偏好项字段
    val transportType = mutableStateOf<TransportType?>(null)
    val networkRequirement = mutableStateOf(NetworkRequirement.VALIDATED)
    val isNotMetered = mutableStateOf(false)

    // CommonNetworkPreference 特有字段
    val proxyRequirement = mutableStateOf(ProxyRequirement.NONE)

    // VpnNetworkPreference 特有字段
    val allowTransportTypes = mutableStateOf(setOf<TransportType>())

    fun canSave(): Boolean {
        return transportType.value != null
    }

    fun reset() {
        networkId.value = ""
        transportType.value = null
        networkRequirement.value = NetworkRequirement.VALIDATED
        isNotMetered.value = false
        proxyRequirement.value = ProxyRequirement.NONE
        allowTransportTypes.value = setOf()
    }
}

class EditNetworkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val networkViewModel by viewModels<NetworkViewModel>()

        setContent {
            AppTheme {
                EditNetworkPage(networkViewModel)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun EditNetworkPage(viewModel: NetworkViewModel) {
        val config by viewModel.networkConfig.collectAsState()
        val haptic = LocalHapticFeedback.current

        var editingPreference by remember { mutableStateOf<NetworkPreference?>(null) }
        var editing by remember { mutableStateOf(false) }

        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = SettingsBackgroundColor,
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            "网络偏好",
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
                    editing = true
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
                    text = "我们通常使用 HTTP 访问 Home Assistant。在连接公共 Wi-Fi 或代理等不可信网络时，通信内容可能被截获。你可以通过自定义网络偏好（如仅使用移动数据）来保护与 Home Assistant 的连接。",
                    style = MaterialTheme.typography.bodyMedium
                )

                // 全局启用/禁用开关
                PrimarySwitchCard(
                    title = "启用网络偏好设置",
                    modifier = Modifier.fillMaxWidth(),
                    checked = config.enabled,
                    onCheckedChange = { enabled ->
                        viewModel.setEnabled(enabled)
                    }
                )

                if (config.enabled) {
                    val customPreferences = config.items.dropLastWhile { it is DefaultNetworkPreference }
                    val defaultPreference = config.items.filterIsInstance<DefaultNetworkPreference>().lastOrNull()
                        ?: DefaultNetworkPreference()

                    // 网络配置列表
                    ReorderableColumn(
                        list = customPreferences,
                        onSettle = { fromIndex, toIndex ->
                            viewModel.reorder(fromIndex, toIndex)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        modifier = Modifier.asSettingsContainer(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) { index, item, isDragging ->
                        key(item.networkId) {
                            ReorderableItem {
                                NetworkPreferenceRow(
                                    item = item,
                                    isDragging = isDragging,
                                    onEdit = {
                                        if (item !is DefaultNetworkPreference) {
                                            editing = true
                                            editingPreference = item
                                        }
                                    },
                                    scope = this,
                                    haptic = haptic
                                )
                            }
                        }
                    }

                    SwitchSettingsItem(
                        title = "允许回退",
                        description = "当所有自定义网络偏好不可用时，使用系统默认网络发送请求。",
                        modifier = Modifier
                            .fillMaxWidth()
                            .asSettingsContainer(),
                        checked = defaultPreference.enabled,
                        onCheckedChange = { enabled ->
                            viewModel.setDefaultFallbackEnabled(enabled)
                        }
                    )
                }
            }
        }

        if (editing) {
            EditNetworkPreferenceDialog(
                initPreference = editingPreference,
                onDismiss = {
                    editing = false
                    editingPreference = null
                },
                onConfirm = { pref ->
                    val oldPref = editingPreference
                    if (oldPref != null) {
                        viewModel.updatePreference(oldPref.networkId, pref)
                    } else {
                        viewModel.addPreference(pref)
                    }

                    editing = false
                    editingPreference = null
                },
                onDelete = if (editingPreference != null) {
                    { id: String ->
                        viewModel.removePreference(id)
                    }
                } else {
                    null
                }
            )
        }
    }

    @Composable
    private fun NetworkPreferenceRow(
        item: NetworkPreference,
        isDragging: Boolean,
        onEdit: () -> Unit,
        scope: ReorderableListItemScope,
        haptic: HapticFeedback,
    ) {
        val item = item
        if (item is DefaultNetworkPreference) {
            return
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = SettingsItemShape,
            colors = CardDefaults.cardColors(
                containerColor = if (isDragging) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    SettingsItemBackgroundColor
                }
            )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        text = when (item) {
                            is DefaultNetworkPreference -> throw IllegalArgumentException(
                                "DefaultNetworkPreference should not be used here."
                            )

                            is CommonNetworkPreference -> item.transportType?.label ?: "未知"
                            is VpnNetworkPreference -> "VPN"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    when (item) {
                        is DefaultNetworkPreference -> throw IllegalArgumentException(
                            "DefaultNetworkPreference is not supported"
                        )

                        is CommonNetworkPreference -> {
                            if (item.networkRequirement != NetworkRequirement.NONE || item.isNotMetered || item.proxyRequirement != ProxyRequirement.NONE) {
                                Spacer(Modifier.height(8.dp))

                                // 副标题：显示网络要求、非计费网络和代理要求标签
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (item.networkRequirement != NetworkRequirement.NONE) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                    shape = MaterialTheme.shapes.small
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                item.networkRequirement.label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (item.isNotMetered) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                                    shape = MaterialTheme.shapes.small
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                "非计费",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                    if (item.proxyRequirement == ProxyRequirement.FORBIDDEN) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                                    shape = MaterialTheme.shapes.small
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                "禁止代理",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                    if (item.proxyRequirement == ProxyRequirement.REQUIRED) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                                    shape = MaterialTheme.shapes.small
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                "需要代理",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is VpnNetworkPreference -> {
                            if (item.networkRequirement != NetworkRequirement.NONE || item.isNotMetered || item.allowTransportTypes.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))

                                // 副标题：显示网络要求、非计费网络和允许的传输类型标签
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (item.networkRequirement != NetworkRequirement.NONE) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                    shape = MaterialTheme.shapes.small
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                item.networkRequirement.label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (item.isNotMetered) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                "非计费",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onEdit
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "修改")
                }

                FilledTonalIconButton(
                    modifier = with(scope) {
                        Modifier
                            .size(40.dp)
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                    },
                    onClick = {}
                ) {
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = "拖动排序",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @ExperimentalMaterial3Api
    @Composable
    private fun EditNetworkPreferenceDialog(
        initPreference: NetworkPreference? = null,
        onDismiss: () -> Unit,
        onConfirm: (NetworkPreference) -> Unit,
        onDelete: ((String) -> Unit)? = null
    ) {
        val viewModel by viewModels<EditNetworkPreferenceViewModel>()

        viewModel.reset()

        initPreference?.let {
            viewModel.networkId.value = it.networkId
            when (it) {
                is CommonNetworkPreference -> {
                    viewModel.transportType.value = it.transportType
                    viewModel.networkRequirement.value = it.networkRequirement
                    viewModel.proxyRequirement.value = it.proxyRequirement
                    viewModel.isNotMetered.value = it.isNotMetered
                }

                is VpnNetworkPreference -> {
                    // VPN 网络偏好项没有单独的允许代理选项，只有允许的网络类型和网络要求
                    viewModel.transportType.value = TransportType.VPN
                    viewModel.networkRequirement.value = it.networkRequirement
                    viewModel.allowTransportTypes.value = it.allowTransportTypes
                    viewModel.isNotMetered.value = it.isNotMetered
                }

                is DefaultNetworkPreference -> {
                }
            }
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Column {
                    val titleLabel = if (viewModel.networkId.value.isBlank()) "添加网络偏好" else "修改网络偏好"
                    Text(titleLabel, style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    var transportExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = transportExpanded,
                        onExpandedChange = { transportExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = viewModel.transportType.value?.label ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("网络类型") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = transportExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(type = PrimaryNotEditable),
                            colors = ExposedDropdownMenuDefaults.textFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = transportExpanded,
                            onDismissRequest = { transportExpanded = false }
                        ) {
                            TransportType.entries.forEach { t ->
                                DropdownMenuItem(text = {
                                    Text(t.label)
                                }, onClick = {
                                    viewModel.transportType.value = t
                                    transportExpanded = false
                                })
                            }
                        }
                    }

                    if (viewModel.transportType.value != TransportType.VPN) {
                        // 网络要求下拉框
                        var requirementExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = requirementExpanded,
                            onExpandedChange = { requirementExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = viewModel.networkRequirement.value.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("网络要求") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = requirementExpanded
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(type = PrimaryNotEditable),
                                colors = ExposedDropdownMenuDefaults.textFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = requirementExpanded,
                                onDismissRequest = { requirementExpanded = false }
                            ) {
                                NetworkRequirement.entries.forEach { requirement ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(requirement.label)
                                                if (requirement.description.isNotBlank()) {
                                                    Text(
                                                        requirement.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.networkRequirement.value = requirement
                                            requirementExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // 代理要求下拉框
                        var proxyExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = proxyExpanded,
                            onExpandedChange = { proxyExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = viewModel.proxyRequirement.value.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("代理要求") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = proxyExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(type = PrimaryNotEditable),
                                colors = ExposedDropdownMenuDefaults.textFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = proxyExpanded,
                                onDismissRequest = { proxyExpanded = false }
                            ) {
                                ProxyRequirement.entries.forEach { requirement ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(requirement.label)
                                                if (requirement.description.isNotBlank()) {
                                                    Text(
                                                        requirement.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.proxyRequirement.value = requirement
                                            proxyExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // 网络要求下拉框
                        var requirementExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = requirementExpanded,
                            onExpandedChange = { requirementExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = viewModel.networkRequirement.value.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("网络要求") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = requirementExpanded
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(type = PrimaryNotEditable),
                                colors = ExposedDropdownMenuDefaults.textFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = requirementExpanded,
                                onDismissRequest = { requirementExpanded = false }
                            ) {
                                NetworkRequirement.entries.forEach { requirement ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(requirement.label)
                                                if (requirement.description.isNotBlank()) {
                                                    Text(
                                                        requirement.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.networkRequirement.value = requirement
                                            requirementExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // 允许网络类型多选下拉框
                        var allowTransportExpanded by remember { mutableStateOf(false) }

                        val selectedTransportLabels = viewModel.allowTransportTypes.value
                            .map { it.label }
                            .sorted()
                            .joinToString(", ")
                        ExposedDropdownMenuBox(
                            expanded = allowTransportExpanded,
                            onExpandedChange = { allowTransportExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = selectedTransportLabels.ifEmpty { "没有要求" },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("允许的网络来源") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = allowTransportExpanded
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(type = PrimaryNotEditable),
                                colors = ExposedDropdownMenuDefaults.textFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = allowTransportExpanded,
                                onDismissRequest = { allowTransportExpanded = false }
                            ) {
                                TransportType.entries.forEach { transportTypeOption ->
                                    if (transportTypeOption == TransportType.VPN) {
                                        // VPN 类型不在允许列表中显示
                                        return@forEach
                                    }
                                    val isSelected = transportTypeOption in viewModel.allowTransportTypes.value
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = null // 由 onClick 处理
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(transportTypeOption.label)
                                            }
                                        },
                                        onClick = {
                                            viewModel.allowTransportTypes.value = if (isSelected) {
                                                viewModel.allowTransportTypes.value - transportTypeOption
                                            } else {
                                                viewModel.allowTransportTypes.value + transportTypeOption
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 非计费网络复选框
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.isNotMetered.value = !viewModel.isNotMetered.value
                            }
                            .padding(0.dp, 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = viewModel.isNotMetered.value,
                            onCheckedChange = { checked ->
                                viewModel.isNotMetered.value = checked
                            }
                        )
                        Column {
                            Text("非计费网络")
                            Text(
                                "排除按量计费的网络",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }, confirmButton = {
                Row {
                    if (onDelete != null && viewModel.networkId.value.isNotBlank()) {
                        TextButton(
                            onClick = {
                                onDelete(viewModel.networkId.value)
                                onDismiss()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text("删除")
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        onClick = {
                            val pref = if (viewModel.transportType.value == TransportType.VPN) {
                                VpnNetworkPreference(
                                    networkRequirement = viewModel.networkRequirement.value,
                                    allowTransportTypes = viewModel.allowTransportTypes.value.ifEmpty {
                                        setOf(
                                            TransportType.WIFI,
                                            TransportType.CELLULAR,
                                            TransportType.ETHERNET,
                                            TransportType.BLUETOOTH
                                        )
                                    },
                                    isNotMetered = viewModel.isNotMetered.value
                                )
                            } else {
                                CommonNetworkPreference(
                                    transportType = viewModel.transportType.value,
                                    networkRequirement = viewModel.networkRequirement.value,
                                    proxyRequirement = viewModel.proxyRequirement.value,
                                    isNotMetered = viewModel.isNotMetered.value
                                )
                            }
                            onConfirm(pref)
                        },
                        enabled = viewModel.canSave()
                    ) { Text("保存") }
                }
            })
    }
}

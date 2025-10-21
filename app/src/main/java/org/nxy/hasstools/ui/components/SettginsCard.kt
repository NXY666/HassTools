package org.nxy.hasstools.ui.components

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

val SettingsBackgroundColor
    @Composable get() = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

val SettingsItemBackgroundColor
    @Composable get() = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }

val SettingsContainerShape: Shape
    @Composable
    inline get() = MaterialTheme.shapes.large

val SettingsItemShape: Shape
    @Composable
    inline get() = MaterialTheme.shapes.extraSmall

@Composable
fun Modifier.asSettingsContainer() = clip(SettingsContainerShape)

@Composable
fun Modifier.asSettingsItem() = clip(SettingsItemShape).background(
    if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
)

@Composable
fun SettingsCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(top = 18.dp, bottom = 4.dp)
            .then(modifier)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 24.dp, end = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )

        Column(
            Modifier
                .padding(16.dp, 0.dp)
                .asSettingsContainer()
        ) {
            content()
        }
    }
}

@Composable
private fun BaseSettingsItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .padding(0.dp, 1.dp)
            .asSettingsItem()
            .clickable(
                enabled = onClick != null && enabled,
                onClick = { onClick?.invoke() }
            )
            .padding(16.dp, 12.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
fun SettingsInstruction(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
        )
        if (description.isEmpty()) return@Column
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alpha(if (enabled) 0.83f else 0.38f),
        )
    }
}

@Composable
fun InputSettingsItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String = "",
    enabled: Boolean = true,
    value: String,
    emptyText: String = "未填写",
    onValueChange: (String) -> Unit,
) {
    var isDialogOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value) }

    BaseSettingsItem(
        modifier = modifier,
        enabled = enabled,
        onClick = {
            if (enabled) {
                draft = value
                isDialogOpen = true
            }
        }
    ) {
        SettingsInstruction(
            title = title,
            description = value.ifBlank { emptyText },
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
    }

    if (isDialogOpen) {
        AlertDialog(
            onDismissRequest = { isDialogOpen = false },
            title = { Text(text = title) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (description.isNotEmpty()) {
                        Text(text = description, style = MaterialTheme.typography.bodySmall)
                    }
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(draft)
                    isDialogOpen = false
                }) {
                    Text(text = "确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { isDialogOpen = false }) {
                    Text(text = "取消")
                }
            }
        )
    }
}

@Composable
fun SwitchSettingsItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String = "",
    enabled: Boolean = true,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    BaseSettingsItem(
        modifier = modifier,
        enabled = enabled,
        onClick = {
            onCheckedChange(!checked)
        }
    ) {
        SettingsInstruction(
            title = title,
            description = description,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            enabled = enabled,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
fun ButtonSettingsItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String = "",
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    BaseSettingsItem(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick
    ) {
        SettingsInstruction(
            title = title,
            description = description,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            }
        )
    }
}

@Composable
fun TextSettingsItem(
    modifier: Modifier = Modifier,
    title: String,
    value: String = "",
    description: String = "",
    enabled: Boolean = true,
    copyable: Boolean = false
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    BaseSettingsItem(
        modifier = modifier,
        enabled = enabled
    ) {
        SettingsInstruction(
            title = title,
            description = value.ifEmpty { description },
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(16.dp))

        if (copyable) {
            IconButton(
                onClick = {
                    if (enabled) {
                        coroutineScope.launch {
                            clipboard.setClipEntry(
                                ClipData.newPlainText("text", value).toClipEntry()
                            )
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "复制",
                    modifier = Modifier.size(20.dp),
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }
        }
    }
}

class RadioSettingsOption<T>(
    val value: T,
    val label: @Composable () -> Unit,
    val description: (@Composable (dismissDialog: () -> Unit) -> Unit)? = null,
    val enabled: Boolean = true,
    val tip: (@Composable () -> Unit)? = null,
)

@Composable
fun <T> RadioSettingsItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String = "",
    enabled: Boolean = true,
    options: List<RadioSettingsOption<T>>,
    value: T,
    valueText: String? = null,
    onSelectChange: (T) -> Unit,
) {
    var isDialogOpen by remember { mutableStateOf(false) }

    BaseSettingsItem(
        modifier = modifier,
        enabled = enabled,
        onClick = {
            if (enabled) {
                isDialogOpen = true
            }
        }
    ) {
        SettingsInstruction(
            title = title,
            description = description,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            modifier = Modifier.widthIn(min = 52.dp),
            textAlign = TextAlign.Center,
            text = valueText ?: value.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (isDialogOpen) {
        AlertDialog(
            onDismissRequest = { isDialogOpen = false },
            title = { Text(text = title) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    options.forEach { option ->
                        val optionEnabled = enabled && option.enabled
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = optionEnabled) {
                                    if (option.value != value) {
                                        onSelectChange(option.value)
                                    }
                                    if (optionEnabled) {
                                        isDialogOpen = false
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = option.value == value,
                                onClick = null,
                                enabled = optionEnabled
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Column(modifier = Modifier.alpha(if (optionEnabled) 1f else 0.38f)) {
                                    option.label()
                                    option.description?.let { descriptionContent ->
                                        descriptionContent { isDialogOpen = false }
                                    }
                                }
                                option.tip?.let { tipContent ->
                                    tipContent()
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isDialogOpen = false }) {
                    Text(text = "关闭")
                }
            }
        )
    }
}


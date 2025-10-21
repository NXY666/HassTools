package org.nxy.hasstools.ui.components

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun PermissionCard(
    permissionLabel: String,
    permissionDesc: String = "",
    permissionCheckFunction: () -> Boolean,
    permissionRequestFunction: () -> Unit,
    permissionRevokedFunction: () -> Unit = {}
) {
    val isPermissionGrantedState = remember { mutableStateOf(false) }
    val isPermissionGranted by isPermissionGrantedState

    val lifecycleOwner = LocalLifecycleOwner.current

    // 使用 LifecycleEventObserver 监听生命周期事件
    val lifecycleObserver = remember {
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isPermissionGrantedState.value = permissionCheckFunction()
                }

                else -> {
//                    println("Lifecycle event: $event")
                }
            }
        }
    }

    // 注册生命周期观察者
    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(
                onClick = {
                    if (!isPermissionGranted) {
                        permissionRequestFunction()
                    } else {
//                            permissionRevokedFunction()
//
//                            Toast.makeText(
//                                context,
//                                "请求成功，权限将在应用退出后撤销。",
//                                Toast.LENGTH_SHORT
//                            ).show()
                    }
                },
                enabled = !isPermissionGranted,
                onClickLabel = "申请权限"
            )
            .background(SettingsItemBackgroundColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isPermissionGranted) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = "状态图标",
                modifier = Modifier
                    .background(
                        color = if (isPermissionGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                    .size(42.dp)
                    .padding(8.dp),
                tint = if (isPermissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            // 位置图标，primary的背景和颜色
            Column {
                Text(
                    text = permissionLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPermissionGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = if (isPermissionGranted) "已授权" else "未授权",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (!isPermissionGranted) {
                    // 申请权限按钮
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Localized description",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        if (permissionDesc.isNotEmpty() && !isPermissionGranted) {
            Text(
                text = permissionDesc,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.Start),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun PermissionCard(
    context: Context,
    permissionLabel: String,
    permissionDesc: String = "",
    permissionName: String,
) {
    return PermissionCard(
        permissionLabel = permissionLabel,
        permissionDesc = permissionDesc,
        permissionCheckFunction = {
            println(
                "Permission $permissionName granted: ${
                    ActivityCompat.checkSelfPermission(
                        context,
                        permissionName
                    ) == PackageManager.PERMISSION_GRANTED
                }"
            )
            ActivityCompat.checkSelfPermission(context, permissionName) == PackageManager.PERMISSION_GRANTED
        },
        permissionRequestFunction = {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(permissionName),
                0
            )
        }
    )
}
package org.nxy.hasstools.ui.permission

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.nxy.hasstools.ui.components.PermissionCard
import org.nxy.hasstools.ui.components.PrimarySwitchCard
import org.nxy.hasstools.ui.components.SettingsBackgroundColor
import org.nxy.hasstools.ui.theme.AppTheme
import org.nxy.hasstools.utils.PermissionItem

open class BaseGrantPermissionActivity(val permissionGroup: List<PermissionItem>) : ComponentActivity() {
    var autoStep = -1

    private fun runAutoStep() {
        autoStep++
        if (autoStep < permissionGroup.size) {
            val permissionItem = permissionGroup[autoStep]
            println("自动授权：$autoStep, $permissionItem ${permissionItem.permissionCheckFunction(this)}")
            if (!permissionItem.permissionCheckFunction(this)) {
                permissionItem.permissionRequestFunction(this)
            } else {
                runAutoStep()
            }
        } else {
            autoStep = -1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LocationGrantPermissionUI()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        println("权限请求结果：$requestCode, $permissions, $grantResults")
    }

    override fun onResume() {
        super.onResume()
        println("GrantPermissionActivity onResume")

        if (autoStep >= 0) {
            if (permissionGroup[autoStep].permissionCheckFunction(this)) {
                runAutoStep()
            } else {
                println("权限请求失败：$autoStep ${permissionGroup[autoStep].permissionLabel}")
                Toast.makeText(this, "授权失败", Toast.LENGTH_SHORT).show()
                autoStep = -1
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LocationGrantPermissionUI() {
        val activity = this

        // 布局
        AppTheme {
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

            val isAllPermissionGrantedState = remember { mutableStateOf(false) }
            val isAllPermissionGranted by isAllPermissionGrantedState

            val lifecycleOwner = LocalLifecycleOwner.current

            // 使用 LifecycleEventObserver 监听生命周期事件
            val lifecycleObserver = remember {
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            isAllPermissionGrantedState.value =
                                permissionGroup.all { it.permissionCheckFunction(activity) }
                        }

                        else -> {
//                            println("Lifecycle event: $event")
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

            Scaffold(
                containerColor = SettingsBackgroundColor,
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = {
                            Text(
                                "授权管理",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        // 进入软件设置的权限设置页面
                        actions = {
                            IconButton(onClick = {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        "package:${activity.packageName}".toUri()
                                    )
                                )
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = "Localized description"
                                )
                            }
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
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    PrimarySwitchCard(
                        title = "全部允许",
                        checked = isAllPermissionGranted,
                        enabled = !isAllPermissionGranted,
                        onCheckedChange = { checked ->
                            if (checked) {
                                runAutoStep()
                            }
                        },
                        switchEnabled = true,
                    )

                    Column(
                        modifier = Modifier.clip(MaterialTheme.shapes.large),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        permissionGroup.forEach { permissionItem ->
                            // 权限卡片
                            PermissionCard(
                                permissionLabel = permissionItem.permissionLabel,
                                permissionDesc = permissionItem.permissionDesc,
                                permissionCheckFunction = {
                                    permissionItem.permissionCheckFunction(activity)
                                },
                                permissionRequestFunction = {
                                    permissionItem.permissionRequestFunction(activity)
                                },
                            ) {
                                permissionItem.permissionRevokedFunction(activity)
                            }
                        }
                    }
                }
            }
        }
    }
}

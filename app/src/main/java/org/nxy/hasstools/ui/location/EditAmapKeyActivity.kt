package org.nxy.hasstools.ui.location

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import org.nxy.hasstools.App
import org.nxy.hasstools.ui.components.InputSettingsItem
import org.nxy.hasstools.ui.components.SettingsBackgroundColor
import org.nxy.hasstools.ui.components.SettingsCard
import org.nxy.hasstools.ui.components.SettingsItemBackgroundColor
import org.nxy.hasstools.ui.components.TextSettingsItem
import org.nxy.hasstools.ui.location.ApkInfo.packageName
import org.nxy.hasstools.ui.theme.AppTheme
import java.security.MessageDigest

internal class EditAmapKeyViewModel : ViewModel() {
    val runtimeApiKey = mutableStateOf("")
}

object ApkInfo {
    val packageName: String = App.context.packageName

    val signingSha1: String? = runCatching {
        val packageInfo = App.context.packageManager.getPackageInfo(
            App.context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        )
        val signingInfo = packageInfo.signingInfo
        val signature = signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray() ?: return@runCatching null
        val digest = MessageDigest.getInstance("SHA1").digest(signature)
        digest.joinToString(":") { byte -> "%02X".format(byte) }
    }.getOrNull()
}

class EditAmapKeyActivity : ComponentActivity() {
    private val viewModel: EditAmapKeyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.runtimeApiKey.value = intent.getStringExtra("runtime_api_key") ?: ""

        setContent {
            AppTheme {
                AmapApiKeyPage(
                    viewModel = viewModel,
                    onSave = { finishWithResult(it) },
                    onClose = { finish() }
                )
            }
        }
    }

    private fun finishWithResult(runtimeApiKey: String) {
        val data = Intent().apply {
            putExtra("runtime_api_key", runtimeApiKey)
        }
        setResult(RESULT_OK, data)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmapApiKeyPage(
    viewModel: EditAmapKeyViewModel,
    onSave: (String) -> Unit,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)

    val keyValue by viewModel.runtimeApiKey

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = SettingsBackgroundColor,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "高德定位 SDK",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onClose()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SettingsBackgroundColor
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            SettingsCard(
                title = "操作指南"
            ) {
                Column(
                    modifier = Modifier
                        .background(SettingsItemBackgroundColor)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "1. 前往高德开放平台，注册账号并完成开发者认证。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "2. 在“控制台 -> 应用管理 -> 我的应用”中点击“创建新应用”。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "3. 填写“新建应用”表单，其中“名称”和“类型”可随意填写。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "4. 在创建的应用菜单上，点击“添加Key”按钮。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "5. 填写“为「xxx」添加Key”表单。其中“Key名称”随意填写、“服务平台”选择“Android平台”、“发布版安全码 SHA1”和“PackageName”根据下方辅助信息填写、“调试版安全码SHA1”不填。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "6. 将新生成的 Key 填入下方“高德 Key”配置项中。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingsCard(title = "授权信息") {
                InputSettingsItem(
                    title = "高德 Key",
                    value = keyValue,
                    onValueChange = {
                        onSave(it)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SettingsCard("辅助信息") {
                TextSettingsItem(
                    title = "应用包名 (PackageName)",
                    value = packageName,
                    modifier = Modifier.fillMaxWidth(),
                    copyable = true
                )

                TextSettingsItem(
                    title = "签名 SHA1 指纹 (安全码)",
                    value = ApkInfo.signingSha1 ?: "获取失败",
                    modifier = Modifier.fillMaxWidth(),
                    copyable = ApkInfo.signingSha1?.isNotBlank() == true
                )
            }
        }
    }
}

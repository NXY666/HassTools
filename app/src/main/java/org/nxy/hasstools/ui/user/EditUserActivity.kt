package org.nxy.hasstools.ui.user

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.nxy.hasstools.R
import org.nxy.hasstools.objects.User
import org.nxy.hasstools.ui.components.CommonPage
import org.nxy.hasstools.ui.components.IconDialog
import org.nxy.hasstools.ui.components.TitleCard
import org.nxy.hasstools.ui.theme.AppTheme
import org.nxy.hasstools.utils.HassClient
import org.nxy.hasstools.utils.Json

internal class EditUserViewModel : ViewModel() {
    val userName = mutableStateOf("")
    val url = mutableStateOf("")
    val token = mutableStateOf("")
    val deviceId = mutableStateOf("")
    val deviceName = mutableStateOf("")
    val webhookId = mutableStateOf("")

    val isReRegistering = mutableStateOf(false)
    val isTesting = mutableStateOf(false)

    val deleteDialogVisible = mutableStateOf(false)
    val exitDialogVisible = mutableStateOf(false)
    val saveDialogVisible = mutableStateOf(false)

    val rawUser = mutableStateOf(User(userType = ""))

    fun canReRegister(): Boolean {
        return !isReRegistering.value
                && url.value.trim().isNotEmpty()
                && token.value.trim().isNotEmpty()
                && deviceId.value.trim().isNotEmpty()
                && deviceName.value.trim().isNotEmpty()
    }

    fun canTest(): Boolean {
        return !isTesting.value
                && url.value.trim().isNotEmpty()
                && webhookId.value.trim().isNotEmpty()
    }

    fun canSave(): Boolean {
        val registerCheck =
            rawUser.value.userType == User.BIND_USER_TYPE
                    || (url.value.trim().isNotEmpty()
                    && token.value.trim().isNotEmpty()
                    && deviceId.value.trim().isNotEmpty()
                    && deviceName.value.trim().isNotEmpty())

        return userName.value.trim().isNotEmpty()
                && registerCheck
                && webhookId.value.trim().isNotEmpty()
    }

    fun hasEdited(): Boolean {
        return userName.value.trim() != rawUser.value.userName
                || url.value.trim() != rawUser.value.url
                || token.value.trim() != rawUser.value.token
                || deviceId.value.trim() != rawUser.value.deviceId
                || deviceName.value.trim() != rawUser.value.deviceName
                || webhookId.value.trim() != rawUser.value.webhookId
    }
}

class EditUserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel by viewModels<EditUserViewModel>()

        // 从传入的 Intent 中获取 UserItem 对象
        val user = intent.getStringExtra("item")?.let {
            Json.decodeFromString<User>(it)
        }

        if (user != null) {
            viewModel.rawUser.value = user
        }

        user?.let {
            viewModel.userName.value = it.userName
            viewModel.url.value = it.url
            viewModel.token.value = it.token
            viewModel.deviceId.value = it.deviceId
            viewModel.deviceName.value = it.deviceName
            viewModel.webhookId.value = it.webhookId
        }

        setContent {
            EditUserUI(viewModel)
        }

        // 返回键检查
        onBackPressedDispatcher.addCallback(this) {
            if (!viewModel.hasEdited()) {
                finish()
            } else if (viewModel.canSave()) {
                // 保存数据
                viewModel.saveDialogVisible.value = true
            } else {
                // 弹出Dialog，问用户是否退出
                viewModel.exitDialogVisible.value = true
            }
        }
    }

    @Composable
    private fun DeleteDialog(viewModel: EditUserViewModel) {
        IconDialog(
            "永久删除",
            icon = Icons.Default.Delete,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
            iconBackground = MaterialTheme.colorScheme.errorContainer,
            buttons = {
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier.fillMaxWidth()
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
                            deleteUserItem(viewModel)
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
    private fun ExitDialog(viewModel: EditUserViewModel) {
        IconDialog(
            "放弃修改",
            icon = R.drawable.ic_edit_off,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
            iconBackground = MaterialTheme.colorScheme.errorContainer,
            buttons = {
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier.fillMaxWidth()
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
    private fun SaveDialog(viewModel: EditUserViewModel) {
        IconDialog(
            "保存修改",
            icon = R.drawable.ic_save,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            iconBackground = MaterialTheme.colorScheme.primaryContainer,
            buttons = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
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
                                saveUserItem(viewModel)
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
    private fun EditUserUI(viewModel: EditUserViewModel) {
        val userName = viewModel.userName

        val url = viewModel.url

        val token = viewModel.token

        val deviceId = viewModel.deviceId

        val deviceName = viewModel.deviceName

        val webhookId = viewModel.webhookId

        // 布局
        AppTheme {
            CommonPage {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp, 0.dp)
                ) {
                    Text(
                        text = "修改用户",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(24.dp)
                    )

                    TitleCard(
                        title = "基本信息"
                    ) {
                        OutlinedTextField(
                            value = userName.value,
                            onValueChange = {
                                userName.value = it
                            },
                            label = { Text("用户名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = url.value,
                            onValueChange = {
                                url.value = it
                            },
                            label = { Text("服务器地址") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 注册信息
                    if (viewModel.rawUser.value.userType == User.REGISTER_USER_TYPE) {
                        TitleCard(
                            title = "注册信息"
                        ) {
                            OutlinedTextField(
                                value = token.value,
                                onValueChange = {
                                    token.value = it
                                },
                                label = { Text("访问令牌") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = deviceId.value,
                                onValueChange = {
                                    deviceId.value = it
                                },
                                label = { Text("设备 ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = deviceName.value,
                                onValueChange = {
                                    deviceName.value = it
                                },
                                label = { Text("设备名称") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 重新注册
                            Button(
                                onClick = {
                                    viewModel.isReRegistering.value = true

                                    // IO操作
                                    CoroutineScope(IO).launch {
                                        val hassClient = HassClient(
                                            baseUrl = url.value.trim(),
                                            token = token.value.trim(),
                                            deviceId = deviceId.value.trim(),
                                            deviceName = deviceName.value.trim()
                                        )

                                        val newWebhookId = hassClient.registerDevice(this@EditUserActivity)

                                        if (newWebhookId != null) {
                                            // 注册成功，更新数据
                                            webhookId.value = newWebhookId

                                            runOnUiThread {
                                                Toast.makeText(
                                                    this@EditUserActivity,
                                                    "注册成功",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } else {
                                            // 注册失败，提示用户
                                            runOnUiThread {
                                                Toast.makeText(
                                                    this@EditUserActivity,
                                                    "注册失败，请检查注册信息是否填写正确",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }

                                        viewModel.isReRegistering.value = false
                                    }
                                },
                                enabled = viewModel.canReRegister()
                            ) {
                                Text("重新注册")
                            }
                        }
                    }

                    // 登录信息
                    TitleCard(
                        title = "登录信息"
                    ) {
                        OutlinedTextField(
                            value = webhookId.value,
                            onValueChange = {
                                webhookId.value = it
                            },
                            label = { Text("Webhook ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 测试
                        Button(
                            onClick = {
                                viewModel.isTesting.value = true

                                // IO操作
                                CoroutineScope(IO).launch {
                                    val hassClient = HassClient(
                                        baseUrl = url.value.trim(),
                                        webhookId = webhookId.value.trim()
                                    )

                                    try {
                                        hassClient.getConfig()
                                        // 测试成功，提示用户
                                        runOnUiThread {
                                            Toast.makeText(
                                                this@EditUserActivity,
                                                "有效",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        // 测试失败，提示用户
                                        runOnUiThread {
                                            Toast.makeText(
                                                this@EditUserActivity,
                                                "失败，请检查相关信息是否填写正确",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }

                                    viewModel.isTesting.value = false
                                }
                            },
                            enabled = viewModel.canTest()
                        ) {
                            Text("验证")
                        }
                    }

                    // 删除
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

            SaveFabButton(viewModel, viewModel.canSave() && viewModel.hasEdited())

            // 弹出Dialog，问用户是否删除
            if (viewModel.deleteDialogVisible.value) {
                DeleteDialog(viewModel)
            }

            // 弹出Dialog，问用户是否退出
            if (viewModel.exitDialogVisible.value) {
                ExitDialog(viewModel)
            }

            // 弹出Dialog，问用户是否保存
            if (viewModel.saveDialogVisible.value) {
                SaveDialog(viewModel)
            }
        }
    }

    @Composable
    private fun SaveFabButton(viewModel: EditUserViewModel, visible: Boolean = false) {
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
                        saveUserItem(viewModel)
                        finish()
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(24.dp)
                        .navigationBarsPadding()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_save),
                        contentDescription = "保存"
                    )
                }
            }
        }
    }

//    @Preview(showBackground = true)
//    @Composable
//    private fun PreviewStartLocationUI() {
//        val viewModel = EditUserViewModel().apply {
//            zoneId.value = "zone-1"
//            ssidItems.addAll(
//                listOf(
//                    UserWifiInfo("SSID-1", "BSSID-1"),
//                    UserWifiInfo("SSID-2", "BSSID-2"),
//                    UserWifiInfo("SSID-3", "BSSID-3"),
//                )
//            )
//            bssidItems.addAll(
//                listOf(
//                    UserWifiInfo("SSID-4", "BSSID-4"),
//                    UserWifiInfo("SSID-5", "BSSID-5"),
//                    UserWifiInfo("SSID-6", "BSSID-6"),
//                )
//            )
//        }
//
//        EditUserUI(viewModel)
//    }

    private fun saveUserItem(viewModel: EditUserViewModel) {
        val rawUserItem = viewModel.rawUser.value

        // 从UI中获取数据
        val userName = viewModel.userName.value
        val url = viewModel.url.value.trim()
        val token = viewModel.token.value.trim()
        val deviceId = viewModel.deviceId.value.trim()
        val deviceName = viewModel.deviceName.value.trim()
        val webhookId = viewModel.webhookId.value.trim()

        // 通过activityResult返回，然后关闭当前页面
        val intent = Intent()
        intent.putExtra("type", 0)
        intent.putExtra(
            "item", Json.encodeToString(
                rawUserItem.copy(
                    userName = userName,
                    url = url,
                    token = token,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    webhookId = webhookId
                )
            )
        )
        setResult(RESULT_OK, intent)
    }

    private fun deleteUserItem(viewModel: EditUserViewModel) {
        val intent = Intent()
        intent.putExtra("type", -1)
        intent.putExtra("item", Json.encodeToString(viewModel.rawUser.value))
        setResult(RESULT_OK, intent)
    }
}

package org.nxy.hasstools.ui.user

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.nxy.hasstools.R
import org.nxy.hasstools.objects.User
import org.nxy.hasstools.ui.components.CommonPage
import org.nxy.hasstools.ui.components.IconDialog
import org.nxy.hasstools.ui.theme.AppTheme
import org.nxy.hasstools.utils.HassClient
import org.nxy.hasstools.utils.Json
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

internal class CreateUserViewModel : ViewModel() {
    val url = mutableStateOf("http://homeassistant.local:8123")

    val type = mutableIntStateOf(-1)

    val token = mutableStateOf("")

    val deviceId = mutableStateOf("")

    val deviceName = mutableStateOf("")

    val webhookId = mutableStateOf("")

    val userName = mutableStateOf("")

    val step = mutableIntStateOf(0)

    val warningMessage = mutableStateOf("")

    val errorMessage = mutableStateOf("")

    val exitDialogVisible = mutableStateOf(false)

    fun hasWarning(): Boolean {
        return warningMessage.value.isNotEmpty()
    }

    fun hasError(): Boolean {
        return errorMessage.value.isNotEmpty()
    }

    fun setWarning(message: String) {
        warningMessage.value = message
    }

    fun setError(message: String) {
        errorMessage.value = message
    }

    fun clearWarning() {
        warningMessage.value = ""
    }

    fun clearError() {
        errorMessage.value = ""
    }

    fun isStep(stepNum: Int): Boolean {
        return !hasWarning() && !hasError() && step.intValue == stepNum
    }

    fun isStepPassed(stepNum: Int): Boolean {
        return step.intValue >= stepNum
    }

    fun nextStep() {
        step.intValue += 1
    }

    fun prevStep() {
        step.intValue -= 1
    }
}

class CreateUserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel by viewModels<CreateUserViewModel>()

        setContent {
            CreateUserUI(viewModel)
        }

        // 返回键检查
        onBackPressedDispatcher.addCallback(this) {
            if (viewModel.isStep(0)) {
                finish()
                return@addCallback
            }

            viewModel.exitDialogVisible.value = true
        }
    }

    @Composable
    private fun ExitDialog(viewModel: CreateUserViewModel) {
        IconDialog(
            title = "放弃创建",
            icon = R.drawable.ic_edit_off,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
            iconBackground = MaterialTheme.colorScheme.errorContainer,
            onDismiss = { viewModel.exitDialogVisible.value = false },
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
        )
    }

    @Composable
    private fun SaveDialog(viewModel: CreateUserViewModel) {
        IconDialog(
            title = "完成创建",
            icon = R.drawable.ic_save,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            iconBackground = MaterialTheme.colorScheme.primaryContainer,
            onDismiss = { viewModel.prevStep() },
            content = {
                OutlinedTextField(
                    value = viewModel.userName.value,
                    onValueChange = {
                        viewModel.userName.value = it
                    },
                    label = { Text("用户名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            buttons = {
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = { viewModel.prevStep() }
                    ) {
                        Text("取消")
                    }
                    TextButton(
                        enabled = viewModel.userName.value.trim().isNotEmpty(),
                        onClick = {
                            saveUserItem(viewModel)
                            finish()
                        }
                    ) {
                        Text("创建")
                    }
                }
            }
        )
    }

    @Composable
    private fun CreateUserUI(viewModel: CreateUserViewModel) {
        val step = viewModel.step

        val url = viewModel.url

        val type = viewModel.type

        val token = viewModel.token

        val deviceId = viewModel.deviceId

        val deviceName = viewModel.deviceName

        val webhookId = viewModel.webhookId

        val scrollState = rememberScrollState()

        val coroutineScope = rememberCoroutineScope()

        // 监听step变化
        val focusStep = remember { mutableIntStateOf(-1) }
        LaunchedEffect(step.intValue) {
            val isBack = focusStep.intValue > step.intValue

            focusStep.intValue = step.intValue

            if (isBack) return@LaunchedEffect

            coroutineScope.launch {
//                println("scroll to offset: current: ${scrollState.value} | max: ${scrollState.maxValue}")

                scrollToStep(type.intValue, step.intValue, scrollState)
            }
        }

        // 布局
        AppTheme {
            CommonPage(scrollState) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp, 0.dp)
                        .animateContentSize(
                            animationSpec = tween(
                                easing = LinearEasing
                            )
                        )
                ) {
                    Text(
                        text = "创建用户向导",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(24.dp)
                    )

                    StepAnchor(-1, 0)

                    StepCard(viewModel, targetStep = 0) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "请输入 Home Assistant 服务器地址。",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            OutlinedTextField(
                                value = url.value,
                                onValueChange = { url.value = it },
                                label = { Text("服务器地址") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = viewModel.isStep(0),
                            )
                        }

                        StepButton(
                            viewModel = viewModel,
                            targetStep = 0,
                            onNext = { resolve, reject ->
                                // 判断 URL 是否有效（是合法的url，且协议是http或https）
                                // 创造一个url对象
                                try {
                                    val urlObj = URL(url.value)

                                    if (urlObj.protocol != "http" && urlObj.protocol != "https") {
                                        throw Exception("Invalid URL protocol")
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()

                                    viewModel.setError("填写的地址不合法，请检查。")

                                    reject()
                                    return@StepButton
                                }

                                // 验证这个url是否能get访问，如果能访问就跳转到下一步；否则warning
                                // 在io线程中执行
                                CoroutineScope(IO).launch {
                                    val connection = URL(url.value).openConnection() as HttpURLConnection
                                    try {
                                        connection.connectTimeout = 2000
                                        connection.readTimeout = 2000
                                        connection.connect()

                                        resolve()
                                        return@launch
                                    } catch (_: Exception) {
                                        viewModel.setWarning(
                                            "无法连接到这个服务器，请检查地址是否填写正确，以及 Home Assistant 服务是否正常运行。"
                                        )

                                        reject()
                                        return@launch
                                    } finally {
                                        connection.disconnect()
                                    }
                                }
                            },
                            enabled = url.value.isNotEmpty()
                        )
                    }

                    WarningStepCard(
                        viewModel = viewModel,
                        targetStep = 0,
                        onBack = {
                            viewModel.clearWarning()
                        },
                        onDismiss = {
                            viewModel.nextStep()
                            viewModel.clearWarning()
                        }
                    )

                    ErrorStepCard(
                        viewModel = viewModel,
                        targetStep = 0,
                        onBack = {
                            viewModel.clearError()
                        }
                    )

                    StepAnchor(-1, 1)

                    // 选择创建方法
                    StepCard(viewModel, targetStep = 1) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "请选择一种方法创建用户。",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Text(
                                text = "“绑定设备”方法需要一定的技术基础，如果你不知道应该如何选择，请选择“注册设备”方法。",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Column {
                                AnimatedVisibility(
                                    visible = viewModel.isStep(1) || (viewModel.isStepPassed(1) && type.intValue == 0),
                                    // 使用淡入淡出动画，然后高度折叠起来
                                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                                ) {
                                    SelectElevatedButton(
                                        icon = {
                                            Icon(
                                                Icons.Rounded.Add,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                        shape = MaterialTheme.shapes.extraLarge
                                                    )
                                                    .padding(12.dp)
                                            )
                                        },
                                        title = "注册设备（推荐）",
                                        content = "向 Home Assistant 注册设备。这会创建新的设备跟踪器实体。",
                                        enabled = viewModel.isStep(1)
                                    ) {
                                        type.intValue = 0
                                        viewModel.nextStep()
                                    }
                                }

                                AnimatedVisibility(
                                    visible = viewModel.isStep(1),
                                    // 使用淡入淡出动画，然后高度折叠起来
                                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                                ) {
                                    Spacer(modifier = Modifier.size(16.dp))
                                }

                                AnimatedVisibility(
                                    visible = viewModel.isStep(1) || (viewModel.isStepPassed(1) && type.intValue == 1),
                                    // 使用淡入淡出动画，然后高度折叠起来
                                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                                ) {
                                    SelectElevatedButton(
                                        icon = {
                                            Icon(
                                                Icons.Rounded.Edit,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                        shape = MaterialTheme.shapes.extraLarge
                                                    )
                                                    .padding(12.dp)
                                            )
                                        },
                                        title = "绑定设备",
                                        content = "手动填写 Webhook ID 绑定已有的设备跟踪器实体。",
                                        enabled = viewModel.isStep(1)
                                    ) {
                                        type.intValue = 1
                                        viewModel.nextStep()
                                    }
                                }
                            }
                        }

                        StepButton(
                            viewModel = viewModel,
                            targetStep = 1,
                            onBack = { resolve, _ -> resolve() }
                        )
                    }

                    StepAnchor(-1, 2)

                    if (type.intValue == 0 || !viewModel.isStepPassed(2)) {
                        StepCard(viewModel, targetStep = 2) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "访问令牌可以在 侧边栏 > 头像 > 安全 > 长期访问令牌 中创建。",
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    OutlinedTextField(
                                        value = token.value,
                                        onValueChange = {
                                            token.value = it
                                        },
                                        label = { Text("访问令牌") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = viewModel.isStep(2),
                                    )
                                }

                                Column {
                                    Text(
                                        text = "设备 ID 将作为设备跟踪器实体的唯一标识符。请勿使用除了字母、数字和下划线以外的任何字符。",
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    OutlinedTextField(
                                        value = deviceId.value,
                                        onValueChange = {
                                            deviceId.value = it
                                        },
                                        label = { Text("设备 ID") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = viewModel.isStep(2),
                                    )
                                }

                                OutlinedTextField(
                                    value = deviceName.value,
                                    onValueChange = {
                                        deviceName.value = it
                                    },
                                    label = { Text("设备名称") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = viewModel.isStep(2),
                                )
                            }

                            StepButton(
                                viewModel = viewModel,
                                targetStep = 2,
                                onBack = { resolve, _ ->
                                    resolve()
                                },
                                onNext = { resolve, reject ->
                                    // IO线程中执行
                                    CoroutineScope(IO).launch {
                                        try {
                                            val hassClient = HassClient(
                                                baseUrl = url.value.trim(),
                                                token = token.value.trim(),
                                                deviceId = deviceId.value.trim(),
                                                deviceName = deviceName.value.trim()
                                            )

                                            val newWebhookId = hassClient.registerDevice(this@CreateUserActivity)
                                            if (newWebhookId == null) {
                                                viewModel.setError("设备注册失败，请检查后重试。")
                                                reject()
                                                return@launch
                                            }

                                            webhookId.value = newWebhookId
                                            resolve()
                                        } catch (e: Exception) {
                                            e.printStackTrace()

                                            viewModel.setError("设备注册失败，请稍后再试。(${e.message})")

                                            reject()
                                            return@launch
                                        }
                                    }
                                },
                                enabled =
                                    token.value.trim().isNotEmpty()
                                            && deviceId.value.trim().isNotEmpty()
                                            && deviceName.value.trim().isNotEmpty()
                            )
                        }

                        ErrorStepCard(
                            viewModel = viewModel,
                            targetStep = 2,
                            onBack = {
                                viewModel.clearError()
                            }
                        )

                        StepAnchor(0, 3)

                        StepCard(viewModel, targetStep = 3) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "请确认注册信息。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )

                                OutlinedTextField(
                                    value = webhookId.value,
                                    onValueChange = {
                                        webhookId.value = it
                                    },
                                    label = { Text("Webhook ID") },
                                    singleLine = true,
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = viewModel.isStep(3),
                                )
                            }

                            StepButton(
                                viewModel = viewModel,
                                targetStep = 3,
                                onBack = { resolve, _ ->
                                    resolve()
                                },
                                onNext = { resolve, _ ->
                                    resolve()
                                },
                                enabled = webhookId.value.trim().isNotEmpty()
                            )
                        }
                    }

                    if (type.intValue == 1 || !viewModel.isStepPassed(2)) {
                        StepCard(viewModel, targetStep = 2) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "你可以通过 WebView 调试或抓取 Home Assistant 应用的通信数据来获取 Webhook ID。",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                OutlinedTextField(
                                    value = webhookId.value,
                                    onValueChange = {
                                        webhookId.value = it
                                    },
                                    label = { Text("Webhook ID") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    enabled = viewModel.isStep(2),
                                )
                            }

                            StepButton(
                                viewModel = viewModel,
                                targetStep = 2,
                                onBack = { resolve, _ ->
                                    resolve()
                                },
                                onNext = { resolve, _ ->
                                    resolve()
                                },
                                enabled = webhookId.value.trim().isNotEmpty()
                            )
                        }
                    }
                }
            }

            // 弹出Dialog，问用户是否退出
            if (viewModel.exitDialogVisible.value) {
                ExitDialog(viewModel)
            }

            if (
                (viewModel.type.intValue == 0 && viewModel.isStep(4)) ||
                (viewModel.type.intValue == 1 && viewModel.isStep(3))
            ) {
                SaveDialog(viewModel)
            }
        }
    }

    val stepOffsets = mutableMapOf<String, Int>()

    suspend fun scrollToStep(type: Int, step: Int, scrollState: ScrollState) = coroutineScope {
        // 滚动到指定的步骤
        var skipCount = 0
        var raw = 0
        while (coroutineContext.isActive) {
            val newRaw = (stepOffsets["$type|$step"] ?: stepOffsets["-1|$step"]) ?: return@coroutineScope
            if (raw == newRaw) {
                skipCount++
                if (skipCount >= 100) break
                delay(1)
                continue
            } else {
//                println("step $step offset changed: $raw -> $newRaw")
                raw = newRaw
            }

            skipCount = 0

            val target = (
                    stepOffsets["$type|$step"]
                        ?: stepOffsets["-1|$step"]
                        ?: return@coroutineScope
                    ).coerceIn(0, scrollState.maxValue)
            val remain = target - scrollState.value
            val step = remain
            val duration = abs(remain) / 2

            // 可选：调试输出
//            println(
//                "curr=${scrollState.value} target=$target remain=$step duration=$duration max=${scrollState.maxValue}"
//            )

            // **每次最多移动10px，匀速tween，完毕后循环重算**
            if (step < 0) {
                scrollState.scrollBy(step.toFloat())
            } else {
                launch(Dispatchers.Default) {
                    scrollState.animateScrollBy(
                        step.toFloat(),
                        animationSpec = tween(
                            durationMillis = duration,
                            delayMillis = 0,
                            easing = LinearEasing
                        )
                    )
                }

                // 比duration少50ms，如果不足50ms就等到结束
                delay(if (duration > 100) (duration - 100).toLong() else duration.toLong())
            }
        }
    }

    @Composable
    private fun StepAnchor(type: Int = -1, step: Int = 0) {
        DisposableEffect(Unit) {
            onDispose {
                // 在 Box 被移除时执行的操作
//                println("remove step $step, type $type")
                stepOffsets.remove("$type|$step")
            }
        }
        Box(
            modifier = Modifier.onGloballyPositioned {
                // 计算当前步骤的高度
//                if (stepOffsets["$type|$step"] != it.positionInParent().y.toInt()) {
//                    println("update: step $step, type $type, offset: ${it.positionInParent().y}")
//                }
                stepOffsets["$type|$step"] = it.positionInParent().y.toInt()
            }
        )
    }

    @Composable
    private fun StepCard(viewModel: CreateUserViewModel, targetStep: Int, content: @Composable () -> Unit = {}) {
        Column {
            AnimatedVisibility(
                visible = viewModel.isStep(targetStep),
                // 使用淡入淡出动画，然后高度折叠起来
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                // 标题
                Text(
                    text = "步骤 ${targetStep + 1}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp, start = 24.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            AnimatedVisibility(
                visible = viewModel.isStepPassed(targetStep),
                // 使用淡入淡出动画，然后高度折叠起来
                enter = fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        AnimatedVisibility(
                            visible = viewModel.isStepPassed(targetStep) && !viewModel.isStep(targetStep),
                            // 使用淡入淡出动画，然后高度折叠起来
                            enter = slideInVertically() + expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { -it }) + shrinkVertically(
                                shrinkTowards = Alignment.Top
                            ) + fadeOut()
                        ) {
                            Text(
                                text = "步骤 ${targetStep + 1}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(bottom = 16.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        content()
                    }
                }
            }
        }
    }

    @Composable
    private fun WarningStepCard(
        viewModel: CreateUserViewModel,
        targetStep: Int,
        onBack: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        AnimatedVisibility(
            visible = viewModel.step.intValue == targetStep && viewModel.hasWarning(),
            // 使用淡入淡出动画，然后高度折叠起来
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "警告",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        modifier = Modifier.animateContentSize(),
                        text = viewModel.warningMessage.value,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onBack,
                        ) {
                            Text("上一步")
                        }
                        OutlinedButton(
                            onClick = onDismiss,
                        ) {
                            Text("忽略")
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ErrorStepCard(
        viewModel: CreateUserViewModel,
        targetStep: Int,
        onBack: () -> Unit
    ) {
        AnimatedVisibility(
            visible = viewModel.step.intValue == targetStep && viewModel.hasError(),
            // 使用淡入淡出动画，然后高度折叠起来
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "错误",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        modifier = Modifier.animateContentSize(),
                        text = viewModel.errorMessage.value,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Button(
                        onClick = onBack
                    ) {
                        Text("上一步")
                    }
                }
            }
        }
    }

    // 上一步和下一步组件，有回调的按钮才显示。
    @Composable
    private fun StepButton(
        viewModel: CreateUserViewModel,
        targetStep: Int,
        onBack: ((resolve: () -> Unit, reject: () -> Unit) -> Unit)? = null,
        onNext: ((resolve: () -> Unit, reject: () -> Unit) -> Unit)? = null,
        enabled: Boolean = true
    ) {
        var backBtnBusy by remember { mutableStateOf(false) }
        var nextBtnBusy by remember { mutableStateOf(false) }

        AnimatedVisibility(
            visible = viewModel.isStep(targetStep),
            // 使用淡入淡出动画，然后高度折叠起来
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                if (onBack != null) {
                    OutlinedButton(
                        onClick = {
                            backBtnBusy = true
                            onBack(
                                {
                                    backBtnBusy = false
                                    viewModel.prevStep()
                                },
                                {
                                    backBtnBusy = false
                                }
                            )
                        },
                        enabled = viewModel.isStep(targetStep) && !backBtnBusy && !nextBtnBusy
                    ) { Text("上一步") }
                }
                if (onNext != null) {
                    Button(
                        onClick = {
                            nextBtnBusy = true
                            onNext(
                                {
                                    nextBtnBusy = false
                                    viewModel.nextStep()
                                },
                                {
                                    nextBtnBusy = false
                                }
                            )
                        },
                        enabled = enabled && viewModel.isStep(targetStep) && !nextBtnBusy && !backBtnBusy
                    ) {
                        Text("下一步")
                    }
                }
            }
        }
    }

    @Composable
    private fun SelectElevatedButton(
        icon: @Composable () -> Unit,
        title: String,
        content: String,
        enabled: Boolean,
        onClick: () -> Unit
    ) {
        ElevatedButton(
            contentPadding = PaddingValues(16.dp),
            onClick = onClick,
            shape = MaterialTheme.shapes.large, // 设置圆角大小
            enabled = enabled,
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()

                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }

    private fun saveUserItem(viewModel: CreateUserViewModel) {
        // 从UI中获取数据
        val userType = viewModel.type.intValue
        val userName = viewModel.userName.value.trim()
        val url = viewModel.url.value.trim()
        val token = viewModel.token.value.trim()
        val deviceId = viewModel.deviceId.value.trim()
        val deviceName = viewModel.deviceName.value.trim()
        val webhookId = viewModel.webhookId.value.trim()

        // 通过activityResult返回，然后关闭当前页面
        val intent = Intent()
        intent.putExtra(
            "item", Json.encodeToString(
                User(
                    userType = if (userType == 0) User.REGISTER_USER_TYPE else User.BIND_USER_TYPE,
                    userName = userName,
                    url = url,
                    token = token,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    webhookId = webhookId,
                )
            )
        )
        setResult(RESULT_OK, intent)
    }

//    @Preview(showBackground = true)
//    @Composable
//    private fun PreviewStartLocationUI() {
//        val viewModel = CreateUserViewModel().apply {
//            zoneId.value = "zone-1"
//            ssidItems.addAll(
//                listOf(
//                    WifiGeofenceWifiInfo("SSID-1", "BSSID-1"),
//                    WifiGeofenceWifiInfo("SSID-2", "BSSID-2"),
//                    WifiGeofenceWifiInfo("SSID-3", "BSSID-3"),
//                )
//            )
//            bssidItems.addAll(
//                listOf(
//                    WifiGeofenceWifiInfo("SSID-4", "BSSID-4"),
//                    WifiGeofenceWifiInfo("SSID-5", "BSSID-5"),
//                    WifiGeofenceWifiInfo("SSID-6", "BSSID-6"),
//                )
//            )
//        }
//
//        CreateUserUI(viewModel)
//    }
}

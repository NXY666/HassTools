package org.nxy.hasstools.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.SpaceBetween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.nxy.hasstools.R

@Composable
fun TitleCard(
    title: String,
    introduction: String? = null,
    isLoading: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var infoDialogShown by remember { mutableStateOf(false) }

    // info dialog
    if (infoDialogShown) {
        AlertDialog(
            onDismissRequest = { infoDialogShown = false },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = introduction ?: "",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { infoDialogShown = false }
                ) {
                    Text(
                        text = "好的",
                    )
                }
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .padding(bottom = 24.dp)
            .fillMaxWidth(),
    ) {
        // 标题
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                horizontalArrangement = SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                )

                // info图标，点一下打开一个dialog
                if (introduction != null) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = "info",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .clickable { infoDialogShown = true }
                            .width(18.dp)
                    )
                }
            }

            // 内容
            Box {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                ) {
                    content()
                }

                // 上面的控件（会覆盖住下面的部分控件）
                this@Column.AnimatedVisibility(
                    modifier = Modifier.matchParentSize(),
                    visible = isLoading,
                    enter = fadeIn(animationSpec = tween(durationMillis = 500)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 500))
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                // 点击事件
                                detectTapGestures(onPress = { })
                            }
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f))
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(42.dp)
                                .align(Alignment.Center), // 设置大小
                            color = MaterialTheme.colorScheme.primary, // 设置颜色
                            trackColor = Color.Transparent,
                        )
                    }
                }
            }
        }
    }
}

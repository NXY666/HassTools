package org.nxy.hasstools.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun IconDialog(
    title: String,
    icon: Int,
    iconTint: Color = MaterialTheme.colorScheme.onTertiaryContainer,
    iconBackground: Color = MaterialTheme.colorScheme.tertiaryContainer,
    content: (@Composable () -> Unit)? = null,
    buttons: @Composable () -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        icon = {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = iconBackground
                ),
                modifier = Modifier.size(64.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        painter = painterResource(id = icon),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.fillMaxSize(0.6f)
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        },
        text = if (content != null) {
            {
                content()
            }
        } else null,
        confirmButton = buttons,
        onDismissRequest = onDismiss
    )
}

@Composable
fun IconDialog(
    title: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.onTertiaryContainer,
    iconBackground: Color = MaterialTheme.colorScheme.tertiaryContainer,
    content: (@Composable () -> Unit)? = null,
    buttons: @Composable () -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        icon = {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = iconBackground
                ),
                modifier = Modifier.size(64.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.fillMaxSize(0.6f)
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        },
        text = if (content != null) {
            {
                content()
            }
        } else null,
        onDismissRequest = onDismiss,
        confirmButton = buttons
    )
}

package org.nxy.hasstools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PrimarySwitchCard(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCardClick: (() -> Unit)? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    switchEnabled: Boolean = enabled,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val resolvedOnCardClick = onCardClick ?: onCheckedChange?.let { handler ->
        {
            handler(!checked)
        }
    }

    val resolvedOnCheckedChange: (Boolean) -> Unit = onCheckedChange ?: when {
        resolvedOnCardClick != null -> {
            { resolvedOnCardClick() }
        }

        else -> {
            { _ -> }
        }
    }

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            var rowModifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.inversePrimary)

            if (resolvedOnCardClick != null) {
                rowModifier = rowModifier.clickable(enabled = enabled, onClick = resolvedOnCardClick)
            }

            Row(
                modifier = rowModifier.padding(PaddingValues(horizontal = 20.dp, vertical = 16.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    if (!description.isNullOrEmpty()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Switch(
                    checked = checked,
                    enabled = switchEnabled,
                    onCheckedChange = resolvedOnCheckedChange,
                )
            }
        }
    }
}

package org.nxy.hasstools.ui.permission

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nxy.hasstools.ui.components.CommonPage
import org.nxy.hasstools.ui.theme.AppTheme

class HealthPermissionUsageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HealthPermissionUsageUI()
        }
    }

    @Composable
    private fun HealthPermissionUsageUI() {
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
                        text = "😶‍🌫️",
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.padding(18.dp)
                    )
                    Text(
                        text = "“位置上报”仅申请 Health Connect 写入权限，你的隐私与我无关。",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

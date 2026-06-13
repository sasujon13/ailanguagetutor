package com.cheradip.ailanguagetutor.feature.help

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.locale.appString
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen

@Composable
fun UserManualDetailScreen(
    manualType: ManualType,
    modifier: Modifier = Modifier,
    viewModel: UserManualViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CheradipScrollScreen(
        modifier = modifier,
        title = appString(manualType.titleKey),
        subtitle = appString(manualType.subtitleKey),
    ) {
        when {
            uiState.loading -> {
                item {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            }
            uiState.error != null -> {
                item {
                    Text(
                        uiState.error ?: appString("manual_load_error"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            else -> {
                uiState.blocks.forEach { block ->
                    item {
                        ManualBlockView(block)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualBlockView(block: ManualBlock) {
    when (block) {
        is ManualBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }
            Text(
                text = block.text,
                style = style,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
        is ManualBlock.Paragraph -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        is ManualBlock.Bullet -> {
            Text(
                text = "• ${block.text}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
            )
        }
        is ManualBlock.Code -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = block.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        ManualBlock.Divider -> {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        }
        ManualBlock.Spacer -> {
            Text("", modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

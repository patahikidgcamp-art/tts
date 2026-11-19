package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.VoiceCatalog
import com.example.model.AudioFormat
import com.example.model.ExportUiState
import com.example.model.LanguageOption
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun AcousticVisualizer(
    isSpeaking: Boolean,
    batterySaverMode: Boolean,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary
    val idleColor = MaterialTheme.colorScheme.outline

    val phase = if (isSpeaking && !batterySaverMode) {
        val transition = rememberInfiniteTransition(label = "acoustic_wave")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_phase"
        )
        value
    } else {
        0.6f
    }

    Canvas(modifier = modifier) {
        val barCount = 18
        val totalWidth = size.width
        val height = size.height
        val slotWidth = totalWidth / barCount
        val barWidth = (slotWidth * 0.56f).coerceAtLeast(3.dp.toPx())

        for (i in 0 until barCount) {
            val normalizedPos = i.toFloat() / (barCount - 1)
            val envelope = sin(normalizedPos * PI).toFloat().coerceIn(0.25f, 1f)
            val amp = when {
                isSpeaking && !batterySaverMode -> {
                    val wave = abs(sin(phase + i * 0.55f))
                    (0.25f + 0.75f * wave) * envelope
                }
                isSpeaking && batterySaverMode -> {
                    // Static high-contrast meter in Eco mode (zero GPU animation overhead)
                    (0.45f + 0.35f * abs(sin(i * 0.8f))) * envelope
                }
                else -> 0.18f + 0.14f * envelope
            }
            val barH = (height * amp).coerceAtLeast(4.dp.toPx())
            val x = i * slotWidth + (slotWidth - barWidth) / 2f
            val y = (height - barH) / 2f
            val color = when {
                !isSpeaking -> idleColor.copy(alpha = 0.45f)
                i % 5 == 2 -> accentColor
                else -> activeColor
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    selectedTag: String,
    onSelectLanguage: (LanguageOption) -> Unit,
    onInstallOfflineVoices: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) VoiceCatalog.languages
        else VoiceCatalog.languages.filter {
            it.displayName.contains(q, ignoreCase = true) ||
                it.nativeName.contains(q, ignoreCase = true) ||
                it.tag.contains(q, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Select Language",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Instant switching with offline engine support",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close language picker")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search Hindi, English, Tamil, Spanish…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("language_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.tag }) { lang ->
                    val isSelected = lang.tag.equals(selectedTag, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectLanguage(lang)
                                onDismiss()
                            }
                            .testTag("language_row_${lang.tag}")
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        text = lang.shortCode,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = lang.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = lang.nativeName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onInstallOfflineVoices,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("install_offline_voices_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdateAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download Offline Language Packs")
            }
        }
    }
}

@Composable
fun ExportAudioDialog(
    initialTitle: String,
    initialFormat: AudioFormat,
    exportState: ExportUiState,
    onExport: (title: String, format: AudioFormat) -> Unit,
    onViewExports: () -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var selectedFormat by remember(initialFormat) { mutableStateOf(initialFormat) }

    AlertDialog(
        onDismissRequest = {
            if (exportState !is ExportUiState.Exporting) onDismiss()
        },
        title = {
            Text(
                text = when (exportState) {
                    is ExportUiState.Success -> "Audio Ready Offline"
                    is ExportUiState.Exporting -> "Exporting ${exportState.format.displayName}"
                    else -> "Download Offline Audio"
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (exportState) {
                    is ExportUiState.Exporting -> {
                        Text(
                            text = exportState.stage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is ExportUiState.Success -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = exportState.item.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${exportState.item.format.displayName} • ${formatBytes(exportState.item.fileSizeBytes)} • ${formatDuration(exportState.item.durationMs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = "Saved to offline library & Downloads/VaniVox",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                    is ExportUiState.Error -> {
                        Text(
                            text = exportState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    ExportUiState.Idle -> {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("File title") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_title_input")
                        )

                        Text(
                            text = "Audio Format",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AudioFormat.entries.forEach { fmt ->
                                FilterChip(
                                    selected = selectedFormat == fmt,
                                    onClick = { selectedFormat = fmt },
                                    label = { Text(fmt.displayName) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("format_chip_${fmt.name.lowercase()}")
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${selectedFormat.displayName} (${selectedFormat.specLabel})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (exportState) {
                is ExportUiState.Exporting -> {}
                is ExportUiState.Success -> {
                    Button(
                        onClick = {
                            onDismiss()
                            onViewExports()
                        },
                        modifier = Modifier.testTag("view_exports_after_save")
                    ) {
                        Text("Open Exports")
                    }
                }
                else -> {
                    Button(
                        onClick = { onExport(title, selectedFormat) },
                        modifier = Modifier.testTag("confirm_export_button")
                    ) {
                        Text("Export ${selectedFormat.displayName}")
                    }
                }
            }
        },
        dismissButton = {
            if (exportState !is ExportUiState.Exporting) {
                TextButton(onClick = onDismiss) {
                    Text(if (exportState is ExportUiState.Success) "Done" else "Cancel")
                }
            }
        }
    )
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%d:%02d".format(mins, secs)
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

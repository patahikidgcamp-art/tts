package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.UserSettings
import com.example.data.VoiceCatalog
import com.example.model.LanguageOption
import com.example.model.TtsPlaybackStatus
import com.example.model.VocalProfile
import com.example.tts.TtsRuntimeState
import com.example.ui.components.AcousticVisualizer
import com.example.ui.components.LanguagePickerSheet

@Composable
fun StudioScreen(
    inputText: String,
    settings: UserSettings,
    activeLanguage: LanguageOption,
    activeProfile: VocalProfile,
    ttsState: TtsRuntimeState,
    onInputTextChange: (String) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onLoadSample: () -> Unit,
    onToggleOfflineMode: () -> Unit,
    onToggleBatterySaver: () -> Unit,
    onOpenVoicesTab: () -> Unit,
    onCycleSpeed: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onOpenExportDialog: () -> Unit,
    onInstallOfflineVoices: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var showLanguageSheet by remember { mutableStateOf(false) }

    val quickLanguages = remember { VoiceCatalog.languages.filter { it.isQuickPin } }
    val isSpeaking = ttsState.status == TtsPlaybackStatus.SPEAKING ||
        ttsState.status == TtsPlaybackStatus.PREPARING
    val isPaused = ttsState.status == TtsPlaybackStatus.PAUSED

    val wordCount = remember(inputText) {
        inputText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }
    val estSeconds = remember(wordCount, settings.speedOverride) {
        ((wordCount / 2.4f) / settings.speedOverride).toInt().coerceAtLeast(if (wordCount > 0) 1 else 0)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Quick Language Switcher Strip
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Language Switch",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { showLanguageSheet = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("all_languages_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = stringResource(R.string.cd_all_languages),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("All (${VoiceCatalog.languages.size})")
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickLanguages, key = { it.tag }) { lang ->
                        val selected = lang.tag.equals(activeLanguage.tag, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = if (selected) null else BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clickable { onSelectLanguage(lang.tag) }
                                .testTag("quick_lang_${lang.tag}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = lang.nativeName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }

                    // Show current non-pinned language if chosen from sheet
                    if (quickLanguages.none { it.tag.equals(activeLanguage.tag, ignoreCase = true) }) {
                        item(key = activeLanguage.tag) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = activeLanguage.nativeName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Mode & Vocal Profile Quick Ribbon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenVoicesTab() }
                        .testTag("active_profile_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${activeProfile.displayName} • ${activeProfile.gender.label}",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeProfile.styleType.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Offline toggle chip
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (settings.offlineOnlyMode) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        1.dp,
                        if (settings.offlineOnlyMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .clickable { onToggleOfflineMode() }
                        .testTag("offline_mode_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudOff,
                            contentDescription = stringResource(R.string.cd_offline_mode),
                            tint = if (settings.offlineOnlyMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (settings.offlineOnlyMode) "Offline" else "Auto",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (settings.offlineOnlyMode) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Eco Battery toggle chip
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (settings.batterySaverMode) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    modifier = Modifier
                        .clickable { onToggleBatterySaver() }
                        .testTag("battery_saver_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatterySaver,
                            contentDescription = stringResource(R.string.cd_battery_saver),
                            tint = if (settings.batterySaverMode) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Eco",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (settings.batterySaverMode) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            // Offline voice data warning if missing on device
            AnimatedVisibility(visible = !ttsState.isLanguageOfflineAvailable && settings.offlineOnlyMode) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Offline pack for ${activeLanguage.displayName} may need installation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        TextButton(onClick = onInstallOfflineVoices) {
                            Text("Install")
                        }
                    }
                }
            }

            // 3. Text Composer Surface
            OutlinedCard(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = if (isSpeaking) 1.5.dp else 1.dp,
                    brush = if (isSpeaking) {
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${activeLanguage.displayName} • $wordCount words (~${estSeconds}s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onLoadSample,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("load_sample_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Load sample text",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboard.getText()?.text?.let { pasted ->
                                        if (pasted.isNotBlank()) onInputTextChange(pasted)
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("paste_text_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = stringResource(R.string.cd_paste_text),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            if (inputText.isNotEmpty()) {
                                IconButton(
                                    onClick = { onInputTextChange("") },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("clear_text_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = stringResource(R.string.cd_clear_text),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputTextChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.studio_hint),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 170.dp, max = 270.dp)
                            .testTag("tts_text_input")
                    )

                    // Live spoken range highlight strip
                    val spokenRange = ttsState.spokenRange
                    AnimatedVisibility(visible = isSpeaking && spokenRange != null) {
                        val safeRange = spokenRange?.let { r ->
                            val s = r.first.coerceIn(0, inputText.length)
                            val e = (r.last + 1).coerceIn(s, inputText.length)
                            s..e
                        }
                        if (safeRange != null && safeRange.first < safeRange.last) {
                            val snippetStart = (safeRange.first - 24).coerceAtLeast(0)
                            val snippetEnd = (safeRange.last + 24).coerceAtMost(inputText.length)
                            val annotated = buildAnnotatedString {
                                if (snippetStart > 0) append("…")
                                append(inputText.substring(snippetStart, safeRange.first))
                                withStyle(
                                    SpanStyle(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        background = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    append(inputText.substring(safeRange.first, safeRange.last))
                                }
                                append(inputText.substring(safeRange.last, snippetEnd))
                                if (snippetEnd < inputText.length) append("…")
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = annotated,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Studio Playback & Export Control Dock
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (ttsState.status) {
                                    TtsPlaybackStatus.SPEAKING -> "Speaking naturally…"
                                    TtsPlaybackStatus.PREPARING -> "Preparing voice…"
                                    TtsPlaybackStatus.PAUSED -> "Paused • Tap play to resume"
                                    TtsPlaybackStatus.ERROR -> ttsState.errorMessage ?: "Engine error"
                                    TtsPlaybackStatus.IDLE -> ttsState.activeVoiceDescription
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (ttsState.status == TtsPlaybackStatus.ERROR) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .clickable { onCycleSpeed() }
                                .testTag("quick_speed_chip")
                        ) {
                            Text(
                                text = String.format("%.2gx", settings.speedOverride),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    AcousticVisualizer(
                        isSpeaking = isSpeaking,
                        batterySaverMode = settings.batterySaverMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPlayPause,
                            enabled = inputText.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("speak_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play_pause)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isSpeaking -> "Pause"
                                    isPaused -> "Resume"
                                    else -> "Speak"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        AnimatedVisibility(visible = isSpeaking || isPaused) {
                            FilledTonalIconButton(
                                onClick = onStop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .testTag("stop_speech_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = stringResource(R.string.cd_stop)
                                )
                            }
                        }

                        Button(
                            onClick = onOpenExportDialog,
                            enabled = inputText.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("open_export_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = stringResource(R.string.cd_export_audio),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = settings.preferredExportFormat.displayName,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLanguageSheet) {
        LanguagePickerSheet(
            selectedTag = activeLanguage.tag,
            onSelectLanguage = { onSelectLanguage(it.tag) },
            onInstallOfflineVoices = {
                showLanguageSheet = false
                onInstallOfflineVoices()
            },
            onDismiss = { showLanguageSheet = false }
        )
    }
}

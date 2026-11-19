package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.UserSettings
import com.example.data.VoiceCatalog
import com.example.model.LanguageOption
import com.example.model.VocalProfile
import com.example.model.VoiceGender
import com.example.tts.TtsRuntimeState
import kotlin.math.roundToLong

@Composable
fun VoicesScreen(
    settings: UserSettings,
    activeLanguage: LanguageOption,
    activeProfile: VocalProfile,
    ttsState: TtsRuntimeState,
    onSelectProfile: (VocalProfile) -> Unit,
    onPreviewProfile: (VocalProfile) -> Unit,
    onSelectSystemVoice: (String?) -> Unit,
    onPitchChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPauseChange: (Long) -> Unit,
    onResetTuning: () -> Unit,
    onInstallOfflineVoices: () -> Unit,
    modifier: Modifier = Modifier
) {
    var genderFilter by remember { mutableStateOf<VoiceGender?>(null) }

    val profiles = remember(genderFilter) {
        if (genderFilter == null) VoiceCatalog.vocalProfiles
        else VoiceCatalog.vocalProfiles.filter { it.gender == genderFilter }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Vocal Profiles • ${activeLanguage.displayName}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Switch seamlessly between soft, sweet, bold, and narrative male or female styles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = genderFilter == null,
                        onClick = { genderFilter = null },
                        label = { Text("All Voices") },
                        modifier = Modifier.testTag("filter_gender_all")
                    )
                    FilterChip(
                        selected = genderFilter == VoiceGender.FEMALE,
                        onClick = { genderFilter = VoiceGender.FEMALE },
                        label = { Text("Female") },
                        modifier = Modifier.testTag("filter_gender_female")
                    )
                    FilterChip(
                        selected = genderFilter == VoiceGender.MALE,
                        onClick = { genderFilter = VoiceGender.MALE },
                        label = { Text("Male") },
                        modifier = Modifier.testTag("filter_gender_male")
                    )
                }
            }

            items(profiles, key = { it.id }) { profile ->
                val isSelected = profile.id == activeProfile.id
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectProfile(profile) }
                        .testTag("voice_profile_${profile.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = profile.displayName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (profile.gender == VoiceGender.FEMALE) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    }
                                ) {
                                    Text(
                                        text = "${profile.gender.label} · ${profile.styleType.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (profile.gender == VoiceGender.FEMALE) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = profile.tagline,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Active vocal profile",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            IconButton(
                                onClick = { onPreviewProfile(profile) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("preview_profile_${profile.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayCircle,
                                    contentDescription = stringResource(R.string.cd_preview_voice),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Acoustic Fine-Tuning Card
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Acoustic Fine-Tuning",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            TextButton(
                                onClick = onResetTuning,
                                modifier = Modifier.testTag("reset_tuning_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }

                        // Pitch
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Pitch Warmth", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = String.format("%.2fx", settings.pitchOverride),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = settings.pitchOverride,
                                onValueChange = onPitchChange,
                                valueRange = 0.7f..1.4f,
                                modifier = Modifier.testTag("pitch_slider")
                            )
                        }

                        // Speed
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Speaking Pace", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = String.format("%.2fx", settings.speedOverride),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = settings.speedOverride,
                                onValueChange = onSpeedChange,
                                valueRange = 0.6f..1.8f,
                                modifier = Modifier.testTag("speed_slider")
                            )
                        }

                        // Natural Breath Pauses
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Natural Breath Pauses", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${settings.pauseCadenceMs} ms",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = settings.pauseCadenceMs.toFloat(),
                                onValueChange = { onPauseChange(it.roundToLong()) },
                                valueRange = 0f..550f,
                                modifier = Modifier.testTag("pause_slider")
                            )
                        }
                    }
                }
            }

            // Installed System Engine Voices for Active Language
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Engine Voices (${activeLanguage.shortCode})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { onSelectSystemVoice(null) }) {
                        Text(if (settings.selectedSystemVoiceName == null) "Auto-Matched" else "Use Auto")
                    }
                }
            }

            if (ttsState.availableVoicesForLocale.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "System voice list unavailable or offline pack not installed yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onInstallOfflineVoices,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SystemUpdateAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Install Offline Voices")
                            }
                        }
                    }
                }
            } else {
                items(ttsState.availableVoicesForLocale.take(12), key = { it.name }) { voice ->
                    val selected = settings.selectedSystemVoiceName == voice.name
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSystemVoice(voice.name) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = voice.displayName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = voice.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (voice.isOfflineReady) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudOff,
                                    contentDescription = "Offline ready",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

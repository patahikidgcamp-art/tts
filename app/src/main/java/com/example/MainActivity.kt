package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppThemeMode
import com.example.ui.components.ExportAudioDialog
import com.example.ui.screens.ExportsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.VoicesScreen
import com.example.ui.theme.VaniVoxTheme
import com.example.viewmodel.VaniVoxViewModel

private enum class VaniTab(val route: String) {
    STUDIO("studio"),
    VOICES("voices"),
    EXPORTS("exports")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: VaniVoxViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            VaniVoxTheme(themeMode = settings.themeMode) {
                VaniVoxRoot(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaniVoxRoot(viewModel: VaniVoxViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val activeLanguage by viewModel.activeLanguage.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val exports by viewModel.exports.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(VaniTab.STUDIO) }
    var showExportDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setNotificationPermissionGranted(granted)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            viewModel.setNotificationPermissionGranted(granted)
        }
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusBanner()
    }

    if (currentTab != VaniTab.STUDIO) {
        BackHandler { currentTab = VaniTab.STUDIO }
    }

    val openInstallVoices = {
        runCatching {
            context.startActivity(viewModel.buildInstallVoiceDataIntent())
        }
        Unit
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.cycleThemeMode() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = when (settings.themeMode) {
                                        AppThemeMode.DARK -> Icons.Rounded.DarkMode
                                        AppThemeMode.LIGHT -> Icons.Rounded.LightMode
                                        AppThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                                    },
                                    contentDescription = stringResource(R.string.cd_toggle_theme),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = settings.themeMode.label,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = currentTab == VaniTab.STUDIO,
                    onClick = { currentTab = VaniTab.STUDIO },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == VaniTab.STUDIO) {
                                Icons.Filled.GraphicEq
                            } else {
                                Icons.Outlined.GraphicEq
                            },
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.nav_studio)) },
                    modifier = Modifier.testTag("nav_studio")
                )
                NavigationBarItem(
                    selected = currentTab == VaniTab.VOICES,
                    onClick = { currentTab = VaniTab.VOICES },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == VaniTab.VOICES) {
                                Icons.Filled.RecordVoiceOver
                            } else {
                                Icons.Outlined.RecordVoiceOver
                            },
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.nav_voices)) },
                    modifier = Modifier.testTag("nav_voices")
                )
                NavigationBarItem(
                    selected = currentTab == VaniTab.EXPORTS,
                    onClick = { currentTab = VaniTab.EXPORTS },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (exports.isNotEmpty()) {
                                    Badge { Text(exports.size.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (currentTab == VaniTab.EXPORTS) {
                                    Icons.Filled.AudioFile
                                } else {
                                    Icons.Outlined.AudioFile
                                },
                                contentDescription = null
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.nav_exports)) },
                    modifier = Modifier.testTag("nav_exports")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                VaniTab.STUDIO -> StudioScreen(
                    inputText = inputText,
                    settings = settings,
                    activeLanguage = activeLanguage,
                    activeProfile = activeProfile,
                    ttsState = ttsState,
                    onInputTextChange = viewModel::updateInputText,
                    onSelectLanguage = { viewModel.selectLanguage(it) },
                    onLoadSample = viewModel::loadSampleForActiveLanguage,
                    onToggleOfflineMode = viewModel::toggleOfflineOnlyMode,
                    onToggleBatterySaver = viewModel::toggleBatterySaverMode,
                    onOpenVoicesTab = { currentTab = VaniTab.VOICES },
                    onCycleSpeed = viewModel::cycleQuickSpeed,
                    onPlayPause = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        viewModel.toggleSpeakOrPause()
                    },
                    onStop = viewModel::stopSpeaking,
                    onOpenExportDialog = {
                        viewModel.clearExportState()
                        showExportDialog = true
                    },
                    onInstallOfflineVoices = openInstallVoices
                )

                VaniTab.VOICES -> VoicesScreen(
                    settings = settings,
                    activeLanguage = activeLanguage,
                    activeProfile = activeProfile,
                    ttsState = ttsState,
                    onSelectProfile = viewModel::selectVocalProfile,
                    onPreviewProfile = viewModel::previewVocalProfile,
                    onSelectSystemVoice = viewModel::selectSystemVoice,
                    onPitchChange = viewModel::setPitchOverride,
                    onSpeedChange = viewModel::setSpeedOverride,
                    onPauseChange = viewModel::setPauseCadenceMs,
                    onResetTuning = viewModel::resetAcousticTuning,
                    onInstallOfflineVoices = openInstallVoices
                )

                VaniTab.EXPORTS -> ExportsScreen(
                    exports = exports,
                    playerState = playerState,
                    onTogglePlay = viewModel::toggleExportPlayback,
                    onSeek = viewModel::seekExportPlayback,
                    onSaveToDownloads = viewModel::saveItemToDownloads,
                    onShare = { item ->
                        viewModel.buildShareIntent(item)?.let { shareIntent ->
                            context.startActivity(Intent.createChooser(shareIntent, "Share ${item.fileName}"))
                        }
                    },
                    onDelete = viewModel::deleteExportItem
                )
            }
        }
    }

    if (showExportDialog) {
        val suggestedTitle = remember(inputText, activeLanguage) {
            val firstWords = inputText.trim().split(Regex("\\s+")).take(4).joinToString(" ")
            firstWords.take(28).ifBlank { "${activeLanguage.displayName} Speech" }
        }
        ExportAudioDialog(
            initialTitle = suggestedTitle,
            initialFormat = settings.preferredExportFormat,
            exportState = exportState,
            onExport = { title, format -> viewModel.exportSpeech(title, format) },
            onViewExports = { currentTab = VaniTab.EXPORTS },
            onDismiss = {
                viewModel.clearExportState()
                showExportDialog = false
            }
        )
    }
}

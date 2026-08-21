package com.tapconvert.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tapconvert.app.share.ShareIntentParser
import com.tapconvert.app.ui.config.ConfigurationScreen
import com.tapconvert.app.ui.dashboard.DashboardScreen
import com.tapconvert.app.ui.history.HistoryScreen
import com.tapconvert.app.ui.history.HistoryViewModel
import com.tapconvert.app.ui.processing.ProcessingScreen
import com.tapconvert.app.ui.result.ResultScreen
import com.tapconvert.app.ui.theme.AccentAmber
import com.tapconvert.app.ui.theme.TapConvertTheme
import com.tapconvert.core.ads.AdReward
import com.tapconvert.core.database.TapConvertDatabase
import com.tapconvert.core.database.cleaner.LruDiskCleaner
import com.tapconvert.core.database.repository.RoomConversionHistoryRepository
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MediaCategory
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.Preset
import java.io.File

enum class NavigationTab {
    DASHBOARD,
    HISTORY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapConvertApp() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600

    // Initialize Room SQLite Database & Repository
    val repository = remember { RoomConversionHistoryRepository.create(context) }
    val diskCleaner = remember {
        LruDiskCleaner(
            cacheDirectories = listOf(context.cacheDir, File(context.filesDir, "conversions")),
            repository = repository
        )
    }

    val mainViewModel: MainViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(historyRepository = repository) as T
        }
    })

    val historyViewModel: HistoryViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(repository = repository, diskCleaner = diskCleaner) as T
        }
    })

    val uiState by mainViewModel.uiState.collectAsState()
    val adState by mainViewModel.adManager.state.collectAsState()
    val shouldShowInterstitial by mainViewModel.shouldShowInterstitial.collectAsState()

    val historyRecords by historyViewModel.records.collectAsState()
    val totalStorageBytes by historyViewModel.totalStorageUsageBytes.collectAsState()
    val onlyFavoritesFilter by historyViewModel.onlyFavoritesFilter.collectAsState()

    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }
    var showFastPassDialog by remember { mutableStateOf(false) }

    var pendingPreset by remember { mutableStateOf<Preset?>(null) }
    var pendingCategory by remember { mutableStateOf<MediaCategory?>(null) }

    // Helper to stage real picked media into working files
    fun processPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return

        val stagingDir = File(context.cacheDir, "intake_staging").apply { mkdirs() }
        val workingUris = mutableListOf<String>()
        val fileNames = mutableListOf<String>()

        for (uri in uris) {
            val name = ShareIntentParser.resolveFileName(uri, context.contentResolver)
            val dest = File(stagingDir, "${System.currentTimeMillis()}_$name")
            try {
                if (uri.scheme == "content") {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                } else {
                    val src = File(uri.path ?: uri.toString())
                    if (src.exists()) src.copyTo(dest, overwrite = true)
                }
                if (dest.exists()) {
                    workingUris.add("file://${dest.absolutePath}")
                    fileNames.add(name)
                }
            } catch (_: Throwable) {}
        }

        if (workingUris.isEmpty()) return

        val preset = pendingPreset
        val category = pendingCategory
        pendingPreset = null
        pendingCategory = null

        if (preset != null) {
            mainViewModel.selectPreset(preset, workingUris)
        } else if (category != null) {
            when (category) {
                MediaCategory.IMAGE -> mainViewModel.configureCustom(workingUris, ConversionType.IMAGE_COMPRESS, MimeType.Image.WEBP)
                MediaCategory.VIDEO -> mainViewModel.configureCustom(workingUris, ConversionType.VIDEO_COMPRESS, MimeType.Video.MP4)
                MediaCategory.DOCUMENT -> mainViewModel.configureCustom(workingUris, ConversionType.IMAGES_TO_PDF, MimeType.Document.PDF)
                MediaCategory.AUDIO -> mainViewModel.configureCustom(workingUris, ConversionType.EXTRACT_AUDIO, MimeType.Audio.MP3)
            }
        } else {
            val first = fileNames.firstOrNull()?.lowercase() ?: ""
            when {
                first.endsWith(".png") || first.endsWith(".jpg") || first.endsWith(".jpeg") || first.endsWith(".webp") || first.endsWith(".heic") ->
                    mainViewModel.configureCustom(workingUris, ConversionType.IMAGE_COMPRESS, MimeType.Image.WEBP)
                first.endsWith(".mp4") || first.endsWith(".mkv") || first.endsWith(".mov") || first.endsWith(".webm") ->
                    mainViewModel.configureCustom(workingUris, ConversionType.VIDEO_COMPRESS, MimeType.Video.MP4)
                first.endsWith(".pdf") ->
                    mainViewModel.configureCustom(workingUris, ConversionType.PDF_TO_IMAGES, MimeType.Image.JPEG)
                first.endsWith(".mp3") || first.endsWith(".m4a") || first.endsWith(".aac") || first.endsWith(".wav") ->
                    mainViewModel.configureCustom(workingUris, ConversionType.EXTRACT_AUDIO, MimeType.Audio.MP3)
                else ->
                    mainViewModel.configureCustom(workingUris, ConversionType.IMAGE_COMPRESS, MimeType.Image.WEBP)
            }
        }
    }

    // Modern Android Photo & Video Picker (Multi-select)
    val visualMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        processPickedUris(uris)
    }

    // Storage Access Framework (SAF) Document / Audio Picker
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        processPickedUris(uris)
    }

    // Interstitial consumption callback
    LaunchedEffect(shouldShowInterstitial) {
        if (shouldShowInterstitial) {
            mainViewModel.onInterstitialConsumed()
        }
    }

    TapConvertTheme {
        // Fast Pass Monetization Dialog
        if (showFastPassDialog) {
            AlertDialog(
                onDismissRequest = { showFastPassDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "TapConvert Fast-Pass",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "⚡ Unlimited Batch Conversions (50+ files)\n⚡ GPU Ultra Multi-Threaded Processing\n⚡ 100% Ad-Free for 24 Hours",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            mainViewModel.unlockBatchMode(AdReward.BatchModeUnlock())
                            showFastPassDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
                    ) {
                        Text("Watch 1 Short Video (Free 24h)", color = MaterialTheme.colorScheme.surface)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFastPassDialog = false }) {
                        Text("Maybe Later")
                    }
                }
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // Navigation Rail for Tablets / Foldables / Landscape
            if (isExpandedScreen && uiState is ConversionUiState.Idle) {
                NavigationRail {
                    NavigationRailItem(
                        selected = currentTab == NavigationTab.DASHBOARD,
                        onClick = { currentTab = NavigationTab.DASHBOARD },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                        label = { Text("Convert") }
                    )
                    NavigationRailItem(
                        selected = currentTab == NavigationTab.HISTORY,
                        onClick = { currentTab = NavigationTab.HISTORY },
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("History") }
                    )
                }
            }

            Scaffold(
                topBar = {
                    if (uiState is ConversionUiState.Idle) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "TapConvert",
                                    fontWeight = FontWeight.ExtraBold
                                )
                            },
                            actions = {
                                if (adState.isBatchModeUnlocked()) {
                                    AssistChip(
                                        onClick = { showFastPassDialog = true },
                                        label = { Text("Fast Pass Active") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = AccentAmber
                                            )
                                        }
                                    )
                                } else {
                                    FilledTonalButton(
                                        onClick = { showFastPassDialog = true },
                                        shape = RoundedCornerShape(99.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = AccentAmber
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Fast Pass", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    if (!isExpandedScreen && uiState is ConversionUiState.Idle) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentTab == NavigationTab.DASHBOARD,
                                onClick = { currentTab = NavigationTab.DASHBOARD },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                                label = { Text("Convert") }
                            )
                            NavigationBarItem(
                                selected = currentTab == NavigationTab.HISTORY,
                                onClick = { currentTab = NavigationTab.HISTORY },
                                icon = { Icon(Icons.Default.History, contentDescription = "History") },
                                label = { Text("History") }
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when (val state = uiState) {
                        is ConversionUiState.Idle -> {
                            when (currentTab) {
                                NavigationTab.DASHBOARD -> {
                                    DashboardScreen(
                                        records = historyRecords,
                                        totalStorageBytes = totalStorageBytes,
                                        onCategoryClick = { category ->
                                            pendingPreset = null
                                            pendingCategory = category
                                            when (category) {
                                                MediaCategory.IMAGE -> {
                                                    visualMediaPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                }
                                                MediaCategory.VIDEO -> {
                                                    visualMediaPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                    )
                                                }
                                                MediaCategory.DOCUMENT -> {
                                                    documentPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                                                }
                                                MediaCategory.AUDIO -> {
                                                    documentPickerLauncher.launch(arrayOf("audio/*", "video/*"))
                                                }
                                            }
                                        },
                                        onPresetClick = { preset ->
                                            pendingPreset = preset
                                            pendingCategory = null
                                            when (preset.category) {
                                                MediaCategory.IMAGE -> {
                                                    visualMediaPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                }
                                                MediaCategory.VIDEO -> {
                                                    visualMediaPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                    )
                                                }
                                                MediaCategory.DOCUMENT -> {
                                                    documentPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                                                }
                                                MediaCategory.AUDIO -> {
                                                    documentPickerLauncher.launch(arrayOf("audio/*", "video/*"))
                                                }
                                            }
                                        },
                                        onUniversalIntakeClick = {
                                            pendingPreset = null
                                            pendingCategory = null
                                            visualMediaPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                            )
                                        },
                                        onHistoryClick = { currentTab = NavigationTab.HISTORY },
                                        onFastPassClick = { showFastPassDialog = true }
                                    )
                                }
                                NavigationTab.HISTORY -> {
                                    HistoryScreen(
                                        records = historyRecords,
                                        totalStorageBytes = totalStorageBytes,
                                        onlyFavoritesFilter = onlyFavoritesFilter,
                                        onToggleFavoritesFilter = { historyViewModel.toggleFavoritesFilter() },
                                        onToggleFavorite = { id, fav -> historyViewModel.toggleFavorite(id, fav) },
                                        onDeleteRecord = { id -> historyViewModel.deleteRecord(id) },
                                        onCleanCacheClick = { historyViewModel.triggerDiskCleanup() }
                                    )
                                }
                            }
                        }

                        is ConversionUiState.Configuring -> {
                            ConfigurationScreen(
                                request = state.request,
                                sourceFileNames = state.sourceFileNames,
                                onQualityChange = { mainViewModel.updateQuality(it) },
                                onConvertClick = {
                                    val outDir = File(context.filesDir, "conversions")
                                    mainViewModel.startConversion(outDir)
                                },
                                onBackClick = { mainViewModel.resetToIdle() }
                            )
                        }

                        is ConversionUiState.Processing -> {
                            ProcessingScreen(
                                stage = state.stage,
                                percentage = state.percentage,
                                statusMessage = state.statusMessage,
                                onCancelClick = { mainViewModel.cancelConversion() },
                                onRunInBackgroundClick = { mainViewModel.resetToIdle() }
                            )
                        }

                        is ConversionUiState.Success -> {
                            ResultScreen(
                                result = state.result,
                                record = state.record,
                                onShareClick = { filePath ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "*/*"
                                        putExtra(Intent.EXTRA_STREAM, filePath)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Converted File"))
                                },
                                onFavoriteToggle = { id, fav -> mainViewModel.toggleFavorite(id, fav) },
                                onDoneClick = { mainViewModel.resetToIdle() }
                            )
                        }

                        is ConversionUiState.Error -> {
                            AlertDialog(
                                onDismissRequest = { mainViewModel.resetToIdle() },
                                title = { Text("Conversion Failed") },
                                text = { Text(state.error.userReadableMessage) },
                                confirmButton = {
                                    TextButton(onClick = { mainViewModel.resetToIdle() }) {
                                        Text("OK")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}



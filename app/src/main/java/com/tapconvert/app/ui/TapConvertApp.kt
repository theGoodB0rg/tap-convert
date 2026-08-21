package com.tapconvert.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tapconvert.app.share.ShareHelper
import com.tapconvert.app.share.ShareIntentParser
import com.tapconvert.app.ui.components.TierLimitExceededDialog
import com.tapconvert.app.ui.config.ConfigurationScreen
import com.tapconvert.app.ui.dashboard.DashboardScreen
import com.tapconvert.app.ui.dashboard.DocumentStudioSheet
import com.tapconvert.app.ui.history.HistoryScreen
import com.tapconvert.app.ui.history.HistoryViewModel
import com.tapconvert.app.ui.processing.ProcessingScreen
import com.tapconvert.app.ui.result.ResultScreen
import com.tapconvert.app.ui.settings.AboutUsScreen
import com.tapconvert.app.ui.settings.HowToUseScreen
import com.tapconvert.app.ui.settings.PrivacyPolicyScreen
import com.tapconvert.app.ui.settings.SettingsScreen
import com.tapconvert.app.ui.theme.AccentAmber
import com.tapconvert.app.ui.theme.TapConvertTheme
import com.tapconvert.core.ads.AdReward
import com.tapconvert.core.common.DataStoreAppSettingsManager
import com.tapconvert.core.common.MediaPublicExporter
import com.tapconvert.core.database.cleaner.LruDiskCleaner
import com.tapconvert.core.database.repository.RoomConversionHistoryRepository
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MediaCategory
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.Preset
import kotlinx.coroutines.launch
import java.io.File

enum class NavigationTab {
    DASHBOARD,
    HISTORY,
    SETTINGS
}

enum class SettingsSubScreen {
    MAIN,
    HOW_TO_USE,
    PRIVACY_POLICY,
    ABOUT_US
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapConvertApp() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val isExpandedScreen = configuration.screenWidthDp >= 600

    // Initialize Room SQLite Database & Repository
    val repository = remember { RoomConversionHistoryRepository.create(context) }
    val diskCleaner = remember {
        LruDiskCleaner(
            cacheDirectories = listOf(context.cacheDir, File(context.filesDir, "conversions")),
            repository = repository
        )
    }

    // Initialize Settings Manager
    val settingsManager = remember { DataStoreAppSettingsManager.create(context) }
    val customStorageUri by settingsManager.customStorageUri.collectAsState(initial = null)
    val customStorageDisplayPath by settingsManager.customStorageDisplayPath.collectAsState(initial = null)
    val autoSaveToGallery by settingsManager.autoSaveToGallery.collectAsState(initial = true)

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
    val tierLimitExceeded by mainViewModel.tierLimitExceeded.collectAsState()

    val historyRecords by historyViewModel.records.collectAsState()
    val totalStorageBytes by historyViewModel.totalStorageUsageBytes.collectAsState()
    val onlyFavoritesFilter by historyViewModel.onlyFavoritesFilter.collectAsState()

    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }
    var settingsSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN) }
    var showFastPassDialog by remember { mutableStateOf(false) }
    var showDocumentStudioSheet by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showCancelProcessingDialog by remember { mutableStateOf(false) }

    var pendingPreset by remember { mutableStateOf<Preset?>(null) }
    var pendingCategory by remember { mutableStateOf<MediaCategory?>(null) }

    // Layered BackHandler Architecture
    // 1. Settings sub-screens
    BackHandler(enabled = settingsSubScreen != SettingsSubScreen.MAIN) {
        settingsSubScreen = SettingsSubScreen.MAIN
    }

    // 2. Non-dashboard tabs (History / Settings)
    BackHandler(enabled = uiState is ConversionUiState.Idle && currentTab != NavigationTab.DASHBOARD && settingsSubScreen == SettingsSubScreen.MAIN) {
        currentTab = NavigationTab.DASHBOARD
    }

    // 3. Configuration, Success, or Error states -> Reset to Idle
    BackHandler(enabled = uiState is ConversionUiState.Configuring || uiState is ConversionUiState.Success || uiState is ConversionUiState.Error) {
        mainViewModel.resetToIdle()
    }

    // 4. In-flight processing state -> prompt cancel confirmation
    BackHandler(enabled = uiState is ConversionUiState.Processing) {
        showCancelProcessingDialog = true
    }

    // 5. Root Dashboard -> prompt exit confirmation dialog
    BackHandler(enabled = uiState is ConversionUiState.Idle && currentTab == NavigationTab.DASHBOARD && settingsSubScreen == SettingsSubScreen.MAIN) {
        showExitDialog = true
    }

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

        if (uiState is ConversionUiState.Configuring) {
            mainViewModel.addSourceUris(workingUris)
            return
        }

        val preset = pendingPreset
        val category = pendingCategory
        pendingPreset = null
        pendingCategory = null

        if (preset != null) {
            mainViewModel.checkAndExecuteIntake(workingUris, preset.conversionType) { allowed ->
                mainViewModel.selectPreset(preset, allowed)
            }
        } else if (category != null) {
            when (category) {
                MediaCategory.IMAGE -> {
                    mainViewModel.checkAndExecuteIntake(workingUris, ConversionType.IMAGE_COMPRESS) { allowed ->
                        mainViewModel.configureCustom(allowed, ConversionType.IMAGE_COMPRESS, MimeType.Image.WEBP)
                    }
                }
                MediaCategory.VIDEO -> {
                    mainViewModel.checkAndExecuteIntake(workingUris, ConversionType.VIDEO_COMPRESS) { allowed ->
                        mainViewModel.configureCustom(allowed, ConversionType.VIDEO_COMPRESS, MimeType.Video.MP4)
                    }
                }
                MediaCategory.DOCUMENT -> {
                    mainViewModel.checkAndExecuteIntake(workingUris, ConversionType.IMAGES_TO_PDF) { allowed ->
                        mainViewModel.configureCustom(allowed, ConversionType.IMAGES_TO_PDF, MimeType.Document.PDF)
                    }
                }
                MediaCategory.AUDIO -> {
                    mainViewModel.checkAndExecuteIntake(workingUris, ConversionType.EXTRACT_AUDIO) { allowed ->
                        mainViewModel.configureCustom(allowed, ConversionType.EXTRACT_AUDIO, MimeType.Audio.MP3)
                    }
                }
            }
        } else {
            val first = fileNames.firstOrNull()?.lowercase() ?: ""
            when {
                first.endsWith(".png") || first.endsWith(".jpg") || first.endsWith(".jpeg") || first.endsWith(".webp") || first.endsWith(".heic") -> {
                    mainViewModel.checkAndExecuteIntake(workingUris, ConversionType.IMAGE_COMPRESS) { allowed ->
                        mainViewModel.configureCustom(allowed, ConversionType.IMAGE_COMPRESS, MimeType.Image.WEBP)
                    }
                }
                first.endsWith(".mp4") || first.endsWith(".mkv") || first.endsWith(".mov") || first.endsWith(".webm") -> {
                    mainViewModel.checkAndExecuteIntake(workingUris, ConversionType.VIDEO_COMPRESS) { allowed ->
                        mainViewModel.configureCustom(allowed, ConversionType.VIDEO_COMPRESS, MimeType.Video.MP4)
                    }
                }
                first.endsWith(".pdf") -> {
                    mainViewModel.checkAndExecuteIntake(workingUris, ConversionType.PDF_TO_IMAGES) { allowed ->
                        mainViewModel.configureCustom(allowed, ConversionType.PDF_TO_IMAGES, MimeType.Image.JPEG)
                    }
                }
                first.endsWith(".mp3") || first.endsWith(".m4a") || first.endsWith(".aac") || first.endsWith(".wav") -> {
                    mainViewModel.checkAndExecuteIntake(workingUris, ConversionType.EXTRACT_AUDIO) { allowed ->
                        mainViewModel.configureCustom(allowed, ConversionType.EXTRACT_AUDIO, MimeType.Audio.MP3)
                    }
                }
                else -> {
                    mainViewModel.checkAndExecuteIntake(workingUris, ConversionType.IMAGE_COMPRESS) { allowed ->
                        mainViewModel.configureCustom(allowed, ConversionType.IMAGE_COMPRESS, MimeType.Image.WEBP)
                    }
                }
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

    // Storage Access Framework (SAF) Custom Save Folder Picker
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Throwable) {}
            val displayPath = uri.lastPathSegment ?: uri.path ?: "Custom Folder"
            coroutineScope.launch {
                settingsManager.setCustomStorageLocation(uri.toString(), displayPath)
                Toast.makeText(context, "Save folder updated: $displayPath", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Intent handoff from Share Sheet or external launch
    val initialIntakeUris = (context as? Activity)?.intent?.getStringArrayListExtra("EXTRA_INTAKE_URIS")
    LaunchedEffect(initialIntakeUris) {
        if (!initialIntakeUris.isNullOrEmpty()) {
            val uris = initialIntakeUris.map { Uri.parse(it) }
            processPickedUris(uris)
            (context as? Activity)?.intent?.removeExtra("EXTRA_INTAKE_URIS")
        }
    }

    // Interstitial consumption callback
    LaunchedEffect(shouldShowInterstitial) {
        if (shouldShowInterstitial) {
            mainViewModel.onInterstitialConsumed()
        }
    }

    // Auto export to public MediaStore on successful conversion
    LaunchedEffect(uiState) {
        val successState = uiState as? ConversionUiState.Success
        if (successState != null && autoSaveToGallery) {
            val outputUris = successState.result.outputUris
            for (uriStr in outputUris) {
                val f = File(uriStr.removePrefix("file://"))
                if (f.exists()) {
                    val mimeType = MimeType.fromFileName(f.name)
                    val exportResult = MediaPublicExporter.exportFile(
                        context = context,
                        sourceFile = f,
                        mimeType = mimeType,
                        customTreeUriString = customStorageUri
                    )
                    if (exportResult.isSuccess) {
                        Toast.makeText(context, exportResult.destinationDisplay, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    TapConvertTheme {
        // Tier Limit Exceeded Dialog
        tierLimitExceeded?.let { limitInfo ->
            TierLimitExceededDialog(
                limitInfo = limitInfo,
                onProceedWithLimit = { allowed ->
                    mainViewModel.proceedWithClampedLimit(allowed)
                },
                onUnlockFastPass = {
                    mainViewModel.unlockBatchMode(AdReward.BatchModeUnlock())
                    mainViewModel.retryPendingIntakeWithNewTier()
                    Toast.makeText(context, "Fast Pass Active (24h)!", Toast.LENGTH_SHORT).show()
                },
                onUpgradePro = {
                    mainViewModel.dismissTierLimit()
                    showFastPassDialog = true
                },
                onDismiss = {
                    mainViewModel.dismissTierLimit()
                }
            )
        }

        // PDF & Document Studio Sheet
        if (showDocumentStudioSheet) {
            DocumentStudioSheet(
                onPhotosToPdfClick = {
                    pendingPreset = null
                    pendingCategory = MediaCategory.DOCUMENT
                    visualMediaPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPdfToPhotosClick = {
                    pendingPreset = null
                    pendingCategory = null
                    documentPickerLauncher.launch(arrayOf("application/pdf"))
                },
                onDismiss = { showDocumentStudioSheet = false }
            )
        }

        // Fast Pass & Pro Monetization Dialog
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
                        text = if (adState.isPro) "TapConvert Pro Active" else "Unlock TapConvert Pro",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "⚡ Unlimited Batch Conversions (500+ files)\n⚡ Lightning Fast Offline Transcoding\n⚡ 100% Ad-Free Permanent Experience\n⚡ Custom Cloud & Storage Output",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!adState.isPro) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    mainViewModel.purchasePro(com.tapconvert.core.ads.SubscriptionPlan.Annual)
                                    showFastPassDialog = false
                                    Toast.makeText(context, "Upgraded to TapConvert Pro Annual!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Upgrade to Pro Annual ($9.99/yr)", maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = {
                                    mainViewModel.purchasePro(com.tapconvert.core.ads.SubscriptionPlan.Monthly)
                                    showFastPassDialog = false
                                    Toast.makeText(context, "Upgraded to TapConvert Pro Monthly!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Upgrade to Pro Monthly ($0.99/mo)", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!adState.isPro) {
                        FilledTonalButton(
                            onClick = {
                                mainViewModel.unlockBatchMode(AdReward.BatchModeUnlock())
                                showFastPassDialog = false
                            }
                        ) {
                            Text("Free 24h Pass (Watch Ad)", maxLines = 1, softWrap = false)
                        }
                    } else {
                        Button(onClick = { showFastPassDialog = false }) {
                            Text("Done", maxLines = 1, softWrap = false)
                        }
                    }
                },
                dismissButton = {
                    if (!adState.isPro) {
                        TextButton(onClick = { showFastPassDialog = false }) {
                            Text("Maybe Later", maxLines = 1, softWrap = false)
                        }
                    }
                }
            )
        }

        // Exit Confirmation Dialog
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Exit TapConvert?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to close the app?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitDialog = false
                            (context as? Activity)?.finish()
                        }
                    ) {
                        Text("Exit", maxLines = 1, softWrap = false)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Cancel", maxLines = 1, softWrap = false)
                    }
                }
            )
        }

        // Cancel Active Processing Confirmation Dialog
        if (showCancelProcessingDialog) {
            AlertDialog(
                onDismissRequest = { showCancelProcessingDialog = false },
                title = { Text("Cancel Active Conversion?", fontWeight = FontWeight.Bold) },
                text = { Text("Your media is currently being converted. Canceling will stop processing and discard incomplete output.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showCancelProcessingDialog = false
                            mainViewModel.cancelConversion()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Stop Conversion", maxLines = 1, softWrap = false)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelProcessingDialog = false }) {
                        Text("Keep Converting", maxLines = 1, softWrap = false)
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
                        label = { Text("Convert", maxLines = 1, softWrap = false) }
                    )
                    NavigationRailItem(
                        selected = currentTab == NavigationTab.HISTORY,
                        onClick = { currentTab = NavigationTab.HISTORY },
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("History", maxLines = 1, softWrap = false) }
                    )
                    NavigationRailItem(
                        selected = currentTab == NavigationTab.SETTINGS,
                        onClick = {
                            settingsSubScreen = SettingsSubScreen.MAIN
                            currentTab = NavigationTab.SETTINGS
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", maxLines = 1, softWrap = false) }
                    )
                }
            }

            Scaffold(
                topBar = {
                    if (uiState is ConversionUiState.Idle && currentTab != NavigationTab.SETTINGS) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "TapConvert",
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            actions = {
                                if (adState.isBatchModeUnlocked()) {
                                    AssistChip(
                                        onClick = { showFastPassDialog = true },
                                        label = { Text("Fast Pass Active", maxLines = 1, softWrap = false) },
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
                                        Text("Fast Pass", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
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
                                label = { Text("Convert", maxLines = 1, softWrap = false) }
                            )
                            NavigationBarItem(
                                selected = currentTab == NavigationTab.HISTORY,
                                onClick = { currentTab = NavigationTab.HISTORY },
                                icon = { Icon(Icons.Default.History, contentDescription = "History") },
                                label = { Text("History", maxLines = 1, softWrap = false) }
                            )
                            NavigationBarItem(
                                selected = currentTab == NavigationTab.SETTINGS,
                                onClick = {
                                    settingsSubScreen = SettingsSubScreen.MAIN
                                    currentTab = NavigationTab.SETTINGS
                                },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings", maxLines = 1, softWrap = false) }
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
                                                    showDocumentStudioSheet = true
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
                                                    showDocumentStudioSheet = true
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
                                        onCleanCacheClick = {
                                            historyViewModel.triggerDiskCleanup { report ->
                                                val msg = if (report.filesDeleted > 0 || report.bytesReclaimed > 0L) {
                                                    val mb = report.bytesReclaimed / (1024.0 * 1024.0)
                                                    "Freed %.1f MB (%d files)".format(mb, report.filesDeleted)
                                                } else {
                                                    "Storage cache is clean"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                                NavigationTab.SETTINGS -> {
                                    when (settingsSubScreen) {
                                        SettingsSubScreen.MAIN -> {
                                            SettingsScreen(
                                                customStorageDisplayPath = customStorageDisplayPath,
                                                autoSaveToGallery = autoSaveToGallery,
                                                totalStorageBytes = totalStorageBytes,
                                                onSelectCustomFolderClick = { openDocumentTreeLauncher.launch(null) },
                                                onResetToDefaultFolderClick = {
                                                    coroutineScope.launch {
                                                        settingsManager.resetToDefaultStorage()
                                                        Toast.makeText(context, "Reset to default gallery storage", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                onToggleAutoSaveToGallery = { enabled ->
                                                    coroutineScope.launch { settingsManager.setAutoSaveToGallery(enabled) }
                                                },
                                                onCleanCacheClick = {
                                                    historyViewModel.triggerDiskCleanup { report ->
                                                        val msg = if (report.filesDeleted > 0 || report.bytesReclaimed > 0L) {
                                                            val mb = report.bytesReclaimed / (1024.0 * 1024.0)
                                                            "Freed %.1f MB (%d files)".format(mb, report.filesDeleted)
                                                        } else {
                                                            "Storage cache is clean"
                                                        }
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                onHowToUseClick = { settingsSubScreen = SettingsSubScreen.HOW_TO_USE },
                                                onPrivacyPolicyClick = { settingsSubScreen = SettingsSubScreen.PRIVACY_POLICY },
                                                onAboutUsClick = { settingsSubScreen = SettingsSubScreen.ABOUT_US },
                                                onBackClick = { currentTab = NavigationTab.DASHBOARD }
                                            )
                                        }
                                        SettingsSubScreen.HOW_TO_USE -> {
                                            HowToUseScreen(
                                                onBackClick = { settingsSubScreen = SettingsSubScreen.MAIN }
                                            )
                                        }
                                        SettingsSubScreen.PRIVACY_POLICY -> {
                                            PrivacyPolicyScreen(
                                                onBackClick = { settingsSubScreen = SettingsSubScreen.MAIN }
                                            )
                                        }
                                        SettingsSubScreen.ABOUT_US -> {
                                            AboutUsScreen(
                                                onBackClick = { settingsSubScreen = SettingsSubScreen.MAIN }
                                            )
                                        }
                                    }
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
                                onBackClick = { mainViewModel.resetToIdle() },
                                onRemoveSourceUri = { index -> mainViewModel.removeSourceUri(index) },
                                onReorderSourceUris = { from, to -> mainViewModel.reorderSourceUris(from, to) },
                                onAddPhotosClick = {
                                    visualMediaPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
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
                                    val chooserIntent = ShareHelper.createShareChooserIntent(context, filePath)
                                    context.startActivity(chooserIntent)
                                },
                                onShareMultipleClick = { uris ->
                                    val chooserIntent = ShareHelper.createMultipleShareChooserIntent(context, uris)
                                    context.startActivity(chooserIntent)
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

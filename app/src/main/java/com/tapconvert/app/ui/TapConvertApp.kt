package com.tapconvert.app.ui

import android.content.Intent
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tapconvert.app.ui.config.ConfigurationScreen
import com.tapconvert.app.ui.dashboard.DashboardScreen
import com.tapconvert.app.ui.history.HistoryScreen
import com.tapconvert.app.ui.history.HistoryViewModel
import com.tapconvert.app.ui.processing.ProcessingScreen
import com.tapconvert.app.ui.result.ResultScreen
import com.tapconvert.app.ui.theme.AccentAmber
import com.tapconvert.app.ui.theme.TapConvertTheme
import com.tapconvert.core.ads.AdReward
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MediaCategory
import com.tapconvert.core.model.MimeType
import java.io.File

enum class NavigationTab {
    DASHBOARD,
    HISTORY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapConvertApp(
    mainViewModel: MainViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600

    val uiState by mainViewModel.uiState.collectAsState()
    val adState by mainViewModel.adManager.state.collectAsState()
    val shouldShowInterstitial by mainViewModel.shouldShowInterstitial.collectAsState()

    val historyRecords by historyViewModel.records.collectAsState()
    val totalStorageBytes by historyViewModel.totalStorageUsageBytes.collectAsState()
    val onlyFavoritesFilter by historyViewModel.onlyFavoritesFilter.collectAsState()

    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }
    var showFastPassDialog by remember { mutableStateOf(false) }

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
                                        onCategoryClick = { category ->
                                            val dummyUri = "file://${context.cacheDir.absolutePath}/sample"
                                            when (category) {
                                                MediaCategory.IMAGE -> mainViewModel.configureCustom(listOf("$dummyUri.png"), ConversionType.IMAGE_COMPRESS, MimeType.Image.JPEG)
                                                MediaCategory.VIDEO -> mainViewModel.configureCustom(listOf("$dummyUri.mp4"), ConversionType.VIDEO_COMPRESS, MimeType.Video.MP4)
                                                MediaCategory.DOCUMENT -> mainViewModel.configureCustom(listOf("$dummyUri.jpg"), ConversionType.IMAGES_TO_PDF, MimeType.Document.PDF)
                                                MediaCategory.AUDIO -> mainViewModel.configureCustom(listOf("$dummyUri.mp4"), ConversionType.EXTRACT_AUDIO, MimeType.Audio.MP3)
                                            }
                                        },
                                        onPresetClick = { preset ->
                                            val dummyUri = "file://${context.cacheDir.absolutePath}/sample_input"
                                            mainViewModel.selectPreset(preset, listOf(dummyUri))
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
                                        onDeleteRecord = { id -> historyViewModel.deleteRecord(id) }
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


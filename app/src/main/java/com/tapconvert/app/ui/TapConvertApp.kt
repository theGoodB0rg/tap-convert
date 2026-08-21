package com.tapconvert.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tapconvert.app.ui.config.ConfigurationScreen
import com.tapconvert.app.ui.dashboard.DashboardScreen
import com.tapconvert.app.ui.history.HistoryScreen
import com.tapconvert.app.ui.history.HistoryViewModel
import com.tapconvert.app.ui.processing.ProcessingScreen
import com.tapconvert.app.ui.result.ResultScreen
import com.tapconvert.app.ui.theme.TapConvertTheme
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
    val uiState by mainViewModel.uiState.collectAsState()
    val adState by mainViewModel.adManager.state.collectAsState()
    val shouldShowInterstitial by mainViewModel.shouldShowInterstitial.collectAsState()

    val historyRecords by historyViewModel.records.collectAsState()
    val totalStorageBytes by historyViewModel.totalStorageUsageBytes.collectAsState()
    val onlyFavoritesFilter by historyViewModel.onlyFavoritesFilter.collectAsState()

    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }

    // Interstitial consumption callback
    LaunchedEffect(shouldShowInterstitial) {
        if (shouldShowInterstitial) {
            mainViewModel.onInterstitialConsumed()
        }
    }

    TapConvertTheme {
        Scaffold(
            topBar = {
                if (uiState is ConversionUiState.Idle) {
                    TopAppBar(
                        title = { Text("TapConvert") },
                        actions = {
                            if (adState.isBatchModeUnlocked()) {
                                AssistChip(
                                    onClick = { },
                                    label = { Text("Fast Pass Active") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (uiState is ConversionUiState.Idle) {
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
            }
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
                                    onHistoryClick = { currentTab = NavigationTab.HISTORY }
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
                            onCancelClick = { mainViewModel.cancelConversion() }
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

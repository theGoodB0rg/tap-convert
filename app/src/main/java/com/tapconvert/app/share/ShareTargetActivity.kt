package com.tapconvert.app.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tapconvert.app.share.ui.ShareTargetBottomSheet
import com.tapconvert.app.ui.theme.TapConvertTheme
import java.io.File

class ShareTargetActivity : ComponentActivity() {

    private val viewModel: ShareTargetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.loadFromIntent(intent, contentResolver, cacheDir)

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            TapConvertTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (uiState !is ShareTargetUiState.Converting) {
                                finish()
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                // Block click bubbling to backdrop
                            }
                    ) {
                        ShareTargetBottomSheet(
                            uiState = uiState,
                            onSelectPreset = { viewModel.selectPreset(it) },
                            onSelectTargetMime = { viewModel.updateTargetMimeType(it) },
                            onQualityChange = { viewModel.updateQuality(it) },
                            onStartConversion = {
                                val outDir = File(filesDir, "conversions")
                                viewModel.startConversion(outDir)
                            },
                            onCancelConversion = {
                                viewModel.cancelConversion()
                                finish()
                            },
                            onShareResult = { outputPath ->
                                dispatchShareResult(outputPath)
                            },
                            onOpenInFullStudio = {
                                launchFullStudio()
                            },
                            onDismiss = {
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun launchFullStudio() {
        val state = viewModel.uiState.value
        val uris = when (state) {
            is ShareTargetUiState.Ready -> state.payload.sourceUris
            is ShareTargetUiState.Success -> state.result.outputUris
            else -> emptyList()
        }
        val intent = Intent(this, com.tapconvert.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putStringArrayListExtra("EXTRA_INTAKE_URIS", ArrayList(uris))
        }
        startActivity(intent)
        finish()
    }

    private fun dispatchShareResult(outputPath: String) {
        val file = File(outputPath)
        if (file.exists()) {
            val extension = file.extension.lowercase()
            val mimeType = when (extension) {
                "jpg", "jpeg" -> com.tapconvert.core.model.MimeType.Image.JPEG
                "png" -> com.tapconvert.core.model.MimeType.Image.PNG
                "webp" -> com.tapconvert.core.model.MimeType.Image.WEBP
                "mp4" -> com.tapconvert.core.model.MimeType.Video.MP4
                "mp3" -> com.tapconvert.core.model.MimeType.Audio.MP3
                "pdf" -> com.tapconvert.core.model.MimeType.Document.PDF
                else -> com.tapconvert.core.model.MimeType.Image.JPEG
            }
            com.tapconvert.core.common.MediaPublicExporter.exportFile(this, file, mimeType)
        }
        val chooserIntent = ShareHelper.createShareChooserIntent(this, outputPath)
        startActivity(chooserIntent)
        finish()
    }
}

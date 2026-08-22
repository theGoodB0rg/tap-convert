package com.tapconvert.feature.pdf.engine

import com.tapconvert.core.analytics.AnalyticsTracker
import com.tapconvert.core.analytics.NoOpAnalyticsTracker
import com.tapconvert.core.common.AppResult
import com.tapconvert.core.model.ConversionError
import com.tapconvert.core.model.ConversionProgress
import com.tapconvert.core.model.ConversionRequest
import com.tapconvert.core.model.ConversionResult
import com.tapconvert.core.model.ConversionStage
import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.UUID

interface PdfEngine {
    fun convertImagesToPdf(
        request: ConversionRequest,
        outputDirectory: File,
        pageSize: PdfPageSize = PdfPageSize.A4
    ): Flow<AppResult<ConversionResult>>

    fun extractPdfToImages(
        request: ConversionRequest,
        outputDirectory: File,
        targetFormat: MimeType.Image = MimeType.Image.JPEG,
        dpiScale: Float = 2.0f
    ): Flow<AppResult<ConversionResult>>
}

class DefaultPdfEngine(
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker()
) : PdfEngine {

    override fun convertImagesToPdf(
        request: ConversionRequest,
        outputDirectory: File,
        pageSize: PdfPageSize
    ): Flow<AppResult<ConversionResult>> = flow {
        val startTime = System.currentTimeMillis()

        if (request.sourceUris.isEmpty()) {
            val error = ConversionError.FileNotFound("No source images provided")
            analyticsTracker.logConversionFailed(ConversionType.IMAGES_TO_PDF, "FileNotFound", error.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(error))
            return@flow
        }

        val imageFiles = request.sourceUris.map { File(it.removePrefix("file://")) }
        val totalInputBytes = imageFiles.filter { it.exists() }.sumOf { it.length() }

        analyticsTracker.logConversionStarted(
            type = ConversionType.IMAGES_TO_PDF,
            inputSizeBytes = totalInputBytes,
            sourceFormat = "image/*",
            presetId = request.preset?.id
        )

        // Stage 1: Analyzing
        currentCoroutineContext().ensureActive()
        emit(AppResult.Progress(10, ConversionProgress(10, ConversionStage.ANALYZING, 1, imageFiles.size).overallSummary))

        outputDirectory.mkdirs()
        val outputName = if (!request.outputFileName.isNullOrBlank()) {
            request.outputFileName!!
        } else {
            val firstFileName = imageFiles.firstOrNull()?.name
            com.tapconvert.core.common.ExportFileNameGenerator.generate(
                originalName = firstFileName ?: "Document",
                extension = "pdf",
                fallbackName = "Document"
            )
        }
        val outputFile = File(outputDirectory, outputName)

        val conversionResult = ImagesToPdfConverter.convert(
            imageFiles = imageFiles,
            outputFile = outputFile,
            pageSize = pageSize,
            includeBranding = request.includeBranding,
            dimensionConstraint = request.dimensionConstraint,
            onPageProgress = { current, total ->
                val pct = 20 + (((current.toFloat() / total.toFloat()) * 70f).toInt())
                // Progress callback
            }
        )

        currentCoroutineContext().ensureActive()

        when (conversionResult) {
            is AppResult.Success -> {
                val duration = System.currentTimeMillis() - startTime
                val outputSize = outputFile.length()

                analyticsTracker.logConversionCompleted(
                    type = ConversionType.IMAGES_TO_PDF,
                    durationMs = duration,
                    inputSizeBytes = totalInputBytes,
                    outputSizeBytes = outputSize,
                    presetId = request.preset?.id
                )

                emit(
                    AppResult.Success(
                        ConversionResult(
                            requestId = request.id,
                            conversionType = ConversionType.IMAGES_TO_PDF,
                            outputUris = listOf(outputFile.absolutePath),
                            originalSizeBytes = totalInputBytes,
                            outputSizeBytes = outputSize,
                            durationMs = duration,
                            metadata = mapOf("pageCount" to imageFiles.size.toString())
                        )
                    )
                )
            }

            is AppResult.Error -> {
                analyticsTracker.logConversionFailed(
                    type = ConversionType.IMAGES_TO_PDF,
                    errorType = conversionResult.throwable::class.java.simpleName,
                    errorMessage = conversionResult.message,
                    presetId = request.preset?.id
                )
                emit(conversionResult)
            }

            is AppResult.Progress -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun extractPdfToImages(
        request: ConversionRequest,
        outputDirectory: File,
        targetFormat: MimeType.Image,
        dpiScale: Float
    ): Flow<AppResult<ConversionResult>> = flow {
        val startTime = System.currentTimeMillis()
        val sourceUri = request.sourceUris.firstOrNull()

        if (sourceUri == null) {
            val error = ConversionError.FileNotFound("No source PDF file provided")
            analyticsTracker.logConversionFailed(ConversionType.PDF_TO_IMAGES, "FileNotFound", error.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(error))
            return@flow
        }

        val pdfFile = File(sourceUri.removePrefix("file://"))
        val inputSize = if (pdfFile.exists()) pdfFile.length() else 0L

        analyticsTracker.logConversionStarted(
            type = ConversionType.PDF_TO_IMAGES,
            inputSizeBytes = inputSize,
            sourceFormat = "application/pdf",
            presetId = request.preset?.id
        )

        // Stage 1: Analyzing
        currentCoroutineContext().ensureActive()
        emit(AppResult.Progress(15, ConversionProgress(15, ConversionStage.ANALYZING).overallSummary))

        if (!pdfFile.exists()) {
            val error = ConversionError.FileNotFound(sourceUri)
            analyticsTracker.logConversionFailed(ConversionType.PDF_TO_IMAGES, "FileNotFound", error.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(error))
            return@flow
        }

        val extractResult = PdfToImagesExtractor.extract(
            pdfFile = pdfFile,
            outputDirectory = outputDirectory,
            targetFormat = targetFormat,
            qualityPercent = request.quality.qualityPercent,
            dpiScale = dpiScale,
            onPageProgress = { current, total ->
                // Progress
            }
        )

        currentCoroutineContext().ensureActive()

        when (extractResult) {
            is AppResult.Success -> {
                val outputFiles = extractResult.data
                val totalOutputSize = outputFiles.sumOf { it.length() }
                val duration = System.currentTimeMillis() - startTime

                analyticsTracker.logConversionCompleted(
                    type = ConversionType.PDF_TO_IMAGES,
                    durationMs = duration,
                    inputSizeBytes = inputSize,
                    outputSizeBytes = totalOutputSize,
                    presetId = request.preset?.id
                )

                emit(
                    AppResult.Success(
                        ConversionResult(
                            requestId = request.id,
                            conversionType = ConversionType.PDF_TO_IMAGES,
                            outputUris = outputFiles.map { it.absolutePath },
                            originalSizeBytes = inputSize,
                            outputSizeBytes = totalOutputSize,
                            durationMs = duration,
                            metadata = mapOf("pageCount" to outputFiles.size.toString())
                        )
                    )
                )
            }

            is AppResult.Error -> {
                analyticsTracker.logConversionFailed(
                    type = ConversionType.PDF_TO_IMAGES,
                    errorType = extractResult.throwable::class.java.simpleName,
                    errorMessage = extractResult.message,
                    presetId = request.preset?.id
                )
                emit(extractResult)
            }

            is AppResult.Progress -> Unit
        }
    }.flowOn(Dispatchers.IO)
}

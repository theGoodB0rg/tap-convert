package com.tapconvert.feature.image.engine

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
import java.io.FileOutputStream
import java.util.UUID

interface ImageEngine {
    fun process(request: ConversionRequest, outputDirectory: File): Flow<AppResult<ConversionResult>>
    fun processBatch(requests: List<ConversionRequest>, outputDirectory: File): Flow<AppResult<List<ConversionResult>>>
}

class DefaultImageEngine(
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker()
) : ImageEngine {

    override fun process(
        request: ConversionRequest,
        outputDirectory: File
    ): Flow<AppResult<ConversionResult>> = flow {
        val startTime = System.currentTimeMillis()
        val sourceUri = request.sourceUris.firstOrNull()
        if (sourceUri == null) {
            val error = ConversionError.FileNotFound("No source URI provided")
            analyticsTracker.logConversionFailed(request.conversionType, "FileNotFound", error.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(error))
            return@flow
        }

        val sourceFile = File(sourceUri.removePrefix("file://"))
        val originalSize = if (sourceFile.exists()) sourceFile.length() else 0L

        analyticsTracker.logConversionStarted(
            type = request.conversionType,
            inputSizeBytes = originalSize,
            sourceFormat = request.targetMimeType.rawMimeType,
            presetId = request.preset?.id
        )

        // Stage 1: Analyzing
        currentCoroutineContext().ensureActive()
        emit(AppResult.Progress(15, ConversionProgress(15, ConversionStage.ANALYZING).overallSummary))

        if (!sourceFile.exists()) {
            val error = ConversionError.FileNotFound(sourceUri)
            analyticsTracker.logConversionFailed(request.conversionType, "FileNotFound", error.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(error))
            return@flow
        }

        // Stage 2: Preparing & Decoding
        currentCoroutineContext().ensureActive()
        emit(AppResult.Progress(35, ConversionProgress(35, ConversionStage.PREPARING).overallSummary))

        val bitmap = BitmapDecoder.decodeFile(sourceFile, request.dimensionConstraint)
        if (bitmap == null) {
            val error = ConversionError.CorruptFile("Failed to decode image bitmap", sourceUri)
            analyticsTracker.logConversionFailed(request.conversionType, "CorruptFile", error.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(error))
            return@flow
        }

        // Stage 3 & 4: Processing & Compressing
        currentCoroutineContext().ensureActive()
        emit(AppResult.Progress(65, ConversionProgress(65, ConversionStage.COMPRESSING).overallSummary))

        val targetImageFormat = when (val mime = request.targetMimeType) {
            is MimeType.Image -> mime
            else -> MimeType.Image.JPEG
        }

        val outputBytes: ByteArray
        val targetSize = request.targetSize
        if (targetSize != null) {
            when (val compressResult = ImageTargetCompressor.compress(bitmap, targetSize, targetImageFormat)) {
                is AppResult.Success -> {
                    outputBytes = compressResult.data.compressedBytes
                }
                is AppResult.Error -> {
                    if (!bitmap.isRecycled) bitmap.recycle()
                    analyticsTracker.logConversionFailed(request.conversionType, "TargetSizeUnachievable", compressResult.message, request.preset?.id)
                    emit(compressResult)
                    return@flow
                }
                is AppResult.Progress -> {
                    outputBytes = ByteArray(0)
                }
            }
        } else {
            outputBytes = ImageFormatConverter.convert(bitmap, targetImageFormat, request.quality.qualityPercent)
        }

        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }

        // Stage 5: Finalizing & Saving
        currentCoroutineContext().ensureActive()
        emit(AppResult.Progress(90, ConversionProgress(90, ConversionStage.FINALIZING).overallSummary))

        outputDirectory.mkdirs()
        val extension = targetImageFormat.primaryExtension
        val outputFileName = request.outputFileName ?: "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension"
        val outputFile = File(outputDirectory, outputFileName)

        try {
            FileOutputStream(outputFile).use { it.write(outputBytes) }
        } catch (e: Throwable) {
            val ioError = ConversionError.IOError(e.message ?: "Failed to write output file", e)
            analyticsTracker.logConversionFailed(request.conversionType, "IOError", ioError.userReadableMessage, request.preset?.id)
            emit(AppResult.Error(ioError))
            return@flow
        }

        if (request.preserveExif && targetImageFormat is MimeType.Image.JPEG) {
            ExifTransformer.copyExifAttributes(sourceFile, outputFile)
        }

        val duration = System.currentTimeMillis() - startTime
        val outputSize = outputFile.length()

        analyticsTracker.logConversionCompleted(
            type = request.conversionType,
            durationMs = duration,
            inputSizeBytes = originalSize,
            outputSizeBytes = outputSize,
            presetId = request.preset?.id
        )

        emit(
            AppResult.Success(
                ConversionResult(
                    requestId = request.id,
                    conversionType = request.conversionType,
                    outputUris = listOf(outputFile.absolutePath),
                    originalSizeBytes = originalSize,
                    outputSizeBytes = outputSize,
                    durationMs = duration,
                    metadata = mapOf("format" to targetImageFormat.displayName)
                )
            )
        )
    }.flowOn(Dispatchers.IO)

    override fun processBatch(
        requests: List<ConversionRequest>,
        outputDirectory: File
    ): Flow<AppResult<List<ConversionResult>>> = flow {
        val total = requests.size
        val results = mutableListOf<ConversionResult>()

        requests.forEachIndexed { index, request ->
            currentCoroutineContext().ensureActive()
            val itemNumber = index + 1
            emit(AppResult.Progress(
                percentage = ((index.toFloat() / total.toFloat()) * 100).toInt(),
                currentStep = "Processing item $itemNumber of $total"
            ))

            var itemResult: ConversionResult? = null
            var itemError: AppResult.Error? = null

            process(request, outputDirectory).collect { step ->
                when (step) {
                    is AppResult.Success -> itemResult = step.data
                    is AppResult.Error -> itemError = step
                    is AppResult.Progress -> Unit
                }
            }

            if (itemError != null) {
                emit(itemError!!)
                return@flow
            }

            itemResult?.let { results.add(it) }
        }

        emit(AppResult.Success(results))
    }.flowOn(Dispatchers.IO)
}

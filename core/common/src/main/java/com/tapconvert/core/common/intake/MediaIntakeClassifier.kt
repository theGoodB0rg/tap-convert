package com.tapconvert.core.common.intake

import com.tapconvert.core.model.ConversionType
import com.tapconvert.core.model.MediaCategory
import com.tapconvert.core.model.MimeType
import com.tapconvert.core.model.Preset

data class IntakeRoutingDecision(
    val category: MediaCategory,
    val conversionType: ConversionType,
    val targetMimeType: MimeType
)

interface MediaIntakeClassifier {
    fun resolve(
        items: List<StagedMediaItem>,
        explicitPreset: Preset? = null,
        explicitCategory: MediaCategory? = null,
        explicitType: ConversionType? = null
    ): IntakeRoutingDecision
}

class DefaultMediaIntakeClassifier : MediaIntakeClassifier {

    override fun resolve(
        items: List<StagedMediaItem>,
        explicitPreset: Preset?,
        explicitCategory: MediaCategory?,
        explicitType: ConversionType?
    ): IntakeRoutingDecision {
        // 1. Explicit Preset takes highest precedence
        if (explicitPreset != null) {
            return IntakeRoutingDecision(
                category = explicitPreset.category,
                conversionType = explicitPreset.conversionType,
                targetMimeType = explicitPreset.targetMimeType
            )
        }

        // 2. Explicit Conversion Type
        if (explicitType != null) {
            val (category, defaultTarget) = when (explicitType) {
                ConversionType.IMAGE_COMPRESS -> MediaCategory.IMAGE to MimeType.Image.WEBP
                ConversionType.IMAGE_CONVERT -> MediaCategory.IMAGE to MimeType.Image.JPEG
                ConversionType.VIDEO_COMPRESS -> MediaCategory.VIDEO to MimeType.Video.MP4
                ConversionType.IMAGES_TO_PDF -> MediaCategory.DOCUMENT to MimeType.Document.PDF
                ConversionType.PDF_TO_IMAGES -> MediaCategory.DOCUMENT to MimeType.Image.JPEG
                ConversionType.PDF_COMPRESS -> MediaCategory.DOCUMENT to MimeType.Document.PDF
                ConversionType.EXTRACT_AUDIO -> MediaCategory.AUDIO to MimeType.Audio.MP3
            }
            return IntakeRoutingDecision(category, explicitType, defaultTarget)
        }

        // 3. Explicit Category
        if (explicitCategory != null) {
            return when (explicitCategory) {
                MediaCategory.IMAGE -> IntakeRoutingDecision(
                    category = MediaCategory.IMAGE,
                    conversionType = ConversionType.IMAGE_COMPRESS,
                    targetMimeType = MimeType.Image.WEBP
                )
                MediaCategory.VIDEO -> IntakeRoutingDecision(
                    category = MediaCategory.VIDEO,
                    conversionType = ConversionType.VIDEO_COMPRESS,
                    targetMimeType = MimeType.Video.MP4
                )
                MediaCategory.DOCUMENT -> {
                    val allPdfs = items.isNotEmpty() && items.all {
                        val mime = MimeType.fromFileName(it.originalName)
                        mime is MimeType.Document.PDF || it.mimeType == "application/pdf"
                    }
                    if (allPdfs) {
                        IntakeRoutingDecision(
                            category = MediaCategory.DOCUMENT,
                            conversionType = ConversionType.PDF_COMPRESS,
                            targetMimeType = MimeType.Document.PDF
                        )
                    } else {
                        IntakeRoutingDecision(
                            category = MediaCategory.DOCUMENT,
                            conversionType = ConversionType.IMAGES_TO_PDF,
                            targetMimeType = MimeType.Document.PDF
                        )
                    }
                }
                MediaCategory.AUDIO -> IntakeRoutingDecision(
                    category = MediaCategory.AUDIO,
                    conversionType = ConversionType.EXTRACT_AUDIO,
                    targetMimeType = MimeType.Audio.MP3
                )
            }
        }

        // 4. Dynamic Auto-Detection (Universal Intake / External Share)
        val firstItem = items.firstOrNull()
        if (firstItem != null) {
            val detectedMime = MimeType.fromFileName(firstItem.originalName).let {
                if (it !is MimeType.Other) it else MimeType.fromMime(firstItem.mimeType)
            }

            return when (detectedMime.category) {
                MediaCategory.VIDEO -> IntakeRoutingDecision(
                    category = MediaCategory.VIDEO,
                    conversionType = ConversionType.VIDEO_COMPRESS,
                    targetMimeType = MimeType.Video.MP4
                )
                MediaCategory.AUDIO -> IntakeRoutingDecision(
                    category = MediaCategory.AUDIO,
                    conversionType = ConversionType.EXTRACT_AUDIO,
                    targetMimeType = MimeType.Audio.MP3
                )
                MediaCategory.DOCUMENT -> {
                    if (detectedMime is MimeType.Document.PDF || firstItem.originalName.endsWith(".pdf", ignoreCase = true)) {
                        IntakeRoutingDecision(
                            category = MediaCategory.DOCUMENT,
                            conversionType = ConversionType.PDF_COMPRESS,
                            targetMimeType = MimeType.Document.PDF
                        )
                    } else {
                        IntakeRoutingDecision(
                            category = MediaCategory.DOCUMENT,
                            conversionType = ConversionType.IMAGES_TO_PDF,
                            targetMimeType = MimeType.Document.PDF
                        )
                    }
                }
                MediaCategory.IMAGE -> IntakeRoutingDecision(
                    category = MediaCategory.IMAGE,
                    conversionType = ConversionType.IMAGE_COMPRESS,
                    targetMimeType = MimeType.Image.WEBP
                )
            }
        }

        // 5. Default Fallback
        return IntakeRoutingDecision(
            category = MediaCategory.IMAGE,
            conversionType = ConversionType.IMAGE_COMPRESS,
            targetMimeType = MimeType.Image.WEBP
        )
    }
}

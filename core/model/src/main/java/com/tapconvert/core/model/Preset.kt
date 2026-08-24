package com.tapconvert.core.model

data class Preset(
    val id: String,
    val name: String,
    val description: String,
    val category: MediaCategory,
    val conversionType: ConversionType,
    val targetMimeType: MimeType,
    val targetSize: TargetSize? = null,
    val dimensionConstraint: DimensionConstraint = DimensionConstraint.None,
    val quality: ConversionQuality = ConversionQuality.High,
    val isPro: Boolean = false,
    val tag: String = "Standard"
) {
    companion object {
        // Video Presets
        val WhatsAppVideo16MB = Preset(
            id = "whatsapp_video_16mb",
            name = "WhatsApp Video",
            description = "Shrink video to fit within WhatsApp 16MB attachment limit",
            category = MediaCategory.VIDEO,
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            targetSize = TargetSize.fromMegabytes(16),
            dimensionConstraint = DimensionConstraint.MaxDimension(1280),
            quality = ConversionQuality.Medium,
            tag = "Social"
        )

        val EmailVideo25MB = Preset(
            id = "email_video_25mb",
            name = "Email Video",
            description = "Compress video to fit standard 25MB email attachment limit",
            category = MediaCategory.VIDEO,
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            targetSize = TargetSize.fromMegabytes(25),
            dimensionConstraint = DimensionConstraint.MaxDimension(1920),
            quality = ConversionQuality.High,
            tag = "Email"
        )

        val DiscordVideo10MB = Preset(
            id = "discord_video_10mb",
            name = "Discord Video (10MB)",
            description = "Compress video under Discord 10MB upload limit",
            category = MediaCategory.VIDEO,
            conversionType = ConversionType.VIDEO_COMPRESS,
            targetMimeType = MimeType.Video.MP4,
            targetSize = TargetSize.fromMegabytes(10),
            dimensionConstraint = DimensionConstraint.MaxDimension(1280),
            quality = ConversionQuality.Medium,
            tag = "Social"
        )

        // Image Presets
        val GovPassport200KB = Preset(
            id = "gov_passport_200kb",
            name = "Gov Portal / Passport (200 KB)",
            description = "Compress photo to exactly under 200 KB for official forms and passport portals",
            category = MediaCategory.IMAGE,
            conversionType = ConversionType.IMAGE_COMPRESS,
            targetMimeType = MimeType.Image.JPEG,
            targetSize = TargetSize.fromKilobytes(200),
            dimensionConstraint = DimensionConstraint.MaxDimension(1600),
            quality = ConversionQuality.High,
            tag = "Government"
        )

        val WebStandard500KB = Preset(
            id = "web_standard_500kb",
            name = "Web & Blog (500 KB)",
            description = "Fast-loading WebP format optimized under 500 KB",
            category = MediaCategory.IMAGE,
            conversionType = ConversionType.IMAGE_COMPRESS,
            targetMimeType = MimeType.Image.WEBP,
            targetSize = TargetSize.fromKilobytes(500),
            dimensionConstraint = DimensionConstraint.MaxDimension(1920),
            quality = ConversionQuality.High,
            tag = "Web"
        )

        val PngToJpeg = Preset(
            id = "png_to_jpeg",
            name = "PNG to JPEG",
            description = "Convert transparent or heavy PNGs to lightweight JPEG files",
            category = MediaCategory.IMAGE,
            conversionType = ConversionType.IMAGE_CONVERT,
            targetMimeType = MimeType.Image.JPEG,
            quality = ConversionQuality.High,
            tag = "Convert"
        )

        val JpegToWebp = Preset(
            id = "jpeg_to_webp",
            name = "JPEG to WebP",
            description = "Convert JPEG photos to modern ultra-compact WebP",
            category = MediaCategory.IMAGE,
            conversionType = ConversionType.IMAGE_CONVERT,
            targetMimeType = MimeType.Image.WEBP,
            quality = ConversionQuality.High,
            tag = "Convert"
        )

        // Audio Presets
        val Mp3HighQuality320 = Preset(
            id = "mp3_hq_320",
            name = "MP3 High Quality (320 kbps)",
            description = "Extract pristine audio track from video at maximum MP3 quality",
            category = MediaCategory.AUDIO,
            conversionType = ConversionType.EXTRACT_AUDIO,
            targetMimeType = MimeType.Audio.MP3,
            quality = ConversionQuality.Original,
            tag = "Music"
        )

        val AacStandard256 = Preset(
            id = "aac_std_256",
            name = "AAC Voice & Podcast (256 kbps)",
            description = "Extract compact AAC audio stream optimized for voices and podcasts",
            category = MediaCategory.AUDIO,
            conversionType = ConversionType.EXTRACT_AUDIO,
            targetMimeType = MimeType.Audio.AAC,
            quality = ConversionQuality.High,
            tag = "Audio"
        )

        // PDF Presets
        val PdfPrintHighRes = Preset(
            id = "pdf_print_hires",
            name = "Photos to PDF (Print Quality)",
            description = "Combine multiple images into full-resolution A4 document",
            category = MediaCategory.DOCUMENT,
            conversionType = ConversionType.IMAGES_TO_PDF,
            targetMimeType = MimeType.Document.PDF,
            dimensionConstraint = DimensionConstraint.None,
            quality = ConversionQuality.Original,
            tag = "Documents"
        )

        val PdfCompactWeb = Preset(
            id = "pdf_compact_web",
            name = "Photos to PDF (Compact)",
            description = "Combine images into lightweight PDF optimized for email and chat",
            category = MediaCategory.DOCUMENT,
            conversionType = ConversionType.IMAGES_TO_PDF,
            targetMimeType = MimeType.Document.PDF,
            dimensionConstraint = DimensionConstraint.MaxDimension(1600),
            quality = ConversionQuality.Medium,
            tag = "Documents"
        )

        val PdfToImages = Preset(
            id = "pdf_to_images_jpeg",
            name = "PDF to JPEG Photos",
            description = "Extract high-resolution JPEG images from PDF pages",
            category = MediaCategory.DOCUMENT,
            conversionType = ConversionType.PDF_TO_IMAGES,
            targetMimeType = MimeType.Image.JPEG,
            quality = ConversionQuality.High,
            tag = "Documents"
        )

        val PdfCompressEmail2MB = Preset(
            id = "pdf_compress_email_2mb",
            name = "Compress PDF (Email 2MB)",
            description = "Reduce PDF file size under standard 2MB email limit",
            category = MediaCategory.DOCUMENT,
            conversionType = ConversionType.PDF_COMPRESS,
            targetMimeType = MimeType.Document.PDF,
            targetSize = TargetSize.fromMegabytes(2),
            quality = ConversionQuality.Medium,
            tag = "Email"
        )

        val PdfCompressGov500KB = Preset(
            id = "pdf_compress_gov_500kb",
            name = "Compress PDF (Gov Portal 500KB)",
            description = "Compress PDF document under 500KB for official upload portals",
            category = MediaCategory.DOCUMENT,
            conversionType = ConversionType.PDF_COMPRESS,
            targetMimeType = MimeType.Document.PDF,
            targetSize = TargetSize.fromKilobytes(500),
            quality = ConversionQuality.Medium,
            tag = "Government"
        )

        val PdfCompressMax = Preset(
            id = "pdf_compress_max",
            name = "Compress PDF (Smallest Size)",
            description = "Maximum compression for ultra-lightweight PDF sharing",
            category = MediaCategory.DOCUMENT,
            conversionType = ConversionType.PDF_COMPRESS,
            targetMimeType = MimeType.Document.PDF,
            quality = ConversionQuality.Low,
            tag = "Compact"
        )

        val allPresets: List<Preset> = listOf(
            WhatsAppVideo16MB,
            EmailVideo25MB,
            DiscordVideo10MB,
            GovPassport200KB,
            WebStandard500KB,
            PngToJpeg,
            JpegToWebp,
            Mp3HighQuality320,
            AacStandard256,
            PdfPrintHighRes,
            PdfCompactWeb,
            PdfToImages,
            PdfCompressEmail2MB,
            PdfCompressGov500KB,
            PdfCompressMax
        )

        fun findById(id: String): Preset? = allPresets.find { it.id == id }

        fun presetsFor(type: ConversionType): List<Preset> =
            allPresets.filter { it.conversionType == type }

        fun presetsFor(category: MediaCategory): List<Preset> =
            allPresets.filter { it.category == category }
    }
}

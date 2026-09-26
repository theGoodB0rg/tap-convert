package com.tapconvert.core.model

sealed interface MimeType {
    val rawMimeType: String
    val primaryExtension: String
    val category: MediaCategory
    val displayName: String

    val mimeString: String get() = rawMimeType
    val isVideo: Boolean get() = this is Video || this.category == MediaCategory.VIDEO
    val isImage: Boolean get() = this is Image || this.category == MediaCategory.IMAGE
    val isAudio: Boolean get() = this is Audio || this.category == MediaCategory.AUDIO
    val isPdf: Boolean get() = this == Document.PDF || (this.category == MediaCategory.DOCUMENT && this.primaryExtension == "pdf")

    // Image Formats
    sealed class Image(
        override val rawMimeType: String,
        override val primaryExtension: String,
        override val displayName: String
    ) : MimeType {
        override val category: MediaCategory = MediaCategory.IMAGE

        data object JPEG : Image("image/jpeg", "jpg", "JPEG")
        data object PNG : Image("image/png", "png", "PNG")
        data object WEBP : Image("image/webp", "webp", "WebP")
        data object HEIC : Image("image/heic", "heic", "HEIC")
        data object GIF : Image("image/gif", "gif", "GIF")
        data object BMP : Image("image/bmp", "bmp", "BMP")
    }

    // Video Formats
    sealed class Video(
        override val rawMimeType: String,
        override val primaryExtension: String,
        override val displayName: String
    ) : MimeType {
        override val category: MediaCategory = MediaCategory.VIDEO

        data object MP4 : Video("video/mp4", "mp4", "MP4 Video")
        data object MOV : Video("video/quicktime", "mov", "QuickTime Video")
        data object MKV : Video("video/x-matroska", "mkv", "Matroska Video")
        data object WEBM : Video("video/webm", "webm", "WebM Video")
        data object AVI : Video("video/x-msvideo", "avi", "AVI Video")
        data object THREE_GP : Video("video/3gpp", "3gp", "3GP Video")
    }

    // Audio Formats
    sealed class Audio(
        override val rawMimeType: String,
        override val primaryExtension: String,
        override val displayName: String
    ) : MimeType {
        override val category: MediaCategory = MediaCategory.AUDIO

        data object MP3 : Audio("audio/mpeg", "mp3", "MP3 Audio")
        data object AAC : Audio("audio/aac", "aac", "AAC Audio")
        data object M4A : Audio("audio/mp4", "m4a", "M4A Audio")
        data object WAV : Audio("audio/wav", "wav", "WAV Audio")
        data object OGG : Audio("audio/ogg", "ogg", "OGG Audio")
        data object FLAC : Audio("audio/flac", "flac", "FLAC Audio")
    }

    // Document Formats
    sealed class Document(
        override val rawMimeType: String,
        override val primaryExtension: String,
        override val displayName: String
    ) : MimeType {
        override val category: MediaCategory = MediaCategory.DOCUMENT

        data object PDF : Document("application/pdf", "pdf", "PDF Document")
        data object TXT : Document("text/plain", "txt", "Text Document")
    }

    data class Other(
        override val rawMimeType: String,
        override val primaryExtension: String = "bin",
        override val category: MediaCategory = MediaCategory.DOCUMENT,
        override val displayName: String = "Unknown File"
    ) : MimeType

    companion object {
        private val allKnown: List<MimeType> = listOf(
            Image.JPEG, Image.PNG, Image.WEBP, Image.HEIC, Image.GIF, Image.BMP,
            Video.MP4, Video.MOV, Video.MKV, Video.WEBM, Video.AVI, Video.THREE_GP,
            Audio.MP3, Audio.AAC, Audio.M4A, Audio.WAV, Audio.OGG, Audio.FLAC,
            Document.PDF, Document.TXT
        )

        fun fromMime(mime: String?): MimeType {
            if (mime.isNullOrBlank()) return Other("application/octet-stream")
            val normalized = mime.trim().lowercase()
            return when {
                normalized == "image/jpg" || normalized == "image/jpeg" -> Image.JPEG
                normalized == "image/pjpeg" -> Image.JPEG
                normalized == "image/png" -> Image.PNG
                normalized == "image/webp" -> Image.WEBP
                normalized == "image/heic" || normalized == "image/heif" -> Image.HEIC
                normalized == "image/gif" -> Image.GIF
                normalized == "image/bmp" || normalized == "image/x-ms-bmp" -> Image.BMP
                normalized == "video/mp4" -> Video.MP4
                normalized == "video/quicktime" -> Video.MOV
                normalized == "video/x-matroska" -> Video.MKV
                normalized == "video/webm" -> Video.WEBM
                normalized == "video/x-msvideo" || normalized == "video/avi" -> Video.AVI
                normalized == "video/3gpp" || normalized == "video/3gp" -> Video.THREE_GP
                normalized == "audio/mpeg" || normalized == "audio/mp3" -> Audio.MP3
                normalized == "audio/aac" -> Audio.AAC
                normalized == "audio/mp4" || normalized == "audio/x-m4a" || normalized == "audio/m4a" -> Audio.M4A
                normalized == "audio/wav" || normalized == "audio/x-wav" -> Audio.WAV
                normalized == "audio/ogg" -> Audio.OGG
                normalized == "audio/flac" || normalized == "audio/x-flac" -> Audio.FLAC
                normalized == "application/pdf" -> Document.PDF
                normalized == "text/plain" -> Document.TXT
                else -> {
                    val category = when {
                        normalized.startsWith("image/") -> MediaCategory.IMAGE
                        normalized.startsWith("video/") -> MediaCategory.VIDEO
                        normalized.startsWith("audio/") -> MediaCategory.AUDIO
                        else -> MediaCategory.DOCUMENT
                    }
                    Other(rawMimeType = normalized, category = category)
                }
            }
        }

        fun fromExtension(ext: String?): MimeType {
            if (ext.isNullOrBlank()) return Other("application/octet-stream")
            val normalized = ext.trim().lowercase().removePrefix(".")
            return when (normalized) {
                "jpg", "jpeg", "pjpeg" -> Image.JPEG
                "png" -> Image.PNG
                "webp" -> Image.WEBP
                "heic", "heif" -> Image.HEIC
                "gif" -> Image.GIF
                "bmp" -> Image.BMP
                "mp4", "m4v" -> Video.MP4
                "mov", "qt" -> Video.MOV
                "mkv" -> Video.MKV
                "webm" -> Video.WEBM
                "avi" -> Video.AVI
                "3gp", "3gpp" -> Video.THREE_GP
                "mp3" -> Audio.MP3
                "aac" -> Audio.AAC
                "m4a" -> Audio.M4A
                "wav" -> Audio.WAV
                "ogg", "oga" -> Audio.OGG
                "flac" -> Audio.FLAC
                "pdf" -> Document.PDF
                "txt" -> Document.TXT
                else -> Other("application/octet-stream", primaryExtension = normalized)
            }
        }

        fun fromFileName(fileName: String?): MimeType {
            if (fileName.isNullOrBlank()) return Other("application/octet-stream")
            val extension = fileName.substringAfterLast('.', "")
            return fromExtension(extension)
        }
    }
}



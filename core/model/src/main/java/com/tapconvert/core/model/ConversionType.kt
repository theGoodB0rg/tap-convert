package com.tapconvert.core.model

enum class ConversionType(val displayName: String, val description: String) {
    IMAGE_COMPRESS("Compress Image", "Shrink photo size to target KB limits"),
    IMAGE_CONVERT("Convert Format", "Convert between JPG, PNG, WEBP, and HEIC"),
    IMAGES_TO_PDF("Photos to PDF", "Combine multiple images into one PDF document"),
    PDF_TO_IMAGES("PDF to Photos", "Extract PDF pages into high quality images"),
    VIDEO_COMPRESS("Compress Video", "Reduce video size for WhatsApp and Email"),
    EXTRACT_AUDIO("Extract Audio", "Save audio track from video as MP3")
}

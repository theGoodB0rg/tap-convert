package com.tapconvert.core.model

enum class MediaCategory(val displayName: String) {
    IMAGE("Image"),
    VIDEO("Video"),
    AUDIO("Audio"),
    DOCUMENT("Document");

    val isVisual: Boolean
        get() = this == IMAGE || this == VIDEO
}

package com.tapconvert.feature.image.engine

import androidx.exifinterface.media.ExifInterface
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExifTransformerTest {

    @Test
    fun `calculates valid orientation transform parameters for standard EXIF flags`() {
        val normal = ExifTransformer.getOrientationTransform(ExifInterface.ORIENTATION_NORMAL)
        assertThat(normal.rotationDegrees).isEqualTo(0f)
        assertThat(normal.flipHorizontal).isFalse()
        assertThat(normal.flipVertical).isFalse()

        val rot90 = ExifTransformer.getOrientationTransform(ExifInterface.ORIENTATION_ROTATE_90)
        assertThat(rot90.rotationDegrees).isEqualTo(90f)
        assertThat(rot90.flipHorizontal).isFalse()

        val rot180 = ExifTransformer.getOrientationTransform(ExifInterface.ORIENTATION_ROTATE_180)
        assertThat(rot180.rotationDegrees).isEqualTo(180f)

        val rot270 = ExifTransformer.getOrientationTransform(ExifInterface.ORIENTATION_ROTATE_270)
        assertThat(rot270.rotationDegrees).isEqualTo(270f)

        val flipH = ExifTransformer.getOrientationTransform(ExifInterface.ORIENTATION_FLIP_HORIZONTAL)
        assertThat(flipH.flipHorizontal).isTrue()

        val flipV = ExifTransformer.getOrientationTransform(ExifInterface.ORIENTATION_FLIP_VERTICAL)
        assertThat(flipV.flipVertical).isTrue()

        val transpose = ExifTransformer.getOrientationTransform(ExifInterface.ORIENTATION_TRANSPOSE)
        assertThat(transpose.rotationDegrees).isEqualTo(90f)
        assertThat(transpose.flipHorizontal).isTrue()
    }
}


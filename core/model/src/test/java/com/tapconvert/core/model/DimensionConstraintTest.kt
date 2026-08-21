package com.tapconvert.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class DimensionConstraintTest {

    @Test
    fun `None constraint leaves original dimensions unchanged`() {
        val constraint = DimensionConstraint.None
        val result = constraint.calculateDimensions(4000, 3000)
        assertThat(result).isEqualTo(4000 to 3000)
    }

    @Test
    fun `MaxDimension constraint scales down aspect ratio preserving max boundary`() {
        val constraint = DimensionConstraint.MaxDimension(1920)

        // Landscape
        val landscape = constraint.calculateDimensions(3840, 2160) // 16:9
        assertThat(landscape).isEqualTo(1920 to 1080)

        // Portrait
        val portrait = constraint.calculateDimensions(2160, 3840)
        assertThat(portrait).isEqualTo(1080 to 1920)

        // Smaller than max remains untouched
        val small = constraint.calculateDimensions(800, 600)
        assertThat(small).isEqualTo(800 to 600)
    }

    @Test
    fun `Exact constraint with FIT_INSIDE preserves aspect ratio`() {
        val constraint = DimensionConstraint.Exact(1000, 1000, DimensionConstraint.FitMode.FIT_INSIDE)

        val landscape = constraint.calculateDimensions(2000, 1000) // 2:1
        assertThat(landscape).isEqualTo(1000 to 500)

        val portrait = constraint.calculateDimensions(1000, 2000) // 1:2
        assertThat(portrait).isEqualTo(500 to 1000)
    }

    @Test
    fun `Exact constraint with STRETCH_EXACT forces exact dimensions`() {
        val constraint = DimensionConstraint.Exact(500, 500, DimensionConstraint.FitMode.STRETCH_EXACT)
        val result = constraint.calculateDimensions(1920, 1080)
        assertThat(result).isEqualTo(500 to 500)
    }

    @Test
    fun `ScalePercentage scales dimensions by ratio`() {
        val half = DimensionConstraint.ScalePercentage(0.5f)
        assertThat(half.calculateDimensions(1000, 800)).isEqualTo(500 to 400)

        val double = DimensionConstraint.ScalePercentage(2.0f)
        assertThat(double.calculateDimensions(500, 400)).isEqualTo(1000 to 800)
    }

    @Test
    fun `invalid arguments throw IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            DimensionConstraint.MaxDimension(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DimensionConstraint.Exact(0, 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DimensionConstraint.ScalePercentage(0.0f)
        }
    }
}

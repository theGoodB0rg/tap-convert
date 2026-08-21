package com.tapconvert.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConversionTypeTest {

    @Test
    fun `all conversion types have non-empty display name and description`() {
        ConversionType.entries.forEach { type ->
            assertThat(type.displayName).isNotEmpty()
            assertThat(type.description).isNotEmpty()
        }
    }
}

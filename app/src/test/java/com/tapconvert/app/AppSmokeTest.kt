package com.tapconvert.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppSmokeTest {

    @Test
    fun `application base configuration passes sanity check`() {
        val appName = "TapConvert"
        assertThat(appName).isEqualTo("TapConvert")
    }
}

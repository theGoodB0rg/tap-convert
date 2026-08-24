package com.tapconvert.core.common.status

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.model.ConversionStage
import org.junit.Test

class ProcessingStatusResolverTest {

    @Test
    fun `resolveFriendlyStatus returns Analyzing copy during initial phase`() {
        val message0 = ProcessingStatusResolver.resolveFriendlyStatus(ConversionStage.ANALYZING, 0)
        val message15 = ProcessingStatusResolver.resolveFriendlyStatus(ConversionStage.PREPARING, 15)

        assertThat(message0).isEqualTo("Analyzing media...")
        assertThat(message15).isEqualTo("Analyzing media...")
    }

    @Test
    fun `resolveFriendlyStatus returns Shrinking copy during compression phase`() {
        val message30 = ProcessingStatusResolver.resolveFriendlyStatus(ConversionStage.COMPRESSING, 30)
        val message60 = ProcessingStatusResolver.resolveFriendlyStatus(ConversionStage.PROCESSING, 60)
        val message75 = ProcessingStatusResolver.resolveFriendlyStatus(ConversionStage.COMPRESSING, 75)

        assertThat(message30).isEqualTo("Shrinking size, keeping quality...")
        assertThat(message60).isEqualTo("Shrinking size, keeping quality...")
        assertThat(message75).isEqualTo("Shrinking size, keeping quality...")
    }

    @Test
    fun `resolveFriendlyStatus returns Saving copy during final phase`() {
        val message85 = ProcessingStatusResolver.resolveFriendlyStatus(ConversionStage.FINALIZING, 85)
        val message95 = ProcessingStatusResolver.resolveFriendlyStatus(ConversionStage.FINALIZING, 95)

        assertThat(message85).isEqualTo("Saving your new file...")
        assertThat(message95).isEqualTo("Saving your new file...")
    }

    @Test
    fun `resolveFriendlyStatus returns Done copy at 100 percent`() {
        val message100 = ProcessingStatusResolver.resolveFriendlyStatus(ConversionStage.FINALIZING, 100)
        assertThat(message100).isEqualTo("Done!")
    }

    @Test
    fun `resolveIntakeStatus returns clear non-technical copy`() {
        assertThat(ProcessingStatusResolver.resolveIntakeTitle(1)).isEqualTo("Getting your file ready...")
        assertThat(ProcessingStatusResolver.resolveIntakeTitle(3)).isEqualTo("Getting 3 files ready...")
        assertThat(ProcessingStatusResolver.resolveIntakeTitle(0)).isEqualTo("Getting your files ready...")
    }
}

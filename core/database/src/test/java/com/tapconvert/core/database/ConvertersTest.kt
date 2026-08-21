package com.tapconvert.core.database

import com.google.common.truth.Truth.assertThat
import com.tapconvert.core.database.converter.Converters
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `string list serialization round trip`() {
        val list = listOf("file:///a.jpg", "file:///b.png", "file:///c.pdf")
        val serialized = converters.fromStringList(list)
        val deserialized = converters.toStringList(serialized)

        assertThat(deserialized).containsExactlyElementsIn(list).inOrder()
    }

    @Test
    fun `empty and null string list serialization`() {
        assertThat(converters.toStringList(null)).isEmpty()
        assertThat(converters.toStringList("")).isEmpty()
        assertThat(converters.fromStringList(emptyList())).isEmpty()
    }

    @Test
    fun `string map serialization round trip`() {
        val map = mapOf("format" to "JPEG", "quality" to "85", "duration" to "1200")
        val serialized = converters.fromStringMap(map)
        val deserialized = converters.toStringMap(serialized)

        assertThat(deserialized).containsExactlyEntriesIn(map)
    }
}

package com.clarohome.core.data

import com.clarohome.core.model.VisualMode
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoSettingsSerializerTest {
    @Test
    fun settingsRoundTripPreservesVisualMode() = runTest {
        val output = ByteArrayOutputStream()

        ProtoSettingsSerializer.writeTo(ClaroSettings(VisualMode.CLASSIC), output)

        val restored = ProtoSettingsSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertThat(restored.visualMode).isEqualTo(VisualMode.CLASSIC)
    }
}

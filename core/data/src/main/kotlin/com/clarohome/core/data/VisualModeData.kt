package com.clarohome.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.clarohome.core.domain.VisualModeRepository
import com.clarohome.core.model.VisualMode
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf

@Serializable
data class ClaroSettings(
    val visualMode: VisualMode = VisualMode.TOUCH,
)

@OptIn(ExperimentalSerializationApi::class)
object ProtoSettingsSerializer : Serializer<ClaroSettings> {
    override val defaultValue = ClaroSettings()

    override suspend fun readFrom(input: InputStream): ClaroSettings =
        ProtoBuf.decodeFromByteArray(ClaroSettings.serializer(), input.readBytes())

    override suspend fun writeTo(t: ClaroSettings, output: OutputStream) {
        output.write(ProtoBuf.encodeToByteArray(ClaroSettings.serializer(), t))
    }
}

private val Context.claroSettings: DataStore<ClaroSettings> by dataStore(
    fileName = "claro_settings.pb",
    serializer = ProtoSettingsSerializer,
)

@Singleton
class ProtoVisualModeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : VisualModeRepository {
    override val visualMode: Flow<VisualMode> = context.claroSettings.data
        .catch { emit(ClaroSettings()) }
        .map { it.visualMode }

    override suspend fun setVisualMode(mode: VisualMode) {
        context.claroSettings.updateData { it.copy(visualMode = mode) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VisualModeDataModule {
    @Binds
    abstract fun bindVisualModeRepository(
        repository: ProtoVisualModeRepository,
    ): VisualModeRepository
}

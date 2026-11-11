package com.clarohome.core.data

import com.clarohome.core.domain.HomeRepository
import com.clarohome.core.model.FavoritePerson
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.TileAccent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Singleton
class FakeHomeRepository @Inject constructor() : HomeRepository {
    override val home: Flow<HomeSnapshot> = flowOf(
        HomeSnapshot(
            greeting = "Good evening",
            favorites = listOf(
                FavoritePerson("ana", "Ana"),
                FavoritePerson("david", "David"),
                FavoritePerson("maya", "Maya"),
                FavoritePerson("sam", "Sam"),
            ),
            tiles = listOf(
                HomeTile("phone", "Phone", TileAccent.GREEN),
                HomeTile("messages", "Messages", TileAccent.BLUE),
                HomeTile("camera", "Camera", TileAccent.ORANGE),
                HomeTile("gallery", "Gallery", TileAccent.BLUE),
                HomeTile("sos", "SOS", TileAccent.RED),
                HomeTile("apps", "Apps", TileAccent.VIOLET),
            ),
        ),
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeDataModule {
    @Binds
    abstract fun bindHomeRepository(repository: FakeHomeRepository): HomeRepository
}

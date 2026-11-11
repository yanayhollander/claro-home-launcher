package com.clarohome.core.data

import com.clarohome.core.domain.HomeRepository
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CellularAvailability
import com.clarohome.core.model.CellularStatus
import com.clarohome.core.model.FavoritePerson
import com.clarohome.core.model.FavoriteState
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.InstalledApp
import com.clarohome.core.model.TileAccent
import android.content.Context
import androidx.core.content.edit
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface FavoriteStore {
    fun load(): List<FavoritePerson?>
    fun save(favorites: List<FavoritePerson>)
}

class InMemoryFavoriteStore : FavoriteStore {
    private var saved: List<FavoritePerson?> = emptyList()
    override fun load() = saved
    override fun save(favorites: List<FavoritePerson>) {
        saved = favorites
    }
}

@Singleton
class SharedPreferencesFavoriteStore @Inject constructor(
    @ApplicationContext context: Context,
) : FavoriteStore {
    private val prefs = context.getSharedPreferences("claro_favorites", Context.MODE_PRIVATE)

    override fun load(): List<FavoritePerson?> = List(FAVORITE_SLOTS) { slot ->
        val id = prefs.getString("$slot.id", null) ?: return@List null
        val state = runCatching {
            FavoriteState.valueOf(prefs.getString("$slot.state", null).orEmpty())
        }.getOrDefault(FavoriteState.READY)
        if (state == FavoriteState.UNASSIGNED) return@List null
        FavoritePerson(
            id = id,
            displayName = prefs.getString("$slot.name", null).orEmpty(),
            phoneNumber = prefs.getString("$slot.number", null),
            photoUri = prefs.getString("$slot.photo", null),
            state = state,
        )
    }

    override fun save(favorites: List<FavoritePerson>) {
        prefs.edit {
            clear()
            favorites.take(FAVORITE_SLOTS).forEachIndexed { slot, person ->
                if (person.state == FavoriteState.UNASSIGNED) return@forEachIndexed
                putString("$slot.id", person.id)
                putString("$slot.name", person.displayName)
                putString("$slot.number", person.phoneNumber)
                putString("$slot.photo", person.photoUri)
                putString("$slot.state", person.state.name)
            }
        }
    }
}

private const val FAVORITE_SLOTS = 4

@Singleton
class LocalHomeRepository @Inject constructor(
    private val favoriteStore: FavoriteStore,
) : HomeRepository {
    private val state = MutableStateFlow(
        HomeSnapshot(
            greeting = "",
            dayAndDate = "",
            time = "",
            battery = BatteryStatus(0, isCharging = false),
            cellular = CellularStatus(CellularAvailability.UNKNOWN),
            favorites = List(4) { index ->
                favoriteStore.load().getOrNull(index) ?: FavoritePerson(
                    id = "favorite-$index",
                    displayName = "Add person",
                    state = FavoriteState.UNASSIGNED,
                )
            },
            tiles = listOf(
                HomeTile(
                    "phone",
                    "Phone",
                    TileAccent.GREEN,
                    HomeTileKind.PHONE,
                    BadgeValue.Unknown,
                ),
                HomeTile(
                    "messages",
                    "Messages",
                    TileAccent.BLUE,
                    HomeTileKind.MESSAGES,
                    BadgeValue.Unknown,
                ),
                HomeTile("camera", "Camera", TileAccent.ORANGE, HomeTileKind.CAMERA),
                HomeTile("gallery", "Gallery", TileAccent.VIOLET, HomeTileKind.GALLERY),
                HomeTile("sos", "SOS", TileAccent.RED, HomeTileKind.SOS),
                HomeTile("apps", "Apps", TileAccent.BLUE, HomeTileKind.APPS),
            ),
            installedApps = emptyList(),
            homeRole = HomeRoleState.UNKNOWN,
        ),
    )

    override val home: Flow<HomeSnapshot> = state.asStateFlow()

    override suspend fun setFavorite(slot: Int, person: FavoritePerson) {
        if (slot !in 0..3) return
        state.value = state.value.copy(
            favorites = state.value.favorites.mapIndexed { index, current ->
                if (index == slot) person else current
            },
        )
        favoriteStore.save(state.value.favorites)
    }

    override suspend fun clearFavorite(slot: Int) {
        if (slot !in 0..3) return
        state.value = state.value.copy(
            favorites = state.value.favorites.mapIndexed { index, current ->
                if (index == slot) {
                    FavoritePerson(
                        id = "favorite-$slot",
                        displayName = "Add person",
                        state = FavoriteState.UNASSIGNED,
                    )
                } else {
                    current
                }
            },
        )
        favoriteStore.save(state.value.favorites)
    }

    override suspend fun reset() {
        val current = state.value
        val clearedApps = current.installedApps.map {
            it.copy(isAllowed = false, isFavorite = false, isOnHome = false)
        }
        state.value = current.copy(
            favorites = List(4) { index ->
                FavoritePerson(
                    id = "favorite-$index",
                    displayName = "Add person",
                    state = FavoriteState.UNASSIGNED,
                )
            },
        ).withApps(clearedApps)
        favoriteStore.save(state.value.favorites)
    }

    override suspend fun setInstalledApps(apps: List<InstalledApp>) {
        val current = state.value
        val discovered = apps.associateBy { it.packageName }
        val savedPackages = current.installedApps.map { it.packageName }.toSet()
        val merged = current.installedApps.map { saved ->
            discovered[saved.packageName]?.copy(
                isAllowed = saved.isAllowed,
                isFavorite = saved.isFavorite,
                isOnHome = saved.isOnHome,
            ) ?: saved.copy(isAvailable = false)
        } + apps.filterNot { it.packageName in savedPackages }
        state.value = current.withApps(merged)
    }

    override suspend fun setAppAllowed(packageName: String, allowed: Boolean) {
        val current = state.value
        state.value = current.withApps(
            current.installedApps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(isAllowed = allowed, isOnHome = app.isOnHome && allowed)
                } else {
                    app
                }
            },
        )
    }

    override suspend fun setAppOnHome(packageName: String, onHome: Boolean) {
        val current = state.value
        state.value = current.withApps(
            current.installedApps.map { app ->
                if (app.packageName == packageName && app.isAllowed && app.isAvailable) {
                    app.copy(isOnHome = onHome)
                } else {
                    app
                }
            },
        )
    }

    override suspend fun setHomeRoleState(state: HomeRoleState) {
        val current = this.state.value
        val badge = if (state == HomeRoleState.GRANTED) {
            BadgeValue.Known(1)
        } else {
            BadgeValue.Unknown
        }
        this.state.value = current.copy(
            homeRole = state,
            tiles = current.tiles.map { tile ->
                if (tile.kind == HomeTileKind.PHONE || tile.kind == HomeTileKind.MESSAGES) {
                    tile.copy(badge = badge)
                } else {
                    tile
                }
            },
        )
    }

    override suspend fun setStatus(
        dayAndDate: String,
        time: String,
        battery: BatteryStatus,
        cellular: CellularStatus,
    ) {
        state.value = state.value.copy(
            dayAndDate = dayAndDate,
            time = time,
            battery = battery,
            cellular = cellular,
        )
    }

    private fun HomeSnapshot.withApps(apps: List<InstalledApp>): HomeSnapshot {
        val coreTiles = tiles.filter { it.isCore }
        val appTiles = apps
            .filter { it.isAllowed && it.isOnHome }
            .map { app ->
                HomeTile(
                    id = "app:${app.packageName}",
                    label = app.label,
                    accent = TileAccent.VIOLET,
                    kind = HomeTileKind.INSTALLED_APP,
                    packageName = app.packageName,
                    isCore = false,
                )
            }
        return copy(installedApps = apps, tiles = coreTiles + appTiles)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeDataModule {
    @Binds
    abstract fun bindHomeRepository(repository: LocalHomeRepository): HomeRepository

    @Binds
    abstract fun bindFavoriteStore(store: SharedPreferencesFavoriteStore): FavoriteStore
}

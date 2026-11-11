package com.clarohome.core.data

import com.clarohome.core.domain.HomeRepository
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CellularAvailability
import com.clarohome.core.model.CellularStatus
import com.clarohome.core.model.FavoritePerson
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.InstalledApp
import com.clarohome.core.model.TileAccent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class FakeHomeRepository @Inject constructor() : HomeRepository {
    private val state = MutableStateFlow(
        HomeSnapshot(
            greeting = "Good evening",
            dayAndDate = "Wed 11 Nov",
            time = "9:41",
            battery = BatteryStatus(82, isCharging = false),
            cellular = CellularStatus(CellularAvailability.AVAILABLE, signalLevel = 3),
            favorites = listOf(
                FavoritePerson("mira", "Mira", "+1 555 0101"),
                FavoritePerson("daniel", "Daniel", "+1 555 0102"),
                FavoritePerson("leah", "Leah", "+1 555 0103"),
                FavoritePerson("noah", "Noah", "+1 555 0104"),
            ),
            tiles = listOf(
                HomeTile(
                    "phone",
                    "Phone",
                    TileAccent.GREEN,
                    HomeTileKind.PHONE,
                    BadgeValue.Known(1),
                ),
                HomeTile(
                    "messages",
                    "Messages",
                    TileAccent.BLUE,
                    HomeTileKind.MESSAGES,
                    BadgeValue.Known(1),
                ),
                HomeTile("camera", "Camera", TileAccent.ORANGE, HomeTileKind.CAMERA),
                HomeTile("gallery", "Gallery", TileAccent.BLUE, HomeTileKind.GALLERY),
                HomeTile("sos", "SOS", TileAccent.RED, HomeTileKind.SOS),
                HomeTile("apps", "Apps", TileAccent.VIOLET, HomeTileKind.APPS),
            ),
            installedApps = listOf(
                InstalledApp("com.android.calculator2", "Calculator", isAllowed = true, isFavorite = true),
                InstalledApp("com.android.chrome", "Browser", isAllowed = true),
                InstalledApp("com.google.android.apps.maps", "Maps", isAllowed = true),
                InstalledApp("com.example.weather", "Weather", isAllowed = true, isAvailable = false),
            ),
            homeRole = HomeRoleState.AVAILABLE,
        ),
    )

    override val home: Flow<HomeSnapshot> = state.asStateFlow()

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
    abstract fun bindHomeRepository(repository: FakeHomeRepository): HomeRepository
}

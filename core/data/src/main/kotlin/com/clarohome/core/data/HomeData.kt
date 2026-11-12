package com.clarohome.core.data

import com.clarohome.core.domain.HomeRepository
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CellularAvailability
import com.clarohome.core.model.CellularStatus
import com.clarohome.core.model.HOME_SLOT_COUNT
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

interface HomeSlotStore {
    fun load(): List<HomeTile?>
    fun save(slots: List<HomeTile?>)
    fun loadMenuOrder(): List<String> = emptyList()
    fun saveMenuOrder(order: List<String>) = Unit
}

class InMemoryHomeSlotStore(
    private var saved: List<HomeTile?> = defaultHomeSlots(),
) : HomeSlotStore {
    override fun load() = saved
    override fun save(slots: List<HomeTile?>) {
        saved = slots
    }

    private var savedOrder: List<String> = emptyList()
    override fun loadMenuOrder() = savedOrder
    override fun saveMenuOrder(order: List<String>) {
        savedOrder = order
    }
}

fun homeSlotTile(
    slot: Int,
    kind: HomeTileKind,
    label: String,
    packageName: String? = null,
    phoneNumber: String? = null,
    photoUri: String? = null,
): HomeTile = HomeTile(
    id = "slot-$slot",
    label = label,
    accent = when (kind) {
        HomeTileKind.PHONE -> TileAccent.GREEN
        HomeTileKind.CAMERA, HomeTileKind.ALARM -> TileAccent.ORANGE
        HomeTileKind.GALLERY, HomeTileKind.INSTALLED_APP -> TileAccent.VIOLET
        HomeTileKind.SOS -> TileAccent.RED
        HomeTileKind.MESSAGES, HomeTileKind.APPS, HomeTileKind.CONTACT -> TileAccent.BLUE
    },
    kind = kind,
    badge = if (kind == HomeTileKind.PHONE || kind == HomeTileKind.MESSAGES) {
        BadgeValue.Unknown
    } else {
        null
    },
    packageName = packageName,
    isCore = false,
    phoneNumber = phoneNumber,
    photoUri = photoUri,
)

/** Fresh installs start with the essentials; everything else is optional. */
fun defaultHomeSlots(extra: List<HomeTile> = emptyList()): List<HomeTile?> {
    val essentials = listOf(
        HomeTileKind.PHONE to "Phone",
        HomeTileKind.CAMERA to "Camera",
        HomeTileKind.GALLERY to "Gallery",
        HomeTileKind.SOS to "SOS",
        HomeTileKind.APPS to "Apps",
    ).map { (kind, label) -> homeSlotTile(0, kind, label) }
    val seeded = (essentials + extra).take(HOME_SLOT_COUNT)
    return List(HOME_SLOT_COUNT) { index ->
        seeded.getOrNull(index)?.let { it.copy(id = "slot-$index") }
    }
}

@Singleton
class SharedPreferencesHomeSlotStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : HomeSlotStore {
    private val prefs = context.getSharedPreferences("claro_home_slots", Context.MODE_PRIVATE)
    private val orderPrefs = context.getSharedPreferences("claro_menu_order", Context.MODE_PRIVATE)

    override fun loadMenuOrder(): List<String> =
        orderPrefs.getString(MENU_ORDER, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    override fun saveMenuOrder(order: List<String>) {
        orderPrefs.edit {
            if (order.isEmpty()) remove(MENU_ORDER) else putString(MENU_ORDER, order.joinToString("\n"))
        }
    }

    override fun load(): List<HomeTile?> {
        if (!prefs.getBoolean(INITIALIZED, false)) {
            val migrated = defaultHomeSlots(legacyFavorites())
            save(migrated)
            return migrated
        }
        return List(HOME_SLOT_COUNT) { slot ->
            val kind = runCatching {
                HomeTileKind.valueOf(prefs.getString("$slot.kind", null).orEmpty())
            }.getOrNull() ?: return@List null
            homeSlotTile(
                slot = slot,
                kind = kind,
                label = prefs.getString("$slot.label", null).orEmpty(),
                packageName = prefs.getString("$slot.package", null),
                phoneNumber = prefs.getString("$slot.number", null),
                photoUri = prefs.getString("$slot.photo", null),
            )
        }
    }

    override fun save(slots: List<HomeTile?>) {
        prefs.edit {
            clear()
            putBoolean(INITIALIZED, true)
            slots.take(HOME_SLOT_COUNT).forEachIndexed { slot, tile ->
                if (tile == null) return@forEachIndexed
                putString("$slot.kind", tile.kind.name)
                putString("$slot.label", tile.label)
                putString("$slot.package", tile.packageName)
                putString("$slot.number", tile.phoneNumber)
                putString("$slot.photo", tile.photoUri)
            }
        }
    }

    // Earlier builds kept four direct-call favorites; keep them as Home tiles.
    private fun legacyFavorites(): List<HomeTile> {
        val legacy = context.getSharedPreferences("claro_favorites", Context.MODE_PRIVATE)
        return (0 until 4).mapNotNull { slot ->
            val number = legacy.getString("$slot.number", null)?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            homeSlotTile(
                slot = slot,
                kind = HomeTileKind.CONTACT,
                label = legacy.getString("$slot.name", null).orEmpty(),
                phoneNumber = number,
                photoUri = legacy.getString("$slot.photo", null),
            )
        }
    }

    private companion object {
        const val INITIALIZED = "initialized"
        const val MENU_ORDER = "order"
    }
}

@Singleton
class LocalHomeRepository @Inject constructor(
    private val slotStore: HomeSlotStore,
) : HomeRepository {
    private val state = MutableStateFlow(
        HomeSnapshot(
            greeting = "",
            dayAndDate = "",
            time = "",
            battery = BatteryStatus(0, isCharging = false),
            cellular = CellularStatus(CellularAvailability.UNKNOWN),
            homeSlots = slotStore.load(),
            menuOrder = slotStore.loadMenuOrder(),
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

    override suspend fun setHomeSlot(slot: Int, tile: HomeTile) {
        if (slot !in 0 until HOME_SLOT_COUNT) return
        val updated = state.value.homeSlots.mapIndexed { index, current ->
            when {
                index == slot -> homeSlotTile(
                    slot = slot,
                    kind = tile.kind,
                    label = tile.label,
                    packageName = tile.packageName,
                    phoneNumber = tile.phoneNumber,
                    photoUri = tile.photoUri,
                ).copy(badge = state.value.badgeFor(tile.kind))
                // Built-in shortcuts exist once; placing one elsewhere moves it.
                tile.kind.isUniqueShortcut && current?.kind == tile.kind -> null
                else -> current
            }
        }
        state.value = state.value.copy(homeSlots = updated)
        slotStore.save(updated)
    }

    override suspend fun clearHomeSlot(slot: Int) {
        if (slot !in 0 until HOME_SLOT_COUNT) return
        val updated = state.value.homeSlots.mapIndexed { index, current ->
            if (index == slot) null else current
        }
        state.value = state.value.copy(homeSlots = updated)
        slotStore.save(updated)
    }

    override suspend fun reset() {
        val current = state.value
        val clearedApps = current.installedApps.map {
            it.copy(isAllowed = false, isFavorite = false, isOnHome = false)
        }
        val defaults = defaultHomeSlots()
        state.value = current.copy(homeSlots = defaults, menuOrder = emptyList()).withApps(clearedApps)
        slotStore.save(defaults)
        slotStore.saveMenuOrder(emptyList())
    }

    override suspend fun setMenuOrder(order: List<String>) {
        val cleaned = order.distinct()
        state.value = state.value.copy(menuOrder = cleaned)
        slotStore.saveMenuOrder(cleaned)
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
            tiles = current.tiles.map { tile -> tile.withBadge(badge) },
            homeSlots = current.homeSlots.map { tile -> tile?.withBadge(badge) },
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

    private fun HomeTile.withBadge(badge: BadgeValue): HomeTile =
        if (kind == HomeTileKind.PHONE || kind == HomeTileKind.MESSAGES) copy(badge = badge) else this

    private fun HomeSnapshot.badgeFor(kind: HomeTileKind): BadgeValue? =
        tiles.firstOrNull { it.kind == kind }?.badge

    private val HomeTileKind.isUniqueShortcut: Boolean
        get() = this != HomeTileKind.CONTACT && this != HomeTileKind.INSTALLED_APP

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
    abstract fun bindHomeSlotStore(store: SharedPreferencesHomeSlotStore): HomeSlotStore
}

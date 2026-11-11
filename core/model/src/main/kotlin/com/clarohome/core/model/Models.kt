package com.clarohome.core.model

enum class VisualMode {
    TOUCH,
    CLASSIC,
}

data class FavoritePerson(
    val id: String,
    val displayName: String,
)

data class HomeTile(
    val id: String,
    val label: String,
    val accent: TileAccent,
)

enum class TileAccent {
    GREEN,
    BLUE,
    ORANGE,
    RED,
    VIOLET,
}

data class HomeSnapshot(
    val greeting: String,
    val favorites: List<FavoritePerson>,
    val tiles: List<HomeTile>,
)

enum class CapabilityStatus {
    READY,
    NEEDS_ATTENTION,
    UNAVAILABLE,
}

data class PlatformReadiness(
    val home: CapabilityStatus,
    val phone: CapabilityStatus,
    val messages: CapabilityStatus,
)

enum class EntitlementState {
    FREE,
    PREMIUM,
    UNKNOWN,
}

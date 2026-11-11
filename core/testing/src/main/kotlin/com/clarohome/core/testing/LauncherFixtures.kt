package com.clarohome.core.testing

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

fun launcherHomeFixture(
    roleState: HomeRoleState = HomeRoleState.GRANTED,
    phoneBadge: BadgeValue = BadgeValue.Known(2),
    messageBadge: BadgeValue = BadgeValue.Known(3),
): HomeSnapshot = HomeSnapshot(
    greeting = "Good morning",
    dayAndDate = "Wed 11 Nov",
    time = "9:41",
    battery = BatteryStatus(82, false),
    cellular = CellularStatus(CellularAvailability.AVAILABLE, 3),
    favorites = listOf(
        FavoritePerson("1", "Mira", "+1"),
        FavoritePerson("2", "Daniel", "+2"),
        FavoritePerson("3", "Leah", "+3"),
        FavoritePerson("4", "Noah", "+4"),
    ),
    tiles = listOf(
        HomeTile("phone", "Phone", TileAccent.GREEN, HomeTileKind.PHONE, phoneBadge),
        HomeTile("messages", "Messages", TileAccent.BLUE, HomeTileKind.MESSAGES, messageBadge),
        HomeTile("camera", "Camera", TileAccent.ORANGE, HomeTileKind.CAMERA),
        HomeTile("gallery", "Gallery", TileAccent.BLUE, HomeTileKind.GALLERY),
        HomeTile("sos", "SOS", TileAccent.RED, HomeTileKind.SOS),
        HomeTile("apps", "Apps", TileAccent.VIOLET, HomeTileKind.APPS),
    ),
    installedApps = listOf(
        InstalledApp("calculator", "Calculator", true, isFavorite = true),
        InstalledApp("browser", "Browser", true),
    ),
    homeRole = roleState,
)

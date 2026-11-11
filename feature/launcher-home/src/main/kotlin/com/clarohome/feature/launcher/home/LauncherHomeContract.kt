package com.clarohome.feature.launcher.home

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.BatteryManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import com.clarohome.core.domain.FeatureContract
import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CellularAvailability
import com.clarohome.core.model.CellularStatus
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.InstalledApp
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LauncherHomeContract : FeatureContract {
    override val id = "launcher-home"
    override val protectedRoutes = emptySet<com.clarohome.core.domain.ProtectedRoute>()
}

interface HomeRoleController {
    fun currentState(): HomeRoleState
    fun requestIntent(): Intent?
    fun repairIntent(): Intent
}

@Singleton
class AndroidHomeRoleController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HomeRoleController {
    private val roleManager = context.getSystemService(RoleManager::class.java)

    override fun currentState(): HomeRoleState = when {
        roleManager == null -> HomeRoleState.UNKNOWN
        !roleManager.isRoleAvailable(RoleManager.ROLE_HOME) -> HomeRoleState.UNAVAILABLE
        roleManager.isRoleHeld(RoleManager.ROLE_HOME) -> HomeRoleState.GRANTED
        else -> HomeRoleState.AVAILABLE
    }

    override fun requestIntent(): Intent? =
        if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            null
        }

    override fun repairIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)
}

interface InstalledAppCatalog {
    fun discoverEligibleApps(): List<InstalledApp>
    fun launchIntent(packageName: String): Intent?
}

data class LauncherStatusSnapshot(
    val dayAndDate: String,
    val time: String,
    val battery: BatteryStatus,
    val cellular: CellularStatus,
)

interface LauncherStatusReader {
    fun read(): LauncherStatusSnapshot
}

@Singleton
class AndroidLauncherStatusReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LauncherStatusReader {
    override fun read(): LauncherStatusSnapshot {
        val now = Date()
        val locale = Locale.getDefault()
        val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
        val timePattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
        return LauncherStatusSnapshot(
            dayAndDate = SimpleDateFormat(datePattern, locale).format(now),
            time = SimpleDateFormat(timePattern, locale).format(now),
            battery = readBattery(),
            cellular = readCellular(),
        )
    }

    private fun readBattery(): BatteryStatus {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return BatteryStatus(
            percentage = percentage,
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }

    private fun readCellular(): CellularStatus {
        val airplaneMode = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) == 1
        if (airplaneMode) {
            return CellularStatus(CellularAvailability.AIRPLANE_MODE)
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return CellularStatus(CellularAvailability.NO_SIM)
        }
        val telephony = context.getSystemService(TelephonyManager::class.java)
            ?: return CellularStatus(CellularAvailability.UNKNOWN)
        return try {
            if (telephony.simState == TelephonyManager.SIM_STATE_ABSENT) {
                CellularStatus(CellularAvailability.NO_SIM)
            } else {
                // Voice service and signal require sensitive access on many devices.
                CellularStatus(CellularAvailability.UNKNOWN)
            }
        } catch (_: SecurityException) {
            CellularStatus(CellularAvailability.UNKNOWN)
        }
    }
}

@Singleton
class PackageManagerInstalledAppCatalog @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : InstalledAppCatalog {
    private val packageManager = context.packageManager

    override fun discoverEligibleApps(): List<InstalledApp> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val results = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                query,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return results
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .filter { it.activityInfo.applicationInfo.enabled }
            .map { result ->
                InstalledApp(
                    packageName = result.activityInfo.packageName,
                    label = result.loadLabel(packageManager).toString(),
                    isAllowed = false,
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    override fun launchIntent(packageName: String): Intent? =
        packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

object HomeCustomizationPolicy {
    fun orderedApps(apps: List<InstalledApp>): List<InstalledApp> =
        apps.sortedWith(
            compareByDescending<InstalledApp> { it.isFavorite }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                .thenBy { it.packageName },
        )

    fun allowedApps(apps: List<InstalledApp>): List<InstalledApp> =
        orderedApps(apps.filter { it.isAllowed })

    fun canAddToHome(app: InstalledApp): Boolean =
        app.isAllowed && app.isAvailable

    fun badgeLabel(label: String, badge: BadgeValue?): String = when (badge) {
        is BadgeValue.Known -> if (badge.count == 0) {
            "$label, no new items"
        } else {
            "$label, ${badge.count} new"
        }
        BadgeValue.Unknown -> "$label, count unavailable, repair required"
        null -> label
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LauncherHomePlatformModule {
    @Binds
    abstract fun bindHomeRoleController(
        controller: AndroidHomeRoleController,
    ): HomeRoleController

    @Binds
    abstract fun bindInstalledAppCatalog(
        catalog: PackageManagerInstalledAppCatalog,
    ): InstalledAppCatalog

    @Binds
    abstract fun bindLauncherStatusReader(
        reader: AndroidLauncherStatusReader,
    ): LauncherStatusReader
}

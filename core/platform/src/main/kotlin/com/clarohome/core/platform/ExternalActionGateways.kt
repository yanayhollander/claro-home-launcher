package com.clarohome.core.platform

import android.content.Context
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.util.Log
import com.clarohome.core.domain.EmergencyNumberDetector
import com.clarohome.core.domain.ExternalAppLaunchResult
import com.clarohome.core.domain.ExternalAppLauncher
import com.clarohome.core.domain.NativeEmergencyDialerGateway
import com.clarohome.core.domain.PhoneCallGateway
import com.clarohome.core.domain.PhoneCallRequest
import com.clarohome.core.domain.PhoneCallRequestResult
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelecomPhoneCallGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PhoneCallGateway {
    private val telecomManager: TelecomManager
        get() = context.getSystemService(TelecomManager::class.java)

    override suspend fun requestCall(request: PhoneCallRequest): PhoneCallRequestResult {
        if (!PhoneNumberUtils.isGlobalPhoneNumber(request.destination)) {
            return PhoneCallRequestResult.InvalidNumber
        }
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles?.isRoleAvailable(RoleManager.ROLE_DIALER) != true ||
            !roles.isRoleHeld(RoleManager.ROLE_DIALER)
        ) {
            return PhoneCallRequestResult.RoleOrPermissionLost
        }
        if (
            context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return PhoneCallRequestResult.RoleOrPermissionLost
        }
        return try {
            if (telecomManager.callCapablePhoneAccounts.isEmpty()) {
                return PhoneCallRequestResult.NoCallingAccount
            }
            telecomManager.placeCall(
                Uri.fromParts("tel", request.destination, null),
                Bundle.EMPTY,
            )
            PhoneCallRequestResult.Requested
        } catch (_: SecurityException) {
            PhoneCallRequestResult.RoleOrPermissionLost
        } catch (exception: IllegalArgumentException) {
            PhoneCallRequestResult.Failed(exception.message ?: "Call request rejected")
        } catch (exception: IllegalStateException) {
            PhoneCallRequestResult.Failed(exception.message ?: "Calling is unavailable")
        }
    }
}

@Singleton
class AndroidEmergencyNumberDetector @Inject constructor() : EmergencyNumberDetector {
    @Suppress("DEPRECATION")
    override fun isEmergencyNumber(destination: String): Boolean =
        PhoneNumberUtils.isEmergencyNumber(destination)
}

@Singleton
class AndroidNativeEmergencyDialerGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NativeEmergencyDialerGateway {
    override fun openEmergencyDialer(): Boolean {
        return try {
            val actions = listOf("com.android.phone.EmergencyDialer.DIAL", "android.intent.action.DIAL_EMERGENCY")
            for (action in actions) {
                val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                @Suppress("DEPRECATION")
                val activity = context.packageManager.queryIntentActivities(intent, 0)
                    .firstOrNull {
                        it.activityInfo.exported &&
                            it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                            it.activityInfo.packageName != context.packageName
                    }?.activityInfo ?: continue
                intent.component = ComponentName(activity.packageName, activity.name)
                context.startActivity(intent)
                return true
            }
            Log.w("ClaroEmergency", "No system emergency dialer could be resolved.")
            false
        } catch (_: ActivityNotFoundException) {
            Log.w("ClaroEmergency", "Android emergency dialer is unavailable.")
            false
        } catch (_: SecurityException) {
            Log.w("ClaroEmergency", "Android denied opening the emergency dialer.")
            false
        }
    }
}

@Singleton
class AndroidExternalAppLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ExternalAppLauncher {
    override fun launch(packageName: String): ExternalAppLaunchResult {
        val packageManager = context.packageManager
        val applicationInfo = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            return ExternalAppLaunchResult.MissingOrDisabled
        }
        if (!applicationInfo.enabled) return ExternalAppLaunchResult.MissingOrDisabled
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: return ExternalAppLaunchResult.MissingOrDisabled
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ExternalAppLaunchResult.Launched
        } catch (_: SecurityException) {
            ExternalAppLaunchResult.BlockedBySystem
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ExternalActionBindingsModule {
    @Binds
    abstract fun bindPhoneCallGateway(gateway: TelecomPhoneCallGateway): PhoneCallGateway

    @Binds
    abstract fun bindEmergencyNumberDetector(
        detector: AndroidEmergencyNumberDetector,
    ): EmergencyNumberDetector

    @Binds
    abstract fun bindEmergencyDialerGateway(
        gateway: AndroidNativeEmergencyDialerGateway,
    ): NativeEmergencyDialerGateway

    @Binds
    abstract fun bindExternalAppLauncher(
        launcher: AndroidExternalAppLauncher,
    ): ExternalAppLauncher
}

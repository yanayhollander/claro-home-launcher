package com.clarohome.core.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
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
        if (
            context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return PhoneCallRequestResult.RoleOrPermissionLost
        }
        if (telecomManager.callCapablePhoneAccounts.isEmpty()) {
            return PhoneCallRequestResult.NoCallingAccount
        }
        return try {
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
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", "", null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        return true
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

package com.clarohome.core.domain

enum class CallOrigin {
    DIALER,
    FAVORITE,
    SPEED_DIAL,
    TRUSTED_CONTACT_SOS,
    CAREGIVER_SOS_TEST,
}

data class PhoneCallRequest(
    val destination: String,
    val origin: CallOrigin,
)

sealed interface PhoneCallRequestResult {
    data object Requested : PhoneCallRequestResult
    data object InvalidNumber : PhoneCallRequestResult
    data object NoCallingAccount : PhoneCallRequestResult
    data object NoService : PhoneCallRequestResult
    data object RoleOrPermissionLost : PhoneCallRequestResult
    data class Failed(val reason: String) : PhoneCallRequestResult
}

interface PhoneCallGateway {
    suspend fun requestCall(request: PhoneCallRequest): PhoneCallRequestResult
}

interface NativeEmergencyDialerGateway {
    fun openEmergencyDialer(): Boolean
}

fun interface EmergencyNumberDetector {
    fun isEmergencyNumber(destination: String): Boolean
}

sealed interface ExternalAppLaunchResult {
    data object Launched : ExternalAppLaunchResult
    data object MissingOrDisabled : ExternalAppLaunchResult
    data object BlockedBySystem : ExternalAppLaunchResult
}

interface ExternalAppLauncher {
    fun launch(packageName: String): ExternalAppLaunchResult
}

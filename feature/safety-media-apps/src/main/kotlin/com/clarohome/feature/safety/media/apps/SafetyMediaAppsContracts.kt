package com.clarohome.feature.safety.media.apps

import com.clarohome.core.domain.CallOrigin
import com.clarohome.core.domain.EmergencyNumberDetector
import com.clarohome.core.domain.FeatureContract
import com.clarohome.core.domain.PhoneCallGateway
import com.clarohome.core.domain.PhoneCallRequest
import com.clarohome.core.domain.PhoneCallRequestResult
import com.clarohome.core.domain.ProtectedRoute
import java.util.Locale
import kotlin.math.max

object SafetyContract : FeatureContract {
    override val id = "safety"
    override val protectedRoutes = setOf(
        ProtectedRoute.SETTINGS,
        ProtectedRoute.SAFETY_CONFIGURATION,
    )
}

object MediaContract : FeatureContract {
    override val id = "media"
    override val protectedRoutes = emptySet<ProtectedRoute>()
}

object AppsContract : FeatureContract {
    override val id = "apps"
    override val protectedRoutes = setOf(
        ProtectedRoute.SETTINGS,
        ProtectedRoute.APP_CONFIGURATION,
    )
}

data class TrustedContact(
    val contactId: String,
    val displayName: String,
    val destination: String,
    val recipientConsented: Boolean,
)

sealed interface SosReadiness {
    data object NotConfigured : SosReadiness
    data object RecipientConsentMissing : SosReadiness
    data object Ready : SosReadiness
    data class Blocked(val reason: String) : SosReadiness
}

enum class SosCallOutcome {
    DIALING,
    RINGING,
    CONNECTED,
    ENDED,
    BUSY,
    NO_ANSWER,
    DECLINED,
    NO_SERVICE,
    ROLE_OR_PERMISSION_LOST,
    UNKNOWN_FAILURE,
}

sealed interface SosState {
    data class Unset(val message: String = "SOS is not configured.") : SosState
    data class Armed(val contact: TrustedContact, val testMode: Boolean = false) : SosState
    data class CountingDown(
        val contact: TrustedContact,
        val deadlineElapsedMillis: Long,
        val activationId: Long,
        val testMode: Boolean,
    ) : SosState
    data class Interrupted(
        val contact: TrustedContact,
        val activationId: Long,
        val testMode: Boolean,
    ) : SosState
    data class Canceled(val contact: TrustedContact, val testMode: Boolean) : SosState
    data class Calling(
        val contact: TrustedContact,
        val activationId: Long,
        val outcome: SosCallOutcome,
        val testMode: Boolean,
    ) : SosState
    data class Failed(
        val contact: TrustedContact,
        val outcome: SosCallOutcome,
        val testMode: Boolean,
    ) : SosState
}

data class PersistedSosState(
    val contact: TrustedContact?,
    val countdownDeadlineElapsedMillis: Long?,
    val activationId: Long,
    val testMode: Boolean,
)

class SosCoordinator(
    private val phoneCallGateway: PhoneCallGateway,
    private val emergencyNumberDetector: EmergencyNumberDetector,
    private val countdownMillis: Long = 5_000,
) {
    private var nextActivationId = 1L
    private var requestedActivationId: Long? = null

    var state: SosState = SosState.Unset()
        private set

    fun configure(contact: TrustedContact?): SosReadiness {
        state = when {
            contact == null -> SosState.Unset()
            emergencyNumberDetector.isEmergencyNumber(contact.destination) ->
                SosState.Unset("Emergency numbers cannot be saved as trusted contacts.")
            !contact.recipientConsented -> SosState.Armed(contact)
            else -> SosState.Armed(contact)
        }
        return readiness()
    }

    fun readiness(): SosReadiness = when (val current = state) {
        is SosState.Unset -> SosReadiness.NotConfigured
        is SosState.Armed -> when {
            emergencyNumberDetector.isEmergencyNumber(current.contact.destination) ->
                SosReadiness.Blocked("Choose a non-emergency trusted contact.")
            !current.contact.recipientConsented -> SosReadiness.RecipientConsentMissing
            else -> SosReadiness.Ready
        }
        else -> SosReadiness.Ready
    }

    fun start(nowElapsedMillis: Long, testMode: Boolean = false): Boolean {
        val contact = when (val current = state) {
            is SosState.Armed -> current.contact
            is SosState.Canceled -> current.contact
            is SosState.Failed -> current.contact
            is SosState.Interrupted -> current.contact
            else -> return false
        }
        if (!contact.recipientConsented || emergencyNumberDetector.isEmergencyNumber(contact.destination)) {
            return false
        }
        val activationId = nextActivationId++
        requestedActivationId = null
        state = SosState.CountingDown(
            contact = contact,
            deadlineElapsedMillis = nowElapsedMillis + countdownMillis,
            activationId = activationId,
            testMode = testMode,
        )
        return true
    }

    fun remainingSeconds(nowElapsedMillis: Long): Int {
        val countdown = state as? SosState.CountingDown ?: return 0
        val remaining = max(0, countdown.deadlineElapsedMillis - nowElapsedMillis)
        return ((remaining + 999) / 1_000).toInt()
    }

    fun cancel(): Boolean {
        val countdown = state as? SosState.CountingDown ?: return false
        state = SosState.Canceled(countdown.contact, countdown.testMode)
        return true
    }

    fun interrupt() {
        val countdown = state as? SosState.CountingDown ?: return
        state = SosState.Interrupted(
            contact = countdown.contact,
            activationId = countdown.activationId,
            testMode = countdown.testMode,
        )
    }

    fun restore(persisted: PersistedSosState) {
        nextActivationId = max(nextActivationId, persisted.activationId + 1)
        requestedActivationId = null
        state = when {
            persisted.contact == null -> SosState.Unset()
            persisted.countdownDeadlineElapsedMillis != null -> SosState.Interrupted(
                contact = persisted.contact,
                activationId = persisted.activationId,
                testMode = persisted.testMode,
            )
            else -> SosState.Armed(persisted.contact, persisted.testMode)
        }
    }

    fun snapshot(): PersistedSosState {
        val current = state
        val contact = when (current) {
            is SosState.Unset -> null
            is SosState.Armed -> current.contact
            is SosState.CountingDown -> current.contact
            is SosState.Interrupted -> current.contact
            is SosState.Canceled -> current.contact
            is SosState.Calling -> current.contact
            is SosState.Failed -> current.contact
        }
        return PersistedSosState(
            contact = contact,
            countdownDeadlineElapsedMillis = (current as? SosState.CountingDown)?.deadlineElapsedMillis,
            activationId = when (current) {
                is SosState.CountingDown -> current.activationId
                is SosState.Interrupted -> current.activationId
                is SosState.Calling -> current.activationId
                else -> 0
            },
            testMode = when (current) {
                is SosState.Armed -> current.testMode
                is SosState.CountingDown -> current.testMode
                is SosState.Interrupted -> current.testMode
                is SosState.Canceled -> current.testMode
                is SosState.Calling -> current.testMode
                is SosState.Failed -> current.testMode
                is SosState.Unset -> false
            },
        )
    }

    suspend fun advance(nowElapsedMillis: Long): SosState {
        val countdown = state as? SosState.CountingDown ?: return state
        if (nowElapsedMillis < countdown.deadlineElapsedMillis) return state
        if (requestedActivationId == countdown.activationId) return state

        requestedActivationId = countdown.activationId
        state = when (phoneCallGateway.requestCall(
            PhoneCallRequest(
                destination = countdown.contact.destination,
                origin = if (countdown.testMode) {
                    CallOrigin.CAREGIVER_SOS_TEST
                } else {
                    CallOrigin.TRUSTED_CONTACT_SOS
                },
            ),
        )) {
            PhoneCallRequestResult.Requested -> SosState.Calling(
                contact = countdown.contact,
                activationId = countdown.activationId,
                outcome = SosCallOutcome.DIALING,
                testMode = countdown.testMode,
            )
            PhoneCallRequestResult.NoService -> failure(countdown, SosCallOutcome.NO_SERVICE)
            PhoneCallRequestResult.NoCallingAccount ->
                failure(countdown, SosCallOutcome.NO_SERVICE)
            PhoneCallRequestResult.RoleOrPermissionLost ->
                failure(countdown, SosCallOutcome.ROLE_OR_PERMISSION_LOST)
            else -> failure(countdown, SosCallOutcome.UNKNOWN_FAILURE)
        }
        return state
    }

    fun onCallOutcome(outcome: SosCallOutcome) {
        val calling = state as? SosState.Calling ?: return
        state = when (outcome) {
            SosCallOutcome.BUSY,
            SosCallOutcome.NO_ANSWER,
            SosCallOutcome.DECLINED,
            SosCallOutcome.NO_SERVICE,
            SosCallOutcome.ROLE_OR_PERMISSION_LOST,
            SosCallOutcome.UNKNOWN_FAILURE,
            -> SosState.Failed(calling.contact, outcome, calling.testMode)
            else -> calling.copy(outcome = outcome)
        }
    }

    private fun failure(
        countdown: SosState.CountingDown,
        outcome: SosCallOutcome,
    ) = SosState.Failed(countdown.contact, outcome, countdown.testMode)
}

enum class PhotoOwnership {
    CLARO_OWNED,
    REFERENCED,
}

enum class PhotoAvailability {
    AVAILABLE,
    PERMISSION_LOST,
    CLOUD_UNAVAILABLE,
    MISSING,
}

data class ClaroPhoto(
    val id: String,
    val title: String,
    val contentUri: String,
    val ownership: PhotoOwnership,
    val availability: PhotoAvailability = PhotoAvailability.AVAILABLE,
)

data class MediaUriGrant(
    val read: Boolean,
    val write: Boolean,
    val persistable: Boolean,
)

sealed interface PhotoRemovalRequest {
    data class RemoveReference(val photoId: String) : PhotoRemovalRequest
    data class RequestOwnedDeleteAuthorization(
        val photoId: String,
        val contentUri: String,
    ) : PhotoRemovalRequest
}

class MediaLibrary(photos: List<ClaroPhoto> = emptyList()) {
    private val records = photos.associateByTo(linkedMapOf()) { it.id }

    fun all(): List<ClaroPhoto> = records.values.toList()

    fun addReferenced(
        photo: ClaroPhoto,
        grant: MediaUriGrant,
    ): Boolean {
        if (photo.ownership != PhotoOwnership.REFERENCED || !grant.read) return false
        records[photo.id] = photo
        return true
    }

    fun addOwned(photo: ClaroPhoto): Boolean {
        if (photo.ownership != PhotoOwnership.CLARO_OWNED) return false
        records[photo.id] = photo
        return true
    }

    fun requestRemoval(photoId: String): PhotoRemovalRequest? = when (val photo = records[photoId]) {
        null -> null
        else -> when (photo.ownership) {
            PhotoOwnership.REFERENCED -> PhotoRemovalRequest.RemoveReference(photo.id)
            PhotoOwnership.CLARO_OWNED ->
                PhotoRemovalRequest.RequestOwnedDeleteAuthorization(photo.id, photo.contentUri)
        }
    }

    fun confirmReferenceRemoval(photoId: String): Boolean {
        val photo = records[photoId] ?: return false
        if (photo.ownership != PhotoOwnership.REFERENCED) return false
        records.remove(photoId)
        return true
    }

    fun confirmOwnedDeletion(photoId: String, authorized: Boolean): Boolean {
        val photo = records[photoId] ?: return false
        if (photo.ownership != PhotoOwnership.CLARO_OWNED || !authorized) return false
        records.remove(photoId)
        return true
    }

    fun markUnavailable(photoId: String, availability: PhotoAvailability) {
        val photo = records[photoId] ?: return
        records[photoId] = photo.copy(availability = availability)
    }
}

data class AllowedApp(
    val packageName: String,
    val label: String,
    val favorite: Boolean,
    val enabled: Boolean = true,
    val installed: Boolean = true,
)

enum class AppListFilter {
    FAVORITES,
    ALL_ALLOWED,
}

sealed interface AllowedAppOpenResult {
    data class Ready(val app: AllowedApp) : AllowedAppOpenResult
    data class Missing(val app: AllowedApp) : AllowedAppOpenResult
    data object NotAllowed : AllowedAppOpenResult
}

class AllowedAppsMenu(apps: Collection<AllowedApp> = emptyList()) {
    private val allowed = apps.associateByTo(linkedMapOf()) { it.packageName }

    fun replaceAllowlist(apps: Collection<AllowedApp>) {
        allowed.clear()
        apps.associateByTo(allowed) { it.packageName }
    }

    fun search(query: String, filter: AppListFilter): List<AllowedApp> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        return allowed.values
            .asSequence()
            .filter { filter == AppListFilter.ALL_ALLOWED || it.favorite }
            .filter {
                normalized.isEmpty() ||
                    it.label.lowercase(Locale.ROOT).contains(normalized) ||
                    it.packageName.lowercase(Locale.ROOT).contains(normalized)
            }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
            .toList()
    }

    fun open(packageName: String): AllowedAppOpenResult {
        val app = allowed[packageName] ?: return AllowedAppOpenResult.NotAllowed
        return if (app.installed && app.enabled) {
            AllowedAppOpenResult.Ready(app)
        } else {
            AllowedAppOpenResult.Missing(app)
        }
    }
}

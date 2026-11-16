package com.clarohome.feature.communication

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.core.app.NotificationCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import com.clarohome.core.domain.ClaroPreferencesRepository
import kotlinx.coroutines.withContext

@Singleton
class AndroidDialerRoleGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : DialerRoleGateway {
    private val mutableQualification = MutableStateFlow(inspect())
    override val qualification: StateFlow<DialerQualification> =
        mutableQualification.asStateFlow()

    override fun createRoleRequest(): Intent? {
        val roleManager = context.getSystemService(RoleManager::class.java)
        return if (roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else {
            null
        }
    }

    override suspend fun refresh() {
        mutableQualification.value = inspect()
    }

    @SuppressLint("MissingPermission")
    private fun inspect(): DialerQualification {
        val roleManager = context.getSystemService(RoleManager::class.java)
        val telecomManager = context.getSystemService(TelecomManager::class.java)
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        val roleAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
        val roleHeld = roleAvailable &&
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        val telephonySupported = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_TELEPHONY,
        ) && telephonyManager?.phoneType != TelephonyManager.PHONE_TYPE_NONE
        var phoneStatePermissionGranted = context.hasPermission(Manifest.permission.READ_PHONE_STATE)
        val hasCallingAccount =
            phoneStatePermissionGranted &&
                runCatching {
                    telecomManager?.callCapablePhoneAccounts?.isNotEmpty() == true
                }.onFailure {
                    if (it is SecurityException) phoneStatePermissionGranted = false
                    Log.w("ClaroTelecom", "Calling-account inspection failed: ${it.javaClass.simpleName}")
                }.getOrDefault(false)
        return DialerQualification(
            roleAvailable = roleAvailable,
            roleHeld = roleHeld,
            telephonySupported = telephonySupported,
            callPermissionGranted = context.hasPermission(Manifest.permission.CALL_PHONE),
            callLogPermissionGranted = context.hasPermission(Manifest.permission.READ_CALL_LOG),
            hasUsableCallingAccount = hasCallingAccount,
            phoneStatePermissionGranted = phoneStatePermissionGranted,
        )
    }
}

@Singleton
class AndroidTelecomGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : TelecomGateway {
    private val telecomManager = context.getSystemService(TelecomManager::class.java)
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val eventChannel = Channel<TelecomEvent>(Channel.UNLIMITED)
    override val events: SharedFlow<TelecomEvent> = eventChannel.receiveAsFlow()
        .shareIn(eventScope, SharingStarted.Eagerly, replay = 1)
    private val calls = ConcurrentHashMap<String, Call>()
    private val connectedAt = ConcurrentHashMap<String, Long>()
    private val callerIdentities = ConcurrentHashMap<String, CallerIdentity>()
    private val requestedCallerNumbers = ConcurrentHashMap<String, String>()
    private var foregroundCallId: String? = null
    private var service: InCallService? = null
    private val automaticAudio = AutomaticCallAudio(context) { route ->
        service?.setAudioRoute(
            if (route == AudioRoute.SPEAKER) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE,
        )
    }

    @SuppressLint("MissingPermission")
    override suspend fun placeCall(number: String): CallPlacementResult {
        if (!context.hasPermission(Manifest.permission.CALL_PHONE)) {
            return CallPlacementResult.Rejected(CallFailure.PERMISSION_DENIED)
        }
        if (!context.hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            return CallPlacementResult.Rejected(CallFailure.PERMISSION_DENIED)
        }
        val accountsAvailable = runCatching {
            telecomManager?.callCapablePhoneAccounts?.isNotEmpty() == true
        }.getOrElse {
            Log.w("ClaroTelecom", "Calling-account inspection failed: ${it.javaClass.simpleName}")
            return CallPlacementResult.Rejected(
                if (it is SecurityException) CallFailure.PERMISSION_DENIED else CallFailure.UNKNOWN,
            )
        }
        if (!accountsAvailable) {
            return CallPlacementResult.Rejected(CallFailure.ACCOUNT_UNAVAILABLE)
        }
        return runCatching {
            telecomManager?.placeCall(Uri.fromParts("tel", number, null), Bundle())
                ?: return CallPlacementResult.Rejected(CallFailure.UNKNOWN)
            CallPlacementResult.SubmittedToTelecom
        }.getOrElse {
            CallPlacementResult.Rejected(
                if (it is SecurityException) {
                    CallFailure.PERMISSION_DENIED
                } else {
                    CallFailure.UNKNOWN
                },
            )
        }
    }

    override suspend fun handoffEmergency(number: String): CallPlacementResult =
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            CallPlacementResult.EmergencyHandoffStarted
        }.getOrElse {
            CallPlacementResult.Rejected(CallFailure.UNKNOWN)
        }

    override fun isEmergencyNumber(number: String): Boolean =
        runCatching { telephonyManager?.isEmergencyNumber(number) == true }.getOrDefault(false)

    override suspend fun answer(callId: String) {
        calls[callId]?.answer(0)
    }

    override suspend fun decline(callId: String) {
        calls[callId]?.reject(false, null)
    }

    override suspend fun disconnect(callId: String) {
        calls[callId]?.disconnect()
    }

    override suspend fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    @Suppress("DEPRECATION")
    override suspend fun setAudioRoute(route: AudioRoute) {
        automaticAudio.manualOverride()
        val platformRoute = when (route) {
            AudioRoute.EARPIECE -> CallAudioState.ROUTE_EARPIECE
            AudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            AudioRoute.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            AudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            AudioRoute.UNKNOWN -> return
        }
        service?.setAudioRoute(platformRoute)
    }

    override suspend fun setHeld(callId: String, held: Boolean) {
        calls[callId]?.let { call ->
            if (held) call.hold() else call.unhold()
        }
    }

    override suspend fun swap(callId: String) {
        val selected = calls[callId] ?: return
        val other = calls.entries.firstOrNull { it.key != callId }?.value ?: return
        selected.hold()
        other.unhold()
    }

    override suspend fun playDtmf(callId: String, digit: Char) {
        calls[callId]?.let { call ->
            call.playDtmfTone(digit)
            call.stopDtmfTone()
        }
    }

    fun attach(service: InCallService) {
        this.service = service
    }

    fun detach(service: InCallService) {
        if (this.service === service) {
            automaticAudio.detach()
            this.service = null
        }
    }

    fun configureCallAudio(easyCallAudio: Boolean, maximumSpeakerVolume: Boolean) {
        automaticAudio.configure(easyCallAudio, maximumSpeakerVolume)
    }

    @Suppress("DEPRECATION")
    private fun updateAutomaticAudio(audioState: CallAudioState? = service?.callAudioState) {
        automaticAudio.update(
            state = audioState,
            hasCall = calls.values.any {
                it.state !in setOf(Call.STATE_RINGING, Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING)
            },
            connected = calls.values.any { it.state == Call.STATE_ACTIVE },
        )
    }

    fun addCall(call: Call) {
        val callId = call.id()
        calls[callId] = call
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(changedCall: Call, state: Int) {
                publishState(changedCall, state)
            }

            override fun onDetailsChanged(changedCall: Call, details: Call.Details) {
                publishState(changedCall, changedCall.state, foreground = false)
                resolveCaller(changedCall)
            }
        })
        if (call.state == Call.STATE_RINGING) {
            foregroundCallId = callId
            showIncomingNotification(call)
            publishEvent(
                TelecomEvent.Incoming(
                    callId = callId,
                    identity = call.callerIdentity(),
                    capabilities = call.capabilities(),
                ),
            )
        } else {
            publishState(call, call.state)
        }
        resolveCaller(call)
    }

    private fun resolveCaller(call: Call) {
        val callId = call.id()
        val identity = call.identity()
        val number = identity.number
        if (calls[callId] === call && !identity.isPrivate && !number.isNullOrBlank() &&
            requestedCallerNumbers.put(callId, number) != number
        ) {
            eventScope.launch {
                val resolved = withContext(Dispatchers.IO) {
                    val resolver = PhoneNumberNameResolver(context)
                    identity.copy(
                        displayName = resolver.resolve(number) ?: identity.displayName,
                        photoUri = resolver.resolvePhotoUri(number),
                    )
                }
                if (calls[callId] === call && call.identity().number == number) {
                    callerIdentities[callId] = resolved
                    publishState(call, call.state, foreground = false)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun removeCall(call: Call) {
        val id = call.id()
        notificationManager().cancel(id.hashCode())
        val identity = call.callerIdentity()
        val direction = call.direction()
        val connected = connectedAt[id]
        val cause = call.details.disconnectCause
        calls.remove(id)
        updateAutomaticAudio()
        connectedAt.remove(id)
        callerIdentities.remove(id)
        requestedCallerNumbers.remove(id)
        val remainingCalls = calls.values.mapNotNull { remaining ->
            val phase = remaining.state.toCallPhase()
            if (phase == CallPhase.DISCONNECTED) null else CallSession(
                id = remaining.id(),
                direction = remaining.direction(),
                identity = remaining.callerIdentity(),
                phase = phase,
                capabilities = remaining.capabilities(),
                audioRoute = service?.callAudioState?.route.toAudioRoute(),
                isMuted = service?.callAudioState?.isMuted == true,
                connectedAtElapsedMillis = connectedAt[remaining.id()],
            )
        }
        if (remainingCalls.none { it.id == foregroundCallId }) {
            foregroundCallId = remainingCalls.firstOrNull { it.phase == CallPhase.INCOMING }?.id
                ?: remainingCalls.firstOrNull { it.phase == CallPhase.CONNECTED }?.id
                ?: remainingCalls.firstOrNull()?.id
        }
        publishEvent(
            TelecomEvent.Disconnected(
                callId = id,
                direction = direction,
                identity = identity,
                outcome = outcome(direction, connected != null, cause),
                failure = failure(cause),
                connectedAtElapsedMillis = connected,
                disconnectedAtElapsedMillis = SystemClock.elapsedRealtime(),
                remainingCalls = remainingCalls,
            ),
        )
    }

    fun updateAudioState(audioState: CallAudioState) {
        updateAutomaticAudio(audioState)
        val active = calls.values.firstOrNull { it.state == Call.STATE_ACTIVE } ?: return
        publishState(active, active.state, audioState, foreground = false)
    }

    @Suppress("DEPRECATION")
    private fun publishState(
        call: Call,
        state: Int,
        audioState: CallAudioState? = service?.callAudioState,
        foreground: Boolean = true,
    ) {
        val callId = call.id()
        val phase = state.toCallPhase()
        updateAutomaticAudio(audioState)
        if (phase == CallPhase.DISCONNECTED) {
            notificationManager().cancel(callId.hashCode())
            return
        }
        if (phase == CallPhase.CONNECTED) {
            connectedAt.putIfAbsent(callId, SystemClock.elapsedRealtime())
        }
        if (foreground || foregroundCallId == null || foregroundCallId == callId) {
            foregroundCallId = callId
            publishEvent(
                TelecomEvent.StateChanged(
                    callId = callId,
                    direction = call.direction(),
                    identity = call.callerIdentity(),
                    phase = phase,
                    capabilities = call.capabilities(),
                    audioRoute = audioState?.route.toAudioRoute(),
                    isMuted = audioState?.isMuted == true,
                    connectedAtElapsedMillis = connectedAt[callId],
                    foreground = foreground,
                ),
            )
        }
        when (phase) {
            CallPhase.DIALING,
            CallPhase.RINGING,
            CallPhase.CONNECTED,
            CallPhase.ON_HOLD,
            -> showOngoingNotification(call)
            CallPhase.INCOMING -> showIncomingNotification(call)
            CallPhase.DISCONNECTED -> notificationManager().cancel(callId.hashCode())
            else -> Unit
        }
    }

    private fun publishEvent(event: TelecomEvent) {
        eventChannel.trySend(event).getOrThrow()
    }

    private fun Call.callerIdentity(): CallerIdentity {
        val identity = identity()
        val resolved = callerIdentities[id()]?.takeIf { it.number == identity.number }
        return identity.copy(
            displayName = resolved?.displayName ?: identity.displayName,
            photoUri = resolved?.photoUri,
        )
    }

    private fun outcome(
        direction: CallDirection,
        wasConnected: Boolean,
        cause: DisconnectCause,
    ): CallOutcome = when {
        wasConnected -> CallOutcome.ENDED
        direction == CallDirection.INCOMING && cause.code == DisconnectCause.REJECTED ->
            CallOutcome.DECLINED_INCOMING
        direction == CallDirection.INCOMING -> CallOutcome.MISSED_INCOMING
        cause.code in setOf(DisconnectCause.CANCELED, DisconnectCause.LOCAL) ->
            CallOutcome.CANCELED_OUTGOING
        cause.code in setOf(DisconnectCause.BUSY, DisconnectCause.REMOTE) ->
            CallOutcome.UNANSWERED_OUTGOING
        else -> CallOutcome.FAILED
    }

    private fun failure(cause: DisconnectCause): CallFailure? = when (cause.code) {
        DisconnectCause.ERROR -> CallFailure.NETWORK_FAILURE
        DisconnectCause.RESTRICTED -> CallFailure.NO_SERVICE
        DisconnectCause.OTHER -> CallFailure.UNKNOWN
        else -> null
    }

    private fun showIncomingNotification(call: Call) {
        val callId = call.id()
        val identity = call.callerIdentity()
        val manager = notificationManager()
        manager.createNotificationChannel(
            NotificationChannel(
                INCOMING_CALL_CHANNEL,
                context.getString(R.string.comm_channel_incoming_calls_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.comm_channel_incoming_calls_description)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        val contentIntent = callContentIntent(callId)
        val answerIntent = callActionIntent(callId, ACTION_ANSWER)
        val declineIntent = callActionIntent(callId, ACTION_DECLINE)
        val notification = NotificationCompat.Builder(context, INCOMING_CALL_CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(identity.localizedTitle(context))
            .setContentText(context.getString(R.string.comm_notification_incoming_call_text))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.comm_notification_action_decline),
                declineIntent,
            )
            .addAction(
                android.R.drawable.sym_action_call,
                context.getString(R.string.comm_notification_action_answer),
                answerIntent,
            )
            .build()
        manager.notify(callId.hashCode(), notification)
    }

    private fun showOngoingNotification(call: Call) {
        val manager = notificationManager()
        manager.createNotificationChannel(
            NotificationChannel(
                ONGOING_CALL_CHANNEL,
                context.getString(R.string.comm_channel_ongoing_calls_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.comm_channel_ongoing_calls_description)
                setSound(null, null)
            },
        )
        val callId = call.id()
        manager.notify(callId.hashCode(), ongoingCallNotification(callId, call.callerIdentity()))
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun ongoingCallNotification(callId: String, identity: CallerIdentity): android.app.Notification =
        NotificationCompat.Builder(context, ONGOING_CALL_CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(identity.localizedTitle(context))
            .setContentText(context.getString(R.string.comm_notification_return_to_call))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(callContentIntent(callId))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.comm_notification_action_end),
                callActionIntent(callId, ACTION_END),
            )
            .build()

    private fun callContentIntent(callId: String): PendingIntent = PendingIntent.getActivity(
        context,
        callId.hashCode(),
        Intent(ACTION_RETURN_TO_CALL)
            .setClassName(context.packageName, "com.clarohome.app.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun callActionIntent(callId: String, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (callId + action).hashCode(),
            Intent(context, IncomingCallActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_CALL_ID, callId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notificationManager(): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    companion object {
        const val ACTION_ANSWER = "com.clarohome.action.ANSWER_CALL"
        const val ACTION_DECLINE = "com.clarohome.action.DECLINE_CALL"
        const val ACTION_END = "com.clarohome.action.END_CALL"
        const val ACTION_RETURN_TO_CALL = "com.clarohome.action.RETURN_TO_CALL"
        const val EXTRA_CALL_ID = "call_id"
        private const val INCOMING_CALL_CHANNEL = "incoming_calls"
        private const val ONGOING_CALL_CHANNEL = "ongoing_calls"
    }
}

@AndroidEntryPoint
class ClaroInCallService : InCallService() {
    @Inject lateinit var gateway: AndroidTelecomGateway
    @Inject lateinit var preferences: ClaroPreferencesRepository
    private val preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        gateway.attach(this)
        preferenceScope.launch {
            preferences.preferences.collect {
                gateway.configureCallAudio(it.easyCallAudio, it.maximumSpeakerVolume)
            }
        }
    }

    override fun onDestroy() {
        preferenceScope.cancel()
        gateway.detach(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        gateway.addCall(call)
    }

    override fun onCallRemoved(call: Call) {
        gateway.removeCall(call)
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        gateway.updateAudioState(audioState)
    }
}

@AndroidEntryPoint
class IncomingCallActionReceiver : BroadcastReceiver() {
    @Inject lateinit var gateway: AndroidTelecomGateway

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(AndroidTelecomGateway.EXTRA_CALL_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    AndroidTelecomGateway.ACTION_ANSWER -> gateway.answer(callId)
                    AndroidTelecomGateway.ACTION_DECLINE -> gateway.decline(callId)
                    AndroidTelecomGateway.ACTION_END -> gateway.disconnect(callId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

@Singleton
class AndroidContactsGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : ContactsGateway {
    private val resolver = context.contentResolver

    override suspend fun matchingNumbers(digits: String, limit: Int): List<CallerIdentity> =
        withContext(Dispatchers.IO) {
            require(digits.isNotEmpty() && digits.all(Char::isDigit) && limit > 0)
            if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) {
                throw SecurityException("Contacts permission is needed")
            }
            val compactNumber = listOf(" ", "-", "(", ")", ".", "\u00a0", "\u202f")
                .fold(ContactsContract.CommonDataKinds.Phone.NUMBER) { column, separator ->
                    "REPLACE($column, '$separator', '')"
                }
            val queryArgs = Bundle().apply {
                putString(
                    ContentResolverQuery.SELECTION,
                    "$compactNumber LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER} LIKE ?",
                )
                putStringArray(ContentResolverQuery.SELECTION_ARGS, arrayOf("%$digits%", "%$digits%"))
                putStringArray(
                    ContentResolverQuery.SORT_COLUMNS,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY),
                )
                putInt(ContentResolverQuery.SORT_DIRECTION, ContentResolverQuery.SORT_ASCENDING)
                putInt(ContentResolverQuery.LIMIT, limit)
            }
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                PHONE_PROJECTION,
                queryArgs,
                null,
            )?.use(::readContacts)
                .orEmpty().flatMap { contact ->
                    contact.numbers.map { number ->
                        CallerIdentity(contact.displayName, number.value, photoUri = contact.photoUri)
                    }
                }.take(limit)
        }

    override suspend fun page(query: String, offset: Int, limit: Int): ContactPage {
        if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) {
            return ContactPage(emptyList(), null, 0)
        }
        val selection = if (query.isBlank()) {
            null
        } else {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ? OR " +
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        }
        val args = if (query.isBlank()) null else arrayOf("%$query%", "%$query%")
        val queryArgs = Bundle().apply {
            putString(ContentResolverQuery.SELECTION, selection)
            putStringArray(ContentResolverQuery.SELECTION_ARGS, args)
            putStringArray(
                ContentResolverQuery.SORT_COLUMNS,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY),
            )
            putInt(ContentResolverQuery.SORT_DIRECTION, ContentResolverQuery.SORT_ASCENDING)
            putInt(ContentResolverQuery.LIMIT, limit)
            putInt(ContentResolverQuery.OFFSET, offset)
        }
        val contacts = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            PHONE_PROJECTION,
            queryArgs,
            null,
        )?.use(::readContacts).orEmpty()
        return ContactPage(
            contacts = contacts,
            nextOffset = if (contacts.size >= limit) offset + limit else null,
            totalCount = null,
        )
    }

    override suspend fun get(lookupKey: String): ClaroContact? {
        if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) return null
        val selection = "${ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY} = ?"
        return resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            PHONE_PROJECTION,
            selection,
            arrayOf(lookupKey),
            null,
        )?.use(::readContacts)?.firstOrNull()
    }

    override suspend fun add(mutation: ContactMutation): ContactWriteResult {
        if (!context.hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            return ContactWriteResult.PermissionDenied
        }
        if (!mutation.isValid()) {
            return ContactWriteResult.Failed(
                context.getString(R.string.comm_contact_name_and_number_required),
            )
        }
        val operations = arrayListOf(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                )
                .withValue(
                    ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                    mutation.displayName,
                )
                .build(),
        )
        mutation.numbers.forEach { number ->
            operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                )
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number.value)
                .withValue(
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,
                )
                .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, number.label)
                .build()
        }
        return runCatching {
            val result = resolver.applyBatch(ContactsContract.AUTHORITY, operations)
            val rawContactId = result.firstOrNull()?.uri?.let(ContentUris::parseId)
                ?: return ContactWriteResult.Failed(
                    context.getString(R.string.comm_contact_not_saved),
                )
            val lookupKey = lookupKeyForRawContact(rawContactId)
                ?: return ContactWriteResult.Failed(
                    context.getString(R.string.comm_contact_saved_not_opened),
                )
            ContactWriteResult.Saved(lookupKey)
        }.contactWriteFailure()
    }

    override suspend fun edit(
        lookupKey: String,
        mutation: ContactMutation,
    ): ContactWriteResult {
        if (!context.hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            return ContactWriteResult.PermissionDenied
        }
        if (!mutation.isValid()) {
            return ContactWriteResult.Failed(
                context.getString(R.string.comm_contact_name_and_number_required),
            )
        }
        val contact = get(lookupKey) ?: return ContactWriteResult.Failed(
            context.getString(R.string.comm_contact_not_found),
        )
        val contactId = resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            "${ContactsContract.Contacts.LOOKUP_KEY} = ?",
            arrayOf(contact.lookupKey),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        } ?: return ContactWriteResult.Failed(
            context.getString(R.string.comm_contact_not_found),
        )
        return runCatching {
            val rawContactId = firstWritableRawContactId(contactId)
                ?: return ContactWriteResult.AccountReadOnly
            val operations = arrayListOf(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND " +
                            "${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(
                            rawContactId.toString(),
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                        ),
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                        mutation.displayName,
                    )
                    .build(),
                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND " +
                            "${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(
                            rawContactId.toString(),
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                        ),
                    )
                    .build(),
            )
            mutation.numbers.forEach { number ->
                operations += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number.value)
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, number.label)
                    .build()
            }
            resolver.applyBatch(ContactsContract.AUTHORITY, operations)
            ContactWriteResult.Saved(lookupKey)
        }.contactWriteFailure()
    }

    override suspend fun delete(lookupKey: String): ContactWriteResult {
        if (!context.hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            return ContactWriteResult.PermissionDenied
        }
        return runCatching {
            val contactUri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                lookupKey,
            )
            if (resolver.delete(contactUri, null, null) > 0) {
                ContactWriteResult.Saved(lookupKey)
            } else {
                ContactWriteResult.AccountReadOnly
            }
        }.contactWriteFailure()
    }

    private fun readContacts(cursor: Cursor): List<ClaroContact> {
        val contacts = linkedMapOf<String, ClaroContact>()
        while (cursor.moveToNext()) {
            val lookupKey = cursor.string(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            val number = cursor.string(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val type = cursor.int(ContactsContract.CommonDataKinds.Phone.TYPE)
            val customLabel = cursor.nullableString(ContactsContract.CommonDataKinds.Phone.LABEL)
            val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                context.resources,
                type,
                customLabel,
            ).toString()
            val existing = contacts[lookupKey]
            val contactNumber = ContactNumber(number, label)
            contacts[lookupKey] = if (existing == null) {
                ClaroContact(
                    lookupKey = lookupKey,
                    displayName = cursor.string(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ),
                    photoUri = cursor.nullableString(
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                    ),
                    numbers = listOf(contactNumber),
                )
            } else {
                existing.copy(
                    numbers = (existing.numbers + contactNumber).distinctBy {
                        PhoneContactsCoordinator.normalizeDialString(it.value)
                    },
                )
            }
        }
        return contacts.values.toList()
    }

    private fun lookupKeyForRawContact(rawContactId: Long): String? =
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts._ID} = ?",
            arrayOf(rawContactId.toString()),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val contactId = cursor.getLong(0)
            resolver.query(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
                null,
                null,
                null,
            )?.use { contactCursor ->
                if (contactCursor.moveToFirst()) contactCursor.getString(0) else null
            }
        }

    private fun firstWritableRawContactId(contactId: Long): Long? =
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND " +
                "${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }

    private fun ContactMutation.isValid(): Boolean =
        displayName.isNotBlank() && numbers.isNotEmpty() &&
            numbers.all { PhoneContactsCoordinator.normalizeDialString(it.value).isNotEmpty() }

    private fun <T> Result<T>.contactWriteFailure(): ContactWriteResult =
        fold(
            onSuccess = { it as ContactWriteResult },
            onFailure = {
                when (it) {
                    is SecurityException -> ContactWriteResult.PermissionDenied
                    is UnsupportedOperationException -> ContactWriteResult.AccountReadOnly
                    else -> ContactWriteResult.Failed(
                        it.message ?: context.getString(R.string.comm_contact_change_failed),
                    )
                }
            },
        )

    private fun Cursor.string(columnName: String): String =
        getString(getColumnIndexOrThrow(columnName))

    private fun Cursor.nullableString(columnName: String): String? =
        getString(getColumnIndexOrThrow(columnName))

    private fun Cursor.int(columnName: String): Int =
        getInt(getColumnIndexOrThrow(columnName))

    companion object {
        private val PHONE_PROJECTION = arrayOf(
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
        )
    }
}

@Singleton
class SharedPreferencesPhoneAssignmentStore @Inject constructor(
    @ApplicationContext context: Context,
) : PhoneAssignmentStore {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mutableAssignments = MutableStateFlow(read())
    override val assignments: StateFlow<List<PhoneAssignment>> =
        mutableAssignments.asStateFlow()

    override suspend fun save(assignment: PhoneAssignment) {
        val next = (mutableAssignments.value.filterNot { it.slot == assignment.slot } + assignment)
            .sortedBy { it.slot }
        write(next)
    }

    override suspend fun clear(slot: String) {
        write(mutableAssignments.value.filterNot { it.slot == slot })
    }

    private fun write(assignments: List<PhoneAssignment>) {
        preferences.edit {
            putStringSet(
                KEY_ASSIGNMENTS,
                assignments.mapTo(mutableSetOf()) { assignment ->
                    listOf(
                        assignment.slot,
                        assignment.contactLookupKey,
                        assignment.selectedNumberSnapshot,
                        assignment.displayNameSnapshot,
                        assignment.status.name,
                    ).joinToString(SEPARATOR) { Uri.encode(it) }
                },
            )
        }
        mutableAssignments.value = assignments
    }

    private fun read(): List<PhoneAssignment> =
        preferences.getStringSet(KEY_ASSIGNMENTS, emptySet()).orEmpty().mapNotNull { encoded ->
            val values = encoded.split(SEPARATOR).map(Uri::decode)
            if (values.size != 5) return@mapNotNull null
            PhoneAssignment(
                slot = values[0],
                contactLookupKey = values[1],
                selectedNumberSnapshot = values[2],
                displayNameSnapshot = values[3],
                status = runCatching { AssignmentStatus.valueOf(values[4]) }
                    .getOrDefault(AssignmentStatus.CONTACT_MISSING),
            )
        }.sortedBy { it.slot }

    companion object {
        private const val PREFERENCES = "phone_assignments"
        private const val KEY_ASSIGNMENTS = "assignments"
        private const val SEPARATOR = "|"
    }
}

@Singleton
class SharedPreferencesCallHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : CallHistoryStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var localEntries: List<CallHistoryEntry> = readLocal()
    private val mutableHistory = MutableStateFlow(localEntries)
    override val history: StateFlow<List<CallHistoryEntry>> = mutableHistory.asStateFlow()

    init {
        runCatching {
            context.contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        scope.launch { refresh() }
                    }
                },
            )
        }
        scope.launch { refresh() }
    }

    override suspend fun record(entry: CallHistoryEntry) {
        localEntries = (listOf(entry) + localEntries.filterNot { it.id == entry.id }).take(MAX_ENTRIES)
        writeLocal(localEntries)
        refresh()
    }

    override suspend fun markOpened(id: String) {
        val opened = openedIds() + id
        preferences.edit { putStringSet(KEY_OPENED, opened) }
        localEntries = localEntries.map { if (it.id == id) it.copy(isOpened = true) else it }
        writeLocal(localEntries)
        refresh()
    }

    // The system call log is authoritative when readable; Claro's own record is only a fallback.
    override suspend fun refresh() {
        val system = readSystemLog()
        mutableHistory.value = (system ?: localEntries).sortedByDescending { it.timestampMillis }
    }

    private fun readSystemLog(): List<CallHistoryEntry>? {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val opened = openedIds()
        val resolver = PhoneNumberNameResolver(context)
        val entries = mutableListOf<CallHistoryEntry>()
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.IS_READ,
                    CallLog.Calls.NUMBER_PRESENTATION,
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext() && entries.size < MAX_ENTRIES) {
                    val type = cursor.getInt(3)
                    val durationMillis = cursor.getLong(5) * 1_000
                    val (direction, outcome) = when (type) {
                        CallLog.Calls.INCOMING_TYPE,
                        CallLog.Calls.ANSWERED_EXTERNALLY_TYPE,
                        CallLog.Calls.VOICEMAIL_TYPE,
                        -> CallDirection.INCOMING to CallOutcome.ENDED
                        CallLog.Calls.MISSED_TYPE -> CallDirection.INCOMING to CallOutcome.MISSED_INCOMING
                        CallLog.Calls.REJECTED_TYPE,
                        CallLog.Calls.BLOCKED_TYPE,
                        -> CallDirection.INCOMING to CallOutcome.DECLINED_INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING to
                            if (durationMillis > 0) CallOutcome.ENDED else CallOutcome.UNANSWERED_OUTGOING
                        else -> continue
                    }
                    val number = cursor.getString(1)?.ifBlank { null }
                    val isPrivate = number == null &&
                        cursor.getInt(7) != CallLog.Calls.PRESENTATION_ALLOWED
                    val id = "log:${cursor.getLong(0)}"
                    entries += CallHistoryEntry(
                        id = id,
                        identity = CallerIdentity(
                            displayName = resolver.resolve(number) ?: cursor.getString(2)?.ifBlank { null },
                            number = number,
                            isPrivate = isPrivate,
                            photoUri = resolver.resolvePhotoUri(number).takeUnless { isPrivate },
                        ),
                        direction = direction,
                        outcome = outcome,
                        timestampMillis = cursor.getLong(4),
                        connectedDurationMillis = durationMillis.takeIf { it > 0 },
                        isOpened = id in opened || cursor.getInt(6) != 0,
                    )
                }
            }
        }.onFailure { return null }
        return entries
    }

    private fun openedIds(): Set<String> =
        preferences.getStringSet(KEY_OPENED, emptySet()).orEmpty().toSet()

    private fun writeLocal(entries: List<CallHistoryEntry>) {
        preferences.edit {
            putStringSet(
                KEY_HISTORY,
                entries.mapTo(mutableSetOf()) { entry ->
                    listOf(
                        entry.id,
                        entry.identity.displayName.orEmpty(),
                        entry.identity.number.orEmpty(),
                        entry.identity.isPrivate.toString(),
                        entry.direction.name,
                        entry.outcome.name,
                        entry.timestampMillis.toString(),
                        entry.connectedDurationMillis?.toString().orEmpty(),
                        entry.isOpened.toString(),
                        entry.identity.photoUri.orEmpty(),
                    ).joinToString(SEPARATOR) { Uri.encode(it) }
                },
            )
        }
    }

    private fun readLocal(): List<CallHistoryEntry> =
        preferences.getStringSet(KEY_HISTORY, emptySet()).orEmpty().mapNotNull { encoded ->
            val values = encoded.split(SEPARATOR).map(Uri::decode)
            if (values.size !in 9..10) return@mapNotNull null
            runCatching {
                CallHistoryEntry(
                    id = values[0],
                    identity = CallerIdentity(
                        displayName = values[1].ifBlank { null },
                        number = values[2].ifBlank { null },
                        isPrivate = values[3].toBoolean(),
                        photoUri = values.getOrNull(9)?.ifBlank { null },
                    ),
                    direction = CallDirection.valueOf(values[4]),
                    outcome = CallOutcome.valueOf(values[5]),
                    timestampMillis = values[6].toLong(),
                    connectedDurationMillis = values[7].toLongOrNull(),
                    isOpened = values[8].toBoolean(),
                )
            }.getOrNull()
        }.sortedByDescending { it.timestampMillis }

    companion object {
        private const val PREFERENCES = "call_history"
        private const val KEY_HISTORY = "history"
        private const val KEY_OPENED = "opened_ids"
        private const val SEPARATOR = "|"
        private const val MAX_ENTRIES = 200
    }
}

@Singleton
class AndroidElapsedRealtimeClock @Inject constructor() : ElapsedRealtimeClock {
    override fun now(): Long = SystemClock.elapsedRealtime()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CommunicationPlatformModule {
    @Binds
    abstract fun bindRoleGateway(gateway: AndroidDialerRoleGateway): DialerRoleGateway

    @Binds
    abstract fun bindTelecomGateway(gateway: AndroidTelecomGateway): TelecomGateway

    @Binds
    abstract fun bindContactsGateway(gateway: AndroidContactsGateway): ContactsGateway

    @Binds
    abstract fun bindAssignmentStore(
        store: SharedPreferencesPhoneAssignmentStore,
    ): PhoneAssignmentStore

    @Binds
    abstract fun bindHistoryStore(store: SharedPreferencesCallHistoryStore): CallHistoryStore

    @Binds
    abstract fun bindElapsedClock(clock: AndroidElapsedRealtimeClock): ElapsedRealtimeClock
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Call.id(): String = System.identityHashCode(this).toString()

private fun Call.direction(): CallDirection =
    if (details.callDirection == Call.Details.DIRECTION_INCOMING) {
        CallDirection.INCOMING
    } else {
        CallDirection.OUTGOING
    }

private fun CallerIdentity.localizedTitle(context: Context): String = when {
    isPrivate -> context.getString(R.string.comm_caller_private_number)
    !displayName.isNullOrBlank() -> displayName
    !number.isNullOrBlank() -> number
    else -> context.getString(R.string.comm_caller_unknown)
}

private fun Call.identity(): CallerIdentity {
    val handle = details.handle
    val presentation = details.handlePresentation
    val privateNumber = presentation == TelecomManager.PRESENTATION_RESTRICTED
    return CallerIdentity(
        displayName = details.callerDisplayName?.toString()?.ifBlank { null },
        number = if (privateNumber) null else handle?.schemeSpecificPart,
        isPrivate = privateNumber,
    )
}

private fun Call.capabilities(): CallCapabilities {
    val capabilities = details.callCapabilities
    return CallCapabilities(
        canMute = true,
        canChangeAudioRoute = true,
        canSendDtmf = true,
        canHold = capabilities and Call.Details.CAPABILITY_HOLD != 0,
        canSwap = capabilities and Call.Details.CAPABILITY_SWAP_CONFERENCE != 0,
        canMerge = capabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE != 0,
    )
}

private fun Int.toCallPhase(): CallPhase = when (this) {
    Call.STATE_DIALING, Call.STATE_CONNECTING -> CallPhase.DIALING
    Call.STATE_RINGING -> CallPhase.INCOMING
    Call.STATE_ACTIVE -> CallPhase.CONNECTED
    Call.STATE_HOLDING -> CallPhase.ON_HOLD
    Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> CallPhase.DISCONNECTED
    else -> CallPhase.RINGING
}

private fun Int?.toAudioRoute(): AudioRoute = when (this) {
    CallAudioState.ROUTE_EARPIECE -> AudioRoute.EARPIECE
    CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
    CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
    CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
    else -> AudioRoute.UNKNOWN
}

private object ContentResolverQuery {
    const val SELECTION = "android:query-arg-sql-selection"
    const val SELECTION_ARGS = "android:query-arg-sql-selection-args"
    const val SORT_COLUMNS = "android:query-arg-sort-columns"
    const val SORT_DIRECTION = "android:query-arg-sort-direction"
    const val SORT_ASCENDING = 0
    const val LIMIT = "android:query-arg-limit"
    const val OFFSET = "android:query-arg-offset"
}

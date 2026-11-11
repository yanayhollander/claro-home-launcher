package com.clarohome.feature.communication

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AndroidSmsRoleGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsRoleGateway {
    private val mutableQualification = MutableStateFlow(inspect())
    override val qualification: StateFlow<SmsQualification> =
        mutableQualification.asStateFlow()

    override fun createRoleRequest(): Intent? {
        val roleManager = context.getSystemService(RoleManager::class.java)
        return if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            null
        }
    }

    override suspend fun refresh() {
        mutableQualification.value = inspect()
    }

    private fun inspect(): SmsQualification {
        val roleManager = context.getSystemService(RoleManager::class.java)
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        val roleAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true
        val roleHeld = roleAvailable && roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        val telephonySupported = context.packageManager.hasSystemFeature(
            "android.hardware.telephony.messaging",
        ) && telephonyManager?.phoneType != TelephonyManager.PHONE_TYPE_NONE
        val defaultSubscription = SmsManager.getDefaultSmsSubscriptionId()
        val hasDefaultAccount =
            defaultSubscription != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        return SmsQualification(
            roleAvailable = roleAvailable,
            roleHeld = roleHeld,
            telephonySupported = telephonySupported,
            sendPermissionGranted = context.hasSmsPermission(Manifest.permission.SEND_SMS),
            readPermissionGranted = context.hasSmsPermission(Manifest.permission.READ_SMS),
            receivePermissionGranted =
                context.hasSmsPermission(Manifest.permission.RECEIVE_SMS) &&
                    context.hasSmsPermission(Manifest.permission.RECEIVE_MMS),
            notificationPermissionGranted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    context.hasSmsPermission(Manifest.permission.POST_NOTIFICATIONS),
            hasUsableDefaultAccount = hasDefaultAccount,
        )
    }
}

@Singleton
class AndroidMessageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : MessageRepository {
    private val mutableConversations = MutableStateFlow<List<MessageConversation>>(emptyList())
    override val conversations: StateFlow<List<MessageConversation>> =
        mutableConversations.asStateFlow()
    private val mutableUnreadCount = MutableStateFlow<Int?>(null)
    override val unreadCount: StateFlow<Int?> = mutableUnreadCount.asStateFlow()

    override suspend fun refresh(query: String) {
        if (!context.hasSmsPermission(Manifest.permission.READ_SMS)) {
            mutableConversations.value = emptyList()
            mutableUnreadCount.value = null
            return
        }
        val rows = linkedMapOf<Long, MutableConversation>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            SMS_PROJECTION,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val threadIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
            while (cursor.moveToNext()) {
                val threadId = cursor.getLong(threadIndex)
                val address = cursor.getString(addressIndex).orEmpty()
                val body = cursor.getString(bodyIndex).orEmpty()
                if (
                    query.isNotBlank() &&
                    !address.contains(query, ignoreCase = true) &&
                    !body.contains(query, ignoreCase = true)
                ) {
                    continue
                }
                val row = rows.getOrPut(threadId) {
                    MutableConversation(
                        id = threadId,
                        address = address,
                        snippet = body,
                        timestampMillis = cursor.getLong(dateIndex),
                    )
                }
                if (cursor.getInt(readIndex) == 0) row.unreadCount += 1
                row.providerIds += cursor.getLong(idIndex)
            }
        }
        appendMmsConversations(rows, query)
        val conversations = rows.values
            .map(MutableConversation::toConversation)
            .sortedByDescending(MessageConversation::timestampMillis)
        mutableConversations.value = conversations
        mutableUnreadCount.value = conversations.sumOf(MessageConversation::unreadCount)
    }

    override suspend fun messages(conversationId: Long): List<ClaroMessage> {
        if (!context.hasSmsPermission(Manifest.permission.READ_SMS)) return emptyList()
        val messages = linkedMapOf<String, ClaroMessage>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            SMS_PROJECTION_WITH_STATUS,
            "${Telephony.Sms.THREAD_ID}=?",
            arrayOf(conversationId.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
            val statusIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)
            while (cursor.moveToNext()) {
                val providerId = cursor.getLong(idIndex)
                val message = ClaroMessage(
                    providerId = providerId,
                    conversationId = conversationId,
                    clientMessageId = null,
                    direction = if (cursor.getInt(typeIndex) == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                        MessageDirection.INCOMING
                    } else {
                        MessageDirection.OUTGOING
                    },
                    senderOrRecipient = cursor.getString(addressIndex).orEmpty(),
                    body = cursor.getString(bodyIndex).orEmpty(),
                    timestampMillis = cursor.getLong(dateIndex),
                    isRead = cursor.getInt(readIndex) != 0,
                    deliveryState = cursor.getInt(statusIndex).toDeliveryState(),
                )
                messages["sms:$providerId"] = message
            }
        }
        appendMmsMessages(conversationId, messages)
        return messages.values.sortedBy(ClaroMessage::timestampMillis)
    }

    override suspend fun markConversationRead(conversationId: Long) {
        if (!context.isDefaultSmsApp()) return
        val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
        context.contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.READ}=0",
            arrayOf(conversationId.toString()),
        )
        val mmsValues = ContentValues().apply { put(Telephony.Mms.READ, 1) }
        context.contentResolver.update(
            Telephony.Mms.CONTENT_URI,
            mmsValues,
            "${Telephony.Mms.THREAD_ID}=? AND ${Telephony.Mms.READ}=0",
            arrayOf(conversationId.toString()),
        )
    }

    override suspend fun reconcileIncoming(messages: List<ClaroMessage>) {
        if (!context.isDefaultSmsApp()) return
        messages.distinctBy { it.providerId.takeIf { id -> id > 0 } ?: incomingFingerprint(it) }
            .forEach { message ->
                if (message.kind != MessageKind.SMS || incomingExists(message)) return@forEach
                context.contentResolver.insert(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, message.senderOrRecipient)
                        put(Telephony.Sms.BODY, message.body)
                        put(Telephony.Sms.DATE, message.timestampMillis)
                        put(Telephony.Sms.READ, if (message.isRead) 1 else 0)
                    },
                )
            }
        refresh()
    }

    override suspend fun updateOutgoingState(
        clientMessageId: String,
        providerId: Long?,
        state: MessageDeliveryState,
        failure: MessageFailure?,
    ) {
        if (providerId == null || !context.isDefaultSmsApp()) return
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, state.toProviderType())
            put(Telephony.Sms.STATUS, state.toProviderStatus())
            if (failure != null) put(Telephony.Sms.ERROR_CODE, failure.ordinal + 1)
        }
        context.contentResolver.update(
            Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, providerId.toString()),
            values,
            null,
            null,
        )
    }

    private fun appendMmsConversations(
        rows: MutableMap<Long, MutableConversation>,
        query: String,
    ) {
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            MMS_PROJECTION,
            null,
            null,
            "${Telephony.Mms.DATE} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
            val threadIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
            val subjectIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.SUBJECT)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)
            val readIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.READ)
            while (cursor.moveToNext()) {
                val subject = cursor.getString(subjectIndex).orEmpty()
                    .ifBlank { context.getString(R.string.comm_mms_fallback_subject) }
                if (query.isNotBlank() && !subject.contains(query, ignoreCase = true)) continue
                val threadId = cursor.getLong(threadIndex)
                val row = rows.getOrPut(threadId) {
                    MutableConversation(
                        id = threadId,
                        address = context.getString(R.string.comm_mms_group_or_multimedia),
                        snippet = subject,
                        timestampMillis = cursor.getLong(dateIndex) * 1_000,
                        kind = MessageKind.MMS,
                    )
                }
                row.kind = MessageKind.MMS
                if (cursor.getInt(readIndex) == 0) row.unreadCount += 1
                row.providerIds += cursor.getLong(idIndex)
            }
        }
    }

    private fun appendMmsMessages(
        conversationId: Long,
        messages: MutableMap<String, ClaroMessage>,
    ) {
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            MMS_PROJECTION_WITH_BOX,
            "${Telephony.Mms.THREAD_ID}=?",
            arrayOf(conversationId.toString()),
            "${Telephony.Mms.DATE} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
            val subjectIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.SUBJECT)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)
            val readIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.READ)
            val boxIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
            while (cursor.moveToNext()) {
                val providerId = cursor.getLong(idIndex)
                messages["mms:$providerId"] = ClaroMessage(
                    providerId = providerId,
                    conversationId = conversationId,
                    clientMessageId = null,
                    direction = if (cursor.getInt(boxIndex) == Telephony.Mms.MESSAGE_BOX_INBOX) {
                        MessageDirection.INCOMING
                    } else {
                        MessageDirection.OUTGOING
                    },
                    senderOrRecipient = context.getString(R.string.comm_mms_group_or_multimedia),
                    body = cursor.getString(subjectIndex).orEmpty().ifBlank {
                        context.getString(R.string.comm_mms_fallback_subject)
                    },
                    timestampMillis = cursor.getLong(dateIndex) * 1_000,
                    isRead = cursor.getInt(readIndex) != 0,
                    deliveryState = MessageDeliveryState.RECEIVED,
                    kind = MessageKind.MMS,
                    attachments = listOf(
                        MessageAttachment(
                            contentType = null,
                            displayName = null,
                            state = AttachmentState.DOWNLOAD_REQUIRED,
                        ),
                    ),
                )
            }
        }
    }

    private fun incomingExists(message: ClaroMessage): Boolean {
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.ADDRESS}=? AND ${Telephony.Sms.DATE}=? AND ${Telephony.Sms.BODY}=?",
            arrayOf(
                message.senderOrRecipient,
                message.timestampMillis.toString(),
                message.body,
            ),
            null,
        )?.use { return it.moveToFirst() }
        return false
    }

    private data class MutableConversation(
        val id: Long,
        val address: String,
        val snippet: String,
        val timestampMillis: Long,
        var unreadCount: Int = 0,
        var kind: MessageKind = MessageKind.SMS,
        val providerIds: MutableSet<Long> = linkedSetOf(),
    ) {
        fun toConversation(): MessageConversation = MessageConversation(
            id = id,
            recipient = MessageRecipient(address),
            snippet = snippet,
            timestampMillis = timestampMillis,
            unreadCount = unreadCount,
            kind = kind,
            groupSize = if (kind == MessageKind.MMS) 2 else 1,
        )
    }

    companion object {
        private val SMS_PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
        )
        private val SMS_PROJECTION_WITH_STATUS = SMS_PROJECTION + arrayOf(
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS,
        )
        private val MMS_PROJECTION = arrayOf(
            Telephony.Mms._ID,
            Telephony.Mms.THREAD_ID,
            Telephony.Mms.SUBJECT,
            Telephony.Mms.DATE,
            Telephony.Mms.READ,
        )
        private val MMS_PROJECTION_WITH_BOX = MMS_PROJECTION + Telephony.Mms.MESSAGE_BOX
    }
}

@Singleton
class SharedPreferencesMessageDraftStore @Inject constructor(
    @ApplicationContext context: Context,
) : MessageDraftStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override suspend fun get(id: String): MessageDraft? =
        preferences.getString(id, null)?.decodeDraft(id)

    override suspend fun save(draft: MessageDraft) {
        preferences.edit { putString(draft.id, draft.encode()) }
    }

    override suspend fun delete(id: String) {
        preferences.edit { remove(id) }
    }

    override suspend fun clearAll() {
        preferences.edit { clear() }
    }

    private fun MessageDraft.encode(): String = listOf(
        conversationId?.toString().orEmpty(),
        recipient?.number.orEmpty(),
        recipient?.displayName.orEmpty(),
        recipient?.contactLookupKey.orEmpty(),
        recipient?.label.orEmpty(),
        body,
        updatedAtMillis.toString(),
    ).joinToString(SEPARATOR) { it.base64Encode() }

    private fun String.decodeDraft(id: String): MessageDraft? = runCatching {
        val values = split(SEPARATOR).map(String::base64Decode)
        MessageDraft(
            id = id,
            conversationId = values[0].toLongOrNull(),
            recipient = values[1].takeIf(String::isNotBlank)?.let { number ->
                MessageRecipient(
                    number = number,
                    displayName = values[2].ifBlank { null },
                    contactLookupKey = values[3].ifBlank { null },
                    label = values[4].ifBlank { null },
                )
            },
            body = values[5],
            updatedAtMillis = values[6].toLongOrNull() ?: 0,
        )
    }.getOrNull()

    companion object {
        private const val PREFERENCES = "message_drafts"
        private const val SEPARATOR = "."
    }
}

@Singleton
class AndroidSmsTransport @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsTransport {
    override val events: StateFlow<SmsTransportEvent?> = SmsEventHub.events

    override suspend fun send(
        clientMessageId: String,
        recipient: String,
        body: String,
    ): SmsSubmissionResult {
        if (!context.hasSmsPermission(Manifest.permission.SEND_SMS)) {
            return SmsSubmissionResult.Rejected(MessageFailure.PERMISSION_DENIED)
        }
        val subscriptionId = SmsManager.getDefaultSmsSubscriptionId()
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return SmsSubmissionResult.NeedsDefaultAccountSetup(
                Intent("android.settings.WIRELESS_SETTINGS"),
            )
        }
        val providerUri = context.contentResolver.insert(
            Telephony.Sms.Outbox.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, recipient)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            },
        )
        val providerId = providerUri?.lastPathSegment?.toLongOrNull()
        return runCatching {
            val manager = context.smsManagerForSubscription(subscriptionId)
            val parts = manager.divideMessage(body)
            val sentIntents = parts.indices.map { index ->
                statusPendingIntent(
                    action = ACTION_SMS_SENT,
                    clientMessageId = clientMessageId,
                    providerId = providerId,
                    partIndex = index,
                    partCount = parts.size,
                )
            }
            val deliveryIntents = parts.indices.map { index ->
                statusPendingIntent(
                    action = ACTION_SMS_DELIVERED,
                    clientMessageId = clientMessageId,
                    providerId = providerId,
                    partIndex = index,
                    partCount = parts.size,
                )
            }
            manager.sendMultipartTextMessage(
                recipient,
                null,
                ArrayList(parts),
                ArrayList(sentIntents),
                ArrayList(deliveryIntents),
            )
            SmsSubmissionResult.Submitted(providerId)
        }.getOrElse {
            SmsSubmissionResult.Rejected(MessageFailure.UNKNOWN)
        }
    }

    private fun statusPendingIntent(
        action: String,
        clientMessageId: String,
        providerId: Long?,
        partIndex: Int,
        partCount: Int,
    ): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_CLIENT_ID, clientMessageId)
            putExtra(EXTRA_PROVIDER_ID, providerId ?: -1)
            putExtra(EXTRA_PART_INDEX, partIndex)
            putExtra(EXTRA_PART_COUNT, partCount)
        }
        return PendingIntent.getBroadcast(
            context,
            (clientMessageId.hashCode() * 31 + partIndex) xor action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val clientId = intent.getStringExtra(EXTRA_CLIENT_ID) ?: return
        val providerId = intent.getLongExtra(EXTRA_PROVIDER_ID, -1).takeIf { it >= 0 }
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
        val partCount = intent.getIntExtra(EXTRA_PART_COUNT, 1)
        SmsEventHub.recordPart(
            action = intent.action,
            clientMessageId = clientId,
            providerId = providerId,
            partIndex = partIndex,
            partCount = partCount,
            resultCode = resultCode,
        )
    }
}

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isEmpty()) return
        val address = parts.first().originatingAddress.orEmpty()
        val timestamp = parts.minOf { it.timestampMillis }
        val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
        val repository = AndroidMessageRepository(context.applicationContext)
        val message = ClaroMessage(
            providerId = 0,
            conversationId = 0,
            clientMessageId = null,
            direction = MessageDirection.INCOMING,
            senderOrRecipient = address,
            body = body,
            timestampMillis = timestamp,
            isRead = false,
            deliveryState = MessageDeliveryState.RECEIVED,
        )
        val pendingResult = goAsync()
        Thread {
            try {
                kotlinx.coroutines.runBlocking { repository.reconcileIncoming(listOf(message)) }
                showIncomingNotification(
                    context,
                    context.getString(R.string.comm_notification_new_sms_title),
                    context.getString(R.string.comm_notification_new_sms_text),
                )
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val pdu = intent.getByteArrayExtra("data") ?: return
        val id = UUID.randomUUID().toString()
        File(context.noBackupFilesDir, "mms-pdu-$id").writeBytes(pdu)
        context.contentResolver.insert(
            Telephony.Mms.Inbox.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Mms.DATE, System.currentTimeMillis() / 1_000)
                put(Telephony.Mms.READ, 0)
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
                put(
                    Telephony.Mms.SUBJECT,
                    context.getString(R.string.comm_mms_download_required_subject),
                )
            },
        )
        showIncomingNotification(
            context,
            context.getString(R.string.comm_notification_new_mms_title),
            context.getString(R.string.comm_notification_new_mms_text),
        )
    }
}

class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val destination = intent?.data?.schemeSpecificPart
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!destination.isNullOrBlank() && !body.isNullOrBlank()) {
            runCatching {
                val subscriptionId = SmsManager.getDefaultSmsSubscriptionId()
                smsManagerForSubscription(subscriptionId)
                    .sendTextMessage(destination, null, body, null, null)
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

class SmsSendToActivity : Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.action = intent?.action
            launchIntent.data = intent?.data
            launchIntent.putExtra(Intent.EXTRA_TEXT, intent?.getStringExtra(Intent.EXTRA_TEXT))
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(launchIntent)
        }
        finish()
    }
}

@Singleton
class AndroidMessagingClock @Inject constructor() : MessagingClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

@Singleton
class PrivacySafeMessagingLogger @Inject constructor() : SensitiveDataSafeLogger {
    private val mutableEvents = mutableListOf<Pair<String, String>>()
    val events: List<Pair<String, String>> get() = synchronized(mutableEvents) {
        mutableEvents.toList()
    }

    override fun event(name: String, outcome: String) {
        require(name.matches(SAFE_TOKEN))
        require(outcome.matches(SAFE_TOKEN))
        synchronized(mutableEvents) { mutableEvents += name to outcome }
    }

    companion object {
        private val SAFE_TOKEN = Regex("[a-z0-9_]+")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MessagingPlatformModule {
    @Binds
    abstract fun bindSmsRoleGateway(gateway: AndroidSmsRoleGateway): SmsRoleGateway

    @Binds
    abstract fun bindMessageRepository(repository: AndroidMessageRepository): MessageRepository

    @Binds
    abstract fun bindMessageDraftStore(
        store: SharedPreferencesMessageDraftStore,
    ): MessageDraftStore

    @Binds
    abstract fun bindSmsTransport(transport: AndroidSmsTransport): SmsTransport

    @Binds
    abstract fun bindMessagingClock(clock: AndroidMessagingClock): MessagingClock

    @Binds
    abstract fun bindMessagingLogger(logger: PrivacySafeMessagingLogger): SensitiveDataSafeLogger
}

private object SmsEventHub {
    private val mutableEvents = MutableStateFlow<SmsTransportEvent?>(null)
    val events: StateFlow<SmsTransportEvent?> = mutableEvents.asStateFlow()
    private val sentParts = mutableMapOf<String, MutableSet<Int>>()
    private val deliveredParts = mutableMapOf<String, MutableSet<Int>>()
    private val terminalFailures = mutableSetOf<String>()

    @Synchronized
    fun recordPart(
        action: String?,
        clientMessageId: String,
        providerId: Long?,
        partIndex: Int,
        partCount: Int,
        resultCode: Int,
    ) {
        if (clientMessageId in terminalFailures) return
        if (action == ACTION_SMS_SENT && resultCode != Activity.RESULT_OK) {
            terminalFailures += clientMessageId
            mutableEvents.value = SmsTransportEvent.Failed(
                clientMessageId = clientMessageId,
                providerId = providerId,
                failure = resultCode.toMessageFailure(),
                outcomeMayBeUnknown = resultCode == SmsManager.RESULT_ERROR_GENERIC_FAILURE,
            )
            return
        }
        if (action == ACTION_SMS_DELIVERED && resultCode != Activity.RESULT_OK) {
            terminalFailures += clientMessageId
            mutableEvents.value = SmsTransportEvent.Failed(
                clientMessageId = clientMessageId,
                providerId = providerId,
                failure = MessageFailure.UNKNOWN,
                outcomeMayBeUnknown = true,
            )
            return
        }
        val parts = if (action == ACTION_SMS_DELIVERED) deliveredParts else sentParts
        val completed = parts.getOrPut(clientMessageId) { linkedSetOf() }
        completed += partIndex
        if (completed.size == partCount) {
            parts.remove(clientMessageId)
            mutableEvents.value = if (action == ACTION_SMS_DELIVERED) {
                SmsTransportEvent.Delivered(clientMessageId, providerId)
            } else {
                SmsTransportEvent.Sent(clientMessageId, providerId)
            }
        }
    }
}

private fun showIncomingNotification(context: Context, title: String, text: String) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !context.hasSmsPermission(Manifest.permission.POST_NOTIFICATIONS)
    ) {
        return
    }
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            MESSAGE_CHANNEL,
            context.getString(R.string.comm_channel_messages_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.comm_channel_messages_description)
        },
    )
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    manager.notify(
        NEW_MESSAGE_NOTIFICATION,
        NotificationCompat.Builder(context, MESSAGE_CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build(),
    )
}

private fun Context.hasSmsPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.isDefaultSmsApp(): Boolean {
    val roleManager = getSystemService(RoleManager::class.java)
    return roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true &&
        roleManager.isRoleHeld(RoleManager.ROLE_SMS)
}

@Suppress("DEPRECATION")
private fun Context.smsManagerForSubscription(subscriptionId: Int): SmsManager =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
    } else {
        SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
    }

private fun String.base64Encode(): String = Base64.encodeToString(
    toByteArray(StandardCharsets.UTF_8),
    Base64.NO_WRAP or Base64.URL_SAFE,
)

private fun String.base64Decode(): String = String(
    Base64.decode(this, Base64.NO_WRAP or Base64.URL_SAFE),
    StandardCharsets.UTF_8,
)

private fun incomingFingerprint(message: ClaroMessage): Long =
    listOf(message.senderOrRecipient, message.timestampMillis, message.body).hashCode().toLong()

private fun Int.toDeliveryState(): MessageDeliveryState = when (this) {
    Telephony.Sms.STATUS_COMPLETE -> MessageDeliveryState.DELIVERY_CONFIRMED
    Telephony.Sms.STATUS_FAILED -> MessageDeliveryState.KNOWN_FAILURE
    Telephony.Sms.STATUS_PENDING -> MessageDeliveryState.PENDING
    else -> MessageDeliveryState.SENT
}

private fun MessageDeliveryState.toProviderType(): Int = when (this) {
    MessageDeliveryState.KNOWN_FAILURE -> Telephony.Sms.MESSAGE_TYPE_FAILED
    MessageDeliveryState.PENDING -> Telephony.Sms.MESSAGE_TYPE_OUTBOX
    else -> Telephony.Sms.MESSAGE_TYPE_SENT
}

private fun MessageDeliveryState.toProviderStatus(): Int = when (this) {
    MessageDeliveryState.DELIVERY_CONFIRMED -> Telephony.Sms.STATUS_COMPLETE
    MessageDeliveryState.KNOWN_FAILURE -> Telephony.Sms.STATUS_FAILED
    MessageDeliveryState.PENDING -> Telephony.Sms.STATUS_PENDING
    else -> Telephony.Sms.STATUS_NONE
}

private fun Int.toMessageFailure(): MessageFailure = when (this) {
    SmsManager.RESULT_ERROR_NO_SERVICE -> MessageFailure.NO_SERVICE
    SmsManager.RESULT_ERROR_RADIO_OFF -> MessageFailure.NO_SERVICE
    SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> MessageFailure.CARRIER_REJECTED
    SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED,
    SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
    -> MessageFailure.CARRIER_REJECTED

    else -> MessageFailure.UNKNOWN
}

internal const val ACTION_SMS_SENT = "com.clarohome.messaging.SMS_SENT"
internal const val ACTION_SMS_DELIVERED = "com.clarohome.messaging.SMS_DELIVERED"
private const val EXTRA_CLIENT_ID = "client_message_id"
private const val EXTRA_PROVIDER_ID = "provider_id"
private const val EXTRA_PART_INDEX = "part_index"
private const val EXTRA_PART_COUNT = "part_count"
private const val MESSAGE_CHANNEL = "messages"
private const val NEW_MESSAGE_NOTIFICATION = 2100

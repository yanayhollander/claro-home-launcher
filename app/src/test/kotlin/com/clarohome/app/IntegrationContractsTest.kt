package com.clarohome.app

import com.clarohome.core.model.BadgeValue
import com.clarohome.core.model.CriticalActivity
import com.clarohome.core.model.HomeSnapshot
import com.clarohome.core.model.HomeTile
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.HomeRoleState
import com.clarohome.core.model.TileAccent
import com.clarohome.core.model.BatteryStatus
import com.clarohome.core.model.CellularStatus
import com.clarohome.core.model.CellularAvailability
import com.clarohome.feature.communication.CallHistoryEntry
import com.clarohome.feature.communication.CallerIdentity
import com.clarohome.feature.communication.CallDirection
import com.clarohome.feature.communication.CallOutcome
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.DialerQualification
import com.clarohome.feature.communication.MessagingUiState
import com.clarohome.feature.communication.MessageConversation
import com.clarohome.feature.communication.MessageRecipient
import com.clarohome.feature.communication.SmsQualification
import org.junit.Assert.assertEquals
import org.junit.Test

class IntegrationContractsTest {
    @Test
    fun onboardingSuggestionsRankRecentCallsAndChooseTheRecentlyUsedNumber() {
        val mira = ClaroContact("mira", "Mira", null, listOf(
            ContactNumber("202-555-0101", "Mobile"),
            ContactNumber("202-555-0102", "Home"),
        ))
        val daniel = ClaroContact("daniel", "Daniel", null, listOf(ContactNumber("2025550103", "Mobile")))
        val history = listOf(
            CallHistoryEntry("old", CallerIdentity(null, "2025550101"), CallDirection.OUTGOING,
                CallOutcome.ENDED, 1_000, null),
            CallHistoryEntry("new", CallerIdentity(null, "2025550102"), CallDirection.OUTGOING,
                CallOutcome.ENDED, 3_000, null),
            CallHistoryEntry("other", CallerIdentity(null, "2025550103"), CallDirection.INCOMING,
                CallOutcome.MISSED_INCOMING, 2_000, null),
        )
        val suggestions = suggestedOnboardingContacts(listOf(daniel, mira), history)
        assertEquals(listOf("mira", "daniel"), suggestions.map { it.contact.lookupKey })
        assertEquals("2025550102", suggestions.first().key)
    }

    @Test
    fun onboardingSuggestionsFallBackWithoutHistoryAndExcludeDuplicateOrEmptyNumbers() {
        fun contact(id: String, number: String) =
            ClaroContact(id, id, null, listOf(ContactNumber(number, "Mobile")))
        val suggestions = suggestedOnboardingContacts(listOf(
            contact("empty", ""),
            contact("first", "202-555-0101"),
            contact("duplicate", "2025550101"),
            contact("second", "2025550102"),
            contact("third", "2025550103"),
            contact("fourth", "2025550104"),
        ), emptyList())
        assertEquals(listOf("first", "second", "third"), suggestions.map { it.contact.lookupKey })
    }

    @Test
    fun menuAndHomeUseRealCountsAndClearThemAfterItemsAreReviewed() {
        val tiles = listOf(
            HomeTile("phone", "Phone", TileAccent.GREEN, HomeTileKind.PHONE, BadgeValue.Known(1)),
            HomeTile("messages", "Messages", TileAccent.BLUE, HomeTileKind.MESSAGES, BadgeValue.Known(1)),
        )
        val home = HomeSnapshot(
            greeting = "",
            dayAndDate = "",
            time = "",
            battery = BatteryStatus(80, false),
            cellular = CellularStatus(CellularAvailability.UNKNOWN),
            homeSlots = tiles,
            tiles = tiles,
            installedApps = emptyList(),
            homeRole = HomeRoleState.GRANTED,
        )
        val phone = CommunicationUiState(
            qualification = DialerQualification(true, true, true, true, true, true, true),
            history = listOf("missed-1", "missed-2").map { id ->
                CallHistoryEntry(id, CallerIdentity("Mira", "2025550101"), CallDirection.INCOMING,
                    CallOutcome.MISSED_INCOMING, 1_000, null)
            },
        )
        val messages = MessagingUiState(
            qualification = SmsQualification(true, true, true, true, true, true, true, true),
            conversations = listOf(MessageConversation(7, MessageRecipient("2025550101"), "Hello", 1_000, 3)),
        )
        val unread = home.withCommunicationProjections(phone, messages)
        assertEquals(listOf(BadgeValue.Known(2), BadgeValue.Known(3)), unread.tiles.map { it.badge })
        assertEquals(unread.tiles.map { it.badge }, unread.homeTiles.map { it.badge })

        val reviewed = home.withCommunicationProjections(
            phone.copy(history = phone.history.map { it.copy(isOpened = true) }),
            messages.copy(conversations = messages.conversations.map { it.copy(unreadCount = 0) }),
        )
        assertEquals(listOf(BadgeValue.Known(0), BadgeValue.Known(0)), reviewed.tiles.map { it.badge })
        assertEquals(reviewed.tiles.map { it.badge }, reviewed.homeTiles.map { it.badge })
    }

    @Test
    fun unavailableProjectionRemainsUnknownInsteadOfFalseZero() {
        assertEquals(BadgeValue.Unknown, projectedBadge(null))
        assertEquals(BadgeValue.Known(0), projectedBadge(0))
    }

    @Test
    fun modeSwitchDefersForEveryIntegratedCriticalFlow() {
        assertEquals(
            setOf(
                CriticalActivity.ACTIVE_CALL,
                CriticalActivity.SOS,
                CriticalActivity.PURCHASE,
            ),
            integrationBlockers(
                hasActiveCall = true,
                sosCritical = true,
                purchaseCritical = true,
            ),
        )
    }
}

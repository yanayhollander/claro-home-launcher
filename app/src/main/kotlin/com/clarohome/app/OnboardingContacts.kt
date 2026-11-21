package com.clarohome.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.design.ClaroPrimaryButton
import com.clarohome.core.design.ClaroRowButton
import com.clarohome.core.design.ClaroSecondaryButton
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.HomeTileKind
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.ReadinessCapability
import com.clarohome.core.model.flowIndex
import com.clarohome.feature.communication.CallHistoryEntry
import com.clarohome.feature.communication.ClaroContact
import com.clarohome.feature.communication.CommunicationUiState
import com.clarohome.feature.communication.ContactNumber
import com.clarohome.feature.communication.PhoneContactsCoordinator.Companion.normalizeDialString
import com.clarohome.feature.onboarding.settings.OnboardingCallbacks
import com.clarohome.feature.onboarding.settings.OnboardingFlow
import com.clarohome.feature.onboarding.settings.OnboardingStepScaffold
import com.clarohome.feature.safety.media.apps.SosReadiness

internal data class OnboardingContactChoice(val contact: ClaroContact, val number: ContactNumber) {
    val key: String get() = onboardingNumberKey(number.value)
}

internal fun onboardingNumberKey(number: String): String = normalizeDialString(number)

internal fun suggestedOnboardingContacts(
    contacts: List<ClaroContact>,
    history: List<CallHistoryEntry>,
): List<OnboardingContactChoice> {
    val recent = history.filter { !it.identity.isPrivate }
        .groupBy { onboardingNumberKey(it.identity.number.orEmpty()) }
        .mapValues { (_, calls) -> calls.maxOf { it.timestampMillis } }
    return contacts.mapNotNull { contact ->
        contact.numbers.filter { onboardingNumberKey(it.value).isNotBlank() }
            .maxByOrNull { recent[onboardingNumberKey(it.value)] ?: Long.MIN_VALUE }
            ?.let { OnboardingContactChoice(contact, it) }
    }.distinctBy { it.contact.lookupKey }
        .sortedByDescending { recent[it.key] ?: Long.MIN_VALUE }
        .distinctBy { it.key }
        .take(3)
}

@Composable
internal fun AppOnboardingFlow(
    state: MainUiState,
    communication: CommunicationUiState,
    viewModel: MainViewModel,
    communicationViewModel: CommunicationViewModel,
    defaultAppReadiness: Map<ReadinessCapability, CapabilityStatus>,
    callbacks: OnboardingCallbacks,
) {
    val context = LocalContext.current
    val contactsAllowed = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED
    val step = state.preferences.onboarding.currentStep
    LaunchedEffect(step, contactsAllowed) {
        if (contactsAllowed && step in setOf(OnboardingStep.PEOPLE_AND_SPEED_DIAL, OnboardingStep.SOS_CONTACT)) {
            communicationViewModel.searchContacts("")
        }
    }
    val suggestions = if (communication.contactQuery.isBlank()) {
        suggestedOnboardingContacts(communication.contacts, communication.history)
    } else emptyList()
    val selectedNumbers = state.home?.homeSlots.orEmpty()
        .filter { it?.kind == HomeTileKind.CONTACT }
        .mapNotNull { it?.phoneNumber?.let(::onboardingNumberKey) }.toSet()
    val sos by viewModel.sosState.collectAsState()
    val loadContacts = { callbacks.onRequestCapability(ReadinessCapability.CONTACTS) }

    OnboardingFlow(
        preferences = state.preferences,
        message = state.message,
        callbacks = callbacks,
        homeTileKinds = state.home?.homeTiles.orEmpty().map { it.kind }.toSet(),
        defaultAppReadiness = defaultAppReadiness,
        installedApps = state.home?.installedApps.orEmpty(),
        homeAppPackages = state.home?.homeSlots.orEmpty().mapNotNull { it?.packageName }.toSet(),
        peopleStep = { onBack, onContinue ->
            var picking by rememberSaveable { mutableStateOf(false) }
            val closePicker = {
                picking = false
                communicationViewModel.searchContacts("")
            }
            if (picking) {
                OnboardingContactPicker(
                    communication = communication,
                    contactsAllowed = contactsAllowed,
                    selectedNumbers = selectedNumbers,
                    multiple = true,
                    message = state.message,
                    onSearch = communicationViewModel::searchContacts,
                    onLoadMore = communicationViewModel::loadMoreContacts,
                    onAllowContacts = loadContacts,
                    onChoose = { choice ->
                        viewModel.setOnboardingVipContact(choice, choice.key !in selectedNumbers)
                    },
                    onDone = closePicker,
                )
            } else {
                OnboardingStepScaffold(
                    title = stringResource(R.string.app_onboarding_vips_title),
                    stepIndex = OnboardingStep.PEOPLE_AND_SPEED_DIAL.flowIndex,
                    message = state.message,
                    onBack = onBack,
                    dock = {
                        ClaroPrimaryButton(label = stringResource(R.string.app_continue), onClick = onContinue)
                    },
                ) {
                    Text(stringResource(R.string.app_onboarding_vips_body))
                    ContactSuggestions(
                        suggestions = suggestions,
                        contactsAllowed = contactsAllowed,
                        loading = communication.contactsLoading,
                        selectedNumbers = selectedNumbers,
                        multiple = true,
                        onChoose = { viewModel.setOnboardingVipContact(it, it.key !in selectedNumbers) },
                        onAllowContacts = loadContacts,
                    )
                    ClaroSecondaryButton(
                        label = stringResource(R.string.app_onboarding_select_contacts),
                        onClick = { picking = true },
                    )
                    ClaroInfoCard(stringResource(R.string.app_onboarding_vips_saved, selectedNumbers.size))
                }
            }
        },
        sosStep = { onBack, onContinue ->
            val existing = sos.trustedContactOrNull()
            var name by rememberSaveable(existing?.destination) { mutableStateOf(existing?.displayName.orEmpty()) }
            var number by rememberSaveable(existing?.destination) { mutableStateOf(existing?.destination.orEmpty()) }
            var lookupKey by rememberSaveable(existing?.destination) { mutableStateOf(existing?.contactId) }
            var photo by rememberSaveable(existing?.destination) { mutableStateOf(existing?.photoUri) }
            var agreed by rememberSaveable(existing?.destination) { mutableStateOf(existing?.recipientConsented == true) }
            var picking by rememberSaveable { mutableStateOf(false) }
            var error by rememberSaveable { mutableStateOf<String?>(null) }
            val choose: (OnboardingContactChoice) -> Unit = {
                name = it.contact.displayName
                number = it.number.value
                lookupKey = it.contact.lookupKey
                photo = it.contact.photoUri
                agreed = false
                error = null
                picking = false
                communicationViewModel.searchContacts("")
            }
            if (picking) {
                OnboardingContactPicker(
                    communication = communication,
                    contactsAllowed = contactsAllowed,
                    selectedNumbers = setOf(onboardingNumberKey(number)),
                    multiple = false,
                    onSearch = communicationViewModel::searchContacts,
                    onLoadMore = communicationViewModel::loadMoreContacts,
                    onAllowContacts = loadContacts,
                    onChoose = choose,
                    onDone = {
                        picking = false
                        communicationViewModel.searchContacts("")
                    },
                )
            } else {
                OnboardingStepScaffold(
                    title = stringResource(R.string.app_sos_contact_title),
                    stepIndex = OnboardingStep.SOS_CONTACT.flowIndex,
                    message = error ?: state.message,
                    onBack = onBack,
                    dock = {
                        ClaroPrimaryButton(
                            label = stringResource(R.string.app_onboarding_finish),
                            enabled = number.isNotBlank() && agreed,
                            onClick = {
                                val result = viewModel.configureSos(name, number, agreed, lookupKey, photo)
                                if (result == SosReadiness.Ready) onContinue()
                                else error = result.configurationError(context.resources)
                            },
                        )
                        if (existing == null) {
                            ClaroSecondaryButton(
                                label = stringResource(R.string.app_onboarding_skip_sos),
                                onClick = onContinue,
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.app_onboarding_sos_body))
                    ContactSuggestions(
                        suggestions = suggestions,
                        contactsAllowed = contactsAllowed,
                        loading = communication.contactsLoading,
                        selectedNumbers = setOf(onboardingNumberKey(number)),
                        multiple = false,
                        onChoose = choose,
                        onAllowContacts = loadContacts,
                    )
                    ClaroSecondaryButton(
                        label = stringResource(R.string.app_choose_from_contacts),
                        onClick = { picking = true },
                    )
                    if (number.isNotBlank()) {
                        ClaroInfoCard("$name\n\u2066$number\u2069")
                        ClaroRowButton(
                            title = stringResource(R.string.app_sos_recipient_agreed),
                            leading = { Checkbox(checked = agreed, onCheckedChange = null) },
                            modifier = Modifier.semantics { selected = agreed },
                            onClick = { agreed = !agreed },
                        )
                    }
                    ClaroSecondaryButton(
                        label = stringResource(R.string.app_onboarding_sos_manual),
                        onClick = callbacks.onOpenSosSetup,
                    )
                    ClaroInfoCard(
                        stringResource(R.string.app_sos_settings_warning),
                        tone = ClaroBannerTone.WARNING,
                    )
                }
            }
        },
    )
}

@Composable
private fun ContactSuggestions(
    suggestions: List<OnboardingContactChoice>,
    contactsAllowed: Boolean,
    loading: Boolean,
    selectedNumbers: Set<String>,
    multiple: Boolean,
    onChoose: (OnboardingContactChoice) -> Unit,
    onAllowContacts: () -> Unit,
) {
    if (!contactsAllowed) {
        ClaroInfoCard(stringResource(R.string.app_no_contacts_available))
        ClaroSecondaryButton(label = stringResource(R.string.app_load_contacts), onClick = onAllowContacts)
    } else if (loading) {
        Text(stringResource(R.string.app_onboarding_loading_contacts))
    } else if (suggestions.isEmpty()) {
        ClaroInfoCard(stringResource(R.string.app_no_contacts_available))
    } else {
        Text(stringResource(R.string.app_onboarding_suggestions))
        suggestions.forEach { choice ->
            OnboardingContactRow(choice, choice.key in selectedNumbers, multiple) { onChoose(choice) }
        }
    }
}

@Composable
internal fun OnboardingContactPicker(
    communication: CommunicationUiState,
    contactsAllowed: Boolean,
    selectedNumbers: Set<String>,
    multiple: Boolean,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onAllowContacts: () -> Unit,
    onChoose: (OnboardingContactChoice) -> Unit,
    onDone: () -> Unit,
    message: String? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(contactsAllowed) { if (contactsAllowed) onSearch(query) }
    BackHandler(onBack = onDone)
    val choices = communication.contacts.flatMap { contact ->
        contact.numbers.filter { onboardingNumberKey(it.value).isNotBlank() }
            .map { OnboardingContactChoice(contact, it) }
    }.distinctBy { it.key }
    OnboardingStepScaffold(
        title = stringResource(
            if (multiple) R.string.app_onboarding_select_contacts else R.string.app_choose_from_contacts,
        ),
        stepIndex = null,
        scrollable = false,
        message = message,
        onBack = onDone,
        dock = { ClaroPrimaryButton(label = stringResource(R.string.app_done), onClick = onDone) },
    ) {
        if (multiple) Text(stringResource(R.string.app_onboarding_vips_saved, selectedNumbers.size))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; if (contactsAllowed) onSearch(it) },
            label = { Text(stringResource(R.string.app_search)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!contactsAllowed) {
                item {
                    ClaroInfoCard(stringResource(R.string.app_no_contacts_available))
                    ClaroSecondaryButton(label = stringResource(R.string.app_load_contacts), onClick = onAllowContacts)
                }
            } else {
                items(choices, key = { it.key }) { choice ->
                    OnboardingContactRow(choice, choice.key in selectedNumbers, multiple) { onChoose(choice) }
                }
                if (communication.contactsLoading) {
                    item { Text(stringResource(R.string.app_onboarding_loading_contacts)) }
                } else if (choices.isEmpty()) {
                    item { ClaroInfoCard(stringResource(R.string.app_no_contacts_match_search)) }
                }
                if (!communication.contactsLoading && communication.contactsNextOffset != null) {
                    item {
                        ClaroSecondaryButton(
                            label = stringResource(R.string.app_sos_load_more_contacts),
                            onClick = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingContactRow(
    choice: OnboardingContactChoice,
    checked: Boolean,
    multiple: Boolean,
    onClick: () -> Unit,
) {
    ClaroRowButton(
        title = choice.contact.displayName,
        subtitle = "\u2066${choice.number.label} · ${choice.number.value}\u2069",
        leading = {
            ClaroAvatar(
                choice.contact.displayName,
                photoUri = choice.contact.photoUri,
                contactLookupKey = choice.contact.lookupKey,
                size = 44.dp,
            )
        },
        trailing = {
            if (multiple) Checkbox(checked = checked, onCheckedChange = null)
            else RadioButton(selected = checked, onClick = null)
        },
        modifier = Modifier.semantics { selected = checked },
        onClick = onClick,
    )
}

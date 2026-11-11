package com.clarohome.feature.onboarding.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.model.AppLanguage
import com.clarohome.core.model.CapabilityStatus
import com.clarohome.core.model.ClaroPreferences
import com.clarohome.core.model.HandoverCheck
import com.clarohome.core.model.OnboardingStep
import com.clarohome.core.model.ReadinessCapability
import com.clarohome.core.model.SettingsAccessMode
import com.clarohome.core.model.TextSize
import com.clarohome.core.model.VisualMode

data class OnboardingCallbacks(
    val onContinue: (OnboardingStep) -> Unit,
    val onChooseMode: (VisualMode) -> Unit,
    val onLanguageDisplay: (AppLanguage, TextSize, Boolean) -> Unit,
    val onUseDateQuestion: () -> Unit,
    val onConfigurePin: (String, String, String) -> Unit,
    val onAssignments: (Int, Boolean, Boolean, Boolean) -> Unit,
    val onReadiness: (ReadinessCapability, CapabilityStatus) -> Unit,
    val onHandover: (HandoverCheck, Boolean) -> Unit,
)

@Composable
fun OnboardingFlow(
    preferences: ClaroPreferences,
    message: String?,
    callbacks: OnboardingCallbacks,
) {
    val step = preferences.onboarding.currentStep
    val title = if (step == OnboardingStep.WELCOME) {
        stringResource(R.string.onboarding_welcome)
    } else {
        step.title()
    }
    ClaroScreen(title = title, message = message) {
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep {
                callbacks.onContinue(OnboardingStep.PHONE_STYLE)
            }
            OnboardingStep.PHONE_STYLE -> PhoneStyleChoice(
                selected = preferences.visualMode,
                onSelected = callbacks.onChooseMode,
                onContinue = { callbacks.onContinue(OnboardingStep.LANGUAGE_AND_DISPLAY) },
            )
            OnboardingStep.LANGUAGE_AND_DISPLAY -> LanguageDisplayStep(
                preferences = preferences,
                onSave = { language, size, contrast ->
                    callbacks.onLanguageDisplay(language, size, contrast)
                    callbacks.onContinue(OnboardingStep.SETTINGS_ACCESS)
                },
            )
            OnboardingStep.SETTINGS_ACCESS -> AccessSetupStep(
                accessMode = preferences.accessProtection.mode,
                onDateQuestion = {
                    callbacks.onUseDateQuestion()
                    callbacks.onContinue(OnboardingStep.PEOPLE_AND_SPEED_DIAL)
                },
                onPin = { pin, confirmation, recovery ->
                    callbacks.onConfigurePin(pin, confirmation, recovery)
                },
            )
            OnboardingStep.PEOPLE_AND_SPEED_DIAL -> PeopleSetupStep(
                preferences = preferences,
                onSave = { count, one, two ->
                    callbacks.onAssignments(
                        count,
                        one,
                        two,
                        preferences.onboarding.sosRecipientAgreed,
                    )
                    callbacks.onContinue(OnboardingStep.SOS_CONTACT)
                },
            )
            OnboardingStep.SOS_CONTACT -> SosSetupStep(
                agreed = preferences.onboarding.sosRecipientAgreed,
                onSave = { agreed ->
                    callbacks.onAssignments(
                        preferences.onboarding.favoriteSlotsAssigned,
                        preferences.onboarding.speedDialOneAssigned,
                        preferences.onboarding.speedDialTwoAssigned,
                        agreed,
                    )
                    callbacks.onContinue(OnboardingStep.ROLE_READINESS)
                },
            )
            OnboardingStep.ROLE_READINESS -> ReadinessChecklist(
                readiness = preferences.onboarding.readiness,
                onStatus = callbacks.onReadiness,
                onContinue = { callbacks.onContinue(OnboardingStep.HANDOVER) },
            )
            OnboardingStep.HANDOVER -> HandoverChecklist(
                checked = preferences.onboarding.handoverChecks,
                onChecked = callbacks.onHandover,
                onFinish = { callbacks.onContinue(OnboardingStep.COMPLETE) },
            )
            OnboardingStep.COMPLETE -> Unit
        }
    }
}

@Composable
fun AccessGateScreen(
    mode: SettingsAccessMode,
    message: String?,
    onUnlock: (String) -> Unit,
    onCancel: () -> Unit,
    onRecoverPin: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    ClaroScreen(
        title = if (mode == SettingsAccessMode.DATE_QUESTION) "Today's date?" else "Caregiver PIN",
        message = message,
    ) {
        Text(
            if (mode == SettingsAccessMode.DATE_QUESTION) {
                "Enter today's date in dd/MM/YYYY format. This prevents accidental changes; it is not a secure lock."
            } else {
                "Enter the 6-digit caregiver PIN."
            },
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(if (mode == SettingsAccessMode.DATE_QUESTION) "dd/MM/YYYY" else "PIN")
            },
            visualTransformation =
                if (mode == SettingsAccessMode.CAREGIVER_PIN) PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
            singleLine = true,
        )
        PrimaryButton(stringResource(R.string.unlock)) { onUnlock(input) }
        if (mode == SettingsAccessMode.CAREGIVER_PIN) {
            OutlinedButton(
                onClick = onRecoverPin,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ClaroDimensions.SecondaryTarget),
            ) {
                Text("Recover PIN")
            }
        }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ClaroDimensions.PrimaryTarget),
        ) {
            Text("Cancel")
        }
    }
}

@Composable
fun PinRecoveryScreen(
    message: String?,
    onRecover: (String, String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var recovery by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    ClaroScreen(title = "Recover caregiver PIN", message = message) {
        Text("Use the recovery secret stored during setup. There is no master code or date fallback.")
        SecretField("Recovery secret", recovery) { recovery = it }
        SecretField("New 6-digit PIN", pin) { pin = it }
        SecretField("Confirm new PIN", confirmation) { confirmation = it }
        PrimaryButton("Set new PIN") { onRecover(recovery, pin, confirmation) }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ClaroDimensions.PrimaryTarget),
        ) {
            Text("Cancel")
        }
        Text(
            "If both the PIN and recovery secret are lost, use Android reset/reconfiguration. " +
                "That removes Claro local setup data.",
        )
    }
}

@Composable
fun SettingsHubScreen(
    preferences: ClaroPreferences,
    onPhoneStyle: () -> Unit,
    onDisplayLanguage: () -> Unit,
    onAccessMethod: () -> Unit,
    onReadiness: () -> Unit,
    onHomeAndApps: () -> Unit,
    onPeopleAndSafety: () -> Unit,
    onMembershipPrivacy: () -> Unit,
    onLockAndExit: () -> Unit,
) {
    ClaroScreen(title = stringResource(R.string.settings)) {
        SettingsRow(
            title = stringResource(R.string.phone_style),
            detail = preferences.visualMode.localizedDisplayName(),
            onClick = onPhoneStyle,
        )
        SettingsRow(
            title = "Language and display",
            detail = "${preferences.language.displayName()} · ${preferences.textSize.displayName()}",
            onClick = onDisplayLanguage,
        )
        SettingsRow(
            title = "Settings access",
            detail = if (preferences.accessProtection.mode == SettingsAccessMode.DATE_QUESTION) {
                "Today's date question"
            } else {
                "Caregiver PIN with local recovery"
            },
            onClick = onAccessMethod,
        )
        SettingsRow(
            title = "Role readiness",
            detail = readinessSummary(preferences),
            onClick = onReadiness,
        )
        SettingsRow(
            title = "Home and apps",
            detail = "Allowed apps and additional Home tiles",
            onClick = onHomeAndApps,
        )
        SettingsRow(
            title = "People and safety",
            detail = "${preferences.onboarding.favoriteSlotsAssigned}/4 people · " +
                if (preferences.onboarding.sosRecipientAgreed) "SOS agreed" else "SOS needs setup",
            onClick = onPeopleAndSafety,
        )
        SettingsRow(
            "Membership and privacy",
            "Core features remain free · diagnostics off by default",
            onClick = onMembershipPrivacy,
        )
        PrimaryButton("Lock settings and go Home", onLockAndExit)
    }
}

@Composable
fun AccessMethodSettingsScreen(
    accessMode: SettingsAccessMode,
    message: String?,
    onUseDateQuestion: () -> Unit,
    onConfigurePin: (String, String, String) -> Unit,
    onCancel: () -> Unit,
) {
    ClaroScreen(title = "Settings access", message = message) {
        AccessSetupStep(
            accessMode = accessMode,
            onDateQuestion = onUseDateQuestion,
            onPin = onConfigurePin,
        )
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ClaroDimensions.PrimaryTarget),
        ) {
            Text("Cancel")
        }
    }
}

@Composable
fun PhoneStyleSettingsScreen(
    current: VisualMode,
    pending: VisualMode?,
    message: String?,
    onApply: (VisualMode) -> Unit,
    onCancel: () -> Unit,
) {
    var preview by rememberSaveable(current) { mutableStateOf(current) }
    ClaroScreen(title = stringResource(R.string.phone_style), message = message) {
        Text("Preview either style. Applying changes only the presentation; shared data stays in place.")
        VisualModeCard(
            mode = VisualMode.TOUCH,
            selected = preview == VisualMode.TOUCH,
            onSelect = { preview = VisualMode.TOUCH },
        )
        VisualModeCard(
            mode = VisualMode.CLASSIC,
            selected = preview == VisualMode.CLASSIC,
            onSelect = { preview = VisualMode.CLASSIC },
        )
        if (pending != null) {
            Text(
                "A switch to ${pending.localizedDisplayName()} is waiting for the current activity to finish.",
            )
        }
        PrimaryButton("Apply ${preview.localizedDisplayName()}") { onApply(preview) }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ClaroDimensions.PrimaryTarget),
        ) {
            Text("Cancel")
        }
    }
}

@Composable
fun DisplayLanguageSettingsScreen(
    preferences: ClaroPreferences,
    onApply: (AppLanguage, TextSize, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    LanguageDisplayStep(preferences = preferences, onSave = onApply, onCancel = onCancel)
}

@Composable
fun ReadinessSettingsScreen(
    readiness: Map<ReadinessCapability, CapabilityStatus>,
    onStatus: (ReadinessCapability, CapabilityStatus) -> Unit,
    onBack: () -> Unit,
) {
    ClaroScreen(title = "Role readiness") {
        ReadinessRows(readiness, onStatus)
        Text("Ready means the capability is granted, not that a real call, message, or SOS test succeeded.")
        PrimaryButton("Back to Settings", onBack)
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Text("Claro provides a simpler home, phone and messages in one app.")
    Text("Camera and system role prompts open Android screens. SOS calls one agreed trusted person; it is not monitored emergency dispatch.")
    Text("You can finish setup independently or with a caregiver. Setup resumes after interruption.")
    PrimaryButton("Continue free", onContinue)
}

@Composable
private fun PhoneStyleChoice(
    selected: VisualMode,
    onSelected: (VisualMode) -> Unit,
    onContinue: () -> Unit,
) {
    Text("Choose a large visual home. You can preview and change it later in protected Settings.")
    VisualModeCard(VisualMode.TOUCH, selected == VisualMode.TOUCH) {
        onSelected(VisualMode.TOUCH)
    }
    VisualModeCard(VisualMode.CLASSIC, selected == VisualMode.CLASSIC) {
        onSelected(VisualMode.CLASSIC)
    }
    PrimaryButton("Use ${selected.localizedDisplayName()}", onContinue)
}

@Composable
private fun VisualModeCard(
    mode: VisualMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp),
        border = if (selected) BorderStroke(4.dp, ClaroColors.Blue) else null,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onSelect)
                Text(mode.localizedDisplayName(), modifier = Modifier.semantics { heading() })
            }
            Text(
                if (mode == VisualMode.TOUCH) {
                    "Large photo contacts and a two-column tile home."
                } else {
                    "A permanent 12-key keypad with fixed Call, Home, Back and Menu controls."
                },
            )
            Text(if (selected) "Selected" else "Preview")
        }
    }
}

@Composable
private fun LanguageDisplayStep(
    preferences: ClaroPreferences,
    onSave: (AppLanguage, TextSize, Boolean) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var language by rememberSaveable { mutableStateOf(preferences.language) }
    var textSize by rememberSaveable { mutableStateOf(preferences.textSize) }
    var highContrast by rememberSaveable { mutableStateOf(preferences.highContrast) }
    ClaroScreenContent {
        Text("Language")
        AppLanguage.entries.forEach { option ->
            ChoiceRow(option.displayName(), language == option) { language = option }
        }
        Text("Text size")
        TextSize.entries.forEach { option ->
            ChoiceRow(option.displayName(), textSize == option) { textSize = option }
        }
        ToggleRow("High contrast", highContrast) { highContrast = it }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Preview", modifier = Modifier.semantics { heading() })
                Text(language.previewText())
                if (language == AppLanguage.HEBREW) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Ltr,
                    ) {
                        Text("050-123-4567 · 12/11/2026")
                    }
                }
            }
        }
        PrimaryButton("Save preview") { onSave(language, textSize, highContrast) }
        onCancel?.let {
            OutlinedButton(
                onClick = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ClaroDimensions.PrimaryTarget),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun AccessSetupStep(
    accessMode: SettingsAccessMode,
    onDateQuestion: () -> Unit,
    onPin: (String, String, String) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(accessMode) }
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var recovery by rememberSaveable { mutableStateOf("") }
    ChoiceRow("Date question", mode == SettingsAccessMode.DATE_QUESTION) {
        mode = SettingsAccessMode.DATE_QUESTION
    }
    Text("No secret to remember. Anyone who knows today's date can unlock.")
    ChoiceRow("Caregiver PIN", mode == SettingsAccessMode.CAREGIVER_PIN) {
        mode = SettingsAccessMode.CAREGIVER_PIN
    }
    Text("A separate recovery secret is required and should be stored safely elsewhere.")
    if (mode == SettingsAccessMode.CAREGIVER_PIN) {
        SecretField("6-digit PIN", pin) { pin = it }
        SecretField("Confirm PIN", confirmation) { confirmation = it }
        SecretField("Recovery secret", recovery) { recovery = it }
    }
    PrimaryButton("Save access method") {
        if (mode == SettingsAccessMode.DATE_QUESTION) onDateQuestion()
        else onPin(pin, confirmation, recovery)
    }
}

@Composable
private fun PeopleSetupStep(
    preferences: ClaroPreferences,
    onSave: (Int, Boolean, Boolean) -> Unit,
) {
    var favorites by rememberSaveable {
        mutableIntStateOf(preferences.onboarding.favoriteSlotsAssigned)
    }
    var one by rememberSaveable { mutableStateOf(preferences.onboarding.speedDialOneAssigned) }
    var two by rememberSaveable { mutableStateOf(preferences.onboarding.speedDialTwoAssigned) }
    Text("Assign up to four photo contacts. Missing optional assignments remain visible after setup.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { favorites = (favorites - 1).coerceAtLeast(0) }) {
            Text("Remove")
        }
        Text("$favorites of 4 assigned", modifier = Modifier.align(Alignment.CenterVertically))
        OutlinedButton(onClick = { favorites = (favorites + 1).coerceAtMost(4) }) {
            Text("Add")
        }
    }
    ToggleRow("Hold 1 assigned", one) { one = it }
    ToggleRow("Hold 2 assigned", two) { two = it }
    PrimaryButton("Save people and speed dial") { onSave(favorites, one, two) }
}

@Composable
private fun SosSetupStep(
    agreed: Boolean,
    onSave: (Boolean) -> Unit,
) {
    var checked by rememberSaveable { mutableStateOf(agreed) }
    Text("SOS calls one trusted person for help. It does not notify a monitoring service or emergency dispatch.")
    ToggleRow("Recipient agreed to receive a test call", checked) { checked = it }
    Text("A real caregiver test remains untested until completed with consent.")
    PrimaryButton("Save SOS readiness") { onSave(checked) }
}

@Composable
private fun ReadinessChecklist(
    readiness: Map<ReadinessCapability, CapabilityStatus>,
    onStatus: (ReadinessCapability, CapabilityStatus) -> Unit,
    onContinue: () -> Unit,
) {
    ReadinessRows(readiness, onStatus)
    Text("Each Android role or permission is requested separately in context. Missing access keeps native fallback guidance visible.")
    PrimaryButton("Continue to handover", onContinue)
}

@Composable
private fun ReadinessRows(
    readiness: Map<ReadinessCapability, CapabilityStatus>,
    onStatus: (ReadinessCapability, CapabilityStatus) -> Unit,
) {
    ReadinessCapability.entries.forEach { capability ->
        val status = readiness[capability] ?: CapabilityStatus.NEEDS_ATTENTION
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(capability.displayName(), modifier = Modifier.semantics { heading() })
                Text(status.displayName())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onStatus(capability, CapabilityStatus.READY) }) {
                        Text("Fake grant")
                    }
                    OutlinedButton(
                        onClick = { onStatus(capability, CapabilityStatus.NEEDS_ATTENTION) },
                    ) {
                        Text("Needs repair")
                    }
                }
            }
        }
    }
}

@Composable
private fun HandoverChecklist(
    checked: Set<HandoverCheck>,
    onChecked: (HandoverCheck, Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    Text("Record observed tests separately from granted permissions.")
    HandoverCheck.entries.forEach { item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ClaroDimensions.SecondaryTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item in checked,
                onCheckedChange = { onChecked(item, it) },
            )
            Text(item.displayName())
        }
    }
    Text("Unchecked items remain honestly marked as not tested.")
    PrimaryButton("Finish setup", onFinish)
}

@Composable
private fun SettingsRow(title: String, detail: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(title)
            Text(detail)
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ClaroDimensions.PrimaryTarget),
        border = if (selected) BorderStroke(3.dp, ClaroColors.Blue) else null,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ClaroDimensions.PrimaryTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SecretField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
    )
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ClaroDimensions.PrimaryTarget),
        colors = ButtonDefaults.buttonColors(containerColor = ClaroColors.Blue),
    ) {
        Text(label)
    }
}

@Composable
private fun ClaroScreen(
    title: String,
    message: String? = null,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(ClaroDimensions.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                title,
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        message?.let {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(12.dp))
                }
            }
        }
        item { ClaroScreenContent(content) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ClaroScreenContent(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = { content() },
    )
}

private fun OnboardingStep.title() = when (this) {
    OnboardingStep.WELCOME -> "Welcome to Claro"
    OnboardingStep.PHONE_STYLE -> "Choose phone style"
    OnboardingStep.LANGUAGE_AND_DISPLAY -> "Language and display"
    OnboardingStep.SETTINGS_ACCESS -> "Settings access"
    OnboardingStep.PEOPLE_AND_SPEED_DIAL -> "People and speed dial"
    OnboardingStep.SOS_CONTACT -> "SOS contact"
    OnboardingStep.ROLE_READINESS -> "Permissions and roles"
    OnboardingStep.HANDOVER -> "Handover checklist"
    OnboardingStep.COMPLETE -> "Setup complete"
}

@Composable
private fun VisualMode.localizedDisplayName() = when (this) {
    VisualMode.TOUCH -> stringResource(R.string.touch_home)
    VisualMode.CLASSIC -> stringResource(R.string.classic_phone)
}

private fun AppLanguage.displayName() = when (this) {
    AppLanguage.ENGLISH -> "English"
    AppLanguage.SPANISH -> "Español"
    AppLanguage.HEBREW -> "עברית"
}

private fun AppLanguage.previewText() = when (this) {
    AppLanguage.ENGLISH -> "Call family · Messages · Settings"
    AppLanguage.SPANISH -> "Llamar a la familia · Mensajes · Configuración"
    AppLanguage.HEBREW -> "שיחה למשפחה · הודעות · הגדרות"
}

private fun TextSize.displayName() = when (this) {
    TextSize.STANDARD -> "Standard"
    TextSize.LARGE -> "Large"
    TextSize.HUGE -> "Huge"
}

private fun ReadinessCapability.displayName() =
    name.lowercase().replaceFirstChar(Char::uppercase)

private fun CapabilityStatus.displayName() = when (this) {
    CapabilityStatus.READY -> "Granted; service test still separate"
    CapabilityStatus.NEEDS_ATTENTION -> "Needs attention"
    CapabilityStatus.UNAVAILABLE -> "Unavailable on this device"
}

private fun HandoverCheck.displayName() = when (this) {
    HandoverCheck.OUTGOING_CALL -> "Outgoing call observed"
    HandoverCheck.INCOMING_CALL -> "Incoming call observed"
    HandoverCheck.SMS_SEND -> "SMS send observed"
    HandoverCheck.SMS_RECEIVE -> "SMS receive observed"
    HandoverCheck.DEFAULT_SIM -> "Default SIM checked"
    HandoverCheck.RINGTONE -> "Audible ringtone checked"
    HandoverCheck.HOME_RETURN -> "Home return checked"
    HandoverCheck.SOS_TEST -> "Consented SOS test observed"
}

private fun readinessSummary(preferences: ClaroPreferences): String {
    val ready = preferences.onboarding.readiness.values.count { it == CapabilityStatus.READY }
    return "$ready of ${ReadinessCapability.entries.size} capabilities granted"
}

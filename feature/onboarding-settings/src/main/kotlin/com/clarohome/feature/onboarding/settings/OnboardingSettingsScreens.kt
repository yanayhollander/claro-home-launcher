package com.clarohome.feature.onboarding.settings

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarohome.core.design.ClaroAvatar
import com.clarohome.core.design.ClaroBannerTone
import com.clarohome.core.design.ClaroColors
import com.clarohome.core.design.ClaroDimensions
import com.clarohome.core.design.ClaroIcon
import com.clarohome.core.design.ClaroIconKind
import com.clarohome.core.design.ClaroInfoCard
import com.clarohome.core.design.ClaroPrimaryButton
import com.clarohome.core.design.ClaroRowButton
import com.clarohome.core.design.ClaroScreenScaffold
import com.clarohome.core.design.ClaroSecondaryButton
import com.clarohome.core.design.ClaroSoftkey
import com.clarohome.core.design.LocalClaroHaptics
import com.clarohome.core.design.toLocale
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
    val onOpenPeopleSetup: () -> Unit,
    val onOpenSosSetup: () -> Unit,
    val onRequestCapability: (ReadinessCapability) -> Unit,
    val onHandover: (HandoverCheck, Boolean) -> Unit,
    val onOpenAppsSetup: () -> Unit = {},
    val onOpenPlans: () -> Unit = {},
    val onHapticFeedbackChange: (Boolean) -> Unit = {},
)

@Composable
fun OnboardingFlow(
    preferences: ClaroPreferences,
    message: String?,
    callbacks: OnboardingCallbacks,
) {
    when (preferences.onboarding.currentStep) {
        OnboardingStep.WELCOME -> WelcomeStep(
            message = message,
            onContinue = { callbacks.onContinue(OnboardingStep.PHONE_STYLE) },
        )
        OnboardingStep.PHONE_STYLE -> PhoneStyleChoice(
            preferences = preferences,
            message = message,
            onSelected = callbacks.onChooseMode,
            onContinue = { callbacks.onContinue(OnboardingStep.LANGUAGE_AND_DISPLAY) },
        )
        OnboardingStep.LANGUAGE_AND_DISPLAY -> LanguageDisplayOnboardingStep(
            preferences = preferences,
            message = message,
            onSave = { language, size, contrast, haptics ->
                callbacks.onLanguageDisplay(language, size, contrast)
                callbacks.onHapticFeedbackChange(haptics)
                callbacks.onContinue(OnboardingStep.SETTINGS_ACCESS)
            },
        )
        OnboardingStep.SETTINGS_ACCESS -> AccessSetupOnboardingStep(
            accessMode = preferences.accessProtection.mode,
            message = message,
            onDateQuestion = {
                callbacks.onUseDateQuestion()
                callbacks.onContinue(OnboardingStep.PEOPLE_AND_SPEED_DIAL)
            },
            onPin = callbacks.onConfigurePin,
        )
        OnboardingStep.PEOPLE_AND_SPEED_DIAL -> PeopleSetupStep(
            preferences = preferences,
            message = message,
            onOpenPeopleSetup = callbacks.onOpenPeopleSetup,
            onContinue = { callbacks.onContinue(OnboardingStep.SOS_CONTACT) },
        )
        OnboardingStep.SOS_CONTACT -> SosSetupStep(
            agreed = preferences.onboarding.sosRecipientAgreed,
            message = message,
            onOpenSosSetup = callbacks.onOpenSosSetup,
            onContinue = { callbacks.onContinue(OnboardingStep.ROLE_READINESS) },
        )
        OnboardingStep.ROLE_READINESS -> ReadinessChecklist(
            readiness = preferences.onboarding.readiness,
            message = message,
            onRequest = callbacks.onRequestCapability,
            onContinue = { callbacks.onContinue(OnboardingStep.HANDOVER) },
        )
        OnboardingStep.HANDOVER -> HandoverChecklist(
            checked = preferences.onboarding.handoverChecks,
            message = message,
            onChecked = callbacks.onHandover,
            onOpenAppsSetup = callbacks.onOpenAppsSetup,
            onOpenPlans = callbacks.onOpenPlans,
            onFinish = { callbacks.onContinue(OnboardingStep.COMPLETE) },
        )
        OnboardingStep.COMPLETE -> OnboardingStepScaffold(
            title = OnboardingStep.COMPLETE.title(),
            stepIndex = null,
            message = message,
        ) {
            ClaroInfoCard(
                text = stringResource(R.string.onb_setup_complete_body),
                tone = ClaroBannerTone.SUCCESS,
            )
        }
    }
}

@Composable
fun AccessGateScreen(
    mode: SettingsAccessMode,
    message: String?,
    onUnlock: (String) -> Unit,
    onRecoverPin: () -> Unit,
) {
    var input by rememberSaveable(mode) { mutableStateOf("") }
    val isDateMode = mode == SettingsAccessMode.DATE_QUESTION

    ClaroScreenScaffold(
        title = stringResource(
            if (isDateMode) R.string.onb_todays_date_title else R.string.onb_settings_access,
        ),
        modifier = Modifier.testTag("onboarding-screen"),
        bannerText = message,
        bannerTone = messageBannerTone(message),
        dockInSoftkeys = true,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.unlock),
                softkeyColor = ClaroColors.Blue,
                onClick = { onUnlock(input) },
            )
            if (mode == SettingsAccessMode.CAREGIVER_PIN) {
                ClaroSecondaryButton(
                    label = stringResource(R.string.onb_forgot_pin),
                    onClick = onRecoverPin,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ClaroColors.PaleBlue),
                contentAlignment = Alignment.Center,
            ) {
                ClaroIcon(
                    kind = ClaroIconKind.LOCK,
                    tint = ClaroColors.Blue,
                    size = 56.dp,
                )
            }
            Text(
                text = stringResource(
                    if (isDateMode) {
                        R.string.onb_date_gate_instructions
                    } else {
                        R.string.onb_pin_gate_instructions
                    },
                ),
                color = ClaroColors.Ink,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isDateMode) {
                ClaroDateField(
                    value = input,
                    onValueChange = { input = it },
                )
            } else {
                PinIndicatorDots(filledCount = input.length)
                ClaroPinField(
                    label = stringResource(R.string.onb_six_digit_pin_label),
                    value = input,
                    onValueChange = { input = it },
                )
            }
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

    ClaroScreenScaffold(
        title = stringResource(R.string.onb_recover_pin_title),
        modifier = Modifier.testTag("onboarding-screen"),
        bannerText = message,
        bannerTone = messageBannerTone(message),
        dockInSoftkeys = true,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.onb_set_new_pin),
                onClick = { onRecover(recovery, pin, confirmation) },
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.onb_cancel),
                softkey = ClaroSoftkey.BACK,
                onClick = onCancel,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(ClaroColors.PaleBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    ClaroIcon(
                        kind = ClaroIconKind.LOCK,
                        tint = ClaroColors.Blue,
                        size = 44.dp,
                    )
                }
            }
            Text(
                text = stringResource(R.string.onb_recover_pin_instructions),
                color = ClaroColors.Ink,
                style = MaterialTheme.typography.bodyLarge,
            )
            ClaroSecretField(
                label = stringResource(R.string.onb_recovery_secret_label),
                value = recovery,
                onValueChange = { recovery = it },
            )
            ClaroPinField(
                label = stringResource(R.string.onb_new_six_digit_pin_label),
                value = pin,
                onValueChange = { pin = it },
            )
            ClaroPinField(
                label = stringResource(R.string.onb_confirm_new_pin_label),
                value = confirmation,
                onValueChange = { confirmation = it },
            )
            ClaroInfoCard(
                text = stringResource(R.string.onb_recover_pin_lost_both_warning),
                tone = ClaroBannerTone.WARNING,
            )
        }
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
    onPeople: () -> Unit,
    onSafety: () -> Unit,
    onMembershipPrivacy: () -> Unit,
    onDone: () -> Unit,
    onLockAndExit: () -> Unit,
    onResetClaro: () -> Unit = {},
) {
    ClaroScreenScaffold(
        title = stringResource(R.string.settings),
        modifier = Modifier.testTag("onboarding-screen"),
        dockInSoftkeys = true,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.onb_done_settings),
                onClick = onDone,
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.onb_lock_settings),
                icon = ClaroIconKind.LOCK,
                onClick = onLockAndExit,
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ClaroRowButton(
                title = stringResource(R.string.phone_style),
                subtitle = preferences.visualMode.localizedDisplayName(),
                leading = {
                    ClaroIcon(
                        kind = ClaroIconKind.HOME,
                        tint = ClaroColors.Blue,
                        size = 32.dp,
                    )
                },
                onClick = onPhoneStyle,
            )
            ClaroRowButton(
                title = stringResource(R.string.onb_home_and_apps_title),
                subtitle = stringResource(R.string.onb_home_and_apps_subtitle),
                leading = {
                    ClaroIcon(
                        kind = ClaroIconKind.APPS,
                        tint = ClaroColors.Blue,
                        size = 32.dp,
                    )
                },
                onClick = onHomeAndApps,
            )
            ClaroRowButton(
                title = stringResource(R.string.onb_people_and_speed_dial_amp_title),
                subtitle = stringResource(
                    R.string.onb_people_configured_short,
                    preferences.onboarding.favoriteSlotsAssigned,
                ),
                leading = {
                    ClaroIcon(
                        kind = ClaroIconKind.CONTACT,
                        tint = ClaroColors.Blue,
                        size = 32.dp,
                    )
                },
                onClick = onPeople,
            )
            ClaroRowButton(
                title = stringResource(R.string.onb_language_and_display_amp_title),
                subtitle = stringResource(
                    R.string.onb_language_display_summary,
                    preferences.language.displayName(),
                    preferences.textSize.displayName(),
                ),
                leading = {
                    ClaroIcon(
                        kind = ClaroIconKind.SUN,
                        tint = ClaroColors.Blue,
                        size = 32.dp,
                    )
                },
                onClick = onDisplayLanguage,
            )
            ClaroRowButton(
                title = stringResource(R.string.onb_sos_contact_title),
                subtitle = stringResource(
                    if (preferences.onboarding.sosRecipientAgreed) {
                        R.string.onb_sos_subtitle_configured
                    } else {
                        R.string.onb_sos_subtitle_not_configured
                    },
                ),
                leading = {
                    ClaroIcon(
                        kind = ClaroIconKind.SOS,
                        tint = ClaroColors.Red,
                        size = 32.dp,
                    )
                },
                onClick = onSafety,
            )
            ClaroRowButton(
                title = stringResource(R.string.onb_permissions_and_roles_amp_title),
                subtitle = readinessSummary(preferences),
                leading = {
                    ClaroIcon(
                        kind = ClaroIconKind.CHECK,
                        tint = ClaroColors.Blue,
                        size = 32.dp,
                    )
                },
                onClick = onReadiness,
            )
            ClaroRowButton(
                title = stringResource(R.string.onb_settings_access),
                subtitle = stringResource(
                    if (preferences.accessProtection.mode == SettingsAccessMode.DATE_QUESTION) {
                        R.string.onb_settings_access_date_subtitle
                    } else {
                        R.string.onb_settings_access_pin_subtitle
                    },
                ),
                leading = {
                    ClaroIcon(
                        kind = ClaroIconKind.LOCK,
                        tint = ClaroColors.Blue,
                        size = 32.dp,
                    )
                },
                onClick = onAccessMethod,
            )
            ClaroRowButton(
                title = stringResource(R.string.onb_membership_and_privacy_title),
                subtitle = stringResource(R.string.onb_membership_and_privacy_subtitle),
                leading = {
                    ClaroIcon(
                        kind = ClaroIconKind.SETTINGS,
                        tint = ClaroColors.Blue,
                        size = 32.dp,
                    )
                },
                onClick = onMembershipPrivacy,
            )
            ClaroRowButton(
                title = stringResource(R.string.onb_reset_claro),
                subtitle = stringResource(R.string.onb_reset_claro_subtitle),
                leading = {
                    ClaroIcon(
                        kind = ClaroIconKind.BACKSPACE,
                        tint = ClaroColors.Red,
                        size = 32.dp,
                    )
                },
                onClick = onResetClaro,
            )
        }
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
    var mode by rememberSaveable(accessMode) { mutableStateOf(accessMode) }
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var recovery by rememberSaveable { mutableStateOf("") }

    ClaroScreenScaffold(
        title = stringResource(R.string.onb_settings_access),
        modifier = Modifier.testTag("onboarding-screen"),
        bannerText = message,
        bannerTone = messageBannerTone(message),
        dockInSoftkeys = true,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.onb_save_access_method),
                softkeyLabel = stringResource(R.string.onb_save),
                onClick = {
                    if (mode == SettingsAccessMode.DATE_QUESTION) {
                        onUseDateQuestion()
                    } else {
                        onConfigurePin(pin, confirmation, recovery)
                    }
                },
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.onb_cancel),
                softkey = ClaroSoftkey.BACK,
                onClick = onCancel,
            )
        },
    ) {
        AccessSetupFields(
            mode = mode,
            onModeChange = { mode = it },
            pin = pin,
            onPinChange = { pin = it },
            confirmation = confirmation,
            onConfirmationChange = { confirmation = it },
            recovery = recovery,
            onRecoveryChange = { recovery = it },
        )
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
    val stackVertically = MaterialTheme.typography.bodyLarge.fontSize >= 32.sp

    ClaroScreenScaffold(
        title = stringResource(R.string.phone_style),
        modifier = Modifier.testTag("onboarding-screen"),
        bannerText = message,
        bannerTone = messageBannerTone(message),
        dockInSoftkeys = true,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.onb_apply_style, preview.localizedDisplayName()),
                softkeyLabel = stringResource(R.string.onb_apply),
                onClick = { onApply(preview) },
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.onb_cancel),
                softkey = ClaroSoftkey.BACK,
                onClick = onCancel,
            )
        },
    ) {
        Text(
            text = stringResource(R.string.onb_phone_style_settings_body),
            color = ClaroColors.Ink,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        VisualModeGrid(
            selected = preview,
            stackVertically = stackVertically,
            onSelect = { preview = it },
        )
        if (pending != null) {
            ClaroInfoCard(
                text = stringResource(
                    R.string.onb_phone_style_pending_switch,
                    pending.localizedDisplayName(),
                ),
                tone = ClaroBannerTone.WARNING,
            )
        }
    }
}

@Composable
fun DisplayLanguageSettingsScreen(
    preferences: ClaroPreferences,
    onApply: (AppLanguage, TextSize, Boolean) -> Unit,
    onCancel: () -> Unit,
    onApplyWithHaptics: ((AppLanguage, TextSize, Boolean, Boolean) -> Unit)? = null,
) {
    var language by rememberSaveable { mutableStateOf(preferences.language) }
    var textSize by rememberSaveable { mutableStateOf(preferences.textSize) }
    var hapticFeedback by rememberSaveable { mutableStateOf(preferences.hapticFeedback) }
    var picker by rememberSaveable { mutableStateOf<DisplayPicker?>(null) }

    val openPicker = picker
    if (openPicker != null) {
        DisplayPickerScreen(
            picker = openPicker,
            language = language,
            textSize = textSize,
            onLanguageChange = { language = it },
            onTextSizeChange = { textSize = it },
            onClose = { picker = null },
        )
        return
    }

    ClaroScreenScaffold(
        title = stringResource(R.string.onb_language_and_display_title),
        modifier = Modifier.testTag("onboarding-screen"),
        dockInSoftkeys = true,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.onb_save),
                softkeyColor = ClaroColors.Blue,
                onClick = {
                    if (onApplyWithHaptics != null) {
                        onApplyWithHaptics(language, textSize, false, hapticFeedback)
                    } else {
                        onApply(language, textSize, false)
                    }
                },
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.onb_cancel),
                softkey = ClaroSoftkey.BACK,
                onClick = onCancel,
            )
        },
    ) {
        LanguageDisplayFields(
            language = language,
            textSize = textSize,
            onOpenPicker = { picker = it },
            hapticFeedback = hapticFeedback,
            onHapticFeedbackChange = { hapticFeedback = it },
        )
    }
}

@Composable
fun ReadinessSettingsScreen(
    readiness: Map<ReadinessCapability, CapabilityStatus>,
    onRequest: (ReadinessCapability) -> Unit,
    onBack: () -> Unit,
) {
    ClaroScreenScaffold(
        title = stringResource(R.string.onb_permissions_and_roles_amp_title),
        modifier = Modifier.testTag("onboarding-screen"),
        dockInSoftkeys = true,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.onb_back_to_settings),
                softkey = ClaroSoftkey.HIDDEN,
                onClick = onBack,
            )
        },
    ) {
        ReadinessRows(
            readiness = readiness,
            onRequest = onRequest,
        )
        ClaroInfoCard(
            text = stringResource(R.string.onb_readiness_settings_info),
            tone = ClaroBannerTone.INFO,
        )
    }
}

@Composable
fun ResetClaroScreen(
    hasActiveCall: Boolean,
    onReturnToCall: () -> Unit,
    onConfirmReset: () -> Unit,
    onCancel: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(hasActiveCall) {
        if (hasActiveCall) confirming = false
    }

    ClaroScreenScaffold(
        title = stringResource(R.string.onb_reset_claro_confirm_title),
        modifier = Modifier.testTag("onboarding-screen"),
        dockInSoftkeys = true,
        dock = {
            when {
                hasActiveCall -> {
                    ClaroPrimaryButton(
                        label = stringResource(R.string.onb_return_to_call),
                        icon = ClaroIconKind.PHONE,
                        containerColor = ClaroColors.Green,
                        onClick = onReturnToCall,
                    )
                    ClaroSecondaryButton(
                        label = stringResource(R.string.onb_cancel),
                        softkey = ClaroSoftkey.BACK,
                        onClick = onCancel,
                    )
                }
                confirming -> {
                    ClaroPrimaryButton(
                        label = stringResource(R.string.onb_reset_claro),
                        containerColor = ClaroColors.Red,
                        onClick = onConfirmReset,
                    )
                    ClaroSecondaryButton(
                        label = stringResource(R.string.onb_cancel),
                        softkey = ClaroSoftkey.BACK,
                        onClick = {
                            confirming = false
                            onCancel()
                        },
                    )
                }
                else -> {
                    ClaroPrimaryButton(
                        label = stringResource(R.string.onb_continue),
                        containerColor = ClaroColors.Red,
                        onClick = { confirming = true },
                    )
                    ClaroSecondaryButton(
                        label = stringResource(R.string.onb_cancel),
                        softkey = ClaroSoftkey.BACK,
                        onClick = onCancel,
                    )
                }
            }
        },
    ) {
        if (hasActiveCall) {
            ClaroInfoCard(
                text = stringResource(R.string.onb_reset_end_call_first),
                tone = ClaroBannerTone.ERROR,
            )
        } else if (confirming) {
            ClaroInfoCard(
                text = stringResource(R.string.onb_reset_all_changes_question),
                tone = ClaroBannerTone.WARNING,
            )
        } else {
            ClaroInfoCard(
                text = stringResource(R.string.onb_reset_scope_warning),
                tone = ClaroBannerTone.WARNING,
            )
        }
        ResetScopeCard(
            heading = stringResource(R.string.onb_reset_cleared_heading),
            accentColor = ClaroColors.Red,
            backgroundColor = ClaroColors.PaleRed,
            items = listOf(
                stringResource(R.string.onb_reset_cleared_item_preferences),
                stringResource(R.string.onb_reset_cleared_item_favorites),
                stringResource(R.string.onb_reset_cleared_item_photos_sos),
                stringResource(R.string.onb_reset_cleared_item_pin_drafts),
            ),
        )
        ResetScopeCard(
            heading = stringResource(R.string.onb_reset_kept_heading),
            accentColor = ClaroColors.Green,
            backgroundColor = ClaroColors.PaleGreen,
            items = listOf(
                stringResource(R.string.onb_reset_kept_item_contacts),
                stringResource(R.string.onb_reset_kept_item_sms),
                stringResource(R.string.onb_reset_kept_item_photos),
                stringResource(R.string.onb_reset_kept_item_purchases),
            ),
        )
    }
}

@Composable
private fun WelcomeStep(
    message: String?,
    onContinue: () -> Unit,
) {
    ClaroScreenScaffold(
        title = stringResource(R.string.onboarding_welcome),
        modifier = Modifier.testTag("onboarding-screen"),
        bannerText = message,
        bannerTone = messageBannerTone(message),
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.onb_set_up_claro),
                onClick = onContinue,
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.onb_continue_free),
                onClick = onContinue,
            )
            Text(
                text = stringResource(R.string.onb_welcome_no_trial_note),
                color = Color(0xFFE2E8F0),
                fontSize = 16.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(ClaroColors.PaleBlue),
                contentAlignment = Alignment.Center,
            ) {
                ClaroIcon(
                    kind = ClaroIconKind.SUN,
                    tint = ClaroColors.Blue,
                    size = 84.dp,
                )
            }
            Text(
                text = stringResource(R.string.onb_welcome_tagline),
                color = ClaroColors.Ink,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.onb_welcome_subtitle),
                color = ClaroColors.Muted,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            ClaroInfoCard(
                text = stringResource(R.string.onb_welcome_permissions_sos_note),
                tone = ClaroBannerTone.INFO,
            )
        }
    }
}

@Composable
private fun PhoneStyleChoice(
    preferences: ClaroPreferences,
    message: String?,
    onSelected: (VisualMode) -> Unit,
    onContinue: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(VisualMode.CLASSIC) }

    OnboardingStepScaffold(
        title = OnboardingStep.PHONE_STYLE.title(),
        stepIndex = 1,
        message = message,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.onb_use_style, selected.localizedDisplayName()),
                onClick = {
                    onSelected(selected)
                    onContinue()
                },
            )
        },
    ) {
        Text(
            text = stringResource(R.string.onb_phone_style_choice_body),
            color = ClaroColors.Ink,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        VisualModeGrid(
            selected = selected,
            stackVertically = preferences.textSize == TextSize.HUGE,
            onSelect = { mode -> selected = mode },
        )
    }
}

@Composable
private fun LanguageDisplayOnboardingStep(
    preferences: ClaroPreferences,
    message: String?,
    onSave: (AppLanguage, TextSize, Boolean, Boolean) -> Unit,
) {
    var language by rememberSaveable { mutableStateOf(preferences.language) }
    var textSize by rememberSaveable { mutableStateOf(preferences.textSize) }
    var hapticFeedback by rememberSaveable { mutableStateOf(preferences.hapticFeedback) }
    var picker by rememberSaveable { mutableStateOf<DisplayPicker?>(null) }

    val openPicker = picker
    if (openPicker != null) {
        DisplayPickerScreen(
            picker = openPicker,
            language = language,
            textSize = textSize,
            onLanguageChange = { language = it },
            onTextSizeChange = { textSize = it },
            onClose = { picker = null },
        )
        return
    }

    OnboardingStepScaffold(
        title = OnboardingStep.LANGUAGE_AND_DISPLAY.title(),
        stepIndex = 2,
        message = message,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.onb_save_and_continue),
                onClick = { onSave(language, textSize, false, hapticFeedback) },
            )
        },
    ) {
        LanguageDisplayFields(
            language = language,
            textSize = textSize,
            onOpenPicker = { picker = it },
            hapticFeedback = hapticFeedback,
            onHapticFeedbackChange = { hapticFeedback = it },
        )
    }
}

@Composable
private fun AccessSetupOnboardingStep(
    accessMode: SettingsAccessMode,
    message: String?,
    onDateQuestion: () -> Unit,
    onPin: (String, String, String) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(accessMode) }
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var recovery by rememberSaveable { mutableStateOf("") }

    OnboardingStepScaffold(
        title = OnboardingStep.SETTINGS_ACCESS.title(),
        stepIndex = 3,
        message = message,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.onb_save_access_method),
                onClick = {
                    if (mode == SettingsAccessMode.DATE_QUESTION) {
                        onDateQuestion()
                    } else {
                        onPin(pin, confirmation, recovery)
                    }
                },
            )
        },
    ) {
        AccessSetupFields(
            mode = mode,
            onModeChange = { mode = it },
            pin = pin,
            onPinChange = { pin = it },
            confirmation = confirmation,
            onConfirmationChange = { confirmation = it },
            recovery = recovery,
            onRecoveryChange = { recovery = it },
        )
    }
}

@Composable
private fun PeopleSetupStep(
    preferences: ClaroPreferences,
    message: String?,
    onOpenPeopleSetup: () -> Unit,
    onContinue: () -> Unit,
) {
    val assigned = preferences.onboarding.favoriteSlotsAssigned
    val speedOne = preferences.onboarding.speedDialOneAssigned
    val speedTwo = preferences.onboarding.speedDialTwoAssigned

    OnboardingStepScaffold(
        title = OnboardingStep.PEOPLE_AND_SPEED_DIAL.title(),
        stepIndex = 4,
        message = message,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.onb_open_contacts_and_speed_dial),
                icon = ClaroIconKind.CONTACT,
                onClick = onOpenPeopleSetup,
            )
            ClaroSecondaryButton(
                label = stringResource(R.string.onb_continue),
                onClick = onContinue,
            )
        },
    ) {
        Text(
            text = stringResource(R.string.onb_people_setup_body),
            color = ClaroColors.Ink,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        val speedOneStatus = stringResource(
            if (speedOne) R.string.onb_speed_dial_assigned else R.string.onb_speed_dial_not_set,
        )
        val speedTwoStatus = stringResource(
            if (speedTwo) R.string.onb_speed_dial_assigned else R.string.onb_speed_dial_not_set,
        )
        ClaroInfoCard(
            text = stringResource(
                R.string.onb_people_setup_status_configured,
                assigned,
                speedOneStatus,
                speedTwoStatus,
            ),
            tone = ClaroBannerTone.INFO,
        )
        Text(
            text = stringResource(R.string.onb_people_setup_hold_note),
            color = ClaroColors.Muted,
            fontSize = 18.sp,
            lineHeight = 23.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun SosSetupStep(
    agreed: Boolean,
    message: String?,
    onOpenSosSetup: () -> Unit,
    onContinue: () -> Unit,
) {
    OnboardingStepScaffold(
        title = OnboardingStep.SOS_CONTACT.title(),
        stepIndex = 5,
        message = message,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.onb_configure_trusted_person),
                icon = ClaroIconKind.SOS,
                onClick = onOpenSosSetup,
            )
            ClaroSecondaryButton(
                label = stringResource(
                    if (agreed) R.string.onb_continue else R.string.onb_continue_sos_incomplete,
                ),
                onClick = onContinue,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ClaroColors.PaleRed),
                contentAlignment = Alignment.Center,
            ) {
                ClaroIcon(
                    kind = ClaroIconKind.SOS,
                    tint = ClaroColors.Red,
                    size = 52.dp,
                )
            }
            Text(
                text = stringResource(R.string.onb_sos_setup_body),
                color = ClaroColors.Ink,
                style = MaterialTheme.typography.bodyLarge,
            )
            ClaroInfoCard(
                text = stringResource(
                    if (agreed) {
                        R.string.onb_sos_setup_agreed_info
                    } else {
                        R.string.onb_sos_setup_unconfigured_warning
                    },
                ),
                tone = if (agreed) ClaroBannerTone.SUCCESS else ClaroBannerTone.WARNING,
            )
        }
    }
}

@Composable
private fun ReadinessChecklist(
    readiness: Map<ReadinessCapability, CapabilityStatus>,
    message: String?,
    onRequest: (ReadinessCapability) -> Unit,
    onContinue: () -> Unit,
) {
    OnboardingStepScaffold(
        title = OnboardingStep.ROLE_READINESS.title(),
        stepIndex = 6,
        message = message,
        dock = {
            ClaroPrimaryButton(
                label = stringResource(R.string.onb_continue_to_handover),
                onClick = onContinue,
            )
        },
    ) {
        ReadinessRows(
            readiness = readiness,
            onRequest = onRequest,
        )
        ClaroInfoCard(
            text = stringResource(R.string.onb_readiness_checklist_info),
            tone = ClaroBannerTone.INFO,
        )
    }
}

@Composable
private fun HandoverChecklist(
    checked: Set<HandoverCheck>,
    message: String?,
    onChecked: (HandoverCheck, Boolean) -> Unit,
    onOpenAppsSetup: () -> Unit,
    onOpenPlans: () -> Unit,
    onFinish: () -> Unit,
) {
    val allChecked = checked.size == HandoverCheck.entries.size

    OnboardingStepScaffold(
        title = OnboardingStep.HANDOVER.title(),
        stepIndex = 7,
        message = message,
        dock = {
            ClaroSecondaryButton(
                label = stringResource(R.string.onb_optional_plans),
                onClick = onOpenPlans,
            )
            ClaroPrimaryButton(
                label = stringResource(R.string.onb_finish_setup),
                icon = ClaroIconKind.CHECK,
                onClick = onFinish,
            )
        },
    ) {
        Text(
            text = stringResource(R.string.onb_handover_body),
            color = ClaroColors.Ink,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        ClaroInfoCard(
            text = if (allChecked) {
                stringResource(R.string.onb_handover_all_observed, HandoverCheck.entries.size)
            } else {
                stringResource(
                    R.string.onb_handover_progress_observed,
                    checked.size,
                    HandoverCheck.entries.size,
                )
            },
            tone = if (allChecked) ClaroBannerTone.SUCCESS else ClaroBannerTone.INFO,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            HandoverCheck.entries.forEach { item ->
                val isChecked = item in checked
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .background(ClaroColors.RowSurface)
                        .toggleable(
                            value = isChecked,
                            role = Role.Checkbox,
                            onValueChange = { onChecked(item, it) },
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = ClaroColors.Blue),
                    )
                    Text(
                        text = item.displayName(),
                        color = ClaroColors.Ink,
                        fontSize = 21.sp,
                        lineHeight = 26.sp,
                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = ClaroColors.Border)
            }
        }
        ClaroRowButton(
            title = stringResource(R.string.onb_allowed_apps_and_tiles_title),
            subtitle = stringResource(R.string.onb_allowed_apps_and_tiles_subtitle),
            leading = {
                ClaroIcon(
                    kind = ClaroIconKind.APPS,
                    tint = ClaroColors.Blue,
                    size = 30.dp,
                )
            },
            onClick = onOpenAppsSetup,
        )
    }
}

@Composable
private fun OnboardingStepScaffold(
    title: String,
    stepIndex: Int?,
    message: String?,
    dock: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ClaroScreenScaffold(
        title = title,
        modifier = Modifier.testTag("onboarding-screen"),
        bannerText = message,
        bannerTone = messageBannerTone(message),
        dock = dock,
    ) {
        if (stepIndex != null) {
            OnboardingProgressBar(
                stepIndex = stepIndex,
                totalSteps = 7,
            )
        }
        content()
    }
}

@Composable
private fun OnboardingProgressBar(
    stepIndex: Int,
    totalSteps: Int,
) {
    val progressDescription = stringResource(
        R.string.onb_setup_step_progress,
        stepIndex,
        totalSteps,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .semantics {
                contentDescription = progressDescription
            },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(7.dp)
                    .background(
                        if (index < stepIndex) ClaroColors.Blue else Color(0xFFDCE4EF),
                    ),
            )
        }
    }
}

@Composable
private fun VisualModeGrid(
    selected: VisualMode?,
    stackVertically: Boolean,
    onSelect: (VisualMode) -> Unit,
) {
    if (stackVertically) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VisualModeCard(
                mode = VisualMode.CLASSIC,
                selected = selected == VisualMode.CLASSIC,
                onSelect = { onSelect(VisualMode.CLASSIC) },
                modifier = Modifier.fillMaxWidth(),
            )
            VisualModeCard(
                mode = VisualMode.TOUCH,
                selected = selected == VisualMode.TOUCH,
                onSelect = { onSelect(VisualMode.TOUCH) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            VisualModeCard(
                mode = VisualMode.CLASSIC,
                selected = selected == VisualMode.CLASSIC,
                onSelect = { onSelect(VisualMode.CLASSIC) },
                modifier = Modifier.weight(1f),
            )
            VisualModeCard(
                mode = VisualMode.TOUCH,
                selected = selected == VisualMode.TOUCH,
                onSelect = { onSelect(VisualMode.TOUCH) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun VisualModeCard(
    mode: VisualMode,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = mode.localizedDisplayName()
    Card(
        onClick = onSelect,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) ClaroColors.PaleBlue else Color.White,
        ),
        border = BorderStroke(
            width = if (selected) 3.dp else 2.dp,
            color = if (selected) ClaroColors.Blue else ClaroColors.Border,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SelectionDot(selected = selected)
                Text(
                    text = label,
                    color = ClaroColors.Ink,
                    fontSize = 19.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
            ModePreview(mode = mode)
            Text(
                text = stringResource(
                    if (mode == VisualMode.TOUCH) {
                        R.string.onb_touch_mode_card_summary
                    } else {
                        R.string.onb_classic_mode_card_summary
                    },
                ),
                color = ClaroColors.Muted,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                minLines = 2,
            )
            Text(
                text = stringResource(
                    if (selected) R.string.onb_mode_selected else R.string.onb_tap_to_choose,
                ),
                color = if (selected) ClaroColors.Blue else ClaroColors.Muted,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ModePreview(mode: VisualMode) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        if (mode == VisualMode.TOUCH) {
            TouchShellPreview()
        } else {
            ClassicShellPreview()
        }
    }
}

@Composable
private fun TouchShellPreview() {
    val tileRows = listOf(
        listOf(
            Triple(ClaroColors.PaleGreen, ClaroColors.Green, ClaroIconKind.PHONE),
            Triple(ClaroColors.PaleBlue, ClaroColors.Blue, ClaroIconKind.MESSAGES),
        ),
        listOf(
            Triple(ClaroColors.PaleOrange, ClaroColors.Orange, ClaroIconKind.CAMERA),
            Triple(ClaroColors.PaleViolet, ClaroColors.Violet, ClaroIconKind.GALLERY),
        ),
        listOf(
            Triple(ClaroColors.PaleRed, ClaroColors.Red, ClaroIconKind.SOS),
            Triple(ClaroColors.AppsPale, ClaroColors.AppsInk, ClaroIconKind.APPS),
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(206.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(3.dp, ClaroColors.Charcoal, RoundedCornerShape(10.dp))
            .background(ClaroColors.Charcoal)
            .padding(3.dp)
            .clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(ClaroColors.Charcoal)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(8.dp)
                    .border(1.dp, Color.White)
                    .padding(1.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(18.dp)
                        .background(Color(0xFF3BE564)),
                )
            }
            Text(
                text = stringResource(R.string.onb_preview_sample_time),
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            ClaroIcon(
                kind = ClaroIconKind.SETTINGS,
                tint = Color.White,
                size = 12.dp,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(Color(0xFFF0F3F8)),
                    contentAlignment = Alignment.Center,
                ) {
                    ClaroAvatar(
                        name = (index + 1).toString(),
                        size = 28.dp,
                        paletteSeed = index,
                    )
                }
            }
        }
        tileRows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(43.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                row.forEach { (bg, fg, iconKind) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        ClaroIcon(
                            kind = iconKind,
                            tint = fg,
                            size = 24.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicShellPreview() {
    val keypadRows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#"),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(206.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(3.dp, ClaroColors.Charcoal, RoundedCornerShape(10.dp))
            .background(ClaroColors.Charcoal)
            .padding(3.dp)
            .clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                .background(Color.White)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.onb_preview_sample_time),
                    color = ClaroColors.Ink,
                    fontSize = 13.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.onb_classic_preview_date),
                    color = ClaroColors.Muted,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(Color(0xFFF0F3F8)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ClaroAvatar(
                            name = (index + 1).toString(),
                            size = 24.dp,
                            paletteSeed = index,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(ClaroColors.PaleBlue, RoundedCornerShape(2.dp)),
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClaroIcon(
                        kind = ClaroIconKind.MESSAGES,
                        tint = ClaroColors.Blue,
                        size = 11.dp,
                    )
                    Text(
                        text = stringResource(R.string.onb_classic_preview_sms),
                        color = ClaroColors.Blue,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(ClaroColors.Red, RoundedCornerShape(2.dp)),
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClaroIcon(
                        kind = ClaroIconKind.SOS,
                        tint = Color.White,
                        size = 11.dp,
                    )
                    Text(
                        text = stringResource(R.string.onb_classic_preview_sos),
                        color = Color.White,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF435066), RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ClaroIcon(
                        kind = ClaroIconKind.ARROW_LEFT,
                        tint = Color.White,
                        size = 11.dp,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(ClaroColors.Green, RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ClaroIcon(
                        kind = ClaroIconKind.PHONE,
                        tint = Color.White,
                        size = 12.dp,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF3A4659), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF8997AA), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ClaroIcon(
                    kind = ClaroIconKind.ARROW_UP,
                    tint = Color.White,
                    size = 8.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp),
                )
                ClaroIcon(
                    kind = ClaroIconKind.ARROW_DOWN,
                    tint = Color.White,
                    size = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp),
                )
                ClaroIcon(
                    kind = ClaroIconKind.ARROW_LEFT,
                    tint = Color.White,
                    size = 8.dp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 2.dp),
                )
                ClaroIcon(
                    kind = ClaroIconKind.ARROW_RIGHT,
                    tint = Color.White,
                    size = 8.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp),
                )
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(16.dp)
                        .background(Color(0xFF61738B), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFFB6C4D6), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.onb_classic_preview_ok),
                        color = Color.White,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF435066), RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ClaroIcon(
                        kind = ClaroIconKind.APPS,
                        tint = Color.White,
                        size = 11.dp,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(ClaroColors.Red, RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ClaroIcon(
                        kind = ClaroIconKind.HOME,
                        tint = Color.White,
                        size = 12.dp,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            keypadRows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    row.forEach { digit ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(Color(0xFF344054), RoundedCornerShape(3.dp))
                                .border(0.5.dp, Color(0xFF66758C), RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = digit,
                                color = Color.White,
                                fontSize = 9.sp,
                                lineHeight = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageDisplayFields(
    language: AppLanguage,
    textSize: TextSize,
    onOpenPicker: (DisplayPicker) -> Unit,
    hapticFeedback: Boolean = true,
    onHapticFeedbackChange: (Boolean) -> Unit = {},
) {
    ClaroRowButton(
        title = stringResource(R.string.onb_section_language),
        subtitle = language.displayName(),
        leading = { Text(text = language.flag(), fontSize = 32.sp, modifier = Modifier.width(52.dp)) },
        onClick = { onOpenPicker(DisplayPicker.LANGUAGE) },
    )
    ClaroRowButton(
        title = stringResource(R.string.onb_section_text_size),
        subtitle = textSize.displayName(),
        leading = { TextSizeSample(textSize) },
        onClick = { onOpenPicker(DisplayPicker.TEXT_SIZE) },
    )
    Spacer(Modifier.height(4.dp))
    ToggleRow(
        label = stringResource(R.string.onb_keypad_and_touch_vibration),
        checked = hapticFeedback,
        onChecked = onHapticFeedbackChange,
    )
    Spacer(Modifier.height(4.dp))
    LanguageLivePreviewCard(
        language = language,
        textSize = textSize,
    )
}

internal enum class DisplayPicker { LANGUAGE, TEXT_SIZE }

@Composable
private fun DisplayPickerScreen(
    picker: DisplayPicker,
    language: AppLanguage,
    textSize: TextSize,
    onLanguageChange: (AppLanguage) -> Unit,
    onTextSizeChange: (TextSize) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    ClaroScreenScaffold(
        title = stringResource(
            if (picker == DisplayPicker.LANGUAGE) R.string.onb_section_language else R.string.onb_section_text_size,
        ),
        modifier = Modifier.testTag("onboarding-screen"),
        dockInSoftkeys = true,
        dock = {
            ClaroSecondaryButton(
                label = stringResource(R.string.onb_cancel),
                softkey = ClaroSoftkey.BACK,
                onClick = onClose,
            )
        },
    ) {
        if (picker == DisplayPicker.LANGUAGE) {
            AppLanguage.entries.forEach { option ->
                ChoiceRow(
                    label = option.displayName(),
                    leading = { Text(text = option.flag(), fontSize = 32.sp, modifier = Modifier.width(52.dp)) },
                    selected = language == option,
                    onClick = {
                        onLanguageChange(option)
                        onClose()
                    },
                )
            }
        } else {
            TextSize.entries.forEach { option ->
                ChoiceRow(
                    label = option.displayName(),
                    leading = { TextSizeSample(option) },
                    selected = textSize == option,
                    onClick = {
                        onTextSizeChange(option)
                        onClose()
                    },
                )
            }
        }
    }
}

@Composable
private fun TextSizeSample(size: TextSize) {
    Text(
        text = stringResource(R.string.onb_text_size_sample),
        color = ClaroColors.Blue,
        fontWeight = FontWeight.Bold,
        fontSize = when (size) {
            TextSize.STANDARD -> 22.sp
            TextSize.LARGE -> 28.sp
            TextSize.HUGE -> 34.sp
        },
        modifier = Modifier.width(52.dp),
    )
}

private fun AppLanguage.flag() = when (this) {
    AppLanguage.ENGLISH -> "\uD83C\uDDFA\uD83C\uDDF8"
    AppLanguage.SPANISH -> "\uD83C\uDDEA\uD83C\uDDF8"
    AppLanguage.HEBREW -> "\uD83C\uDDEE\uD83C\uDDF1"
}

@Composable
private fun LanguageLivePreviewCard(
    language: AppLanguage,
    textSize: TextSize,
) {
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val previewLocale = remember(language) { language.toLocale() }
    val previewDirection = if (language == AppLanguage.HEBREW) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    val previewConfig = remember(baseConfig, previewLocale) {
        @android.annotation.SuppressLint("AppBundleLocaleChanges")
        Configuration(baseConfig).apply {
            setLocale(previewLocale)
            setLayoutDirection(previewLocale)
        }
    }
    val previewContext = remember(baseContext, previewConfig) {
        baseContext.createConfigurationContext(previewConfig)
    }
    val previewScale = when (textSize) {
        TextSize.STANDARD -> 1f
        TextSize.LARGE -> 1.15f
        TextSize.HUGE -> 1.3f
    }
    val bgColor = ClaroColors.InfoBlueBg
    val inkColor = ClaroColors.InfoBlueInk
    val borderColor = ClaroColors.Blue

    CompositionLocalProvider(
        LocalConfiguration provides previewConfig,
        LocalContext provides previewContext,
        LocalLayoutDirection provides previewDirection,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor, RoundedCornerShape(4.dp))
                .border(2.dp, borderColor, RoundedCornerShape(4.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.onb_preview_heading),
                color = inkColor,
                fontSize = (24 * previewScale).sp,
                lineHeight = (29 * previewScale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.onb_preview_labels),
                color = inkColor,
                fontSize = (20 * previewScale).sp,
                lineHeight = (25 * previewScale).sp,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = stringResource(R.string.onb_preview_sample_phone_time),
                    color = inkColor,
                    fontSize = (19 * previewScale).sp,
                    lineHeight = (24 * previewScale).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun AccessSetupFields(
    mode: SettingsAccessMode,
    onModeChange: (SettingsAccessMode) -> Unit,
    pin: String,
    onPinChange: (String) -> Unit,
    confirmation: String,
    onConfirmationChange: (String) -> Unit,
    recovery: String,
    onRecoveryChange: (String) -> Unit,
) {
    ChoiceRow(
        label = stringResource(R.string.onb_date_question_option_title),
        subtitle = stringResource(R.string.onb_date_question_option_subtitle),
        selected = mode == SettingsAccessMode.DATE_QUESTION,
        onClick = { onModeChange(SettingsAccessMode.DATE_QUESTION) },
    )
    ChoiceRow(
        label = stringResource(R.string.onb_caregiver_pin_option_title),
        subtitle = stringResource(R.string.onb_caregiver_pin_option_subtitle),
        selected = mode == SettingsAccessMode.CAREGIVER_PIN,
        onClick = { onModeChange(SettingsAccessMode.CAREGIVER_PIN) },
    )
    if (mode == SettingsAccessMode.DATE_QUESTION) {
        ClaroInfoCard(
            text = stringResource(R.string.onb_date_question_info),
            tone = ClaroBannerTone.INFO,
        )
    } else {
        ClaroInfoCard(
            text = stringResource(R.string.onb_caregiver_pin_warning),
            tone = ClaroBannerTone.WARNING,
        )
        ClaroPinField(
            label = stringResource(R.string.onb_six_digit_pin_label),
            value = pin,
            onValueChange = onPinChange,
        )
        ClaroPinField(
            label = stringResource(R.string.onb_confirm_six_digit_pin_label),
            value = confirmation,
            onValueChange = onConfirmationChange,
        )
        ClaroSecretField(
            label = stringResource(R.string.onb_recovery_secret_with_min_label),
            value = recovery,
            onValueChange = onRecoveryChange,
            supportingText = stringResource(
                R.string.onb_recovery_secret_min_progress,
                recovery.length,
            ),
        )
    }
}

@Composable
private fun ReadinessRows(
    readiness: Map<ReadinessCapability, CapabilityStatus>,
    onRequest: (ReadinessCapability) -> Unit,
) {
    ReadinessCapability.entries.forEach { capability ->
        val status = readiness[capability] ?: CapabilityStatus.NEEDS_ATTENTION
        val statusColor = when (status) {
            CapabilityStatus.READY -> ClaroColors.Green
            CapabilityStatus.NEEDS_ATTENTION -> ClaroColors.Orange
            CapabilityStatus.UNAVAILABLE -> ClaroColors.Muted
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ClaroColors.RowSurface)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClaroIcon(
                    kind = capability.iconKind(),
                    tint = if (status == CapabilityStatus.READY) ClaroColors.Green else ClaroColors.Blue,
                    size = 30.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = capability.displayName(),
                        color = ClaroColors.Ink,
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = status.displayName(),
                        color = statusColor,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (status != CapabilityStatus.READY) {
                ClaroSecondaryButton(
                    label = stringResource(
                        if (status == CapabilityStatus.UNAVAILABLE) {
                            R.string.onb_open_android_settings
                        } else {
                            R.string.onb_allow_or_repair
                        },
                    ),
                    onClick = { onRequest(capability) },
                )
            }
        }
        HorizontalDivider(thickness = 2.dp, color = ClaroColors.Charcoal)
    }
}

@Composable
private fun ResetScopeCard(
    heading: String,
    accentColor: Color,
    backgroundColor: Color,
    items: List<String>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .border(1.dp, accentColor, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = heading,
            color = accentColor,
            fontSize = 21.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        items.forEach { item ->
            Text(
                text = stringResource(R.string.onb_bullet_item, item),
                color = ClaroColors.Ink,
                fontSize = 19.sp,
                lineHeight = 24.sp,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = ClaroColors.Ink,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SelectionDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(
                width = 2.dp,
                color = if (selected) ClaroColors.Blue else ClaroColors.FieldBorder,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(ClaroColors.Blue, CircleShape),
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val haptics = LocalClaroHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ClaroDimensions.PrimaryTarget)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) ClaroColors.PaleBlue else ClaroColors.RowSurface)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) ClaroColors.Blue else ClaroColors.Border,
                shape = RoundedCornerShape(4.dp),
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = {
                    haptics.keyClick()
                    onClick()
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SelectionDot(selected = selected)
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = ClaroColors.Ink,
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = ClaroColors.Muted,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    val haptics = LocalClaroHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ClaroColors.Surface)
            .border(1.dp, ClaroColors.Border, RoundedCornerShape(4.dp))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = {
                    haptics.keyClick()
                    onChecked(it)
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = ClaroColors.Ink,
            fontSize = 23.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun PinIndicatorDots(filledCount: Int) {
    val pinDigitsDescription = stringResource(
        R.string.onb_pin_digits_entered,
        filledCount.coerceIn(0, 6),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics {
                contentDescription = pinDigitsDescription
            },
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(6) { index ->
            val filled = index < filledCount
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        color = if (filled) ClaroColors.Blue else Color.Transparent,
                        shape = CircleShape,
                    )
                    .border(2.dp, ClaroColors.Blue, CircleShape),
            )
        }
    }
}

@Composable
private fun ClaroDateField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        OutlinedTextField(
            value = TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            ),
            onValueChange = { next ->
                onValueChange(formatDateDigits(next.text))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.onb_date_format_pattern)) },
            supportingText = {
                Text(stringResource(R.string.onb_date_field_supporting))
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
            ),
            colors = claroTextFieldColors(),
            singleLine = true,
        )
    }
}

@Composable
private fun ClaroPinField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        OutlinedTextField(
            value = value,
            onValueChange = { raw ->
                onValueChange(raw.filter(Char::isDigit).take(6))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
            ),
            colors = claroTextFieldColors(),
            singleLine = true,
        )
    }
}

@Composable
private fun ClaroSecretField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = supportingText?.let { note -> { Text(note) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = claroTextFieldColors(),
        singleLine = true,
    )
}

@Composable
private fun claroTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ClaroColors.Blue,
    unfocusedBorderColor = ClaroColors.FieldBorder,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedTextColor = ClaroColors.Ink,
    unfocusedTextColor = ClaroColors.Ink,
)

private fun formatDateDigits(raw: String): String {
    val digits = raw.filter(Char::isDigit).take(8)
    return buildString {
        append(digits.take(2))
        if (digits.length > 2) {
            append('/')
            append(digits.substring(2, minOf(4, digits.length)))
        }
        if (digits.length > 4) {
            append('/')
            append(digits.substring(4, digits.length))
        }
    }
}

private fun messageBannerTone(message: String?): ClaroBannerTone {
    if (message.isNullOrBlank()) return ClaroBannerTone.INFO
    val lower = message.lowercase()
    return when {
        "changed" in lower || "saved" in lower ||
            "actualizado" in lower || "guardado" in lower ||
            "שונה" in lower || "נשמר" in lower -> ClaroBannerTone.SUCCESS
        "try again in" in lower || "paused" in lower || "will change after" in lower ||
            "intentarlo en" in lower || "en pausa" in lower || "cambiará después" in lower ||
            "נסו שוב בעוד" in lower || "מושהה" in lower || "ישתנה לאחר" in lower ->
            ClaroBannerTone.WARNING
        else -> ClaroBannerTone.ERROR
    }
}

@Composable
private fun OnboardingStep.title() = stringResource(
    when (this) {
        OnboardingStep.WELCOME -> R.string.onboarding_welcome
        OnboardingStep.PHONE_STYLE -> R.string.onb_step_choose_phone_style
        OnboardingStep.LANGUAGE_AND_DISPLAY -> R.string.onb_language_and_display_title
        OnboardingStep.SETTINGS_ACCESS -> R.string.onb_settings_access
        OnboardingStep.PEOPLE_AND_SPEED_DIAL -> R.string.onb_step_people_and_speed_dial
        OnboardingStep.SOS_CONTACT -> R.string.onb_sos_contact_title
        OnboardingStep.ROLE_READINESS -> R.string.onb_step_permissions_and_roles
        OnboardingStep.HANDOVER -> R.string.onb_step_handover_checklist
        OnboardingStep.COMPLETE -> R.string.onb_step_setup_complete
    },
)

@Composable
private fun VisualMode.localizedDisplayName() = when (this) {
    VisualMode.TOUCH -> stringResource(R.string.touch_home)
    VisualMode.CLASSIC -> stringResource(R.string.classic_phone)
}

@Composable
private fun AppLanguage.displayName() = stringResource(
    when (this) {
        AppLanguage.ENGLISH -> R.string.onb_language_english
        AppLanguage.SPANISH -> R.string.onb_language_spanish
        AppLanguage.HEBREW -> R.string.onb_language_hebrew
    },
)

@Composable
private fun TextSize.displayName() = stringResource(
    when (this) {
        TextSize.STANDARD -> R.string.onb_text_size_standard
        TextSize.LARGE -> R.string.onb_text_size_large
        TextSize.HUGE -> R.string.onb_text_size_huge
    },
)

@Composable
private fun ReadinessCapability.displayName() = stringResource(
    when (this) {
        ReadinessCapability.HOME -> R.string.onb_capability_home
        ReadinessCapability.PHONE -> R.string.onb_capability_phone
        ReadinessCapability.MESSAGES -> R.string.onb_capability_messages
        ReadinessCapability.CONTACTS -> R.string.onb_capability_contacts
        ReadinessCapability.NOTIFICATIONS -> R.string.onb_capability_notifications
        ReadinessCapability.MEDIA -> R.string.onb_capability_media
    },
)

private fun ReadinessCapability.iconKind() = when (this) {
    ReadinessCapability.HOME -> ClaroIconKind.HOME
    ReadinessCapability.PHONE -> ClaroIconKind.PHONE
    ReadinessCapability.MESSAGES -> ClaroIconKind.MESSAGES
    ReadinessCapability.CONTACTS -> ClaroIconKind.CONTACT
    ReadinessCapability.NOTIFICATIONS -> ClaroIconKind.CLOCK
    ReadinessCapability.MEDIA -> ClaroIconKind.GALLERY
}

@Composable
private fun CapabilityStatus.displayName() = stringResource(
    when (this) {
        CapabilityStatus.READY -> R.string.onb_capability_status_ready
        CapabilityStatus.NEEDS_ATTENTION -> R.string.onb_capability_status_needs_attention
        CapabilityStatus.UNAVAILABLE -> R.string.onb_capability_status_unavailable
    },
)

@Composable
private fun HandoverCheck.displayName() = stringResource(
    when (this) {
        HandoverCheck.OUTGOING_CALL -> R.string.onb_handover_outgoing_call
        HandoverCheck.INCOMING_CALL -> R.string.onb_handover_incoming_call
        HandoverCheck.SMS_SEND -> R.string.onb_handover_sms_send
        HandoverCheck.SMS_RECEIVE -> R.string.onb_handover_sms_receive
        HandoverCheck.DEFAULT_SIM -> R.string.onb_handover_default_sim
        HandoverCheck.RINGTONE -> R.string.onb_handover_ringtone
        HandoverCheck.HOME_RETURN -> R.string.onb_handover_home_return
        HandoverCheck.SOS_TEST -> R.string.onb_handover_sos_test
    },
)

@Composable
private fun readinessSummary(preferences: ClaroPreferences): String {
    val ready = preferences.onboarding.readiness.values.count { it == CapabilityStatus.READY }
    return stringResource(
        R.string.onb_readiness_summary,
        ready,
        ReadinessCapability.entries.size,
    )
}

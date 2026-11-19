package com.clarohome.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.clarohome.core.domain.ClaroPreferencesRepository
import com.clarohome.core.domain.ClaroEvent
import com.clarohome.core.domain.EventOutcome
import com.clarohome.core.model.ClaroPreferences
import com.clarohome.core.model.OnboardingStep
import com.clarohome.feature.billing.observability.ConsentAwareObservability
import com.clarohome.feature.billing.observability.ConsentController
import com.clarohome.feature.communication.SmsTransport
import com.clarohome.feature.communication.observeSmsDeliveryTelemetry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class AppTelemetry @Inject constructor(
    private val observability: ConsentAwareObservability,
    private val consent: ConsentController,
    private val preferences: ClaroPreferencesRepository,
    private val smsTransport: SmsTransport,
) : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startedActivities = 0
    private var backgroundedAt: Long? = null
    private var session = 0L

    fun start(application: Application) {
        observability.initialize()
        observeSmsDeliveryTelemetry(smsTransport, observability, observability, scope)
        application.registerActivityLifecycleCallbacks(this)
        scope.launch {
            var previous: ClaroPreferences? = null
            preferences.preferences.collect { current ->
                observability.updateContext(current.visualMode, current.textSize)
                previous?.let { old ->
                    if (old.visualMode != current.visualMode) {
                        observability.track(ClaroEvent.VisualModeSelected(current.visualMode))
                    }
                    if (old.textSize != current.textSize) {
                        observability.track(ClaroEvent.AccessibilityPresetSelected(current.textSize))
                    }
                    (current.onboarding.completedSteps - old.onboarding.completedSteps).forEach {
                        observability.track(ClaroEvent.SetupStepResult(it, EventOutcome.COMPLETED))
                    }
                    if (old.onboarding.currentStep != OnboardingStep.COMPLETE &&
                        current.onboarding.currentStep == OnboardingStep.COMPLETE
                    ) {
                        observability.track(ClaroEvent.SetupCompleted)
                    }
                }
                previous = current
            }
        }
        scope.launch {
            consent.consent.collect {
                if (startedActivities > 0) observability.foregroundSession(session)
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities++ == 0) {
            val now = SystemClock.elapsedRealtime()
            val backgroundStart = backgroundedAt
            if (session == 0L || (backgroundStart != null && now - backgroundStart >= SESSION_TIMEOUT_MILLIS)) {
                session++
            }
            backgroundedAt = null
            observability.foregroundSession(session)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities--
        if (startedActivities == 0 && !activity.isChangingConfigurations) {
            backgroundedAt = SystemClock.elapsedRealtime()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private companion object {
        const val SESSION_TIMEOUT_MILLIS = 30 * 60 * 1_000L
    }
}

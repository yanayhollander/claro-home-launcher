package com.clarohome.feature.communication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.telecom.CallAudioState
import android.util.Log

@Suppress("DEPRECATION")
internal class AutomaticCallAudio(
    context: Context,
    private val requestRoute: (AudioRoute) -> Unit,
) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val proximity = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val audio = context.getSystemService(AudioManager::class.java)
    private var enabled = false
    private var maximumSpeakerVolume = false
    private var listening = false
    private var near: Boolean? = null
    private var manuallyControlled = false
    private var hasCall = false
    private var connected = false
    private var state: CallAudioState? = null
    private var requestedRoute: AudioRoute? = null
    private var originalVolume: Int? = null

    fun configure(enabled: Boolean, maximumSpeakerVolume: Boolean) {
        this.enabled = enabled
        this.maximumSpeakerVolume = maximumSpeakerVolume
        update(state, hasCall, connected)
    }

    fun update(state: CallAudioState?, hasCall: Boolean, connected: Boolean) {
        if (this.state?.route != state?.route) requestedRoute = null
        this.state = state
        this.hasCall = hasCall
        this.connected = connected
        if (!hasCall || !enabled) {
            stopListening()
            restoreVolume()
            if (!hasCall) manuallyControlled = false
            return
        }
        if (!listening && proximity != null && proximity.maximumRange > 0f) {
            near = null
            listening = sensors?.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL) == true
            if (!listening) Log.w(TAG, "Proximity monitoring could not be started.")
        }
        apply()
    }

    fun manualOverride() {
        manuallyControlled = true
        restoreVolume()
    }

    fun detach() {
        stopListening()
        restoreVolume()
        state = null
        requestedRoute = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values.firstOrNull()
        near = if (value != null && value.isFinite()) {
            value < minOf(5f, event.sensor.maximumRange)
        } else {
            null
        }
        apply()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun apply() {
        val current = state ?: return
        if (!enabled || !hasCall) return
        val externalAudio = current.supportedRouteMask and
            (CallAudioState.ROUTE_BLUETOOTH or CallAudioState.ROUTE_WIRED_HEADSET) != 0
        val route = automaticCallRoute(listening, near, externalAudio, manuallyControlled)
        if (route == null) {
            restoreVolume()
            return
        }
        val platformRoute = if (route == AudioRoute.SPEAKER) {
            CallAudioState.ROUTE_SPEAKER
        } else {
            CallAudioState.ROUTE_EARPIECE
        }
        if (current.supportedRouteMask and platformRoute == 0) {
            restoreVolume()
            return
        }
        if (route != AudioRoute.SPEAKER || !connected || !maximumSpeakerVolume) restoreVolume()
        if (current.route != platformRoute && requestedRoute != route) {
            requestedRoute = route
            requestRoute(route)
        }
        if (route == AudioRoute.SPEAKER && current.route == platformRoute &&
            connected && maximumSpeakerVolume && originalVolume == null
        ) {
            val manager = audio ?: return
            if (manager.isVolumeFixed) {
                Log.w(TAG, "Call volume is fixed by this device.")
                return
            }
            val previous = manager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            try {
                manager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maximum, 0)
                originalVolume = previous
            } catch (_: SecurityException) {
                Log.w(TAG, "Android denied changing call volume.")
            }
        }
    }

    private fun restoreVolume() {
        val previous = originalVolume ?: return
        val manager = audio ?: return
        try {
            // Preserve any volume adjustment the user made after the automatic increase.
            if (manager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) ==
                manager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            ) {
                manager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, previous, 0)
            }
            originalVolume = null
        } catch (_: SecurityException) {
            Log.w(TAG, "Android denied restoring call volume.")
        }
    }

    private fun stopListening() {
        if (listening) sensors?.unregisterListener(this)
        listening = false
        near = null
        requestedRoute = null
    }

    private companion object {
        const val TAG = "ClaroCallAudio"
    }
}

internal fun automaticCallRoute(
    sensorAvailable: Boolean,
    near: Boolean?,
    externalAudio: Boolean = false,
    manuallyControlled: Boolean = false,
): AudioRoute? = when {
    externalAudio || manuallyControlled -> null
    sensorAvailable && near == false -> AudioRoute.SPEAKER
    else -> AudioRoute.EARPIECE
}

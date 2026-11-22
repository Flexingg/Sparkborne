package com.sparkborne.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Low-power hardware step counter (`Sensor.TYPE_STEP_COUNTER`) tracker.
 *
 * Tracks today's cumulative phone steps (safely handling midnight date rollover and device reboots)
 * so steps tick into the Step Bank immediately while reconciling cleanly with Garmin/SparkyFitness.
 */
class LiveStepTracker(
    private val context: Context,
    private val onTodayStepsChanged: (date: String, totalPhoneStepsToday: Int) -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var isRegistered = false

    val isHardwareSupported: Boolean
        get() = stepCounterSensor != null

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun start(): Boolean {
        if (!isHardwareSupported || !hasPermission() || isRegistered) return isRegistered
        val registered = sensorManager?.registerListener(
            this,
            stepCounterSensor,
            SensorManager.SENSOR_DELAY_UI,
        ) == true
        isRegistered = registered
        return registered
    }

    fun stop() {
        if (!isRegistered) return
        sensorManager?.unregisterListener(this)
        isRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val rawSinceBoot = event?.values?.firstOrNull() ?: return
        val today = LocalDate.now().toString()

        val savedDate = prefs.getString(KEY_DATE, null)
        var baseline = prefs.getFloat(KEY_BASELINE, -1f)
        var carriedBeforeReboot = prefs.getInt(KEY_CARRIED, 0)

        if (savedDate != today || baseline < 0f) {
            // New calendar day or very first reading: anchor baseline to current sensor reading
            baseline = rawSinceBoot
            carriedBeforeReboot = 0
            prefs.edit()
                .putString(KEY_DATE, today)
                .putFloat(KEY_BASELINE, baseline)
                .putInt(KEY_CARRIED, 0)
                .putInt(KEY_LAST_TOTAL, 0)
                .apply()
            return
        }

        if (rawSinceBoot < baseline) {
            // Device rebooted mid-day: preserve previous total and re-anchor baseline
            carriedBeforeReboot = prefs.getInt(KEY_LAST_TOTAL, 0)
            baseline = rawSinceBoot
            prefs.edit()
                .putFloat(KEY_BASELINE, baseline)
                .putInt(KEY_CARRIED, carriedBeforeReboot)
                .apply()
        }

        val todayTotal = (carriedBeforeReboot + (rawSinceBoot - baseline).roundToInt()).coerceAtLeast(0)
        val lastReported = prefs.getInt(KEY_LAST_TOTAL, 0)
        if (todayTotal > lastReported) {
            prefs.edit().putInt(KEY_LAST_TOTAL, todayTotal).apply()
            onTodayStepsChanged(today, todayTotal)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val PREFS_NAME = "sparkborne_live_pedometer"
        private const val KEY_DATE = "baseline_date"
        private const val KEY_BASELINE = "baseline_since_boot"
        private const val KEY_CARRIED = "carried_before_reboot"
        private const val KEY_LAST_TOTAL = "last_today_total"
    }
}

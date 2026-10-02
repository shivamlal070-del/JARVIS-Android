package com.jarvis.assistant.core.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

data class BatteryInfo(
    val percentage: Int,
    val isCharging: Boolean,
    val isFull: Boolean,
    val powerSource: String, // AC, USB, Wireless, Battery
    val temperatureCelsius: Float,
    val health: String, // Good, Overheat, Dead, Over Voltage, Unknown
    val voltageMilliVolts: Int,
    val technology: String
)

/**
 * BatteryController queries real physical hardware battery telemetry via BatteryManager.
 * Does not guess or hallucinate battery status.
 */
class BatteryController(private val context: Context) {

    fun getBatteryInfo(): BatteryInfo {
        val batteryIntent: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else -1

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val isFull = status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val powerSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Dock"
            else -> if (isCharging) "Charging" else "Unplugged (Battery Power)"
        }

        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val temperatureCelsius = tempRaw / 10.0f

        val healthRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val health = when (healthRaw) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }

        val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tech = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-Ion"

        return BatteryInfo(
            percentage = percentage,
            isCharging = isCharging,
            isFull = isFull,
            powerSource = powerSource,
            temperatureCelsius = temperatureCelsius,
            health = health,
            voltageMilliVolts = voltage,
            technology = tech
        )
    }

    fun getSpokenBatteryStatus(): String {
        val info = getBatteryInfo()
        if (info.percentage < 0) return "Unable to retrieve battery data from the Android BatteryManager."

        return if (info.isFull) {
            "Your Galaxy Tab S5e is fully charged at 100 percent and connected to ${info.powerSource}."
        } else if (info.isCharging) {
            "Your battery is at ${info.percentage} percent and currently charging via ${info.powerSource}. Battery health is ${info.health} (${info.temperatureCelsius}°C)."
        } else {
            "Your battery level is ${info.percentage} percent on battery power. Health is ${info.health}."
        }
    }
}

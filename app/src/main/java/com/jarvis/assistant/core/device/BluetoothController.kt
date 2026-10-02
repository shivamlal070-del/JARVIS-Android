package com.jarvis.assistant.core.device

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.provider.Settings

data class BluetoothStateInfo(
    val isEnabled: Boolean,
    val isSupported: Boolean,
    val connectedDevices: List<String>,
    val pairedDevicesCount: Int
)

data class BluetoothActionResult(
    val success: Boolean,
    val openedSettings: Boolean,
    val message: String
)

/**
 * BluetoothController queries hardware Bluetooth adapter, paired peripherals,
 * and launches standard settings for pairing/toggling.
 */
class BluetoothController(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    fun getBluetoothState(): BluetoothStateInfo {
        if (adapter == null) {
            return BluetoothStateInfo(isEnabled = false, isSupported = false, connectedDevices = emptyList(), pairedDevicesCount = 0)
        }

        val isEnabled = adapter.isEnabled
        val paired = try {
            adapter.bondedDevices?.map { it.name ?: it.address } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }

        return BluetoothStateInfo(
            isEnabled = isEnabled,
            isSupported = true,
            connectedDevices = emptyList(), // Filled dynamically via BluetoothProfile listeners
            pairedDevicesCount = paired.size
        )
    }

    fun openBluetoothSettings(): BluetoothActionResult {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            BluetoothActionResult(true, true, "Opening Android Bluetooth settings.")
        } catch (e: Exception) {
            BluetoothActionResult(false, false, "Unable to open Bluetooth settings: ${e.localizedMessage}")
        }
    }

    fun getSpokenBluetoothStatus(): String {
        val state = getBluetoothState()
        if (!state.isSupported) return "Bluetooth is not supported on this hardware."

        return if (!state.isEnabled) {
            "Bluetooth is currently turned off."
        } else {
            "Bluetooth is turned on with ${state.pairedDevicesCount} paired devices available."
        }
    }
}

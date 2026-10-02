package com.jarvis.assistant.core.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AudioDeviceCategory {
    EARPHONES,
    HEADPHONES,
    SPEAKER,
    HEADSET,
    INTERNAL_TABLET
}

data class KnownAudioDevice(
    val macAddress: String,
    val name: String,
    val category: AudioDeviceCategory,
    val isConnected: Boolean = false,
    val lastConnectedTime: Long = 0L
)

class BluetoothAudioDeviceManager(private val context: Context) {
    private val tag = "BluetoothAudioManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val _knownAudioDevices = MutableStateFlow<List<KnownAudioDevice>>(
        listOf(
            KnownAudioDevice(
                macAddress = "INTERNAL",
                name = "Built-in Super AMOLED Quad Speakers",
                category = AudioDeviceCategory.INTERNAL_TABLET,
                isConnected = true
            ),
            KnownAudioDevice(
                macAddress = "00:11:22:33:44:55",
                name = "Galaxy Buds Pro (Earphones)",
                category = AudioDeviceCategory.EARPHONES,
                isConnected = false
            ),
            KnownAudioDevice(
                macAddress = "00:11:22:AA:BB:CC",
                name = "Sony WH-1000XM4 (Headphones)",
                category = AudioDeviceCategory.HEADPHONES,
                isConnected = false
            ),
            KnownAudioDevice(
                macAddress = "00:11:22:DD:EE:FF",
                name = "JBL Flip (Bluetooth Speaker)",
                category = AudioDeviceCategory.SPEAKER,
                isConnected = false
            )
        )
    )
    val knownAudioDevices: StateFlow<List<KnownAudioDevice>> = _knownAudioDevices.asStateFlow()

    private val _activeAudioDevice = MutableStateFlow("Built-in Speakers & Mic")
    val activeAudioDevice: StateFlow<String> = _activeAudioDevice.asStateFlow()

    init {
        refreshPairedDevices()
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        try {
            val bonded = bluetoothAdapter.bondedDevices ?: return
            val currentList = _knownAudioDevices.value.toMutableList()

            for (device in bonded) {
                val name = device.name ?: "Bluetooth Device"
                val category = classifyDevice(name)
                val exists = currentList.any { it.macAddress == device.address }
                if (!exists) {
                    currentList.add(
                        KnownAudioDevice(
                            macAddress = device.address,
                            name = name,
                            category = category,
                            isConnected = false
                        )
                    )
                }
            }
            _knownAudioDevices.value = currentList
        } catch (e: SecurityException) {
            Log.w(tag, "Bluetooth permission missing: ${e.message}")
        }
    }

    /**
     * Handles natural language audio routing commands:
     * "Jarvis, connect my headphones"
     * "Jarvis, use my headset"
     * "Jarvis, use the speaker"
     */
    fun handleSpokenAudioDeviceCommand(query: String): String? {
        val q = query.lowercase(Locale.ROOT)

        val targetCategory: AudioDeviceCategory? = when {
            q.contains("headphone") || q.contains("headphones") -> AudioDeviceCategory.HEADPHONES
            q.contains("earphone") || q.contains("earphones") || q.contains("earbuds") || q.contains("buds") -> AudioDeviceCategory.EARPHONES
            q.contains("speaker") -> AudioDeviceCategory.SPEAKER
            q.contains("headset") -> AudioDeviceCategory.HEADSET
            q.contains("internal") || q.contains("tablet speaker") -> AudioDeviceCategory.INTERNAL_TABLET
            else -> null
        }

        if (targetCategory != null) {
            return routeAudioToCategory(targetCategory)
        }
        return null
    }

    fun routeAudioToCategory(category: AudioDeviceCategory): String {
        if (category == AudioDeviceCategory.INTERNAL_TABLET) {
            stopBluetoothScoRouting()
            _activeAudioDevice.value = "Built-in Super AMOLED Quad Speakers"
            return "Audio output routed to Galaxy Tab S5e internal speakers."
        }

        val device = _knownAudioDevices.value.find { it.category == category }
        if (device == null) {
            return "No paired ${category.name.lowercase()} found in known audio devices. Please pair the device in Android Settings first."
        }

        // On Android 11, route voice mic/audio through Bluetooth SCO if supported
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return "Bluetooth is currently disabled on this tablet. Please enable Bluetooth to connect to ${device.name}."
        }

        return try {
            if (audioManager.isBluetoothScoAvailableOffCall) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                _activeAudioDevice.value = device.name

                // Update state
                _knownAudioDevices.value = _knownAudioDevices.value.map {
                    if (it.macAddress == device.macAddress) it.copy(isConnected = true, lastConnectedTime = System.currentTimeMillis())
                    else it.copy(isConnected = false)
                }
                "Audio successfully routed to ${device.name} via Bluetooth."
            } else {
                "${device.name} is recognized, but Bluetooth SCO voice routing is not currently supported by the active audio policy."
            }
        } catch (e: Exception) {
            "I could not connect to ${device.name}: ${e.message}"
        }
    }

    fun stopBluetoothScoRouting() {
        try {
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
        } catch (e: Exception) {
            Log.w(tag, "Error stopping SCO: ${e.message}")
        }
    }

    private fun classifyDevice(name: String): AudioDeviceCategory {
        val lower = name.lowercase()
        return when {
            lower.contains("buds") || lower.contains("airpod") || lower.contains("earphone") -> AudioDeviceCategory.EARPHONES
            lower.contains("wh-") || lower.contains("headphone") || lower.contains("qc35") || lower.contains("xm") -> AudioDeviceCategory.HEADPHONES
            lower.contains("speaker") || lower.contains("flip") || lower.contains("soundbar") || lower.contains("jbl") -> AudioDeviceCategory.SPEAKER
            else -> AudioDeviceCategory.HEADSET
        }
    }
}

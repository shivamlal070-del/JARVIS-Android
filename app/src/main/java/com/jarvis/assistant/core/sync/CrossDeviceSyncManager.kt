package com.jarvis.assistant.core.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class CrossDeviceResult {
    data class Success(val responseMessage: String, val executionDetails: String) : CrossDeviceResult()
    data class Failed(val honestReason: String) : CrossDeviceResult()
}

class CrossDeviceSyncManager(
    private val context: Context,
    private val deviceIdentityManager: DeviceIdentityManager,
    private val deviceRegistry: DeviceRegistry
) {
    private val tag = "CrossDeviceSync"
    private val prefs = context.getSharedPreferences("jarvis_sync_prefs", Context.MODE_PRIVATE)

    var syncHubUrl: String
        get() = prefs.getString("sync_hub_url", "http://10.0.2.2:3000") ?: "http://10.0.2.2:3000"
        set(value) = prefs.edit().putString("sync_hub_url", value).apply()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun checkDesktopStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$syncHubUrl/api/sync/devices")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val desktopOnline = json.optBoolean("windowsDesktopOnline", false)
                    deviceRegistry.updateDeviceStatus("windows_pc_01", desktopOnline)
                    return@withContext desktopOnline
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Sync hub unreachable: ${e.message}")
        }
        deviceRegistry.updateDeviceStatus("windows_pc_01", false)
        return@withContext false
    }

    suspend fun sendActionToWindows(
        actionType: String, // "LAUNCH_APP", "KEYSTROKE", "VOLUME", "SYSTEM_INFO", "OPEN_URL"
        payload: Map<String, String>
    ): CrossDeviceResult = withContext(Dispatchers.IO) {
        try {
            val isOnline = checkDesktopStatus()
            if (!isOnline) {
                return@withContext CrossDeviceResult.Failed(
                    "The Windows 10 desktop is currently offline or unreachable on the network. I cannot perform that action there."
                )
            }

            val requestJson = JSONObject().apply {
                put("sourceDeviceId", deviceIdentityManager.currentDeviceId)
                put("targetPlatform", "WINDOWS_DESKTOP")
                put("actionType", actionType)
                put("payload", JSONObject(payload))
                put("timestamp", System.currentTimeMillis())
            }

            val request = Request.Builder()
                .url("$syncHubUrl/api/sync/execute")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val resJson = JSONObject(bodyStr)
                    val success = resJson.optBoolean("success", false)
                    val msg = resJson.optString("message", "Executed on Windows desktop.")
                    val details = resJson.optString("details", "")

                    if (success) {
                        return@withContext CrossDeviceResult.Success(msg, details)
                    } else {
                        val error = resJson.optString("error", "Windows client could not perform this operation.")
                        return@withContext CrossDeviceResult.Failed(error)
                    }
                } else {
                    return@withContext CrossDeviceResult.Failed("Windows sync hub returned error code ${response.code}.")
                }
            }
        } catch (e: Exception) {
            return@withContext CrossDeviceResult.Failed(
                "Failed to communicate with Windows desktop: ${e.localizedMessage ?: "Connection timed out"}"
            )
        }
    }
}

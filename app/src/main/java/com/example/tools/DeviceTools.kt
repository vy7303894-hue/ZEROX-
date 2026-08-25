package com.example.tools

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceTools(private val context: Context) {
    companion object {
        private const val TAG = "DeviceTools"
    }

    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    private var isTorchOn = false

    fun toggleFlashlight(enable: Boolean): String {
        return try {
            val cm = cameraManager ?: return "Flashlight is not supported on this device"
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                flashAvailable && facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull() ?: return "No camera flash found"

            cm.setTorchMode(cameraId, enable)
            isTorchOn = enable
            if (enable) "Flashlight turned ON ✨" else "Flashlight turned OFF 🌙"
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight: ${e.message}")
            "Failed to toggle flashlight: ${e.message}"
        }
    }

    fun isFlashlightOn(): Boolean = isTorchOn

    fun openWebsite(url: String): String {
        return try {
            val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Opening $formattedUrl for you babe!"
        } catch (e: Exception) {
            Log.e(TAG, "Error opening URL: ${e.message}")
            "Couldn't open URL: ${e.message}"
        }
    }

    fun searchGoogle(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Searched Google for '$query'. Check it out!"
        } catch (e: Exception) {
            "Couldn't search: ${e.message}"
        }
    }

    fun playMusic(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Putting on '$query' on YouTube for you! 🎶"
        } catch (e: Exception) {
            "Couldn't launch music: ${e.message}"
        }
    }

    fun openClockApp(): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Opened your Clock app ⏰"
        } catch (e: Exception) {
            "Couldn't open clock: ${e.message}"
        }
    }

    fun setTimer(seconds: Int, label: String = "Zoya Timer"): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Timer set for $seconds seconds ($label) ⏳"
        } catch (e: Exception) {
            "Couldn't set timer: ${e.message}"
        }
    }

    fun getDeviceInfo(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = bm?.isCharging ?: false
        val timeStr = SimpleDateFormat("h:mm a, EEEE", Locale.getDefault()).format(Date())
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

        return "Current time: $timeStr. Device: $manufacturer $model. Battery: $batteryPct% ${if (isCharging) "(Charging ⚡)" else ""}"
    }
}

package expo.modules.backgroundstreamer

import android.content.Context
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.File
import expo.modules.core.ExpoModule
import expo.modules.core.errors.CodedException
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

object GlobalStreamObserver {
    private var eventEmitter: Module? = null
    private val startTimes = ConcurrentHashMap<String, Long>()
    private const val TAG = "GlobalStreamObserver"

    fun setEventEmitter(module: Module) {
        eventEmitter = module
    }

    fun clearEventEmitter() {
        eventEmitter = null
    }

    fun onUploadProgress(uploadId: String, bytesWritten: Long, contentLength: Long) {
        try {
            val startTime = startTimes.getOrPut(uploadId) { System.currentTimeMillis() }
            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
            val speed = if (elapsedTime > 0) (bytesWritten / elapsedTime).roundToInt() else 0
            val progress = if (contentLength > 0) (bytesWritten * 100.0 / contentLength).roundToInt() else 0

            val event = mapOf(
                "type" to "uploadProgress",
                "uploadId" to uploadId,
                "bytesWritten" to bytesWritten,
                "contentLength" to contentLength,
                "speed" to speed,
                "progress" to progress
            )

            eventEmitter?.sendEvent("onUploadProgress", event)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending upload progress event: ${e.message}")
        }
    }

    fun onUploadComplete(uploadId: String, responseCode: Int, responseBody: String) {
        try {
            val startTime = startTimes.remove(uploadId) ?: System.currentTimeMillis()
            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0

            val event = mapOf(
                "type" to "uploadComplete",
                "uploadId" to uploadId,
                "responseCode" to responseCode,
                "responseBody" to responseBody,
                "elapsedTime" to elapsedTime
            )

            eventEmitter?.sendEvent("onUploadComplete", event)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending upload complete event: ${e.message}")
        }
    }

    fun onUploadError(uploadId: String, error: String) {
        try {
            startTimes.remove(uploadId)
            val event = mapOf(
                "type" to "uploadError",
                "uploadId" to uploadId,
                "error" to error
            )

            eventEmitter?.sendEvent("onUploadError", event)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending upload error event: ${e.message}")
        }
    }

    fun onDownloadProgress(downloadId: String, bytesRead: Long, contentLength: Long) {
        try {
            val startTime = startTimes.getOrPut(downloadId) { System.currentTimeMillis() }
            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
            val speed = if (elapsedTime > 0) (bytesRead / elapsedTime).roundToInt() else 0
            val progress = if (contentLength > 0) (bytesRead * 100.0 / contentLength).roundToInt() else 0

            val event = mapOf(
                "type" to "downloadProgress",
                "downloadId" to downloadId,
                "bytesRead" to bytesRead,
                "contentLength" to contentLength,
                "speed" to speed,
                "progress" to progress
            )

            eventEmitter?.sendEvent("onDownloadProgress", event)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending download progress event: ${e.message}")
        }
    }

    fun onDownloadComplete(downloadId: String, filePath: String) {
        try {
            val startTime = startTimes.remove(downloadId) ?: System.currentTimeMillis()
            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0

            val event = mapOf(
                "type" to "downloadComplete",
                "downloadId" to downloadId,
                "filePath" to filePath,
                "elapsedTime" to elapsedTime
            )

            eventEmitter?.sendEvent("onDownloadComplete", event)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending download complete event: ${e.message}")
        }
    }

    fun onDownloadError(downloadId: String, error: String) {
        try {
            startTimes.remove(downloadId)
            val event = mapOf(
                "type" to "downloadError",
                "downloadId" to downloadId,
                "error" to error
            )

            eventEmitter?.sendEvent("onDownloadError", event)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending download error event: ${e.message}")
        }
    }

    fun onDebug(message: String, level: String = "info", details: Map<String, Any>? = null) {
        val params = Arguments.createMap().apply {
            putString("message", message)
            putString("level", level)
            putDouble("timestamp", System.currentTimeMillis().toDouble())
            details?.let { putMap("details", Arguments.makeNativeMap(it)) }
        }
        sendEvent("debug", params)
    }

    private fun sendEvent(eventName: String, params: WritableMap) {
        try {
            Log.d(TAG, "Sending event: $eventName")
            if (eventEmitter != null) {
                eventEmitter.emit(eventName, params)
                Log.d(TAG, "Event sent successfully: $eventName")
            } else {
                Log.e(TAG, "Event emitter is null")
                throw CodedException("ERROR", "Event emitter is null", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send event: ${e.message}", e)
            throw CodedException("ERROR", "Failed to send event: ${e.message}", e)
        }
    }
}

// Data classes for tracking transfer info
data class TransferInfo(
    val transferId: String,
    val destinationPath: String,
    val progressPercent: Int,
    val type: TransferType
)

enum class TransferType {
    UPLOAD,
    DOWNLOAD
}
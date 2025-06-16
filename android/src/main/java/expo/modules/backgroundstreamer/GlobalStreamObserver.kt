package expo.modules.backgroundstreamer

import android.content.Context
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.File
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.exception.CodedException
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

    private fun sendEvent(eventName: String, event: Map<String, Any?>) {
        try {
            eventEmitter?.sendEvent(eventName, event)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending event $eventName: ${e.message}")
        }
    }

    fun onUploadProgress(uploadId: String, bytesWritten: Long, contentLength: Long) {
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

        sendEvent("onUploadProgress", event)
    }

    fun onUploadComplete(uploadId: String, responseCode: Int, responseBody: String) {
        val startTime = startTimes.remove(uploadId) ?: System.currentTimeMillis()
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0

        val event = mapOf(
            "type" to "uploadComplete",
            "uploadId" to uploadId,
            "responseCode" to responseCode,
            "responseBody" to responseBody,
            "elapsedTime" to elapsedTime
        )

        sendEvent("onUploadComplete", event)
    }

    fun onUploadError(uploadId: String, error: String) {
        startTimes.remove(uploadId)
        val event = mapOf(
            "type" to "uploadError",
            "uploadId" to uploadId,
            "error" to error
        )

        sendEvent("onUploadError", event)
    }

    fun onDownloadProgress(downloadId: String, bytesRead: Long, contentLength: Long) {
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

        sendEvent("onDownloadProgress", event)
    }

    fun onDownloadComplete(downloadId: String, filePath: String) {
        val startTime = startTimes.remove(downloadId) ?: System.currentTimeMillis()
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0

        val event = mapOf(
            "type" to "downloadComplete",
            "downloadId" to downloadId,
            "filePath" to filePath,
            "elapsedTime" to elapsedTime
        )

        sendEvent("onDownloadComplete", event)
    }

    fun onDownloadError(downloadId: String, error: String) {
        startTimes.remove(downloadId)
        val event = mapOf(
            "type" to "downloadError",
            "downloadId" to downloadId,
            "error" to error
        )

        sendEvent("onDownloadError", event)
    }

    fun onDebug(message: String, level: String = "info", details: Map<String, Any>? = null) {
        val event = mapOf(
            "message" to message,
            "level" to level,
            "timestamp" to System.currentTimeMillis(),
            "details" to details
        )
        sendEvent("debug", event)
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
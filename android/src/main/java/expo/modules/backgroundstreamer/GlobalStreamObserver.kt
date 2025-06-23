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
import kotlinx.coroutines.*
import java.util.concurrent.Executors

object GlobalStreamObserver {
    private var eventEmitter: Module? = null
    private val startTimes = ConcurrentHashMap<String, Long>()
    private const val TAG = "GlobalStreamObserver"
    
    // Single-threaded executor for event emission to prevent threading issues
    private val eventExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GlobalStreamObserver-EventThread").apply {
            isDaemon = true
        }
    }

    fun setEventEmitter(module: Module) {
        eventEmitter = module
    }

    fun clearEventEmitter() {
        eventEmitter = null
    }

    private fun sendEvent(eventName: String, event: Map<String, Any?>) {
        // Dispatch to single-threaded executor to prevent UI thread blocking
        eventExecutor.execute {
            try {
                eventEmitter?.sendEvent(eventName, event)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending event $eventName: ${e.message}")
            }
        }
    }

    fun onUploadProgress(uploadId: String, bytesWritten: Long, contentLength: Long) {
        val startTime = startTimes.getOrPut(uploadId) { System.currentTimeMillis() }
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
        val speed = if (elapsedTime > 0) (bytesWritten / elapsedTime).roundToInt() else 0
        val progress = if (contentLength > 0) (bytesWritten * 100.0 / contentLength).roundToInt() else 0
        val estimatedTimeRemaining = if (speed > 0 && contentLength > 0) 
            ((contentLength - bytesWritten) / speed).toDouble() else 0.0

        val event = mapOf(
            "uploadId" to uploadId,
            "progress" to progress,
            "bytesWritten" to bytesWritten,
            "totalBytes" to contentLength,
            "speed" to speed,
            "estimatedTimeRemaining" to estimatedTimeRemaining
        )

        sendEvent("upload-progress", event)
    }

    fun onUploadComplete(uploadId: String, responseCode: Int, responseBody: String, totalBytes: Long = 0L) {
        val startTime = startTimes.remove(uploadId) ?: System.currentTimeMillis()
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0

        val event = mapOf(
            "uploadId" to uploadId,
            "response" to responseBody,
            "responseHeaders" to emptyMap<String, String>(),
            "responseCode" to responseCode,
            "totalBytes" to totalBytes,
            "duration" to elapsedTime
        )

        sendEvent("upload-complete", event)
    }

    fun onUploadError(uploadId: String, error: String) {
        startTimes.remove(uploadId)
        val event = mapOf(
            "uploadId" to uploadId,
            "error" to error
        )

        sendEvent("error", event)
    }

    fun onDownloadProgress(downloadId: String, bytesRead: Long, contentLength: Long) {
        val startTime = startTimes.getOrPut(downloadId) { System.currentTimeMillis() }
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
        val speed = if (elapsedTime > 0) (bytesRead / elapsedTime).roundToInt() else 0
        val progress = if (contentLength > 0) (bytesRead * 100.0 / contentLength).roundToInt() else 0
        val estimatedTimeRemaining = if (speed > 0 && contentLength > 0) 
            ((contentLength - bytesRead) / speed).toDouble() else 0.0

        val event = mapOf(
            "downloadId" to downloadId,
            "progress" to progress,
            "bytesWritten" to bytesRead,
            "totalBytes" to contentLength,
            "speed" to speed,
            "estimatedTimeRemaining" to estimatedTimeRemaining
        )

        sendEvent("download-progress", event)
    }

    fun onDownloadComplete(downloadId: String, filePath: String) {
        val startTime = startTimes.remove(downloadId) ?: System.currentTimeMillis()
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0

        // Get file info
        val file = File(filePath)
        val fileSize = if (file.exists()) file.length() else 0L
        
        // Basic MIME type detection
        val mimeType = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "application/octet-stream"
        }

        val event = mapOf(
            "downloadId" to downloadId,
            "filePath" to filePath,
            "totalBytes" to fileSize,
            "duration" to elapsedTime,
            "mimeType" to mimeType
        )

        sendEvent("download-complete", event)
    }

    fun onDownloadError(downloadId: String, error: String) {
        startTimes.remove(downloadId)
        val event = mapOf(
            "downloadId" to downloadId,
            "error" to error
        )

        sendEvent("error", event)
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
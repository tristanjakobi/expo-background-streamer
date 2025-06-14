package expo.modules.backgroundstreamer

import android.content.Context
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.File

class GlobalStreamObserver(private val reactContext: ReactApplicationContext) {
    private val TAG = "GlobalStreamObserver"
    private val eventEmitter = reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
    private val startTimes = mutableMapOf<String, Long>()

    fun onUploadProgress(uploadId: String, bytesWritten: Long, totalBytes: Long) {
        val startTime = startTimes[uploadId] ?: System.currentTimeMillis()
        startTimes[uploadId] = startTime
        
        val duration = (System.currentTimeMillis() - startTime) / 1000.0
        val speed = if (duration > 0) bytesWritten / duration else 0.0
        val remainingBytes = totalBytes - bytesWritten
        val estimatedTimeRemaining = if (speed > 0) remainingBytes / speed else 0.0
        
        val params = Arguments.createMap().apply {
            putString("uploadId", uploadId)
            putInt("progress", ((bytesWritten * 100) / totalBytes).toInt())
            putDouble("bytesWritten", bytesWritten.toDouble())
            putDouble("totalBytes", totalBytes.toDouble())
            putDouble("speed", speed)
            putDouble("estimatedTimeRemaining", estimatedTimeRemaining)
        }
        sendEvent("upload-progress", params)
    }

    fun onUploadComplete(uploadId: String, response: String, responseCode: Int, responseHeaders: Map<String, String>) {
        val startTime = startTimes.remove(uploadId) ?: System.currentTimeMillis()
        val duration = (System.currentTimeMillis() - startTime) / 1000.0
        
        val params = Arguments.createMap().apply {
            putString("uploadId", uploadId)
            putString("response", response)
            putInt("responseCode", responseCode)
            putMap("responseHeaders", Arguments.makeNativeMap(responseHeaders))
            putDouble("duration", duration)
        }
        sendEvent("upload-complete", params)
    }

    fun onUploadError(uploadId: String, error: String, code: String? = null, details: Map<String, Any>? = null) {
        val params = Arguments.createMap().apply {
            putString("uploadId", uploadId)
            putString("error", error)
            code?.let { putString("code", it) }
            details?.let { putMap("details", Arguments.makeNativeMap(it)) }
        }
        sendEvent("error", params)
    }

    fun onDownloadProgress(downloadId: String, bytesWritten: Long, totalBytes: Long) {
        val startTime = startTimes[downloadId] ?: System.currentTimeMillis()
        startTimes[downloadId] = startTime
        
        val duration = (System.currentTimeMillis() - startTime) / 1000.0
        val speed = if (duration > 0) bytesWritten / duration else 0.0
        val remainingBytes = totalBytes - bytesWritten
        val estimatedTimeRemaining = if (speed > 0) remainingBytes / speed else 0.0
        
        val params = Arguments.createMap().apply {
            putString("downloadId", downloadId)
            putInt("progress", ((bytesWritten * 100) / totalBytes).toInt())
            putDouble("bytesWritten", bytesWritten.toDouble())
            putDouble("totalBytes", totalBytes.toDouble())
            putDouble("speed", speed)
            putDouble("estimatedTimeRemaining", estimatedTimeRemaining)
        }
        sendEvent("download-progress", params)
    }

    fun onDownloadComplete(downloadId: String, filePath: String, mimeType: String) {
        val startTime = startTimes.remove(downloadId) ?: System.currentTimeMillis()
        val duration = (System.currentTimeMillis() - startTime) / 1000.0
        val file = File(filePath)
        
        val params = Arguments.createMap().apply {
            putString("downloadId", downloadId)
            putString("filePath", filePath)
            putString("mimeType", mimeType)
            putDouble("totalBytes", file.length().toDouble())
            putDouble("duration", duration)
        }
        sendEvent("download-complete", params)
    }

    fun onDownloadError(downloadId: String, error: String, code: String? = null, details: Map<String, Any>? = null) {
        val params = Arguments.createMap().apply {
            putString("downloadId", downloadId)
            putString("error", error)
            code?.let { putString("code", it) }
            details?.let { putMap("details", Arguments.makeNativeMap(it)) }
        }
        sendEvent("error", params)
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
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
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
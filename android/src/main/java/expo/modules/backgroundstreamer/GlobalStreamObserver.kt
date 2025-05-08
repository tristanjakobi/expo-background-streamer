package expo.modules.backgroundstreamer

import android.content.Context
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule

class GlobalStreamObserver(private val reactContext: ReactApplicationContext) {
    private val TAG = "GlobalStreamObserver"
    private val eventEmitter = reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)

    fun onUploadProgress(uploadId: String, bytesWritten: Long, totalBytes: Long) {
        val params = Arguments.createMap().apply {
            putString("uploadId", uploadId)
            putInt("bytesWritten", bytesWritten.toInt())
            putInt("totalBytes", totalBytes.toInt())
        }
        sendEvent("upload-progress", params)
    }

    fun onUploadComplete(uploadId: String, response: String) {
        val params = Arguments.createMap().apply {
            putString("uploadId", uploadId)
            putString("response", response)
        }
        sendEvent("upload-complete", params)
    }

    fun onUploadError(uploadId: String, error: String) {
        val params = Arguments.createMap().apply {
            putString("uploadId", uploadId)
            putString("error", error)
        }
        sendEvent("error", params)
    }

    fun onDownloadProgress(downloadId: String, bytesWritten: Long, totalBytes: Long) {
        val params = Arguments.createMap().apply {
            putString("downloadId", downloadId)
            putInt("bytesWritten", bytesWritten.toInt())
            putInt("totalBytes", totalBytes.toInt())
        }
        sendEvent("download-progress", params)
    }

    fun onDownloadComplete(downloadId: String, filePath: String) {
        val params = Arguments.createMap().apply {
            putString("downloadId", downloadId)
            putString("filePath", filePath)
        }
        sendEvent("download-complete", params)
    }

    fun onDownloadError(downloadId: String, error: String) {
        val params = Arguments.createMap().apply {
            putString("downloadId", downloadId)
            putString("error", error)
        }
        sendEvent("error", params)
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
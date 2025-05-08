package expo.modules.backgroundstreamer

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import java.io.File
import java.net.URL
import java.util.UUID
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.io.InputStream
import java.io.OutputStream
import expo.modules.kotlin.exception.CodedException
import com.facebook.react.bridge.ReactApplicationContext

class UploadOptions : Record {
    @Field
    var url: String = ""
    
    @Field
    var path: String = ""
    
    @Field
    var method: String = "POST"
    
    @Field
    var headers: Map<String, String> = mapOf()
    
    @Field
    var encryptionKey: String = ""
    
    @Field
    var encryptionNonce: String = ""
}

class DownloadOptions : Record {
    @Field
    var url: String = ""
    
    @Field
    var path: String = ""
    
    @Field
    var method: String = "GET"
    
    @Field
    var headers: Map<String, String> = mapOf()
    
    @Field
    var customTransferId: String? = null
}

class ExpoBackgroundStreamerModule : Module() {
    private var currentUploadId: String? = null
    private val activeUploadTasks = mutableMapOf<String, Any>()
    private val activeDownloadTasks = mutableMapOf<String, Any>()
    private lateinit var uploadService: UploadService
    private lateinit var observer: GlobalStreamObserver
    
    companion object {
        private const val TAG = "ExpoBackgroundStreamer"
    }

    override fun definition() = ModuleDefinition {
        Name("ExpoBackgroundStreamer")

        Events("upload-progress", "download-progress", "upload-complete", "download-complete", "error", "debug")

        OnCreate {
            val reactContext = appContext.reactContext as ReactApplicationContext
            uploadService = UploadService(reactContext)
            observer = GlobalStreamObserver(reactContext)
        }

        Function("getFileInfo") { path: String, promise: Promise ->
            try {
                val file = File(path)
                val exists = file.exists()
                val size = if (exists) file.length() else 0
                val name = file.name
                val extension = if (name.contains(".")) name.substringAfterLast(".") else ""

                promise.resolve(mapOf(
                    "exists" to exists,
                    "size" to size,
                    "name" to name,
                    "extension" to extension,
                    "mimeType" to getMimeType(extension)
                ))
            } catch (e: Exception) {
                promise.reject(CodedException("ERROR", "Failed to get file info: ${e.message}", e))
            }
        }

        Function("startUpload") { url: String, filePath: String, headers: Map<String, String>, promise: Promise ->
            try {
                val uploadId = java.util.UUID.randomUUID().toString()
                uploadService.startUpload(uploadId, url, filePath, headers)
                promise.resolve(uploadId)
            } catch (e: Exception) {
                promise.reject(CodedException("ERROR", "Failed to start upload: ${e.message}", e))
            }
        }

        Function("cancelUpload") { uploadId: String, promise: Promise ->
            try {
                uploadService.cancelUpload(uploadId)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject(CodedException("ERROR", "Failed to cancel upload: ${e.message}", e))
            }
        }

        Function("startDownload") { url: String, filePath: String, headers: Map<String, String>, promise: Promise ->
            try {
                val downloadId = java.util.UUID.randomUUID().toString()
                // TODO: Implement download functionality
                promise.resolve(downloadId)
            } catch (e: Exception) {
                promise.reject(CodedException("ERROR", "Failed to start download: ${e.message}", e))
            }
        }

        Function("cancelDownload") { downloadId: String, promise: Promise ->
            try {
                // TODO: Implement download cancellation
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject(CodedException("ERROR", "Failed to cancel download: ${e.message}", e))
            }
        }
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }
}

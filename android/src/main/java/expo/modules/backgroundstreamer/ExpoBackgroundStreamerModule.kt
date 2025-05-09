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
    var encryption: Map<String, String> = mapOf()
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
    private var uploadService: UploadService? = null
    private var observer: GlobalStreamObserver? = null
    
    companion object {
        private const val TAG = "ExpoBackgroundStreamer"
    }

    override fun definition() = ModuleDefinition {
        Name("ExpoBackgroundStreamer")

        Events("upload-progress", "download-progress", "upload-complete", "download-complete", "error", "debug")

        OnCreate {
            try {
                Log.d(TAG, "Module onCreate")
                val reactContext = appContext.reactContext as? ReactApplicationContext
                if (reactContext != null) {
                    Log.d(TAG, "Initializing services")
                    uploadService = UploadService(reactContext)
                    observer = GlobalStreamObserver(reactContext)
                    Log.d(TAG, "Services initialized successfully")
                } else {
                    Log.e(TAG, "React context is null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize module: ${e.message}", e)
            }
        }

        OnDestroy {
            Log.d(TAG, "Module onDestroy")
            uploadService = null
            observer = null
        }

        AsyncFunction("getFileInfo") { path: String ->
            try {
                Log.d(TAG, "Getting file info for: $path")
                val file = File(path)
                val exists = file.exists()
                val size = if (exists) file.length() else 0
                val name = file.name
                val extension = if (name.contains(".")) name.substringAfterLast(".") else ""

                mapOf(
                    "exists" to exists,
                    "size" to size,
                    "name" to name,
                    "extension" to extension,
                    "mimeType" to getMimeType(extension)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get file info: ${e.message}", e)
                throw CodedException("ERROR", "Failed to get file info: ${e.message}", e)
            }
        }

        AsyncFunction("startUpload") { options: UploadOptions ->
            try {
                Log.d(TAG, "Starting upload: url=${options.url}, path=${options.path}")
                val service = uploadService
                if (service == null) {
                    Log.e(TAG, "Upload service not initialized")
                    throw CodedException("ERROR", "Upload service not initialized", null)
                }

                // Get encryption options
                val encryptionKey = options.encryption["key"]
                val encryptionNonce = options.encryption["nonce"]
                
                if (encryptionKey == null || encryptionNonce == null) {
                    throw CodedException("ERROR", "Encryption key and nonce are required", null)
                }

                val uploadId = UUID.randomUUID().toString()
                Log.d(TAG, "Generated upload ID: $uploadId")
                service.startUpload(uploadId, options.url, options.path, options.headers, encryptionKey, encryptionNonce)
                uploadId
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start upload: ${e.message}", e)
                throw CodedException("ERROR", "Failed to start upload: ${e.message}", e)
            }
        }

        AsyncFunction("cancelUpload") { uploadId: String ->
            try {
                Log.d(TAG, "Cancelling upload: $uploadId")
                val service = uploadService
                if (service == null) {
                    Log.e(TAG, "Upload service not initialized")
                    throw CodedException("ERROR", "Upload service not initialized", null)
                }
                service.cancelUpload(uploadId)
                Unit
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel upload: ${e.message}", e)
                throw CodedException("ERROR", "Failed to cancel upload: ${e.message}", e)
            }
        }

        AsyncFunction("startDownload") { options: DownloadOptions ->
            try {
                Log.d(TAG, "Starting download: url=${options.url}, path=${options.path}")
                val downloadId = UUID.randomUUID().toString()
                // TODO: Implement download functionality
                downloadId
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start download: ${e.message}", e)
                throw CodedException("ERROR", "Failed to start download: ${e.message}", e)
            }
        }

        AsyncFunction("cancelDownload") { downloadId: String ->
            try {
                Log.d(TAG, "Cancelling download: $downloadId")
                // TODO: Implement download cancellation
                Unit
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel download: ${e.message}", e)
                throw CodedException("ERROR", "Failed to cancel download: ${e.message}", e)
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

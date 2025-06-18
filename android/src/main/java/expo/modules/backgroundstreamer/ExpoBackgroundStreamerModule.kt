package expo.modules.backgroundstreamer

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class ExpoBackgroundStreamerModule : Module() {
    private val TAG = "ExpoBackgroundStreamer"

    private fun convertFileUriToPath(fileUri: String): String {
        return if (fileUri.startsWith("file://")) {
            fileUri.substring(7)
        } else {
            fileUri
        }
    }

    override fun definition() = ModuleDefinition {
        Name("ExpoBackgroundStreamer")

        Events("onUploadProgress", "onUploadComplete", "onUploadError", "onDownloadProgress", "onDownloadComplete", "onDownloadError")

        OnCreate {
            GlobalStreamObserver.setEventEmitter(this@ExpoBackgroundStreamerModule)
        }

        OnDestroy {
            GlobalStreamObserver.clearEventEmitter()
        }

        AsyncFunction("startUpload") { optionsMap: Map<String, Any>, promise: Promise ->
            Log.d(TAG, "startUpload called with raw options map: $optionsMap")
            
            try {
                // Extract encryption options from the map
                val encryptionMap = optionsMap["encryption"] as? Map<String, Any>
                Log.d(TAG, "Encryption map: $encryptionMap")
                
                val encryption = if (encryptionMap != null) {
                    EncryptionOptions(
                        enabled = encryptionMap["enabled"] as? Boolean ?: false,
                        key = encryptionMap["key"] as? String,
                        nonce = encryptionMap["nonce"] as? String
                    )
                } else {
                    null
                }
                
                Log.d(TAG, "Constructed encryption object: $encryption")
                Log.d(TAG, "Encryption enabled: ${encryption?.enabled}")
                Log.d(TAG, "Encryption key: ${encryption?.key}")
                Log.d(TAG, "Encryption nonce: ${encryption?.nonce}")
                
                // Create UploadOptions manually
                val options = UploadOptions(
                    url = optionsMap["url"] as? String ?: "",
                    path = convertFileUriToPath(optionsMap["path"] as? String ?: ""),
                    method = optionsMap["method"] as? String ?: "POST",
                    headers = (optionsMap["headers"] as? Map<String, String>) ?: mapOf(),
                    customTransferId = optionsMap["customTransferId"] as? String,
                    appGroup = optionsMap["appGroup"] as? String,
                    encryption = encryption,
                    compression = null
                )
                
                Log.d(TAG, "Constructed UploadOptions: $options")
                Log.d(TAG, "UploadOptions encryption: ${options.encryption}")
                Log.d(TAG, "UploadOptions encryption enabled: ${options.encryption?.enabled}")

                // Start the background service instead of a coroutine
                try {
                    val context = appContext.reactContext ?: throw CodedException("ERR_NO_CONTEXT", "React context not available", null)
                    BackgroundUploadService.startUpload(context, options)
                    Log.d(TAG, "Background upload service started successfully")
                    promise.resolve(options.uploadId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting background upload service: ${e.message}", e)
                    promise.reject("ERR_UPLOAD_START", e.message ?: "Failed to start upload", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing options: ${e.message}", e)
                promise.reject("ERR_UPLOAD_START", "Failed to parse upload options: ${e.message}", e)
            }
        }

        AsyncFunction("cancelUpload") { uploadId: String, promise: Promise ->
            try {
                val context = appContext.reactContext ?: throw CodedException("ERR_NO_CONTEXT", "React context not available", null)
                BackgroundUploadService.cancelUpload(context, uploadId)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("ERR_UPLOAD_CANCEL", e.message ?: "Failed to cancel upload", e)
            }
        }

        AsyncFunction("startDownload") { optionsMap: Map<String, Any>, promise: Promise ->
            Log.d(TAG, "startDownload called with raw options map: $optionsMap")
            
            try {
                // Extract encryption options from the map
                val encryptionMap = optionsMap["encryption"] as? Map<String, Any>
                Log.d(TAG, "Download encryption map: $encryptionMap")
                
                val encryption = if (encryptionMap != null) {
                    EncryptionOptions(
                        enabled = encryptionMap["enabled"] as? Boolean ?: false,
                        key = encryptionMap["key"] as? String,
                        nonce = encryptionMap["nonce"] as? String
                    )
                } else {
                    null
                }
                
                Log.d(TAG, "Constructed download encryption object: $encryption")
                
                // Create DownloadOptions manually
                val options = DownloadOptions(
                    url = optionsMap["url"] as? String ?: "",
                    path = convertFileUriToPath(optionsMap["path"] as? String ?: ""),
                    method = optionsMap["method"] as? String ?: "GET",
                    headers = (optionsMap["headers"] as? Map<String, String>) ?: mapOf(),
                    customTransferId = optionsMap["customTransferId"] as? String,
                    appGroup = optionsMap["appGroup"] as? String,
                    encryption = encryption,
                    compression = null
                )
                
                Log.d(TAG, "Constructed DownloadOptions: $options")
                
                // For now, keep downloads as coroutines (can be moved to service later)
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        UploadService.startDownload(options)
                        promise.resolve(options.downloadId)
                    } catch (e: Exception) {
                        promise.reject("ERR_DOWNLOAD_START", e.message ?: "Failed to start download", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing download options: ${e.message}", e)
                promise.reject("ERR_DOWNLOAD_START", "Failed to parse download options: ${e.message}", e)
            }
        }

        AsyncFunction("cancelDownload") { downloadId: String, promise: Promise ->
            try {
                UploadService.cancelDownload(downloadId)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("ERR_DOWNLOAD_CANCEL", e.message ?: "Failed to cancel download", e)
            }
        }
    }
}

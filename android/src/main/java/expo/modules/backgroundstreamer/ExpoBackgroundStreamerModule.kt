package expo.modules.backgroundstreamer

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ExpoBackgroundStreamerModule : Module() {
    private val TAG = "ExpoBackgroundStreamer"
    private val moduleCoroutineScope = CoroutineScope(appContext.mainQueue)

    override fun definition() = ModuleDefinition {
        Name("ExpoBackgroundStreamer")

        Events("onUploadProgress", "onUploadComplete", "onUploadError", "onDownloadProgress", "onDownloadComplete", "onDownloadError")

        OnCreate {
            GlobalStreamObserver.setEventEmitter(this)
        }

        OnDestroy {
            GlobalStreamObserver.clearEventEmitter()
        }

        AsyncFunction("startUpload") { options: UploadOptions, promise: Promise ->
            moduleCoroutineScope.launch {
                try {
                    UploadService.startUpload(options)
                    promise.resolve(null)
                } catch (e: Exception) {
                    promise.reject("ERR_UPLOAD_START", e.message ?: "Failed to start upload", e)
                }
            }
        }

        AsyncFunction("cancelUpload") { uploadId: String, promise: Promise ->
            try {
                UploadService.cancelUpload(uploadId)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("ERR_UPLOAD_CANCEL", e.message ?: "Failed to cancel upload", e)
            }
        }

        AsyncFunction("startDownload") { options: DownloadOptions, promise: Promise ->
            moduleCoroutineScope.launch {
                try {
                    UploadService.startDownload(options)
                    promise.resolve(null)
                } catch (e: Exception) {
                    promise.reject("ERR_DOWNLOAD_START", e.message ?: "Failed to start download", e)
                }
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

data class UploadOptions(
    val url: String,
    val fileUri: String,
    val uploadId: String,
    val headers: Map<String, String> = mapOf(),
    val encryptionKey: String? = null
)

data class DownloadOptions(
    val url: String,
    val fileUri: String,
    val downloadId: String,
    val headers: Map<String, String> = mapOf(),
    val encryptionKey: String? = null
)

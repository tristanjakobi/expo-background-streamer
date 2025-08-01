package expo.modules.backgroundstreamer

import android.content.Context
import android.util.Log
import okhttp3.*
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Headers.Companion.toHeaders
import java.io.Closeable
import com.facebook.react.bridge.ReactApplicationContext
import okio.BufferedSink
import java.util.concurrent.TimeUnit
import java.io.FileOutputStream
import expo.modules.kotlin.exception.CodedException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import java.util.UUID
import javax.crypto.spec.GCMParameterSpec

object UploadService {
    private const val TAG = "UploadService"
    private val activeUploads = mutableMapOf<String, HttpURLConnection>()
    private val activeDownloads = mutableMapOf<String, HttpURLConnection>()
    private val transferStatus = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun validateEncryption(encryption: EncryptionOptions?): Pair<String, String> {
        if (encryption?.enabled != true) {
            throw CodedException("ERR_ENCRYPTION_CONFIG", "Encryption is not enabled", null)
        }
        
        val key = encryption.key
        val nonce = encryption.nonce
        
        if (key == null) {
            throw CodedException("ERR_ENCRYPTION_CONFIG", "Encryption key is required", null)
        }
        
        if (nonce == null) {
            throw CodedException("ERR_ENCRYPTION_CONFIG", "Encryption nonce is required", null)
        }
        
        return Pair(key, nonce)
    }

    suspend fun startUpload(options: UploadOptions) {
        Log.d(TAG, "Starting upload with options: url=${options.url}, path=${options.path}, uploadId=${options.uploadId}")
        Log.d(TAG, "Raw options object: $options")
        Log.d(TAG, "Headers: ${options.headers}")
        Log.d(TAG, "Encryption object: ${options.encryption}")
        Log.d(TAG, "Encryption enabled: ${options.encryption?.enabled}")
        Log.d(TAG, "Encryption key: ${options.encryption?.key ?: "null"}")
        Log.d(TAG, "Encryption nonce: ${options.encryption?.nonce ?: "null"}")

        if (options.encryption?.enabled == true) {
            val (key, nonce) = validateEncryption(options.encryption)
            Log.d(TAG, "Encryption key length: ${key?.length ?: 0} bytes")
            Log.d(TAG, "Encryption nonce length: ${nonce?.length ?: 0} bytes")
        } else {
            Log.w(TAG, "Encryption is null or not enabled")
        }

        val file = File(options.path)
        Log.d(TAG, "File exists: ${file.exists()}, absolute path: ${file.absolutePath}")
        if (!file.exists()) {
            Log.e(TAG, "File not found: ${options.path}")
            throw CodedException("ERR_FILE_NOT_FOUND", "File not found: ${options.path}", null)
        }

        try {
            Log.d(TAG, "Creating connection to ${options.url}")
            val connection = URL(options.url).openConnection() as HttpURLConnection
            connection.requestMethod = options.method
            connection.doOutput = true
            connection.setChunkedStreamingMode(8192)
            Log.d(TAG, "Connection created with chunked streaming mode")

            options.headers.forEach { (key, value) ->
                Log.d(TAG, "Setting header: $key=$value")
                connection.setRequestProperty(key, value)
            }

                    activeUploads[options.uploadId] = connection
        transferStatus[options.uploadId] = "uploading"
        Log.d(TAG, "Connection stored in activeUploads")

        try {
            Log.d(TAG, "Connecting to server...")
            connection.connect()
            Log.d(TAG, "Connected successfully")

                val outputStream = if (options.encryption?.enabled == true) {
                    val (key, nonce) = validateEncryption(options.encryption)
                    Log.d(TAG, "Setting up encrypted output stream")
                    try {
                        val keyBytes = Base64.getDecoder().decode(key)
                        val nonceBytes = Base64.getDecoder().decode(nonce)
                        Log.d(TAG, "Using EncryptedOutputStream with key/nonce")
                        EncryptedOutputStream(connection.outputStream, keyBytes, nonceBytes)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting up encryption: ${e.message}", e)
                        throw e
                    }
                } else {
                    Log.d(TAG, "Using plain output stream")
                    connection.outputStream
                }

                Log.d(TAG, "Starting file read and upload")
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        try {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            Log.v(TAG, "Uploaded $totalBytesRead/${file.length()} bytes")
                            
                            // Throttle progress events to prevent UI thread blocking (update every 1% or 1MB)
                            val progress = ((totalBytesRead * 100) / file.length()).toInt()
                            val lastProgress = (((totalBytesRead - bytesRead) * 100) / file.length()).toInt()
                            if (progress != lastProgress || totalBytesRead % (1024 * 1024) == 0L) {
                                GlobalStreamObserver.onUploadProgress(options.uploadId, totalBytesRead, file.length())
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during upload: ${e.message}", e)
                            throw e
                        }
                    }
                }
                Log.d(TAG, "File upload completed, closing output stream")

                try {
                    outputStream.close()
                    Log.d(TAG, "Output stream closed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing output stream: ${e.message}", e)
                    throw e
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Response code: $responseCode")
                
                val responseBody = try {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading response body: ${e.message}", e)
                    "Error reading response: ${e.message}"
                }
                Log.d(TAG, "Response body: $responseBody")

                if (responseCode in 200..299) {
                    Log.d(TAG, "Upload completed successfully")
                    transferStatus[options.uploadId] = "completed"
                    GlobalStreamObserver.onUploadComplete(options.uploadId, responseCode, responseBody, file.length())
                } else {
                    Log.e(TAG, "Upload failed with response code: $responseCode")
                    transferStatus[options.uploadId] = "error"
                    throw CodedException("ERR_UPLOAD_FAILED", "Upload failed with response code: $responseCode", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during upload process: ${e.message}", e)
                transferStatus[options.uploadId] = "error"
                GlobalStreamObserver.onUploadError(options.uploadId, e.message ?: "Unknown error")
                throw CodedException("ERR_UPLOAD_START", e.message ?: "Unknown error", e)
            } finally {
                Log.d(TAG, "Cleaning up connection")
                activeUploads.remove(options.uploadId)?.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in startUpload: ${e.message}", e)
            throw CodedException("ERR_UPLOAD_START", e.message ?: "Unknown error", e)
        }
    }

    fun cancelUpload(uploadId: String) {
        try {
            activeUploads[uploadId]?.disconnect()
            activeUploads.remove(uploadId)
        } catch (e: Exception) {
            throw CodedException("ERR_UPLOAD_CANCEL", e.message ?: "Unknown error", e)
        }
    }

    suspend fun startDownload(options: DownloadOptions) {
        val connection = URL(options.url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.doInput = true

        options.headers.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        activeDownloads[options.downloadId] = connection
        transferStatus[options.downloadId] = "downloading"

        try {
            connection.connect()

            val contentLength = connection.contentLength.toLong()
            val file = File(options.path)
            file.parentFile?.mkdirs()

            val inputStream = if (options.encryption?.enabled == true) {
                val (key, nonce) = validateEncryption(options.encryption)
                val keyBytes = Base64.getDecoder().decode(key)
                val nonceBytes = Base64.getDecoder().decode(nonce)
                Log.d(TAG, "Using EncryptedInputStream with key/nonce")
                EncryptedInputStream(connection.inputStream, keyBytes, nonceBytes)
            } else {
                connection.inputStream
            }

            FileOutputStream(file).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    // Throttle progress events to prevent UI thread blocking (update every 1% or 1MB)
                    val progress = if (contentLength > 0) ((totalBytesRead * 100) / contentLength).toInt() else 0
                    val lastProgress = if (contentLength > 0) (((totalBytesRead - bytesRead) * 100) / contentLength).toInt() else 0
                    if (progress != lastProgress || totalBytesRead % (1024 * 1024) == 0L) {
                        GlobalStreamObserver.onDownloadProgress(options.downloadId, totalBytesRead, contentLength)
                    }
                }
            }

            inputStream.close()

            transferStatus[options.downloadId] = "completed"
            GlobalStreamObserver.onDownloadComplete(options.downloadId, file.absolutePath)
        } catch (e: Exception) {
            transferStatus[options.downloadId] = "error"
            GlobalStreamObserver.onDownloadError(options.downloadId, e.message ?: "Unknown error")
            throw CodedException("ERR_DOWNLOAD_START", e.message ?: "Unknown error", e)
        } finally {
            activeDownloads.remove(options.downloadId)?.disconnect()
        }
    }

    fun cancelDownload(downloadId: String) {
        try {
            activeDownloads[downloadId]?.disconnect()
            activeDownloads.remove(downloadId)
            transferStatus[downloadId] = "cancelled"
        } catch (e: Exception) {
            throw CodedException("ERR_DOWNLOAD_CANCEL", e.message ?: "Unknown error", e)
        }
    }

    fun getActiveDownloads(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        // Add currently downloading transfers
        activeDownloads.keys.forEach { downloadId ->
            result[downloadId] = transferStatus[downloadId] ?: "downloading"
        }
        // Add failed transfers that are still being tracked
        transferStatus.filter { it.value == "error" }.forEach { (downloadId, status) ->
            result[downloadId] = status
        }
        return result
    }
    
    fun getDownloadStatus(downloadId: String): String? {
        return if (activeDownloads.containsKey(downloadId)) {
            transferStatus[downloadId] ?: "downloading"
        } else {
            transferStatus[downloadId]
        }
    }
} 
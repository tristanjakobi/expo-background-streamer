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
import expo.modules.kotlin.modules.Module
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class UploadService(
    private val reactContext: ReactApplicationContext,
    private val module: Module
) {
    private val TAG = "UploadService"
    private val executor = Executors.newCachedThreadPool()
    private val activeUploads = mutableMapOf<String, UploadTask>()
    private val activeDownloads = mutableMapOf<String, DownloadTask>()
    private val uploadProgress = mutableMapOf<String, Pair<Long, Long>>() // uploadId -> (bytesWritten, totalBytes)
    private val downloadProgress = mutableMapOf<String, Pair<Long, Long>>() // downloadId -> (bytesWritten, totalBytes)
    private val uploadIdCounter = AtomicInteger(0)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun startUpload(
        uploadId: String,
        url: String,
        filePath: String,
        headers: Map<String, String>,
        encryptionKey: String,
        encryptionNonce: String
    ) {
        val keyBytes = encryptionKey.toByteArray()
        val nonceBytes = encryptionNonce.toByteArray()
        val task = UploadTask(
            uploadId,
            url,
            filePath,
            headers,
            keyBytes,
            nonceBytes,
            module
        )
        activeUploads[uploadId] = task
        executor.execute(task)
    }

    fun cancelUpload(uploadId: String) {
        activeUploads[uploadId]?.cancel()
        activeUploads.remove(uploadId)
    }

    fun startDownload(
        downloadId: String,
        url: String,
        filePath: String,
        headers: Map<String, String>
    ) {
        val task = DownloadTask(
            downloadId,
            url,
            filePath,
            headers,
            module
        )
        activeDownloads[downloadId] = task
        executor.execute(task)
    }

    fun cancelDownload(downloadId: String) {
        activeDownloads[downloadId]?.cancel()
        activeDownloads.remove(downloadId)
    }

    private inner class UploadTask(
        private val uploadId: String,
        private val url: String,
        private val filePath: String,
        private val headers: Map<String, String>,
        private val encryptionKey: ByteArray,
        private val encryptionNonce: ByteArray,
        private val module: Module
    ) : Runnable {
        private val cancelled = AtomicBoolean(false)
        private var connection: HttpURLConnection? = null

        override fun run() {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    module.sendEvent("error", mapOf(
                        "uploadId" to uploadId,
                        "error" to "File does not exist: $filePath"
                    ))
                    return
                }

                val fileSize = file.length()
                var bytesWritten = 0L
                var lastProgressUpdate = 0L
                val progressInterval = 100L // Update progress every 100ms

                val url = URL(url)
                connection = url.openConnection() as HttpURLConnection
                connection?.apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 30000

                    // Add headers
                    headers.forEach { (key, value) ->
                        setRequestProperty(key, value)
                    }

                    // Set content length
                    setFixedLengthStreamingMode(fileSize)
                }

                val inputStream: InputStream = if (encryptionKey.isNotEmpty() && encryptionNonce.isNotEmpty()) {
                    EncryptedInputStream(FileInputStream(file), encryptionKey, encryptionNonce)
                } else {
                    FileInputStream(file)
                }

                connection?.outputStream?.use { output ->
                    inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1 && !cancelled.get()) {
                            output.write(buffer, 0, bytes)
                            bytesWritten += bytes

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastProgressUpdate >= progressInterval) {
                                module.sendEvent("upload-progress", mapOf(
                                    "uploadId" to uploadId,
                                    "progress" to ((bytesWritten * 100) / fileSize).toInt(),
                                    "bytesWritten" to bytesWritten,
                                    "totalBytes" to fileSize
                                ))
                                lastProgressUpdate = currentTime
                            }
                        }
                    }
                }

                if (cancelled.get()) {
                    module.sendEvent("upload-cancelled", mapOf(
                        "uploadId" to uploadId,
                        "bytesWritten" to bytesWritten,
                        "totalBytes" to fileSize
                    ))
                    return
                }

                val responseCode = connection?.responseCode ?: -1
                val response = connection?.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                val responseHeaders = connection?.headerFields?.mapValues { it.value.firstOrNull() ?: "" } ?: mapOf()

                if (responseCode in 200..299) {
                    module.sendEvent("upload-complete", mapOf(
                        "uploadId" to uploadId,
                        "response" to response,
                        "responseCode" to responseCode,
                        "responseHeaders" to responseHeaders
                    ))
                } else {
                    module.sendEvent("error", mapOf(
                        "uploadId" to uploadId,
                        "error" to "Upload failed with status code: $responseCode",
                        "code" to "HTTP_ERROR",
                        "details" to mapOf(
                            "responseCode" to responseCode,
                            "response" to response,
                            "responseHeaders" to responseHeaders
                        )
                    ))
                }

            } catch (e: Exception) {
                if (!cancelled.get()) {
                    module.sendEvent("error", mapOf(
                        "uploadId" to uploadId,
                        "error" to (e.message ?: "Unknown error"),
                        "code" to "UPLOAD_ERROR"
                    ))
                }
            } finally {
                connection?.disconnect()
                activeUploads.remove(uploadId)
            }
        }

        fun cancel() {
            cancelled.set(true)
            connection?.disconnect()
        }
    }

    private inner class DownloadTask(
        private val downloadId: String,
        private val url: String,
        private val filePath: String,
        private val headers: Map<String, String>,
        private val module: Module
    ) : Runnable {
        private val cancelled = AtomicBoolean(false)
        private var connection: HttpURLConnection? = null

        override fun run() {
            try {
                val url = URL(url)
                connection = url.openConnection() as HttpURLConnection
                connection?.apply {
                    requestMethod = "GET"
                    connectTimeout = 30000
                    readTimeout = 30000

                    // Add headers
                    headers.forEach { (key, value) ->
                        setRequestProperty(key, value)
                    }
                }

                val responseCode = connection?.responseCode ?: -1
                if (responseCode !in 200..299) {
                    module.sendEvent("error", mapOf(
                        "downloadId" to downloadId,
                        "error" to "Download failed with status code: $responseCode",
                        "code" to "HTTP_ERROR"
                    ))
                    return
                }

                val contentLength = connection?.contentLength?.toLong() ?: -1L
                var bytesWritten = 0L
                var lastProgressUpdate = 0L
                val progressInterval = 100L // Update progress every 100ms

                val file = File(filePath)
                file.parentFile?.mkdirs()

                connection?.inputStream?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1 && !cancelled.get()) {
                            output.write(buffer, 0, bytes)
                            bytesWritten += bytes

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastProgressUpdate >= progressInterval && contentLength > 0) {
                                module.sendEvent("download-progress", mapOf(
                                    "downloadId" to downloadId,
                                    "progress" to ((bytesWritten * 100) / contentLength).toInt(),
                                    "bytesWritten" to bytesWritten,
                                    "totalBytes" to contentLength
                                ))
                                lastProgressUpdate = currentTime
                            }
                        }
                    }
                }

                if (cancelled.get()) {
                    module.sendEvent("download-cancelled", mapOf(
                        "downloadId" to downloadId,
                        "bytesWritten" to bytesWritten,
                        "totalBytes" to contentLength
                    ))
                    file.delete()
                    return
                }

                val mimeType = connection?.contentType ?: "application/octet-stream"
                module.sendEvent("download-complete", mapOf(
                    "downloadId" to downloadId,
                    "filePath" to filePath,
                    "mimeType" to mimeType,
                    "totalBytes" to bytesWritten
                ))

            } catch (e: Exception) {
                if (!cancelled.get()) {
                    module.sendEvent("error", mapOf(
                        "downloadId" to downloadId,
                        "error" to (e.message ?: "Unknown error"),
                        "code" to "DOWNLOAD_ERROR"
                    ))
                }
                File(filePath).delete()
            } finally {
                connection?.disconnect()
                activeDownloads.remove(downloadId)
            }
        }

        fun cancel() {
            cancelled.set(true)
            connection?.disconnect()
        }
    }
} 
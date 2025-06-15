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

    suspend fun startUpload(options: UploadOptions) {
        try {
            val file = File(options.fileUri)
            if (!file.exists()) {
                throw CodedException("ERR_FILE_NOT_FOUND", "File not found: ${options.fileUri}")
            }

            val connection = URL(options.url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setChunkedStreamingMode(8192)

            options.headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            activeUploads[options.uploadId] = connection

            connection.connect()

            val outputStream = if (options.encryptionKey != null) {
                val key = SecretKeySpec(options.encryptionKey.toByteArray(), "AES")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val nonce = ByteArray(12).apply { UUID.randomUUID().toString().toByteArray().copyInto(this) }
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
                EncryptedOutputStream(connection.outputStream, cipher, nonce)
            } else {
                connection.outputStream
            }

            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    GlobalStreamObserver.onUploadProgress(options.uploadId, totalBytesRead, file.length())
                }
            }

            outputStream.close()

            val responseCode = connection.responseCode
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }

            if (responseCode in 200..299) {
                GlobalStreamObserver.onUploadComplete(options.uploadId, responseCode, responseBody)
            } else {
                throw CodedException("ERR_UPLOAD_FAILED", "Upload failed with response code: $responseCode")
            }
        } catch (e: Exception) {
            GlobalStreamObserver.onUploadError(options.uploadId, e.message ?: "Unknown error")
            throw CodedException("ERR_UPLOAD_START", e)
        } finally {
            activeUploads.remove(options.uploadId)?.disconnect()
        }
    }

    fun cancelUpload(uploadId: String) {
        try {
            activeUploads[uploadId]?.disconnect()
            activeUploads.remove(uploadId)
        } catch (e: Exception) {
            throw CodedException("ERR_UPLOAD_CANCEL", e)
        }
    }

    suspend fun startDownload(options: DownloadOptions) {
        try {
            val connection = URL(options.url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.doInput = true

            options.headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            activeDownloads[options.downloadId] = connection

            connection.connect()

            val contentLength = connection.contentLength.toLong()
            val file = File(options.fileUri)
            file.parentFile?.mkdirs()

            val inputStream = if (options.encryptionKey != null) {
                val key = SecretKeySpec(options.encryptionKey.toByteArray(), "AES")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val nonce = ByteArray(12).apply { UUID.randomUUID().toString().toByteArray().copyInto(this) }
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
                EncryptedInputStream(connection.inputStream, cipher, nonce)
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
                    GlobalStreamObserver.onDownloadProgress(options.downloadId, totalBytesRead, contentLength)
                }
            }

            inputStream.close()

            GlobalStreamObserver.onDownloadComplete(options.downloadId, file.absolutePath)
        } catch (e: Exception) {
            GlobalStreamObserver.onDownloadError(options.downloadId, e.message ?: "Unknown error")
            throw CodedException("ERR_DOWNLOAD_START", e)
        } finally {
            activeDownloads.remove(options.downloadId)?.disconnect()
        }
    }

    fun cancelDownload(downloadId: String) {
        try {
            activeDownloads[downloadId]?.disconnect()
            activeDownloads.remove(downloadId)
        } catch (e: Exception) {
            throw CodedException("ERR_DOWNLOAD_CANCEL", e)
        }
    }
} 
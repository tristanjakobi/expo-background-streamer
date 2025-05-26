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

class UploadService(private val reactContext: ReactApplicationContext) {
    private val executor = Executors.newCachedThreadPool()
    private val activeUploads = mutableMapOf<String, Call>()
    private val activeDownloads = mutableMapOf<String, Call>()
    private val uploadIdCounter = AtomicInteger(0)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val observer = GlobalStreamObserver(reactContext)

    fun startUpload(
        uploadId: String,
        url: String,
        filePath: String,
        headers: Map<String, String> = emptyMap(),
        encryptionKey: String,
        encryptionNonce: String
    ) {
        Log.d("UploadService", "Starting upload: $uploadId, url: $url, path: $filePath")
        val file = File(filePath)
        if (!file.exists()) {
            Log.e("UploadService", "File not found: $filePath")
            observer.onUploadError(uploadId, "File not found: $filePath")
            return
        }

        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType? = "application/octet-stream".toMediaType()

            override fun contentLength(): Long = file.length()

            override fun writeTo(sink: BufferedSink) {
                try {
                    Log.d("UploadService", "Starting file upload, size: ${file.length()}")
                    file.inputStream().use { input ->
                        val stream = if (encryptionKey.isNotEmpty() && encryptionNonce.isNotEmpty()) {
                            // Create encrypted input stream
                            val keyBytes = Base64.getDecoder().decode(encryptionKey)
                            val nonceBytes = Base64.getDecoder().decode(encryptionNonce)
                            EncryptedInputStream(input, keyBytes, nonceBytes)
                        } else {
                            input
                        }
                        
                        val buffer = ByteArray(16384)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        while (stream.read(buffer).also { bytesRead = it } != -1) {
                            sink.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            val progress = (totalBytesRead * 100 / file.length()).toInt()
                            Log.d("UploadService", "Upload progress: $progress%")
                            observer.onUploadProgress(uploadId, totalBytesRead, file.length())
                        }
                    }
                    Log.d("UploadService", "File upload completed")
                } catch (e: Exception) {
                    Log.e("UploadService", "Error during upload: ${e.message}", e)
                    throw e
                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .apply {
                headers.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()

        val call = client.newCall(request)
        activeUploads[uploadId] = call

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("UploadService", "Upload failed: ${e.message}", e)
                activeUploads.remove(uploadId)
                observer.onUploadError(uploadId, e.message ?: "Upload failed")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("UploadService", "Upload response received: ${response.code}")
                activeUploads.remove(uploadId)
                response.use {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        Log.d("UploadService", "Upload completed successfully")
                        observer.onUploadComplete(uploadId, responseBody, response.code, response.headers.toMap())
                    } else {
                        Log.e("UploadService", "Upload failed with status: ${response.code}")
                        observer.onUploadError(uploadId, "Upload failed with status: ${response.code}")
                    }
                }
            }
        })
    }

    fun cancelUpload(uploadId: String) {
        Log.d("UploadService", "Cancelling upload: $uploadId")
        activeUploads[uploadId]?.let { call ->
            if (!call.isCanceled()) {
                call.cancel()
            }
            activeUploads.remove(uploadId)
        }
    }

    fun startDownload(
        downloadId: String,
        url: String,
        filePath: String,
        headers: Map<String, String> = emptyMap()
    ) {
        Log.d("UploadService", "Starting download: $downloadId, url: $url, path: $filePath")
        
        val request = Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()

        val call = client.newCall(request)
        activeDownloads[downloadId] = call

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("UploadService", "Download failed: ${e.message}", e)
                activeDownloads.remove(downloadId)
                observer.onDownloadError(downloadId, e.message ?: "Download failed")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("UploadService", "Download response received: ${response.code}")
                activeDownloads.remove(downloadId)
                
                response.use {
                    if (!response.isSuccessful) {
                        Log.e("UploadService", "Download failed with status: ${response.code}")
                        observer.onDownloadError(downloadId, "Download failed with status: ${response.code}")
                        return
                    }

                    val body = response.body ?: run {
                        observer.onDownloadError(downloadId, "Empty response body")
                        return
                    }

                    val contentLength = body.contentLength()
                    var bytesRead = 0L

                    try {
                        val file = File(filePath)
                        file.parentFile?.mkdirs()
                        
                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(8192)
                            val input = body.byteStream()
                            
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                
                                output.write(buffer, 0, read)
                                bytesRead += read
                                
                                val progress = (bytesRead * 100 / contentLength).toInt()
                                observer.onDownloadProgress(downloadId, bytesRead, contentLength)
                            }
                        }
                        
                        Log.d("UploadService", "Download completed successfully")
                        val mimeType = response.body?.contentType()?.toString() ?: "application/octet-stream"
                        observer.onDownloadComplete(downloadId, filePath, mimeType)
                    } catch (e: Exception) {
                        Log.e("UploadService", "Error saving downloaded file: ${e.message}", e)
                        observer.onDownloadError(downloadId, "Error saving file: ${e.message}")
                    }
                }
            }
        })
    }

    fun cancelDownload(downloadId: String) {
        Log.d("UploadService", "Cancelling download: $downloadId")
        activeDownloads[downloadId]?.let { call ->
            if (!call.isCanceled()) {
                call.cancel()
                activeDownloads.remove(downloadId)
                observer.onDownloadError(downloadId, "Download cancelled")
            }
        }
    }
} 
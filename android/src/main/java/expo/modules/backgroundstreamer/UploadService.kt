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

class UploadService(private val reactContext: ReactApplicationContext) {
    private val executor = Executors.newCachedThreadPool()
    private val activeUploads = mutableMapOf<String, Call>()
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
                        observer.onUploadComplete(uploadId, responseBody)
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
} 
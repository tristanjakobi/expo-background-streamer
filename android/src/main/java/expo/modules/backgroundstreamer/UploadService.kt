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
    private val executor = Executors.newFixedThreadPool(3)
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
        headers: Map<String, String> = emptyMap()
    ) {
        val file = File(filePath)
        if (!file.exists()) {
            observer.onUploadError(uploadId, "File not found: $filePath")
            return
        }

        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType? = "application/octet-stream".toMediaType()

            override fun contentLength(): Long = file.length()

            override fun writeTo(sink: BufferedSink) {
                file.inputStream().use { input ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        sink.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        observer.onUploadProgress(uploadId, totalBytesRead, file.length())
                    }
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
                activeUploads.remove(uploadId)
                observer.onUploadError(uploadId, e.message ?: "Upload failed")
            }

            override fun onResponse(call: Call, response: Response) {
                activeUploads.remove(uploadId)
                response.use {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        observer.onUploadComplete(uploadId, responseBody)
                    } else {
                        observer.onUploadError(uploadId, "Upload failed with status: ${response.code}")
                    }
                }
            }
        })
    }

    fun cancelUpload(uploadId: String) {
        activeUploads[uploadId]?.let { call ->
            if (!call.isCanceled()) {
                call.cancel()
            }
            activeUploads.remove(uploadId)
        }
    }
} 
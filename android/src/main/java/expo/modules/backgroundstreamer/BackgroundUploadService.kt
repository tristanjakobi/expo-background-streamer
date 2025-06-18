package expo.modules.backgroundstreamer

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import expo.modules.kotlin.exception.CodedException
import java.util.concurrent.ConcurrentHashMap

class BackgroundUploadService : Service() {
    companion object {
        private const val TAG = "BackgroundUploadService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "background_upload_channel"
        private const val ACTION_START_UPLOAD = "start_upload"
        private const val ACTION_CANCEL_UPLOAD = "cancel_upload"
        private const val EXTRA_UPLOAD_OPTIONS = "upload_options"
        private const val EXTRA_UPLOAD_ID = "upload_id"
        
        private val activeUploads = ConcurrentHashMap<String, Job>()
        private val activeConnections = ConcurrentHashMap<String, HttpURLConnection>()
        
        fun startUpload(context: Context, options: UploadOptions) {
            val intent = Intent(context, BackgroundUploadService::class.java).apply {
                action = ACTION_START_UPLOAD
                putExtra(EXTRA_UPLOAD_OPTIONS, options)
            }
            
            // Start as regular service first to avoid foreground service restrictions
            context.startService(intent)
        }
        
        fun cancelUpload(context: Context, uploadId: String) {
            val intent = Intent(context, BackgroundUploadService::class.java).apply {
                action = ACTION_CANCEL_UPLOAD
                putExtra(EXTRA_UPLOAD_ID, uploadId)
            }
            context.startService(intent)
        }
        
        fun getActiveUploads(): Map<String, String> {
            return activeUploads.keys.associateWith { "uploading" }
        }
        
        fun getUploadStatus(uploadId: String): String? {
            return if (activeUploads.containsKey(uploadId)) {
                "uploading"
            } else {
                null
            }
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BackgroundUploadService created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_UPLOAD -> {
                val options = intent.extras?.get(EXTRA_UPLOAD_OPTIONS) as? UploadOptions
                if (options != null) {
                    handleStartUpload(options)
                }
            }
            ACTION_CANCEL_UPLOAD -> {
                val uploadId = intent.getStringExtra(EXTRA_UPLOAD_ID)
                if (uploadId != null) {
                    handleCancelUpload(uploadId)
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BackgroundUploadService destroyed")
        serviceScope.cancel()
        
        // Cancel all active uploads
        activeUploads.values.forEach { it.cancel() }
        activeConnections.values.forEach { it.disconnect() }
        activeUploads.clear()
        activeConnections.clear()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Uploads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows upload progress for background file uploads"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(title: String, content: String, progress: Int = -1): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setAutoCancel(false)
        
        if (progress >= 0) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        
        return builder.build()
    }
    
    private fun handleStartUpload(options: UploadOptions) {
        Log.d(TAG, "Starting upload: ${options.uploadId}")
        
        // Try to start foreground service, but handle the case where it's not allowed
        try {
            val notification = createNotification("Upload Starting", "Preparing to upload file...")
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Successfully started as foreground service")
        } catch (e: Exception) {
            Log.w(TAG, "Could not start as foreground service: ${e.message}")
            // Continue as regular service - upload will still work but may be killed by system
        }
        
        val uploadJob = serviceScope.launch {
            Log.d(TAG, "Upload coroutine started for ${options.uploadId}")
            try {
                Log.d(TAG, "About to call performUpload...")
                performUpload(options)
                Log.d(TAG, "performUpload completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed: ${e.javaClass.simpleName}: ${e.message}", e)
                GlobalStreamObserver.onUploadError(options.uploadId, e.message ?: "Unknown error")
            } finally {
                activeUploads.remove(options.uploadId)
                activeConnections.remove(options.uploadId)
                
                // If no more active uploads, stop the service
                if (activeUploads.isEmpty()) {
                    try {
                        stopForeground(true)
                    } catch (e: Exception) {
                        // Service might not be foreground
                        Log.d(TAG, "Could not stop foreground: ${e.message}")
                    }
                    stopSelf()
                }
            }
        }
        
        Log.d(TAG, "Adding upload ${options.uploadId} to activeUploads")
        activeUploads[options.uploadId] = uploadJob
        Log.d(TAG, "activeUploads now contains: ${activeUploads.keys}")
    }
    
    private fun handleCancelUpload(uploadId: String) {
        Log.d(TAG, "Cancelling upload: $uploadId")
        
        activeUploads[uploadId]?.cancel()
        activeConnections[uploadId]?.disconnect()
        activeUploads.remove(uploadId)
        activeConnections.remove(uploadId)
        
        GlobalStreamObserver.onUploadError(uploadId, "Upload cancelled by user")
        
        if (activeUploads.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }
    
    private suspend fun performUpload(options: UploadOptions) {
        Log.d(TAG, "performUpload started for ${options.uploadId}")
        Log.d(TAG, "Upload URL: ${options.url}")
        Log.d(TAG, "File path: ${options.path}")
        
        val file = File(options.path)
        Log.d(TAG, "File exists: ${file.exists()}, size: ${file.length()}")
        
        if (!file.exists()) {
            Log.e(TAG, "File not found: ${options.path}")
            throw CodedException("ERR_FILE_NOT_FOUND", "File not found: ${options.path}", null)
        }
        
        Log.d(TAG, "Creating HTTP connection to ${options.url}")
        val connection = URL(options.url).openConnection() as HttpURLConnection
        activeConnections[options.uploadId] = connection
        
        try {
            Log.d(TAG, "Configuring connection...")
            connection.requestMethod = options.method
            connection.doOutput = true
            connection.setChunkedStreamingMode(8192)
            
            Log.d(TAG, "Setting headers...")
            options.headers.forEach { (key, value) ->
                Log.d(TAG, "Header: $key = $value")
                connection.setRequestProperty(key, value)
            }
            
            // Update notification
            val notificationManager = getSystemService(NotificationManager::class.java)
            val notification = createNotification("Uploading", "Connecting to server...")
            notificationManager.notify(NOTIFICATION_ID, notification)
            
            Log.d(TAG, "Attempting to connect...")
            connection.connect()
            Log.d(TAG, "Connected successfully!")
            
            val outputStream = if (options.encryption?.enabled == true) {
                setupEncryptedOutputStream(connection, options.encryption)
            } else {
                connection.outputStream
            }
            
            val fileSize = file.length()
            var totalBytesRead = 0L
            val buffer = ByteArray(8192)
            
            FileInputStream(file).use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    // Check if upload was cancelled
                    Log.d(TAG, "Checking activeUploads for ${options.uploadId}")
                    Log.d(TAG, "activeUploads contains: ${activeUploads.keys}")
                    Log.d(TAG, "Contains key? ${activeUploads.containsKey(options.uploadId)}")
                    
                    if (!activeUploads.containsKey(options.uploadId)) {
                        Log.e(TAG, "Upload ${options.uploadId} not found in activeUploads, cancelling")
                        throw CodedException("ERR_UPLOAD_CANCELLED", "Upload was cancelled", null)
                    }
                    
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    val progress = ((totalBytesRead * 100) / fileSize).toInt()
                    
                    // Update notification with progress
                    val progressNotification = createNotification(
                        "Uploading", 
                        "Progress: $progress%",
                        progress
                    )
                    notificationManager.notify(NOTIFICATION_ID, progressNotification)
                    
                    // Notify observers
                    GlobalStreamObserver.onUploadProgress(options.uploadId, totalBytesRead, fileSize)
                }
            }
            
            outputStream.close()
            
            val responseCode = connection.responseCode
            val responseBody = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                "Error reading response: ${e.message}"
            }
            
            if (responseCode in 200..299) {
                GlobalStreamObserver.onUploadComplete(options.uploadId, responseCode, responseBody)
                
                // Show completion notification
                val completionNotification = createNotification("Upload Complete", "File uploaded successfully")
                notificationManager.notify(NOTIFICATION_ID, completionNotification)
            } else {
                throw CodedException("ERR_UPLOAD_FAILED", "Upload failed with response code: $responseCode", null)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}", e)
            throw e
        } finally {
            activeConnections.remove(options.uploadId)?.disconnect()
        }
    }
    
    private fun setupEncryptedOutputStream(connection: HttpURLConnection, encryption: EncryptionOptions): OutputStream {
        val key = encryption.key ?: throw CodedException("ERR_ENCRYPTION_CONFIG", "Encryption key is required", null)
        val nonce = encryption.nonce ?: throw CodedException("ERR_ENCRYPTION_CONFIG", "Encryption nonce is required", null)
        
        val keyBytes = key.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val nonceBytes = nonce.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, nonceBytes))
        
        return EncryptedOutputStream(connection.outputStream, cipher, nonceBytes)
    }
} 
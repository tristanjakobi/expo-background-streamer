package expo.modules.backgroundstreamer

import android.util.Log
import java.io.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

class EncryptedInputStream(
    private val sourceStream: InputStream,
    private val key: ByteArray,
    private val nonce: ByteArray
) : InputStream() {
    private val decryptedData: ByteArray
    private var position = 0

    init {
        Log.d(TAG, "Reading and decrypting entire stream with key length: ${key.size}, nonce length: ${nonce.size}")
        
        // Read all encrypted data first
        val encryptedData = sourceStream.readBytes()
        Log.d(TAG, "Read ${encryptedData.size} bytes of encrypted data")
        
        // Decrypt all at once (GCM requires this)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        
        decryptedData = cipher.doFinal(encryptedData)
        Log.d(TAG, "Successfully decrypted ${encryptedData.size} bytes to ${decryptedData.size} bytes")
        
        sourceStream.close()
    }

    override fun read(): Int {
        return if (position < decryptedData.size) {
            decryptedData[position++].toInt() and 0xFF
        } else {
            -1
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (position >= decryptedData.size) {
            return -1
        }
        
        val available = decryptedData.size - position
        val toRead = minOf(len, available)
        
        System.arraycopy(decryptedData, position, b, off, toRead)
        position += toRead
        
        return toRead
    }

    override fun close() {
        // Nothing to do - source stream already closed in init
        Log.d(TAG, "EncryptedInputStream closed")
    }

    override fun available(): Int {
        return decryptedData.size - position
    }

    companion object {
        private const val TAG = "EncryptedInputStream"
    }
}

class EncryptedOutputStream(
    private val outputStream: OutputStream,
    private val cipher: Cipher,
    private val nonce: ByteArray
) : OutputStream() {
    init {
        Log.d(TAG, "Initializing EncryptedOutputStream:")
        Log.d(TAG, "  ➤ nonce length: ${nonce.size}")
        Log.d(TAG, "  ➤ nonce (base64): ${Base64.getEncoder().encodeToString(nonce)}")
    }

    override fun write(b: Int) {
        val buffer = ByteArray(1)
        buffer[0] = b.toByte()
        write(buffer, 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        val encrypted = cipher.update(b, off, len)
        outputStream.write(encrypted)
    }

    override fun close() {
        try {
            val finalBytes = cipher.doFinal()
            if (finalBytes.isNotEmpty()) {
                outputStream.write(finalBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in final encryption: ${e.message}")
        }
        outputStream.close()
    }

    companion object {
        private const val TAG = "EncryptedOutputStream"
    }
} 
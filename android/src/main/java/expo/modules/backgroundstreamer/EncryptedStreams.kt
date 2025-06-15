package expo.modules.backgroundstreamer

import android.util.Log
import java.io.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

class EncryptedInputStream(
    private val sourceStream: InputStream,
    private val key: ByteArray,
    private val nonce: ByteArray
) : InputStream() {
    private val cipher: Cipher
    private val buffer = ByteArray(4096)
    private var bufferPos = 0
    private var bufferLen = 0

    init {
        cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        Log.d(TAG, "Successfully initialized EncryptedInputStream with key length: ${key.size}, nonce length: ${nonce.size}")
        Log.d(TAG, "Key (base64): ${Base64.getEncoder().encodeToString(key)}")
        Log.d(TAG, "Nonce (base64): ${Base64.getEncoder().encodeToString(nonce)}")
    }

    override fun read(): Int {
        if (bufferPos >= bufferLen) {
            val bytesRead = sourceStream.read(buffer)
            if (bytesRead <= 0) {
                Log.d(TAG, "No more data to read or error occurred")
                return bytesRead
            }

            val decrypted = cipher.update(buffer, 0, bytesRead)
            System.arraycopy(decrypted, 0, buffer, 0, decrypted.size)
            bufferLen = decrypted.size
            bufferPos = 0
            Log.d(TAG, "Decrypted $bytesRead bytes to ${decrypted.size} bytes")
        }

        return buffer[bufferPos++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (bufferPos >= bufferLen) {
            val bytesRead = sourceStream.read(buffer)
            if (bytesRead <= 0) {
                return bytesRead
            }

            val decrypted = cipher.update(buffer, 0, bytesRead)
            System.arraycopy(decrypted, 0, buffer, 0, decrypted.size)
            bufferLen = decrypted.size
            bufferPos = 0
            Log.d(TAG, "Decrypted $bytesRead bytes to ${decrypted.size} bytes")
        }

        val available = bufferLen - bufferPos
        val toCopy = minOf(len, available)
        System.arraycopy(buffer, bufferPos, b, off, toCopy)
        bufferPos += toCopy

        return toCopy
    }

    override fun close() {
        try {
            val finalBytes = cipher.doFinal()
            if (finalBytes.isNotEmpty()) {
                Log.d(TAG, "Final decryption block: ${finalBytes.size} bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in final decryption: ${e.message}")
        }
        sourceStream.close()
        Log.d(TAG, "Stream closed")
    }

    companion object {
        private const val TAG = "EncryptedInputStream"
    }
}

class EncryptedOutputStream(
    private val filePath: String,
    private val key: ByteArray,
    private val nonce: ByteArray
) : OutputStream() {
    private val outputStream: FileOutputStream
    private val cipher: Cipher

    init {
        val resolvedPath = if (filePath.startsWith("file://")) {
            filePath.substring(7)
        } else {
            filePath
        }

        Log.d(TAG, "Initializing EncryptedOutputStream:")
        Log.d(TAG, "  ➤ original path: $filePath")
        Log.d(TAG, "  ➤ resolved path: $resolvedPath")
        Log.d(TAG, "  ➤ key length: ${key.size}")
        Log.d(TAG, "  ➤ nonce length: ${nonce.size}")
        Log.d(TAG, "  ➤ key (base64): ${Base64.getEncoder().encodeToString(key)}")
        Log.d(TAG, "  ➤ nonce (base64): ${Base64.getEncoder().encodeToString(nonce)}")

        val file = File(resolvedPath)
        if (file.exists()) {
            Log.d(TAG, "File already exists, will be overwritten: $resolvedPath")
        }

        file.parentFile?.mkdirs()
        outputStream = FileOutputStream(resolvedPath)

        cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

        Log.d(TAG, "Successfully initialized")
    }

    override fun write(b: Int) {
        val buffer = ByteArray(1)
        buffer[0] = b.toByte()
        write(buffer, 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        Log.d(TAG, "Writing data of size: $len")
        val encrypted = cipher.update(b, off, len)
        outputStream.write(encrypted)
        Log.d(TAG, "Encrypted $len bytes to ${encrypted.size} bytes")
    }

    override fun close() {
        try {
            val finalBytes = cipher.doFinal()
            if (finalBytes.isNotEmpty()) {
                outputStream.write(finalBytes)
                Log.d(TAG, "Final encryption block: ${finalBytes.size} bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in final encryption: ${e.message}")
        }
        Log.d(TAG, "Closing stream")
        outputStream.close()
        Log.d(TAG, "Closed successfully")
    }

    companion object {
        private const val TAG = "EncryptedOutputStream"
    }
} 
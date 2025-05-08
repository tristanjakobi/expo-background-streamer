package expo.modules.backgroundstreamer

import android.util.Log
import java.io.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

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
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        Log.d(TAG, "Successfully initialized EncryptedInputStream with key length: ${key.size}, nonce length: ${nonce.size}")
    }

    override fun read(): Int {
        if (bufferPos >= bufferLen) {
            val bytesRead = sourceStream.read(buffer)
            if (bytesRead <= 0) {
                Log.d(TAG, "No more data to read or error occurred")
                return bytesRead
            }

            val encrypted = cipher.update(buffer, 0, bytesRead)
            System.arraycopy(encrypted, 0, buffer, 0, encrypted.size)
            bufferLen = encrypted.size
            bufferPos = 0
            Log.d(TAG, "Encrypted $bytesRead bytes")
        }

        return buffer[bufferPos++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (bufferPos >= bufferLen) {
            val bytesRead = sourceStream.read(buffer)
            if (bytesRead <= 0) {
                return bytesRead
            }

            val encrypted = cipher.update(buffer, 0, bytesRead)
            System.arraycopy(encrypted, 0, buffer, 0, encrypted.size)
            bufferLen = encrypted.size
            bufferPos = 0
        }

        val available = bufferLen - bufferPos
        val toCopy = minOf(len, available)
        System.arraycopy(buffer, bufferPos, b, off, toCopy)
        bufferPos += toCopy

        return toCopy
    }

    override fun close() {
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

        val file = File(resolvedPath)
        if (file.exists()) {
            Log.d(TAG, "File already exists, will be overwritten: $resolvedPath")
        }

        file.parentFile?.mkdirs()
        outputStream = FileOutputStream(resolvedPath)

        cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

        Log.d(TAG, "Successfully initialized")
    }

    override fun write(b: Int) {
        val buffer = ByteArray(1)
        buffer[0] = b.toByte()
        write(buffer, 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        Log.d(TAG, "Writing data of size: $len")

        val decrypted = cipher.update(b, off, len)
        outputStream.write(decrypted)

        val file = File(filePath)
        val exists = file.exists()
        val size = file.length()

        Log.d(TAG, "File written:")
        Log.d(TAG, "  ➤ path: $filePath")
        Log.d(TAG, "  ➤ exists: $exists")
        Log.d(TAG, "  ➤ size: $size bytes")
    }

    override fun close() {
        Log.d(TAG, "Closing stream")
        outputStream.close()
        Log.d(TAG, "Closed successfully")
    }

    companion object {
        private const val TAG = "EncryptedOutputStream"
    }
} 
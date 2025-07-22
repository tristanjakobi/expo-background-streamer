package expo.modules.backgroundstreamer

import android.util.Log
import java.io.*
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

class EncryptedInputStream(
    sourceStream: InputStream,
    key: ByteArray,
    nonce: ByteArray
) : CipherInputStream(
    sourceStream,
    Cipher.getInstance("AES/CTR/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(nonce))
        Log.d(TAG, "[OPEN] Initialized EncryptedInputStream with key length: ${key.size}, nonce length: ${nonce.size}")
        Log.d(TAG, "[OPEN] Key (base64): ${Base64.getEncoder().encodeToString(key)}")
        Log.d(TAG, "[OPEN] Nonce (base64): ${Base64.getEncoder().encodeToString(nonce)}")
    }
) {
    override fun read(): Int {
        val result = super.read()
        Log.d(TAG, "[READ] Single byte: ${if (result == -1) "EOF" else result}")
        return result
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val bytesRead = super.read(b, off, len)
        Log.d(TAG, "[READ] Buffer: requested=$len, read=$bytesRead")
        return bytesRead
    }

    override fun close() {
        Log.d(TAG, "[CLOSE] Closing EncryptedInputStream")
        try {
            super.close()
            Log.d(TAG, "[CLOSE] Closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "[CLOSE] Error closing stream: ${e.message}")
        }
    }
    companion object {
        private const val TAG = "EncryptedInputStream"
    }
}

class EncryptedOutputStream(
    outputStream: OutputStream,
    key: ByteArray,
    nonce: ByteArray
) : CipherOutputStream(
    outputStream,
    Cipher.getInstance("AES/CTR/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(nonce))
        Log.d(TAG, "[OPEN] Initialized EncryptedOutputStream with key length: ${key.size}, nonce length: ${nonce.size}")
        Log.d(TAG, "[OPEN] Key (base64): ${Base64.getEncoder().encodeToString(key)}")
        Log.d(TAG, "[OPEN] Nonce (base64): ${Base64.getEncoder().encodeToString(nonce)}")
    }
) {
    override fun write(b: Int) {
        Log.d(TAG, "[WRITE] Single byte: $b")
        super.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        Log.d(TAG, "[WRITE] Buffer: offset=$off, length=$len")
        super.write(b, off, len)
    }

    override fun close() {
        Log.d(TAG, "[CLOSE] Closing EncryptedOutputStream")
        try {
            super.close()
            Log.d(TAG, "[CLOSE] Closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "[CLOSE] Error closing stream: ${e.message}")
        }
    }
    companion object {
        private const val TAG = "EncryptedOutputStream"
    }
} 
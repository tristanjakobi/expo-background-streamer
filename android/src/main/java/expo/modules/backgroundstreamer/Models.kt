package expo.modules.backgroundstreamer

import expo.modules.kotlin.records.Record
import java.util.UUID
import java.io.Serializable

data class UploadOptions(
    val url: String,
    val path: String,
    val method: String = "POST",
    val headers: Map<String, String> = mapOf(),
    val customTransferId: String? = null,
    val appGroup: String? = null,
    val encryption: EncryptionOptions? = null,
    val compression: CompressionOptions? = null
) : Record, Serializable {
    val uploadId: String = customTransferId ?: UUID.randomUUID().toString()
}

data class DownloadOptions(
    val url: String,
    val path: String,
    val method: String = "GET",
    val headers: Map<String, String> = mapOf(),
    val customTransferId: String? = null,
    val appGroup: String? = null,
    val encryption: EncryptionOptions? = null,
    val compression: CompressionOptions? = null
) : Record, Serializable {
    val downloadId: String = customTransferId ?: UUID.randomUUID().toString()
}

data class EncryptionOptions(
    val enabled: Boolean,
    val key: String?,
    val nonce: String?
) : Record, Serializable

data class CompressionOptions(
    val enabled: Boolean
) : Record, Serializable 
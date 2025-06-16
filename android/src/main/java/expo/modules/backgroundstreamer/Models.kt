package expo.modules.backgroundstreamer

import expo.modules.kotlin.records.Record

data class UploadOptions(
    val url: String,
    val fileUri: String,
    val uploadId: String,
    val headers: Map<String, String> = mapOf(),
    val encryption: EncryptionOptions? = null
) : Record

data class DownloadOptions(
    val url: String,
    val fileUri: String,
    val downloadId: String,
    val headers: Map<String, String> = mapOf(),
    val encryption: EncryptionOptions? = null
) : Record

data class EncryptionOptions(
    val enabled: Boolean,
    val key: String,
    val nonce: String
) : Record 
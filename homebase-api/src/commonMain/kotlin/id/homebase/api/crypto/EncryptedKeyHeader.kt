package id.homebase.api.crypto

import id.homebase.api.toBase64
import id.homebase.api.common.SecureByteArray
import id.homebase.api.client.KeyHeader
import id.homebase.api.prototype.lib.serialization.Base64ByteArraySerializer
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

/**
 * Encrypted key header for secure key storage
 * Port of C# EncryptedKeyHeader class from Odin.Services.Peer.Encryption
 */
@Serializable
class EncryptedKeyHeader(
    var encryptionVersion: Int = 1,
    var type: EncryptionType = EncryptionType.Aes,
    @Serializable(with = Base64ByteArraySerializer::class)
    var iv: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    var encryptedAesKey: ByteArray
) {
    /**
     * Decrypts this Encrypted Key header
     * @param key The decryption key
     * @return Decrypted KeyHeader
     * @throws Exception if unsupported encryption version
     */
    suspend fun decryptAesToKeyHeader(key: SecureByteArray): KeyHeader {
        if (encryptionVersion == 1) {
            val bytes = AesCbc.decrypt(encryptedAesKey, key, iv)
            val kh = KeyHeader.Companion.fromCombinedBytes(bytes, 16, 16)
            SecureByteArray(bytes).clear()
            return kh
        }

        throw Exception("Unsupported encryption version")
    }

    companion object {
        /**
         * Encrypts a KeyHeader using AES
         * @param keyHeader The key header to encrypt
         * @param iv The initialization vector
         * @param key The encryption key
         * @return Encrypted key header
         */
        suspend fun encryptKeyHeaderAes(
            keyHeader: KeyHeader,
            iv: ByteArray,
            key: SecureByteArray
        ): EncryptedKeyHeader {
            val secureKeyHeader = keyHeader.combine()
            val data = AesCbc.encrypt(secureKeyHeader.unsafeBytes, key, iv)
            secureKeyHeader.clear()

            return EncryptedKeyHeader(
                encryptionVersion = 1,
                type = EncryptionType.Aes,
                iv = iv,
                encryptedAesKey = data
            )
        }

        /**
         * Creates an empty EncryptedKeyHeader
         */
        fun empty(): EncryptedKeyHeader {
            val empty = ByteArray(16) { 0 }
            return EncryptedKeyHeader(
                encryptionVersion = 1,
                type = EncryptionType.Aes,
                iv = empty,
                encryptedAesKey = ByteArrayUtil.combine(empty, empty, empty)
            )
        }

        fun bytesToInt32(bytes: ByteArray): Int {
            require(bytes.size == 4)
            return (bytes[0].toInt() and 0xFF) or
                    ((bytes[1].toInt() and 0xFF) shl 8) or
                    ((bytes[2].toInt() and 0xFF) shl 16) or
                    ((bytes[3].toInt() and 0xFF) shl 24)
        }
    }
}

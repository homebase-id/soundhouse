package id.homebase.api.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlin.uuid.Uuid

/** Cryptographic hashing utilities using cryptography-kotlin */
object HashUtil {
    const val SHA256_ALGORITHM = "SHA-256"

    private val crypto = CryptographyProvider.Default
    private val sha256Algo = crypto.get(SHA256)
    private val hkdfAlgo = crypto.get(HKDF.Companion)

    /** Compute SHA-256 hash of input */
    suspend fun sha256(input: ByteArray): ByteArray {
        return sha256Algo.hasher().hash(input)
    }

    /**
     * Synchronous SHA-256, pure-Kotlin (FIPS PUB 180-4 §6.2).
     *
     * The default [sha256] suspends because `cryptography-kotlin`'s WebCrypto
     * backend on wasmJs is async-only. Synchronous identity hashing (OdinId
     * construction, KSerializer.deserialize) cannot wait on that, so we ship a
     * platform-agnostic implementation here. Inputs from those paths are tiny
     * (a domain string → 32-byte digest) so performance is irrelevant. Verified
     * bit-for-bit against [sha256] in [HashUtilTest].
     */
    fun sha256Sync(input: ByteArray): ByteArray = PureKotlinSha256.hash(input)

    /** Compute SHA-256 hash of a stream with optional nonce Returns hash and stream length */
    suspend fun streamSha256(
            inputStream: Source,
            optionalNonce: ByteArray? = null
    ): Pair<ByteArray, Long> {
        // Read all data from the stream
        val streamData = inputStream.readByteArray()
        val totalBytes = streamData.size.toLong()

        // Combine nonce (if provided) with stream data
        val dataToHash =
                if (optionalNonce != null) {
                    optionalNonce + streamData
                } else {
                    streamData
                }

        // Hash the combined data
        val hash = sha256Algo.hasher().hash(dataToHash)

        return Pair(hash, totalBytes)
    }

    /** HKDF (HMAC-based Extract-and-Expand Key Derivation Function) using SHA-256 RFC 5869 */
    suspend fun hkdf(sharedEccSecret: ByteArray, salt: ByteArray, outputKeySize: Int): ByteArray {
        require(outputKeySize >= 16) { "Output key size cannot be less than 16" }

        // Use cryptography-kotlin HKDF with SHA-256
        val derivation =
                hkdfAlgo.secretDerivation(
                        digest = SHA256,
                        outputSize = outputKeySize.bytes,
                        salt = salt,
                        info = null // No additional info in our use case
                )

        return derivation.deriveSecretToByteArray(sharedEccSecret)
    }

    /** Reduce a SHA-256 hash to 16 bytes by taking first 16 bytes */
    private suspend fun reduceSha256Hash(data: ByteArray): ByteArray {
        val hash = sha256(data)
        return hash.copyOf(16)
    }
}

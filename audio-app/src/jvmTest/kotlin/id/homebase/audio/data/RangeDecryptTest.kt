package id.homebase.audio.data

import id.homebase.api.client.KeyHeader
import id.homebase.api.client.auth.ApiCredentials
import id.homebase.api.client.auth.CredentialsManager
import id.homebase.api.client.drives.cache.DriveFileProviderCached
import id.homebase.api.common.OdinId
import id.homebase.api.common.SecureByteArray
import id.homebase.api.crypto.AesCbc
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.uuid.Uuid

/**
 * The streaming approach rests on this: any byte range of a whole-file AES-CBC payload can be
 * decrypted from the ciphertext range plus the preceding block. Runs the production cached
 * provider against a server that only ever hands out the requested ciphertext bytes.
 */
class RangeDecryptTest {
    private val cacheDir: File = Files.createTempDirectory("hba-range").toFile()

    @AfterTest
    fun cleanup() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `every range decrypts to the matching plaintext`() = runBlocking {
        for (size in listOf(1, 15, 16, 17, 1000, 4096, 100_003)) {
            val plaintext = Random(size).nextBytes(size)
            val keyHeader = KeyHeader.newRandom16()
            val provider = providerFor(encrypt(plaintext, keyHeader))
            for ((start, length) in rangesFor(size)) {
                val bytes = provider.getPayloadBytesDecrypted(
                    driveId = audioDriveId,
                    fileId = Uuid.random(),
                    key = AUDIO_PAYLOAD_KEY,
                    keyHeader = keyHeader,
                    chunkStart = start.toLong(),
                    chunkLength = length.toLong(),
                )!!.bytes
                assertContentEquals(
                    plaintext.copyOfRange(start, start + length),
                    bytes,
                    "size=$size start=$start length=$length",
                )
            }
        }
    }

    private fun rangesFor(size: Int): List<Pair<Int, Int>> {
        val fixed = listOf(0 to size, 0 to 1, size - 1 to 1, 0 to minOf(size, 100))
        val aligned = if (size > 48) listOf(16 to 32, 32 to size - 32, 17 to 30) else emptyList()
        val random = Random(size * 31)
        val randomRanges = List(20) {
            val start = random.nextInt(size)
            start to (1 + random.nextInt(size - start))
        }
        return fixed + aligned + randomRanges
    }

    private suspend fun providerFor(ciphertext: ByteArray): DriveFileProviderCached {
        val credentials = CredentialsManager().apply {
            setActiveCredentials(ApiCredentials.create(OdinId("range.example"), "token", SecureByteArray(ByteArray(16))))
        }
        return DriveFileProviderCached(
            HttpClient(FakePayloadServer(ciphertext).engine),
            credentials,
            TestFileOps(cacheDir),
        )
    }

    private suspend fun encrypt(plaintext: ByteArray, keyHeader: KeyHeader): ByteArray =
        AesCbc.streamEncryptWithCbc(flowOf(plaintext), keyHeader.aesKey, keyHeader.iv).toList()
            .fold(ByteArray(0)) { acc, chunk -> acc + chunk }
}

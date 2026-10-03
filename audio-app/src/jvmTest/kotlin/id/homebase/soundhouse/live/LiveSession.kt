package id.homebase.soundhouse.live

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import id.homebase.api.client.HttpClientProvider
import id.homebase.api.client.auth.ApiCredentials
import id.homebase.api.client.auth.CredentialsManager
import id.homebase.api.client.drives.cache.DriveFileProviderCached
import id.homebase.api.client.drives.files.DriveFileProvider
import id.homebase.api.client.drives.query.DriveQueryProvider
import id.homebase.api.client.drives.upload.DriveUploadProvider
import id.homebase.api.common.OdinId
import id.homebase.api.common.SecureByteArray
import id.homebase.soundhouse.data.AudioDriveApi
import id.homebase.soundhouse.data.TestFileOps
import id.homebase.core.config.audioLabeledDrive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/**
 * The disposable test identity's session. Its token and secret never leave this class: no
 * toString, no logging, and every Kermit line is scrubbed of both before it is written.
 */
internal class LiveSession private constructor(private val raw: RawSession) {
    val identity: String get() = raw.identity

    override fun toString(): String = "LiveSession(${raw.identity})"

    suspend fun credentialsManager(): CredentialsManager = CredentialsManager().apply {
        setActiveCredentials(
            ApiCredentials.create(
                OdinId(raw.identity),
                raw.clientAuthToken,
                SecureByteArray(Base64.decode(raw.sharedSecret)),
            )
        )
    }

    /** Same wiring the app uses after sign-in, against the Audio drive only. */
    suspend fun audioDriveApi(
        cacheDir: File,
        credentials: CredentialsManager? = null,
    ): AudioDriveApi {
        val http = HttpClientProvider.create()
        val credentials = credentials ?: credentialsManager()
        val fileOps = TestFileOps(cacheDir)
        return AudioDriveApi(
            queryProvider = DriveQueryProvider(http, credentials),
            uploadProvider = DriveUploadProvider(http, credentials, fileOps),
            fileProvider = DriveFileProvider(http, credentials, DriveFileProviderCached(http, credentials, fileOps)),
            fileOps = fileOps,
        )
    }

    @Serializable
    private class RawSession(
        val identity: String,
        val appId: String,
        val clientAuthToken: String,
        val sharedSecret: String,
        val audioDriveAlias: String,
        val audioDriveType: String,
    )

    private class RedactingLogWriter(private val secrets: List<String>) : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            if (severity < Severity.Info) return
            val clean = secrets.fold(message) { acc, s -> acc.replace(s, "<redacted>") }
            println("$severity ($tag) $clean${throwable?.let { " — ${it::class.simpleName}" } ?: ""}")
        }
    }

    companion object {
        private val file = File(System.getProperty("user.home"), ".config/homebase-audio-test/session.json")
        private val json = Json { ignoreUnknownKeys = true }

        /** Skips the calling test (JUnit assumption) when there is no session on this machine. */
        fun requireOrSkip(): LiveSession {
            assumeTrue("no live session at ~/.config/homebase-audio-test/session.json", file.isFile)
            val raw = json.decodeFromString<RawSession>(file.readText())
            check(Uuid.parse(raw.audioDriveAlias) == audioLabeledDrive.drive.alias) { "session is for a different Audio drive alias" }
            check(Uuid.parse(raw.audioDriveType) == audioLabeledDrive.drive.type) { "session is for a different Audio drive type" }
            Logger.setLogWriters(RedactingLogWriter(listOf(raw.clientAuthToken, raw.sharedSecret)))
            return LiveSession(raw)
        }
    }
}

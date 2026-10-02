package id.homebase.api.youauth

import co.touchlab.kermit.Logger
import id.homebase.api.common.OdinId
import id.homebase.api.common.SecureByteArray
import id.homebase.api.crypto.AesCbc
import id.homebase.api.crypto.EccKeyPair
import id.homebase.api.crypto.EccKeySize
import id.homebase.api.crypto.HashUtil
import id.homebase.api.crypto.generateEccKeyPair
import id.homebase.api.crypto.performEcdhKeyAgreement
import id.homebase.api.crypto.publicKeyFromJwkBase64Url
import id.homebase.api.crypto.publicKeyToJwkBase64Url
import id.homebase.api.device.deviceDisplayName
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.io.encoding.Base64

data class AuthResult(
    val clientAuthToken: String,
    val sharedSecret: String,
    val identity: OdinId
)

class YouAuthProvider(
    private val httpClient: HttpClient,
    private val identity: OdinId
) {
    private val baseApiUrl: String = identity.domainName.toHttpsBaseUrl()
    private val ownerApiUrl: String = identity.domainName.toHttpsBaseUrl()

    companion object {
        private const val TAG = "YouAuthProvider"
    }

    suspend fun exchangeDigestForToken(
        base64ExchangedSecretDigest: String
    ): YouAuthTokenResponse {
        val response =
            httpClient.post("$ownerApiUrl/api/owner/v1/youauth/token") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("secret_digest" to base64ExchangedSecretDigest))
            }

        if (response.status.value != 200) {
            error("Token exchange failed: ${response.status.value}")
        }

        return response.body()
    }

    suspend fun finalizeAuthentication(
        identity: OdinId,
        keyPair: EccKeyPair,
        password: SecureByteArray,
        publicKey: String,
        salt: String
    ): AuthResult {
        val remotePublicKey = publicKeyFromJwkBase64Url(publicKey)
        val saltBytes = Base64.decode(salt)

        val exchangedSecret =
            performEcdhKeyAgreement(keyPair, password, remotePublicKey, saltBytes)

        val digest =
            HashUtil.sha256(exchangedSecret.unsafeBytes)

        val token =
            exchangeDigestForToken(Base64.encode(digest))

        val sharedSecret =
            AesCbc.decrypt(
                Base64.decode(token.base64SharedSecretCipher),
                exchangedSecret.unsafeBytes,
                Base64.decode(token.base64SharedSecretIv)
            )

        val clientAuthToken =
            AesCbc.decrypt(
                Base64.decode(token.base64ClientAuthTokenCipher),
                exchangedSecret.unsafeBytes,
                Base64.decode(token.base64ClientAuthTokenIv)
            )

        return AuthResult(
            clientAuthToken = Base64.encode(clientAuthToken),
            sharedSecret = Base64.encode(sharedSecret),
            identity = identity
        )
    }

    suspend fun logout(): Boolean =
        try {
            httpClient.post("$baseApiUrl/api/apps/v1/auth/logout")
            true
        } catch (e: Exception) {
            Logger.e(throwable = e, tag = TAG) { "Logout failed" }
            false
        }
}

private fun String.toHttpsBaseUrl(): String {
    val trimmed = trim()
    return when {
        trimmed.startsWith("https://") -> trimmed
        trimmed.startsWith("http://") ->
            "https://${trimmed.removePrefix("http://")}"
        else -> "https://$trimmed"
    }
}

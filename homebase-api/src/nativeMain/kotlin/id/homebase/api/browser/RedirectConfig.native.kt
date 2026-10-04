package id.homebase.api.browser

actual object RedirectConfig {
    actual val scheme: String = "homebase-soundhouse"

    actual fun buildRedirectUri(clientId: String): String {
        return "homebase-soundhouse://$clientId/authorization-code-callback"
    }
}

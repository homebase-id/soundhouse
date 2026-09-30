package id.homebase.api.browser

actual object RedirectConfig {
    actual val scheme: String = "homebase-audio"

    actual fun buildRedirectUri(clientId: String): String {
        return "homebase-audio://$clientId/authorization-code-callback"
    }
}

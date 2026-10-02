package id.homebase.api.di

import co.touchlab.kermit.Logger
import id.homebase.api.client.HttpClientProvider
import id.homebase.api.client.auth.CredentialsManager
import id.homebase.api.client.auth.OwnerSessionRepository
import id.homebase.api.client.contacts.ContactInfoGateway
import id.homebase.api.client.drives.cache.DriveFileProviderCached
import id.homebase.api.client.drives.files.DriveFileHttpProvider
import id.homebase.api.client.drives.files.DriveFileOperationsProvider
import id.homebase.api.client.drives.files.DriveFileProvider
import id.homebase.api.client.drives.files.reactions.DriveFileGroupReactionProvider
import id.homebase.api.client.drives.query.DriveQueryProvider
import id.homebase.api.client.drives.upload.DriveUploadProvider
import id.homebase.api.client.eventbus.EventBus
import id.homebase.api.client.identity.PublicIdentityRepository
import id.homebase.api.client.profile.PublicProfileProviderCached
import id.homebase.api.file.StartupCacheAudit
import id.homebase.api.sync.database.DatabaseManager
import id.homebase.api.youauth.SecurityContextProvider
import id.homebase.api.youauth.UsernameStorage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val apiModule = module {
    single { DatabaseManager.appDb }

    single<CoroutineScope> {
        val handler = CoroutineExceptionHandler { _, e ->
            Logger.e(throwable = e, tag = "AppScope") { "Unhandled coroutine exception" }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
    }

    // this creates the HttpClient
    single { HttpClientProvider.create() }

    singleOf(::CredentialsManager)
    singleOf(::OwnerSessionRepository)
    singleOf(::PublicIdentityRepository)
    singleOf(::DriveFileHttpProvider)
    singleOf(::DriveFileProviderCached)

    // YouAuthFlowManager is bound in homebase-core's AppModule where the platform
    // singletons (ImageLoader, FileOperationsProvider) needed by its
    // clearPlatformCaches hook are available. See homebase-core/.../AppModule.kt.

    single { UsernameStorage() }

    factoryOf(::DriveQueryProvider)
    factoryOf(::DriveUploadProvider)

    factoryOf(::DriveFileProvider)
    factoryOf(::DriveFileOperationsProvider)
    factoryOf(::DriveFileGroupReactionProvider)

    singleOf(::PublicProfileProviderCached)
    single { ContactInfoGateway(publicProfiles = get()) }

    factoryOf(::SecurityContextProvider)

    single { EventBus() }

    // Eager startup task: logs a one-shot breakdown of the cache directory so an
    // `adb logcat -s CacheAudit:*` capture shows where disk usage is going. The
    // audit work runs off the main thread (see StartupCacheAudit). Diagnostics only.
    single(createdAtStart = true) { StartupCacheAudit(get()) }
}

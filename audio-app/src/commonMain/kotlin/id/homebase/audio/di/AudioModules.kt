package id.homebase.audio.di

import co.touchlab.kermit.Logger
import id.homebase.api.client.drives.files.DriveFileProvider
import id.homebase.api.client.drives.upload.DriveUploadProvider
import id.homebase.api.di.apiModule
import id.homebase.api.file.CacheAudit
import id.homebase.api.file.CacheSweeper
import id.homebase.api.file.FileOperationsProvider
import id.homebase.api.file.wipeOutboxStaging
import id.homebase.api.sync.DriveSyncManager
import id.homebase.api.youauth.YouAuthFlowManager
import id.homebase.audio.data.AudioDriveApi
import id.homebase.audio.data.TrackStore
import id.homebase.audio.importing.TrackImporter
import id.homebase.api.file.systemFileSystem
import id.homebase.audio.download.DownloadStore
import id.homebase.audio.download.downloadsDirectory
import id.homebase.audio.playback.AudioStreamServer
import id.homebase.audio.playback.PlaybackController
import id.homebase.audio.playback.DefaultTrackLocator
import id.homebase.audio.playback.TrackLocator
import id.homebase.audio.ui.library.LibraryViewModel
import id.homebase.audio.ui.player.PlayerViewModel
import id.homebase.audio.ui.record.RecordViewModel
import id.homebase.core.audio.AudioPlayer
import id.homebase.audio.ui.loading.AppLoadingViewModel
import id.homebase.auth.login.LoginViewModel
import id.homebase.core.auth.AuthConnectionCoordinator
import id.homebase.core.config.mandatorySyncDrives
import id.homebase.core.notifications.NotificationBackend
import id.homebase.core.notifications.NotificationService
import id.homebase.core.notifications.PendingNotificationTap
import id.homebase.core.session.IdentitySessionScope
import id.homebase.core.settings.UserPreferences
import id.homebase.core.sync.DriveRegistry
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val audioAppModule = module {
    single { UserPreferences(get()) }

    single<NotificationBackend> { NoPushNotificationBackend }
    single { PendingNotificationTap() }
    single {
        NotificationService(
            api = get(),
            scope = get(),
            contactInfo = get(),
            userPreferences = get(),
            credentialsManager = get(),
            pendingNotificationTap = get(),
            notificationBackend = get(),
            eventBus = get(),
            authState = get<YouAuthFlowManager>().authState,
        )
    }

    // Only the copied auth coordinator reads the registry; it lives on the Chat drive, which this
    // app never requests, so its server half 403s and falls back to the mandatory Audio drive.
    single {
        val uploader = get<DriveUploadProvider>()
        val files = get<DriveFileProvider>()
        DriveRegistry(
            credentialsManager = get(),
            databaseManager = get(),
            getFileHeaderByUid = { driveId, uniqueId -> files.getFileHeaderByUid(driveId, uniqueId) },
            uploadFile = { request -> uploader.uploadFile(request) },
            updateFileByUniqueId = { request -> uploader.updateFileByUniqueId(request) },
            eventBus = get(),
        )
    }

    single {
        DriveSyncManager(
            get(), get(), get(), get(), get(),
            mandatoryDrives = mandatorySyncDrives.associate { it.drive.alias to it.label },
        )
    }

    single {
        val fileOps: FileOperationsProvider = get()
        YouAuthFlowManager(
            driveSyncManager = get(),
            credentialsManager = get(),
            httpClient = get(),
            driveFileProviderCached = get(),
            contactInfo = get(),
            clearPlatformCaches = {
                runCatching { CacheSweeper.sweepAll(CacheAudit.audit(fileOps.getCacheDirectory())) }
                    .onFailure { Logger.w(tag = "YouAuthFlowManager", throwable = it) { "logout cache sweep failed" } }
                runCatching { wipeOutboxStaging(fileOps.getOutboxStagingDirectory()) }
                    .onFailure { Logger.w(tag = "YouAuthFlowManager", throwable = it) { "logout outbox-staging wipe failed" } }
            },
        )
    }

    single { IdentitySessionScope(getKoin()) }

    // No background wake or push on any platform here, so the coordinator connects as soon as the
    // session is authenticated and keeps the websocket open.
    single(createdAtStart = true) {
        AuthConnectionCoordinator(
            credentialsManager = get(),
            ownerSessionRepository = get(),
            youAuthFlowManager = get(),
            driveSyncManager = get(),
            outboxSync = get(),
            eventBus = get(),
            databaseManager = get(),
            driveRegistry = get(),
            securityContextProvider = get(),
            peerWebSocketManager = get(),
            identitySession = get(),
        )
    }

    single { AudioDriveApi(get(), get(), get(), get()) }
    single { TrackStore(get(), get(), get(), get()) }
    single {
        val store = get<TrackStore>()
        TrackImporter(get<AudioDriveApi>(), get(), get(), onUploaded = store::upsert)
    }

    single { AudioStreamServer() }
    single {
        val api = get<AudioDriveApi>()
        DownloadStore(
            directory = downloadsDirectory(),
            downloader = { track, path, onProgress -> api.downloadTo(track, path, onProgress) },
            scope = get(),
            fileSystem = systemFileSystem,
        )
    }
    single<TrackLocator> { DefaultTrackLocator(get(), get(), get()) }
    single { PlaybackController(get<AudioPlayer>(), get(), get()) }

    viewModelOf(::AppLoadingViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::RecordViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::LibraryViewModel)
}

expect fun audioPlatformModule(): Module

fun allAudioModules(): List<Module> = listOf(audioPlatformModule(), apiModule, audioAppModule)

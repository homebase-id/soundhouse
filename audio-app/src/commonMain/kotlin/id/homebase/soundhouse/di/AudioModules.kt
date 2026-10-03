package id.homebase.soundhouse.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
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
import id.homebase.soundhouse.data.AudioDriveApi
import id.homebase.soundhouse.data.LibraryReconciler
import id.homebase.soundhouse.data.TrackManager
import id.homebase.soundhouse.data.TrackStore
import id.homebase.soundhouse.importing.TrackImporter
import id.homebase.soundhouse.playback.QualityBackfill
import id.homebase.api.file.systemFileSystem
import id.homebase.soundhouse.download.DownloadStore
import id.homebase.soundhouse.download.downloadsDirectory
import id.homebase.soundhouse.download.importQueueFile
import id.homebase.soundhouse.download.OfflineKeeper
import id.homebase.soundhouse.download.offlineStateFile
import id.homebase.soundhouse.download.importStagingDirectory
import id.homebase.soundhouse.download.listeningHistoryFile
import id.homebase.soundhouse.history.ListeningHistory
import id.homebase.soundhouse.history.ListeningRecorder
import id.homebase.soundhouse.ui.home.HomeViewModel
import id.homebase.soundhouse.playback.AudioStreamServer
import id.homebase.soundhouse.ui.common.CoverLoader
import id.homebase.soundhouse.playback.PlaybackController
import id.homebase.soundhouse.playback.DefaultTrackLocator
import id.homebase.soundhouse.playback.TrackLocator
import id.homebase.soundhouse.ui.library.LibraryViewModel
import id.homebase.soundhouse.ui.player.PlayerViewModel
import id.homebase.soundhouse.ui.settings.SettingsViewModel
import id.homebase.soundhouse.ui.collections.CollectionViewModel
import id.homebase.soundhouse.data.CollectionStore
import id.homebase.soundhouse.data.CollectionManager
import id.homebase.soundhouse.ui.record.RecordViewModel
import id.homebase.core.audio.AudioPlayer
import id.homebase.soundhouse.ui.loading.AppLoadingViewModel
import id.homebase.auth.login.LoginViewModel
import id.homebase.core.auth.AuthConnectionCoordinator
import id.homebase.core.config.mandatorySyncDrives
import id.homebase.core.session.IdentitySessionScope
import id.homebase.core.settings.UserPreferences
import id.homebase.core.sync.DriveRegistry
import org.koin.core.Koin
import id.homebase.soundhouse.settings.AudioSettings
import id.homebase.soundhouse.settings.StoredAudioSettings
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val audioAppModule = module {
    single { UserPreferences(get()) }


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
    single {
        AuthConnectionCoordinator(
            credentialsManager = get(),
            ownerSessionRepository = get(),
            youAuthFlowManager = get(),
            driveSyncManager = get(),
            eventBus = get(),
            databaseManager = get(),
            driveRegistry = get(),
            securityContextProvider = get(),
            identitySession = get(),
            // Audio drive only: never read the Chat drive's cross-app registry of optional drives.
            useDriveRegistry = false,
        )
    }

    single { AudioDriveApi(get(), get(), get(), get()) }
    single { TrackStore(get(), get(), get(), get()) }
    single {
        val api = get<AudioDriveApi>()
        LibraryReconciler(get(), { api.queryTrackFiles().mapTo(HashSet()) { it.fileId } }, get(), get())
    }
    single {
        val store = get<TrackStore>()
        TrackImporter(
            get<AudioDriveApi>(), get(), get(),
            onUploaded = store::upsert,
            queueFile = importQueueFile(),
            stagingDir = importStagingDirectory(),
            eventBus = get(),
            parallelism = get<AudioSettings>().preferences
                .map { it.uploadsAtOnce }
                .stateIn(get<CoroutineScope>(), SharingStarted.Eagerly, get<AudioSettings>().preferences.value.uploadsAtOnce),
        )
    }

    single { AudioStreamServer() }
    single { CoverLoader(get()) }
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
    single<AudioSettings> { StoredAudioSettings() }
    single { CollectionStore(get(), get(), get(), get()) }
    single {
        val collections = get<CollectionStore>()
        val tracks = get<TrackStore>()
        val playback = get<PlaybackController>()
        CollectionManager(
            editor = get<AudioDriveApi>(),
            collections = { collections.collections.value },
            tracks = { tracks.tracks.value },
            writeCollection = collections::upsert,
            writeTrack = tracks::upsert,
            onTrackChanged = playback::replaceTrack,
        )
    }
    single {
        val store = get<TrackStore>()
        val history = get<ListeningHistory>()
        OfflineKeeper(
            downloads = get(),
            tracks = store.tracks,
            tracksLoaded = store.isLoaded,
            history = history.entries,
            historyLoaded = history.isLoaded,
            settings = get(),
            network = get(),
            stateFile = offlineStateFile(),
            fileSystem = systemFileSystem,
            scope = get(),
        )
    }
    single {
        PlaybackController(get<AudioPlayer>(), get(), get(), initialSpeed = get<AudioSettings>().preferences.value.playbackSpeed)
    }
    single { ListeningHistory(listeningHistoryFile(), systemFileSystem, get(), get()) }
    single { ListeningRecorder(get(), get(), get()) }
    single {
        val store = get<TrackStore>()
        val downloads = get<DownloadStore>()
        val playback = get<PlaybackController>()
        TrackManager(
            editor = get<AudioDriveApi>(),
            writeLocal = store::upsert,
            removeDownload = downloads::remove,
            onChanged = playback::replaceTrack,
            onDeleted = playback::removeTrack,
        )
    }

    single { QualityBackfill(get(), get(), get(), get()) }

    viewModelOf(::AppLoadingViewModel)
    viewModel { PlayerViewModel(get(), get()) }
    viewModelOf(::HomeViewModel)
    viewModelOf(::RecordViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::LibraryViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel { params -> CollectionViewModel(params.get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}

expect fun audioPlatformModule(): Module

// Call once the database is open: these read it as they start.
fun Koin.startAudioServices() {
    get<AuthConnectionCoordinator>()
    get<ListeningRecorder>()
    get<LibraryReconciler>()
    get<OfflineKeeper>()
    get<QualityBackfill>()
}

fun allAudioModules(): List<Module> = listOf(audioPlatformModule(), apiModule, audioAppModule)

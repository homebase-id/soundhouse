package id.homebase.soundhouse.data

import co.touchlab.kermit.Logger
import id.homebase.api.client.auth.CredentialsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/** Once per signed-in session, after the local library has loaded, removes tracks deleted on the server. */
class LibraryReconciler(
    private val trackStore: TrackStore,
    private val listServerTrackIds: suspend () -> Set<Uuid>,
    credentialsManager: CredentialsManager,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            credentialsManager.credentialsFlow.filterNotNull().map { it.domain }.distinctUntilChanged().collect {
                trackStore.isLoaded.first { loaded -> loaded }
                reconcile()
            }
        }
    }

    suspend fun reconcile(): Int = try {
        trackStore.removeMissing(listServerTrackIds())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A partial or failed listing must never delete anything; try again next session.
        Logger.w(e, "LibraryReconciler") { "Could not list server tracks; library left as is" }
        0
    }
}

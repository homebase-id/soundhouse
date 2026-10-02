package id.homebase.api.sync.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.uuid.Uuid

class ConnectionCacheWrapper(
    driver: SqlDriver,
    connectionCacheAdapter: ConnectionCache.Adapter,
    private val databaseManager: DatabaseManager,
) {
    private val delegate = ConnectionCacheQueries(driver, connectionCacheAdapter)

    suspend fun selectByIdentity(identityId: Uuid): List<ConnectionCache> =
        databaseManager.readValue("connectionCache.selectByIdentity") {
            delegate.selectByIdentity(identityId).executeAsList()
        }

    suspend fun selectByIdentityAndStatus(
        identityId: Uuid,
        status: String,
    ): List<ConnectionCache> =
        databaseManager.readValue("connectionCache.selectByIdentityAndStatus") {
            delegate.selectByIdentityAndStatus(identityId, status).executeAsList()
        }

    suspend fun upsert(
        identityId: Uuid,
        odinId: String,
        status: String,
        lastRefresh: Long,
    ) {
        databaseManager.withWrite {
            delegate.upsert(identityId, odinId, status, lastRefresh)
        }
    }
}

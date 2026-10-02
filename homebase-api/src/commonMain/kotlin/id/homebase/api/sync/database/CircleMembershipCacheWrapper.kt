package id.homebase.api.sync.database

import app.cash.sqldelight.db.SqlDriver

class CircleMembershipCacheWrapper(
    driver: SqlDriver,
    private val databaseManager: DatabaseManager,
) {
    private val delegate = CircleMembershipCacheQueries(driver)

    suspend fun upsert(identityId: String, circlesJson: String, lastRefresh: Long) {
        databaseManager.withWrite {
            delegate.upsert(identityId, circlesJson, lastRefresh)
        }
    }
}

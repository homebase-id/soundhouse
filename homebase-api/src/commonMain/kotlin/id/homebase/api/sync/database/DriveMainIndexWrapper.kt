package id.homebase.api.sync.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlCursor
import kotlin.Any
import kotlin.Long
import kotlin.compareTo
import id.homebase.api.client.drives.HomebaseFile
import id.homebase.api.serialization.OdinSystemSerializer
import kotlin.uuid.Uuid

class DriveMainIndexWrapper(
    driver: SqlDriver,
    driveMainIndexAdapter: DriveMainIndex.Adapter,
    private val databaseManager: DatabaseManager,
) {
    private val delegate = DriveMainIndexQueries(driver, driveMainIndexAdapter)

    // All reads below run on DatabaseManager's read lane (readValue → readDispatcher), NOT
    // on the caller's thread. This is the single-row counterpart to PR #600's list-read
    // routing: a synchronous SQLite read here on the Main thread (e.g. a header lookup from
    // a viewModelScope/Main.immediate coroutine) would block in
    // SQLiteConnectionPool.waitForConnection behind a long cold-load read and wedge the UI —
    // the proven cold-boot "tap does nothing until green" cause. Row→model deserialization is
    // done OUTSIDE readValue so the lane's slot (and SlowDbRead sql= timing) cover only SQL.
    suspend fun <T : Any> selectByIdentityAndDriveAndFile(
        identityId: Uuid,
        driveId: Uuid,
        fileId: Uuid,
        mapper: (
            rowId: Long,
            identityId: Uuid,
            driveId: Uuid,
            fileId: Uuid?,
            uniqueId: Uuid?,
            globalTransitId: Uuid?,
            senderId: String?,
            originalAuthor: String?,
            groupId: Uuid?,
            fileType: Long,
            dataType: Long,
            archivalStatus: Long,
            fileState: Long,
            historyStatus: Long,
            userDate: Long,
            created: Long,
            modified: Long,
            fileSystemType: Long,
            jsonHeader: String,
        ) -> T,
    ): T? = databaseManager.readValue("selectByIdentityAndDriveAndFile(mapper)") {
        delegate.selectByIdentityAndDriveAndFile(identityId, driveId, fileId, mapper)
            .executeAsOneOrNull()
    }

    suspend fun selectByIdentityAndDriveAndFile(
        identityId: Uuid,
        driveId: Uuid,
        fileId: Uuid,
    ): DriveMainIndex? = databaseManager.readValue("selectByIdentityAndDriveAndFile") {
        delegate.selectByIdentityAndDriveAndFile(identityId, driveId, fileId).executeAsOneOrNull()
    }

    suspend fun selectByIdentityAndDriveAndUnique(
        identityId: Uuid,
        driveId: Uuid,
        uniqueId: Uuid,
    ): DriveMainIndex? = databaseManager.readValue("selectByIdentityAndDriveAndUnique") {
        delegate.selectByIdentityAndDriveAndUnique(identityId, driveId, uniqueId).executeAsOneOrNull()
    }

    suspend fun selectHomebaseFileByUnique(
        identityId: Uuid,
        driveId: Uuid,
        uniqueId: Uuid,
    ): HomebaseFile? {
        val row = selectByIdentityAndDriveAndUnique(identityId, driveId, uniqueId) ?: return null
        return OdinSystemSerializer.deserialize<HomebaseFile>(row.jsonHeader)
    }

    suspend fun <T : Any> selectAll(
        mapper: (
            rowId: Long,
            identityId: Uuid,
            driveId: Uuid,
            fileId: Uuid?,
            uniqueId: Uuid?,
            globalTransitId: Uuid?,
            senderId: String?,
            originalAuthor: String?,
            groupId: Uuid?,
            fileType: Long,
            dataType: Long,
            archivalStatus: Long,
            fileState: Long,
            historyStatus: Long,
            userDate: Long,
            created: Long,
            modified: Long,
            fileSystemType: Long,
            jsonHeader: String,
        ) -> T,
    ): List<T> = databaseManager.readValue("selectAll(mapper)") {
        delegate.selectAll(mapper).executeAsList()
    }

    suspend fun selectAll(): List<DriveMainIndex> =
        databaseManager.readValue("selectAll") { delegate.selectAll().executeAsList() }

    suspend fun countAll(): Long =
        databaseManager.readValue("countAll") { delegate.countAll().executeAsOne() }

    suspend fun upsertDriveMainIndex(
        identityId: Uuid,
        driveId: Uuid,
        fileId: Uuid,
        uniqueId: Uuid?,
        globalTransitId: Uuid?,
        groupId: Uuid?,
        senderId: String?,
        originalAuthor: String?,
        fileType: Long,
        dataType: Long,
        archivalStatus: Long,
        fileState: Long,
        historyStatus: Long,
        userDate: Long,
        created: Long,
        modified: Long,
        fileSystemType: Long,
        jsonHeader: String,
    ): Boolean {
        return databaseManager.withWriteValue {
            delegate.upsertDriveMainIndex(
                identityId,
                driveId,
                fileId,
                uniqueId,
                globalTransitId,
                groupId,
                senderId,
                originalAuthor,
                fileType,
                dataType,
                archivalStatus,
                fileState,
                historyStatus,
                userDate,
                created,
                modified,
                fileSystemType,
                jsonHeader
            ).value > 0
        }
    }

    suspend fun deleteAll(): Boolean {
        return databaseManager.withWriteValue { delegate.deleteAll().value > 0 }
    }
}
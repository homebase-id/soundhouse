package id.homebase.soundhouse.ui.importing

import id.homebase.soundhouse.importing.ImportFailure
import id.homebase.soundhouse.importing.ImportJob
import id.homebase.soundhouse.importing.ImportStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ImportSummaryTest {
    private fun job(status: ImportStatus, progress: Float = 0f) =
        ImportJob(Uuid.random(), "f.mp3", status, progress, if (status == ImportStatus.Failed) ImportFailure.Connection else null)

    @Test
    fun `failed files are left out of the batch and partial progress counts`() {
        val s = importSummary(
            listOf(job(ImportStatus.Done), job(ImportStatus.Uploading, 0.5f), job(ImportStatus.Queued), job(ImportStatus.Failed))
        )
        assertEquals(3, s.total)
        assertEquals(1, s.done)
        assertEquals(0.5f, s.progress)
        assertEquals(1, s.failed.size)
        assertTrue(s.isActive)
    }

    @Test
    fun `a finished batch is not active`() {
        val s = importSummary(listOf(job(ImportStatus.Done), job(ImportStatus.Done)))
        assertFalse(s.isActive)
        assertEquals(1f, s.progress)
    }
}

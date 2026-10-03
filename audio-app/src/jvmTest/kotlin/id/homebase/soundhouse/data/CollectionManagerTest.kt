package id.homebase.soundhouse.data

import id.homebase.api.client.KeyHeader
import id.homebase.api.client.drives.HomebaseFile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class CollectionManagerTest {
    private class FakeEditor : CollectionEditor {
        val log = mutableListOf<String>()
        override suspend fun createCollection(name: String, id: Uuid): Uuid { log += "create $name"; return Uuid.random() }
        override suspend fun renameCollection(collection: AudioCollection, newName: String) { log += "rename $newName" }
        override suspend fun deleteCollection(fileId: Uuid) { log += "delete collection" }
        override suspend fun setTrackTags(track: AudioTrack, tags: List<Uuid>) { log += "tag ${track.title} ${tags.size}" }
        override suspend fun getFile(fileId: Uuid): HomebaseFile? = null
    }

    private fun collection(name: String) = AudioCollection(Uuid.random(), Uuid.random(), name, 0, null, KeyHeader.empty())

    private fun track(title: String, vararg tags: Uuid) = AudioTrack(
        Uuid.random(), null, AudioTrackContent(title, 1, "audio/mpeg"), 0, null, tags.toList(), KeyHeader.empty(), KeyHeader.empty(),
    )

    private val mixes = collection("Mixes")
    private val talks = collection("Talks")
    private val otherTag = Uuid.random()

    private fun manager(editor: FakeEditor, tracks: List<AudioTrack>) = CollectionManager(
        editor, { listOf(mixes, talks) }, { tracks }, writeCollection = {}, writeTrack = {}, onTrackChanged = {},
    )

    @Test
    fun `retagging swaps collections and keeps other tags`() {
        val t = track("a", otherTag, mixes.id)
        assertEquals(listOf(otherTag, talks.id), retagged(t, listOf(mixes, talks), setOf(talks.id)))
        assertEquals(listOf(otherTag), retagged(t, listOf(mixes, talks), emptySet()))
    }

    @Test
    fun `membership writes only when it changes`() = runBlocking {
        val editor = FakeEditor()
        val t = track("a", mixes.id)
        manager(editor, listOf(t)).setMembership(t, setOf(mixes.id))
        manager(editor, listOf(t)).setMembership(t, setOf(mixes.id, talks.id))
        assertEquals(listOf("tag a 2"), editor.log)
    }

    @Test
    fun `deleting a collection untags its tracks before the collection goes`() = runBlocking {
        val editor = FakeEditor()
        val tracks = listOf(track("in", mixes.id, otherTag), track("out", talks.id))
        manager(editor, tracks).delete(mixes)
        assertEquals(listOf("tag in 1", "delete collection"), editor.log)
    }

    @Test
    fun `adding skips tracks already in the collection`() = runBlocking {
        val editor = FakeEditor()
        manager(editor, emptyList()).add(listOf(track("member", mixes.id), track("new")), mixes)
        assertEquals(listOf("tag new 1"), editor.log)
    }

    @Test
    fun `names are trimmed and must not be blank`() = runBlocking {
        val editor = FakeEditor()
        manager(editor, emptyList()).create("  Field recordings ")
        assertFailsWith<IllegalArgumentException> { manager(editor, emptyList()).create("   ") }
        assertEquals(listOf("create Field recordings"), editor.log)
    }
}

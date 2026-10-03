package id.homebase.soundhouse.importing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals

class UploadSlotsTest {
    @Test
    fun `a waiting large upload goes before small ones that arrive after it`() = runBlocking<Unit> {
        val slots = UploadSlots(MutableStateFlow(2))
        val order = Collections.synchronizedList(mutableListOf<String>())
        val releaseFirst = CompletableDeferred<Unit>()
        launch(Dispatchers.Default) { slots.withSlot(large = false) { order += "small1"; releaseFirst.await() } }
        delay(50)
        val large = async(Dispatchers.Default) { slots.withSlot(large = true) { order += "large" } }
        delay(50)
        val late = async(Dispatchers.Default) { slots.withSlot(large = false) { order += "small2" } }
        delay(50)
        releaseFirst.complete(Unit)
        withTimeout(5_000) { large.await(); late.await() }
        assertEquals(listOf("small1", "large", "small2"), order)
    }

    @Test
    fun `lowering the limit holds back new uploads until running ones finish`() = runBlocking<Unit> {
        val limit = MutableStateFlow(3)
        val slots = UploadSlots(limit)
        val running = java.util.concurrent.atomic.AtomicInteger()
        val maxSeen = java.util.concurrent.atomic.AtomicInteger()
        val jobs = (1..6).map {
            async(Dispatchers.Default) {
                slots.withSlot(large = false) {
                    val now = running.incrementAndGet(); maxSeen.accumulateAndGet(now) { a, b -> maxOf(a, b) }
                    delay(100); running.decrementAndGet()
                }
            }
        }
        delay(20)
        limit.value = 1
        maxSeen.set(0)
        delay(150)
        withTimeout(5_000) { jobs.forEach { it.await() } }
        assertEquals(1, maxSeen.get())
    }
}

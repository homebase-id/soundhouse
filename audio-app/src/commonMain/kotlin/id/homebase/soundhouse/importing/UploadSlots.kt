package id.homebase.soundhouse.importing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Up to [limit] uploads at once, where the limit can change while uploads run. A large upload waits for
 * every slot to drain and then holds them all; while it waits no new small upload starts, so it can't starve.
 */
class UploadSlots(private val limit: StateFlow<Int>) {
    private val inUse = MutableStateFlow(0)
    private val largeWaiting = MutableStateFlow(false)
    private val largeGate = Mutex()
    private val lock = Mutex()

    suspend fun <T> withSlot(large: Boolean, block: suspend () -> T): T {
        val held = acquire(large)
        try {
            return block()
        } finally {
            release(held)
        }
    }

    /** Returns the number of slots taken; give it back to [release]. */
    suspend fun acquire(large: Boolean): Int = if (large) largeGate.withLock { takeAll() } else takeOne()

    fun release(held: Int) {
        inUse.update { it - held }
    }

    private suspend fun takeOne(): Int {
        while (true) {
            val taken = lock.withLock {
                if (!largeWaiting.value && inUse.value < limit.value.coerceAtLeast(1)) {
                    inUse.value += 1
                    true
                } else false
            }
            if (taken) return 1
            awaitChange()
        }
    }

    private suspend fun takeAll(): Int {
        largeWaiting.value = true
        try {
            while (true) {
                val held = lock.withLock {
                    if (inUse.value == 0) limit.value.coerceAtLeast(1).also { inUse.value = it } else null
                }
                if (held != null) return held
                awaitChange()
            }
        } finally {
            largeWaiting.value = false
        }
    }

    private suspend fun awaitChange() {
        val seen = Triple(inUse.value, limit.value, largeWaiting.value)
        combine(inUse, limit, largeWaiting) { a, b, c -> Triple(a, b, c) }.first { it != seen }
    }
}

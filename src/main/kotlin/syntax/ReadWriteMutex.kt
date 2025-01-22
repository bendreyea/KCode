package org.editor.syntax
/*
 * Just in case if it may be useful
 * https://gist.github.com/bobvawter/4ff642d5996dfccb228425909f303306
 */


import kotlinx.coroutines.sync.withLock

enum class MutexMode { READ, WRITE }
enum class MutexState { LOCKED, UNLOCKED }
data class MutexInfo(val mode: MutexMode, val state: MutexState)

interface ReadWriteMutex {
    suspend fun <T> withReadLock(block: suspend () -> T): T
    suspend fun <T> withWriteLock(block: suspend () -> T): T
}

fun ReadWriteMutex(block: ReadWriteMutexBuilder.() -> Unit = {}): ReadWriteMutex {
    return ReadWriteMutexBuilder().apply(block).build()
}

class ReadWriteMutexBuilder internal constructor() {
    private var onStateChange: (suspend (MutexInfo) -> Unit)? = null

    fun onStateChange(block: suspend (MutexInfo) -> Unit) {
        onStateChange = block
    }

    internal fun build(): ReadWriteMutex = ReadWriteMutexImpl(onStateChange)
}

private class ReadWriteMutexImpl(
    private val onStateChange: (suspend (MutexInfo) -> Unit)?
) : ReadWriteMutex {
    private val allowNewReads = kotlinx.coroutines.sync.Mutex()
    private val allowNewWrites = kotlinx.coroutines.sync.Mutex()
    private val stateLock = kotlinx.coroutines.sync.Mutex()
    private var readers = 0

    override suspend fun <T> withReadLock(block: suspend () -> T): T {
        return try {
            allowNewReads.withLock {
                stateLock.withLock {
                    if (readers++ == 0) {
                        allowNewWrites.lock(this)
                        onStateChange?.invoke(MutexInfo(MutexMode.READ, MutexState.LOCKED))
                    }
                }
            }
            block()
        } finally {
            // Drain readers in stateLock
            stateLock.withLock {
                if (--readers == 0) {
                    try {
                        onStateChange?.invoke(MutexInfo(MutexMode.READ, MutexState.UNLOCKED))
                    } finally {
                        allowNewWrites.unlock(this)
                    }
                }
            }
        }
    }

    override suspend fun <T> withWriteLock(block: suspend () -> T): T {
        // Prevent new readers:
        return allowNewReads.withLock {
            // Wait for existing readers to finish:
            allowNewWrites.withLock {
                try {
                    onStateChange?.invoke(MutexInfo(MutexMode.WRITE, MutexState.LOCKED))
                    block()
                } finally {
                    onStateChange?.invoke(MutexInfo(MutexMode.WRITE, MutexState.UNLOCKED))
                }
            }
        }
    }
}

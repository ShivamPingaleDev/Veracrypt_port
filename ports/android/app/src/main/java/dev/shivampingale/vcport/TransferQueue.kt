package dev.shivampingale.vcport

/**
 * One in-memory batch of file copies. A single worker claims each row.
 * Cancel stops at the next file. Panic and dismount call [requestCancel].
 * Nothing here is written to disk or scheduled after the process dies.
 */
enum class TransferJobState(val label: String) {
    Waiting("Waiting"),
    Running("Running"),
    Done("Done"),
    Failed("Failed"),
    Cancelled("Cancelled")
}

data class TransferJob(
    val name: String,
    val bytes: Long,
    val state: TransferJobState,
    val detail: String = ""
)

class TransferQueue {
    private val lock = Any()
    private val jobs = mutableListOf<TransferJob>()

    @Volatile
    var cancelRequested: Boolean = false
        private set

    @Volatile
    var generation: Int = 0
        private set

    private var startedNanos: Long = 0L

    fun begin(items: List<Pair<String, Long>>) = synchronized(lock) {
        generation += 1
        cancelRequested = false
        startedNanos = System.nanoTime()
        jobs.clear()
        for ((name, bytes) in items) {
            jobs.add(TransferJob(name, bytes.coerceAtLeast(0L), TransferJobState.Waiting))
        }
    }

    /** False means this file and every later file were cancelled. */
    fun claim(index: Int): Boolean = synchronized(lock) {
        if (cancelRequested) {
            cancelFrom(index)
            return false
        }
        if (index in jobs.indices) {
            jobs[index] = jobs[index].copy(state = TransferJobState.Running)
        }
        true
    }

    fun finish(index: Int, failed: String?) = synchronized(lock) {
        if (index !in jobs.indices) return
        jobs[index] = if (failed == null) {
            jobs[index].copy(state = TransferJobState.Done, detail = "")
        } else {
            jobs[index].copy(state = TransferJobState.Failed, detail = failed)
        }
    }

    fun requestCancel() {
        cancelRequested = true
    }

    fun reset() = synchronized(lock) {
        generation += 1
        cancelRequested = true
        jobs.clear()
        startedNanos = 0L
    }

    fun snapshot(): List<TransferJob> = synchronized(lock) { jobs.toList() }

    /** Empty until some bytes have finished and a total size is known. */
    fun etaLabel(): String = synchronized(lock) {
        val total = jobs.sumOf { it.bytes }
        val done = jobs.sumOf { job ->
            if (job.state == TransferJobState.Done || job.state == TransferJobState.Failed) job.bytes else 0L
        }
        if (total <= 0L || done <= 0L || done >= total) return ""
        val elapsed = System.nanoTime() - startedNanos
        if (elapsed < 500_000_000L) return ""
        val seconds = (elapsed * (total - done) / done / 1_000_000_000L).coerceAtLeast(1L)
        "about ${seconds}s left"
    }

    private fun cancelFrom(index: Int) {
        for (i in index until jobs.size) {
            val state = jobs[i].state
            if (state == TransferJobState.Waiting || state == TransferJobState.Running) {
                jobs[i] = jobs[i].copy(state = TransferJobState.Cancelled, detail = "Queue cancelled")
            }
        }
    }
}

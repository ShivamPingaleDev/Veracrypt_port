import Foundation

/// One in-memory batch of file copies. A single worker claims each row.
/// Cancel stops at the next file. Panic and dismount call requestCancel().
/// Nothing here is written to disk or scheduled after the process dies.
enum TransferJobState: String {
    case waiting = "Waiting"
    case running = "Running"
    case done = "Done"
    case failed = "Failed"
    case cancelled = "Cancelled"

    var label: String { rawValue }
}

struct TransferJob: Identifiable {
    let id: Int
    let name: String
    let bytes: Int64
    let state: TransferJobState
    let detail: String
}

final class TransferQueue {
    private let lock = NSLock()
    private var jobs: [TransferJob] = []
    private var started = Date()
    private(set) var cancelRequested = false
    private(set) var generation = 0

    func begin(_ items: [(String, Int64)]) {
        lock.lock()
        generation += 1
        cancelRequested = false
        started = Date()
        jobs = items.enumerated().map { index, item in
            TransferJob(id: index, name: item.0, bytes: max(item.1, 0), state: .waiting, detail: "")
        }
        lock.unlock()
    }

    /// False means this file and every later file were cancelled.
    func claim(_ index: Int) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if cancelRequested {
            cancelFrom(index)
            return false
        }
        guard jobs.indices.contains(index) else { return true }
        let job = jobs[index]
        jobs[index] = TransferJob(id: job.id, name: job.name, bytes: job.bytes, state: .running, detail: "")
        return true
    }

    func finish(_ index: Int, failed: String?) {
        lock.lock()
        defer { lock.unlock() }
        guard jobs.indices.contains(index) else { return }
        let job = jobs[index]
        if let failed {
            jobs[index] = TransferJob(id: job.id, name: job.name, bytes: job.bytes, state: .failed, detail: failed)
        } else {
            jobs[index] = TransferJob(id: job.id, name: job.name, bytes: job.bytes, state: .done, detail: "")
        }
    }

    func requestCancel() {
        lock.lock()
        cancelRequested = true
        lock.unlock()
    }

    func reset() {
        lock.lock()
        generation += 1
        cancelRequested = true
        jobs = []
        lock.unlock()
    }

    func snapshot() -> [TransferJob] {
        lock.lock()
        defer { lock.unlock() }
        return jobs
    }

    /// Empty until some bytes have finished and a total size is known.
    func etaLabel() -> String {
        lock.lock()
        defer { lock.unlock() }
        let total = jobs.reduce(Int64(0)) { $0 + $1.bytes }
        let done = jobs.reduce(Int64(0)) { sum, job in
            (job.state == .done || job.state == .failed) ? sum + job.bytes : sum
        }
        if total <= 0 || done <= 0 || done >= total { return "" }
        let elapsed = Date().timeIntervalSince(started)
        if elapsed < 0.5 { return "" }
        let seconds = max(Int64(elapsed * Double(total - done) / Double(done)), 1)
        return "about \(seconds)s left"
    }

    private func cancelFrom(_ index: Int) {
        guard index < jobs.count else { return }
        for i in index..<jobs.count {
            let job = jobs[i]
            if job.state == .waiting || job.state == .running {
                jobs[i] = TransferJob(
                    id: job.id, name: job.name, bytes: job.bytes, state: .cancelled, detail: "Queue cancelled"
                )
            }
        }
    }
}

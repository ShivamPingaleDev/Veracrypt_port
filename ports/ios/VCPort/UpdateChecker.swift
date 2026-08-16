import Foundation

/// Opt-in HTTPS. ≤20s window to allowlisted hosts, then offline. No IPA/src fetch.
enum UpdateChecker {
    static var localVersion: String { SourcePin.localVersion }
    static var manifestURL: URL { SourcePin.manifestURL }

    typealias Result = SourcePin.CheckResult

    private final class NoRedirect: NSObject, URLSessionTaskDelegate {
        func urlSession(
            _ session: URLSession,
            task: URLSessionTask,
            willPerformHTTPRedirection response: HTTPURLResponse,
            newRequest request: URLRequest,
            completionHandler: @escaping (URLRequest?) -> Void
        ) {
            completionHandler(nil)
        }
    }

    static func check() throws -> SourcePin.CheckResult {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 8
        config.timeoutIntervalForResource = SourcePin.TrustedNet.windowSeconds
        config.httpMaximumConnectionsPerHost = 1
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        config.urlCache = nil
        let delegate = NoRedirect()
        let session = URLSession(configuration: config, delegate: delegate, delegateQueue: nil)
        defer { session.finishTasksAndInvalidate() }

        var warnings: [String] = []
        if let statusWarn = githubStatus(session: session) {
            warnings.append(statusWarn)
        }
        let body = try fetch(SourcePin.manifestURL.absoluteString, session: session)
        guard let json = try JSONSerialization.jsonObject(with: body) as? [String: Any],
              let remote = json["port_version"] as? String, !remote.isEmpty else {
            throw URLError(.cannotParseResponse)
        }
        if let sha = json["android_apk_sha256"] as? String, !sha.isEmpty {
            let hex = CharacterSet(charactersIn: "0123456789abcdefABCDEF")
            guard sha.count == 64, sha.unicodeScalars.allSatisfy({ hex.contains($0) }) else {
                throw URLError(.cannotParseResponse)
            }
        }
        let sha = json["android_apk_sha256"] as? String ?? ""
        let notes = json["notes"] as? String ?? ""
        let url = (json["ios_url"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? (json["download_url"] as? String ?? "")
        if !url.isEmpty, !url.hasPrefix("https://") {
            throw URLError(.cannotParseResponse)
        }
        let remoteCommit = json["upstream_commit"] as? String ?? ""
        let newer = SourcePin.compare(remote, localVersion) > 0
        let (officialNewer, officialVersion, officialWarn) = officialRelease(session: session)
        if !officialWarn.isEmpty { warnings.append(officialWarn) }
        return SourcePin.CheckResult(
            newer: newer,
            remoteVersion: remote,
            notes: notes,
            downloadURL: url,
            apkSha256: sha,
            remoteUpstreamCommit: remoteCommit,
            sourceMoved: !newer && !remoteCommit.isEmpty && remoteCommit != SourcePin.upstreamCommit,
            officialNewer: officialNewer,
            officialVersion: officialVersion,
            sourceDegraded: !warnings.isEmpty,
            sourceWarning: warnings.joined(separator: " ")
        )
    }

    private static func fetch(_ raw: String, session: URLSession) throws -> Data {
        guard SourcePin.TrustedNet.allow(raw), let url = URL(string: raw) else {
            throw URLError(.badURL)
        }
        var request = URLRequest(url: url, timeoutInterval: 8)
        request.setValue("VCPort-OfflineUpdate/\(localVersion)", forHTTPHeaderField: "User-Agent")
        request.setValue("close", forHTTPHeaderField: "Connection")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let semaphore = DispatchSemaphore(value: 0)
        var body: Data?
        var fetchError: Error?
        var status = 0
        session.dataTask(with: request) { data, response, error in
            body = data
            fetchError = error
            status = (response as? HTTPURLResponse)?.statusCode ?? 0
            semaphore.signal()
        }.resume()
        _ = semaphore.wait(timeout: .now() + SourcePin.TrustedNet.windowSeconds)
        if let fetchError { throw fetchError }
        if (300..<400).contains(status) { throw URLError(.httpTooManyRedirects) }
        guard status == 200, let body else { throw URLError(.cannotParseResponse) }
        if body.count > SourcePin.TrustedNet.maxBody { throw URLError(.dataLengthExceedsMaximum) }
        return body
    }

    private static func githubStatus(session: URLSession) -> String? {
        do {
            let body = try fetch(SourcePin.TrustedNet.githubStatus.absoluteString, session: session)
            guard let json = try JSONSerialization.jsonObject(with: body) as? [String: Any],
                  let status = json["status"] as? [String: Any] else {
                return "Could not confirm GitHub status. Stay cautious."
            }
            let indicator = status["indicator"] as? String ?? ""
            let description = status["description"] as? String ?? ""
            if indicator == "major" || indicator == "critical" {
                return "GitHub status is \(indicator) (\(description)). Treat this check as unverified."
            }
            return nil
        } catch {
            return nil
        }
    }

    private static func officialRelease(session: URLSession) -> (Bool, String, String) {
        do {
            let body = try fetch(SourcePin.upstreamReleases.absoluteString, session: session)
            guard let json = try JSONSerialization.jsonObject(with: body) as? [String: Any],
                  let tag = json["tag_name"] as? String else {
                return (false, "", "Official VeraCrypt GitHub was unreachable. Do not trust a missing pin.")
            }
            let ver = SourcePin.versionFromVeraCryptTag(tag)
            if ver.isEmpty {
                return (false, "", "Official VeraCrypt tag was empty or unexpected.")
            }
            return (SourcePin.compare(ver, SourcePin.upstreamVersion) > 0, ver, "")
        } catch {
            return (false, "", "Official VeraCrypt GitHub was unreachable. Do not trust a missing pin.")
        }
    }
}

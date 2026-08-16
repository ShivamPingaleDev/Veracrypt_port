import Foundation

enum UpdateChecker {
    static let localVersion = "0.2.2"
    static let manifestURL = URL(string: "https://raw.githubusercontent.com/ShivamPingaleDev/Veracrypt_port/master/ports/version.json")!

    struct Result {
        var newer: Bool
        var remoteVersion: String
        var notes: String
        var downloadURL: String
    }

    static func check() throws -> Result {
        var request = URLRequest(url: manifestURL, timeoutInterval: 20)
        request.setValue("VCPort-OfflineUpdate/0.2", forHTTPHeaderField: "User-Agent")
        request.setValue("close", forHTTPHeaderField: "Connection")
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let session = URLSession(configuration: .ephemeral)
        defer { session.finishTasksAndInvalidate() }
        let semaphore = DispatchSemaphore(value: 0)
        var body: Data?
        var fetchError: Error?
        session.dataTask(with: request) { data, _, error in
            body = data
            fetchError = error
            semaphore.signal()
        }.resume()
        _ = semaphore.wait(timeout: .now() + 25)
        if let fetchError { throw fetchError }
        guard let body,
              let json = try JSONSerialization.jsonObject(with: body) as? [String: Any],
              let remote = json["port_version"] as? String else {
            throw URLError(.cannotParseResponse)
        }
        let notes = json["notes"] as? String ?? ""
        let url = (json["ios_url"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? (json["download_url"] as? String ?? "")
        return Result(newer: compare(remote, localVersion) > 0, remoteVersion: remote, notes: notes, downloadURL: url)
    }

    private static func compare(_ a: String, _ b: String) -> Int {
        let pa = a.split { $0 == "." || $0 == "-" }.compactMap { Int($0) }
        let pb = b.split { $0 == "." || $0 == "-" }.compactMap { Int($0) }
        let n = max(pa.count, pb.count)
        for i in 0..<n {
            let x = i < pa.count ? pa[i] : 0
            let y = i < pb.count ? pb[i] : 0
            if x != y { return x < y ? -1 : 1 }
        }
        return 0
    }
}

import AVKit
import PDFKit
import SwiftUI
import UIKit

enum InAppPreviewKind {
    case image, text, pdf, audio, video, unsupported
}

struct InAppPreviewItem: Identifiable {
    let id = UUID()
    let url: URL
    let name: String
}

enum InAppPreview {
    static let maxBytes: UInt64 = 64 * 1024 * 1024
    static let textChars = 256 * 1024

    static func kind(of name: String) -> InAppPreviewKind {
        let ext = (name as NSString).pathExtension.lowercased()
        switch ext {
        case "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif":
            return .image
        case "txt", "md", "json", "xml", "csv", "log", "html", "htm",
             "c", "h", "cc", "cpp", "py", "kt", "swift", "sh", "ini", "cfg":
            return .text
        case "pdf":
            return .pdf
        case "mp3", "m4a", "aac", "wav", "ogg", "flac", "oga":
            return .audio
        case "mp4", "mkv", "webm", "3gp", "mov", "m4v":
            return .video
        default:
            return .unsupported
        }
    }

    static func directory() -> URL {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("preview", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func wipe() {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("preview", isDirectory: true)
        if let files = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) {
            for url in files {
                wipeFile(url)
            }
        }
        try? FileManager.default.removeItem(at: dir)
    }

    static func materialize(handle: OpaquePointer, volumePath: String, name: String) -> URL? {
        wipe()
        let dest = directory().appendingPathComponent(safeName(name))
        let rc = VcMobileBridge.exportFile(handle, name: volumePath, dest: dest.path)
        let size = (try? FileManager.default.attributesOfItem(atPath: dest.path)[.size] as? NSNumber)?.uint64Value ?? 0
        if rc != 0 || size == 0 || size > maxBytes {
            try? FileManager.default.removeItem(at: dest)
            return nil
        }
        return dest
    }

    static func readText(_ url: URL) -> String {
        let raw = (try? String(contentsOf: url, encoding: .utf8)) ?? ""
        if raw.count > textChars {
            return String(raw.prefix(textChars)) + "\n… truncated …"
        }
        return raw
    }

    static func hexHead(_ url: URL, n: Int = 256) -> String {
        guard let data = try? Data(contentsOf: url) else { return "" }
        return data.prefix(n).map { String(format: "%02x", $0) }.joined(separator: " ")
    }

    static func safeName(_ name: String) -> String {
        let base = (name as NSString).lastPathComponent
        let trimmed = base.replacingOccurrences(of: "/", with: "_")
        return trimmed.isEmpty ? "file" : trimmed
    }

    private static func wipeFile(_ url: URL) {
        let length = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
        if length > 0 && length <= 64 * 1024 * 1024, let handle = try? FileHandle(forWritingTo: url) {
            try? handle.write(contentsOf: Data(count: length))
            try? handle.close()
        }
        try? FileManager.default.removeItem(at: url)
    }
}

struct InAppPreviewSheet: View {
    let item: InAppPreviewItem
    let onClose: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Inside VC Port. Not VLC, Files, or another app.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    content
                }
                .padding()
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .navigationTitle(item.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { onClose() }
                        .portTag("in_app_preview_close")
                }
            }
        }
        .portTag("in_app_preview")
    }

    @ViewBuilder
    private var content: some View {
        switch InAppPreview.kind(of: item.name) {
        case .image:
            if let ui = UIImage(contentsOfFile: item.url.path) {
                Image(uiImage: ui)
                    .resizable()
                    .scaledToFit()
            } else {
                Text("Could not decode this image in-app.")
            }
        case .text:
            Text(InAppPreview.readText(item.url))
                .font(.system(.footnote, design: .monospaced))
                .textSelection(.enabled)
                .portTag("in_app_preview_text")
        case .pdf:
            PdfPreviewView(url: item.url)
                .frame(minHeight: 360)
        case .audio, .video:
            VideoPlayer(player: AVPlayer(url: item.url))
                .frame(minHeight: 180)
        case .unsupported:
            Text("This type is not decoded in-app (Office and unknown binaries stay in the volume). Copy to device if you need another tool — that leaves plaintext on the phone.")
                .font(.caption)
            Text(InAppPreview.hexHead(item.url))
                .font(.system(.footnote, design: .monospaced))
        }
    }
}

private struct PdfPreviewView: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true
        view.document = PDFDocument(url: url)
        return view
    }

    func updateUIView(_ uiView: PDFView, context: Context) {}
}

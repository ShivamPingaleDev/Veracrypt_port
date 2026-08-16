import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @State private var containerURL: URL?
    @State private var password = ""
    @State private var pim = "0"
    @State private var rememberBiometrics = false
    @State private var status = "Offline. Choose a VeraCrypt container, or share an encrypted file as-is."
    @State private var entries: [VaultEntry] = []
    @State private var importerPresented = false
    @State private var shareEncImporterPresented = false
    @State private var volumeHandle: OpaquePointer?
    @State private var incomingFile: URL?

    var body: some View {
        NavigationStack {
            Form {
                if let incoming = incomingFile {
                    Section("Received from another app") {
                        Text(incoming.lastPathComponent)
                        Button("Open as container") {
                            containerURL = incoming
                            status = "Selected \(incoming.lastPathComponent)"
                        }
                        Button("Share encrypted file") {
                            SystemShare.present(items: [incoming])
                        }
                    }
                }
                Section("Share encrypted file") {
                    Button("Share encrypted file…") { shareEncImporterPresented = true }
                    Text("Sends .hc / .tc / .vera as-is. No password, no decrypt. WhatsApp, Mail, Drive, AirDrop, and the rest of the share sheet.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if let url = containerURL {
                        Button("Share this encrypted file") {
                            SystemShare.present(items: [url])
                        }
                    }
                }
                Section("Container") {
                    Button("Choose container") { importerPresented = true }
                    Text(containerURL?.lastPathComponent ?? "No file selected")
                    SecureField("Password", text: $password)
                    TextField("PIM", text: $pim)
                        .keyboardType(.numberPad)
                    if BiometricStore.isAvailable {
                        Toggle("Remember with Face ID / Touch ID", isOn: $rememberBiometrics)
                        Button("Unlock with biometrics") {
                            guard let path = containerURL?.path else { return }
                            if let stored = BiometricStore.load(path: path) {
                                password = stored.0
                                pim = String(stored.1)
                                status = "Password loaded with biometrics."
                            } else {
                                status = "Biometric unlock failed."
                            }
                        }
                    }
                    Button("Open volume") { openVolume() }
                }
                Section("Updates") {
                    Button("Check for updates") {
                        status = "Checking for updates (one HTTPS request)..."
                        DispatchQueue.global(qos: .userInitiated).async {
                            do {
                                let result = try UpdateChecker.check()
                                DispatchQueue.main.async {
                                    status = result.newer
                                        ? "Update \(result.remoteVersion) available. \(result.notes) Offline again."
                                        : "Already up to date (\(UpdateChecker.localVersion)). Offline again."
                                }
                            } catch {
                                DispatchQueue.main.async {
                                    status = "Update check failed. Offline again."
                                }
                            }
                        }
                    }
                    Text("The app stays offline until you tap this.")
                }
                Section("Status") {
                    Text(status)
                }
                Section("Files") {
                    if entries.isEmpty {
                        Text("Open a volume to list files. Share decrypted copies from here, or share the encrypted volume from the section above.")
                    }
                    ForEach(entries) { entry in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(entry.name)
                                Text(entry.isDir ? "Folder" : byteCount(entry.size))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if !entry.isDir {
                                Button("Share decrypted") { shareVaultFile(entry) }
                            }
                        }
                    }
                }
            }
            .navigationTitle("VC Port")
            .fileImporter(isPresented: $importerPresented, allowedContentTypes: Self.containerTypes) { result in
                if case .success(let url) = result {
                    _ = url.startAccessingSecurityScopedResource()
                    containerURL = url
                    status = "Selected \(url.lastPathComponent)"
                }
            }
            .fileImporter(
                isPresented: $shareEncImporterPresented,
                allowedContentTypes: Self.containerTypes,
                allowsMultipleSelection: true
            ) { result in
                if case .success(let urls) = result {
                    urls.forEach { _ = $0.startAccessingSecurityScopedResource() }
                    if urls.isEmpty { return }
                    status = "Sharing \(urls.count) encrypted file(s) as-is."
                    SystemShare.present(items: urls)
                }
            }
            .onOpenURL { url in
                _ = url.startAccessingSecurityScopedResource()
                incomingFile = url
                if looksLikeContainer(url) {
                    containerURL = url
                    status = "Received container \(url.lastPathComponent)."
                } else {
                    status = "Received \(url.lastPathComponent). Open as a container or share it."
                }
            }
        }
    }

    private func openVolume() {
        guard let path = containerURL?.path else {
            status = "Select a container first."
            return
        }
        closeVolume()
        var error: Int32 = 0
        guard let handle = VcMobileBridge.open(
            path: path,
            password: password,
            pim: Int32(pim) ?? 0,
            error: &error
        ) else {
            status = "Open failed (code \(error))."
            return
        }
        volumeHandle = handle
        status = "Opened. Size \(VcMobileBridge.size(handle)) bytes. Tap Share on a file."
        entries = VcMobileBridge.listRoot(handle)
        if rememberBiometrics {
            _ = BiometricStore.store(path: path, password: password, pim: Int(pim) ?? 0)
        }
    }

    private func closeVolume() {
        if let handle = volumeHandle {
            VcMobileBridge.close(handle)
            volumeHandle = nil
        }
    }

    private func shareVaultFile(_ entry: VaultEntry) {
        guard let handle = volumeHandle else {
            status = "Open a volume first."
            return
        }
        status = "Preparing \(entry.name)…"
        DispatchQueue.global(qos: .userInitiated).async {
            let dest = FileManager.default.temporaryDirectory
                .appendingPathComponent(entry.name.replacingOccurrences(of: "/", with: "_"))
            let rc = VcMobileBridge.exportFile(handle, name: entry.name, dest: dest.path)
            DispatchQueue.main.async {
                if rc != 0 {
                    status = "Could not extract \(entry.name) (code \(rc)). FAT volumes only."
                    return
                }
                status = "Share \(entry.name) with WhatsApp, Mail, Drive, or any app."
                SystemShare.present(items: [dest])
            }
        }
    }

    private func looksLikeContainer(_ url: URL) -> Bool {
        let ext = url.pathExtension.lowercased()
        return ext == "hc" || ext == "tc" || ext == "vera"
    }

    private static var containerTypes: [UTType] {
        var types: [UTType] = [.data]
        for ext in ["hc", "tc", "vera"] {
            if let type = UTType(filenameExtension: ext) {
                types.insert(type, at: 0)
            }
        }
        return types
    }

    private func byteCount(_ size: UInt64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: Int64(size))
    }
}

#Preview {
    ContentView()
}

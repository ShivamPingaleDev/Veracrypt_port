import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @State private var containerURL: URL?
    @State private var password = ""
    @State private var pim = "0"
    @State private var useTextPassword = true
    @State private var useBiometric = false
    @State private var rememberBiometrics = false
    @State private var biometricKey: Data?
    @State private var keyfileURLs: [URL] = []
    @State private var keyfileImporterPresented = false
    @State private var importBioPresented = false
    @State private var status = "Offline. Choose a VeraCrypt container, or share an encrypted file as-is."
    @State private var entries: [VaultEntry] = []
    @State private var importerPresented = false
    @State private var shareEncImporterPresented = false
    @State private var volumeHandle: OpaquePointer?
    @State private var incomingFile: URL?
    @State private var wrapPassword = ""
    @State private var wrapImporterPresented = false
    @State private var unwrapImporterPresented = false

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
                        if incoming.pathExtension.lowercased() == "vcpw" {
                            Button("Decrypt wrap") { unwrapURL(incoming) }
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
                Section("Wrap a single file") {
                    Text("Encrypt one file with a password. Share the .vcpw wrap. The generator never writes history.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    SecureField("Wrap password (never stored)", text: $wrapPassword)
                        .privacySensitive()
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    Button("Generate strong password") {
                        if let generated = VcMobileBridge.generatePassword() {
                            wrapPassword = generated
                            status = "Generated a 24-character password in memory. It is not saved."
                        } else {
                            status = "Password generator failed."
                        }
                    }
                    Button("Copy once") {
                        guard !wrapPassword.isEmpty else { return }
                        SensitivePaste.copyOnce(wrapPassword)
                        status = "Copied once. Clipboard expires in 30 seconds and stays off iCloud clipboard."
                    }
                    Button("Forget password") {
                        wrapPassword = ""
                        SensitivePaste.forget()
                        status = "Password forgotten. Clipboard cleared."
                    }
                    Button("Encrypt file…") { wrapImporterPresented = true }
                    Button("Decrypt wrap…") { unwrapImporterPresented = true }
                }
                Section("Container") {
                    Button("Choose container") { importerPresented = true }
                    Text(containerURL?.lastPathComponent ?? "No file selected")
                }
                Section("Unlock factors") {
                    Text("Combine any of: biometric password, text password, keyfiles, and PIM. Same mix VeraCrypt uses on a computer.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Toggle("Text password", isOn: $useTextPassword)
                    if useTextPassword {
                        SecureField("Password", text: $password)
                    }
                    TextField("PIM (0 = default)", text: $pim)
                        .keyboardType(.numberPad)
                    ForEach(keyfileURLs, id: \.self) { url in
                        HStack {
                            Text(url.lastPathComponent)
                            Spacer()
                            Button("Remove") {
                                keyfileURLs.removeAll { $0 == url }
                            }
                        }
                    }
                    Button("Add keyfiles…") { keyfileImporterPresented = true }
                    if BiometricStore.isAvailable {
                        Toggle("Biometric as password", isOn: $useBiometric)
                        Text("Stored in the Keychain behind Face ID / Touch ID. Mixed as a VeraCrypt keyfile, so you can still add a text password, more keyfiles, and PIM.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if let biometricKey {
                            Text("Biometric password ready (\(biometricKey.count) bytes).")
                                .font(.caption)
                        } else if let path = containerURL?.path, BiometricStore.hasFactors(for: path) {
                            Text("A saved factor set exists. Unlock with biometrics to load it.")
                                .font(.caption)
                        } else {
                            Text("Create a random biometric password, or import a keyfile you already use.")
                                .font(.caption)
                        }
                        Button("Create biometric password") { createBiometricPassword() }
                        Button("Import keyfile as biometric password…") { importBioPresented = true }
                        Button("Export biometric keyfile") { exportBiometricKeyfile() }
                        Button("Unlock with biometrics") { loadBiometricFactors() }
                        Toggle("Remember this combination", isOn: $rememberBiometrics)
                    }
                    Button("Open volume") { openVolume() }
                }
                Section("Updates") {
                    if FossConfig.enableUpdateCheck {
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
                    } else {
                        Text("This build does not contact the network. Install updates from AltStore, SideStore, or a new source build.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Section("About / licenses") {
                    Text("Portions of this product are based in part on TrueCrypt, freely available at http://www.truecrypt.org/")
                    Link("http://www.truecrypt.org/", destination: URL(string: "http://www.truecrypt.org/")!)
                    Text("VC Port original code is Apache License 2.0. The volume core is VeraCrypt (Apache 2.0 / TrueCrypt License 3.0). You may not call this app VeraCrypt.")
                        .font(.caption)
                    Text("No ads, analytics, or crash reporters. Volume passwords stay on this device. Face ID / Touch ID never leave the Secure Enclave-backed Keychain.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
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
            .fileImporter(isPresented: $wrapImporterPresented, allowedContentTypes: [.data]) { result in
                if case .success(let url) = result {
                    _ = url.startAccessingSecurityScopedResource()
                    wrapURL(url)
                }
            }
            .fileImporter(isPresented: $unwrapImporterPresented, allowedContentTypes: [.data]) { result in
                if case .success(let url) = result {
                    _ = url.startAccessingSecurityScopedResource()
                    unwrapURL(url)
                }
            }
            .fileImporter(
                isPresented: $keyfileImporterPresented,
                allowedContentTypes: [.data],
                allowsMultipleSelection: true
            ) { result in
                if case .success(let urls) = result {
                    urls.forEach { _ = $0.startAccessingSecurityScopedResource() }
                    for url in urls where !keyfileURLs.contains(url) {
                        keyfileURLs.append(url)
                    }
                }
            }
            .fileImporter(isPresented: $importBioPresented, allowedContentTypes: [.data]) { result in
                if case .success(let url) = result {
                    _ = url.startAccessingSecurityScopedResource()
                    importBiometricKeyfile(url)
                }
            }
            .onOpenURL { url in
                _ = url.startAccessingSecurityScopedResource()
                incomingFile = url
                if looksLikeContainer(url) {
                    containerURL = url
                    status = "Received container \(url.lastPathComponent)."
                } else if url.pathExtension.lowercased() == "vcpw" {
                    status = "Received wrapped file \(url.lastPathComponent). Enter the wrap password and decrypt it."
                } else {
                    status = "Received \(url.lastPathComponent). Wrap it, share it, or open as a container."
                }
            }
        }
    }

    private func openVolume() {
        guard let path = containerURL?.path else {
            status = "Select a container first."
            return
        }
        let text = useTextPassword ? password : ""
        if text.isEmpty && !useBiometric && keyfileURLs.isEmpty {
            status = "Choose at least one factor: text password, biometric password, or a keyfile."
            return
        }
        if useBiometric && (biometricKey == nil || biometricKey?.isEmpty == true) {
            status = "Create or import a biometric password, or unlock with biometrics to load a saved one."
            return
        }
        closeVolume()
        var temps: [URL] = []
        var keyfilePaths = keyfileURLs.map(\.path)
        if useBiometric, let biometricKey {
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("vcbio-\(UUID().uuidString).key")
            try? biometricKey.write(to: url, options: .completeFileProtection)
            temps.append(url)
            keyfilePaths.insert(url.path, at: 0)
        }
        var error: Int32 = 0
        let handle = VcMobileBridge.open(
            path: path,
            password: text,
            pim: Int32(pim) ?? 0,
            keyfiles: keyfilePaths,
            error: &error
        )
        temps.forEach { try? FileManager.default.removeItem(at: $0) }
        guard let handle else {
            status = "Open failed (code \(error)). Wrong password, PIM, or keyfile mix."
            return
        }
        volumeHandle = handle
        status = "Opened. Size \(VcMobileBridge.size(handle)) bytes. Tap Share on a file."
        entries = VcMobileBridge.listRoot(handle)
        if rememberBiometrics {
            _ = BiometricStore.store(
                path: path,
                bundle: FactorBundle(
                    pim: Int(pim) ?? 0,
                    password: text,
                    biometricKey: useBiometric ? biometricKey : nil,
                    keyfilePaths: keyfileURLs.map(\.path)
                )
            )
        }
    }

    private func createBiometricPassword() {
        guard let path = containerURL?.path else {
            status = "Choose a container first."
            return
        }
        let secret = FactorCodec.randomBiometricKey()
        biometricKey = secret
        useBiometric = true
        let ok = BiometricStore.store(
            path: path,
            bundle: FactorBundle(
                pim: Int(pim) ?? 0,
                password: useTextPassword ? password : "",
                biometricKey: secret,
                keyfilePaths: keyfileURLs.map(\.path)
            )
        )
        status = ok
            ? "Created a 64-byte biometric password. Export it and add that file as a keyfile when you create the volume."
            : "Could not save the biometric password."
    }

    private func importBiometricKeyfile(_ url: URL) {
        guard let data = try? Data(contentsOf: url), !data.isEmpty, data.count <= 1_048_576 else {
            status = "Could not import keyfile (empty or larger than 1 MiB)."
            return
        }
        biometricKey = data
        useBiometric = true
        status = "Imported \(data.count) bytes as the biometric password (VeraCrypt keyfile)."
    }

    private func exportBiometricKeyfile() {
        guard let biometricKey else {
            status = "Create or import a biometric password first."
            return
        }
        let dest = FileManager.default.temporaryDirectory.appendingPathComponent("vcport-biometric.key")
        do {
            try biometricKey.write(to: dest, options: .completeFileProtection)
            status = "Share this keyfile into VeraCrypt on a computer (Add keyfile)."
            SystemShare.present(items: [dest])
        } catch {
            status = "Could not export the biometric keyfile."
        }
    }

    private func loadBiometricFactors() {
        guard let path = containerURL?.path else {
            status = "Choose a container first."
            return
        }
        guard let stored = BiometricStore.load(path: path) else {
            status = "Biometric unlock failed."
            return
        }
        password = stored.password
        pim = String(stored.pim)
        useTextPassword = !stored.password.isEmpty
        useBiometric = stored.hasBiometric
        biometricKey = stored.biometricKey
        keyfileURLs = stored.keyfilePaths.map { URL(fileURLWithPath: $0) }
        status = "Loaded factors with biometrics. Add or remove anything, then Open volume."
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

    private func wrapURL(_ url: URL) {
        guard wrapPassword.count >= 16 else {
            status = "Use Generate strong password, or type at least 16 characters. Nothing is saved."
            return
        }
        status = "Wrapping \(url.lastPathComponent)…"
        DispatchQueue.global(qos: .userInitiated).async {
            let dest = FileManager.default.temporaryDirectory
                .appendingPathComponent(url.lastPathComponent + ".vcpw")
            let rc = VcMobileBridge.wrapFile(
                src: url.path,
                dest: dest.path,
                password: wrapPassword,
                originalName: url.lastPathComponent
            )
            DispatchQueue.main.async {
                if rc != 0 {
                    status = "Wrap failed (code \(rc))."
                    return
                }
                status = "Wrapped \(url.lastPathComponent). Password was not saved."
                SystemShare.present(items: [dest])
            }
        }
    }

    private func unwrapURL(_ url: URL) {
        guard !wrapPassword.isEmpty else {
            status = "Enter the wrap password first. It is not stored."
            return
        }
        status = "Unwrapping \(url.lastPathComponent)…"
        DispatchQueue.global(qos: .userInitiated).async {
            let dir = FileManager.default.temporaryDirectory.appendingPathComponent("unwrapped", isDirectory: true)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let out = VcMobileBridge.unwrapFile(src: url.path, destDir: dir.path, password: wrapPassword)
            DispatchQueue.main.async {
                guard let out else {
                    status = "Unwrap failed. Wrong password or not a VC Port wrap."
                    return
                }
                let file = URL(fileURLWithPath: out)
                status = "Unwrapped \(file.lastPathComponent). Password was not saved."
                SystemShare.present(items: [file])
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

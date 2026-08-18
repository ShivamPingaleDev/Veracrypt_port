import SwiftUI
import UniformTypeIdentifiers
import CryptoKit

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var containerURL: URL?
    @State private var password = ""
    @State private var pim = "0"
    @State private var useTextPassword = true
    @State private var keyfileURLs: [URL] = []
    @State private var keyfileGenName = "keyfile.bin"
    @State private var keyfileGenCount = "1"
    @State private var keyfileImporterPresented = false
    @State private var status = "Offline. Choose a VeraCrypt container, or share an encrypted file as-is."
    @State private var entries: [VaultEntry] = []
    @State private var importerPresented = false
    @State private var shareEncImporterPresented = false
    @State private var volumeHandle: OpaquePointer?
    @State private var dirPath = ""
    @State private var listTruncated = false
    @State private var incomingFile: URL?
    @State private var wrapPassword = ""
    @State private var wrapImporterPresented = false
    @State private var unwrapImporterPresented = false
    @State private var lastPlain: [URL] = []
    @State private var selectedNames: Set<String> = []
    @State private var createCipher = VcMobileBridge.defaultCipher
    @State private var createKdf = VcMobileBridge.defaultKdf
    @State private var createSizeAmount = "16"
    @State private var createSizeUnit = SizeUnit.mib
    @State private var createFilesystem = "FAT"
    @State private var createHiddenSizeAmount = "4"
    @State private var createHiddenSizeUnit = SizeUnit.mib
    @State private var createPassword = ""
    @State private var createPim = "0"
    @State private var createHidden = false
    @State private var createHiddenPassword = ""
    @State private var createHiddenPim = "0"
    @State private var createFileName = "volume.hc"
    @State private var entropyPercent = 0
    @State private var newPassword = ""
    @State private var newPim = "0"
    @State private var headerKdf = "(keep current)"
    @State private var useBackupHeader = false
    @State private var readOnlyOpen = false
    @State private var trueCryptMode = false
    @State private var protectHidden = false
    @State private var hiddenProtectPassword = ""
    @State private var hiddenProtectPim = "0"
    @State private var newFolderPresented = false
    @State private var renamePresented = false
    @State private var namePromptValue = ""
    @State private var restoreHeaderPresented = false
    @State private var copyFromDevicePresented = false
    @State private var moveFromDevice = false
    @State private var busy = false
    @State private var workTitle = ""
    @State private var workPercent = -1
    @State private var entropyMarks: [CGPoint] = []
    @State private var wrapHold = ""
    @State private var holdLock = false
    @State private var basketURLs: [URL] = []
    @State private var basketHashes: [String: String] = [:]
    @State private var basketImporterPresented = false
    @State private var hiddenKeyfileURLs: [URL] = []
    @State private var hiddenKeyfileImporterPresented = false

    @State private var selectedTab = 0

    private var holdingForPicker: Bool {
        holdLock || wrapImporterPresented || unwrapImporterPresented || importerPresented
            || shareEncImporterPresented || keyfileImporterPresented
            || restoreHeaderPresented || copyFromDevicePresented || basketImporterPresented
            || hiddenKeyfileImporterPresented
    }

    var body: some View {
        ZStack {
        NavigationStack {
            Group {
                if volumeHandle != nil {
                    mountedVolumeForm
                } else {
                    TabView(selection: $selectedTab) {
                        volumeTab
                            .tag(0)
                            .tabItem { Label("Volume", systemImage: "lock") }
                        createTab
                            .tag(1)
                            .tabItem { Label("Create", systemImage: "plus.rectangle.on.folder") }
                        toolsTab
                            .tag(2)
                            .tabItem { Label("Tools", systemImage: "wrench.and.screwdriver") }
                    }
                }
            }
            .disabled(busy)
            .navigationTitle("VC Port")
            .tint(Color(red: 10 / 255, green: 108 / 255, blue: 206 / 255))
            .toolbarBackground(Color(red: 10 / 255, green: 108 / 255, blue: 206 / 255), for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                if volumeHandle != nil {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("Dismount") { lockSession() }
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Panic wipe", role: .destructive) { panicWipe() }
                }
            }
            .fileImporter(isPresented: $importerPresented, allowedContentTypes: [.item, .data]) { result in
                switch result {
                case .success(let url):
                    _ = url.startAccessingSecurityScopedResource()
                    incomingFile = nil
                    containerURL = url
                    holdLock = false
                    status = "Selected \(url.lastPathComponent)"
                case .failure:
                    holdLock = false
                }
            }
            .fileImporter(
                isPresented: $shareEncImporterPresented,
                allowedContentTypes: [.item, .data],
                allowsMultipleSelection: true
            ) { result in
                if case .success(let urls) = result {
                    urls.forEach { _ = $0.startAccessingSecurityScopedResource() }
                    if urls.isEmpty { return }
                    status = "Sharing \(urls.count) encrypted file(s) as-is."
                    SystemShare.present(items: urls)
                }
            }
            .fileImporter(isPresented: $wrapImporterPresented, allowedContentTypes: [.item, .data]) { result in
                switch result {
                case .success(let url):
                    _ = url.startAccessingSecurityScopedResource()
                    wrapURL(url, password: wrapHold.count >= 16 ? wrapHold : wrapPassword)
                case .failure:
                    holdLock = false
                    wrapHold = ""
                }
            }
            .fileImporter(isPresented: $unwrapImporterPresented, allowedContentTypes: [.item, .data]) { result in
                switch result {
                case .success(let url):
                    _ = url.startAccessingSecurityScopedResource()
                    unwrapURL(url, password: wrapHold.isEmpty ? wrapPassword : wrapHold)
                case .failure:
                    holdLock = false
                    wrapHold = ""
                }
            }
            .fileImporter(
                isPresented: $keyfileImporterPresented,
                allowedContentTypes: [.data],
                allowsMultipleSelection: true
            ) { result in
                holdLock = false
                if case .success(let urls) = result {
                    urls.forEach { _ = $0.startAccessingSecurityScopedResource() }
                    for url in urls where !keyfileURLs.contains(url) {
                        keyfileURLs.append(url)
                    }
                }
            }
            .fileImporter(isPresented: $restoreHeaderPresented, allowedContentTypes: [.data]) { result in
                if case .success(let url) = result {
                    _ = url.startAccessingSecurityScopedResource()
                    restoreVolumeHeader(url)
                }
            }
            .fileImporter(
                isPresented: $basketImporterPresented,
                allowedContentTypes: [.item, .data],
                allowsMultipleSelection: true
            ) { result in
                holdLock = false
                if case .success(let urls) = result {
                    urls.forEach { _ = $0.startAccessingSecurityScopedResource() }
                    for url in urls where !basketURLs.contains(url) {
                        basketURLs.append(url)
                    }
                    status = "Basket: \(basketSummary(basketURLs)). SHA-256 runs in this session only."
                    DispatchQueue.global(qos: .utility).async {
                        var extra: [String: String] = [:]
                        for url in urls {
                            if let hex = sha256File(url) {
                                extra[url.path] = hex
                            }
                        }
                        DispatchQueue.main.async {
                            basketHashes.merge(extra) { _, new in new }
                        }
                    }
                }
            }
            .fileImporter(
                isPresented: $hiddenKeyfileImporterPresented,
                allowedContentTypes: [.item, .data],
                allowsMultipleSelection: true
            ) { result in
                holdLock = false
                if case .success(let urls) = result {
                    urls.forEach { _ = $0.startAccessingSecurityScopedResource() }
                    for url in urls where !hiddenKeyfileURLs.contains(url) {
                        hiddenKeyfileURLs.append(url)
                    }
                    status = "Nested keyfile(s) added. First 1 MiB, same as VeraCrypt."
                }
            }
            .fileImporter(isPresented: $copyFromDevicePresented, allowedContentTypes: [.item, .data]) { result in
                if case .success(let url) = result {
                    _ = url.startAccessingSecurityScopedResource()
                    importFromDevice(url, move: moveFromDevice)
                }
            }
            .alert("New folder", isPresented: $newFolderPresented) {
                TextField("Name", text: $namePromptValue)
                Button("Create") { mkdirInVolume(namePromptValue) }
                Button("Cancel", role: .cancel) {}
            }
            .alert("Rename", isPresented: $renamePresented) {
                TextField("Name", text: $namePromptValue)
                Button("Rename") { renameSelected(namePromptValue) }
                Button("Cancel", role: .cancel) {}
            }
            .onChange(of: scenePhase) { phase in
                if phase == .background && !holdingForPicker && !busy {
                    dismountOnLeave()
                }
            }
            .onChange(of: basketURLs) { _ in syncCreateSizeFromBasket() }
            .onChange(of: createHidden) { _ in syncCreateSizeFromBasket() }
            .onChange(of: createHiddenSizeAmount) { _ in syncCreateSizeFromBasket() }
            .onChange(of: createHiddenSizeUnit) { _ in syncCreateSizeFromBasket() }
            .onOpenURL { url in
                _ = url.startAccessingSecurityScopedResource()
                incomingFile = url
                if url.pathExtension.lowercased() == "vcpw" {
                    status = "Received wrapped file \(url.lastPathComponent). Enter the wrap password and decrypt it."
                } else {
                    containerURL = url
                    status = "Received \(url.lastPathComponent). Any extension can be a volume. Open with the correct password, PIM, and keyfiles."
                }
            }
        }
        .animation(.easeOut(duration: 0.15), value: busy)
        if busy {
            WorkOverlay(title: workTitle.isEmpty ? status : workTitle, percent: workPercent)
                .transition(.opacity)
        }
        }
        .task(id: busy) {
            guard busy else { return }
            while !Task.isCancelled {
                workPercent = VcMobileBridge.progressPercent()
                let phase = VcMobileBridge.progressPhase()
                if !phase.isEmpty {
                    workTitle = phase
                }
                try? await Task.sleep(nanoseconds: 100_000_000)
            }
        }
    }

    @ViewBuilder
    private var statusSection: some View {
        Section("Status") {
            HStack(alignment: .top, spacing: 10) {
                Capsule()
                    .fill(statusTone)
                    .frame(width: 4, height: 36)
                Text(status)
            }
        }
    }

    @ViewBuilder
    private var incomingSection: some View {
        if let incoming = incomingFile {
            Section("Received from another app") {
                Text(incoming.lastPathComponent)
                Button("Open as container") {
                    containerURL = incoming
                    selectedTab = 0
                    status = "Selected \(incoming.lastPathComponent)"
                }
                Button("Share encrypted file") {
                    SystemShare.present(items: [incoming])
                }
                if incoming.pathExtension.lowercased() == "vcpw" {
                    SecureField("Wrap password (never stored)", text: $wrapPassword)
                        .neverSaveHistory()
                    Button("Decrypt wrap") { unwrapURL(incoming) }
                }
            }
        }
    }

    @ViewBuilder
    private var inFrontSection: some View {
        Section("In front of you") {
            Text(inFrontLabel)
                .font(.caption)
            Button("Share encrypted") { shareInFrontEncrypted() }
            Button("Share decrypted") { shareInFrontDecrypted() }
                .disabled(!canShareDecrypted)
        }
    }

    @ViewBuilder
    private var keyfileRows: some View {
        Text("Pick several files. Any extension. VeraCrypt mixes the first 1 MiB of each.")
            .font(.caption)
            .foregroundStyle(.secondary)
        ForEach(keyfileURLs, id: \.self) { url in
            HStack {
                Text(url.lastPathComponent)
                Spacer()
                Button("Remove") {
                    keyfileURLs.removeAll { $0 == url }
                }
            }
        }
        Button("Add keyfiles…") {
            holdLock = true
            keyfileImporterPresented = true
        }
        TextField("Keyfile name (any extension)", text: $keyfileGenName)
        TextField("How many (1–8)", text: $keyfileGenCount)
            .keyboardType(.numberPad)
        Button("Generate keyfile and add") { generateKeyfile(nested: false) }
    }

    @ViewBuilder
    private var mountedVolumeForm: some View {
        Form {
            Section("Mounted in this app") {
                if entries.isEmpty {
                    Text("This folder is empty. Tap a folder after Copy from device. FAT and exFAT folders are browsable.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 4) {
                        if !dirPath.isEmpty {
                            Button("Up") {
                                dirPath = parentDir(dirPath)
                                reloadDir()
                            }
                        }
                        Button("/") {
                            dirPath = ""
                            reloadDir()
                        }
                        .disabled(dirPath.isEmpty)
                        ForEach(Array(dirPath.split(separator: "/").map(String.init).enumerated()), id: \.offset) { index, part in
                            HStack(spacing: 4) {
                                Text("›")
                                    .foregroundStyle(.secondary)
                                Button(part) {
                                    dirPath = dirPath.split(separator: "/").map(String.init).prefix(index + 1).joined(separator: "/")
                                    reloadDir()
                                }
                                .disabled(index == dirPath.split(separator: "/").count - 1)
                            }
                        }
                    }
                }
                HStack {
                    Button("Copy from device") {
                        moveFromDevice = false
                        copyFromDevicePresented = true
                    }
                    Button("Move from device") {
                        moveFromDevice = true
                        copyFromDevicePresented = true
                    }
                }
                HStack {
                    Button("Copy to device") { copySelectedToDevice(move: false) }
                    Button("Move to device") { copySelectedToDevice(move: true) }
                }
                HStack {
                    Button("New folder") {
                        namePromptValue = ""
                        newFolderPresented = true
                    }
                    Button("Rename") {
                        guard let name = selectedNames.first else {
                            status = "Tap a file or folder, then Rename."
                            return
                        }
                        namePromptValue = name
                        renamePresented = true
                    }
                    Button("Delete") { deleteSelected() }
                    Button("Properties") { showEntryProperties() }
                }
                Button("Wipe free space") { wipeFreeSpace() }
                ForEach(entries) { entry in
                    HStack {
                        ZStack {
                            Circle()
                                .fill(selectedNames.contains(entry.name) ? Color.accentColor : Color.secondary.opacity(0.18))
                                .frame(width: 28, height: 28)
                            if selectedNames.contains(entry.name) {
                                Image(systemName: "checkmark")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(.white)
                            } else {
                                Image(systemName: entry.isDir ? "folder" : "doc")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        VStack(alignment: .leading) {
                            Text(entry.name)
                            Text(entry.isDir ? "Folder — tap Open" : byteCount(entry.size))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        if entry.isDir {
                            Button("Open") {
                                dirPath = joinDir(dirPath, entry.name)
                                selectedNames = []
                                reloadDir()
                            }
                        } else {
                            Button(selectedNames.contains(entry.name) ? "Selected" : "Select") {
                                if selectedNames.contains(entry.name) {
                                    selectedNames.remove(entry.name)
                                } else {
                                    selectedNames.insert(entry.name)
                                }
                            }
                            Button("Share decrypted") { shareVaultFile(entry) }
                        }
                    }
                }
                if listTruncated {
                    Button("Load more") { reloadDir(append: true) }
                }
            }
            inFrontSection
            statusSection
        }
    }

    @ViewBuilder
    private var volumeTab: some View {
        Form {
            statusSection
            incomingSection
            Section {
                Text("Stay offline by default. A compelled password still wins — prefer a long password and a keyfile. This is not unbreakable.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if FossConfig.enableUpdateCheck {
                    Button("Check for updates") { checkForUpdates() }
                }
                Button("Choose container") {
                    holdLock = true
                    importerPresented = true
                }
                Text("USB/OTG: a file on the stick, not the whole disk.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if let url = containerURL {
                    Text("Selected: \(url.lastPathComponent)")
                    if isTemporaryContainer(url) {
                        Text("On this phone until you save it to Files.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else {
                        Text(url.path)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                } else {
                    Text("No file selected")
                }
                Button("Share encrypted file…") { shareEncImporterPresented = true }
                if let url = containerURL {
                    Button("Share this encrypted file") {
                        SystemShare.present(items: [url])
                    }
                }
            }
            inFrontSection
            Section("Volume password") {
                Toggle("Password", isOn: $useTextPassword)
                if useTextPassword {
                    SecureField("Password", text: $password)
                        .neverSaveHistory()
                }
                TextField("PIM (0 = default)", text: $pim)
                    .keyboardType(.numberPad)
                keyfileRows
                Text("Mount options")
                    .font(.headline)
                Toggle("Use backup header", isOn: $useBackupHeader)
                Toggle("Read-only", isOn: $readOnlyOpen)
                Toggle("TrueCrypt Mode", isOn: $trueCryptMode)
                Toggle("Protect hidden volume against damage caused by writing to outer volume", isOn: $protectHidden)
                if protectHidden {
                    SecureField("Password to hidden volume", text: $hiddenProtectPassword)
                        .neverSaveHistory()
                    TextField("Hidden volume PIM (0 = default)", text: $hiddenProtectPim)
                        .keyboardType(.numberPad)
                }
                Button("Open volume") { openVolume() }
            }
        }
    }

    @ViewBuilder
    private var createTab: some View {
        Form {
            statusSection
            Section("File basket") {
                Text("Copied into the new volume. Originals stay. SHA-256 is session-only; BASKET.sha256 is written inside.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if basketURLs.isEmpty {
                    Text("Basket is empty.")
                        .font(.caption)
                } else {
                    ForEach(basketURLs, id: \.path) { url in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(url.lastPathComponent)
                                Text(shortHash(basketHashes[url.path]))
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button("Remove") {
                                basketURLs.removeAll { $0 == url }
                                basketHashes.removeValue(forKey: url.path)
                            }
                        }
                    }
                    Text(basketSummary(basketURLs))
                        .font(.caption)
                }
                Button("Add files to basket") {
                    holdLock = true
                    basketImporterPresented = true
                }
                Button("Empty basket") {
                    basketURLs.removeAll()
                    basketHashes.removeAll()
                    status = "Basket emptied. Hashes wiped from RAM."
                }
                .disabled(basketURLs.isEmpty)
            }
            Section("Encryption Options") {
                Text("Opening ignores the extension. Opening uses whichever password you type — there is no open-time hidden checkbox.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Picker("Encryption Algorithm", selection: $createCipher) {
                    ForEach(VcMobileBridge.ciphers, id: \.self) { Text($0).tag($0) }
                }
                Picker("KDF", selection: $createKdf) {
                    ForEach(VcMobileBridge.kdfs, id: \.self) { Text($0).tag($0) }
                }
                Picker("Inside the volume", selection: $createFilesystem) {
                    Text("FAT").tag("FAT")
                    Text("exFAT").tag("exFAT")
                }
                Text("exFAT if a file is over 4 GiB.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("File name (any extension)", text: $createFileName)
                Text("The name is only a disguise — volume.hc, photo.jpg, image.png, model.safetensors, adapter.lora.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                HStack(alignment: .center, spacing: 8) {
                    TextField("Size", text: $createSizeAmount)
                        .keyboardType(.numberPad)
                    Picker("Unit", selection: $createSizeUnit) {
                        ForEach(SizeUnit.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .labelsHidden()
                    .pickerStyle(.menu)
                    .frame(width: 72)
                }
                Text("2 MiB–64 GiB.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                SecureField("Volume password (never stored)", text: $createPassword)
                    .neverSaveHistory()
                Text(PasswordEntropy.label(createPassword))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("PIM (0 = default)", text: $createPim)
                    .keyboardType(.numberPad)
                Button("Generate strong password") {
                    if let generated = VcMobileBridge.generatePassword() {
                        createPassword = generated
                        status = PasswordEntropy.label(generated) + " Generated a 64-character password in memory. Copy once if you need it elsewhere. It is not saved."
                    }
                }
                Button("Copy once") {
                    guard !createPassword.isEmpty else { return }
                    SensitivePaste.copyOnce(createPassword)
                    status = "Copied once. Clipboard expires in 30 seconds and stays off iCloud clipboard."
                }
                Button("Forget password") {
                    createPassword = ""
                    SensitivePaste.forget()
                    status = "Password forgotten. Clipboard cleared."
                }
                keyfileRows
                VStack(alignment: .leading, spacing: 8) {
                    Text("Randomness (move your finger)")
                    ProgressView(value: Double(entropyPercent), total: 100)
                        .tint(Color(red: 10 / 255, green: 108 / 255, blue: 206 / 255))
                        .animation(.easeInOut(duration: 0.12), value: entropyPercent)
                    Text("\(entropyPercent)%")
                        .font(.caption)
                        .monospacedDigit()
                    ZStack {
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(Color(white: 0.97))
                            .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).stroke(Color.secondary.opacity(0.4)))
                        Path { path in
                            guard let first = entropyMarks.first else { return }
                            path.move(to: first)
                            for point in entropyMarks.dropFirst() {
                                path.addLine(to: point)
                            }
                        }
                        .stroke(
                            Color(red: 10 / 255, green: 108 / 255, blue: 206 / 255).opacity(0.72),
                            style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round)
                        )
                        if let tip = entropyMarks.last {
                            Circle()
                                .fill(Color(red: 10 / 255, green: 108 / 255, blue: 206 / 255))
                                .frame(width: 12, height: 12)
                                .position(tip)
                        }
                        Text(entropyPercent >= 100 ? "Entropy ready" : "Move your finger randomly here")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, minHeight: 240)
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { collectCreateEntropy($0) }
                    )
                }
                Toggle("Nested volume (VeraCrypt hidden volume)", isOn: $createHidden)
                if createHidden {
                    Text("Same cipher and KDF. Different password. Do not fill the outer volume.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    SecureField("Nested volume password", text: $createHiddenPassword)
                        .neverSaveHistory()
                    Text(PasswordEntropy.label(createHiddenPassword))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Button("Generate nested password") {
                        if let generated = VcMobileBridge.generatePassword() {
                            createHiddenPassword = generated
                            status = PasswordEntropy.label(generated) + " Nested password generated in memory. Copy once if you need it elsewhere. It is not saved."
                        }
                    }
                    HStack(spacing: 8) {
                        Button("Copy nested once") {
                            guard !createHiddenPassword.isEmpty else { return }
                            SensitivePaste.copyOnce(createHiddenPassword)
                            status = "Copied nested password once. Clipboard expires in 30 seconds and stays off iCloud clipboard."
                        }
                        Button("Forget nested") {
                            createHiddenPassword = ""
                            SensitivePaste.forget()
                            status = "Nested password forgotten. Clipboard cleared."
                        }
                    }
                    TextField("Nested PIM (0 = default)", text: $createHiddenPim)
                        .keyboardType(.numberPad)
                    HStack(alignment: .center, spacing: 8) {
                        TextField("Nested size", text: $createHiddenSizeAmount)
                            .keyboardType(.numberPad)
                        Picker("Unit", selection: $createHiddenSizeUnit) {
                            ForEach(SizeUnit.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                        }
                        .labelsHidden()
                        .pickerStyle(.menu)
                        .frame(width: 72)
                    }
                    if hiddenKeyfileURLs.isEmpty {
                        Text("No nested keyfiles.")
                            .font(.caption)
                    } else {
                        ForEach(hiddenKeyfileURLs, id: \.path) { url in
                            HStack {
                                Text(url.lastPathComponent)
                                Spacer()
                                Button("Remove") {
                                    hiddenKeyfileURLs.removeAll { $0 == url }
                                }
                            }
                        }
                    }
                    Button("Add nested keyfiles…") {
                        holdLock = true
                        hiddenKeyfileImporterPresented = true
                    }
                    Button("Generate nested keyfile and add") { generateKeyfile(nested: true) }
                }
                Button("Create volume") { createVolume() }
                    .disabled(entropyPercent < 100)
            }
        }
    }

    @ViewBuilder
    private var toolsTab: some View {
        Form {
            statusSection
            Section("Tools") {
                SecureField("New password (empty = keep current)", text: $newPassword)
                    .neverSaveHistory()
                TextField("New PIM (0 = VeraCrypt default)", text: $newPim)
                    .keyboardType(.numberPad)
                Button("Change volume password") { changeVolumePassword() }
                Picker("Header KDF", selection: $headerKdf) {
                    Text("(keep current)").tag("(keep current)")
                    ForEach(VcMobileBridge.kdfs, id: \.self) { Text($0).tag($0) }
                }
                Button("Set header key derivation algorithm") { setHeaderKdf() }
                Button("Add/Remove keyfiles to/from volume") { applyKeyfilesToVolume() }
                Button("Remove all keyfiles from volume") { removeAllKeyfiles() }
                Button("Backup volume header") { backupVolumeHeader() }
                Button("Restore volume header") { restoreHeaderPresented = true }
                Button("Restore from embedded backup header") { restoreEmbeddedHeader() }
                Button("Volume properties") { showVolumeProperties() }
                TextField("Keyfile name (any extension)", text: $keyfileGenName)
                TextField("How many (1–8)", text: $keyfileGenCount)
                    .keyboardType(.numberPad)
                Button("Keyfile generator") { generateKeyfile(nested: false) }
                Button("Benchmark") { runBenchmark() }
                Button("Test vectors") { runTestVectors() }
                Button("Wipe cached passwords") {
                    lockSession()
                    status = "Wipe cached passwords complete. Volume closed."
                }
            }
            Section("Leftover wrap") {
                Text("Decrypt a leftover .vcpw.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                SecureField("Wrap password (never stored)", text: $wrapPassword)
                    .neverSaveHistory()
                Button("Decrypt wrap…") {
                    if wrapPassword.isEmpty {
                        status = "Enter the wrap password first. It is not stored."
                    } else {
                        wrapHold = wrapPassword
                        holdLock = true
                        unwrapImporterPresented = true
                    }
                }
            }
            Section("Not on this phone") {
                Text("Root / jailbreak: this app will not ask for superuser.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("Device encryption: this app encrypts VeraCrypt container files (any file name). It cannot encrypt the iPhone operating system. iOS already encrypts the device with your passcode.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("Security tokens: PKCS#11 smart cards are not available on this phone. Export a keyfile from the token on a computer, then Add keyfiles here.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("Desktop leftovers: this is the full file-container port. These stay on a computer: mount as a drive (no FUSE), Select Device / Auto-Mount All Devices, system encryption, rescue disk, traveler disk, volume expander, Quick Format, dynamic sparse containers, favorite volumes, driver password cache, VeraCrypt background task, in-place partition encrypt/decrypt, hotkeys, language files, NTFS/ext, PKCS#11 tokens, and a File Provider browse of an unlocked volume. Phone volumes are FAT or exFAT file containers. Online help is not fetched while Stay offline. English UI only.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Section("About / licenses") {
                Text("“We must defend our own privacy if we expect to have any.” — Eric Hughes, A Cypherpunk’s Manifesto (1993)")
                    .font(.caption)
                    .italic()
                    .foregroundStyle(.secondary)
                Text("Portions of this product are based in part on TrueCrypt, freely available at http://www.truecrypt.org/")
                Link("http://www.truecrypt.org/", destination: URL(string: "http://www.truecrypt.org/")!)
                Text("VC Port original code is Apache License 2.0. The volume core is VeraCrypt (Apache 2.0 / TrueCrypt License 3.0). You may not call this app VeraCrypt. There is no key escrow and no intelligence or police backdoor. A nation-state implant still wins. Not unbreakable.")
                    .font(.caption)
                Text(SourcePin.describeBuild())
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("Tap Check for updates only if this build allows it; that reads ports/version.json from the public source. The app does not install itself. AltStore or a rebuild from that source is how a new IPA arrives.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("Contact: Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("Footnote: A programming noob still doing a five-year IT engineering degree (graduate summer 2027). Just trying to make something better that he likes to use, without much knowledge. Open to suggestions and advice.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("No ads, analytics, or crash reporters. Volume passwords stay on this device.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func checkForUpdates() {
        beginWork("Checking for updates (≤20s HTTPS window)...")
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let result = try UpdateChecker.check()
                DispatchQueue.main.async {
                    endWork()
                    status = Self.formatUpdateStatus(result)
                }
            } catch {
                DispatchQueue.main.async {
                    endWork()
                    status = "Update check failed. Offline again."
                }
            }
        }
    }


    private var inFrontLabel: String {
        let selected = entries.filter { selectedNames.contains($0.name) && !$0.isDir }
        if !selected.isEmpty {
            return selected.map(\.name).joined(separator: ", ") + " — decrypted from the open volume"
        }
        if let live = lastPlain.first, FileManager.default.fileExists(atPath: live.path) {
            return live.lastPathComponent + " — decrypted copy"
        }
        if let incoming = incomingFile, incoming.pathExtension.lowercased() == "vcpw" {
            return "\(incoming.lastPathComponent) — encrypted wrap in front"
        }
        if let containerURL {
            return "\(containerURL.lastPathComponent) — encrypted file in front"
        }
        if let incoming = incomingFile {
            return "\(incoming.lastPathComponent) — encrypted file in front"
        }
        return "Nothing in front. Select a container or file, then share from here."
    }

    private var canShareDecrypted: Bool {
        entries.contains { selectedNames.contains($0.name) && !$0.isDir }
            || lastPlain.contains { FileManager.default.fileExists(atPath: $0.path) }
            || (incomingFile?.pathExtension.lowercased() == "vcpw" && !wrapPassword.isEmpty)
    }

    private var statusTone: Color {
        let lower = status.lowercased()
        if ["fail", "could not", "wrong", "empty"].contains(where: { lower.contains($0) }) {
            return .red
        }
        if ["opened", "copied", "created", "moved", "wiped", "complete", "saved", "unwrapped", "wrapped", "renamed", "deleted"].contains(where: { lower.contains($0) }) {
            return Color(red: 0.15, green: 0.48, blue: 0.25)
        }
        return Color(red: 10 / 255, green: 108 / 255, blue: 206 / 255)
    }

    private func collectCreateEntropy(_ value: DragGesture.Value) {
        var bytes = [UInt8](repeating: 0, count: 24)
        func put(_ offset: Int, _ v: UInt32) {
            bytes[offset] = UInt8(v & 0xff)
            bytes[offset + 1] = UInt8((v >> 8) & 0xff)
            bytes[offset + 2] = UInt8((v >> 16) & 0xff)
            bytes[offset + 3] = UInt8((v >> 24) & 0xff)
        }
        put(0, Float(value.location.x).bitPattern)
        put(4, Float(value.location.y).bitPattern)
        let t = DispatchTime.now().uptimeNanoseconds
        put(8, UInt32(truncatingIfNeeded: t))
        put(12, UInt32(truncatingIfNeeded: t >> 32))
        put(16, Float(value.translation.width).bitPattern)
        put(20, Float(value.translation.height).bitPattern)
        entropyMarks.append(value.location)
        if entropyMarks.count > 96 {
            entropyMarks.removeFirst(entropyMarks.count - 96)
        }
        VcMobileBridge.addEntropy(Data(bytes))
        entropyPercent = Int(VcMobileBridge.entropyPercent())
    }

    private func beginWork(_ title: String) {
        VcMobileBridge.resetProgress()
        workTitle = title
        workPercent = -1
        busy = true
        status = title
    }

    private func endWork() {
        busy = false
        workTitle = ""
        workPercent = -1
        VcMobileBridge.resetProgress()
    }

    private func shareInFrontEncrypted() {
        if let incoming = incomingFile {
            status = "Sharing \(incoming.lastPathComponent) encrypted as-is."
            SystemShare.present(items: [incoming])
            return
        }
        if let containerURL {
            status = "Sharing encrypted file as-is."
            SystemShare.present(items: [containerURL])
            return
        }
        shareEncImporterPresented = true
    }

    private func shareInFrontDecrypted() {
        let selected = entries.filter { selectedNames.contains($0.name) && !$0.isDir }
        if volumeHandle != nil, let first = selected.first {
            shareVaultFile(first)
            return
        }
        if let live = lastPlain.first, FileManager.default.fileExists(atPath: live.path) {
            status = "Sharing decrypted \(live.lastPathComponent)."
            SystemShare.present(items: lastPlain.filter { FileManager.default.fileExists(atPath: $0.path) })
            return
        }
        if let incoming = incomingFile, incoming.pathExtension.lowercased() == "vcpw" {
            unwrapURL(incoming)
            return
        }
        status = "Tap files in an open volume, or decrypt a wrap, then Share decrypted."
    }

    private func createVolume() {
        if createPassword.isEmpty && keyfileURLs.isEmpty {
            status = "Type a volume password, or add a keyfile."
            return
        }
        if !createPassword.isEmpty && createPassword.count < 16 && keyfileURLs.isEmpty {
            status = "Use Generate strong password, or type at least 16 characters. Nothing is saved."
            return
        }
        if entropyPercent < 100 {
            status = "Move your finger in the blank area until the randomness bar is full."
            return
        }
        var hiddenBytes: UInt64 = 0
        if createHidden {
            if createHiddenPassword.isEmpty && hiddenKeyfileURLs.isEmpty {
                status = "Nested volume needs a password or keyfile, different from the outer volume."
                return
            }
            if !createHiddenPassword.isEmpty && createHiddenPassword.count < 16 && hiddenKeyfileURLs.isEmpty {
                status = "Nested volume password must be at least 16 characters, and different from the outer password."
                return
            }
            guard createHiddenPassword != createPassword || createHiddenPassword.isEmpty else {
                status = "Use a different password for the nested volume."
                return
            }
            guard let nested = parseSizeBytes(amount: createHiddenSizeAmount, unit: createHiddenSizeUnit),
                  nested >= SizeUnit.minVolume else {
                status = "Nested size must be at least 2 MiB and less than half the outer size, so the outer volume has room."
                return
            }
            hiddenBytes = nested
        }
        let asked = parseSizeBytes(amount: createSizeAmount, unit: createSizeUnit) ?? 0
        if basketURLs.isEmpty {
            guard asked >= SizeUnit.minVolume, asked <= SizeUnit.maxVolume else {
                status = "Size must be 2 MiB–64 GiB (KiB, MiB, or GiB)."
                return
            }
        }
        let bytes = volumeBytesForBasket(asked: asked, urls: basketURLs, hiddenBytes: hiddenBytes)
        if bytes > SizeUnit.maxVolume {
            status = "Basket is too large for a 64 GiB phone volume. Remove files."
            return
        }
        if createHidden {
            guard bytes >= SizeUnit.minVolume * 4, hiddenBytes * 2 < bytes else {
                status = "Nested size must be at least 2 MiB and less than half the outer size, so the outer volume has room."
                return
            }
        }
        startCreateVolume(sizeBytes: bytes, hiddenBytes: hiddenBytes)
    }

    private func startCreateVolume(sizeBytes: UInt64, hiddenBytes: UInt64) {
        let dest = FileManager.default.temporaryDirectory.appendingPathComponent(Self.sanitizeDisguiseName(createFileName))
        beginWork("Creating \(SizeUnit.formatBytes(sizeBytes)) \(createCipher) / \(createKdf) volume…")
        let password = createPassword
        let pim = Int32(createPim) ?? 0
        let cipher = createCipher
        let kdf = createKdf
        var keys = keyfileURLs.map(\.path)
        let hiddenPw = createHidden ? createHiddenPassword : ""
        let hiddenPimVal = Int32(createHiddenPim) ?? 0
        let nested = createHidden
        let basket = basketURLs
        let hashes = basketHashes
        let hiddenKeys = hiddenKeyfileURLs.map(\.path)
        var filesystem = createFilesystem
        if sizeBytes >= 4 * 1024 * 1024 * 1024 { filesystem = "exFAT" }
        DispatchQueue.global(qos: .userInitiated).async {
            let rc = VcMobileBridge.createVolume(
                path: dest.path,
                password: password,
                pim: pim,
                sizeBytes: sizeBytes,
                cipher: cipher,
                kdf: kdf,
                keyfiles: keys,
                hiddenPassword: hiddenPw,
                hiddenPim: hiddenPimVal,
                hiddenSizeBytes: hiddenBytes,
                hiddenKeyfiles: hiddenKeys,
                filesystem: filesystem
            )
            var packed = 0
            var packFail: String?
            if rc == 0 && !basket.isEmpty {
                var error: Int32 = 0
                if let handle = VcMobileBridge.open(
                    path: dest.path,
                    password: password,
                    pim: pim,
                    keyfiles: keys,
                    error: &error
                ) {
                    var used = Set<String>()
                    for url in basket {
                        if let err = importURLIntoVolume(handle, url, used: &used) {
                            packFail = err
                            break
                        }
                        packed += 1
                    }
                    writeBasketProof(handle, urls: basket, hashes: hashes)
                    VcMobileBridge.close(handle)
                } else {
                    packFail = "Created the volume, but could not open it to copy the basket."
                }
            }
            DispatchQueue.main.async {
                endWork()
                if rc != 0 {
                    status = "Create failed (code \(rc))."
                    return
                }
                containerURL = dest
                self.password = password
                self.pim = createPim
                var msg = "Created \(SizeUnit.formatBytes(sizeBytes)) \(cipher) / \(kdf) \(filesystem) volume as \(dest.lastPathComponent) (standard VeraCrypt file; the name is only a disguise). Open volume, or Share encrypted. Same password, PIM, and keyfiles open it on a PC, Mac, or another phone — the extension is ignored."
                if packed > 0 {
                    msg += " Copied \(packed) file(s) from the basket into the volume. SHA-256 proof is BASKET.sha256 inside the volume."
                    if packFail == nil {
                        basketURLs = []
                        basketHashes = [:]
                    }
                }
                if let packFail {
                    msg += " \(packFail)"
                }
                if nested {
                    msg += " Nested volume is inside; open it with the nested password. Do not fill the outer volume."
                }
                status = msg
                holdLock = true
                SystemFiles.exportCopy(url: dest) { saved in
                    holdLock = false
                    if let saved {
                        _ = saved.startAccessingSecurityScopedResource()
                        incomingFile = nil
                        containerURL = saved
                        status = "Saved \(saved.lastPathComponent). That file is selected. Open volume, or Share encrypted."
                    }
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
        if text.isEmpty && keyfileURLs.isEmpty {
            status = "Type the volume password, or add a keyfile."
            return
        }
        startOpenVolume()
    }

    private func startOpenVolume() {
        guard let path = containerURL?.path else {
            status = "Select a container first."
            return
        }
        let text = useTextPassword ? password : ""
        closeVolume()
        beginWork("Opening volume…")
        let backup = useBackupHeader
        let readOnly = readOnlyOpen
        let tcMode = trueCryptMode
        let pimValue = Int32(pim) ?? 0
        let keys = keyfileURLs
        let protect = protectHidden
        let hiddenPw = hiddenProtectPassword
        let hiddenPimValue = Int32(hiddenProtectPim) ?? 0
        DispatchQueue.global(qos: .userInitiated).async {
            let keyfilePaths = keys.map(\.path)
            var error: Int32 = 0
            let handle = VcMobileBridge.open(
                path: path,
                password: text,
                pim: tcMode ? 0 : pimValue,
                keyfiles: keyfilePaths,
                useBackupHeader: backup,
                readOnly: readOnly,
                protectHidden: protect,
                hiddenPassword: protect ? hiddenPw : "",
                hiddenPim: protect ? hiddenPimValue : 0,
                error: &error
            )
            DispatchQueue.main.async {
                endWork()
                guard let handle else {
                    status = openErrorMessage(error)
                    return
                }
                volumeHandle = handle
                dirPath = ""
                switch VcMobileBridge.listDir(handle, path: "/") {
                case .failure(let err):
                    VcMobileBridge.close(handle)
                    volumeHandle = nil
                    status = listErrorMessage(err.rawValue)
                    entries = []
                    listTruncated = false
                case .success(let listed):
                    let truncated = listed.contains { $0.name == "!truncated!" }
                    entries = listed.filter { $0.name != "!truncated!" }
                    listTruncated = truncated
                    status = "Mounted in this app. Size \(VcMobileBridge.size(handle)) bytes. Tap Open on a folder, or Share on a file."
                    if protect { status = "Hidden volume is being protected against damage. " + status }
                    if truncated { status += " Listing truncated at \(VC_LIST_UI_MAX) entries. Tap Load more." }
                }
            }
        }
    }

    private func wipeFile(_ url: URL) {
        var isDir: ObjCBool = false
        guard FileManager.default.fileExists(atPath: url.path, isDirectory: &isDir) else { return }
        if isDir.boolValue {
            if let children = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: nil) {
                children.forEach { wipeFile($0) }
            }
            try? FileManager.default.removeItem(at: url)
            return
        }
        let length = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
        if length > 0 && length <= 64 * 1024 * 1024, let handle = try? FileHandle(forWritingTo: url) {
            try? handle.write(contentsOf: Data(count: length))
            try? handle.close()
        }
        try? FileManager.default.removeItem(at: url)
    }

    private func wipeSessionFiles() {
        lastPlain.forEach { wipeFile($0) }
        let tmp = FileManager.default.temporaryDirectory
        let unwrapped = tmp.appendingPathComponent("unwrapped", isDirectory: true)
        if let files = try? FileManager.default.contentsOfDirectory(at: unwrapped, includingPropertiesForKeys: nil) {
            files.forEach { wipeFile($0) }
        }
        try? FileManager.default.removeItem(at: unwrapped)
        if let files = try? FileManager.default.contentsOfDirectory(at: tmp, includingPropertiesForKeys: nil) {
            for url in files {
                let name = url.lastPathComponent
                if name.hasPrefix("vcbio-") ||
                    name.hasPrefix("vc-in-") ||
                    name.hasPrefix("wrap-in-") ||
                    name == "vcport-biometric.key" {
                    wipeFile(url)
                }
            }
        }
    }

    private func closeVolume() {
        if let handle = volumeHandle {
            VcMobileBridge.close(handle)
            volumeHandle = nil
        }
        entries = []
        dirPath = ""
        listTruncated = false
        selectedNames = []
    }

    /// Home / app switcher: close a mounted volume. Keep the Create wizard
    /// (generated passwords, nested volume options, basket) so Copy once
    /// keyfile) so Copy once can be pasted into Notes and creation can
    /// continue. Dismount / Panic still call lockSession().
    private func dismountOnLeave() {
        let wasOpen = volumeHandle != nil
        closeVolume()
        password = ""
        hiddenProtectPassword = ""
        pim = "0"
        hiddenProtectPim = "0"
        lastPlain = []
        if wasOpen && !status.hasPrefix("Panic") {
            status = "Dismounted. Create form kept. Dismount or Panic wipe also clears the generated password."
        }
    }

    private func isTemporaryContainer(_ url: URL) -> Bool {
        let path = url.path
        return path.contains("/tmp/") ||
            path.contains("/Caches/") ||
            path.contains("/TemporaryItems/") ||
            path.contains(FileManager.default.temporaryDirectory.path)
    }

    private func fileSize(_ url: URL) -> Int64 {
        (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize).map { Int64($0) } ?? 0
    }

    private func parseSizeBytes(amount: String, unit: SizeUnit) -> UInt64? {
        let digits = amount.filter(\.isNumber)
        guard let n = UInt64(digits), n > 0 else { return nil }
        return SizeUnit.toBytes(amount: n, unit: unit)
    }

    private func syncCreateSizeFromBasket() {
        guard !basketURLs.isEmpty else { return }
        let hidden: UInt64
        if createHidden, let n = parseSizeBytes(amount: createHiddenSizeAmount, unit: createHiddenSizeUnit) {
            hidden = n
        } else {
            hidden = 0
        }
        let need = volumeBytesForBasket(asked: SizeUnit.minVolume, urls: basketURLs, hiddenBytes: hidden)
        let (n, unit) = SizeUnit.fit(need)
        createSizeAmount = String(n)
        createSizeUnit = unit
    }

    private func volumeBytesForBasket(asked: UInt64, urls: [URL], hiddenBytes: UInt64) -> UInt64 {
        var payload: UInt64 = 0
        for url in urls {
            let n = fileSize(url)
            payload += n > 0 ? UInt64(n) : (1 << 20)
        }
        let overhead: UInt64 = urls.isEmpty ? 0 : (5 << 20)
        let need = payload + overhead + hiddenBytes
        var bytes = max(max(asked, need), SizeUnit.minVolume)
        if hiddenBytes > 0 {
            bytes = max(bytes, hiddenBytes * 2 + SizeUnit.minVolume)
        }
        return bytes
    }

    private func basketSummary(_ urls: [URL]) -> String {
        var bytes: Int64 = 0
        var unknown = false
        for url in urls {
            let n = fileSize(url)
            if n > 0 { bytes += n } else { unknown = true }
        }
        let size = (unknown && bytes == 0) ? "size unknown" : SizeUnit.formatBytes(UInt64(max(bytes, 0)))
        let files = urls.count == 1 ? "1 file" : "\(urls.count) files"
        let hidden: UInt64
        if createHidden, let n = parseSizeBytes(amount: createHiddenSizeAmount, unit: createHiddenSizeUnit) {
            hidden = n
        } else {
            hidden = 0
        }
        let need = volumeBytesForBasket(asked: SizeUnit.minVolume, urls: urls, hiddenBytes: hidden)
        return "\(files), about \(size). Volume will be at least \(SizeUnit.formatBytes(need))."
    }

    private func shortHex(_ hex: String) -> String {
        guard hex.count >= 12 else { return hex }
        return "\(hex.prefix(8))…\(hex.suffix(4))"
    }

    private func shortHash(_ hex: String?) -> String {
        guard let hex else { return "SHA-256 …" }
        return "SHA-256 \(shortHex(hex))"
    }

    private func sha256Path(_ path: String) -> String? {
        guard let handle = try? FileHandle(forReadingFrom: URL(fileURLWithPath: path)) else { return nil }
        defer { try? handle.close() }
        var hasher = SHA256()
        while true {
            let data = handle.readData(ofLength: 64 * 1024)
            if data.isEmpty { break }
            hasher.update(data: data)
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    private func sha256File(_ url: URL) -> String? {
        let accessed = url.startAccessingSecurityScopedResource()
        defer {
            if accessed { url.stopAccessingSecurityScopedResource() }
        }
        return sha256Path(url.path)
    }

    private func writeBasketProof(_ handle: OpaquePointer, urls: [URL], hashes: [String: String]) {
        let lines = urls.compactMap { url -> String? in
            let hex = hashes[url.path] ?? sha256File(url)
            guard let hex else { return nil }
            return "\(hex)  \(url.lastPathComponent)"
        }
        guard !lines.isEmpty else { return }
        let proof = FileManager.default.temporaryDirectory.appendingPathComponent("BASKET.sha256")
        try? (lines.joined(separator: "\n") + "\n").write(to: proof, atomically: true, encoding: .utf8)
        _ = VcMobileBridge.importFile(handle, destDir: "/", src: proof.path, destName: "BASKET.sha256")
        wipeFile(proof)
    }

    private func uniqueDestName(_ raw: String, used: inout Set<String>) -> String {
        var name = raw.replacingOccurrences(of: "/", with: "_")
        if name.isEmpty { name = "file" }
        if !used.contains(name) {
            used.insert(name)
            return name
        }
        let ns = name as NSString
        let ext = ns.pathExtension
        let stem = ext.isEmpty ? name : ns.deletingPathExtension
        var n = 2
        while true {
            let cand = ext.isEmpty ? "\(stem)-\(n)" : "\(stem)-\(n).\(ext)"
            if !used.contains(cand) {
                used.insert(cand)
                return cand
            }
            n += 1
        }
    }

    private func importURLIntoVolume(_ handle: OpaquePointer, _ url: URL, used: inout Set<String>) -> String? {
        let name = uniqueDestName(url.lastPathComponent, used: &used)
        let accessed = url.startAccessingSecurityScopedResource()
        defer {
            if accessed { url.stopAccessingSecurityScopedResource() }
        }
        var temp: URL?
        let srcPath: String
        if url.isFileURL, FileManager.default.isReadableFile(atPath: url.path) {
            srcPath = url.path
        } else if let copied = copyScopedFile(url) {
            temp = copied
            srcPath = copied.path
        } else {
            return "Could not read \(url.lastPathComponent). Pick it again from Files."
        }
        let rc = VcMobileBridge.importFile(handle, destDir: "/", src: srcPath, destName: name)
        if let temp { try? FileManager.default.removeItem(at: temp) }
        if rc == 0 { return nil }
        return importErrorMessage(name, rc, handle: handle)
    }

    private func lockSession() {
        closeVolume()
        password = ""
        wrapPassword = ""
        wrapHold = ""
        holdLock = false
        createPassword = ""
        createHiddenPassword = ""
        hiddenProtectPassword = ""
        newPassword = ""
        pim = "0"
        createPim = "0"
        createHiddenPim = "0"
        hiddenProtectPim = "0"
        newPim = "0"
        keyfileURLs = []
        hiddenKeyfileURLs = []
        basketHashes = [:]
        basketURLs = []
        createHidden = false
        createCipher = VcMobileBridge.defaultCipher
        createKdf = VcMobileBridge.defaultKdf
        createFilesystem = "FAT"
        createFileName = "volume.hc"
        createSizeAmount = "16"
        createSizeUnit = .mib
        createHiddenSizeAmount = "4"
        createHiddenSizeUnit = .mib
        entropyPercent = 0
        entropyMarks = []
        VcMobileBridge.resetEntropy()
        entries = []
        dirPath = ""
        listTruncated = false
        lastPlain = []
        selectedNames = []
        wipeSessionFiles()
        endWork()
        if !status.hasPrefix("Panic") {
            status = "Dismounted. Passwords, keyfiles in memory, and decrypted copies wiped. Ciphertext stays."
        }
    }

    private func panicWipe() {
        closeVolume()
        password = ""
        wrapPassword = ""
        wrapHold = ""
        holdLock = false
        createPassword = ""
        createHiddenPassword = ""
        hiddenProtectPassword = ""
        newPassword = ""
        entries = []
        dirPath = ""
        lastPlain = []
        wipeSessionFiles()
        SensitivePaste.forget()
        let tmp = FileManager.default.temporaryDirectory
        if let files = try? FileManager.default.contentsOfDirectory(at: tmp, includingPropertiesForKeys: nil) {
            for url in files {
                wipeFile(url)
            }
        }
        status = "Panic wipe complete. Cache and clipboard leftovers are gone."
        basketURLs = []
        basketHashes = [:]
        hiddenKeyfileURLs = []
    }

    private func shareVaultFile(_ entry: VaultEntry) {
        guard let handle = volumeHandle else {
            status = "Open a volume first."
            return
        }
        let volumePath = joinDir(dirPath, entry.name)
        beginWork("Preparing \(entry.name)…")
        DispatchQueue.global(qos: .userInitiated).async {
            let dest = FileManager.default.temporaryDirectory
                .appendingPathComponent(entry.name.replacingOccurrences(of: "/", with: "_"))
            let rc = VcMobileBridge.exportFile(handle, name: volumePath, dest: dest.path)
            DispatchQueue.main.async {
                endWork()
                if rc != 0 {
                    status = extractErrorMessage(entry.name, rc)
                    return
                }
                status = "Share \(entry.name) with WhatsApp, Mail, Drive, or any app."
                lastPlain = [dest]
                SystemShare.present(items: [dest])
            }
        }
    }

    private func importFromDevice(_ url: URL, move: Bool) {
        guard let handle = volumeHandle else {
            status = "Open a volume first."
            return
        }
        beginWork(move ? "Moving from device…" : "Copying from device…")
        let destDir = dirPath.isEmpty ? "/" : dirPath
        DispatchQueue.global(qos: .userInitiated).async {
            let name = url.lastPathComponent
            var temp: URL?
            let srcPath: String
            if url.isFileURL {
                srcPath = url.path
            } else {
                let dest = FileManager.default.temporaryDirectory
                    .appendingPathComponent("from-device-\(Int(Date().timeIntervalSince1970))-\(name)")
                do {
                    try? FileManager.default.removeItem(at: dest)
                    try FileManager.default.copyItem(at: url, to: dest)
                    temp = dest
                    srcPath = dest.path
                } catch {
                    DispatchQueue.main.async {
                        endWork()
                        status = "Could not read that file from the device."
                    }
                    return
                }
            }
            let hex = sha256Path(srcPath)
            let rc = VcMobileBridge.importFile(handle, destDir: destDir, src: srcPath, destName: name)
            var deletedOriginal = false
            if rc == 0 && move {
                do {
                    try FileManager.default.removeItem(at: url)
                    deletedOriginal = true
                } catch {
                    deletedOriginal = false
                }
            }
            if let temp {
                try? FileManager.default.removeItem(at: temp)
            }
            let proof = hex.map { " SHA-256 \(shortHex($0)) (session only)." } ?? ""
            DispatchQueue.main.async {
                endWork()
                if rc != 0 {
                    status = importErrorMessage(name, rc, handle: handle)
                    return
                }
                if move && !deletedOriginal {
                    status = "Copied \(name) into the volume. Could not delete the original; remove it in Files if you meant a move.\(proof)"
                } else if move {
                    status = "Moved \(name) into the volume.\(proof)"
                } else {
                    status = "Copied \(name) from the device into this folder.\(proof)"
                }
                reloadDir()
            }
        }
    }

    private func copySelectedToDevice(move: Bool) {
        guard volumeHandle != nil else {
            status = "Open a volume first."
            return
        }
        guard let name = selectedNames.first,
              let entry = entries.first(where: { $0.name == name && !$0.isDir }) else {
            status = move
                ? "Tap a file in the volume, then Move to device."
                : "Tap a file in the volume, then Copy to device."
            return
        }
        exportToDevice(entry, move: move)
    }

    private func exportToDevice(_ entry: VaultEntry, move: Bool) {
        guard let handle = volumeHandle else {
            status = "Open a volume first."
            return
        }
        let volumePath = joinDir(dirPath, entry.name)
        beginWork(move ? "Moving \(entry.name) to device…" : "Copying \(entry.name) to device…")
        DispatchQueue.global(qos: .userInitiated).async {
            let dest = FileManager.default.temporaryDirectory
                .appendingPathComponent(entry.name.replacingOccurrences(of: "/", with: "_"))
            try? FileManager.default.removeItem(at: dest)
            let rc = VcMobileBridge.exportFile(handle, name: volumePath, dest: dest.path)
            DispatchQueue.main.async {
                endWork()
                if rc != 0 {
                    status = extractErrorMessage(entry.name, rc)
                    return
                }
                SystemFiles.exportCopy(url: dest) { saved in
                    if saved == nil {
                        status = "Cancelled saving \(entry.name)."
                        return
                    }
                    if move {
                        let dlt = VcMobileBridge.deleteFile(handle, path: volumePath)
                        if dlt == 0 {
                            status = "Moved \(entry.name) to the device."
                            selectedNames.remove(entry.name)
                            reloadDir()
                        } else {
                            status = "Copied \(entry.name) to the device, but could not remove it from the volume."
                        }
                    } else {
                        status = "Copied \(entry.name) to the device."
                    }
                }
            }
        }
    }

    private func reloadDir(append: Bool = false) {
        guard let handle = volumeHandle else { return }
        let path = dirPath.isEmpty ? "/" : dirPath
        let offset = append ? Int32(entries.count) : 0
        switch VcMobileBridge.listDir(handle, path: path, offset: offset) {
        case .failure(let err):
            status = listErrorMessage(err.rawValue)
        case .success(let listed):
            let truncated = listed.contains { $0.name == "!truncated!" }
            let files = listed.filter { $0.name != "!truncated!" }
            entries = append ? entries + files : files
            listTruncated = truncated
            if truncated { status = "Folder listing truncated at \(VC_LIST_UI_MAX) entries. Tap Load more." }
        }
    }

    private func joinDir(_ dir: String, _ name: String) -> String {
        dir.isEmpty ? name : "\(dir)/\(name)"
    }

    private func parentDir(_ dir: String) -> String {
        guard let slash = dir.lastIndex(of: "/") else { return "" }
        return String(dir[..<slash])
    }

    private func openErrorMessage(_ code: Int32) -> String {
        switch code {
        case -2: return "Wrong password, PIM, or keyfile mix."
        case -6: return "This container uses NTFS, ext, or another filesystem VC Port does not open. FAT and exFAT are supported."
        case -1: return "Could not read the container file."
        case -3: return "Not a VeraCrypt-compatible volume, or the header is damaged."
        case -4: return "Missing path or password argument."
        case -5: return "Not enough memory to open the volume."
        default: return "Open failed (code \(code))."
        }
    }

    private func listErrorMessage(_ code: Int32) -> String {
        switch code {
        case -6: return "Opened the volume, but the filesystem is NTFS or otherwise unsupported. FAT and exFAT work here."
        case -4: return "Could not list that folder path."
        case -5: return "Not enough memory to list the folder."
        case -1: return "Could not read the folder from the volume."
        default: return "Could not list files (code \(code))."
        }
    }

    private func extractErrorMessage(_ name: String, _ rc: Int32) -> String {
        switch rc {
        case -6: return "Could not extract \(name). NTFS/ext are unsupported; FAT and exFAT work."
        case -4: return "Could not extract \(name). Bad path."
        case -5: return "Could not extract \(name). Not enough memory."
        case -1: return "Could not extract \(name). Read failed."
        case -2: return "Could not extract \(name). Wrong password or header."
        default: return "Could not extract \(name) (code \(rc))."
        }
    }

    private func importErrorMessage(_ name: String, _ rc: Int32, handle: OpaquePointer? = nil) -> String {
        if let handle, VcMobileBridge.protectionTriggered(handle) {
            return "Hidden volume protection triggered. The outer volume is now write-protected until you dismount."
        }
        switch rc {
        case -6: return "Could not copy \(name). Folders are not created this way."
        case -4: return "Could not copy \(name). Bad name or path."
        case -5: return "Could not copy \(name). Volume is full, or the file is larger than 4 GiB (FAT limit)."
        case -3: return "A file named \(name) already exists in this folder."
        case -1: return "Could not copy \(name) into the volume."
        default: return "Could not copy \(name) (code \(rc))."
        }
    }

    private static func formatUpdateStatus(_ result: SourcePin.CheckResult) -> String {
        var bits: [String] = [
            result.newer
                ? "Update \(result.remoteVersion) available from source. \(result.notes)"
                : result.sourceMoved
                    ? "Same VC Port \(SourcePin.localVersion), VeraCrypt pin moved to \(result.remoteUpstreamCommit.prefix(12)). Rebuild from source."
                    : "Already up to date (\(SourcePin.localVersion))."
        ]
        if result.officialNewer && !result.officialVersion.isEmpty {
            bits.append(
                "Official VeraCrypt \(result.officialVersion) is published. This build still compiles \(SourcePin.upstreamVersion). Merge with scripts/sync-upstream.sh and rebuild. This app does not fetch their source."
            )
        }
        if result.sourceDegraded && !result.sourceWarning.isEmpty {
            bits.append(result.sourceWarning)
        }
        if !result.downloadURL.isEmpty { bits.append(result.downloadURL) }
        if !result.apkSha256.isEmpty {
            bits.append("SHA-256 \(result.apkSha256)")
        } else if result.newer {
            bits.append("No APK hash in the manifest yet; GitHub APKs are debug-signed previews.")
        }
        bits.append("This app does not install itself. Offline again.")
        return bits.joined(separator: " ")
    }

    private func headerErrorMessage(_ rc: Int32) -> String {
        switch rc {
        case -4: return "Need a container path and at least a password or keyfile."
        case -2: return "Wrong password, PIM, or keyfile mix."
        case -1: return "Could not read or write the container. Close it first if it is open."
        case -3: return "Not a VeraCrypt-compatible volume, or the header is damaged."
        case -6: return "That KDF is not available in this build."
        case -5: return "Not enough memory."
        default: return "Header operation failed (code \(rc))."
        }
    }

    private func currentUnlockPaths() -> (password: String, keyfiles: [String], temps: [URL])? {
        guard containerURL != nil else {
            status = "Choose a container first."
            return nil
        }
        let text = useTextPassword ? password : ""
        if text.isEmpty && keyfileURLs.isEmpty {
            status = "Enter the current password or keyfiles above."
            return nil
        }
        var temps: [URL] = []
        var paths = keyfileURLs.map(\.path)
        return (text, paths, temps)
    }

    private func changeVolumePassword() {
        runChangeHeader(
            newPassword: newPassword,
            newKdf: "",
            keepKeyfiles: true,
            success: "Changed volume password. Open with the new password and the same keyfiles."
        )
    }

    private func setHeaderKdf() {
        guard headerKdf != "(keep current)" else {
            status = "Pick a KDF other than keep current."
            return
        }
        runChangeHeader(
            newPassword: "",
            newKdf: headerKdf,
            keepKeyfiles: true,
            success: "Set header key derivation algorithm to \(headerKdf)."
        )
    }

    private func applyKeyfilesToVolume() {
        runChangeHeader(
            newPassword: "",
            newKdf: "",
            keepKeyfiles: true,
            success: "Applied the current keyfile list (Add/Remove keyfiles) to the volume header."
        )
    }

    private func removeAllKeyfiles() {
        runChangeHeader(
            newPassword: "",
            newKdf: "",
            keepKeyfiles: false,
            success: "Removed all keyfiles from volume. Open with the password only."
        )
    }

    private func runChangeHeader(newPassword: String, newKdf: String, keepKeyfiles: Bool, success: String) {
        guard let path = containerURL?.path, let unlock = currentUnlockPaths() else { return }
        let nextPassword = newPassword.isEmpty ? unlock.password : newPassword
        if !keepKeyfiles && nextPassword.isEmpty {
            status = "Removing all keyfiles needs a text password, or the volume cannot be opened."
            return
        }
        let nextPim: Int32 = {
            if newPassword.isEmpty && (newPim.isEmpty || newPim == "0") {
                return Int32(pim) ?? 0
            }
            return Int32(newPim) ?? 0
        }()
        closeVolume()
        beginWork("Rewriting volume header…")
        DispatchQueue.global(qos: .userInitiated).async {
            let rc = VcMobileBridge.changeHeader(
                path: path,
                password: unlock.password,
                pim: Int32(pim) ?? 0,
                keyfiles: unlock.keyfiles,
                backup: useBackupHeader,
                newPassword: newPassword,
                newPim: nextPim,
                newKdf: newKdf,
                newKeyfiles: keepKeyfiles ? unlock.keyfiles : []
            )
            unlock.temps.forEach { try? FileManager.default.removeItem(at: $0) }
            DispatchQueue.main.async {
                endWork()
                status = rc == 0 ? success : headerErrorMessage(rc)
            }
        }
    }

    private func backupVolumeHeader() {
        guard let path = containerURL?.path, let unlock = currentUnlockPaths() else { return }
        closeVolume()
        let dest = FileManager.default.temporaryDirectory.appendingPathComponent("volume-header.bak")
        let rc = VcMobileBridge.backupHeaders(
            volumePath: path,
            backupPath: dest.path,
            password: unlock.password,
            pim: Int32(pim) ?? 0,
            keyfiles: unlock.keyfiles
        )
        unlock.temps.forEach { try? FileManager.default.removeItem(at: $0) }
        guard rc == 0 else {
            status = headerErrorMessage(rc)
            return
        }
        status = "Header backup ready. Save the .bak file somewhere safe, not only on this phone."
        SystemShare.present(items: [dest])
    }

    private func restoreVolumeHeader(_ backup: URL) {
        guard let path = containerURL?.path, let unlock = currentUnlockPaths() else { return }
        closeVolume()
        let rc = VcMobileBridge.restoreHeaders(
            volumePath: path,
            backupPath: backup.path,
            password: unlock.password,
            pim: Int32(pim) ?? 0,
            keyfiles: unlock.keyfiles
        )
        unlock.temps.forEach { try? FileManager.default.removeItem(at: $0) }
        status = rc == 0
            ? "Restored volume header. Open with the password that was current when the backup was made."
            : headerErrorMessage(rc)
    }

    private func restoreEmbeddedHeader() {
        guard let path = containerURL?.path, let unlock = currentUnlockPaths() else { return }
        closeVolume()
        let rc = VcMobileBridge.restoreHeaders(
            volumePath: path,
            backupPath: "",
            password: unlock.password,
            pim: Int32(pim) ?? 0,
            keyfiles: unlock.keyfiles
        )
        unlock.temps.forEach { try? FileManager.default.removeItem(at: $0) }
        status = rc == 0
            ? "Restored from embedded backup header. Open with the same password, PIM, and keyfiles."
            : headerErrorMessage(rc)
    }

    private func mkdirInVolume(_ name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let handle = volumeHandle else {
            status = "Open a volume first."
            return
        }
        guard !trimmed.isEmpty else {
            status = "Name is empty."
            return
        }
        let destDir = dirPath.isEmpty ? "/" : dirPath
        let rc = VcMobileBridge.mkdir(handle, parent: destDir, name: trimmed)
        if rc != 0 {
            status = importErrorMessage(trimmed, rc)
            return
        }
        status = "Created folder \(trimmed)."
        reloadDir()
    }

    private func renameSelected(_ newName: String) {
        let trimmed = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let handle = volumeHandle else {
            status = "Open a volume first."
            return
        }
        guard let old = selectedNames.first else {
            status = "Tap a file or folder, then Rename."
            return
        }
        let rc = VcMobileBridge.renameFile(handle, path: joinDir(dirPath, old), newName: trimmed)
        if rc != 0 {
            status = importErrorMessage(old, rc)
            return
        }
        selectedNames = [trimmed]
        status = "Renamed \(old) to \(trimmed)."
        reloadDir()
    }

    private func deleteSelected() {
        guard let handle = volumeHandle else {
            status = "Open a volume first."
            return
        }
        guard let name = selectedNames.first,
              let entry = entries.first(where: { $0.name == name }) else {
            status = "Tap a file or folder, then Delete."
            return
        }
        let path = joinDir(dirPath, entry.name)
        let rc = entry.isDir ? VcMobileBridge.rmdir(handle, path: path) : VcMobileBridge.deleteFile(handle, path: path)
        if rc != 0 {
            status = entry.isDir && rc == -3 ? "Folder \(path) is not empty." : importErrorMessage(entry.name, rc)
            return
        }
        selectedNames.remove(name)
        status = "Deleted \(entry.name)."
        reloadDir()
    }

    private func showEntryProperties() {
        guard let name = selectedNames.first,
              let entry = entries.first(where: { $0.name == name }) else {
            status = entries.isEmpty ? "This folder is empty." : "Tap a file or folder, then Properties."
            return
        }
        let kind = entry.isDir ? "Folder" : "File"
        let size = entry.isDir ? "" : ", \(byteCount(entry.size))"
        status = "\(kind) \(entry.name)\(size), modified \(formatFatStamp(entry.dosDate, entry.dosTime)). Browsed in this app; this is not a mounted drive."
    }

    private func wipeFreeSpace() {
        guard let handle = volumeHandle else {
            status = "Open a volume first."
            return
        }
        beginWork("Wiping free space…")
        DispatchQueue.global(qos: .userInitiated).async {
            let rc = VcMobileBridge.wipeFreeSpace(handle)
            DispatchQueue.main.async {
                endWork()
                if rc != 0 {
                    status = "Could not wipe free space (code \(rc)). Read-only volumes refuse this."
                } else {
                    status = "Wiped unused FAT clusters. Deleted file contents in free space are overwritten."
                    reloadDir()
                }
            }
        }
    }

    private func formatFatStamp(_ date: UInt16, _ time: UInt16) -> String {
        if date == 0 { return "unknown" }
        let year = 1980 + Int(date >> 9)
        let month = Int((date >> 5) & 0xF)
        let day = Int(date & 0x1F)
        let hour = Int(time >> 11)
        let min = Int((time >> 5) & 0x3F)
        return String(format: "%04d-%02d-%02d %02d:%02d UTC", year, month, day, hour, min)
    }

    private func showVolumeProperties() {
        guard let handle = volumeHandle else {
            status = "Open the volume first for Volume properties."
            return
        }
        status = VcMobileBridge.volumeInfo(handle) ?? "Could not read volume properties."
    }

    private func generateKeyfile(nested: Bool) {
        let n = min(max(Int(keyfileGenCount.filter(\.isNumber)) ?? 1, 1), 8)
        let base = keyfileGenName.trimmingCharacters(in: .whitespacesAndNewlines)
        let pattern = base.isEmpty ? "keyfile.bin" : base
        var urls: [URL] = []
        for i in 1...n {
            let name: String
            if n == 1 {
                name = pattern
            } else {
                let dot = pattern.lastIndex(of: ".")
                let stem = dot.map { String(pattern[..<$0]) } ?? pattern
                let ext = dot.map { String(pattern[$0...]) } ?? ""
                name = "\(stem)-\(i)\(ext)"
            }
            let dest = FileManager.default.temporaryDirectory.appendingPathComponent(name)
            let rc = VcMobileBridge.generateKeyfile(path: dest.path)
            guard rc == 0 else {
                status = "Keyfile generator failed."
                return
            }
            urls.append(dest)
        }
        if nested {
            hiddenKeyfileURLs.append(contentsOf: urls)
        } else {
            keyfileURLs.append(contentsOf: urls)
        }
        status = n == 1
            ? "Generated and added \(urls[0].lastPathComponent). Save a copy. Any extension is fine."
            : "Generated \(n) keyfiles and added them. Save copies. Any extension is fine."
        SystemShare.present(items: urls)
    }

    private func runBenchmark() {
        beginWork("Running encryption benchmark…")
        DispatchQueue.global(qos: .userInitiated).async {
            let result = VcMobileBridge.benchmark()
            DispatchQueue.main.async {
                endWork()
                status = result
            }
        }
    }

    private func runTestVectors() {
        beginWork("Running known-answer test vectors…")
        DispatchQueue.global(qos: .userInitiated).async {
            let rc = VcMobileBridge.testVectors()
            DispatchQueue.main.async {
                endWork()
                status = rc == 0
                    ? "Test vectors passed. AES, Serpent, Twofish, Camellia, Kuznyechik, and XTS match the VeraCrypt known-answer tests."
                    : "Test vectors failed."
            }
        }
    }

    private func wrapURL(_ url: URL, password: String? = nil) {
        let secret = password ?? (wrapHold.count >= 16 ? wrapHold : wrapPassword)
        guard secret.count >= 16 else {
            holdLock = false
            wrapHold = ""
            status = "Use Generate strong password, or type at least 16 characters. Nothing is saved."
            return
        }
        beginWork("Wrapping \(url.lastPathComponent)…")
        holdLock = false
        DispatchQueue.global(qos: .userInitiated).async {
            guard let src = copyScopedFile(url) else {
                DispatchQueue.main.async {
                    endWork()
                    status = "Could not read \(url.lastPathComponent). Pick it again from Files."
                }
                return
            }
            let dest = FileManager.default.temporaryDirectory
                .appendingPathComponent(url.lastPathComponent + ".vcpw")
            let rc = VcMobileBridge.wrapFile(
                src: src.path,
                dest: dest.path,
                password: secret,
                originalName: url.lastPathComponent
            )
            DispatchQueue.main.async {
                endWork()
                if rc != 0 {
                    status = "Wrap failed (code \(rc))."
                    return
                }
                status = "Wrapped \(url.lastPathComponent). Password was not saved."
                incomingFile = dest
                SystemShare.present(items: [dest])
            }
        }
    }

    private func unwrapURL(_ url: URL, password: String? = nil) {
        let secret = password ?? (wrapHold.isEmpty ? wrapPassword : wrapHold)
        guard !secret.isEmpty else {
            holdLock = false
            wrapHold = ""
            status = "Enter the wrap password first. It is not stored."
            return
        }
        beginWork("Unwrapping \(url.lastPathComponent)…")
        holdLock = false
        DispatchQueue.global(qos: .userInitiated).async {
            guard let src = copyScopedFile(url) else {
                DispatchQueue.main.async {
                    endWork()
                    status = "Could not read \(url.lastPathComponent). Pick it again from Files."
                }
                return
            }
            let dir = FileManager.default.temporaryDirectory.appendingPathComponent("unwrapped", isDirectory: true)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let out = VcMobileBridge.unwrapFile(src: src.path, destDir: dir.path, password: secret)
            DispatchQueue.main.async {
                endWork()
                guard let out else {
                    status = "Unwrap failed. Wrong password or not a VC Port wrap."
                    return
                }
                let file = URL(fileURLWithPath: out)
                lastPlain = [file]
                status = "Unwrapped \(file.lastPathComponent). Password was not saved."
                SystemShare.present(items: [file])
            }
        }
    }

    private func copyScopedFile(_ url: URL) -> URL? {
        let accessed = url.startAccessingSecurityScopedResource()
        defer {
            if accessed { url.stopAccessingSecurityScopedResource() }
        }
        if url.isFileURL, FileManager.default.isReadableFile(atPath: url.path) {
            let dest = FileManager.default.temporaryDirectory
                .appendingPathComponent("vc-in-\(UUID().uuidString)-\(url.lastPathComponent)")
            do {
                try? FileManager.default.removeItem(at: dest)
                try FileManager.default.copyItem(at: url, to: dest)
                return dest
            } catch {
                return url
            }
        }
        let dest = FileManager.default.temporaryDirectory
            .appendingPathComponent("vc-in-\(UUID().uuidString)-\(url.lastPathComponent)")
        do {
            try? FileManager.default.removeItem(at: dest)
            try FileManager.default.copyItem(at: url, to: dest)
            return dest
        } catch {
            return nil
        }
    }

    private static let disguiseNames = [
        "volume.hc",
        "photo.jpg",
        "image.png",
        "clip.mp4",
        "notes.pdf",
        "model.safetensors",
        "adapter.lora",
        "weights.bin"
    ]

    private static func sanitizeDisguiseName(_ raw: String) -> String {
        var name = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "\\", with: "_")
        if let slash = name.lastIndex(of: "/") {
            name = String(name[name.index(after: slash)...])
        }
        if name.isEmpty || name == "." || name == ".." { return "volume.hc" }
        if name.lowercased().hasSuffix(".vcpw") {
            name = String(name.dropLast(5))
        }
        if name.isEmpty { return "volume.hc" }
        return String(name.prefix(120))
    }

    private func byteCount(_ size: UInt64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: Int64(size))
    }
}

private struct WorkOverlay: View {
    let title: String
    let percent: Int

    private var shown: String {
        title.isEmpty ? "On this phone" : title
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.38)
                .ignoresSafeArea()
            VStack(spacing: 16) {
                Text("This step")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.secondary)
                Text(shown)
                    .font(.title3.weight(.semibold))
                    .multilineTextAlignment(.center)
                WorkMeter(percent: percent)
                if percent >= 0 {
                    ProgressView(value: Double(min(max(percent, 0), 100)), total: 100)
                        .tint(Color(red: 10 / 255, green: 108 / 255, blue: 206 / 255))
                        .animation(.easeInOut(duration: 0.18), value: percent)
                    Text("\(percent)%")
                        .font(.system(size: 44, weight: .semibold, design: .rounded))
                        .monospacedDigit()
                } else {
                    Text("This step has no percent. The cells move until it finishes.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                Text("Nothing runs out of sight.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding(28)
            .frame(maxWidth: 340)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .allowsHitTesting(true)
        .transition(.opacity)
    }
}

private struct WorkMeter: View {
    let percent: Int

    var body: some View {
        if percent >= 0 {
            meter { i in Double(percent) / 12.5 > Double(i) }
        } else {
            TimelineView(.periodic(from: .now, by: 0.12)) { context in
                let tick = Int(context.date.timeIntervalSinceReferenceDate * 8) % 8
                meter { i in i == tick }
            }
        }
    }

    private func meter(on: (Int) -> Bool) -> some View {
        let lit = (0..<8).map(on)
        return HStack(spacing: 6) {
            ForEach(0..<8, id: \.self) { i in
                RoundedRectangle(cornerRadius: 3, style: .continuous)
                    .fill(lit[i]
                        ? Color(red: 10 / 255, green: 108 / 255, blue: 206 / 255)
                        : Color.secondary.opacity(0.22))
                    .frame(height: 10)
            }
        }
    }
}

private extension View {
    /// OTP content-type is the supported way to skip iOS Keychain / AutoFill save.
    func neverSaveHistory() -> some View {
        self
            .textContentType(.oneTimeCode)
            .privacySensitive(true)
            .autocorrectionDisabled()
            .textInputAutocapitalization(.never)
    }
}

#Preview {
    ContentView()
}

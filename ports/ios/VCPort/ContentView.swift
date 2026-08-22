import SwiftUI
import UniformTypeIdentifiers
import CryptoKit
import UIKit

let mountSlots = 8

struct MountedVolume: Identifiable {
    let id = UUID()
    let handle: OpaquePointer
    let url: URL
    let label: String
    var dirPath: String
    var entries: [VaultEntry]
    var truncated: Bool
    var readOnly: Bool = false
}

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State var containerURL: URL?
    @State var password = ""
    @State var lastUnlockPassword = ""
    @State var lastUnlockPim = "0"
    @State var pim = "0"
    @State var useTextPassword = true
    @State var keyfileURLs: [URL] = []
    @State var headerKeyfileURLs: [URL] = []
    @State var keyfileGenName = "keyfile.bin"
    @State var keyfileGenCount = "1"
    @State var keyfileImporterPresented = false
    @State var status = "Offline. Choose a VeraCrypt container, or share an encrypted file as-is."
    @State var entries: [VaultEntry] = []
    @State var importerPresented = false
    @State var shareEncImporterPresented = false
    @State var volumeHandle: OpaquePointer?
    @State var mountedVolumes: [MountedVolume] = []
    @State var activeMountIndex = 0
    @State var showOpenAnother = false
    @State var pimEstimateResult = ""
    @State var hashResult = ""
    @State var transferMove: Bool? = nil
    @State var dirPath = ""
    @State var listTruncated = false
    @State var incomingFile: URL?
    @State var lastPlain: [URL] = []
    @State var selectedNames: Set<String> = []
    @State var previewItem: InAppPreviewItem?
    @State var createCipher = VcMobileBridge.defaultCipher
    @State var createKdf = VcMobileBridge.defaultKdf
    @State var createSizeAmount = "16"
    @State var createSizeUnit = SizeUnit.mib
    @State var createFilesystem = "FAT"
    @State var createHiddenSizeAmount = "4"
    @State var createHiddenSizeUnit = SizeUnit.mib
    @State var createPassword = ""
    @State var createPim = "0"
    @State var createHidden = false
    @State var createHiddenPassword = ""
    @State var createHiddenPim = "0"
    @State var createFileName = "volume.hc"
    @State var entropyPercent = 0
    @State var newPassword = ""
    @State var newPim = "0"
    @State var headerKdf = "(keep current)"
    @State var useBackupHeader = false
    @State var readOnlyOpen = false
    @State var trueCryptMode = false
    @State var protectHidden = false
    @State var hiddenProtectPassword = ""
    @State var hiddenProtectPim = "0"
    @State var newFolderPresented = false
    @State var renamePresented = false
    @State var namePromptValue = ""
    @State var restoreHeaderPresented = false
    @State var copyFromDevicePresented = false
    @State var moveFromDevice = false
    @State var busy = false
    @State var workTitle = ""
    @State var workPercent = -1
    @State var entropyMarks: [CGPoint] = []
    @State var holdLock = false
    @State var basketURLs: [URL] = []
    @State var basketHashes: [String: String] = [:]
    @State var basketImporterPresented = false
    @State var hiddenKeyfileURLs: [URL] = []
    @State var hiddenKeyfileImporterPresented = false

    @State var selectedTab = 0
    @AppStorage("vc_port_idle_minutes") var idleMinutes = 0
    @State var idleAmountText = "0"
    @State var idleUnit = IdleUnit.minutes
    @State var idleTask: Task<Void, Never>?

    var selectableFileNames: Set<String> {
        Set(entries.filter { !$0.isDir }.map(\.name))
    }

    var holdingForPicker: Bool {
        holdLock || importerPresented
            || shareEncImporterPresented || keyfileImporterPresented
            || restoreHeaderPresented || copyFromDevicePresented || basketImporterPresented
            || hiddenKeyfileImporterPresented
    }

    var body: some View {
        ZStack {
            NavigationStack {
                sessionRoot
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
        .onAppear { installTestingHooks() }
        .onReceive(NotificationCenter.default.publisher(for: .vcPortTestingColdStart)) { _ in
            resetLaunchState()
            installTestingHooks()
        }
    }

    var sessionTabs: some View {
        TabView(selection: $selectedTab) {
            volumeTab
                .tag(0)
                .tabItem { Label("Volume", systemImage: "lock") }
                .portTag("tab_volume")
            createTab
                .tag(1)
                .tabItem { Label("Create", systemImage: "plus.rectangle.on.folder") }
                .portTag("tab_create")
            mountedVolumeForm
                .tag(2)
                .tabItem { Label("Mounted", systemImage: "externaldrive") }
                .portTag("tab_mounted")
            toolsTab
                .tag(3)
                .tabItem { Label("Tools", systemImage: "wrench.and.screwdriver") }
                .portTag("tab_tools")
        }
        .frame(maxWidth: horizontalSizeClass == .regular ? 760 : .infinity)
        .frame(maxWidth: .infinity)
    }

    var sessionChrome: some View {
        sessionTabs
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
    }

    var sessionPickers: some View {
        sessionChrome
            .fileImporter(isPresented: $importerPresented, allowedContentTypes: [.item, .data]) { result in
                switch result {
                case .success(let url):
                    ingestPickedContainer(url)
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
            .fileImporter(
                isPresented: $copyFromDevicePresented,
                allowedContentTypes: [.item, .data],
                allowsMultipleSelection: true
            ) { result in
                if case .success(let urls) = result {
                    urls.forEach { _ = $0.startAccessingSecurityScopedResource() }
                    importFromDevice(urls, move: moveFromDevice)
                }
            }
    }

    var sessionDialogs: some View {
        sessionPickers
            .alert("New folder", isPresented: $newFolderPresented) {
                TextField("Name", text: $namePromptValue)
                    .portTag("name_prompt")
                Button("Create") { mkdirInVolume(namePromptValue) }
                    .portTag("name_prompt_ok")
                Button("Cancel", role: .cancel) {}
            }
            .alert("Rename", isPresented: $renamePresented) {
                TextField("Name", text: $namePromptValue)
                Button("Rename") { renameSelected(namePromptValue) }
                Button("Cancel", role: .cancel) {}
            }
            .confirmationDialog(
                transferMove == true ? "Move to volume" : "Copy to volume",
                isPresented: Binding(
                    get: { transferMove != nil },
                    set: { if !$0 { transferMove = nil } }
                )
            ) {
                ForEach(Array(mountedVolumes.enumerated().filter { $0.offset != activeMountIndex }), id: \.element.id) { _, dest in
                    Button(dest.label) {
                        let move = transferMove == true
                        transferMove = nil
                        let files = entries.filter { selectedNames.contains($0.name) && !$0.isDir }
                        guard !files.isEmpty else {
                            status = "Tap one or more files, then Copy to volume or Move to volume."
                            return
                        }
                        transferBetweenVolumes(entries: files, dest: dest, move: move)
                    }
                }
                Button("Cancel", role: .cancel) { transferMove = nil }
            } message: {
                Text("Selected files land in the folder last opened on that volume.")
            }
            .sheet(isPresented: $showOpenAnother) {
                NavigationStack {
                    Form {
                        openVolumeForm(mountedSlot: true)
                    }
                    .navigationTitle("Open volume")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Cancel") { showOpenAnother = false }
                                .portTag("mounted_open_cancel")
                        }
                    }
                }
                .portTag("mounted_open_dialog")
            }
            .sheet(item: $previewItem) { item in
                InAppPreviewSheet(item: item) {
                    previewItem = nil
                    InAppPreview.wipe()
                }
            }
    }

    var sessionRoot: some View {
        sessionDialogs
            .onChange(of: scenePhase) { phase in
                if phase == .background && !holdingForPicker && !busy {
                    dismountOnLeave()
                }
                if phase == .active {
                    bumpIdle()
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.protectedDataWillBecomeUnavailableNotification)) { _ in
                if volumeHandle != nil {
                    closeOpenVolumes("Screen locked. Volume closed.")
                }
            }
            .simultaneousGesture(
                DragGesture(minimumDistance: 0).onChanged { _ in bumpIdle() }
            )
            .onChange(of: basketURLs) { _ in syncCreateSizeFromBasket() }
            .onChange(of: createHidden) { _ in syncCreateSizeFromBasket() }
            .onChange(of: createHiddenSizeAmount) { _ in syncCreateSizeFromBasket() }
            .onChange(of: createHiddenSizeUnit) { _ in syncCreateSizeFromBasket() }
            .onAppear {
                let split = SessionIdle.split(idleMinutes)
                idleAmountText = String(split.amount)
                idleUnit = split.unit
            }
            .onOpenURL { url in
                _ = url.startAccessingSecurityScopedResource()
                incomingFile = url
                containerURL = url
                status = "Received \(url.lastPathComponent). Any extension can be a volume. Open with the correct password, PIM, and keyfiles."
            }
    }

    @ViewBuilder
    var statusSection: some View {
        Section("Status") {
            HStack(alignment: .top, spacing: 10) {
                Capsule()
                    .fill(statusTone)
                    .frame(width: 4, height: 36)
                Text(status)
                    .portTag("status")
                    .accessibilityValue(status)
            }
        }
    }

    @ViewBuilder
    var incomingSection: some View {
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
            }
        }
    }

    @ViewBuilder
    var inFrontSection: some View {
        Section("In front of you") {
            Text(inFrontLabel)
                .font(.caption)
            Button("Share encrypted") { shareInFrontEncrypted() }
            Button("Share decrypted") { shareInFrontDecrypted() }
                .disabled(!canShareDecrypted)
        }
    }

    @ViewBuilder
    var keyfileRows: some View {
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
        .portTag("add_keyfiles")
        TextField("Keyfile name (any extension)", text: $keyfileGenName)
            .portTag("create_keyfile_name")
        TextField("How many (1–8)", text: $keyfileGenCount)
            .keyboardType(.numberPad)
            .portTag("create_keyfile_count")
        Button("Generate keyfile and add") { generateKeyfile(nested: false) }
            .portTag("create_generate_keyfile")
    }

    var inFrontLabel: String {
        let selected = entries.filter { selectedNames.contains($0.name) && !$0.isDir }
        if !selected.isEmpty {
            return selected.map(\.name).joined(separator: ", ") + " — decrypted from the open volume"
        }
        if let live = lastPlain.first, FileManager.default.fileExists(atPath: live.path) {
            return live.lastPathComponent + " — decrypted copy"
        }
        if let containerURL {
            return "\(containerURL.lastPathComponent) — encrypted file in front"
        }
        if let incoming = incomingFile {
            return "\(incoming.lastPathComponent) — encrypted file in front"
        }
        return "Nothing in front. Select a container or file, then share from here."
    }

    var canShareDecrypted: Bool {
        entries.contains { selectedNames.contains($0.name) && !$0.isDir }
            || lastPlain.contains { FileManager.default.fileExists(atPath: $0.path) }
    }

    var statusTone: Color {
        let lower = status.lowercased()
        if ["fail", "could not", "wrong", "empty"].contains(where: { lower.contains($0) }) {
            return .red
        }
        if ["opened", "copied", "created", "moved", "wiped", "complete", "saved", "renamed", "deleted"].contains(where: { lower.contains($0) }) {
            return Color(red: 0.15, green: 0.48, blue: 0.25)
        }
        return Color(red: 10 / 255, green: 108 / 255, blue: 206 / 255)
    }

    func collectCreateEntropy(_ value: DragGesture.Value) {
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

    func beginWork(_ title: String, updateStatus: Bool = true) {
        VcMobileBridge.resetProgress()
        workTitle = title
        workPercent = -1
        busy = true
        if updateStatus {
            status = title
        }
    }

    func endWork() {
        busy = false
        workTitle = ""
        workPercent = -1
        VcMobileBridge.resetProgress()
    }

    func shareInFrontEncrypted() {
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

    func shareInFrontDecrypted() {
        let selected = entries.filter { selectedNames.contains($0.name) && !$0.isDir }
        if volumeHandle != nil, !selected.isEmpty {
            shareVaultFiles(selected)
            return
        }
        if let live = lastPlain.first, FileManager.default.fileExists(atPath: live.path) {
            status = "Sharing decrypted \(live.lastPathComponent)."
            SystemShare.present(items: lastPlain.filter { FileManager.default.fileExists(atPath: $0.path) })
            return
        }
        status = "Tap files in an open volume, then Share decrypted."
    }

    func createVolume() {
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
        if let shortage = AppStorageSpace.shortageMessage(payload: bytes) {
            status = shortage
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

    func startCreateVolume(sizeBytes: UInt64, hiddenBytes: UInt64) {
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
                    incomingFile = nil
                    containerURL = dest
                    if let saved {
                        wipeCreateSecrets()
                        status = "Saved \(saved.lastPathComponent). Type the volume password and Open volume. Create secrets were wiped."
                    }
                }
            }
        }
    }

    func ingestPickedContainer(_ url: URL) {
        incomingFile = nil
        holdLock = false
        _ = url.startAccessingSecurityScopedResource()
        if isTemporaryContainer(url), FileManager.default.isReadableFile(atPath: url.path) {
            containerURL = url
            status = "Selected \(url.lastPathComponent)"
            return
        }
        if let copied = copyScopedFile(url), FileManager.default.isReadableFile(atPath: copied.path) {
            containerURL = copied
            status = "Selected \(url.lastPathComponent)"
            return
        }
        containerURL = nil
        status = AppStorageSpace.lastError.isEmpty ? AppStorageSpace.unreadable : AppStorageSpace.lastError
    }

    func ensureContainerURL() -> URL? {
        if let url = containerURL, FileManager.default.isReadableFile(atPath: url.path) {
            return url
        }
        guard let url = containerURL, let copied = copyScopedFile(url),
              FileManager.default.isReadableFile(atPath: copied.path) else {
            return nil
        }
        containerURL = copied
        return copied
    }

    func openVolume() {
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

    func startOpenVolume() {
        guard let url = ensureContainerURL() else {
            status = AppStorageSpace.lastError.isEmpty
                ? "Could not read the container file. Pick it again from Files."
                : AppStorageSpace.lastError
            return
        }
        let path = url.path
        let text = useTextPassword ? password : ""
        if text.isEmpty && keyfileURLs.isEmpty {
            status = "Type the volume password, or add a keyfile."
            return
        }
        if mountedVolumes.count >= mountSlots {
            status = "This session already has 8 volumes mounted. Dismount one first."
            return
        }
        if mountedVolumes.contains(where: { $0.url.path == path }) {
            status = "That container is already mounted. Switch to it on the Mounted tab."
            return
        }
        beginWork("Opening volume…")
        showOpenAnother = false
        let backup = useBackupHeader
        let readOnly = readOnlyOpen
        let tcMode = trueCryptMode
        let pimText = pim
        let pimValue = Int32(pimText) ?? 0
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
                persistActiveMount()
                switch VcMobileBridge.listDir(handle, path: "/") {
                case .failure(let err):
                    VcMobileBridge.close(handle)
                    status = listErrorMessage(err.rawValue)
                case .success(let listed):
                    let truncated = listed.contains { $0.name == "!truncated!" }
                    let files = listed.filter { $0.name != "!truncated!" }
                    mountedVolumes.append(
                        MountedVolume(
                            handle: handle,
                            url: url,
                            label: url.lastPathComponent,
                            dirPath: "",
                            entries: files,
                            truncated: truncated,
                            readOnly: readOnly
                        )
                    )
                    activeMountIndex = mountedVolumes.count - 1
                    volumeHandle = handle
                    dirPath = ""
                    entries = files
                    listTruncated = truncated
                    selectedNames = []
                    headerKeyfileURLs = keys
                    rememberUnlock(text, pimText)
                    wipeUnlockForm()
                    selectedTab = 2
                    bumpIdle()
                    var msg = "Mounted in this app. Size \(VcMobileBridge.size(handle)) bytes. Slots are on the Mounted tab. Tap Open on a folder, or select files. Copy to volume moves selected files into another mounted container."
                    if readOnly {
                        msg = "Read-only. Writes are refused. " + msg
                    }
                    if mountedVolumes.count > 1 {
                        msg = "\(mountedVolumes.count) volumes mounted. " + msg
                    }
                    if protect { msg = "Hidden volume is being protected against damage. " + msg }
                    if truncated { msg += " Listing truncated at \(VC_LIST_UI_MAX) entries. Tap Load more." }
                    status = msg
                }
            }
        }
    }

    func wipeFile(_ url: URL) {
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

    func wipeSessionFiles() {
        lastPlain.forEach { wipeFile($0) }
        InAppPreview.wipe()
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
                    name.hasPrefix("xfer-") ||
                    name == "vcport-biometric.key" {
                    wipeFile(url)
                }
            }
        }
    }

    func closeVolume() {
        for vol in mountedVolumes {
            VcMobileBridge.close(vol.handle)
        }
        mountedVolumes = []
        activeMountIndex = 0
        volumeHandle = nil
        entries = []
        dirPath = ""
        listTruncated = false
        selectedNames = []
        previewItem = nil
        InAppPreview.wipe()
    }

    func persistActiveMount() {
        guard activeMountIndex < mountedVolumes.count else { return }
        mountedVolumes[activeMountIndex].dirPath = dirPath
        mountedVolumes[activeMountIndex].entries = entries
        mountedVolumes[activeMountIndex].truncated = listTruncated
    }

    func selectMount(_ index: Int) {
        guard index < mountedVolumes.count else { return }
        showOpenAnother = false
        persistActiveMount()
        activeMountIndex = index
        let v = mountedVolumes[index]
        volumeHandle = v.handle
        dirPath = v.dirPath
        entries = v.entries
        listTruncated = v.truncated
        selectedNames = []
    }

    func dismountMountedAt(_ index: Int) {
        persistActiveMount()
        guard index < mountedVolumes.count else { return }
        let victim = mountedVolumes.remove(at: index)
        VcMobileBridge.close(victim.handle)
        if mountedVolumes.isEmpty {
            activeMountIndex = 0
            volumeHandle = nil
            entries = []
            dirPath = ""
            listTruncated = false
            selectedNames = []
            status = "Dismounted \(victim.label)."
            return
        }
        let next: Int
        if index < activeMountIndex {
            next = max(0, activeMountIndex - 1)
        } else if index == activeMountIndex {
            next = min(activeMountIndex, mountedVolumes.count - 1)
        } else {
            next = activeMountIndex
        }
        activeMountIndex = next
        let v = mountedVolumes[next]
        volumeHandle = v.handle
        dirPath = v.dirPath
        entries = v.entries
        listTruncated = v.truncated
        selectedNames = []
        status = "Dismounted \(victim.label). \(mountedVolumes.count) still mounted."
    }

    func transferBetweenVolumes(entries files: [VaultEntry], dest: MountedVolume, move: Bool) {
        guard let src = volumeHandle else {
            status = "Open a volume first."
            return
        }
        let toCopy = files.filter { !$0.isDir }
        guard !toCopy.isEmpty else {
            status = "Tap one or more files, then Copy to volume or Move to volume."
            return
        }
        persistActiveMount()
        let label = dest.label
        beginWork(
            toCopy.count == 1
                ? (move ? "Moving \(toCopy[0].name) to \(label)…" : "Copying \(toCopy[0].name) to \(label)…")
                : (move ? "Moving \(toCopy.count) files to \(label)…" : "Copying \(toCopy.count) files to \(label)…")
        )
        let srcDir = dirPath
        DispatchQueue.global(qos: .userInitiated).async {
            var copied = 0
            var moved = 0
            var lastError: String?
            for entry in toCopy {
                let temp = FileManager.default.temporaryDirectory
                    .appendingPathComponent("xfer-\(Int(Date().timeIntervalSince1970 * 1000))-\(entry.name.replacingOccurrences(of: "/", with: "_"))")
                try? FileManager.default.removeItem(at: temp)
                let srcPath = joinDir(srcDir, entry.name)
                let rcExport = VcMobileBridge.exportFile(src, name: srcPath, dest: temp.path)
                if rcExport != 0 {
                    lastError = extractErrorMessage(entry.name, rcExport)
                    wipeFile(temp)
                    continue
                }
                let destDir = dest.dirPath.isEmpty ? "/" : dest.dirPath
                let rcImport = VcMobileBridge.importFile(dest.handle, destDir: destDir, src: temp.path, destName: entry.name)
                wipeFile(temp)
                if rcImport != 0 {
                    lastError = importErrorMessage(entry.name, rcImport, handle: dest.handle)
                    continue
                }
                copied += 1
                if move {
                    if VcMobileBridge.deleteFile(src, path: srcPath) == 0 {
                        moved += 1
                    }
                }
            }
            DispatchQueue.main.async {
                endWork()
                if let lastError, copied == 0 {
                    status = lastError
                } else if move && moved < copied {
                    status = "Copied \(copied) file(s) into \(label). Could not delete \(copied - moved) from the source volume."
                } else if move && copied == toCopy.count {
                    status = "Moved \(copied) file(s) into \(label)."
                    selectedNames.subtract(toCopy.map(\.name))
                } else if copied == toCopy.count {
                    status = "Copied \(copied) file(s) into \(label)."
                } else {
                    status = "Copied \(copied) of \(toCopy.count) file(s) into \(label). \(lastError ?? "")"
                }
                reloadDir(quiet: true)
                persistActiveMount()
                refreshMountedListing(dest)
            }
        }
    }

    func refreshMountedListing(_ dest: MountedVolume) {
        let path = dest.dirPath.isEmpty ? "/" : dest.dirPath
        switch VcMobileBridge.listDir(dest.handle, path: path) {
        case .failure:
            break
        case .success(let listed):
            let truncated = listed.contains { $0.name == "!truncated!" }
            let files = listed.filter { $0.name != "!truncated!" }
            if let i = mountedVolumes.firstIndex(where: { $0.id == dest.id }) {
                mountedVolumes[i].entries = files
                mountedVolumes[i].truncated = truncated
            }
        }
    }

    /// Home / app switcher: close a mounted volume. Keep the Create wizard
    /// (generated passwords, nested volume options, basket) so Copy once
    /// keyfile) so Copy once can be pasted into Notes and creation can
    /// continue. Dismount / Panic still call lockSession().
    func dismountOnLeave() {
        let wasOpen = volumeHandle != nil
        closeVolume()
        password = ""
        hiddenProtectPassword = ""
        newPassword = ""
        pim = "0"
        hiddenProtectPim = "0"
        newPim = "0"
        lastPlain = []
        forgetUnlock()
        if wasOpen && !status.hasPrefix("Panic") {
            status = "Dismounted. Create form kept. Dismount or Panic wipe also clears the generated password."
        }
    }

    func isTemporaryContainer(_ url: URL) -> Bool {
        let path = url.path
        return path.contains("/tmp/") ||
            path.contains("/Caches/") ||
            path.contains("/TemporaryItems/") ||
            path.contains(FileManager.default.temporaryDirectory.path)
    }

    func fileSize(_ url: URL) -> Int64 {
        (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize).map { Int64($0) } ?? 0
    }

    func parseSizeBytes(amount: String, unit: SizeUnit) -> UInt64? {
        let digits = amount.filter(\.isNumber)
        guard let n = UInt64(digits), n > 0 else { return nil }
        return SizeUnit.toBytes(amount: n, unit: unit)
    }

    func syncCreateSizeFromBasket() {
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

    func volumeBytesForBasket(asked: UInt64, urls: [URL], hiddenBytes: UInt64) -> UInt64 {
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

    func basketSummary(_ urls: [URL]) -> String {
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

    func shortHex(_ hex: String) -> String {
        guard hex.count >= 12 else { return hex }
        return "\(hex.prefix(8))…\(hex.suffix(4))"
    }

    func shortHash(_ hex: String?) -> String {
        guard let hex else { return "SHA-256 …" }
        return "SHA-256 \(shortHex(hex))"
    }

    func sha256Path(_ path: String) -> String? {
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

    func sha256Streaming(_ url: URL, index: Int, totalFiles: Int, name: String) -> String? {
        guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? handle.close() }
        var hasher = SHA256()
        let total = max((try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 1, 1)
        var hashed = 0
        while true {
            let data = handle.readData(ofLength: 64 * 1024)
            if data.isEmpty { break }
            hasher.update(data: data)
            hashed += data.count
            let filePct = ((index * 100) + ((hashed * 100) / total)) / max(totalFiles, 1)
            VcMobileBridge.setProgress(Int32(min(max(filePct, 0), 99)), phase: "Hashing \(index + 1) of \(totalFiles): \(name)")
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    func sha256File(_ url: URL) -> String? {
        let accessed = url.startAccessingSecurityScopedResource()
        defer {
            if accessed { url.stopAccessingSecurityScopedResource() }
        }
        return sha256Path(url.path)
    }

    func writeBasketProof(_ handle: OpaquePointer, urls: [URL], hashes: [String: String]) {
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

    func uniqueDestName(_ raw: String, used: inout Set<String>) -> String {
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

    func importURLIntoVolume(_ handle: OpaquePointer, _ url: URL, used: inout Set<String>) -> String? {
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

    /// After a successful Files save: forget create/open secrets. Keep the local
    /// container URL so Open volume still works after they re-type the password.
    func wipeCreateSecrets() {
        createPassword = ""
        createHiddenPassword = ""
        hiddenProtectPassword = ""
        password = ""
        createPim = "0"
        createHiddenPim = "0"
        hiddenProtectPim = "0"
        pim = "0"
        newPim = "0"
        newPassword = ""
        let keys = keyfileURLs + hiddenKeyfileURLs + headerKeyfileURLs
        keyfileURLs = []
        headerKeyfileURLs = []
        hiddenKeyfileURLs = []
        let tmp = FileManager.default.temporaryDirectory.path
        for url in keys where url.path.hasPrefix(tmp) {
            wipeFile(url)
        }
        forgetUnlock()
    }

    func clearMountOptions() {
        useBackupHeader = false
        readOnlyOpen = false
        trueCryptMode = false
        protectHidden = false
        headerKeyfileURLs = []
    }

    func lockSession() {
        closeVolume()
        password = ""
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
        headerKeyfileURLs = []
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
        clearMountOptions()
        wipeSessionFiles()
        forgetUnlock()
        hashResult = ""
        endWork()
        if !status.hasPrefix("Panic") {
            status = "Dismounted. Passwords, keyfiles in memory, and decrypted copies wiped. Ciphertext stays."
        }
    }

    /// Full closer: idle, screen-lock, Tools wipe-cache, Dismount.
    /// Home / Recents uses dismountOnLeave so Create can continue. Panic adds
    /// Hardening.panic after lock. Do not grow a fifth path.
    func closeOpenVolumes(_ reason: String) {
        if volumeHandle != nil {
            beginWork(reason)
            VcMobileBridge.setProgress(100, phase: reason)
        }
        lockSession()
        if !status.hasPrefix("Panic") {
            status = reason
        }
    }

    func panicWipe() {
        closeVolume()
        password = ""
        holdLock = false
        createPassword = ""
        createHiddenPassword = ""
        hiddenProtectPassword = ""
        newPassword = ""
        pim = "0"
        newPim = "0"
        createPim = "0"
        hiddenProtectPim = "0"
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
        clearMountOptions()
        forgetUnlock()
    }

    func startInAppPreview() {
        guard FossConfig.enableInAppPreview else {
            status = "In-app preview is off in this build."
            return
        }
        guard let handle = volumeHandle else {
            status = "Open a volume first."
            return
        }
        let files = entries.filter { selectedNames.contains($0.name) && !$0.isDir }
        guard files.count == 1, let entry = files.first else {
            status = "Tap one file, then View in app. Preview stays inside VC Port (not VLC or Files)."
            return
        }
        beginWork("Opening \(entry.name) in this app…")
        DispatchQueue.global(qos: .userInitiated).async {
            let dest = InAppPreview.materialize(handle: handle, volumePath: joinDir(dirPath, entry.name), name: entry.name)
            DispatchQueue.main.async {
                endWork()
                guard let dest else {
                    status = "Could not preview \(entry.name) in-app. File may be over 64 MiB, or export failed."
                    return
                }
                previewItem = InAppPreviewItem(url: dest, name: entry.name)
                status = "Viewing \(entry.name) in this app. Not VLC or Files."
            }
        }
    }

    func shareVaultFiles(_ files: [VaultEntry]) {
        guard let handle = volumeHandle else {
            status = "Open a volume first."
            return
        }
        let toShare = files.filter { !$0.isDir }
        if toShare.isEmpty {
            status = "Tap one or more files in the volume, then Share decrypted."
            return
        }
        beginWork("Preparing \(toShare.count) decrypted file(s)…")
        DispatchQueue.global(qos: .userInitiated).async {
            var dests: [URL] = []
            for entry in toShare {
                let dest = FileManager.default.temporaryDirectory
                    .appendingPathComponent(entry.name.replacingOccurrences(of: "/", with: "_"))
                try? FileManager.default.removeItem(at: dest)
                let rc = VcMobileBridge.exportFile(handle, name: joinDir(dirPath, entry.name), dest: dest.path)
                if rc != 0 {
                    DispatchQueue.main.async {
                        endWork()
                        status = extractErrorMessage(entry.name, rc)
                    }
                    return
                }
                dests.append(dest)
            }
            DispatchQueue.main.async {
                endWork()
                lastPlain = dests
                status = "Share \(dests.map(\.lastPathComponent).joined(separator: ", ")) with WhatsApp, Mail, Drive, or any app."
                SystemShare.present(items: dests)
            }
        }
    }

    func importFromDevice(_ urls: [URL], move: Bool) {
        guard let handle = volumeHandle else {
            status = "Open a volume first."
            return
        }
        if urls.isEmpty { return }
        let verb = move ? "Moving" : "Copying"
        beginWork(urls.count == 1 ? "\(verb) from device…" : "\(verb) \(urls.count) files from device…")
        let destDir = dirPath.isEmpty ? "/" : dirPath
        var used = Set(entries.filter { !$0.isDir }.map(\.name))
        DispatchQueue.global(qos: .userInitiated).async {
            var copied = 0
            var moved = 0
            var lastError: String?
            for url in urls {
                let accessed = url.startAccessingSecurityScopedResource()
                defer {
                    if accessed { url.stopAccessingSecurityScopedResource() }
                }
                let name = uniqueDestName(url.lastPathComponent, used: &used)
                var temp: URL?
                let srcPath: String
                if url.isFileURL, FileManager.default.isReadableFile(atPath: url.path) {
                    srcPath = url.path
                } else if let copiedUrl = copyScopedFile(url) {
                    temp = copiedUrl
                    srcPath = copiedUrl.path
                } else {
                    lastError = "Could not read \(url.lastPathComponent). Pick it again from Files."
                    continue
                }
                let rc = VcMobileBridge.importFile(handle, destDir: destDir, src: srcPath, destName: name)
                if let temp {
                    try? FileManager.default.removeItem(at: temp)
                }
                if rc != 0 {
                    lastError = importErrorMessage(name, rc, handle: handle)
                    continue
                }
                copied += 1
                if move {
                    do {
                        try FileManager.default.removeItem(at: url)
                        moved += 1
                    } catch {
                    }
                }
            }
            DispatchQueue.main.async {
                endWork()
                if lastError != nil && copied == 0 {
                    status = lastError ?? "Could not copy that file into the volume."
                } else if move && moved < copied {
                    status = "Copied \(copied) file(s) into the volume. Could not delete the original; remove it in Files if you meant a move."
                } else if move && copied == urls.count {
                    status = "Moved \(copied) file(s) into the volume."
                } else if copied == urls.count {
                    status = "Copied \(copied) file(s) from the device into this folder."
                } else {
                    status = "Copied \(copied) of \(urls.count) file(s) into the volume. \(lastError ?? "")"
                }
                if copied > 0 { reloadDir() }
            }
        }
    }

    func copySelectedToDevice(move: Bool) {
        guard volumeHandle != nil else {
            status = "Open a volume first."
            return
        }
        let files = entries.filter { selectedNames.contains($0.name) && !$0.isDir }
        guard !files.isEmpty else {
            status = move
                ? "Tap one or more files in the volume, then Move to device."
                : "Tap one or more files in the volume, then Copy to device."
            return
        }
        exportToDevice(files, move: move)
    }

    func exportToDevice(_ files: [VaultEntry], move: Bool) {
        guard let handle = volumeHandle else {
            status = "Open a volume first."
            return
        }
        let verb = move ? "Moving" : "Copying"
        beginWork(files.count == 1 ? "\(verb) \(files[0].name) to device…" : "\(verb) \(files.count) files to device…")
        DispatchQueue.global(qos: .userInitiated).async {
            var dests: [(VaultEntry, URL)] = []
            for entry in files {
                let dest = FileManager.default.temporaryDirectory
                    .appendingPathComponent(entry.name.replacingOccurrences(of: "/", with: "_"))
                try? FileManager.default.removeItem(at: dest)
                let rc = VcMobileBridge.exportFile(handle, name: joinDir(dirPath, entry.name), dest: dest.path)
                if rc != 0 {
                    DispatchQueue.main.async {
                        endWork()
                        status = extractErrorMessage(entry.name, rc)
                    }
                    return
                }
                dests.append((entry, dest))
            }
            DispatchQueue.main.async {
                endWork()
                SystemFiles.exportCopy(urls: dests.map(\.1)) { saved in
                    if saved == nil {
                        status = files.count == 1
                            ? "Cancelled saving \(files[0].name)."
                            : "Cancelled saving \(files.count) files."
                        return
                    }
                    if move {
                        var deleted = 0
                        for (entry, _) in dests {
                            if VcMobileBridge.deleteFile(handle, path: joinDir(dirPath, entry.name)) == 0 {
                                selectedNames.remove(entry.name)
                                deleted += 1
                            }
                        }
                        if deleted == dests.count {
                            status = "Moved \(dests.count) file(s) to the device."
                            reloadDir()
                        } else {
                            status = "Copied \(dests.count) file(s) to the device, but could not remove \(dests.count - deleted) from the volume."
                            if deleted > 0 { reloadDir() }
                        }
                    } else {
                        status = dests.count == 1
                            ? "Copied \(dests[0].0.name) to the device."
                            : "Copied \(dests.count) files to the device."
                    }
                }
            }
        }
    }

    func reloadDir(append: Bool = false, quiet: Bool = false) {
        guard let handle = volumeHandle else { return }
        let path = dirPath.isEmpty ? "/" : dirPath
        let offset = append ? Int32(entries.count) : 0
        switch VcMobileBridge.listDir(handle, path: path, offset: offset) {
        case .failure(let err):
            if !quiet { status = listErrorMessage(err.rawValue) }
        case .success(let listed):
            let truncated = listed.contains { $0.name == "!truncated!" }
            let files = listed.filter { $0.name != "!truncated!" }
            entries = append ? entries + files : files
            listTruncated = truncated
            persistActiveMount()
            if truncated { status = "Folder listing truncated at \(VC_LIST_UI_MAX) entries. Tap Load more." }
        }
    }

    func joinDir(_ dir: String, _ name: String) -> String {
        dir.isEmpty ? name : "\(dir)/\(name)"
    }

    func parentDir(_ dir: String) -> String {
        guard let slash = dir.lastIndex(of: "/") else { return "" }
        return String(dir[..<slash])
    }

    func openErrorMessage(_ code: Int32) -> String {
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

    func listErrorMessage(_ code: Int32) -> String {
        switch code {
        case -6: return "Opened the volume, but the filesystem is NTFS or otherwise unsupported. FAT and exFAT work here."
        case -4: return "Could not list that folder path."
        case -5: return "Not enough memory to list the folder."
        case -1: return "Could not read the folder from the volume."
        default: return "Could not list files (code \(code))."
        }
    }

    func extractErrorMessage(_ name: String, _ rc: Int32) -> String {
        switch rc {
        case -6: return "Could not extract \(name). NTFS/ext are unsupported; FAT and exFAT work."
        case -4: return "Could not extract \(name). Bad path."
        case -5: return "Could not extract \(name). Not enough memory."
        case -1: return "Could not extract \(name). Read failed."
        case -2: return "Could not extract \(name). Wrong password or header."
        default: return "Could not extract \(name) (code \(rc))."
        }
    }

    func importErrorMessage(_ name: String, _ rc: Int32, handle: OpaquePointer? = nil) -> String {
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

    func headerErrorMessage(_ rc: Int32) -> String {
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

    func unlockPassword() -> String {
        if !useTextPassword { return "" }
        return password.isEmpty ? lastUnlockPassword : password
    }

    func unlockPimText() -> String {
        if password.isEmpty && !lastUnlockPassword.isEmpty {
            return lastUnlockPim
        }
        return pim
    }

    func rememberUnlock(_ password: String, _ pimText: String) {
        lastUnlockPassword = password
        lastUnlockPim = pimText.isEmpty ? "0" : pimText
    }

    func forgetUnlock() {
        lastUnlockPassword = ""
        lastUnlockPim = "0"
    }

    /// Clear Volume-tab unlock fields after a successful mount. Tools still uses lastUnlockPassword.
    func wipeUnlockForm() {
        password = ""
        pim = "0"
        hiddenProtectPassword = ""
        hiddenProtectPim = "0"
        useBackupHeader = false
        readOnlyOpen = false
        trueCryptMode = false
        protectHidden = false
    }

    func currentUnlockPaths() -> (password: String, pim: String, keyfiles: [String], temps: [URL])? {
        guard ensureContainerURL() != nil else {
            status = AppStorageSpace.lastError.isEmpty
                ? "Could not read the container file. Pick it again from Files."
                : AppStorageSpace.lastError
            return nil
        }
        let text = unlockPassword()
        if text.isEmpty && keyfileURLs.isEmpty {
            status = "Enter the current password or keyfiles above."
            return nil
        }
        var temps: [URL] = []
        var paths = keyfileURLs.map(\.path)
        return (text, unlockPimText(), paths, temps)
    }

    func changeVolumePassword() {
        runChangeHeader(
            newPassword: newPassword,
            newKdf: "",
            keepKeyfiles: true,
            success: "Changed volume password. Open with the new password and the same keyfiles."
        )
    }

    func setHeaderKdf() {
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

    func applyKeyfilesToVolume() {
        runChangeHeader(
            newPassword: "",
            newKdf: "",
            keepKeyfiles: true,
            applySessionKeyfiles: true,
            success: "Applied the current keyfile list (Add/Remove keyfiles) to the volume header."
        )
    }

    func removeAllKeyfiles() {
        runChangeHeader(
            newPassword: "",
            newKdf: "",
            keepKeyfiles: false,
            success: "Removed all keyfiles from volume. Open with the password only."
        )
    }

    func runChangeHeader(newPassword: String, newKdf: String, keepKeyfiles: Bool, applySessionKeyfiles: Bool = false, success: String) {
        guard let unlock = currentUnlockPaths(), let path = containerURL?.path else { return }
        let unlockKeys = headerKeyfileURLs.isEmpty ? unlock.keyfiles : headerKeyfileURLs.map(\.path)
        let text = unlock.password
        if text.isEmpty && unlockKeys.isEmpty {
            status = "Enter the current password or keyfiles above."
            return
        }
        let nextPassword = newPassword.isEmpty ? text : newPassword
        if !keepKeyfiles && nextPassword.isEmpty {
            status = "Removing all keyfiles needs a text password, or the volume cannot be opened."
            return
        }
        let pimUsed = unlock.pim
        let nextPim: Int32 = {
            if newPassword.isEmpty && (newPim.isEmpty || newPim == "0") {
                return Int32(pimUsed) ?? 0
            }
            return Int32(newPim) ?? 0
        }()
        let sessionKeys = unlock.keyfiles
        closeVolume()
        beginWork("Rewriting volume header…")
        DispatchQueue.global(qos: .userInitiated).async {
            let newKeys: [String]
            if !keepKeyfiles {
                newKeys = []
            } else if applySessionKeyfiles {
                newKeys = sessionKeys
            } else {
                newKeys = unlockKeys
            }
            let rc = VcMobileBridge.changeHeader(
                path: path,
                password: text,
                pim: Int32(pimUsed) ?? 0,
                keyfiles: unlockKeys,
                backup: useBackupHeader,
                newPassword: newPassword,
                newPim: nextPim,
                newKdf: newKdf,
                newKeyfiles: newKeys
            )
            unlock.temps.forEach { try? FileManager.default.removeItem(at: $0) }
            DispatchQueue.main.async {
                endWork()
                if rc == 0 {
                    if !keepKeyfiles {
                        headerKeyfileURLs = []
                    } else if applySessionKeyfiles {
                        headerKeyfileURLs = keyfileURLs
                    } else {
                        headerKeyfileURLs = headerKeyfileURLs.isEmpty ? keyfileURLs : headerKeyfileURLs
                    }
                    rememberUnlock(nextPassword, String(nextPim))
                    wipeUnlockForm()
                }
                status = rc == 0 ? success : headerErrorMessage(rc)
            }
        }
    }

    func backupVolumeHeader() {
        guard let unlock = currentUnlockPaths(), let path = containerURL?.path else { return }
        closeVolume()
        let dest = FileManager.default.temporaryDirectory.appendingPathComponent("volume-header.bak")
        let rc = VcMobileBridge.backupHeaders(
            volumePath: path,
            backupPath: dest.path,
            password: unlock.password,
            pim: Int32(unlock.pim) ?? 0,
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

    func restoreVolumeHeader(_ backup: URL) {
        guard let unlock = currentUnlockPaths(), let path = containerURL?.path else { return }
        closeVolume()
        let rc = VcMobileBridge.restoreHeaders(
            volumePath: path,
            backupPath: backup.path,
            password: unlock.password,
            pim: Int32(unlock.pim) ?? 0,
            keyfiles: unlock.keyfiles
        )
        unlock.temps.forEach { try? FileManager.default.removeItem(at: $0) }
        status = rc == 0
            ? "Restored volume header. Open with the password that was current when the backup was made."
            : headerErrorMessage(rc)
    }

    func restoreEmbeddedHeader() {
        guard let unlock = currentUnlockPaths(), let path = containerURL?.path else { return }
        closeVolume()
        let rc = VcMobileBridge.restoreHeaders(
            volumePath: path,
            backupPath: "",
            password: unlock.password,
            pim: Int32(unlock.pim) ?? 0,
            keyfiles: unlock.keyfiles
        )
        unlock.temps.forEach { try? FileManager.default.removeItem(at: $0) }
        status = rc == 0
            ? "Restored from embedded backup header. Open with the same password, PIM, and keyfiles."
            : headerErrorMessage(rc)
    }

    func mkdirInVolume(_ name: String) {
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
            status = importErrorMessage(trimmed, rc, handle: handle)
            return
        }
        status = "Created folder \(trimmed)."
        reloadDir()
    }

    func renameSelected(_ newName: String) {
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
            status = importErrorMessage(old, rc, handle: handle)
            return
        }
        selectedNames = [trimmed]
        status = "Renamed \(old) to \(trimmed)."
        reloadDir()
    }

    func deleteSelected() {
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
            status = entry.isDir && rc == -3 ? "Folder \(path) is not empty." : importErrorMessage(entry.name, rc, handle: handle)
            return
        }
        selectedNames.remove(name)
        status = "Deleted \(entry.name)."
        reloadDir()
    }

    func showEntryProperties() {
        guard let name = selectedNames.first,
              let entry = entries.first(where: { $0.name == name }) else {
            status = entries.isEmpty ? "This folder is empty." : "Tap a file or folder, then Properties."
            return
        }
        let kind = entry.isDir ? "Folder" : "File"
        let size = entry.isDir ? "" : ", \(byteCount(entry.size))"
        status = "\(kind) \(entry.name)\(size), modified \(formatFatStamp(entry.dosDate, entry.dosTime)). Browsed in this app; this is not a mounted drive."
    }

    func bumpIdle() {
        idleTask?.cancel()
        guard idleMinutes > 0, volumeHandle != nil else { return }
        let mins = idleMinutes
        idleTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(mins) * 60 * 1_000_000_000)
            guard !Task.isCancelled, volumeHandle != nil else { return }
            closeOpenVolumes("Idle timeout. Volume closed.")
        }
    }

    func applyIdleFromFields() {
        let n = Int(idleAmountText.filter(\.isNumber)) ?? 0
        idleMinutes = SessionIdle.toMinutes(amount: n, unit: idleUnit)
        bumpIdle()
    }

    func hashSelectedInVolume() {
        let files = entries.filter { selectedNames.contains($0.name) && !$0.isDir }
        guard let handle = volumeHandle, !files.isEmpty else {
            status = "Tap a file, then SHA-256 in volume."
            return
        }
        beginWork("Hashing \(files.count) file(s) inside the volume…")
        DispatchQueue.global(qos: .userInitiated).async {
            var lines: [String] = []
            for (index, entry) in files.enumerated() {
                let pct = Int32((index * 100) / files.count)
                VcMobileBridge.setProgress(pct, phase: "Hashing \(index + 1) of \(files.count): \(entry.name)")
                let dest = FileManager.default.temporaryDirectory.appendingPathComponent("hash-\(entry.name)")
                try? FileManager.default.removeItem(at: dest)
                let rc = VcMobileBridge.exportFile(handle, name: joinDir(dirPath, entry.name), dest: dest.path)
                if rc != 0 {
                    lines.append("\(entry.name): hash failed")
                    VcMobileBridge.setProgress(Int32(((index + 1) * 100) / files.count), phase: "Hash failed: \(entry.name)")
                    continue
                }
                if let hex = sha256Streaming(dest, index: index, totalFiles: files.count, name: entry.name) {
                    lines.append("\(entry.name): \(hex)")
                    VcMobileBridge.setProgress(Int32(((index + 1) * 100) / files.count), phase: "Hashed \(entry.name)")
                } else {
                    lines.append("\(entry.name): hash failed")
                }
                try? FileManager.default.removeItem(at: dest)
            }
            DispatchQueue.main.async {
                let summary = "SHA-256 in volume (temp wiped): " + lines.joined(separator: " · ")
                hashResult = summary
                endWork()
                status = summary
            }
        }
    }

    func wipeFreeSpace() {
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
                    if VcMobileBridge.protectionTriggered(handle) {
                        status = "Hidden volume protection triggered. The outer volume is now write-protected until you dismount."
                    } else {
                        status = "Could not wipe free space (code \(rc)). Read-only volumes refuse this."
                    }
                } else {
                    status = "Wiped unused FAT clusters. Deleted file contents in free space are overwritten."
                    reloadDir()
                }
            }
        }
    }

    func formatFatStamp(_ date: UInt16, _ time: UInt16) -> String {
        if date == 0 { return "unknown" }
        let year = 1980 + Int(date >> 9)
        let month = Int((date >> 5) & 0xF)
        let day = Int(date & 0x1F)
        let hour = Int(time >> 11)
        let min = Int((time >> 5) & 0x3F)
        return String(format: "%04d-%02d-%02d %02d:%02d UTC", year, month, day, hour, min)
    }

    func showVolumeProperties() {
        guard let handle = volumeHandle else {
            status = "Open the volume first for Volume properties."
            return
        }
        status = VcMobileBridge.volumeInfo(handle) ?? "Could not read volume properties."
    }

    func generateKeyfile(nested: Bool) {
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

    func runBenchmark() {
        beginWork("Running encryption benchmark…")
        DispatchQueue.global(qos: .userInitiated).async {
            let result = VcMobileBridge.benchmark()
            DispatchQueue.main.async {
                endWork()
                status = result
            }
        }
    }

    func runTestVectors() {
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

    func copyScopedFile(_ url: URL) -> URL? {
        AppStorageSpace.lastError = ""
        let accessed = url.startAccessingSecurityScopedResource()
        defer {
            if accessed { url.stopAccessingSecurityScopedResource() }
        }
        let payload = AppStorageSpace.fileSize(url)
        if let shortage = AppStorageSpace.shortageMessage(payload: payload) {
            AppStorageSpace.lastError = shortage
            return nil
        }
        if url.isFileURL, FileManager.default.isReadableFile(atPath: url.path) {
            let dest = FileManager.default.temporaryDirectory
                .appendingPathComponent("vc-in-\(UUID().uuidString)-\(url.lastPathComponent)")
            do {
                try? FileManager.default.removeItem(at: dest)
                try FileManager.default.copyItem(at: url, to: dest)
                return dest
            } catch {
                if let shortage = AppStorageSpace.shortageMessage(payload: payload) {
                    AppStorageSpace.lastError = shortage
                    return nil
                }
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
            AppStorageSpace.lastError = AppStorageSpace.shortageMessage(payload: payload)
                ?? AppStorageSpace.unreadable
            return nil
        }
    }

    static let disguiseNames = [
        "volume.hc",
        "photo.jpg",
        "image.png",
        "clip.mp4",
        "notes.pdf",
        "model.safetensors",
        "adapter.lora",
        "weights.bin"
    ]

    static func sanitizeDisguiseName(_ raw: String) -> String {
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

    func byteCount(_ size: UInt64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: Int64(size))
    }

    func resetLaunchState() {
        closeVolume()
        containerURL = nil
        password = ""
        pim = "0"
        useTextPassword = true
        keyfileURLs = []
        headerKeyfileURLs = []
        keyfileGenName = "keyfile.bin"
        keyfileGenCount = "1"
        status = "Offline. Choose a VeraCrypt container, or share an encrypted file as-is."
        entries = []
        incomingFile = nil
        dirPath = ""
        listTruncated = false
        lastPlain = []
        selectedNames = []
        createCipher = VcMobileBridge.defaultCipher
        createKdf = VcMobileBridge.defaultKdf
        createSizeAmount = "16"
        createSizeUnit = .mib
        createFilesystem = "FAT"
        createHiddenSizeAmount = "4"
        createHiddenSizeUnit = .mib
        createPassword = ""
        createPim = "0"
        createHidden = false
        createHiddenPassword = ""
        createHiddenPim = "0"
        createFileName = "volume.hc"
        entropyPercent = 0
        entropyMarks = []
        VcMobileBridge.resetEntropy()
        newPassword = ""
        newPim = "0"
        headerKdf = "(keep current)"
        hiddenProtectPassword = ""
        hiddenProtectPim = "0"
        namePromptValue = ""
        basketURLs = []
        basketHashes = [:]
        hiddenKeyfileURLs = []
        selectedTab = 0
        holdLock = false
        clearMountOptions()
        wipeSessionFiles()
        forgetUnlock()
        endWork()
    }

    func installTestingHooks() {
        let testing = VcPortTesting.shared
        testing.status = { status }
        testing.createPassword = { createPassword }
        testing.volumePassword = { password }
        testing.createPim = { createPim }
        testing.volumePim = { pim }
        testing.hiddenCreatePassword = { createHiddenPassword }
        testing.basketEmpty = { basketURLs.isEmpty }
        testing.entryNames = { entries.map(\.name) }
        testing.volumeInfo = {
            guard let handle = volumeHandle else { return nil }
            return VcMobileBridge.volumeInfo(handle)
        }
        testing.keyfileURLs = { keyfileURLs }
        testing.containerName = { containerURL?.lastPathComponent ?? "" }
        testing.selectTab = { selectedTab = $0 }
        testing.setCreateCipher = { createCipher = $0 }
        testing.setCreateKdf = { createKdf = $0 }
        testing.setCreatePim = { createPim = $0 }
        testing.setCreateFilename = { createFileName = $0 }
        testing.setCreateSize = { createSizeAmount = $0 }
        testing.setCreateHidden = { createHidden = $0 }
        testing.setCreateHiddenPim = { createHiddenPim = $0 }
        testing.setCreateHiddenSize = { createHiddenSizeAmount = $0 }
        testing.setVolumePassword = { password = $0 }
        testing.setVolumePim = { pim = $0 }
        testing.setProtectHidden = { protectHidden = $0 }
        testing.setHiddenProtectPassword = { hiddenProtectPassword = $0 }
        testing.setHiddenProtectPim = { hiddenProtectPim = $0 }
        testing.setUseBackupHeader = { useBackupHeader = $0 }
        testing.setReadOnly = { readOnlyOpen = $0 }
        testing.setNewPassword = { newPassword = $0 }
        testing.setNewPim = { newPim = $0 }
        testing.setHeaderKdf = { headerKdf = $0 }
        testing.setKeyfileGenName = { keyfileGenName = $0 }
        testing.fillEntropy = {
            var i = 0
            while VcMobileBridge.entropyPercent() < 100 && i < 2000 {
                var bytes = Data(count: 64)
                bytes.withUnsafeMutableBytes { buf in
                    guard let base = buf.baseAddress else { return }
                    arc4random_buf(base, 64)
                }
                VcMobileBridge.addEntropy(bytes)
                i += 1
            }
            entropyPercent = Int(VcMobileBridge.entropyPercent())
        }
        testing.generateCreatePassword = {
            if let generated = VcMobileBridge.generatePassword() {
                createPassword = generated
                status = PasswordEntropy.label(generated) + " Generated a 64-character password in memory. Copy once if you need it elsewhere. It is not saved."
            }
        }
        testing.copyOnce = {
            guard !createPassword.isEmpty else { return }
            SensitivePaste.copyOnce(createPassword)
            status = "Copied once. Clipboard expires in 30 seconds and stays off iCloud clipboard."
        }
        testing.generateNestedPassword = {
            if let generated = VcMobileBridge.generatePassword() {
                createHiddenPassword = generated
                status = PasswordEntropy.label(generated) + " Nested password generated in memory. Copy once if you need it elsewhere. It is not saved."
            }
        }
        testing.copyNestedOnce = {
            guard !createHiddenPassword.isEmpty else { return }
            SensitivePaste.copyOnce(createHiddenPassword)
            status = "Copied nested password once. Clipboard expires in 30 seconds and stays off iCloud clipboard."
        }
        testing.generateKeyfile = { generateKeyfile(nested: false) }
        testing.generateToolsKeyfile = { generateKeyfile(nested: false) }
        testing.createVolume = { createVolume() }
        testing.openVolume = { openVolume() }
        testing.lockSession = { lockSession() }
        testing.showVolumeProperties = { showVolumeProperties() }
        testing.backupHeader = { backupVolumeHeader() }
        testing.changePassword = { changeVolumePassword() }
        testing.setKdf = { setHeaderKdf() }
        testing.applyKeyfiles = { applyKeyfilesToVolume() }
        testing.removeAllKeyfiles = { removeAllKeyfiles() }
        testing.restoreEmbedded = { restoreEmbeddedHeader() }
        testing.wipeFreeSpace = { wipeFreeSpace() }
        testing.mkdir = { mkdirInVolume($0) }
        testing.addBasketFiles = { urls in
            for url in urls where !basketURLs.contains(url) {
                basketURLs.append(url)
            }
            status = "Basket: \(basketSummary(basketURLs)). SHA-256 runs in this session only."
            selectedTab = 1
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
        testing.finishCreateSave = { dest in
            guard let src = containerURL else { return false }
            let fm = FileManager.default
            try? fm.createDirectory(at: dest.deletingLastPathComponent(), withIntermediateDirectories: true)
            try? fm.removeItem(at: dest)
            do {
                try fm.copyItem(at: src, to: dest)
            } catch {
                return false
            }
            incomingFile = nil
            containerURL = dest
            wipeCreateSecrets()
            status = "Saved \(dest.lastPathComponent). Type the volume password and Open volume. Create secrets were wiped."
            selectedTab = 0
            return fm.fileExists(atPath: dest.path)
        }
        testing.selectContainer = { url in
            incomingFile = nil
            containerURL = url
            status = "Selected \(url.lastPathComponent). Open volume to browse folders here."
            selectedTab = 0
        }
        testing.clearKeyfiles = {
            keyfileURLs = []
            hiddenKeyfileURLs = []
        }
        testing.addKeyfiles = { urls in
            for url in urls where !keyfileURLs.contains(url) {
                keyfileURLs.append(url)
            }
        }
        testing.importFiles = { urls in
            guard let handle = volumeHandle else { return }
            beginWork("Copying from device…")
            let destDir = dirPath.isEmpty ? "/" : dirPath
            DispatchQueue.global(qos: .userInitiated).async {
                for url in urls {
                    _ = VcMobileBridge.importFile(
                        handle,
                        destDir: destDir,
                        src: url.path,
                        destName: url.lastPathComponent
                    )
                }
                DispatchQueue.main.async {
                    endWork()
                    reloadDir(quiet: true)
                    persistActiveMount()
                }
            }
        }
        testing.exportNamed = { name, dest in
            guard let handle = volumeHandle else { return false }
            let rel = joinDir(dirPath, name)
            try? FileManager.default.createDirectory(at: dest.deletingLastPathComponent(), withIntermediateDirectories: true)
            try? FileManager.default.removeItem(at: dest)
            return VcMobileBridge.exportFile(handle, name: rel, dest: dest.path) == 0
        }
        testing.openDir = { name in
            dirPath = joinDir(dirPath, name)
            selectedNames = []
            reloadDir()
        }
        testing.goParent = {
            dirPath = parentDir(dirPath)
            selectedNames = []
            reloadDir()
        }
        testing.transferNamed = { names, destLabel, move in
            persistActiveMount()
            let dest = mountedVolumes.first { vol in
                vol.url.lastPathComponent.compare(destLabel, options: .caseInsensitive) == .orderedSame
                    || vol.label.compare(destLabel, options: .caseInsensitive) == .orderedSame
            }
            guard let dest else { return false }
            let src = mountedVolumes.first { vol in
                vol.handle != dest.handle && vol.entries.contains { names.contains($0.name) && !$0.isDir }
            } ?? mountedVolumes.first { $0.handle != dest.handle }
            guard let src, src.handle != dest.handle else { return false }
            if let srcIndex = mountedVolumes.firstIndex(where: { $0.handle == src.handle }) {
                persistActiveMount()
                selectMount(srcIndex)
            }
            var files = src.entries.filter { names.contains($0.name) && !$0.isDir }
            if files.isEmpty {
                files = entries.filter { names.contains($0.name) && !$0.isDir }
            }
            guard !files.isEmpty else { return false }
            selectedNames = names
            transferBetweenVolumes(entries: files, dest: dest, move: move)
            return true
        }
        testing.restoreHeader = { bak in
            restoreVolumeHeader(bak)
        }
        testing.copyHeaderBackup = { dest in
            let src = FileManager.default.temporaryDirectory.appendingPathComponent("volume-header.bak")
            guard FileManager.default.fileExists(atPath: src.path) else { return false }
            try? FileManager.default.createDirectory(at: dest.deletingLastPathComponent(), withIntermediateDirectories: true)
            try? FileManager.default.removeItem(at: dest)
            do {
                try FileManager.default.copyItem(at: src, to: dest)
                return true
            } catch {
                return false
            }
        }
        testing.homeLeave = { dismountOnLeave() }
        testing.selectMountSlot = { selectMount($0) }
        testing.openMountedSlot = {
            selectedTab = 2
            showOpenAnother = true
        }
        testing.selectNames = { selectedNames = $0 }
        testing.startPreview = { startInAppPreview() }
        testing.previewName = { previewItem?.name }
        testing.otgDiskEnabled = { FossConfig.enableOtgDisk }
        testing.fireIdleTimeout = {
            idleTask?.cancel()
            closeOpenVolumes("Idle timeout. Volume closed.")
        }
        testing.hashSelected = { name in
            selectedNames = [name]
            hashSelectedInVolume()
        }
        testing.pimEstimate = { PimEstimator.describe(kdf: createKdf, pimText: pim) }
        testing.ready = true
    }

}

private struct WorkOverlay: View {
    let title: String
    let percent: Int

    var shown: String {
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

    func meter(on: (Int) -> Bool) -> some View {
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

extension View {
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

import SwiftUI

extension ContentView {
    @ViewBuilder
    func openVolumeForm(mountedSlot: Bool) -> some View {
        Group {
            Section {
                Text("Stay offline. A compelled password still wins. Not unbreakable.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button("Choose container") {
                    holdLock = true
                    importerPresented = true
                }
                Text("USB/OTG: pick a file on the stick in Files, then Open. See OTG Master. Whole-disk Open is not on iPhone.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if FossConfig.enableBiometrics {
                    Text("Face ID extra is on. A compelled face still wins. Turn VCPortEnableBiometrics off for the non-biometric IPA.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
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
            if !mountedSlot {
                inFrontSection
            }
            Section("Volume password") {
                Toggle("Password", isOn: $useTextPassword)
                if useTextPassword {
                    SecureField("Password", text: $password)
                        .neverSaveHistory()
                        .portTag("volume_password")
                }
                TextField("PIM (0 = default)", text: $pim)
                    .keyboardType(.numberPad)
                    .portTag("volume_pim")
                keyfileRows
                Text("Mount options")
                    .font(.headline)
                Toggle("Use backup header", isOn: $useBackupHeader)
                    .portTag("use_backup_header")
                Toggle("Read-only", isOn: $readOnlyOpen)
                    .portTag("read_only")
                Toggle("TrueCrypt Mode", isOn: $trueCryptMode)
                    .portTag("truecrypt_mode")
                Toggle("Protect hidden volume against damage caused by writing to outer volume", isOn: $protectHidden)
                    .portTag("protect_hidden")
                if protectHidden {
                    SecureField("Password to hidden volume", text: $hiddenProtectPassword)
                        .neverSaveHistory()
                        .portTag("hidden_protect_password")
                    TextField("Hidden volume PIM (0 = default)", text: $hiddenProtectPim)
                        .keyboardType(.numberPad)
                        .portTag("hidden_protect_pim")
                }
                Text("Idle dismount")
                    .font(.headline)
                HStack {
                    TextField("Idle", text: Binding(
                        get: { idleAmountText },
                        set: {
                            idleAmountText = $0.filter(\.isNumber)
                            if idleAmountText.count > 4 { idleAmountText = String(idleAmountText.prefix(4)) }
                            applyIdleFromFields()
                        }
                    ))
                        .keyboardType(.numberPad)
                        .portTag("idle_amount")
                    Picker("Unit", selection: Binding(
                        get: { idleUnit },
                        set: {
                            idleUnit = $0
                            applyIdleFromFields()
                        }
                    )) {
                        ForEach(IdleUnit.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .portTag("idle_unit")
                }
                Text("0 = Off. Home and screen lock already close. Idle is for walking away with the app still in front.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button("Open volume") {
                    showOpenAnother = false
                    persistActiveMount()
                    openVolume()
                }
                .portTag("open_volume")
                if mountedSlot {
                    Button("Cancel") { showOpenAnother = false }
                        .portTag("mounted_open_cancel")
                }
            }
        }
        .portTag("open_volume_form")
    }

    @ViewBuilder
    var volumeTab: some View {
        Form {
            statusSection
            incomingSection
            openVolumeForm(mountedSlot: false)
        }
    }

}

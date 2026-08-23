import SwiftUI

extension ContentView {
    @ViewBuilder
    var createTab: some View {
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
                .portTag("create_cipher")
                Picker("KDF", selection: $createKdf) {
                    ForEach(VcMobileBridge.kdfs, id: \.self) { Text($0).tag($0) }
                }
                .portTag("create_kdf")
                Picker("Inside the volume", selection: $createFilesystem) {
                    Text("FAT").tag("FAT")
                    Text("exFAT").tag("exFAT")
                }
                .portTag("create_filesystem")
                Text("exFAT if a file is over 4 GiB.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Toggle("Full format (slow)", isOn: $createFullFormat)
                    .portTag("create_full_format")
                Text("Quick format is default. Full format fills the volume with random encrypted data before the filesystem — better plausible deniability (especially with a nested volume), but much slower on large sizes.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("File name (any extension)", text: $createFileName)
                    .portTag("create_filename")
                Text("The name is only a disguise — volume.hc, photo.jpg, image.png, model.safetensors, adapter.lora.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                HStack(alignment: .center, spacing: 8) {
                    TextField("Size", text: $createSizeAmount)
                        .keyboardType(.numberPad)
                        .portTag("create_size")
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
                    .portTag("create_password")
                Text(PasswordEntropy.label(createPassword))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("PIM (0 = default)", text: $createPim)
                    .keyboardType(.numberPad)
                    .portTag("create_pim")
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
                .portTag("copy_once")
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
                    .portTag("entropy_pad")
                }
                Toggle("Nested volume (VeraCrypt hidden volume)", isOn: $createHidden)
                    .portTag("create_hidden")
                if createHidden {
                    Text("Same cipher and KDF. Different password. Do not fill the outer volume.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    SecureField("Nested volume password", text: $createHiddenPassword)
                        .neverSaveHistory()
                        .portTag("create_hidden_password")
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
                        .portTag("copy_nested_once")
                        Button("Forget nested") {
                            createHiddenPassword = ""
                            SensitivePaste.forget()
                            status = "Nested password forgotten. Clipboard cleared."
                        }
                    }
                    TextField("Nested PIM (0 = default)", text: $createHiddenPim)
                        .keyboardType(.numberPad)
                        .portTag("create_hidden_pim")
                    HStack(alignment: .center, spacing: 8) {
                        TextField("Nested size", text: $createHiddenSizeAmount)
                            .keyboardType(.numberPad)
                            .portTag("create_hidden_size")
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
                    .portTag("create_volume")
            }
        }
    }

}

import SwiftUI

extension ContentView {
    @ViewBuilder
    var toolsTab: some View {
        Form {
            statusSection
            Section("Tools") {
                SecureField("New password (empty = keep current)", text: $newPassword)
                    .neverSaveHistory()
                    .portTag("tools_new_password")
                TextField("New PIM (0 = VeraCrypt default)", text: $newPim)
                    .keyboardType(.numberPad)
                    .portTag("tools_new_pim")
                Button("Change volume password") { changeVolumePassword() }
                    .portTag("tools_change_password")
                Picker("Header KDF", selection: $headerKdf) {
                    Text("(keep current)").tag("(keep current)")
                    ForEach(VcMobileBridge.kdfs, id: \.self) { Text($0).tag($0) }
                }
                .portTag("tools_header_kdf")
                Button("Set header key derivation algorithm") { setHeaderKdf() }
                    .portTag("tools_set_kdf")
                Button("Add/Remove keyfiles to/from volume") { applyKeyfilesToVolume() }
                    .portTag("tools_apply_keyfiles")
                Button("Remove all keyfiles from volume") { removeAllKeyfiles() }
                    .portTag("tools_remove_all_keyfiles")
                Button("Backup volume header") { backupVolumeHeader() }
                    .portTag("tools_backup_header")
                Button("Restore volume header") { restoreHeaderPresented = true }
                Button("Restore from embedded backup header") { restoreEmbeddedHeader() }
                    .portTag("tools_restore_embedded")
                Button("Volume properties") { showVolumeProperties() }
                    .portTag("tools_volume_properties")
                TextField("Keyfile name (any extension)", text: $keyfileGenName)
                    .portTag("tools_keyfile_name")
                TextField("How many (1–8)", text: $keyfileGenCount)
                    .keyboardType(.numberPad)
                Button("Keyfile generator") { generateKeyfile(nested: false) }
                    .portTag("tools_generate_keyfile")
                Button("Benchmark") { runBenchmark() }
                Button("Test vectors") { runTestVectors() }
                Text(PimEstimator.describe(kdf: createKdf, pimText: createPim))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if !pimEstimateResult.isEmpty {
                    Text(pimEstimateResult)
                        .font(.caption)
                        .portTag("pim_estimate_result")
                }
                Button("PIM iteration estimate") {
                    beginWork("Estimating header iterations…")
                    let text = PimEstimator.describe(kdf: createKdf, pimText: createPim)
                    VcMobileBridge.setProgress(100, phase: text)
                    pimEstimateResult = text
                    status = text
                    endWork()
                }
                .portTag("tools_pim_estimate")
                Button("Wipe cached passwords") {
                    closeOpenVolumes("Wipe cached passwords complete. Volume closed.")
                }
            }
            Section {
                Text("“We must defend our own privacy if we expect to have any.” — Eric Hughes, A Cypherpunk’s Manifesto (1993)")
                    .font(.caption)
                    .italic()
                    .foregroundStyle(.secondary)
                Text("Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com")
                    .font(.caption)
                Text("https://github.com/ShivamPingaleDev/Veracrypt_port")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("Portions of this product are based in part on TrueCrypt, freely available at http://www.truecrypt.org/")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Text("Apache-2.0 / TrueCrypt License 3.0. Not named VeraCrypt. The app does not install itself.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(SourcePin.describeBuild())
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

}

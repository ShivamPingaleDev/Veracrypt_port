import SwiftUI

extension ContentView {
    @ViewBuilder
    var mountedVolumeForm: some View {
        Form {
            Section {
                if mountedVolumes.count > 1 {
                    Text("\(mountedVolumes.count) volumes mounted")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Text("Slots are this session only. Not a system drive. Select files, then Copy to volume / Copy to device, or Copy from device.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if mountedVolumes.indices.contains(activeMountIndex), mountedVolumes[activeMountIndex].readOnly {
                    Text("Read-only. This slot refuses writes (wipe, import, delete, rename).")
                        .foregroundStyle(.red)
                        .portTag("read_only_banner")
                }
                HStack {
                    Text("No.")
                        .frame(width: 28, alignment: .leading)
                    Text("Volume")
                    Spacer()
                }
                .font(.caption)
                .foregroundStyle(.secondary)
                ForEach(0..<mountSlots, id: \.self) { slot in
                    let vol = mountedVolumes.indices.contains(slot) ? mountedVolumes[slot] : nil
                    HStack(spacing: 8) {
                        Text("\(slot + 1)")
                            .frame(width: 28, alignment: .leading)
                            .font(.body.monospacedDigit())
                            .foregroundStyle(.secondary)
                        if let vol {
                            Button {
                                selectMount(slot)
                            } label: {
                                Text(vol.label)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .buttonStyle(.plain)
                            .portTag("mount_slot_\(slot)")
                            Button {
                                dismountMountedAt(slot)
                            } label: {
                                Image(systemName: "xmark")
                                    .font(.caption.weight(.semibold))
                            }
                            .buttonStyle(.plain)
                            .foregroundStyle(.secondary)
                            .accessibilityLabel("Dismount \(vol.label)")
                        } else {
                            Button {
                                showOpenAnother = true
                            } label: {
                                Text("Empty")
                                    .foregroundStyle(.secondary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .buttonStyle(.plain)
                            .portTag("mount_slot_\(slot)")
                        }
                    }
                    .listRowBackground(vol != nil && slot == activeMountIndex ? Color.accentColor.opacity(0.12) : Color.clear)
                }
                Button("Open another container") {
                    showOpenAnother = true
                }
            } header: {
                Text("Mounted in this app")
            }

            Section {
                if mountedVolumes.isEmpty {
                    Text("No volume in this slot. Open volume on the Volume tab, or tap an empty slot. This is not a system drive.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else if entries.isEmpty {
                    Text("This folder is empty. Tap a folder after Copy from device. FAT and exFAT folders are browsable. Copy from device can pick several files.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if !hashResult.isEmpty {
                    Text(hashResult)
                        .font(.caption)
                        .portTag("hash_result")
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
                ForEach(entries) { entry in
                    Button {
                        if entry.isDir {
                            dirPath = joinDir(dirPath, entry.name)
                            selectedNames = []
                            reloadDir()
                        } else if selectedNames.contains(entry.name) {
                            hashResult = ""
                            selectedNames.remove(entry.name)
                        } else {
                            hashResult = ""
                            selectedNames.insert(entry.name)
                        }
                    } label: {
                        HStack {
                            Image(systemName: entry.isDir ? "folder" : (selectedNames.contains(entry.name) ? "checkmark.circle.fill" : "circle"))
                                .foregroundStyle(selectedNames.contains(entry.name) ? Color.accentColor : Color.secondary)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(entry.name)
                                    .foregroundStyle(.primary)
                                Text(entry.isDir ? "Folder — tap Open" : byteCount(entry.size))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                        }
                    }
                    .buttonStyle(.plain)
                }
                if listTruncated {
                    Button("Load more") { reloadDir(append: true) }
                }
            }

            Section {
                if mountedVolumes.count > 1 {
                    HStack {
                        Button("Copy to volume") { transferMove = false }
                        Button("Move to volume") { transferMove = true }
                    }
                }
                Button(selectableFileNames.isSubset(of: selectedNames) && !selectableFileNames.isEmpty ? "Clear selection" : "Select files") {
                    hashResult = ""
                    if selectableFileNames.isSubset(of: selectedNames) && !selectableFileNames.isEmpty {
                        selectedNames = []
                    } else {
                        selectedNames = selectableFileNames
                    }
                }
                .disabled(selectableFileNames.isEmpty)
                Button("View in app") { startInAppPreview() }
                    .disabled(!FossConfig.enableInAppPreview)
                    .portTag("view_in_app")
                HStack {
                    Button("New folder") {
                        namePromptValue = ""
                        newFolderPresented = true
                    }
                    .portTag("new_folder")
                    Button("Wipe free space") { wipeFreeSpace() }
                        .portTag("wipe_free_space")
                    Button("SHA-256 in volume") { hashSelectedInVolume() }
                        .portTag("hash_in_volume")
                }
                Menu("Folder") {
                    Button("Copy from device") {
                        moveFromDevice = false
                        copyFromDevicePresented = true
                    }
                    Button("Move from device") {
                        moveFromDevice = true
                        copyFromDevicePresented = true
                    }
                    Button("Copy to device") { copySelectedToDevice(move: false) }
                    Button("Move to device") { copySelectedToDevice(move: true) }
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
                    Button("Share decrypted") {
                        let files = entries.filter { selectedNames.contains($0.name) && !$0.isDir }
                        shareVaultFiles(files)
                    }
                }
            }

            inFrontSection
            statusSection
        }
    }

}

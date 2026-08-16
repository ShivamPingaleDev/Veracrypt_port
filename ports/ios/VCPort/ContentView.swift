import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @State private var containerURL: URL?
    @State private var password = ""
    @State private var pim = "0"
    @State private var rememberBiometrics = false
    @State private var status = "Choose a VeraCrypt container."
    @State private var entries: [String] = []
    @State private var importerPresented = false

    var body: some View {
        NavigationStack {
            Form {
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
                Section("Status") {
                    Text(status)
                }
                Section("Files") {
                    ForEach(entries, id: \.self) { Text($0) }
                }
            }
            .navigationTitle("VC Port")
            .fileImporter(isPresented: $importerPresented, allowedContentTypes: [.data]) { result in
                if case .success(let url) = result {
                    _ = url.startAccessingSecurityScopedResource()
                    containerURL = url
                    status = "Selected \(url.lastPathComponent)"
                }
            }
        }
    }

    private func openVolume() {
        guard let path = containerURL?.path else {
            status = "Select a container first."
            return
        }
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
        defer { VcMobileBridge.close(handle) }
        status = "Opened. Size \(VcMobileBridge.size(handle)) bytes."
        entries = VcMobileBridge.listRoot(handle)
        if rememberBiometrics {
            _ = BiometricStore.store(path: path, password: password, pim: Int(pim) ?? 0)
        }
    }
}

#Preview {
    ContentView()
}

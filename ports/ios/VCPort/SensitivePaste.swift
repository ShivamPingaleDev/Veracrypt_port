import UIKit
import UniformTypeIdentifiers

/// One-shot pasteboard write for a generated password. Local only, expiring.
enum SensitivePaste {
    static func copyOnce(_ secret: String) {
        UIPasteboard.general.setItems(
            [[UTType.utf8PlainText.identifier: secret]],
            options: [
                .expirationDate: Date().addingTimeInterval(30),
                .localOnly: true
            ]
        )
    }

    static func forget() {
        UIPasteboard.general.setItems([])
    }
}

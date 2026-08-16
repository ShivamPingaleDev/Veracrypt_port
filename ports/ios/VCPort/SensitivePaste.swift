import UIKit
import UniformTypeIdentifiers

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

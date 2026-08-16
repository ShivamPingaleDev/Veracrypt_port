import SwiftUI
import UIKit

enum SystemShare {
    static func present(items: [Any]) {
        let activity = UIActivityViewController(activityItems: items, applicationActivities: nil)
        guard let root = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow })?
            .rootViewController else { return }
        var top = root
        while let presented = top.presentedViewController {
            top = presented
        }
        if let popover = activity.popoverPresentationController {
            popover.sourceView = top.view
            popover.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY, width: 0, height: 0)
            popover.permittedArrowDirections = []
        }
        top.present(activity, animated: true)
    }
}

private final class FilesExportController: NSObject, UIDocumentPickerDelegate {
    static var current: FilesExportController?
    let onFinish: (Bool) -> Void
    init(onFinish: @escaping (Bool) -> Void) {
        self.onFinish = onFinish
    }
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        onFinish(true)
        FilesExportController.current = nil
    }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        onFinish(false)
        FilesExportController.current = nil
    }
}

enum SystemFiles {
    static func exportCopy(url: URL, onFinish: @escaping (Bool) -> Void) {
        let controller = FilesExportController(onFinish: onFinish)
        FilesExportController.current = controller
        let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
        picker.delegate = controller
        guard let root = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow })?
            .rootViewController else {
            onFinish(false)
            FilesExportController.current = nil
            return
        }
        var top = root
        while let presented = top.presentedViewController {
            top = presented
        }
        top.present(picker, animated: true)
    }
}

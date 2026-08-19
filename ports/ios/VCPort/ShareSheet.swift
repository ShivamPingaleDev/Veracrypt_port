import SwiftUI
import UIKit

enum SystemShare {
    static func present(items: [Any]) {
        if VcPortTesting.shared.skipSystemPickers { return }
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
    let onFinish: ([URL]?) -> Void
    init(onFinish: @escaping ([URL]?) -> Void) {
        self.onFinish = onFinish
    }
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        onFinish(urls)
        FilesExportController.current = nil
    }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        onFinish(nil)
        FilesExportController.current = nil
    }
}

enum SystemFiles {
    static func exportCopy(url: URL, onFinish: @escaping (URL?) -> Void) {
        exportCopy(urls: [url]) { saved in onFinish(saved?.first) }
    }

    static func exportCopy(urls: [URL], onFinish: @escaping ([URL]?) -> Void) {
        if VcPortTesting.shared.skipSystemPickers {
            onFinish(nil)
            return
        }
        let controller = FilesExportController(onFinish: onFinish)
        FilesExportController.current = controller
        let picker = UIDocumentPickerViewController(forExporting: urls, asCopy: true)
        picker.delegate = controller
        guard let root = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow })?
            .rootViewController else {
            onFinish(nil)
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

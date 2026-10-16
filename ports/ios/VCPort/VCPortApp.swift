import SwiftUI

/// App entry. No network client. ContentView owns the session.
@main
struct VCPortApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .tint(Color(red: 10 / 255, green: 108 / 255, blue: 206 / 255))
        }
    }
}

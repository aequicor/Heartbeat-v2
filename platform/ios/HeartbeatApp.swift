import OSLog
import SwiftUI

@main
struct HeartbeatApp: App {
    init() {
        Logger(subsystem: "io.github.aequicor.heartbeat", category: "startup")
            .notice("Heartbeat started")
    }

    var body: some Scene {
        WindowGroup {
            Color.clear
        }
    }
}

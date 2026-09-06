#if os(macOS)
import SwiftUI

@main
struct ShikejiMacApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
                .frame(minWidth: 440, minHeight: 620)
        }
    }
}
#endif

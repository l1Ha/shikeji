import SwiftUI

/// 双平台根视图：提醒 / 呼吸 / 设置 三个标签页
public struct RootView: View {
    @StateObject private var health = HealthStore.shared
    @StateObject private var reminders = ReminderStore.shared
    @StateObject private var updater = UpdateChecker.shared

    public init() {}

    public var body: some View {
        TabView {
            ReminderListView()
                .tabItem { Label("提醒", systemImage: "bell") }
            BreathingView()
                .tabItem { Label("呼吸", systemImage: "wind") }
            SettingsView()
                .tabItem { Label("设置", systemImage: "gearshape") }
        }
        .environmentObject(health)
        .environmentObject(reminders)
        .environmentObject(updater)
        .tint(Color(red: 0, green: 0.72, blue: 0.58))
        .task {
            NotificationScheduler.requestAuthorization()
            reminders.ensureScheduled()
            updater.check()
        }
    }
}

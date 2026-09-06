import SwiftUI
import UserNotifications

#if canImport(AppKit)
import AppKit
#endif
#if canImport(UIKit)
import UIKit
#endif

struct SettingsView: View {
    @EnvironmentObject private var updater: UpdateChecker

    @State private var authText = "检查中…"

    var body: some View {
        NavigationView {
            Form {
                Section("通知") {
                    Button("重新请求通知权限") {
                        NotificationScheduler.requestAuthorization()
                        checkAuth()
                    }
                    Text(authText).font(.footnote).foregroundColor(.secondary)
                    Text("提醒在应用关闭后仍会按已排程的时间触发；下次启动应用会自动续排后续通知。")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }

                Section("关于与更新") {
                    HStack {
                        Text("当前版本")
                        Spacer()
                        Text("v\(updater.currentVersion)").foregroundColor(.secondary)
                    }
                    Button {
                        updater.check()
                    } label: {
                        if updater.checking {
                            HStack(spacing: 8) { ProgressView(); Text("检查中…") }
                        } else {
                            Text("检查更新")
                        }
                    }
                    .disabled(updater.checking)

                    if let message = updater.message {
                        Text(message).font(.footnote).foregroundColor(.secondary)
                    }
                    if let latest = updater.latest {
                        Button {
                            openReleasePage()
                        } label: {
                            Label("发现新版本 \(latest.version)，前往下载", systemImage: "arrow.down.circle")
                        }
                        .tint(Color(red: 0, green: 0.72, blue: 0.58))
                    }
                }

                Section("关于") {
                    Text("时刻计 · 健康提醒助手\n工作再忙，也要照顾好自己的身体。")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
            }
            .navigationTitle("设置")
            .task { checkAuth() }
        }
    }

    private func checkAuth() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            DispatchQueue.main.async {
                switch settings.authorizationStatus {
                case .authorized, .provisional, .ephemeral:
                    authText = "已允许通知"
                case .denied:
                    authText = "通知已禁用，请在系统设置中开启"
                default:
                    authText = "尚未授权通知"
                }
            }
        }
    }

    private func openReleasePage() {
        guard let url = URL(string: "https://github.com/l1Ha/shikeji/releases/latest") else { return }
        #if os(macOS)
        NSWorkspace.shared.open(url)
        #else
        UIApplication.shared.open(url)
        #endif
    }
}

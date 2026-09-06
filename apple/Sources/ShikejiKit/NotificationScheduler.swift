import Foundation
import UserNotifications

/// 本地通知调度：每个提醒一条一次性延迟通知；完成/改间隔后重排
public enum NotificationScheduler {
    public static func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    public static func schedule(_ reminder: ShikejiReminder) {
        let center = UNUserNotificationCenter.current()
        let identifier = "reminder_\(reminder.id)"
        center.removePendingNotificationRequests(withIdentifiers: [identifier])

        let content = UNMutableNotificationContent()
        content.title = reminder.title
        content.body = reminder.desc
        content.sound = .default

        let seconds = max(60, reminder.nextTrigger.timeIntervalSinceNow)
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false)
        center.add(UNNotificationRequest(identifier: identifier, content: content, trigger: trigger))
    }

    public static func cancel(_ id: String) {
        UNUserNotificationCenter.current()
            .removePendingNotificationRequests(withIdentifiers: ["reminder_\(id)"])
    }
}

import Foundation

/// 提醒状态持久化：清单 JSON 存 UserDefaults，通知排程走 NotificationScheduler
public final class ReminderStore: ObservableObject {
    public static let shared = ReminderStore()
    private static let storageKey = "shikeji_reminders_v1"

    private let defaults: UserDefaults

    @Published public private(set) var reminders: [ShikejiReminder] = []

    private init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: Self.storageKey),
           let list = try? JSONDecoder().decode([ShikejiReminder].self, from: data),
           !list.isEmpty {
            reminders = list
        } else {
            let now = Date()
            reminders = [
                ShikejiReminder(id: "activity", title: "起身活动", desc: "站起来伸个腰，走动2分钟",
                                intervalMinutes: 45, nextTrigger: now.addingTimeInterval(45 * 60)),
                ShikejiReminder(id: "water", title: "喝水提醒", desc: "给身体充个电，喝杯温水吧",
                                intervalMinutes: 60, nextTrigger: now.addingTimeInterval(60 * 60)),
                ShikejiReminder(id: "eye", title: "远眺放松", desc: "凝视远处绿色或远方，放松睫状肌",
                                intervalMinutes: 30, nextTrigger: now.addingTimeInterval(30 * 60))
            ]
        }
    }

    public func add() {
        reminders.append(ShikejiReminder(
            title: "新提醒",
            desc: "提醒内容",
            intervalMinutes: 30,
            nextTrigger: Date().addingTimeInterval(30 * 60)
        ))
        NotificationScheduler.schedule(reminders.last!)
    }

    public func remove(_ reminder: ShikejiReminder) {
        guard reminders.count > 1 else { return }
        NotificationScheduler.cancel(reminder.id)
        reminders.removeAll { $0.id == reminder.id }
    }

    public func update(_ reminder: ShikejiReminder) {
        guard let index = reminders.firstIndex(where: { $0.id == reminder.id }) else { return }
        reminders[index] = reminder
    }

    /// 改间隔：按新间隔立即重新计时并重排通知（与小程序 / Android 一致）
    public func changeInterval(_ reminder: ShikejiReminder, to minutes: Int) {
        var updated = reminder
        updated.intervalMinutes = min(max(minutes, 1), 180)
        updated.nextTrigger = Date().addingTimeInterval(Double(updated.intervalMinutes) * 60)
        update(updated)
        NotificationScheduler.schedule(updated)
    }

    /// 完成一轮：重置周期并重排通知（+10 分由 UI 层调用 HealthStore）
    public func complete(_ reminder: ShikejiReminder) {
        var updated = reminder
        updated.unacknowledged = false
        updated.nextTrigger = Date().addingTimeInterval(Double(updated.intervalMinutes) * 60)
        update(updated)
        NotificationScheduler.schedule(updated)
    }

    /// 应用启动时调用：过期未确认的提醒顺延一个周期，并为所有提醒排程
    public func ensureScheduled() {
        let now = Date()
        for var reminder in reminders {
            if reminder.nextTrigger < now {
                reminder.nextTrigger = now.addingTimeInterval(Double(reminder.intervalMinutes) * 60)
                update(reminder)
            }
            NotificationScheduler.schedule(reminder)
        }
    }
}

import Foundation

/// 提醒模型：与小程序 / Android 版语义一致
public struct ShikejiReminder: Codable, Identifiable, Equatable {
    public var id: String
    public var title: String
    public var desc: String
    public var intervalMinutes: Int
    public var nextTrigger: Date
    public var unacknowledged: Bool

    public init(
        id: String = UUID().uuidString,
        title: String,
        desc: String,
        intervalMinutes: Int = 30,
        nextTrigger: Date = Date(),
        unacknowledged: Bool = false
    ) {
        self.id = id
        self.title = title
        self.desc = desc
        self.intervalMinutes = intervalMinutes
        self.nextTrigger = nextTrigger
        self.unacknowledged = unacknowledged
    }
}

/// 呼吸模式
public struct BreathMode: Identifiable, Equatable {
    public var id: String { name }
    public let name: String
    public let inhale: Int
    public let hold: Int
    public let exhale: Int
    public let isCustom: Bool

    public init(name: String, inhale: Int, hold: Int, exhale: Int, isCustom: Bool = false) {
        self.name = name
        self.inhale = inhale
        self.hold = hold
        self.exhale = exhale
        self.isCustom = isCustom
    }
}

public enum BreathPresets {
    public static let all: [BreathMode] = [
        BreathMode(name: "4-7-8 助眠", inhale: 4, hold: 7, exhale: 8),
        BreathMode(name: "等比呼吸", inhale: 4, hold: 4, exhale: 4),
        BreathMode(name: "快速冷静", inhale: 2, hold: 0, exhale: 4),
        BreathMode(name: "自定义", inhale: 4, hold: 4, exhale: 6, isCustom: true)
    ]

    public static func effective(index: Int, customInhale: Int, customHold: Int, customExhale: Int) -> BreathMode {
        guard all.indices.contains(index) else { return all[1] }
        let mode = all[index]
        guard mode.isCustom else { return mode }
        return BreathMode(
            name: mode.name,
            inhale: min(max(customInhale, 1), 10),
            hold: min(max(customHold, 0), 10),
            exhale: min(max(customExhale, 1), 10),
            isCustom: true
        )
    }
}

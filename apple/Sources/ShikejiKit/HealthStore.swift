import Foundation

/// 健康分：今日分每日清零，累计完成次数保留（与小程序 / Android 一致）
public final class HealthStore: ObservableObject {
    public static let shared = HealthStore()

    private let defaults: UserDefaults
    private static let scoreKey = "shikeji_score"
    private static let totalKey = "shikeji_total"
    private static let dateKey = "shikeji_score_date"

    @Published public private(set) var score: Int = 0
    @Published public private(set) var total: Int = 0

    private init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        rolloverIfNeeded()
        total = defaults.integer(forKey: Self.totalKey)
    }

    public func add(_ points: Int) {
        rolloverIfNeeded()
        score += points
        total += 1
        defaults.set(score, forKey: Self.scoreKey)
        defaults.set(total, forKey: Self.totalKey)
    }

    private func rolloverIfNeeded() {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        let today = formatter.string(from: Date())
        if defaults.string(forKey: Self.dateKey) != today {
            defaults.set(today, forKey: Self.dateKey)
            defaults.set(0, forKey: Self.scoreKey)
            score = 0
        } else {
            score = defaults.integer(forKey: Self.scoreKey)
        }
    }
}

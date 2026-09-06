import Foundation

/// 检查 GitHub Releases 中的 Mac 新版本（tag 以 mac- 开头且带 zip/dmg 附件）
public final class UpdateChecker: ObservableObject {
    public static let shared = UpdateChecker()
    private static let api = URL(string: "https://api.github.com/repos/l1Ha/shikeji/releases?per_page=20")!

    public struct ReleaseInfo: Equatable {
        public let version: String
        public let notes: String
        public let pageURL: URL
    }

    @Published public private(set) var checking = false
    @Published public private(set) var message: String?
    @Published public private(set) var latest: ReleaseInfo?

    public var currentVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    }

    public func check() {
        guard !checking else { return }
        checking = true
        message = nil

        var request = URLRequest(url: Self.api)
        request.timeoutInterval = 10
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")

        URLSession.shared.dataTask(with: request) { [weak self] data, _, _ in
            DispatchQueue.main.async {
                guard let self else { return }
                self.checking = false
                guard let data,
                      let releases = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
                    self.message = "检查失败：网络不可用或无法访问 GitHub"
                    return
                }

                for release in releases {
                    let tag = release["tag_name"] as? String ?? ""
                    guard tag.hasPrefix("mac-") else { continue }
                    let assets = release["assets"] as? [[String: Any]] ?? []
                    let hasInstaller = assets.contains {
                        ($0["name"] as? String)?.lowercased().hasSuffix(".zip") == true
                            || ($0["name"] as? String)?.lowercased().hasSuffix(".dmg") == true
                    }
                    guard hasInstaller else { continue }

                    if Self.isNewer(tag, than: self.currentVersion) {
                        self.latest = ReleaseInfo(
                            version: tag,
                            notes: release["body"] as? String ?? "",
                            pageURL: URL(string: "https://github.com/l1Ha/shikeji/releases/latest")!
                        )
                    } else {
                        self.message = "已是最新版本 v\(self.currentVersion)"
                    }
                    return
                }
                self.message = "暂未找到 Mac 安装包"
            }
        }.resume()
    }

    static func isNewer(_ latest: String, than current: String) -> Bool {
        func parse(_ version: String) -> [Int] {
            version.trimmingCharacters(in: .whitespaces)
                .trimmingCharacters(in: CharacterSet(charactersIn: "vVmac-"))
                .split(separator: ".")
                .map { Int($0.filter { $0.isNumber }) ?? 0 }
        }
        let a = parse(latest)
        let b = parse(current)
        for i in 0..<max(a.count, b.count) {
            let x = i < a.count ? a[i] : 0
            let y = i < b.count ? b[i] : 0
            if x != y { return x > y }
        }
        return false
    }
}

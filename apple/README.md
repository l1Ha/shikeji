# 时刻计 for Apple（macOS / iOS）

与小程序、Android 版同源的 SwiftUI 实现：健康提醒 + 呼吸冥想 + 健康分。

```
apple/
├── Sources/
│   ├── ShikejiKit/       # 共享逻辑：提醒存储、通知调度、健康分、更新检查
│   └── ShikejiApp/       # 共享 SwiftUI 界面 + 双平台入口（MacMain / IOSMain）
├── project.yml           # XcodeGen 工程描述（生成 iOS 工程）
├── Shikeji.xcodeproj     # 已生成的 iOS 工程（可直接用 Xcode 打开）
└── scripts/
    ├── build-mac.sh      # macOS 构建：swiftc -> .app -> ad-hoc 签名 -> zip
    └── make-icon.swift   # 应用图标生成脚本
```

## macOS 版

**直接安装**：前往 [Releases](https://github.com/l1Ha/shikeji/releases) 下载 `shikeji-mac-1.0.0.zip`，解压得 `Shikeji.app`。

- 支持 macOS 13（Ventura）及以上，Apple Silicon 与 Intel 双架构
- 应用为 ad-hoc 签名（无开发者账号分发），首次打开如果提示无法验证：
  **右键点击 Shikeji.app → 打开 → 再点「打开」**（只需一次），
  或在终端执行 `xattr -cr /Applications/Shikeji.app` 后正常打开
- 通知需要授权：首次使用请在弹窗或「系统设置 → 通知 → 时刻计」中允许

**本地构建**：`./scripts/build-mac.sh`（需要 Xcode 或 Command Line Tools）

## iOS 版（需要 Xcode）

本仓库提供完整的 Xcode 工程（`Shikeji.xcodeproj`），安装到 iPhone 需要 **Xcode + Apple ID 签名**（苹果要求应用必须经开发者签名，无法像 APK 一样直接分发安装包）：

1. 从 App Store 安装 [Xcode](https://apps.apple.com/app/xcode/id497799835)（或 `brew install --cask xcode`）
2. 打开 `apple/Shikeji.xcodeproj`（如丢失可用 `brew install xcodegen && cd apple && xcodegen generate` 重新生成）
3. 选中 Shikeji target → Signing & Capabilities → 勾选 **Automatically manage signing**，Team 选择你的个人 Apple ID（免费个人账号即可）
4. iPhone 用数据线连接 Mac → 顶部选择你的 iPhone → 点 ▶ 运行
5. 首次安装需在 iPhone「设置 → 通用 → VPN 与设备管理」中信任你的开发者证书
6. 免费账号签名有效期 7 天，到期后重新在 Xcode 运行即可续签

> 需要 Mac 构建助力或 CI 签名分发（TestFlight / 开发者签名 IPA），告诉我即可继续。

import SwiftUI

struct BreathingView: View {
    private enum Phase: String {
        case idle = "准备好了吗？"
        case inhale = "吸气..."
        case hold = "屏息..."
        case exhale = "呼气..."
    }

    @EnvironmentObject private var health: HealthStore

    @AppStorage("breath_mode") private var modeIndex = 1
    @AppStorage("breath_inhale") private var customInhale = 4
    @AppStorage("breath_hold") private var customHold = 4
    @AppStorage("breath_exhale") private var customExhale = 6

    @State private var running = false
    @State private var phase: Phase = .idle
    @State private var scale: CGFloat = 1
    @State private var phaseDuration: Double = 4
    @State private var phaseLeft = 0
    @State private var sessionLeft = 60

    private var mode: BreathMode {
        BreathPresets.effective(
            index: modeIndex,
            customInhale: customInhale,
            customHold: customHold,
            customExhale: customExhale
        )
    }

    var body: some View {
        ZStack {
            Color(red: 0, green: 0.808, blue: 0.788).ignoresSafeArea()
            VStack(spacing: 20) {
                modeSelector
                if mode.isCustom { customEditor }
                Spacer()
                breathingCircle
                timerText
                Spacer()
                controlButton
            }
            .padding(24)
        }
        .task(id: running) { await breathLoop() }
    }

    private var modeSelector: some View {
        VStack(spacing: 8) {
            if !running {
                Text("选择呼吸节奏").font(.footnote).foregroundColor(.white.opacity(0.85))
                Picker("模式", selection: $modeIndex) {
                    ForEach(Array(BreathPresets.all.enumerated()), id: \.offset) { index, mode in
                        Text(mode.name).tag(index)
                    }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            }
        }
    }

    private var customEditor: some View {
        VStack(spacing: 8) {
            if !running {
                sliderRow("吸气 \(customInhale)s", value: $customInhale, range: 1...10)
                sliderRow("屏息 \(customHold)s", value: $customHold, range: 0...10)
                sliderRow("呼气 \(customExhale)s", value: $customExhale, range: 1...10)
                Text("一轮共 \(mode.inhale + mode.hold + mode.exhale) 秒 · 自动保存")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.8))
            }
        }
        .padding(.horizontal, 8)
    }

    private func sliderRow(_ label: String, value: Binding<Int>, range: ClosedRange<Int>) -> some View {
        HStack(spacing: 12) {
            Text(label).font(.caption).foregroundColor(.white).frame(width: 70, alignment: .leading)
            Slider(
                value: Binding(
                    get: { Double(value.wrappedValue) },
                    set: { value.wrappedValue = Int($0.rounded()) }
                ),
                in: Double(range.lowerBound)...Double(range.upperBound),
                step: 1
            )
            .tint(.white)
        }
    }

    private var breathingCircle: some View {
        VStack(spacing: 14) {
            ZStack {
                Circle().fill(.white.opacity(0.12)).frame(width: 200, height: 200)
                Circle()
                    .fill(.white.opacity(0.55))
                    .frame(width: 90, height: 90)
                    .scaleEffect(scale)
                    .animation(.linear(duration: phaseDuration), value: scale)
                VStack(spacing: 4) {
                    Text(phase.rawValue)
                        .font(.title3)
                        .foregroundColor(.white)
                    if running && phaseLeft > 0 {
                        Text("\(phaseLeft)s")
                            .font(.subheadline.monospacedDigit())
                            .foregroundColor(.white.opacity(0.75))
                    }
                }
            }
        }
    }

    private var timerText: some View {
        Text(String(format: "%02d", max(0, sessionLeft)))
            .font(.system(size: 40, weight: .light, design: .monospaced))
            .foregroundColor(.white.opacity(0.85))
    }

    private var controlButton: some View {
        Button {
            running ? stop() : start()
        } label: {
            Text(running ? "结束" : "开始")
                .font(.headline)
                .foregroundColor(Color(red: 0, green: 0.808, blue: 0.788))
                .frame(maxWidth: 220)
                .padding(.vertical, 8)
        }
        .buttonStyle(.borderedProminent)
        .tint(.white)
        .padding(.bottom, 24)
    }

    private func start() {
        sessionLeft = 60
        phaseLeft = 0
        running = true
    }

    private func stop() {
        running = false
        phase = .idle
        scale = 1
        phaseLeft = 0
        sessionLeft = 60
    }

    /// 呼吸阶段状态机：吸气 -> (屏息) -> 呼气 循环，直到会话结束或手动停止
    private func breathLoop() async {
        guard running else { return }
        let mode = self.mode
        let sessionStart = Date()

        while running {
            // 吸气
            enterPhase(.inhale, seconds: mode.inhale)
            if !(await wait(seconds: mode.inhale)) { return }
            // 屏息
            if mode.hold > 0 {
                enterPhase(.hold, seconds: mode.hold)
                if !(await wait(seconds: mode.hold)) { return }
            }
            // 呼气
            enterPhase(.exhale, seconds: mode.exhale)
            if !(await wait(seconds: mode.exhale)) { return }

            // 会话计时
            let elapsed = Date().timeIntervalSince(sessionStart)
            sessionLeft = max(0, 60 - Int(elapsed))
            if elapsed >= 60 {
                finishSession()
                return
            }
        }
    }

    private func enterPhase(_ newPhase: Phase, seconds: Int) {
        phase = newPhase
        phaseDuration = Double(seconds)
        phaseLeft = seconds
        switch newPhase {
        case .inhale, .hold:
            withAnimation(.linear(duration: Double(seconds))) { scale = 2.2 }
        case .exhale:
            withAnimation(.linear(duration: Double(seconds))) { scale = 1 }
        case .idle:
            scale = 1
        }
    }

    /// 等待指定秒数；期间每 250ms 刷新阶段倒计时。返回 false 表示会话已取消
    private func wait(seconds: Int) async -> Bool {
        let deadline = Date().addingTimeInterval(Double(seconds))
        while Date() < deadline {
            if !running { return false }
            try? await Task.sleep(for: .milliseconds(250))
            if !running { return false }
            phaseLeft = max(0, Int(deadline.timeIntervalSinceNow.rounded(.up)))
        }
        return true
    }

    private func finishSession() {
        running = false
        phase = .idle
        scale = 1
        phaseLeft = 0
        sessionLeft = 60
        health.add(5)
    }
}

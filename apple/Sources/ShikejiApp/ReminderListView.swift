import SwiftUI

struct ReminderListView: View {
    @EnvironmentObject private var store: ReminderStore
    @EnvironmentObject private var health: HealthStore

    var body: some View {
        NavigationView {
            TimelineView(.periodic(from: .now, by: 1)) { context in
                List {
                    Section {
                        HealthScoreCard(score: health.score, total: health.total)
                    }
                    Section("提醒清单") {
                        ForEach(store.reminders) { reminder in
                            ReminderRow(reminder: reminder, now: context.date)
                        }
                        Button {
                            store.add()
                        } label: {
                            Label("添加新提醒", systemImage: "plus.circle")
                        }
                    }
                    Section {
                        Text("通知按周期触发；触发后回到应用点「去完成」可领取健康分。")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle("时刻计")
        }
    }
}

private struct HealthScoreCard: View {
    let score: Int
    let total: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("今日健康分").font(.footnote).opacity(0.9)
            Text("\(score) 分 · 累计完成 \(total) 次")
                .font(.title2.bold())
            Text("完成提醒 +10 分，呼吸练习 +5 分")
                .font(.caption).opacity(0.85)
        }
        .foregroundColor(.white)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(LinearGradient(
                    colors: [Color(red: 0.42, green: 0.36, blue: 0.91), Color(red: 0.63, green: 0.61, blue: 1.0)],
                    startPoint: .topLeading, endPoint: .bottomTrailing
                ))
        )
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.clear)
    }
}

private struct ReminderRow: View {
    let reminder: ShikejiReminder
    let now: Date

    @EnvironmentObject private var store: ReminderStore
    @EnvironmentObject private var health: HealthStore
    @State private var expanded = false

    private var remainingText: String {
        let remaining = max(0, Int(reminder.nextTrigger.timeIntervalSince(now)))
        return String(format: "%02d:%02d", remaining / 60, remaining % 60)
    }

    private var isDue: Bool { reminder.nextTrigger <= now }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(reminder.title).font(.headline)
                    Text(reminder.desc).font(.subheadline).foregroundColor(.secondary)
                    Text("每 \(reminder.intervalMinutes) 分钟 · 下次 \(remainingText)")
                        .font(.caption).foregroundColor(.secondary)
                }
                Spacer()
                Button {
                    guard isDue else { return }
                    health.add(10)
                    store.complete(reminder)
                } label: {
                    Text(isDue ? "去完成 +10分" : "等待中")
                        .font(.caption.bold())
                }
                .buttonStyle(.borderedProminent)
                .tint(isDue ? Color(red: 0, green: 0.72, blue: 0.58) : Color.gray.opacity(0.35))
                .disabled(!isDue)
            }

            if expanded {
                Divider()
                ReminderEditor(reminder: reminder)
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture { withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() } }
    }
}

private struct ReminderEditor: View {
    let reminder: ShikejiReminder

    @EnvironmentObject private var store: ReminderStore

    @State private var title: String
    @State private var desc: String

    init(reminder: ShikejiReminder) {
        self.reminder = reminder
        _title = State(initialValue: reminder.title)
        _desc = State(initialValue: reminder.desc)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            TextField("标题", text: Binding(
                get: { title },
                set: { title = $0; persist() }
            ))
            .textFieldStyle(.roundedBorder)

            TextField("提醒描述", text: Binding(
                get: { desc },
                set: { desc = $0; persist() }
            ))
            .textFieldStyle(.roundedBorder)

            Stepper(value: intervalBinding, in: 1...180) {
                Text("间隔 \(reminder.intervalMinutes) 分钟")
            }

            HStack {
                Spacer()
                Button(role: .destructive) {
                    store.remove(reminder)
                } label: {
                    Text("删除").font(.footnote)
                }
                .disabled(store.reminders.count <= 1)
            }
        }
    }

    private func persist() {
        var updated = reminder
        updated.title = title.isEmpty ? "未命名提醒" : title
        updated.desc = desc
        store.update(updated)
    }

    private var intervalBinding: Binding<Int> {
        Binding(
            get: { reminder.intervalMinutes },
            set: { store.changeInterval(reminder, to: $0) }
        )
    }
}

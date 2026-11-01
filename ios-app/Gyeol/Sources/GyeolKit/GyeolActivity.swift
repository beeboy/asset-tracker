import ActivityKit
import SwiftUI
import WidgetKit

/// 결 Live Activity 화면: 섬은 얼굴(펼치면 큰 얼굴과 말풍선), 잠금 화면은 앉은 결과 말풍선
public struct GyeolLiveActivity: Widget {
    public init() {}

    static let teal = Color(red: 0.235, green: 0.753, blue: 0.651)
    static let paper = Color(red: 0.957, green: 0.937, blue: 0.902)
    static let ink = Color(red: 0.169, green: 0.133, blue: 0.2)

    public var body: some WidgetConfiguration {
        ActivityConfiguration(for: GyeolAttributes.self) { ctx in
            GyeolLockView(s: ctx.state)
                .activityBackgroundTint(Self.paper)
                .activitySystemActionForegroundColor(Self.ink)
                .widgetURL(URL(string: "naeilo://home"))
        } dynamicIsland: { ctx in
            let s = ctx.state, p = GyeolPose.of(s.pose)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.bottom) {
                    HStack(alignment: .center, spacing: 6) {
                        p.image(.island).resizable().scaledToFit().frame(width: 88, height: 110)
                        GyeolBubble(text: s.bubble, fill: Color.white.opacity(0.14), ink: .white)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(s.chipText).font(.caption.monospacedDigit().bold()).foregroundStyle(Self.teal)
                }
            } compactLeading: {
                p.image(.face).resizable().scaledToFit().frame(width: 34, height: 34)
            } compactTrailing: {
                if let say = s.say, !say.isEmpty {   // 결의 말은 섬을 펼치지 않고 카메라 바로 옆 글자로 (너비를 고정해야 카메라 쪽에서 시작한다)
                    Text(verbatim: say).font(.system(size: 20, weight: .semibold)).foregroundStyle(.white).lineLimit(1).truncationMode(.tail)
                        .frame(width: 96, alignment: .leading)
                } else {
                    Text(s.chipText).font(.caption2.monospacedDigit().bold()).foregroundStyle(Self.teal).lineLimit(1)
                }
            } minimal: {
                p.image(.face).resizable().scaledToFit().frame(width: 30, height: 30)
            }
            .widgetURL(URL(string: "naeilo://home"))
            .keylineTint(Self.teal)
        }
    }
}

struct GyeolLockView: View {
    let s: GyeolAttributes.ContentState
    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            GyeolPose.of(s.pose).image(.island).resizable().scaledToFit().frame(width: 72, height: 90)
            VStack(alignment: .leading, spacing: 6) {
                GyeolBubble(text: s.bubble, fill: .white, ink: GyeolLiveActivity.ink)
                if !s.chipText.isEmpty {
                    Text(verbatim: (GyeolShelf.en ? "Today " : "오늘 ") + s.chipText).font(.caption.monospacedDigit().bold()).foregroundStyle(GyeolLiveActivity.teal).padding(.leading, 10)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
    }
}

/// 말풍선: 꼬리가 왼쪽(결 쪽)으로
struct GyeolBubble: View {
    let text: String
    let fill: Color
    let ink: Color
    var body: some View {
        Text(text)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(ink)
            .lineLimit(2).minimumScaleFactor(0.8)
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(fill, in: RoundedRectangle(cornerRadius: 14))
            .overlay(alignment: .leading) {
                Tail().fill(fill).frame(width: 8, height: 10).offset(x: -7)
            }
            .contentTransition(.opacity)
    }
    struct Tail: Shape {
        func path(in r: CGRect) -> Path {
            var p = Path()
            p.move(to: CGPoint(x: r.maxX, y: r.minY)); p.addLine(to: CGPoint(x: r.minX, y: r.midY)); p.addLine(to: CGPoint(x: r.maxX, y: r.maxY)); p.closeSubpath()
            return p
        }
    }
}

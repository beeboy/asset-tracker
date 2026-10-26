import WidgetKit
import SwiftUI
import UIKit

// 워치 스마트 스택 위젯 3개 (2026-10-26 시안 확정): A 자산 언덕 · B 인물 한마디 · C 본전 진행
// 인물은 앱 홈의 인물을 따라간다. 값은 워치 앱이 받아 둔 WWatch 를 그리기만 한다

@main
struct NaeiloWatchWidgets: WidgetBundle {
    var body: some Widget { WatchHill(); WatchSay(); WatchLadder() }
}

struct WEntry: TimelineEntry { let date: Date; let w: WWatch?; var preview = false }

struct WProvider: TimelineProvider {
    func placeholder(in context: Context) -> WEntry { WEntry(date: Date(), w: .sample, preview: true) }
    func getSnapshot(in context: Context, completion: @escaping (WEntry) -> Void) {
        completion(context.isPreview ? WEntry(date: Date(), w: .sample, preview: true) : WEntry(date: Date(), w: WWatch.read()))
    }
    // 다시 그리기는 워치 앱이 새 값을 받을 때 한다
    func getTimeline(in context: Context, completion: @escaping (Timeline<WEntry>) -> Void) {
        completion(Timeline(entries: [WEntry(date: Date(), w: WWatch.read())], policy: .never))
    }
}

private func watchWidget<V: View>(_ kind: String, _ name: String, _ desc: String, @ViewBuilder _ v: @escaping (WWatch) -> V) -> some WidgetConfiguration {
    StaticConfiguration(kind: kind, provider: WProvider()) { e in WFrame(e: e) { v($0) } }
        .configurationDisplayName(name)
        .description(desc)
        .supportedFamilies([.accessoryRectangular])
}
struct WatchHill: Widget { var body: some WidgetConfiguration { watchWidget("watch.hill", "자산 언덕", "총액과 오늘 등락, 최근 10일") { HillView(w: $0) } } }
struct WatchSay: Widget { var body: some WidgetConfiguration { watchWidget("watch.say", "인물 한마디", "인물 말풍선과 총액") { SayView(w: $0) } } }
struct WatchLadder: Widget { var body: some WidgetConfiguration { watchWidget("watch.ladder", "본전 진행", "본전(목표)까지 진행") { LadderView(w: $0) } } }

// MARK: 공통

private func rgb(_ x: UInt32) -> Color {
    Color(red: Double((x >> 16) & 0xff) / 255, green: Double((x >> 8) & 0xff) / 255, blue: Double(x & 0xff) / 255)
}
private let upC = Color(red: 1, green: 0.42, blue: 0.42)
private let dnC = Color(red: 0.42, green: 0.61, blue: 1)

/// 인물 색 그라데이션 바탕. 값이 아직 없으면 세리 바탕에 안내 한 줄
struct WFrame<Content: View>: View {
    let e: WEntry
    @ViewBuilder var content: (WWatch) -> Content
    var body: some View {
        let w = e.w ?? .sample
        Group {
            if let v = e.w { content(v) } else {
                Text("아이폰에서 naeilo를 열면 값이 들어와요.").font(.system(size: 12, weight: .semibold))
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            }
        }
        .foregroundStyle(.white)
        .containerBackground(for: .widget) {
            LinearGradient(colors: [rgb(w.g0), rgb(w.g1)], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
    }
}

/// 도트 그림: 포즈 그림(w_<인물>_pose_*)이 없으면 세리 그림. 아이폰과 같은 그림을 최근접 확대
struct WSprite: View {
    let w: WWatch
    let pose: String
    var height: CGFloat = 48
    var body: some View {
        let n = ["w_\(w.char)_\(pose)", "w_seri_\(pose)"].first { UIImage(named: $0) != nil } ?? "w_seri"
        Image(n).interpolation(.none).resizable().scaledToFit().frame(height: height)
    }
}

// MARK: A 자산 언덕

struct HillView: View {
    let w: WWatch
    var body: some View {
        let sp = Array(w.spark.suffix(10))
        ZStack(alignment: .bottomLeading) {
            VStack(alignment: .leading, spacing: 1) {
                Text(w.moneyText).font(.system(size: 17, weight: .bold)).lineLimit(1).minimumScaleFactor(0.6)
                Text(w.dayText + " 오늘").font(.system(size: 9, weight: .semibold)).foregroundStyle(w.dayChg >= 0 ? upC : dnC)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            if sp.count >= 2 { hill(sp, rgb(w.accent)).padding(.bottom, 1) }
            Rectangle().fill(.white.opacity(0.35)).frame(height: 1)
            WSprite(w: w, pose: w.dayChg < 0 ? "pose_trend_down" : "pose_trend_up")
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
        }
    }
    /// 기둥 10개: 가장 낮은 날 9pt ~ 가장 높은 날 26pt, 오래된 날일수록 옅게, 오늘만 진하게
    private func hill(_ v: [Double], _ c: Color) -> some View {
        let lo = v.min() ?? 0, hi = v.max() ?? 1, span = max(hi - lo, 1e-9)
        return HStack(alignment: .bottom, spacing: 1.5) {
            ForEach(Array(v.enumerated()), id: \.offset) { i, x in
                RoundedRectangle(cornerRadius: 1).fill(c)
                    .opacity(i == v.count - 1 ? 1 : 0.25 + 0.04 * Double(i))
                    .frame(width: 7.5, height: 9 + 17 * CGFloat((x - lo) / span))
            }
        }
    }
}

// MARK: B 인물 한마디

struct SayView: View {
    let w: WWatch
    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            WSprite(w: w, pose: w.dayChg < 0 ? "pose_point_down" : "pose_point_up")
            VStack(alignment: .leading, spacing: 4) {
                Text(w.line).font(.system(size: 9.5, weight: .semibold)).lineLimit(2).minimumScaleFactor(0.8)
                    .padding(.horizontal, 7).padding(.vertical, 5)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Bubble().fill(.white.opacity(0.14)))
                Spacer(minLength: 0)
                HStack(alignment: .firstTextBaseline, spacing: 5) {
                    Text(w.moneyText).font(.system(size: 15, weight: .bold)).lineLimit(1).minimumScaleFactor(0.6)
                    Text(w.dayText).font(.system(size: 9, weight: .semibold)).foregroundStyle(w.dayChg >= 0 ? upC : dnC)
                }
            }
        }
    }
}

/// 둥근 말풍선 + 왼쪽 아래 꼬리 (인물 쪽)
struct Bubble: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path(roundedRect: r, cornerRadius: 9)
        let y = r.maxY - 7
        p.move(to: CGPoint(x: r.minX, y: y - 4))
        p.addLine(to: CGPoint(x: r.minX - 5, y: y))
        p.addLine(to: CGPoint(x: r.minX, y: y + 4))
        p.closeSubpath()
        return p
    }
}

// MARK: C 본전 진행

struct LadderView: View {
    let w: WWatch
    var body: some View {
        let p = min(1, max(0, w.pct))
        ZStack(alignment: .bottomLeading) {
            VStack(alignment: .leading, spacing: 1) {
                Text(w.keyName + "까지").font(.system(size: 10, weight: .semibold)).opacity(0.78)
                HStack(alignment: .firstTextBaseline, spacing: 5) {
                    Text(w.moneyText).font(.system(size: 20, weight: .bold)).lineLimit(1).minimumScaleFactor(0.6)
                    Text("\(Int((p * 100).rounded()))%").font(.system(size: 10, weight: .semibold))
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(.white.opacity(0.18))
                    Capsule().fill(rgb(w.accent)).frame(width: g.size.width * p)
                }
            }
            .frame(height: 7)
            .padding(.trailing, 47)
            .padding(.bottom, 4)
            WSprite(w: w, pose: w.dayChg < 0 ? "pose_trend_down" : "pose_trend_up")
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
        }
    }
}

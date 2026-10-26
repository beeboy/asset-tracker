import WidgetKit
import SwiftUI
import UIKit

// 잠금 화면 위젯. 잠금 화면은 늘 단색이라 처음부터 흰색 명암(불투명도)으로만 그린다 (2026-10-26 시안)
// 기본 2개(자산 추이·목표 진행) + 인물 3개(워치 스마트 스택과 같은 자산 언덕·한마디·본전 진행)

struct LockAssetView: View { // 1 자산 추이: 금액 오른쪽에 흐름선, ↻ 는 오른쪽 위
    let s: Snapshot
    @Environment(\.widgetKind) var kind
    var body: some View {
        ZStack(alignment: .topTrailing) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 8) {
                    Money { h in Text(h ? hiddenAmount : Fmt.eok(s.total)).font(.system(size: 24, weight: .heavy, design: .rounded)).lineLimit(1).minimumScaleFactor(0.6).flip(s.total) }
                    Spark(values: Array(s.spark.suffix(21)), color: Color.primary, width: 1.6).frame(width: 44, height: 14).flipFade()
                }
                HStack(spacing: 6) {
                    Text("\(Fmt.arrow(s.dayChg))\(Fmt.pct(s.dayChg))").font(.system(size: 12, weight: .bold)).flip(s.dayChg)
                    LockAgo(s: s)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            LockReload()
        }
    }
}

struct LockGoalView: View { // 2 목표 진행: 동그라미는 목표 대비 %, 가운데는 총자산 (5.3억 · 2.7천만 · 823만)
    let s: Snapshot
    var body: some View {
        Gauge(value: min(1, max(0, s.progress))) {
            Text(WL.t("목표"))
        } currentValueLabel: {
            // 동그라미 안은 버튼을 두지 않는다 (누르면 앱). 숨김·잠긴 동안 가리기는 다른 위젯과 같이 따른다
            Text(Store.isHidden("lock.goal") ? hiddenAmount : Fmt.eok1(s.total)).font(.system(size: 14, weight: .heavy, design: .rounded))
                .lineLimit(1).minimumScaleFactor(0.6).privacySensitive(Store.lockPrivate).flip(s.total)
        }
        .gaugeStyle(.accessoryCircularCapacity)
    }
}

// MARK: 인물 잠금화면 위젯 (워치 A·B·C 를 잠금화면 직사각형으로)

/// 인물 그림: 높이는 위젯(72pt)의 80% 수준. 34px 그림 한 칸 = 58/34pt, 발은 아래 맞춤
struct LockChar: View {
    let c: WChar
    let pose: String
    var body: some View {
        let n = lockPose(c, pose)
        if let img = LockMono.image(n) {
            Image(uiImage: img).interpolation(.none).resizable().aspectRatio(contentMode: .fit)
                .frame(height: CGFloat(img.cgImage?.height ?? 34) * 58 / 34).accessibilityHidden(true)
        }
    }
}

/// 동작 그림 w_<인물>_<동작> 이 있으면 그것, 없으면 기본 그림
private func lockPose(_ c: WChar, _ p: String) -> String {
    let n = "w_\(c.id)_\(p)"
    if c.id == "me" { return Store.readData("me_\(p).png") != nil ? n : "w_me" }
    return UIImage(named: n) != nil ? n : "w_\(c.id)"
}

/// 잠금화면 인물 그림을 흰색 명암 단계로 바꾼다. 손으로 다듬지 않고 다섯 인물·내 캐릭터 모두 이 규칙 하나로 (2026-10-26 결정)
/// 1) 투명과 맞닿은 어두운 칸(그림 안 밝기 하위 20%)은 비운다: 바깥 외곽선
/// 2) 남은 색을 밝기 순으로 줄 세워 넷으로 나눠 불투명도 .3 · .5 · .75 · 1. 면적이 아니라 색 순위라서 몇 칸뿐인 눈동자도 둘레와 다른 단계가 된다
enum LockMono {
    private static var cache: [String: UIImage] = [:]
    private static let levels: [Double] = [0.3, 0.5, 0.75, 1]

    static func image(_ name: String) -> UIImage? {
        if let i = cache[name] { return i }
        guard let src = name.hasPrefix("w_me") ? WMe.image(name) : UIImage(named: name), let out = convert(src) else { return nil }
        cache[name] = out
        return out
    }

    static func convert(_ img: UIImage) -> UIImage? {
        guard let cg = img.cgImage else { return nil }
        let w = cg.width, h = cg.height
        var px = [UInt8](repeating: 0, count: w * h * 4)
        return px.withUnsafeMutableBytes { buf -> UIImage? in
            guard let ctx = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                      space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
            ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
            let p = buf.bindMemory(to: UInt8.self)
            let alpha = { (x: Int, y: Int) -> Int in x < 0 || y < 0 || x >= w || y >= h ? 0 : Int(p[(y * w + x) * 4 + 3]) }
            // 색(미리 곱한 알파를 푼 RGB)과 밝기
            func rgb(_ i: Int) -> (Int, Double) {
                let a = max(1, Int(p[i + 3]))
                let r = min(255, Int(p[i]) * 255 / a), g = min(255, Int(p[i + 1]) * 255 / a), b = min(255, Int(p[i + 2]) * 255 / a)
                return (r << 16 | g << 8 | b, 0.2126 * Double(r) + 0.7152 * Double(g) + 0.0722 * Double(b))
            }
            var lum: [Int: Double] = [:]
            for y in 0..<h { for x in 0..<w where alpha(x, y) > 0 { let (k, l) = rgb((y * w + x) * 4); lum[k] = l } }
            guard let lo = lum.values.min(), let hi = lum.values.max() else { return nil }
            let span = max(1, hi - lo)
            var edge = Set<Int>()
            for y in 0..<h { for x in 0..<w where alpha(x, y) > 0 {
                let (k, _) = rgb((y * w + x) * 4)
                let open = alpha(x + 1, y) == 0 || alpha(x - 1, y) == 0 || alpha(x, y + 1) == 0 || alpha(x, y - 1) == 0
                if open && (lum[k]! - lo) / span < 0.2 { edge.insert(y * w + x) }
            } }
            var bodyCols = Set<Int>()
            for y in 0..<h { for x in 0..<w where alpha(x, y) > 0 && !edge.contains(y * w + x) { bodyCols.insert(rgb((y * w + x) * 4).0) } }
            let ranked = bodyCols.sorted { lum[$0]! < lum[$1]! }
            var level: [Int: Double] = [:]
            for (i, k) in ranked.enumerated() { level[k] = levels[min(3, Int(Double(i + 1) / Double(ranked.count) * 4 - 1e-9))] }
            for y in 0..<h { for x in 0..<w {
                let i = (y * w + x) * 4, a = Double(p[i + 3])
                guard a > 0 else { continue }
                let v = edge.contains(y * w + x) ? 0 : (level[rgb(i).0] ?? 0)
                let o = UInt8((v * a).rounded())
                p[i] = o; p[i + 1] = o; p[i + 2] = o; p[i + 3] = o   // 흰색, 미리 곱한 알파
            } }
            return ctx.makeImage().map { UIImage(cgImage: $0, scale: img.scale, orientation: .up) }
        }
    }
}

/// 인물 위젯 공통 줄: 오늘 등락 ▲1.2% (색 대신 화살표)
private func lockChg(_ d: Double) -> String { Fmt.arrow(d) + String(format: "%.1f%%", abs(d) * 100) }

struct LockHillView: View { // A 자산 언덕: 총액·오늘 등락·최근 10일 기둥, 오른쪽 아래 인물
    let e: CharEntry
    var body: some View {
        let r = e.r, sp = Array((r.spark ?? []).suffix(10))
        ZStack(alignment: .bottomLeading) {
            VStack(alignment: .leading, spacing: 0) {
                Money { h in Text(h ? hiddenAmount : Fmt.eok(r.total)).font(.system(size: 20, weight: .heavy, design: .rounded)).lineLimit(1).minimumScaleFactor(0.6).flip(r.total) }
                Text(lockChg(r.dayChg) + " " + WL.t("오늘")).font(.system(size: 11, weight: .bold)).flip(r.dayChg)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            if sp.count >= 2 { hill(sp).padding(.bottom, 3) }
            Rectangle().fill(Color.white.opacity(0.35)).frame(height: 1).padding(.trailing, 46).padding(.bottom, 2)
            LockChar(c: e.c, pose: r.dayChg < 0 ? "pose_trend_down" : "pose_trend_up")
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
        }
    }
    /// 기둥 10개: 가장 낮은 날 6pt ~ 가장 높은 날 19pt, 오래된 날일수록 옅게, 오늘만 진하게
    private func hill(_ v: [Double]) -> some View {
        let lo = v.min() ?? 0, hi = v.max() ?? 1, span = max(hi - lo, 1e-9)
        return HStack(alignment: .bottom, spacing: 2) {
            ForEach(Array(v.enumerated()), id: \.offset) { i, x in
                RoundedRectangle(cornerRadius: 1).fill(Color.white)
                    .opacity(i == v.count - 1 ? 1 : 0.22 + 0.05 * Double(i))
                    .frame(width: 6, height: 6 + 13 * CGFloat((x - lo) / span))
            }
        }
        .flipFade()
    }
}

struct LockSayView: View { // B 인물 한마디: 왼쪽 인물, 말풍선이 주인공이고 금액은 아래
    let e: CharEntry
    var body: some View {
        let r = e.r
        HStack(alignment: .bottom, spacing: 9) {
            LockChar(c: e.c, pose: r.dayChg < 0 ? "pose_point_down" : "pose_point_up")
            VStack(alignment: .leading, spacing: 2) {
                Text(e.c.line(r)).font(.system(size: 10.5, weight: .semibold)).lineLimit(2).minimumScaleFactor(0.8)
                    .padding(.horizontal, 7).padding(.vertical, 4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(LockBubble().fill(Color.white.opacity(0.2)))
                Spacer(minLength: 0)
                HStack(alignment: .firstTextBaseline, spacing: 5) {
                    Money { h in Text(h ? hiddenAmount : Fmt.eok(r.total)).font(.system(size: 15, weight: .heavy, design: .rounded)).lineLimit(1).minimumScaleFactor(0.6).flip(r.total) }
                    Text(lockChg(r.dayChg)).font(.system(size: 10, weight: .bold)).flip(r.dayChg)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
    }
}

/// 둥근 말풍선 + 왼쪽 아래 꼬리 (인물 쪽)
struct LockBubble: Shape {
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

struct LockLadderView: View { // C 본전 진행: '본전까지 ▼1.8%', 총액, 진행 막대, 오른쪽 아래 인물
    let e: CharEntry
    var body: some View {
        let r = e.r, p = min(1, max(0, r.pct))
        ZStack(alignment: .bottomLeading) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    Text(WL.t("{key}까지", ["key": WL.t(r.keyName)])).font(.system(size: 11, weight: .bold)).opacity(0.75)
                    Text(lockChg(r.dayChg)).font(.system(size: 11, weight: .bold)).flip(r.dayChg)
                }
                Money { h in Text(h ? hiddenAmount : Fmt.eok(r.total)).font(.system(size: 18, weight: .heavy, design: .rounded)).lineLimit(1).minimumScaleFactor(0.6).flip(r.total) }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.white.opacity(0.22))
                    Capsule().fill(Color.white).frame(width: g.size.width * p)
                }
            }
            .frame(height: 6).padding(.trailing, 56).padding(.bottom, 6).flipFade()
            LockChar(c: e.c, pose: r.dayChg < 0 ? "pose_trend_down" : "pose_trend_up")
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
        }
    }
}

/// 잠금화면 ↻: 잠금화면은 단색이라 옅은 동그라미 위 기호만. 누르면 홈 위젯의 ↻ 와 같이 받는다
struct LockReload: View {
    var body: some View {
        let busy = Store.isReloading
        let icon = Image(systemName: busy ? "ellipsis" : "arrow.clockwise").font(.system(size: 9, weight: .bold))
            .frame(width: 16, height: 16).background(Circle().fill(Color.primary.opacity(busy ? 0.35 : 0.2)))
            .accessibilityLabel(WL.t(busy ? "받는 중" : "다시 받기"))
        #if WIDGET_EXT
        Button(intent: ReloadIntent()) { icon }.buttonStyle(.plain)
        #else
        icon
        #endif
    }
}

/// 잠금화면의 '3분 전' (자리가 좁아 출처는 뺀다. 마지막 ↻ 가 실패했으면 '받기 실패')
struct LockAgo: View {
    let s: Snapshot
    @Environment(\.entryDate) private var now
    var body: some View {
        Text(Store.fetchFailed ? WL.t("받기 실패") : Fresh.ago(s.pricedAt, now: now)).font(.system(size: 9, weight: .semibold)).opacity(0.7).lineLimit(1).fixedSize()
    }
}

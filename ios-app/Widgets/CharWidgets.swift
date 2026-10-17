import WidgetKit
import SwiftUI
import AppIntents

// 인물 보상 위젯 (캐릭터 설정 스레드 시안): 모두 작은 크기. 키 재기 · 퍼즐 · 꽃밭 · 줄다리기 (2026-10-17 이전 4개 삭제)
// 위젯마다 인물을 고른다. 아직 못 만난 인물은 실루엣으로 보이고, 누르면 앱의 쉼터로 간다.
// 앱 타깃에도 같이 넣어 설정의 시안 조작 > 인물 위젯 미리보기에서 다섯 인물을 한 번에 본다.
// 기본은 인물 색 그라데이션 바탕, 다크 모드는 #1c1c1f 바탕에 인물 색은 빛·제목·막대·블록·말풍선 테두리에만.

enum WhoEnum: String, AppEnum {
    // 'seri' 는 '홈 인물 따라가기'가 생기기 전 위젯의 기본값이었다. 그때 둔 위젯은 그 값이 그대로 저장돼 있어
    // 앱에서 홈 인물을 바꿔도 세리로 남았다. 그래서 예전 값 'seri' 는 홈 인물 따라가기로 읽고,
    // 세리를 고정하고 싶을 때는 새 값 'seri.pin' 을 쓴다 (고르는 목록에는 legacy 가 안 보인다)
    case home, legacy = "seri", seri = "seri.pin", sio, seonbae, ir, sua, me
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "인물"
    static var caseDisplayRepresentations: [WhoEnum: DisplayRepresentation] = [
        .home: "앱 홈의 인물 따라가기", .legacy: "앱 홈의 인물 따라가기",
        .seri: "세리", .sio: "시오", .seonbae: "선배", .ir: "이르", .sua: "수아", .me: "내 캐릭터",
    ]
    static let choices: [WhoEnum] = [.home, .seri, .sio, .seonbae, .ir, .sua, .me]
    var follows: Bool { self == .home || self == .legacy }
    var charId: String { self == .seri ? "seri" : rawValue }
}

struct WhoOptions: DynamicOptionsProvider {
    /// 만난 인물만 고를 수 있다 (2026-10-16 결정: 못 만난 친구 미리 보기는 뺌)
    func results() async throws -> [WhoEnum] {
        let on = Store.read(WReward.self, "reward.json")?.friendsOn
        return WhoEnum.choices.filter { $0 == .home || $0 == .seri || on?.contains($0.charId) ?? true }
    }
}

struct WhoIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "인물 고르기"
    static var description = IntentDescription("위젯에 나올 인물을 골라요. 아직 못 만난 인물은 실루엣으로 보여요.")
    @Parameter(title: "인물", default: .home, optionsProvider: WhoOptions()) var who: WhoEnum
    init() {}
    init(_ w: WhoEnum) { who = w }
}

struct CharEntry: TimelineEntry {
    var date: Date
    let r: WReward
    let c: WChar
    var locked: Bool            // 아직 못 만난 인물 (실루엣). 7일 무료 뒤 잠금은 이것과 따로 Gate 가 한다
    var preview = false         // 위젯 갤러리 미리보기면 7일 무료 뒤에도 잠그지 않는다 (다른 위젯과 같이)
    var at: Date? = nil         // 지금 값의 시세를 받은 시각과 출처 (큰 위젯 아래 줄 '3분 전 · 토스')
    var feed: String? = nil
}

struct CharProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> CharEntry { CharEntry(date: Date(), r: .sample, c: WChar.all[0], locked: false) }
    func snapshot(for cfg: WhoIntent, in context: Context) async -> CharEntry { entry(cfg, preview: context.isPreview) }
    func timeline(for cfg: WhoIntent, in context: Context) async -> Timeline<CharEntry> {
        // ↻ 직후 '받는 중' 그리기: 받지 않고 바로 그린다 (받기는 ReloadIntent 가 하고, 끝나면 다시 그린다)
        if Store.isReloading { return Timeline(entries: [entry(cfg, preview: false)], policy: .after(Date().addingTimeInterval(15))) }
        WidgetLine.bump()   // 새로 그릴 때마다 다음 대사
        // 기본 위젯처럼 지금 가격을 직접 받는다 (전에는 받지 않아, 인물 위젯만 두면 ↻나 앱을 열 때까지 금액이 멈춰 있었다).
        // 다음 갱신도 기본 위젯과 같이: 국내 종목이 있으면 한국 장중에도, 7일 무료가 끝나는 때에도
        let next = await WidgetRefresh.fetch()
        // 같은 값을 몇 장 더 그려 둔다: 큰 위젯 아래 줄의 '몇 분 전'이 시간이 지나면서 바뀌게
        let base = entry(cfg, preview: false)
        return Timeline(entries: Fresh.dates(base.date, until: next).map { var x = base; x.date = $0; return x }, policy: .after(next))
    }
    func recommendations() -> [AppIntentRecommendation<WhoIntent>] {
        WhoEnum.choices.map { AppIntentRecommendation(intent: WhoIntent($0), description: $0 == .home ? "앱 홈의 인물" : WChar.of($0.charId).name) }
    }
    private func entry(_ cfg: WhoIntent, preview: Bool) -> CharEntry {
        var r = Store.read(WReward.self, "reward.json") ?? .sample
        // ↻·정기 갱신으로 위젯이 앱보다 나중에 받은 시세가 있으면 금액·오늘 등락·점선 끝점을 지금 값으로 (기본 위젯과 같은 값)
        if !preview, let h = Engine.liveHoldings() {
            r.total = h.total; r.dayChg = h.dayChg
            if var sp = r.spark, !sp.isEmpty { sp[sp.count - 1] = h.total / 1e4; r.spark = sp }
        }
        // '홈 인물 따라가기'면 앱 홈에 둔 인물 (앱에서 홈 인물을 바꾸면 위젯도 바뀐다)
        // 못 만난 친구는 실루엣으로도 보이지 않는다 (2026-10-16 결정: 미리 보기 뺌). 고른 인물을 아직 못 만났으면 홈 인물로
        let met = { (id: String) in id == "seri" || r.friendsOn.contains(id) }
        let want = cfg.who.follows ? (r.homeFriend ?? "seri") : cfg.who.charId
        let c = WChar.of(preview || met(want) ? want : (r.homeFriend.flatMap { met($0) ? $0 : nil } ?? "seri"))
        var e = CharEntry(date: Date(), r: r, c: c, locked: false, preview: preview)
        if !preview { let s = Engine.snapshot(); if !s.placeholder { e.at = s.pricedAt; e.feed = s.feed } }
        return e
    }
}

/// 인물 위젯은 모두 작은 크기 (2026-10-17 큰·중간 삭제, 시안 B 4개 추가)
private func charWidget<V: View>(_ kind: String, @ViewBuilder _ v: @escaping (CharEntry) -> V) -> some WidgetConfiguration {
    AppIntentConfiguration(kind: kind, intent: WhoIntent.self, provider: CharProvider()) { e in
        CharFrame(e: e, kind: kind) { v(e) }
    }
    .catalog(kind)
    .supportedFamilies([.systemSmall])
}
struct CharHeight: Widget { var body: some WidgetConfiguration { charWidget("char.height") { HeightCharView(e: $0) } } }
struct CharPuzzle: Widget { var body: some WidgetConfiguration { charWidget("char.puzzle") { PuzzleCharView(e: $0) } } }
struct CharGarden: Widget { var body: some WidgetConfiguration { charWidget("char.garden") { GardenCharView(e: $0) } } }
struct CharTug: Widget { var body: some WidgetConfiguration { charWidget("char.tug") { TugCharView(e: $0) } } }

// MARK: 공통

private func rgb(_ x: UInt32) -> Color {
    Color(red: Double((x >> 16) & 0xff) / 255, green: Double((x >> 8) & 0xff) / 255, blue: Double(x & 0xff) / 255)
}

/// 인물 색: 잠겨 있으면 회색
struct CharStyle {
    let c: WChar
    let locked: Bool
    let dark: Bool
    var accent: Color { locked ? Color(white: 0.55) : rgb(c.accent) }
    /// 제목 색: 다크에선 인물 색, 기본에선 흰색 조금 흐리게
    var heading: Color { dark ? accent : .white.opacity(0.78) }
    var bubbleFill: Color { dark ? .clear : .white.opacity(0.14) }
    var bubbleStroke: Color { dark ? accent.opacity(0.7) : .clear }
}

struct CharFrame<Content: View>: View {
    let e: CharEntry
    let kind: String
    @ViewBuilder var content: Content
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        // 7일 무료 뒤 잠금은 다른 위젯과 같은 Gate 로. 잠긴 모습만 인물 바탕을 지킨다 (이 파일은 앱에도 들어가 Locked.swift 를 못 쓴다)
        Gate(kind: kind, preview: e.preview) { open } locked: { closed }
    }
    private var closed: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "lock.fill").font(.system(size: 12, weight: .bold))
                Text(Catalog.name(kind)).font(.system(size: 13, weight: .bold)).lineLimit(1).minimumScaleFactor(0.8)
            }
            Spacer(minLength: 0)
            Text(WidgetUnlock.how(kind)).font(.system(size: 11, weight: .semibold)).opacity(0.75)
                .lineLimit(3).fixedSize(horizontal: false, vertical: true)
            Text(WidgetUnlock.cta).font(.system(size: 10, weight: .bold)).opacity(0.5)
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(WL.t("{name} 잠김. {how}", ["name": Catalog.name(kind), "how": WidgetUnlock.how(kind)]))
        .containerBackground(for: .widget) { CharBG(c: e.c, locked: true, dark: scheme == .dark) }
    }
    private var open: some View {
        content
            .environment(\.charKind, kind)
            .foregroundStyle(.white)
            .environment(\.colorScheme, .dark)
            .environment(\.charDark, scheme == .dark)
            .containerBackground(for: .widget) { CharBG(c: e.c, locked: e.locked, dark: scheme == .dark) }
            .widgetURL(URL(string: e.locked ? "naeilo://shelter" : "naeilo://guide")!)
    }
}

struct CharBG: View {
    let c: WChar, locked: Bool, dark: Bool
    var body: some View {
        if dark {
            ZStack {
                Color(red: 0x1c / 255, green: 0x1c / 255, blue: 0x1f / 255)
                RadialGradient(colors: [(locked ? Color.gray : rgb(c.accent)).opacity(0.22), .clear], center: .bottomLeading, startRadius: 0, endRadius: 220)
            }
        } else if locked {
            LinearGradient(colors: [Color(white: 0.16), Color(white: 0.30)], startPoint: .topLeading, endPoint: .bottomTrailing)
        } else {
            LinearGradient(colors: [rgb(c.g0), rgb(c.g1)], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
    }
}

/// 도트 그림: 최근접 확대. 못 만난 인물은 실루엣
struct CharSprite: View {
    let name: String
    let locked: Bool
    var height: CGFloat
    /// false: 위젯이 갱신 때 따로 움직인다 (새 시안 4개: 동작 그림 바꾸기·톡 뛰기)
    var shake = true
    @Environment(\.widgetRenderingMode) private var mode
    var body: some View {
        if shake { sprite.charShake() } else { sprite }
    }
    private var sprite: some View {
        Group {
            // 내 캐릭터: 앱이 겹쳐 그린 그림 하나 (동작별 그림 없음)
            if name.hasPrefix("w_me"), let img = WMe.image {
                if locked { Image(uiImage: img).interpolation(.none).resizable().renderingMode(.template).foregroundStyle(Color.white.opacity(0.22)) }
                else if mode == .accented, #available(iOS 18, *) {
                    // 투명·틴트: 기본 인물처럼 원래 색 그대로 (안 그러면 시스템이 한 색으로 칠해 흰 덩어리가 된다)
                    Image(uiImage: img).interpolation(.none).resizable().widgetAccentedRenderingMode(.fullColor)
                } else { Image(uiImage: img).interpolation(.none).resizable() }
            }
            else if locked { Image(name).interpolation(.none).resizable().renderingMode(.template).foregroundStyle(Color.white.opacity(0.22)) }
            else if mode == .accented, #available(iOS 18, *) {
                // 투명·틴트 홈 화면: 그대로 두면 시스템이 그림 전체를 한 색(흰색)으로 칠해 흰 덩어리가 된다.
                // 캐릭터 설정 스레드가 만든 회색 단계 그림(_tint)을 쓰고, 밝기만 살려 틴트 색으로 칠하게 한다
                Image(name).interpolation(.none).resizable().widgetAccentedRenderingMode(.fullColor)
            } else { Image(name).interpolation(.none).resizable() }
        }
        .aspectRatio(contentMode: .fit)
        .frame(height: height)
        .accessibilityHidden(true)
    }
}

/// 쉼터에 돌아온 물건 하나 (인물 발치). 투명·틴트 홈 화면에서는 한 색 덩어리가 되니 그리지 않는다
struct CharItem: View {
    let id: String
    @Environment(\.widgetRenderingMode) private var mode
    var body: some View {
        if mode != .accented {
            Image("w_item_" + id).interpolation(.none).resizable().frame(width: 20, height: 20).accessibilityHidden(true)
        }
    }
}

private func lockLine(_ c: WChar) -> String { WL.t("인터미션 {w}주차에 만나요", ["w": "\(c.week)"]) }

/// 1억 2,340만
private func eokMan(_ v: Double) -> String {
    let man = Int((abs(v) / 1e4).rounded()), eok = man / 10000, rest = man % 10000
    if eok == 0 { return Fmt.comma(man) + "만" }
    return "\(eok)억" + (rest > 0 ? " \(Fmt.comma(rest))만" : "")
}
private let upC = Color(red: 1, green: 0.42, blue: 0.42)
private let dnC = Color(red: 0.42, green: 0.61, blue: 1)
private func pctText(_ c: Double) -> String { Fmt.arrow(c) + String(format: "%.1f%%", abs(c) * 100) }

/// 동작 그림 w_<인물>_<동작> 이 있으면 그것, 없으면 기본 그림. '위젯 인물 포즈 그리기'에서 그림이 오면 그 이름으로 넣기만 하면 바뀐다
private func poseOr(_ c: WChar, _ p: String) -> String {
    let n = "w_\(c.id)_\(p)"
    return UIImage(named: n) != nil ? n : "w_\(c.id)"
}

fileprivate extension View {
    /// 시안처럼 위젯 가장자리부터 그리는 그림 (Canvas 좌표 = 위젯 좌표)
    func charBleed() -> some View { frame(maxWidth: .infinity, maxHeight: .infinity).padding(-16) }
    /// 갱신 때 인물이 dy 만큼 톡 움직였다가, 다 받으면 통통 돌아온다
    func charHop(_ dy: CGFloat) -> some View {
        let busy = Store.isReloading
        return offset(y: busy ? dy : 0).animation(.interpolatingSpring(stiffness: 180, damping: 7), value: busy)
    }
    /// 시안 좌표(위젯 가장자리부터 pt)로 놓는다. 위젯 콘텐츠 여백 16 안에 그리므로 16을 뺀다
    func charPin(_ a: Alignment, x: CGFloat = 16, y: CGFloat = 16) -> some View {
        let ox: CGFloat = a.horizontal == .leading ? x - 16 : a.horizontal == .trailing ? 16 - x : 0
        let oy: CGFloat = a.vertical == .top ? y - 16 : a.vertical == .bottom ? 16 - y : 0
        return frame(maxWidth: .infinity, maxHeight: .infinity, alignment: a).offset(x: ox, y: oy)
    }
}

/// '3분 전': 지금 값의 시세를 받은 때 (갤러리 미리보기처럼 모르면 안 보인다)
struct CharAgo: View {
    let e: CharEntry
    var body: some View {
        if let at = e.at { Text(Fresh.ago(at, now: e.date)).font(.system(size: 9, weight: .medium)).opacity(0.55).lineLimit(1).fixedSize() }
    }
}

/// 작은 위젯 윗줄: 제목 · '3분 전' · ↻
struct CharTop: View {
    let title: String, e: CharEntry, s: CharStyle
    var body: some View {
        HStack(spacing: 4) {
            Text(title).font(.system(size: 12, weight: .semibold)).foregroundStyle(s.heading).lineLimit(1)
            Spacer(minLength: 0)
            if !e.locked { CharAgo(e: e).padding(.trailing, 2); CharReload() }
        }
        .frame(height: 20)
    }
}

/// 새 디자인(B) 오른쪽 위: '3분 전'과 ↻
@ViewBuilder private func cornerB(_ e: CharEntry) -> some View {
    if !e.locked {
        CharAgo(e: e).charPin(.topTrailing, x: 38, y: 17)
        CharReload().charPin(.topTrailing, x: 12, y: 12)
    }
}

/// 못 만난 인물: 자물쇠와 만나는 주차
private func meetLater(_ c: WChar) -> some View {
    VStack(alignment: .leading, spacing: 4) {
        Image(systemName: "lock.fill").font(.system(size: 22, weight: .semibold)).foregroundStyle(.white.opacity(0.7))
        Text(WL.t("인터미션\n{w}주차에 만나요", ["w": "\(c.week)"])).font(.system(size: 12, weight: .semibold)).foregroundStyle(.white.opacity(0.8))
            .fixedSize(horizontal: false, vertical: true)
    }
}

private func speakMoney(_ e: CharEntry, _ kind: String) -> String {
    e.locked ? "\(e.c.title), \(lockLine(e.c))"
        : "\(Catalog.name(kind)). \(e.c.title), " + WL.t("총자산 {v}, 오늘 {p} {dir}", ["v": Store.isHidden(kind) ? WL.t("숨김") : eokMan(e.r.total), "p": String(format: "%.1f%%", abs(e.r.dayChg) * 100), "dir": WL.dir(e.r.dayChg)])
}
private func speakRecover(_ e: CharEntry, _ kind: String) -> String {
    e.locked ? "\(e.c.title), \(lockLine(e.c))"
        : "\(Catalog.name(kind)). \(e.c.title), " + WL.t("{key}까지 {p}%, 남은 금액 {v}", ["key": WL.t(e.r.keyName), "p": "\(Int((e.r.pct * 100).rounded()))", "v": Store.isHidden(kind) ? WL.t("숨김") : eokMan(e.r.remain)])
}
private func speakBlock(_ e: CharEntry, _ kind: String) -> String {
    e.locked ? "\(e.c.title), \(lockLine(e.c))"
        : "\(Catalog.name(kind)). \(e.c.title), " + WL.t("1000칸 중 {n}칸, 어제보다 {d}칸", ["n": "\(e.r.cells)", "d": (e.r.cells >= e.r.cellsYesterday ? "+" : "") + "\(e.r.cells - e.r.cellsYesterday)"])
}

// MARK: 새 시안 ① 자산 추이 · 키 재기: 문틀 기둥에 지난 6거래일 자산을 금으로, 오늘 금은 인물 머리 높이에 빨갛게

struct HeightCharView: View {
    let e: CharEntry
    var body: some View {
        StyleReader { _ in
            let r = e.r
            ZStack {
                HeightMarks(v: e.locked ? [] : Array((r.spark ?? []).suffix(7)), today: e.date).charBleed()
                CharSprite(name: poseOr(e.c, Store.isReloading ? "pose_measure_up" : r.dayChg < 0 ? "pose_measure_down" : "pose_measure_base"), locked: e.locked, height: 64, shake: false)
                    .charHop(-4).charPin(.bottomLeading, x: 66, y: 10)
                Group {
                    if e.locked { meetLater(e.c) } else {
                        VStack(alignment: .leading, spacing: 4) {
                            CharMoney { h in Text(h ? charHidden : eokMan(r.total)).font(.system(size: 24)).lineLimit(1).minimumScaleFactor(0.6).charFlip(r.total) }
                            (Text(pctText(r.dayChg)).foregroundColor(r.dayChg >= 0 ? upC : dnC) + Text(" " + WL.t("오늘")))
                                .font(.system(size: 11, weight: .semibold)).lineLimit(1).widgetAccentable().charFlip(r.dayChg)
                        }
                    }
                }
                .charPin(.topLeading, y: 40)
                cornerB(e)
            }
        }
        .speak(speakMoney(e, "char.height"), kind: "char.height", money: !e.locked, reload: !e.locked)
    }
}

/// 기둥은 오른쪽 가장자리에서 46~56pt, 오늘 금은 인물 머리(아래에서 74pt). 가장 멀리 간 날이 머리에서 54pt (2% 안쪽 차이는 덜 벌린다)
/// 금액이 줄면 지난 날 금이 머리 위로 올라간다
private struct HeightMarks: View {
    let v: [Double], today: Date
    var body: some View {
        Canvas { ctx, size in
            let px = size.width - 56, head = size.height - 74
            var ys: [CGFloat] = []
            if let now = v.last, v.count >= 2 {
                let past = v.dropLast(), dev = max(past.map { abs($0 - now) }.max() ?? 0, abs(now) * 0.02, 1e-9)
                ys = past.map { min(size.height - 6, max(44, head + CGFloat((now - $0) / dev) * 54)) }
            }
            let top = min(head - 8, (ys.min() ?? head) - 8)
            ctx.fill(Path(CGRect(x: px, y: top, width: 10, height: size.height - top)), with: .color(rgb(0xd9a877)))
            ctx.fill(Path(CGRect(x: px, y: top, width: 2, height: size.height - top)), with: .color(rgb(0xf0c89a)))
            var used: [CGFloat] = v.isEmpty ? [] : [head]
            for (i, y) in ys.enumerated().reversed() {   // 최근 날부터: 날짜 글자가 겹치면 오래된 날 글자를 뺀다
                var l = Path(); l.move(to: CGPoint(x: px - 2, y: y)); l.addLine(to: CGPoint(x: px + 12, y: y))
                ctx.stroke(l, with: .color(rgb(0x4a3426)), lineWidth: 1.5)
                guard used.allSatisfy({ abs($0 - y) >= 9 }) else { continue }
                used.append(y)
                ctx.draw(Text(tradeDay(back: ys.count - i, from: today)).font(.system(size: 8)).foregroundColor(.white.opacity(0.7)),
                         at: CGPoint(x: px + 16, y: y), anchor: .leading)
            }
            if !v.isEmpty {
                var l = Path(); l.move(to: CGPoint(x: px - 40, y: head)); l.addLine(to: CGPoint(x: px + 12, y: head))
                ctx.stroke(l, with: .color(upC), lineWidth: 2)
                ctx.draw(Text(WL.t("오늘")).font(.system(size: 8, weight: .bold)).foregroundColor(upC), at: CGPoint(x: px + 16, y: head), anchor: .leading)
            }
        }
        .flipFade()
    }
}

/// n 거래일 전 'M/d' (주말은 건너뛴다. 공휴일은 모른다)
private func tradeDay(back n: Int, from d: Date) -> String {
    let cal = Calendar.current
    var x = d, k = 0
    while k < n { x = cal.date(byAdding: .day, value: -1, to: x)!; if !cal.isDateInWeekend(x) { k += 1 } }
    let c = cal.dateComponents([.month, .day], from: x)
    return "\(c.month ?? 0)/\(c.day ?? 0)"
}

// MARK: 새 시안 ② 본전 진행 · 퍼즐: 5×4=20조각(1조각 5%), 다 맞추면 깃발 그림. 인물이 다음 조각을 든다

struct PuzzleCharView: View {
    let e: CharEntry
    var body: some View {
        StyleReader { dark in
            let s = CharStyle(c: e.c, locked: e.locked, dark: dark), r = e.r
            let n = min(20, max(0, Int((r.pct * 20 + 1e-9).rounded(.down))))
            ZStack {
                if !e.locked { PuzzleBoard(n: n, c: s.accent).charBleed() }
                CharSprite(name: poseOr(e.c, Store.isReloading ? "pose_puzzle_refresh" : "pose_puzzle_base"), locked: e.locked, height: 64, shake: false)
                    .charHop(3).charPin(.bottomTrailing, x: 8, y: 26)
                Text(WL.t("{key}까지", ["key": WL.t(r.keyName)])).font(.system(size: 12, weight: .semibold)).foregroundStyle(s.heading)
                    .lineLimit(1).frame(height: 20).charPin(.topLeading)
                Group {
                    if e.locked { meetLater(e.c) } else {
                        Text("\(Int((r.pct * 100).rounded()))%").font(.system(size: 34)).lineLimit(1).minimumScaleFactor(0.6).charFlip(r.pct)
                    }
                }
                .charPin(.topLeading, y: 38)
                if !e.locked {
                    let d = (r.pct - r.pctYesterday) * 100
                    HStack(spacing: 0) {
                        CharMoney { h in Text(WL.t("남은 {v}", ["v": h ? charHidden : eokMan(r.remain)])).charFlip(r.remain) }
                        Text(WL.t(" · 어제 "))
                        Text((d >= 0 ? "+" : "") + String(format: "%.1f%%p", d)).foregroundStyle(d >= 0 ? upC : dnC)
                    }
                    .font(.system(size: 10, weight: .medium)).foregroundStyle(.white.opacity(0.75)).lineLimit(1)
                    .charPin(.bottomLeading)
                }
                cornerB(e)
            }
        }
        .speak(speakRecover(e, "char.puzzle"), kind: "char.puzzle", money: !e.locked, reload: !e.locked)
    }
}

/// 조각 하나: 오른쪽·위에 둥근 이음새. 시안 170pt 좌표
private func puzzlePiece(_ x: CGFloat, _ y: CGFloat, right: Bool, top: Bool) -> Path {
    let w: CGFloat = 12.5, r: CGFloat = 2.2
    var p = Path()
    p.move(to: CGPoint(x: x, y: y + w))
    p.addLine(to: CGPoint(x: x, y: y))
    if top { p.addLine(to: CGPoint(x: x + w / 2 - r, y: y)); p.addArc(center: CGPoint(x: x + w / 2, y: y), radius: r, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false) }
    p.addLine(to: CGPoint(x: x + w, y: y))
    if right { p.addLine(to: CGPoint(x: x + w, y: y + w / 2 - r)); p.addArc(center: CGPoint(x: x + w, y: y + w / 2), radius: r, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: false) }
    p.addLine(to: CGPoint(x: x + w, y: y + w))
    p.closeSubpath()
    return p
}

/// 아래 줄 왼쪽부터 맞춘다. 맞춘 조각에만 깃발 그림이 보이고, 다음 조각은 인물 손 위에 (위젯이 작으면 같이 줄인다)
private struct PuzzleBoard: View {
    let n: Int, c: Color
    var body: some View {
        Canvas { ctx, size in
            let k = min(size.width, size.height) / 170
            ctx.scaleBy(x: k, y: k)
            let w: CGFloat = 12.5, y0: CGFloat = 134 - 4 * w
            var done = Path(), pieces: [Path] = []
            for i in 0..<20 {
                let col = i % 5, row = i / 5   // row 0 = 맨 아래 줄
                let p = puzzlePiece(16 + CGFloat(col) * w, y0 + CGFloat(3 - row) * w, right: col < 4, top: row < 3)
                if i < n { done.addPath(p); pieces.append(p) }
                else { ctx.stroke(p, with: .color(.white.opacity(0.4)), style: StrokeStyle(lineWidth: 1, dash: [2, 1.5])) }
            }
            ctx.fill(done, with: .color(c))
            var flag = ctx
            flag.clip(to: done)
            flag.fill(Path(CGRect(x: 30, y: 86, width: 3, height: 48)), with: .color(rgb(0x5a3324)))
            var tri = Path(); tri.move(to: CGPoint(x: 33, y: 86)); tri.addLine(to: CGPoint(x: 70, y: 96)); tri.addLine(to: CGPoint(x: 33, y: 108)); tri.closeSubpath()
            flag.fill(tri, with: .color(.white))
            for p in pieces { ctx.stroke(p, with: .color(.black.opacity(0.3)), lineWidth: 0.8) }
            if n < 20 {
                let p = puzzlePiece(146, 64, right: true, top: false)
                ctx.fill(p, with: .color(c)); ctx.stroke(p, with: .color(.white), lineWidth: 1)
            }
        }
        .flipFade()
    }
}

// MARK: 새 시안 ③ 블록 · 꽃밭: 100칸 밭에 블록 1개 = 꽃 1송이, 어제 늘어난 칸은 봉오리. 밭은 10번째까지

struct GardenCharView: View {
    let e: CharEntry
    var body: some View {
        StyleReader { dark in
            let s = CharStyle(c: e.c, locked: e.locked, dark: dark), r = e.r
            let g = min(10, r.cells / 100 + 1), fill = r.cells >= 1000 ? 100 : r.cells % 100
            let gy = min(10, r.cellsYesterday / 100 + 1), fy = gy == g ? (r.cellsYesterday >= 1000 ? 100 : r.cellsYesterday % 100) : 0
            let d = r.cells - r.cellsYesterday
            ZStack {
                if !e.locked { GardenBed(fill: fill, bloom: min(fill, fy), garden: g, c: s.accent).charBleed() }
                CharSprite(name: poseOr(e.c, Store.isReloading ? "pose_garden_refresh" : "pose_garden_base"), locked: e.locked, height: 64, shake: false)
                    .charPin(.bottomTrailing, x: 8, y: 10)
                Text(WL.t("{n}번째 꽃밭", ["n": "\(g)"])).font(.system(size: 11, weight: .semibold)).foregroundStyle(s.heading)
                    .lineLimit(1).frame(height: 20).charPin(.topLeading)
                if e.locked { meetLater(e.c).charPin(.topLeading, y: 40) } else {
                    (Text("\(fill)").font(.system(size: 20))
                     + Text("/100" + WL.t(" · 어제 ")).font(.system(size: 10)).foregroundColor(.white.opacity(0.75))
                     + Text((d >= 0 ? "+" : "") + "\(d)").font(.system(size: 10)).foregroundColor(d >= 0 ? upC : dnC))
                        .lineLimit(1).charFlip(Double(fill)).charPin(.bottomLeading)
                }
                cornerB(e)
            }
        }
        .speak(speakBlock(e, "char.garden"), kind: "char.garden", money: false, reload: !e.locked)
    }
}

/// 아래 줄 왼쪽부터 핀다. 오른쪽 위에 밭 탭: 다 핀 밭(최근 3개까지) + 테두리 친 지금 밭. 물뿌리개는 밭 오른쪽 아래
private struct GardenBed: View {
    let fill: Int, bloom: Int, garden: Int, c: Color
    var body: some View {
        Canvas { ctx, size in
            let k = min(size.width, size.height) / 170
            ctx.scaleBy(x: k, y: k)
            let dirt = GraphicsContext.Shading.color(rgb(0x5a3a28)), leaf = GraphicsContext.Shading.color(rgb(0x7bc46a))
            let petal = GraphicsContext.Shading.color(rgb(0xff9fb4)), core = GraphicsContext.Shading.color(rgb(0xffe27a))
            for i in 0..<100 {
                let x = 16 + CGFloat(i % 10) * 8.6, y = 42 + CGFloat(9 - i / 10) * 8.6
                ctx.fill(Path(roundedRect: CGRect(x: x, y: y, width: 7, height: 7), cornerRadius: 1.2), with: dirt)
                if i < bloom {
                    ctx.fill(Path(CGRect(x: x + 3, y: y + 4, width: 1, height: 3)), with: leaf)
                    ctx.fill(Path(roundedRect: CGRect(x: x + 1.5, y: y + 1, width: 4, height: 3.4), cornerRadius: 1), with: petal)
                    ctx.fill(Path(CGRect(x: x + 3, y: y + 2, width: 1, height: 1)), with: core)
                } else if i < fill {
                    ctx.fill(Path(CGRect(x: x + 3, y: y + 3.5, width: 1, height: 3.5)), with: leaf)
                    ctx.fill(Path(ellipseIn: CGRect(x: x + 2.5, y: y + 2, width: 2, height: 2)), with: leaf)
                }
            }
            for j in 0..<min(3, garden - 1) {
                let x = 132 - CGFloat(j) * 12
                ctx.fill(Path(roundedRect: CGRect(x: x, y: 44, width: 9, height: 9), cornerRadius: 2), with: petal)
                ctx.fill(Path(CGRect(x: x + 3, y: 47, width: 3, height: 3)), with: core)
            }
            let cur = CGRect(x: 144.5, y: 43.5, width: 10, height: 10)
            ctx.fill(Path(roundedRect: cur, cornerRadius: 2), with: dirt)
            ctx.stroke(Path(roundedRect: cur, cornerRadius: 2), with: .color(c), lineWidth: 1.5)
            ctx.draw(Text("\(garden)").font(.system(size: 7, weight: .bold)).foregroundColor(c), at: CGPoint(x: cur.midX, y: cur.midY))
            let can = GraphicsContext.Shading.color(rgb(0x8fb7d9)), drop = GraphicsContext.Shading.color(rgb(0xbfe3ff))
            ctx.fill(Path(roundedRect: CGRect(x: 104, y: 106, width: 9, height: 7), cornerRadius: 1), with: can)
            var spout = Path(); spout.move(to: CGPoint(x: 104.5, y: 107)); spout.addLine(to: CGPoint(x: 100, y: 104.5))
            ctx.stroke(spout, with: can, lineWidth: 2)
            ctx.fill(Path(ellipseIn: CGRect(x: 97.2, y: 108.2, width: 1.6, height: 1.6)), with: drop)
            ctx.fill(Path(ellipseIn: CGRect(x: 95.2, y: 111.2, width: 1.6, height: 1.6)), with: drop)
        }
        .flipFade()
    }
}

// MARK: 새 시안 ④ 오늘의 움직임 · 줄다리기: 왼쪽 내린 종목 팀, 오른쪽 오른 종목 팀. 리본이 오늘 등락만큼 끌려가고 인물은 심판

struct TugCharView: View {
    let e: CharEntry
    @Environment(\.widgetRenderingMode) private var mode
    var body: some View {
        StyleReader { dark in
            let s = CharStyle(c: e.c, locked: e.locked, dark: dark), r = e.r
            ZStack {
                TugRope(chg: e.locked ? 0 : r.dayChg).charBleed()
                if !e.locked {
                    team(r.tiles.filter { $0.c < 0 }, up: false).charPin(.topLeading, y: 64)
                    team(r.tiles.filter { $0.c > 0 }, up: true).charPin(.topTrailing, x: 4, y: 64)
                }
                CharSprite(name: poseOr(e.c, Store.isReloading ? "pose_tug_refresh" : abs(r.dayChg) < 0.0005 ? "pose_tug_tie" : r.dayChg > 0 ? "pose_tug_right" : "pose_tug_left"),
                           locked: e.locked, height: 64, shake: false).charPin(.bottomLeading, x: 44, y: 8)
                VStack(alignment: .leading, spacing: 0) {
                    Text(WL.t("오늘의 움직임")).font(.system(size: 12, weight: .semibold)).foregroundStyle(s.heading).lineLimit(1).frame(height: 20)
                    Text(e.locked ? lockLine(e.c) : WL.t("다음: ") + (r.next ?? WL.t("예정된 일정 없음"))).font(.system(size: 9, weight: .medium))
                        .foregroundStyle(.white.opacity(0.8)).lineLimit(1).padding(.top, 4)
                }
                .charPin(.topLeading)
                if !e.locked {
                    VStack(alignment: .trailing, spacing: 2) {
                        CharMoney { h in Text(h ? charHidden : eokMan(r.total)).font(.system(size: 12)).lineLimit(1).charFlip(r.total) }
                        Text(pctText(r.dayChg) + " " + WL.t("오늘")).font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(r.dayChg >= 0 ? upC : dnC).lineLimit(1).widgetAccentable().charFlip(r.dayChg)
                    }
                    .charPin(.bottomTrailing, x: 12)
                }
                cornerB(e)
            }
        }
        .speak(speakMoney(e, "char.tug"), kind: "char.tug", money: !e.locked, reload: !e.locked)
    }

    /// 한 팀: 비중 큰 종목 하나 + 나머지는 '외 n' 한 칸
    private func team(_ t: [WReward.Tile], up: Bool) -> some View {
        let all = t.sorted { $0.w > $1.w }, rest = Array(all.dropFirst())
        let w = rest.reduce(0) { $0 + $1.w }, rc = w > 0 ? rest.reduce(0) { $0 + $1.w * $1.c } / w : 0
        return HStack(spacing: 2) {
            if let top = all.first { block(top.t, top.c, width: 38, up: up) }
            if !rest.isEmpty { block(WL.t("외 {n}", ["n": "\(rest.count)"]), rc, width: 26, up: up) }
        }
        .flipFade()
    }

    private func block(_ name: String, _ c: Double, width: CGFloat, up: Bool) -> some View {
        let fill: Color = mode == .accented ? .white.opacity(0.2)
            : up ? Color(red: 0.95, green: 0.35, blue: 0.38).opacity(0.55) : Color(red: 0.42, green: 0.58, blue: 0.95).opacity(0.6)
        return VStack(alignment: .leading, spacing: 0) {
            Text(name).font(.system(size: 8, weight: .semibold)).lineLimit(1).truncationMode(.tail)
            Spacer(minLength: 0)
            Text(pctText(c)).font(.system(size: 9)).lineLimit(1).minimumScaleFactor(0.7).charFlip(c)
        }
        .padding(3).frame(width: width, height: 28, alignment: .leading)
        .background(fill, in: RoundedRectangle(cornerRadius: 6))
    }
}

/// 밧줄(위에서 78pt)과 가운데 점선. 리본은 오늘 1%에 6.7pt, 많아야 40pt 끌려간다
private struct TugRope: View {
    let chg: Double
    var body: some View {
        Canvas { ctx, size in
            let mid = size.width / 2, y: CGFloat = 78
            var c = Path(); c.move(to: CGPoint(x: mid, y: 60)); c.addLine(to: CGPoint(x: mid, y: 98))
            ctx.stroke(c, with: .color(.white.opacity(0.4)), style: StrokeStyle(lineWidth: 1, dash: [2, 2]))
            var rope = Path(); rope.move(to: CGPoint(x: 6, y: y)); rope.addLine(to: CGPoint(x: size.width - 6, y: y))
            ctx.stroke(rope, with: .color(rgb(0xe8d3a8)), lineWidth: 2.5)
            let x = mid + CGFloat(max(-40, min(40, chg * 100 * 6.7))), col = chg >= 0 ? upC : dnC
            var tri = Path(); tri.move(to: CGPoint(x: x, y: y)); tri.addLine(to: CGPoint(x: x - 4, y: y - 8)); tri.addLine(to: CGPoint(x: x + 4, y: y - 8)); tri.closeSubpath()
            ctx.fill(tri, with: .color(col))
            ctx.fill(Path(CGRect(x: x - 1.5, y: y - 4, width: 3, height: 8)), with: .color(col))
        }
        .flipFade()
    }
}

/// CharFrame 이 글자를 늘 밝게 쓰려고 colorScheme 을 .dark 로 덮으므로, 기본·다크 구분은 원래 값을 따로 읽는다
struct StyleReader<Content: View>: View {
    @ViewBuilder var content: (Bool) -> Content
    @Environment(\.charDark) private var dark
    var body: some View { content(dark) }
}

private struct CharDarkKey: EnvironmentKey { static let defaultValue = false }
private struct CharKindKey: EnvironmentKey { static let defaultValue = "" }
extension EnvironmentValues {
    /// 이 화면을 그리는 인물 위젯 종류 (금액 숨기기를 위젯마다 따로 할 때)
    var charKind: String {
        get { self[CharKindKey.self] }
        set { self[CharKindKey.self] = newValue }
    }
    var charDark: Bool {
        get { self[CharDarkKey.self] }
        set { self[CharDarkKey.self] = newValue }
    }
}

/// ↻ 다시 받기: 앱이 써 둔 최신 값으로 위젯을 다시 그린다 (기본 위젯의 버튼과 같은 동작)
/// 서재 위젯(storyOnly)은 시세와 상관이 없어서 시세는 받지 않고 읽던 자리만 다시 그린다
struct CharReload: View {
    var storyOnly = false
    var body: some View {
        let busy = Store.isReloading // 누른 뒤 받는 동안은 '받는 중' 모양
        let icon = Image(systemName: busy ? "ellipsis" : "arrow.clockwise").font(.system(size: 10, weight: .bold))
            .frame(width: 20, height: 20).background(Circle().fill(Color.white.opacity(busy ? 0.35 : 0.18)))
            .accessibilityLabel(WL.t(busy ? "받는 중" : "다시 받기"))
        #if WIDGET_EXT
        if storyOnly { Button(intent: StoryReloadIntent()) { icon }.buttonStyle(.plain) }
        else { Button(intent: ReloadIntent()) { icon }.buttonStyle(.plain) }
        #else
        icon
        #endif
    }
}

extension View {
    /// VoiceOver: 위젯 하나를 한 문장으로 읽는다 (숫자 조각을 따로 읽지 않게). ↻·금액 숨기기는 따로 고를 수 있는 동작으로 남긴다
    /// 기본·잠금화면 위젯(HomeWidgets.swift)도 이것을 쓴다. 앱 미리보기에서는 문장만
    @ViewBuilder func speak(_ label: String, kind: String, money: Bool = true, reload: Bool = true) -> some View {
        let v = accessibilityElement(children: .ignore).accessibilityLabel(label)
        #if WIDGET_EXT
        let reloadName = Text(WL.t("다시 받기")), moneyName = Text(WL.t(Store.isHidden(kind) ? "금액 보이기" : "금액 숨기기"))
        if #available(iOS 18, *) {   // 동작에 App Intent 를 거는 것은 iOS 18 부터. 17 에서는 문장만 읽는다
            switch (reload, money) {
            case (true, true): v.accessibilityAction(named: reloadName, intent: ReloadIntent()).accessibilityAction(named: moneyName, intent: ToggleAmountIntent(kind: kind))
            case (true, false): v.accessibilityAction(named: reloadName, intent: ReloadIntent())
            case (false, true): v.accessibilityAction(named: moneyName, intent: ToggleAmountIntent(kind: kind))
            case (false, false): v
            }
        } else { v }
        #else
        v
        #endif
    }
}

/// 숨긴 금액 자리 (기본 위젯과 같은 모양)
private let charHidden = "••••"

/// 금액: 누르면 숨기고 한 번 더 누르면 보인다 (기본 위젯의 Money 와 같은 동작, 앱 미리보기에서는 그리기만)
struct CharMoney<L: View>: View {
    @Environment(\.charKind) private var kind
    @ViewBuilder let label: (Bool) -> L
    var body: some View {
        #if WIDGET_EXT
        Button(intent: ToggleAmountIntent(kind: kind)) { label(Store.isHidden(kind)) }.buttonStyle(.plain)
        #else
        label(Store.isHidden(kind))
        #endif
    }
}

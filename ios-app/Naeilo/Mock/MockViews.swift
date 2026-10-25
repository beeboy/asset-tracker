#if MOCK
import SwiftUI

// 모의투자 세 화면(관찰·선정·일지)의 공통 부품 · 관찰 · 선정.
// 개발자 전용 화면이라 글은 번역 목록(Localizable)에 넣지 않는다: 모든 글은 mt(…) = Text(verbatim:)

func mt(_ s: String) -> Text { Text(verbatim: s) }

enum MockTab: String, CaseIterable, Identifiable {
    case observe, select, journal
    var id: String { rawValue }
    var name: String {
        switch self {
        case .observe: return "관찰"
        case .select: return "선정"
        case .journal: return "일지"
        }
    }
    var icon: String {
        switch self {
        case .observe: return "binoculars"
        case .select: return "checklist"
        case .journal: return "book.closed"
        }
    }
}

extension MockFmt {
    /// 만원 (만원 아래는 원): "1,240만원"
    static func man(_ x: Double) -> String {
        abs(x) < 1e4 ? won(x) : Int((x / 1e4).rounded()).formatted() + "만원"
    }
    /// 줄 가운데 회색 값: 100만원 넘으면 만원으로
    static func small(_ x: Double) -> String { abs(x) >= 1e6 ? man(x) : won(x) }
    /// 부호 붙인 원: "+12,300원"
    static func signedWon(_ x: Double) -> String { signed(x.rounded()) + "원" }
    /// "2026-10-24" → "10/24"
    static func md(_ day: String) -> String {
        let p = day.split(separator: "-")
        guard p.count == 3, let m = Int(p[1]), let d = Int(p[2]) else { return day }
        return "\(m)/\(d)"
    }
}

enum MockNum {
    /// 입력 칸 글 → 수 (쉼표·긴 빼기 허용, 비면 nil)
    static func parse(_ s: String) -> Double? {
        let t = s.replacingOccurrences(of: ",", with: "").replacingOccurrences(of: "−", with: "-").replacingOccurrences(of: "+", with: "")
            .trimmingCharacters(in: .whitespaces)
        return t.isEmpty ? nil : Double(t)
    }
}

extension MockDesk {
    static func busyName(_ b: String) -> String {
        switch b {
        case "observe": return "훑는"
        case "select": return "고르는"
        case "order": return "주문"
        case "cycle": return "한 바퀴"
        case "hand": return "손 주문"
        default: return b
        }
    }

    /// 오늘 시작 평가액: 오늘 전 마지막 점 (없으면 오늘 첫 점, 장부가 오늘 시작했으면 시작 현금)
    var dayBase: Double {
        let l = ledger(.rule), today = MockFmt.day(Date())
        if MockFmt.day(l.started) == today { return l.startCash }
        let ms = marks
        if let m = ms.last(where: { MockFmt.day($0.at) < today }) { return m.eq }
        return ms.first(where: { MockFmt.day($0.at) == today })?.eq ?? l.startCash
    }

    /// 보유 종목을 산 값 대비 오른 · 그대로 · 내린으로 나눈 평가액과 수
    var moods: (values: [Double], counts: [Int]) {
        var v = [0.0, 0.0, 0.0], n = [0, 0, 0]
        for p in ledger(.rule).positions.values {
            let now = s.prices[p.code] ?? p.avg
            let r = p.avg > 0 ? now / p.avg - 1 : 0
            let k = r > 0.0005 ? 0 : r < -0.0005 ? 2 : 1
            v[k] += Double(p.qty) * now
            n[k] += 1
        }
        return (v, n)
    }
}

// MARK: 요약 박스 (세 탭 위. 홈에서 고른 평가액 박스 모양의 바탕·색, 밝기 모드)

/// 라벨 + 상태 알약 / 큰 숫자 + 옆 숫자 / 도형 한 줄 / 꼬리 두 칸
struct MockBox<Fig: View>: View {
    let label: String
    let big: Text
    let side: String
    var sideColor: Color = BoxInk.sub
    let tailL: Text
    let tailR: String
    @ViewBuilder var shape: Fig

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                mt(label).appFont(13).foregroundStyle(BoxInk.dim).lineLimit(1)
                Spacer(minLength: 4)
                MockStatePill()
            }
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                big.font(.system(size: 30, weight: .heavy)).tracking(-0.5)
                    .lineLimit(1).minimumScaleFactor(0.5).layoutPriority(1)
                mt(side).font(.system(size: 16, weight: .semibold)).foregroundStyle(sideColor).lineLimit(1).minimumScaleFactor(0.7)
            }
            shape
            HStack(spacing: 8) {
                tailL.appFont(13).lineLimit(1).minimumScaleFactor(0.7)
                Spacer(minLength: 4)
                mt(tailR).appFont(13).foregroundStyle(BoxInk.dim).lineLimit(1).minimumScaleFactor(0.7)
            }
        }
        .padding(.horizontal, 18).padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// 몫 띠 한 줄 (전체 중 몫만큼 칠한다)
struct MockShareBar: View {
    let value: Double
    let color: Color
    var minShare = 0.0
    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(BoxInk.ink.opacity(0.12))
                if value > 0 {
                    Capsule().fill(color).frame(width: g.size.width * max(minShare, min(1, value)))
                }
            }
        }
        .frame(height: 10)
    }
}

/// 탭마다 요약 박스
struct MockTabBox: View {
    let tab: MockTab
    @AppStorage(BoxLook.key) private var lookRaw = BoxLook.jump.rawValue

    var body: some View {
        let d = MockDesk.shared
        Group {
            switch tab {
            case .observe: observe(d)
            case .select: select(d)
            case .journal: journal(d)
            }
        }
        .background { MoodBackdrop(look: BoxLook(rawValue: lookRaw) ?? .jump) }
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .boxScheme()
    }

    private func observe(_ d: MockDesk) -> some View {
        let n = d.conds.filter(\.on).count
        let sc = d.s.scan
        let hits = sc?.hits.count ?? 0, total = sc?.total ?? MockDesk.universe.count
        let r = total > 0 ? Double(hits) / Double(total) : 0
        return MockBox(label: "조건 \(n)개 · 국내 전체",
                       big: mt("\(hits)종목").foregroundColor(BoxInk.ink),
                       side: "\(total.formatted()) 중",
                       tailL: mt("걸림 " + String(format: "%.1f%%", r * 100)).foregroundColor(BoxInk.sub),
                       tailR: sc.map { MockFmt.time($0.at) + " · 일봉 " + MockFmt.md($0.barDay) } ?? "아직 안 훑음") {
            MockShareBar(value: r, color: BoxInk.good, minShare: hits > 0 ? 0.06 : 0)
        }
    }

    private func select(_ d: MockDesk) -> some View {
        let l = d.ledger(.rule)
        let waitBuys = Set(l.pending.filter { $0.side == .buy && l.positions[$0.code] == nil }.map(\.code))
        let n = l.positions.count + waitBuys.count + d.s.picks.count
        let bought = l.positions.values.reduce(0) { $0 + $1.cost }
        let all = bought + l.cash + l.reserved
        let r = all > 0 ? bought / all : 0
        return MockBox(label: "고른 종목",
                       big: mt("\(n)종목").foregroundColor(BoxInk.ink),
                       side: MockFmt.man(bought),
                       tailL: mt("산 돈 " + String(format: "%.0f%%", r * 100)).foregroundColor(BoxInk.sub),
                       tailR: "현금 " + MockFmt.man(l.cash)) {
            MockShareBar(value: r, color: Theme.gold)
        }
    }

    private func journal(_ d: MockDesk) -> some View {
        let eq = d.equity(.rule), base = d.dayBase
        let mv = eq - base, pct = base > 0 ? mv / base : 0
        let c = mv > 0 ? BoxInk.up : mv < 0 ? BoxInk.down : BoxInk.dim
        let tri = mv > 0 ? "▲ " : mv < 0 ? "▼ " : ""
        let m = d.moods
        let sum = m.values.reduce(0, +)
        let shares = sum > 0 ? m.values.map { $0 / sum } : [0, 0, 0]
        let look = BoxLook(rawValue: lookRaw) ?? .jump
        return MockBox(label: "오늘 일지 · 수수료·세금 뒤",
                       big: (mt(tri).font(.system(size: 21, weight: .heavy)) + mt(MockFmt.man(abs(mv)))).foregroundColor(c),
                       side: MockFmt.pct(pct), sideColor: c,
                       tailL: mt("오른 \(m.counts[0]) · 그대로 \(m.counts[1]) · 내린 \(m.counts[2])").foregroundColor(BoxInk.sub),
                       tailR: "평가액 " + MockFmt.man(eq)) {
            MoodBand(look: look, shares: shares, up: mv >= 0)
        }
    }
}

/// 상태 알약: 도는 중 · 정상 n분 전 · 장 밖(시험) · 장 확인 못 함. 1분 주기는 보이지 않는다 (깜빡이지 않게)
struct MockStatePill: View {
    var body: some View {
        TimelineView(.everyMinute) { ctx in
            let (t, c) = state(ctx.date)
            HStack(spacing: 4) {
                if MockDesk.shared.busy != nil { ProgressView().controlSize(.mini) }
                mt(t).appFont(11, .semibold)
            }
            .foregroundStyle(c)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .overlay(Capsule().stroke(c.opacity(0.6)))
        }
    }

    private func state(_ now: Date) -> (String, Color) {
        let d = MockDesk.shared
        if let b = d.busy { return (MockDesk.busyName(b) + " 중", BoxInk.gold) }
        switch d.phase {
        case .open:
            guard let at = d.s.pricesAt ?? d.s.runs["observe"]?.at else { return ("정상", BoxInk.good) }
            let n = max(0, Int(now.timeIntervalSince(at) / 60))
            return ("정상 · " + (n < 1 ? "방금" : "\(n)분 전"), BoxInk.good)
        case .closed: return ("장 밖 · 시험", BoxInk.dim)
        case .unknown: return ("장 확인 못 함", BoxInk.up)
        }
    }
}

// MARK: 공통 부품

/// teal 채움 작은 알약 (36 높이). 단추 일이 도는 동안만 막힌다 (1분 주기로는 막히지 않는다)
struct MockGoPill: View {
    let title: String
    var enabled = true
    let action: () async -> Void
    var body: some View {
        let blocked = MockDesk.shared.busy != nil || !enabled
        Button { Task { await action() } } label: {
            mt(title).appFont(14, .bold).foregroundStyle(.white).lineLimit(1)
                .padding(.horizontal, 16).frame(height: 36)
                .background(blocked ? Theme.muted : Theme.teal, in: Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(blocked)
    }
}

/// 조건 알약 (보라). 누르면 켜고 끈다, 길게 누르면 고치기·지우기
struct MockCondPill: View {
    let text: String
    var on = true
    var tap: (() -> Void)? = nil
    var edit: (() -> Void)? = nil
    let delete: () -> Void
    var body: some View {
        mt(text).appFont(13, .semibold).lineLimit(1)
            .foregroundStyle(on ? Theme.rule : Theme.muted)
            .padding(.horizontal, 12).frame(minHeight: 32)
            .background(on ? Theme.rule.opacity(0.12) : Theme.card, in: Capsule())
            .overlay(Capsule().stroke(on ? Theme.rule.opacity(0.7) : Theme.border))
            .contentShape(Capsule())
            .onTapGesture { tap?() }
            .contextMenu {
                if let edit {
                    Button { edit() } label: { Label { mt("고치기") } icon: { Image(systemName: "pencil") } }
                }
                Button(role: .destructive) { delete() } label: { Label { mt("지우기") } icon: { Image(systemName: "trash") } }
            }
            .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// '+ 조건' (보라 점선)
struct MockAddPill: View {
    var title = "+ 조건"
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            mt(title).appFont(13, .semibold).foregroundStyle(Theme.rule)
                .padding(.horizontal, 12).frame(minHeight: 32)
                .overlay(Capsule().stroke(Theme.rule.opacity(0.7), style: StrokeStyle(lineWidth: 1.2, dash: [4, 3])))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// 펼침 안 종류 고르기 알약
struct MockKindChip: View {
    let name: String
    let on: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            mt(name).appFont(13, .semibold).lineLimit(1)
                .foregroundStyle(on ? .white : Theme.sub)
                .padding(.horizontal, 12).frame(minHeight: 32)
                .background(on ? Theme.rule : Theme.card, in: Capsule())
                .overlay(Capsule().stroke(on ? Theme.rule : Theme.border))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// 수 입력 칸
struct MockNumField: View {
    @Binding var text: String
    var width: CGFloat = 72
    var body: some View {
        TextField("비움" as String, text: $text)
            .keyboardType(.numbersAndPunctuation).multilineTextAlignment(.trailing)
            .appFont(14).monospacedDigit()
            .frame(width: width)
            .padding(.horizontal, 8).frame(height: 34)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
    }
}

/// 펼침 아래 '닫기 · 넣기'
struct MockFormButtons: View {
    let commitTitle: String
    let canCommit: Bool
    let close: () -> Void
    let commit: () -> Void
    var body: some View {
        HStack(spacing: 8) {
            Spacer()
            Button(action: close) {
                mt("닫기").appFont(14, .semibold).foregroundStyle(Theme.sub)
                    .padding(.horizontal, 16).frame(height: 36)
                    .overlay(Capsule().stroke(Theme.border)).contentShape(Capsule())
            }
            .buttonStyle(.plain)
            Button(action: commit) {
                mt(commitTitle).appFont(14, .bold).foregroundStyle(.white)
                    .padding(.horizontal, 16).frame(height: 36)
                    .background(canCommit ? Theme.teal : Theme.muted, in: Capsule()).contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .disabled(!canCommit)
        }
    }
}

/// 관찰·예외 조건 하나를 고치는 중인 값
struct MockPieceDraft: Equatable {
    var edit: UUID? = nil           // 고치는 조건 (nil = 새로)
    var kind: MockPiece.Kind = .change
    var lo = ""
    var hi = ""
    var days = "20"
    var above = true

    init() {}
    init(_ p: MockPiece) {
        edit = p.id; kind = p.kind
        lo = p.lo.map(MockFmt.num) ?? ""; hi = p.hi.map(MockFmt.num) ?? ""
        days = "\(p.days)"; above = p.above
    }

    /// 넣을 조건. 값이 하나도 없으면 nil (이동평균은 값이 없다)
    var piece: MockPiece? {
        var x = MockPiece(id: edit ?? UUID(), kind: kind, days: min(120, max(2, Int(days) ?? 20)), above: above)
        if kind != .ma {
            x.lo = MockNum.parse(lo); x.hi = MockNum.parse(hi)
            if x.lo == nil && x.hi == nil { return nil }
        }
        if let a = x.lo, let b = x.hi, a > b { (x.lo, x.hi) = (b, a) }
        return x
    }

    static func unit(_ k: MockPiece.Kind) -> String {
        switch k {
        case .price: return "원"
        case .change, .rise: return "%"
        case .volume: return "배"
        case .ma: return ""
        case .amount: return "억"
        }
    }
}

/// 조건 펼침: 종류 알약 → 값 → 닫기·넣기 (별도 화면 없이 카드 아래에)
struct MockPieceForm: View {
    @Binding var draft: MockPieceDraft
    var kinds: [MockPiece.Kind] = [.price, .change, .volume, .ma, .rise]
    let close: () -> Void
    let commit: (MockPiece) -> Void

    var body: some View {
        let ks = kinds.contains(draft.kind) ? kinds : kinds + [draft.kind]
        VStack(alignment: .leading, spacing: 10) {
            FlowRow(spacing: 6) {
                ForEach(ks) { k in
                    MockKindChip(name: k.name, on: draft.kind == k) { draft.kind = k }
                }
            }
            fields
            MockFormButtons(commitTitle: draft.edit == nil ? "넣기" : "고치기", canCommit: draft.piece != nil, close: close) {
                if let p = draft.piece { commit(p) }
            }
        }
        .padding(12)
        .background(Theme.rule.opacity(0.06), in: RoundedRectangle(cornerRadius: 14))
    }

    @ViewBuilder private var fields: some View {
        let u = MockPieceDraft.unit(draft.kind)
        if draft.kind == .ma {
            HStack(spacing: 6) {
                MockNumField(text: $draft.days, width: 48)
                mt("일선").appFont(14)
                Spacer()
                mt("가격이").appFont(13).foregroundStyle(Theme.sub)
                HoldSeg(items: [(true, "위"), (false, "아래")], selection: $draft.above)
            }
        } else {
            if draft.kind == .volume || draft.kind == .rise {
                HStack(spacing: 6) {
                    MockNumField(text: $draft.days, width: 48)
                    mt(draft.kind == .volume ? "일 평균과 견줘" : "일 동안").appFont(14).foregroundStyle(Theme.sub)
                }
            }
            HStack(spacing: 6) {
                MockNumField(text: $draft.lo)
                mt(u + " 이상").appFont(14)
                Spacer(minLength: 4)
                MockNumField(text: $draft.hi)
                mt(u + " 이하").appFont(14)
            }
        }
    }
}

/// 고르기 조건 하나를 고치는 중인 값
struct MockRuleDraft: Equatable {
    var edit: UUID? = nil
    var kind: MockRuleCond.Kind = .per
    var sort: MockPiece.Kind = .volume
    var capKind: MockPiece.Kind = .volume
    var n = "5"
    var pct = "20"
    var lim = "20"

    init() {}
    init(_ c: MockRuleCond) {
        edit = c.id; kind = c.kind; sort = c.sort; capKind = c.capKind
        n = "\(c.n)"; pct = MockFmt.num((c.pct * 100).rounded()); lim = MockFmt.num(c.lim)
    }

    var cond: MockRuleCond? {
        var c = MockRuleCond(id: edit ?? UUID(), kind: kind)
        switch kind {
        case .top:
            guard let x = Int(n), x >= 1, x <= 30 else { return nil }
            c.n = x; c.sort = sort
        case .per, .cash:
            guard let x = MockNum.parse(pct), x >= (kind == .per ? 1 : 0), x <= 100 else { return nil }
            c.pct = x / 100
        case .cap:
            guard let x = MockNum.parse(lim), x > 0 else { return nil }
            c.capKind = capKind; c.lim = x
        }
        return c
    }
}

/// 고르기 조건 펼침
struct MockRuleForm: View {
    @Binding var draft: MockRuleDraft
    let close: () -> Void
    let commit: (MockRuleCond) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            FlowRow(spacing: 6) {
                ForEach(MockRuleCond.Kind.allCases) { k in
                    MockKindChip(name: k.name, on: draft.kind == k) { draft.kind = k }
                }
            }
            fields
            MockFormButtons(commitTitle: draft.edit == nil ? "넣기" : "고치기", canCommit: draft.cond != nil, close: close) {
                if let c = draft.cond { commit(c) }
            }
        }
        .padding(12)
        .background(Theme.rule.opacity(0.06), in: RoundedRectangle(cornerRadius: 14))
    }

    @ViewBuilder private var fields: some View {
        switch draft.kind {
        case .top:
            FlowRow(spacing: 6) {
                ForEach([MockPiece.Kind.volume, .change, .amount, .rise]) { k in
                    MockKindChip(name: k.name + " 순", on: draft.sort == k) { draft.sort = k }
                }
            }
            HStack(spacing: 6) {
                mt("상위").appFont(14)
                MockNumField(text: $draft.n, width: 48)
                mt("종목").appFont(14)
            }
        case .per:
            HStack(spacing: 6) {
                mt("종목당 ≤").appFont(14)
                MockNumField(text: $draft.pct, width: 56)
                mt("% (평가액 대비)").appFont(14)
            }
        case .cash:
            HStack(spacing: 6) {
                mt("현금").appFont(14)
                MockNumField(text: $draft.pct, width: 56)
                mt("% 남김").appFont(14)
            }
        case .cap:
            HoldSeg(items: [(MockPiece.Kind.volume, "거래량"), (.rise, "최근 5일")], selection: $draft.capKind)
            HStack(spacing: 6) {
                mt("≤").appFont(14)
                MockNumField(text: $draft.lim, width: 56)
                mt(draft.capKind == .rise ? "% 넘으면 거름" : "배 넘으면 거름").appFont(14)
            }
        }
    }
}

/// 팔기 조건 하나를 고치는 중인 값
struct MockSellDraft: Equatable {
    var edit: UUID? = nil
    var kind: MockSellCond.Kind = .loss
    var pct = "5"
    var n = "5"

    init() {}
    init(_ c: MockSellCond) {
        edit = c.id; kind = c.kind
        pct = MockFmt.num((c.pct * 100).rounded()); n = "\(c.n)"
    }

    var cond: MockSellCond? {
        var c = MockSellCond(id: edit ?? UUID(), kind: kind)
        switch kind {
        case .loss, .gain:
            guard let x = MockNum.parse(pct), x >= 1, x <= 90 else { return nil }
            c.pct = x / 100
        case .days:
            guard let x = Int(n), x >= 1, x <= 60 else { return nil }
            c.n = x
        }
        return c
    }
}

/// 팔기 조건 펼침
struct MockSellForm: View {
    @Binding var draft: MockSellDraft
    let close: () -> Void
    let commit: (MockSellCond) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            FlowRow(spacing: 6) {
                ForEach(MockSellCond.Kind.allCases) { k in
                    MockKindChip(name: k.name, on: draft.kind == k) { draft.kind = k }
                }
            }
            HStack(spacing: 6) {
                switch draft.kind {
                case .loss:
                    mt("산 값보다").appFont(14)
                    MockNumField(text: $draft.pct, width: 56)
                    mt("% 내리면").appFont(14)
                case .gain:
                    mt("산 값보다").appFont(14)
                    MockNumField(text: $draft.pct, width: 56)
                    mt("% 오르면").appFont(14)
                case .days:
                    mt("산 뒤 거래일").appFont(14)
                    MockNumField(text: $draft.n, width: 48)
                    mt("일 넘으면").appFont(14)
                }
            }
            MockFormButtons(commitTitle: draft.edit == nil ? "넣기" : "고치기", canCommit: draft.cond != nil, close: close) {
                if let c = draft.cond { commit(c) }
            }
        }
        .padding(12)
        .background(Theme.rule.opacity(0.06), in: RoundedRectangle(cornerRadius: 14))
    }
}

/// 상태 한 줄 (성공은 회색, 실패는 빨강)
struct MockRunLine: View {
    let run: MockRun?
    var empty = "아직 돌리지 않았어요"
    var body: some View {
        mt(run?.text ?? empty).appFont(12).foregroundStyle(run?.ok == false ? Theme.up : Theme.muted)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// 누가 냈나 · 시험 표시
struct MockTag: View {
    let text: String
    var color: Color = Theme.sub
    var body: some View {
        mt(text).appFont(11, .semibold).foregroundStyle(color)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 5))
    }
    static func who(_ w: MockWho) -> MockTag {
        MockTag(text: w.name, color: w == .rule ? Theme.rule : w == .hand ? Theme.orange : Theme.blue)
    }
}

/// 종목 로고 (앱에 로고가 없으면 이름 첫 글자)
struct MockLogo: View {
    let code: String
    let name: String
    var body: some View {
        if Sample.profiles[code] != nil {
            LogoTile(symbol: code, size: 28)
        } else {
            mt(String(name.prefix(1))).font(.system(size: 13, weight: .heavy)).foregroundStyle(.white)
                .frame(width: 28, height: 28)
                .background(Color(hex: 0x5B6670), in: RoundedRectangle(cornerRadius: 28 * 0.28))
                .accessibilityHidden(true)
        }
    }
}

/// 종목 탭 그룹 카드 모양: 머리줄 + 한 줄 행들
struct MockListCard<Head: View, Rows: View>: View {
    @ViewBuilder var head: Head
    @ViewBuilder var rows: Rows
    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) { head }
                .padding(.horizontal, 14).padding(.vertical, 10)
            rows
        }
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }
}

/// 머리줄 오른쪽 등락 (▲ 9pt + %)
struct MockMove: View {
    let r: Double
    var body: some View {
        (mt(r > 0 ? "▲ " : r < 0 ? "▼ " : "").font(.system(size: 9, weight: .bold)) + mt(MockFmt.pct(r, digits: 1)))
            .appFont(14, .semibold).monospacedDigit().lineLimit(1)
            .foregroundStyle(r > 0 ? Theme.up : r < 0 ? Theme.down : Theme.sub)
    }
}

/// 머리줄 정렬 토글: 오른 순 ⇅ / 내린 순 ⇅
struct MockSortToggle: View {
    @Binding var downFirst: Bool
    var body: some View {
        Button { downFirst.toggle() } label: {
            mt(downFirst ? "내린 순 ⇅" : "오른 순 ⇅").appFont(13, .bold).foregroundStyle(Theme.teal)
                .frame(minHeight: 32).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// 빈 카드 한 줄
struct MockEmptyRow: View {
    let text: String
    var body: some View {
        VStack(spacing: 0) {
            Divider().overlay(Theme.line)
            mt(text).appFont(13).foregroundStyle(Theme.muted)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 14).frame(minHeight: 48)
        }
    }
}

/// 종목 한 줄: 로고 · 이름 · 가운데(12 sub) · % · 값 · 오른쪽 알약. 뒤 왼쪽에 수익률 띠 (종목 탭 tint 와 같은 색, 최대 34%)
struct MockStockRow<Right: View>: View {
    let code: String
    let name: String
    let mid: String
    let pct: Double
    var price: String? = nil
    var band: Double = 0           // 띠 몫 (0~1)
    var struck = false
    var colW: [Int: CGFloat] = [:] // 칸 폭 (같은 칸은 줄마다 같은 폭, 오른쪽 맞춤)
    @ViewBuilder var right: Right
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        VStack(spacing: 0) {
            Divider().overlay(Theme.line)
            HStack(spacing: 6) {
                MockLogo(code: code, name: name)
                mt(name).strikethrough(struck).appFont(15, .bold).lineLimit(1).truncationMode(.tail)
                    .frame(minWidth: 56, alignment: .leading).layoutPriority(1)
                Spacer(minLength: 4)
                mt(mid).appFont(12).foregroundStyle(Theme.sub).monospacedDigit().lineLimit(1).holdCol(0, colW)
                mt(MockFmt.pct(pct)).appFont(14, .bold).monospacedDigit().lineLimit(1)
                    .foregroundStyle(pct > 0 ? Theme.up : pct < 0 ? Theme.down : Theme.sub)
                    .holdCol(1, colW)
                if let price { mt(price).appFont(14, .bold).monospacedDigit().lineLimit(1).holdCol(2, colW) }
                right
            }
            .padding(.horizontal, 12).frame(minHeight: 52)
            .background(alignment: .leading) { tint }
            .opacity(struck ? 0.55 : 1)
        }
        .contentShape(Rectangle())
    }

    private var tint: some View {
        let c = pct > 0 ? Color(hex: 0xFF8A80) : pct < 0 ? Color(hex: 0x8CC4F2) : Color(hex: 0xAAB2BE, dark: 0x4A5672)
        let f = max(0.12, min(1, band))
        return GeometryReader { g in
            Rectangle().fill(c).opacity(scheme == .dark ? 0.26 : 0.32).frame(width: g.size.width * 0.34 * f)
        }
        .accessibilityHidden(true)
    }
}

/// 줄 오른쪽 'N주 체결'(금색) · 'N주 대기'(회색)
struct MockQtyPill: View {
    let text: String
    let done: Bool
    var body: some View {
        mt(text).appFont(12, .bold).foregroundStyle(done ? Color.black : Theme.sub).lineLimit(1)
            .padding(.horizontal, 6).padding(.vertical, 3)
            .background(done ? Theme.gold : Theme.track, in: Capsule())
    }
}

extension MockStockRow where Right == EmptyView {
    init(code: String, name: String, mid: String, pct: Double, price: String? = nil, band: Double = 0, struck: Bool = false, colW: [Int: CGFloat] = [:]) {
        self.init(code: code, name: name, mid: mid, pct: pct, price: price, band: band, struck: struck, colW: colW) { EmptyView() }
    }
}

/// 팝업 머리: 제목 왼쪽, 닫기(X) 오른쪽 (SheetTitleBar 와 같은 모양, 글은 번역 목록 밖)
struct MockSheetBar: View {
    let title: String
    let close: () -> Void
    var body: some View {
        HStack(alignment: .center) {
            mt(title).appFont(22, .bold).foregroundStyle(Theme.ink).lineLimit(1)
            Spacer(minLength: 8)
            Button(action: close) {
                Image(systemName: "xmark").font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.sub)
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain).accessibilityLabel(Text(verbatim: "닫기"))
        }
        .padding(.leading, 20).padding(.trailing, 8).padding(.top, 10)
    }
}

/// 탭 화면 틀: 머리 · 요약 박스 · 본문
struct MockScreen<Content: View>: View {
    let tab: MockTab
    var gear: (() -> Void)? = nil
    @ViewBuilder var content: Content
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 0) {
                    AppHeader(tab: tab.name)
                    if let gear {
                        Button(action: gear) {
                            Image(systemName: "gearshape").font(.system(size: 18, weight: .semibold)).foregroundStyle(Theme.sub)
                                .frame(width: 44, height: 44).contentShape(Rectangle())
                        }
                        .buttonStyle(.plain).accessibilityLabel(Text(verbatim: "모의투자 설정"))
                        .padding(.trailing, 8)
                    }
                }
                .padding(.horizontal, -16)
                MockTabBox(tab: tab)
                content
            }
            .screen()
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .task { await MockDesk.shared.refreshPhase() }
    }
}

// MARK: 관찰

struct MockObserveView: View {
    @State private var draft = MockPieceDraft()
    @State private var adding = false
    @State private var more = false
    @State private var downFirst = false
    @State private var colW: [Int: CGFloat] = [:]
    @State private var info: MockStockRef?

    var body: some View {
        let d = MockDesk.shared
        MockScreen(tab: .observe) {
            condCard(d)
            if let sc = d.s.scan { hitsCard(sc) }
        }
        .onPreferenceChange(HoldColW.self) { colW = $0 }
        .sheet(item: $info) { MockStockSheet(code: $0.code, name: $0.name) }
    }

    private func condCard(_ d: MockDesk) -> some View {
        Card {
            FlowRow(spacing: 6) {
                ForEach(d.conds) { c in
                    MockCondPill(text: c.piece.text, on: c.on,
                                 tap: { toggle(d, c) },
                                 edit: { draft = MockPieceDraft(c.piece); adding = true },
                                 delete: { remove(d, c) })
                }
                MockAddPill { draft = MockPieceDraft(); adding = true }
            }
            if adding {
                MockPieceForm(draft: $draft, close: { adding = false }) { commit(d, $0) }
            }
            if let p = d.progress, d.busy == "observe" || d.busy == "cycle" {
                ProgressView(value: Double(p.done), total: Double(max(1, p.total))).tint(Theme.teal)
                mt("일봉 받는 중 \(p.done.formatted()) / \(p.total.formatted())").appFont(12).foregroundStyle(Theme.muted)
            }
            if let r = d.s.runs["observe"], !r.ok { MockRunLine(run: r) }
            HStack {
                Spacer()
                MockGoPill(title: "지금 훑기") { await d.runObserve() }
            }
        }
    }

    private func toggle(_ d: MockDesk, _ c: MockCond) {
        var cs = d.conds
        guard let i = cs.firstIndex(where: { $0.id == c.id }) else { return }
        cs[i].on.toggle()
        d.setConds((cs[i].on ? "켬 · " : "끔 · ") + c.piece.text, cs)
    }

    private func remove(_ d: MockDesk, _ c: MockCond) {
        d.setConds("지움 · " + c.piece.text, d.conds.filter { $0.id != c.id })
        if draft.edit == c.id { adding = false }
    }

    private func commit(_ d: MockDesk, _ p: MockPiece) {
        var cs = d.conds
        if let i = cs.firstIndex(where: { $0.id == p.id }) {
            cs[i].piece = p
            d.setConds("고침 · " + p.text, cs)
        } else {
            cs.append(MockCond(piece: p))
            d.setConds("넣음 · " + p.text, cs)
        }
        adding = false
    }

    private func hitsCard(_ sc: MockScan) -> some View {
        let ch = sc.hits.compactMap(\.change)
        let avg = ch.isEmpty ? 0 : ch.reduce(0, +) / Double(ch.count)
        let mx = ch.map(abs).max() ?? 0
        let sorted = sc.hits.sorted { a, b in
            let x = a.change ?? 0, y = b.change ?? 0
            return downFirst ? x < y : x > y
        }
        let shown = Array(sorted.prefix(more ? 200 : 10))
        return MockListCard {
            mt("걸린 종목 \(sc.hits.count)").appFont(14, .bold)
            MockSortToggle(downFirst: $downFirst)
            Spacer()
            if !ch.isEmpty { MockMove(r: avg) }
        } rows: {
            if sc.hits.isEmpty { MockEmptyRow(text: "걸린 종목이 없어요") }
            ForEach(shown) { h in
                let c = h.change ?? 0
                Button { info = MockStockRef(code: h.code, name: h.name) } label: {
                    MockStockRow(code: h.code, name: h.name, mid: h.vol.map { String(format: "%.1f배", $0) } ?? "",
                                 pct: c, price: MockFmt.won(h.price), band: mx > 0 ? abs(c) / mx : 0, colW: colW)
                }
                .buttonStyle(.plain)
            }
            if sc.hits.count > 10 {
                VStack(spacing: 0) {
                    Divider().overlay(Theme.line)
                    Button { withAnimation(.snappy) { more.toggle() } } label: {
                        mt(more ? "접기" : "\(sc.hits.count - 10)종목 더 보기").appFont(13, .semibold).foregroundStyle(Theme.teal)
                            .frame(maxWidth: .infinity).frame(minHeight: 44).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

// MARK: 선정 (주문 합침)

/// 고른 종목 한 줄: 검토 중 · 대기 · 체결 · 손으로 뺌
struct MockPickRow: Identifiable {
    enum Kind: String { case pick, wait, filled, dropped }
    var code: String
    var name: String
    var qty: Int
    var kind: Kind
    var price: Double            // 지금 가격
    var pct: Double              // 체결 = 산 값 대비, 나머지 = 오늘 등락
    var side: MockSide = .buy
    var orderId: UUID? = nil
    var id: String { code + kind.rawValue + side.rawValue }
}

struct MockSelectView: View {
    @State private var draft = MockRuleDraft()
    @State private var adding = false
    @State private var editRow: MockPickRow?
    @State private var downFirst = false
    @State private var colW: [Int: CGFloat] = [:]

    var body: some View {
        let d = MockDesk.shared
        MockScreen(tab: .select) {
            ruleCard(d)
            picksCard(d)
            cycleCard(d)
        }
        .onPreferenceChange(HoldColW.self) { colW = $0 }
        .sheet(item: $editRow) { r in
            MockRowEdit(row: r).presentationDetents([.large])
        }
    }

    private func ruleCard(_ d: MockDesk) -> some View {
        Card {
            FlowRow(spacing: 6) {
                ForEach(d.rconds) { c in
                    MockCondPill(text: c.text, on: c.on,
                                 tap: { toggle(d, c) },
                                 edit: { draft = MockRuleDraft(c); adding = true },
                                 delete: { remove(d, c) })
                }
                MockAddPill { draft = MockRuleDraft(); adding = true }
            }
            if adding {
                MockRuleForm(draft: $draft, close: { adding = false }) { commit(d, $0) }
            }
            if d.s.scan == nil { mt("관찰 결과가 없어요 · 관찰 탭에서 먼저 훑어요").appFont(12).foregroundStyle(Theme.up) }
            if let r = d.s.runs["select"], !r.ok { MockRunLine(run: r) }
            HStack {
                Spacer()
                MockGoPill(title: "지금 고르기") { await d.runSelect() }
            }
        }
    }

    private func toggle(_ d: MockDesk, _ c: MockRuleCond) {
        var cs = d.rconds
        guard let i = cs.firstIndex(where: { $0.id == c.id }) else { return }
        cs[i].on.toggle()
        d.setRuleConds((cs[i].on ? "켬 · " : "끔 · ") + c.text, cs)
    }

    private func remove(_ d: MockDesk, _ c: MockRuleCond) {
        d.setRuleConds("지움 · " + c.text, d.rconds.filter { $0.id != c.id })
        if draft.edit == c.id { adding = false }
    }

    private func commit(_ d: MockDesk, _ c: MockRuleCond) {
        var cs = d.rconds
        if let i = cs.firstIndex(where: { $0.id == c.id }) {
            var x = c
            x.on = cs[i].on
            cs[i] = x
            d.setRuleConds("고침 · " + c.text, cs)
        } else {
            cs.append(c)
            d.setRuleConds("넣음 · " + c.text, cs)
        }
        adding = false
    }

    private func rows(_ d: MockDesk) -> [MockPickRow] {
        let l = d.ledger(.rule)
        let change = Dictionary((d.s.scan?.hits ?? []).map { ($0.code, $0.change ?? 0) }, uniquingKeysWith: { a, _ in a })
        func px(_ c: String, _ fallback: Double) -> Double { d.s.prices[c] ?? fallback }
        var out: [MockPickRow] = []
        for p in l.positions.values.sorted(by: { $0.opened < $1.opened }) {
            let now = px(p.code, p.avg)
            out.append(MockPickRow(code: p.code, name: p.name, qty: p.qty, kind: .filled, price: now, pct: p.avg > 0 ? now / p.avg - 1 : 0))
        }
        for o in l.pending {
            let now = px(o.code, o.limit ?? 0)
            out.append(MockPickRow(code: o.code, name: o.name, qty: o.qty, kind: .wait, price: now, pct: change[o.code] ?? 0, side: o.side, orderId: o.id))
        }
        for p in d.s.picks {
            out.append(MockPickRow(code: p.code, name: p.name, qty: p.qty, kind: .pick, price: px(p.code, p.price), pct: change[p.code] ?? 0))
        }
        for p in d.dropped {
            out.append(MockPickRow(code: p.code, name: p.name, qty: p.qty, kind: .dropped, price: px(p.code, p.price), pct: change[p.code] ?? 0))
        }
        return out.sorted { downFirst ? $0.pct < $1.pct : $0.pct > $1.pct }
    }

    private func picksCard(_ d: MockDesk) -> some View {
        let rs = rows(d)
        let mx = rs.map { abs($0.pct) }.max() ?? 0
        return MockListCard {
            mt("고른 종목 \(rs.filter { $0.kind != .dropped }.count)").appFont(14, .bold)
            MockSortToggle(downFirst: $downFirst)
            Spacer()
            if let t = d.s.pickedAt { mt(MockFmt.stamp(t) + " 고름").appFont(12).foregroundStyle(Theme.muted) }
        } rows: {
            if rs.isEmpty { MockEmptyRow(text: "아직 고른 종목이 없어요") }
            ForEach(rs) { r in
                Button { if r.kind != .dropped { editRow = r } } label: {
                    MockStockRow(code: r.code, name: r.name, mid: MockFmt.small(r.price), pct: r.pct, price: MockFmt.man(Double(r.qty) * r.price),
                                 band: mx > 0 ? abs(r.pct) / mx : 0, struck: r.kind == .dropped, colW: colW) { pill(r).holdCol(3, colW) }
                }
                .buttonStyle(.plain)
                .disabled(r.kind == .dropped)
            }
            VStack(spacing: 0) {
                Divider().overlay(Theme.line)
                HStack {
                    Spacer()
                    MockGoPill(title: "주문", enabled: !d.s.picks.isEmpty) { await d.orderPicks() }
                }
                .padding(.horizontal, 12).padding(.vertical, 10)
            }
        }
    }

    @ViewBuilder private func pill(_ r: MockPickRow) -> some View {
        switch r.kind {
        case .filled:
            MockQtyPill(text: "\(r.qty)주 체결", done: true)
        case .wait, .pick:
            MockQtyPill(text: (r.side == .sell ? "팔기 " : "") + "\(r.qty)주 대기", done: false)
        case .dropped:
            mt("뺌").appFont(12, .bold).foregroundStyle(Theme.muted).lineLimit(1)
                .padding(.horizontal, 6).padding(.vertical, 3)
                .overlay(Capsule().stroke(Theme.border))
        }
    }

    private func cycleCard(_ d: MockDesk) -> some View {
        Card {
            HStack(spacing: 8) {
                mt("훑기 → 고르기 → 주문").appFont(14, .semibold).foregroundStyle(Theme.sub).lineLimit(1).minimumScaleFactor(0.8)
                Spacer(minLength: 4)
                MockGoPill(title: "지금 한 바퀴") { await d.runCycle() }
            }
        }
    }
}

/// 고른 종목 줄을 눌렀을 때: 검토 중 = 주수 고치기·빼기, 체결 = 주수 고치기·빼기(손 매매), 대기 = 지정가·취소
struct MockRowEdit: View {
    let row: MockPickRow
    @Environment(\.dismiss) private var dismiss
    @State private var qty = ""
    @State private var limit = ""
    @State private var note: String?
    @State private var sending = false

    var body: some View {
        let d = MockDesk.shared
        VStack(alignment: .leading, spacing: 14) {
            MockSheetBar(title: row.name) { dismiss() }
            MockStockChart(code: row.code, height: 120).padding(.horizontal, 16)
            VStack(alignment: .leading, spacing: 12) {
                switch row.kind {
                case .pick, .filled:
                    mt(row.kind == .pick ? "검토 중 · 주문 전" : "체결 · 지금 \(row.qty)주").appFont(13).foregroundStyle(Theme.muted)
                    HStack(spacing: 6) {
                        mt("주수").appFont(15)
                        Spacer()
                        MockNumField(text: $qty, width: 90)
                        mt("주").appFont(15)
                    }
                    actionButton(row.kind == .pick ? "고치기" : "주수 맞추기", color: Theme.teal, enabled: (Int(qty) ?? 0) >= 1) { await setQty(d) }
                    actionButton("빼기", color: Theme.up, enabled: true) { await drop(d) }
                case .wait:
                    mt("\(row.side == .sell ? "팔기" : "사기") \(row.qty)주 대기").appFont(13).foregroundStyle(Theme.muted)
                    HStack(spacing: 6) {
                        mt("지정가").appFont(15)
                        Spacer()
                        MockNumField(text: $limit, width: 110)
                        mt("원").appFont(15)
                    }
                    actionButton("지정가로 다시 내기", color: Theme.teal, enabled: (MockNum.parse(limit) ?? 0) > 0) { await relimit(d) }
                    actionButton("취소", color: Theme.up, enabled: true) {
                        if let id = row.orderId { d.cancel(.rule, orderId: id) }
                        dismiss()
                    }
                case .dropped:
                    EmptyView()
                }
                if let note { mt(note).appFont(12).foregroundStyle(Theme.up).fixedSize(horizontal: false, vertical: true) }
            }
            .padding(.horizontal, 20)
            Spacer(minLength: 0)
        }
        .background(Theme.bg)
        .onAppear {
            qty = "\(row.qty)"
            limit = MockFmt.num(row.price)
        }
    }

    private func actionButton(_ title: String, color: Color, enabled: Bool, _ action: @escaping () async -> Void) -> some View {
        Button {
            sending = true
            Task { await action(); sending = false }
        } label: {
            mt(title).appFont(15, .bold).foregroundStyle(.white)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(enabled && !sending ? color : Theme.muted, in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .disabled(!enabled || sending)
    }

    private func done(_ ok: Bool, _ d: MockDesk) {
        if ok { dismiss() } else { note = d.events.last.map { $0.kind.name + " · " + $0.text } ?? "하지 못했어요" }
    }

    private func setQty(_ d: MockDesk) async {
        guard let t = Int(qty), t >= 1 else { return }
        if row.kind == .pick { d.setPickQty(row.code, t); dismiss(); return }
        if t == row.qty { dismiss(); return }
        let ok: Bool
        if t < row.qty { ok = await d.runHand(.sell, code: row.code, name: row.name, qty: row.qty - t, type: .market, limit: nil) }
        else { ok = await d.runHand(.buy, code: row.code, name: row.name, qty: t - row.qty, type: .market, limit: nil) }
        done(ok, d)
    }

    private func drop(_ d: MockDesk) async {
        if row.kind == .pick { d.dropPick(row.code); dismiss(); return }
        let ok = await d.runHand(.sell, code: row.code, name: row.name, qty: nil, type: .market, limit: nil)
        done(ok, d)
    }

    private func relimit(_ d: MockDesk) async {
        guard let id = row.orderId, let lim = MockNum.parse(limit), lim > 0 else { return }
        d.cancel(.rule, orderId: id)
        let ok = await d.runHand(row.side, code: row.code, name: row.name, qty: row.qty, type: .limit, limit: lim)
        done(ok, d)
    }
}

/// 국내 종목 찾기 (KRX 전체 목록)
struct MockStockFinder: View {
    let title: String
    let pick: (MockStock) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var q = ""
    var body: some View {
        let key = StockSearch.norm(q)
        let rows = key.isEmpty ? [] : Array(KrxList.all.lazy.filter { $0.key.contains(key) }.prefix(40))
        NavigationStack {
            List {
                ForEach(rows, id: \.hit.id) { r in
                    Button {
                        pick(MockStock(code: r.hit.id, name: r.hit.name, kospi: r.hit.market == "코스피"))
                        dismiss()
                    } label: {
                        HStack {
                            mt(r.hit.name).foregroundStyle(Theme.ink)
                            Spacer()
                            mt(r.hit.id + " · " + r.hit.market).appFont(12).foregroundStyle(Theme.muted)
                        }
                    }
                }
            }
            .searchable(text: $q, placement: .navigationBarDrawer(displayMode: .always), prompt: Text(verbatim: "이름이나 코드"))
            .navigationTitle(Text(verbatim: title))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button { dismiss() } label: { mt("닫기") } } }
        }
    }
}

// MARK: 종목 상세

/// 상세를 열 종목
struct MockStockRef: Identifiable {
    let code: String
    let name: String
    var id: String { code }
}

/// 종목 상세 (종목 탭 상세와 같은 모양): 지금 가격 · 기간 차트 · 산 값. 1일은 마지막 거래일 1분봉, 나머지는 일봉
struct MockStockSheet: View {
    let code: String
    let name: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let d = MockDesk.shared
        let p = d.ledger(.rule).positions[code]
        let now = d.s.prices[code] ?? p?.avg ?? 0
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                MockSheetBar(title: name) { dismiss() }
                VStack(spacing: 12) {
                    Card {
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            mt(MockFmt.won(now)).appFont(30, .heavy).tracking(-0.5).monospacedDigit().lineLimit(1).minimumScaleFactor(0.6)
                            Spacer(minLength: 4)
                            mt(code).appFont(12, .bold).foregroundStyle(Theme.sub).lineLimit(1).fixedSize()
                                .padding(.horizontal, 9).padding(.vertical, 2).background(Theme.line, in: Capsule())
                        }
                    }
                    MockStockChart(code: code)
                    if let p {
                        let r = p.avg > 0 ? now / p.avg - 1 : 0
                        Card(padding: 0) {
                            VStack(spacing: 0) {
                                kv("산 값", mt(MockFmt.won(p.avg)))
                                Divider().overlay(Theme.line)
                                kv("주수", mt("\(p.qty)주"))
                                Divider().overlay(Theme.line)
                                kv("수익률", mt(MockFmt.pct(r)).foregroundStyle(r > 0 ? Theme.up : r < 0 ? Theme.down : Theme.sub))
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
            .padding(.bottom, 20)
        }
        .background(Theme.bg)
    }

    private func kv(_ k: String, _ v: some View) -> some View {
        HStack(alignment: .firstTextBaseline) { mt(k).foregroundStyle(Theme.sub); Spacer(); v.fontWeight(.semibold).monospacedDigit() }
            .appFont(14).padding(.horizontal, 16).frame(minHeight: 46)
    }
}

/// 기간 차트 카드. 열 때 한 번 받는다 (1분봉은 서버가 1분, 일봉은 앱이 그날 동안 다시 쓴다)
struct MockStockChart: View {
    let code: String
    var height: CGFloat = 140
    @State private var span = Span.d1
    @State private var day = ""
    @State private var mins: [MockBar] = []
    @State private var days: [MockBar] = []
    @State private var fail: String?
    @State private var loaded = false

    enum Span: CaseIterable, Hashable {
        case d1, w1, m1, m3, m6
        var label: String {
            switch self { case .d1: return "1일"; case .w1: return "1주"; case .m1: return "1개월"; case .m3: return "3개월"; case .m6: return "6개월" }
        }
        var n: Int {
            switch self { case .d1: return 0; case .w1: return 5; case .m1: return 21; case .m3: return 63; case .m6: return 126 }
        }
    }

    private var bars: [MockBar] { span == .d1 ? mins : Array(days.suffix(span.n + 1)) }

    var body: some View {
        let b = bars, pts = b.map(\.c)
        Card {
            if !loaded {
                ProgressView().frame(maxWidth: .infinity, minHeight: height)
            } else if pts.count < 2 {
                mt(fail ?? "봉이 아직 없어요").appFont(14, .bold).foregroundStyle(Theme.sub)
                    .frame(maxWidth: .infinity, minHeight: height)
            } else {
                let S = pts.first!, E = pts.last!, up = E >= S
                TagChart(points: pts, height: height, tags: [
                    .init(at: E, text: MockFmt.won(E), bg: Theme.ink, fg: Theme.card),
                    .init(at: E, dy: 20, text: (up ? "+" : "−") + MockFmt.won(abs(E - S)), bg: up ? Color(hex: 0xFF8A80) : Color(hex: 0x8CC4F2)),
                    .init(at: S, text: MockFmt.won(S), bg: TagChart.gray),
                ], left: label(b.first!), right: label(b.last!),
                         scrub: Scrub(date: { x in date(b[min(b.count - 1, max(0, Int((x * Double(b.count - 1)).rounded())))]) }, value: { MockFmt.won($0) }))
            }
            ChipRow(items: Span.allCases.map { ($0, $0.label) }, selection: $span, fill: true, pad: 4)
        }
        .task(id: code) { await load() }
    }

    private func load() async {
        do {
            let r = try await MockFeed.detail(code)
            day = r.day; mins = r.minutes; days = r.daily
            // 1분봉이 비면(장 전 등) 1주로 연다
            if mins.count < 2 && span == .d1 { span = .w1 }
        } catch {
            fail = (error as? MockFeed.Fail)?.why ?? "봉을 받지 못했어요"
        }
        loaded = true
    }

    private func label(_ b: MockBar) -> String { span == .d1 ? b.d : MockFmt.md(b.d) }

    private func date(_ b: MockBar) -> Date {
        let f = DateFormatter(); f.timeZone = MockFmt.kst; f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = span == .d1 ? "yyyy-MM-dd HH:mm" : "yyyy-MM-dd"
        return f.date(from: span == .d1 ? day + " " + b.d : b.d) ?? Date()
    }
}
#endif

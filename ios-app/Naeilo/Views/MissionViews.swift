import SwiftUI

// 회복 루트 미션 화면 (시안 m1~m4r, nx). 숫자 설명은 모두 계산값, 종목 추천 문구 없음.

struct MissionScreen: View {
    let route: MissionRoute
    var body: some View {
        switch route {
        case .m1: Mission1View()
        case .m1r: Mission1ResultView()
        case .m2: Mission2View()
        case .m2r: Mission2ResultView()
        case .m3: Mission3View()
        case .m3r: Mission3ResultView()
        case .m4: TaxInputView()
        case .m4r: TaxResultView()
        case .nx: AppStartView()
        case .gp1r: GoalHoldResultView()
        case .g1: GoalSetView()
        case .g1r: GoalSetResultView()
        case .g3: GoalMixView()
        case .g3r: GoalMixResultView()
        case .gt: GoalTaxView()
        case .gi: GoalIntermissionView()
        }
    }
}

// 공통 틀
private struct MissionPage<Content: View>: View {
    let kicker: String
    let title: String
    @ViewBuilder var content: Content
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenTitle(kicker: kicker, title: title)
                content
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct BoardButtons: View {
    @Environment(AppModel.self) private var m
    let next: String?
    let to: MissionRoute?
    var body: some View {
        VStack(spacing: 8) {
            if let next, let to { PrimaryButton(title: next) { m.boardPath.append(to) } }
            Button("미션 판으로") { m.boardPath = [] }
                .appFont(15, .semibold).foregroundStyle(Theme.teal).frame(maxWidth: .infinity, minHeight: 44)
        }
    }
}

private func horizonChips(_ sel: Binding<Int>) -> some View {
    ChipRow(items: [(3, "3개월"), (6, "6개월"), (12, "1년")], selection: sel, fill: true)
}

private func statBox(_ k: String, _ v: String, _ c: Color = Theme.teal) -> some View {
    VStack(alignment: .leading, spacing: 4) {
        Text(k).appFont(13).foregroundStyle(Theme.sub)
        Text(v).appFont(26, .bold).foregroundStyle(c)
    }
    .padding(14).frame(maxWidth: .infinity, alignment: .leading)
    .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border))
}

// MARK: 미션 1 — 종목·단가 넣기
// 내 종목에 있는 종목을 그대로 보여 주고 수량·평균 단가를 고친다. 샘플 종목이면 빈칸으로 시작
struct Mission1View: View {
    @Environment(AppModel.self) private var m
    @State private var ids: [String] = []
    @State private var q: [String: String] = [:]
    @State private var p: [String: String] = [:]

    private func num(_ t: String?) -> Double { Double((t ?? "").replacingOccurrences(of: ",", with: "")) ?? 0 }

    var body: some View {
        let ok = ids.contains { num(q[$0]) > 0 && num(p[$0]) > 0 }
        MissionPage(kicker: "미션 1 / 4", title: "종목과 산 가격은?") {
            GuideBubble(pose: .point, text: "매수 단가가 있어야 \"본전\"을 계산할 수 있어요. " + (m.syncOn ? "동기화가 켜져 있어서, 이 폰에서 암호화한 값만 같은 비밀번호의 기기로 가요." : "넣은 숫자는 기기 밖으로 나가지 않아요."))
            ForEach(ids, id: \.self) { id in
                if let s = Sample.symbol(id) { entry(s) }
            }
            // '+종목 추가' 버튼 대신 그 자리에 바로 종목 추가 상자
            AddHoldingView(inline: true)
            PrimaryButton(title: ok ? (m.route == .recover ? "회복 확률 보기" : "평가액 보기") : "수량·단가 넣기", color: ok ? Theme.teal : Theme.muted) {
                guard ok else { return }
                for id in ids {
                    let qn = num(q[id]), pn = num(p[id])
                    if let i = m.holdings.firstIndex(where: { $0.symbol == id }) {
                        if qn > 0 && pn > 0 { m.holdings[i] = Holding(symbol: id, qty: qn, avg: pn) } else { m.holdings.remove(at: i) }
                    } else if qn > 0 && pn > 0 { m.holdings.append(Holding(symbol: id, qty: qn, avg: pn)) }
                }
                if m.route == .recover { m.done.insert(1); m.boardPath.append(.m1r) }
                else { m.gDone.insert("hold"); m.boardPath.append(.gp1r) }
            }
        }
        // 종목 추가에서 돌아오면 새 종목도 칸에 넣는다
        .onAppear { load() }
        .onChange(of: m.holdings) { _, _ in load() }
    }

    private func load() {
        // 샘플(가상 DRNK·QQQ)이면 빈칸으로 시작한다: 종목 추가로 내 종목을 넣는다
        let want = m.isSample ? [] : m.holdings.map(\.symbol).filter { $0 != KoreaEstimate.sumId }   // 국내 주식 합계는 아래 칸에서
        for id in want where !ids.contains(id) {
            ids.append(id)
            if let h = m.holdings.first(where: { $0.symbol == id }) { q[id] = h.qty.formatted(.number.grouping(.never)); p[id] = h.avg.formatted(.number.grouping(.never)) }
        }
    }

    /// 지금 가격 (시세 기준과 같음). DRNK 는 가상 종목
    private func now(_ s: Symbol) -> String {
        (s.quote.live ? "지금 " : "전일 종가 ") + AppModel.price(s, s.last) + (s.id == "DRNK" ? " (가상)" : "")
    }

    private func entry(_ s: Symbol) -> some View {
        let unit = s.currency == .usd ? "$" : "원"
        let qb = Binding(get: { q[s.id] ?? "" }, set: { q[s.id] = $0 }), pb = Binding(get: { p[s.id] ?? "" }, set: { p[s.id] = $0 })
        return Card {
            HStack { LogoTile(symbol: s.id, size: 28); Text(s.short).appFont(17, .bold); Spacer(); Text(now(s)).appFont(13).foregroundStyle(Theme.sub) }
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 10) { field("수량(주)", qb); field("평균 단가(\(unit))", pb) }
                VStack(spacing: 10) { field("수량(주)", qb); field("평균 단가(\(unit))", pb) }
            }
        }
    }

    private func field(_ label: String, _ b: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).appFont(13, .semibold).foregroundStyle(Theme.sub)
            TextField("", text: b).keyboardType(.decimalPad).appFont(18, .semibold)
                .padding(.horizontal, 12).frame(minHeight: 48)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border, lineWidth: 2))
                .accessibilityLabel(label)
        }
    }
}

// MARK: 미션 1 결과 — 회복 확률
struct Mission1ResultView: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        @Bindable var m = m
        let keep = m.keepPlan, T = Double(m.horizon) / 12
        MissionPage(kicker: "미션 1 완료 · 회복 확률 열림", title: "지금 평가액과 본전") {
            if m.ret >= 0 {
                Card {
                    Text("이미 본전 위 (\(AppModel.sgn(m.ret)))").appFont(17, .bold)
                    Text("목표 루트가 맞아요 · 종목·위젯·친구 그대로").appFont(14).foregroundStyle(Theme.sub)
                    PrimaryButton(title: "목표 루트로") { m.graduateToGoal() }
                }
            } else {
                GuideBubble(pose: .spread, text: "…… 여기가 지금 위치예요. 본전까지는 \(AppModel.sgn(m.cost / max(1, m.total) - 1))가 필요해요. 기간을 바꾸면 확률이 다시 계산돼요.")
                VStack(alignment: .leading, spacing: 8) {
                    HStack { Text("지금 평가액"); Spacer(); Text("들어간 돈 \(AppModel.man(m.cost))") }
                        .appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Text(AppModel.man(m.total)).appFont(30, .bold)
                        Text(AppModel.sgn(m.ret)).appFont(16, .bold).foregroundStyle(Color(hex: 0x8CC4F2))
                    }
                    ProgressBar(value: m.total / m.cost)
                    Text("본전의 \(AppModel.pct(min(1, m.total / m.cost))) · 절반 회복 \(AppModel.sgn(m.halfway / m.total - 1)) · 본전 \(AppModel.sgn(m.cost / m.total - 1)) 올라야")
                        .appFont(14).foregroundStyle(Color(hex: 0xC9D0D6)).fixedSize(horizontal: false, vertical: true)
                }
                .foregroundStyle(.white).padding(18)
                .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
                horizonChips($m.horizon)
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 8) { probBoxes(keep, T) }
                    VStack(spacing: 8) { probBoxes(keep, T) }
                }
                Card {
                    Text("\(m.horizonLabel) 뒤 내 손익").appFont(14, .bold)
                    outcomeRow("나쁜 경우", keep.outcome(m.cost, T, -1))
                    outcomeRow("보통", keep.outcome(m.cost, T, 0))
                    outcomeRow("좋은 경우", keep.outcome(m.cost, T, 1))
                    Text("10번 중 9번 이 사이 · 지금 \(AppModel.sgn(m.ret))").appFont(12).foregroundStyle(Theme.muted)
                }
            }
            // 본전 위면 원인 진단은 건너뛴다 (목표 루트로만)
            BoardButtons(next: m.ret >= 0 ? nil : "다음: 원인 진단", to: m.ret >= 0 ? nil : .m2)
        }
    }
    @ViewBuilder private func probBoxes(_ keep: Plan, _ T: Double) -> some View {
        statBox("\(m.horizonLabel) 안 절반 회복", AppModel.pct(keep.prob(m.halfway, T)))
        statBox("\(m.horizonLabel) 안 본전", AppModel.pct(keep.prob(m.cost, T)))
    }
    private func outcomeRow(_ k: String, _ v: Double) -> some View {
        HStack { Text(k).foregroundStyle(Theme.sub); Spacer(); Text(AppModel.sgn0(v)).fontWeight(.bold).foregroundStyle(Theme.change(v)) }
            .appFont(15)
    }
}

// 회복 루트 적립 지렛대 (계획 4안과 별개): 매달 더 넣으면 본전이 언제. 비율·확률로만 보인다
struct DepositLeverCard: View {
    @Environment(AppModel.self) private var m
    let plan: Plan
    var body: some View {
        @Bindable var m = m
        let T = m.horizon, base = m.deposit(plan, monthly: 0, months: T), with = m.deposit(plan, monthly: m.recM, months: T)
        Card {
            Text("매달 더 넣으면?").appFont(14, .bold)
            Text("지금 구성 그대로 · 넣은 만큼 본전도 오름").appFont(12).foregroundStyle(Theme.sub)
                .fixedSize(horizontal: false, vertical: true)
            ChipRow(items: chips, selection: $m.recM, fill: true)
            if m.recM > 0 {
                Text("월 \(AppModel.wonK(Double(m.recM))) · 평가액의 \(AppModel.pct(Double(m.recM) * 1e4 / max(1, m.total)))").appFont(13, .semibold).foregroundStyle(Theme.sub)
            }
            HStack { Text("\(m.horizonLabel) 안 본전").foregroundStyle(Theme.sub); Spacer()
                Text(m.recM == 0 ? AppModel.pct(base.prob) : "\(AppModel.pct(base.prob)) → \(AppModel.pct(with.prob))").fontWeight(.bold) }
                .appFont(15)
            HStack { Text("보통 본전까지").foregroundStyle(Theme.sub); Spacer()
                Text(m.recM == 0 ? eta(base) : "\(eta(base)) → \(eta(with))").fontWeight(.bold) }
                .appFont(15)
            if m.recM > 0 {
                // 넣는 돈이 평가액보다 크면 손실 금액은 그대로인데 비율만 작아져 본전이 빨라 보인다 (회복이 아니라 묽어짐)
                let loss = m.cost - m.total, add = Double(m.recM) * 1e4 * Double(T)
                Text("손실 \(AppModel.man(loss))은 그대로 · \(AppModel.man(add)) 더 넣으면 비율만 \(AppModel.sgn(-loss / max(1, m.cost)))→\(AppModel.sgn(-loss / max(1, m.cost + add))) · 본전이 빨라 보일 뿐")
                    .appFont(12).foregroundStyle(Theme.sub).lineSpacing(2).fixedSize(horizontal: false, vertical: true)
            }
            Text("전망과 같은 3,000번 경로 · 월말 기준 · 지금 비중대로 나눠 넣음").appFont(12).foregroundStyle(Theme.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
    /// 칩: 지금 평가액의 5·10·20·50% (만원 단위로 반올림, 최소 1만원). 작은 계좌에서도 말이 되는 크기
    private var chips: [(Int, String)] {
        var out: [(Int, String)] = [(0, "안 넣음")]
        for r in [5, 10, 20, 50] {
            let v = max(1, Int((m.total * Double(r) / 100 / 1e4).rounded()))
            if !out.contains(where: { $0.0 == v }) { out.append((v, "\(r)%")) }
        }
        return out
    }
    private func eta(_ d: AppModel.Deposit) -> String {
        d.eta == 0 ? "지금" : d.eta.isFinite ? AppModel.eta(d.eta).replacingOccurrences(of: " 뒤", with: "") : "3년 넘게"
    }
}

// MARK: 미션 2 — 원인 진단 퀴즈
struct Mission2View: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        let opts = [("conc", "한 종목에 몰려서"), ("mkt", "시장 전체 하락"), ("fx", "환율")]
        let right = m.quizRight, ans = m.quizAnswer
        MissionPage(kicker: "미션 2 / 4", title: "손실 가장 큰 원인은?") {
            GuideBubble(pose: ans == nil ? .think : ans == right ? .cheer : .point,
                        text: ans == nil ? "감으로 하나 골라 보세요. 정답은 숫자로 확인해요." : ans == right ? "맞아요. 아래 숫자로 확인해 봐요." : "숫자로 보면 조금 달라요. 아래를 같이 봐요.")
            ForEach(opts, id: \.0) { k, label in
                let isRight = k == right, picked = ans == k
                Button { withAnimation { m.quizAnswer = k } } label: {
                    Text(label).appFont(16, .semibold).multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading).padding(.horizontal, 16)
                        .foregroundStyle(ans == nil ? Theme.ink : isRight ? Theme.tealDark : picked ? Theme.sub : Theme.sub2)
                        .background(ans != nil && isRight ? Theme.tealBg : Theme.card, in: RoundedRectangle(cornerRadius: 14))
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(ans == nil ? Theme.dash : isRight ? Theme.teal : picked ? Theme.muted : Theme.track, lineWidth: 2))
                }.buttonStyle(.plain)
            }
            if ans != nil {
                let qRet = m.restRet
                Card {
                    Text(ans == right ? "맞아요" : "숫자로는 달라요").appFont(17, .bold)
                    Text((m.cost > m.total ? "손실 \(AppModel.man(m.cost - m.total)) 중 \(AppModel.pct(m.lossShareFocus))가 \(m.focusName)에서"
                          : "들어간 돈보다 \(AppModel.sgn(m.ret))")
                         + (m.restRows.isEmpty ? "" : " · 같은 기간 \(m.restName) \(AppModel.sgn(qRet))"))
                        .appFont(15).lineSpacing(3)
                    HStack { Text("\(m.focusName) 비중"); Spacer(); Text(AppModel.pct(m.focusWeight)).fontWeight(.bold) }.appFont(14)
                    ProgressBar(value: m.focusWeight, height: 10, fill: Theme.purple, track: Theme.track)
                    PrimaryButton(title: "하나 더 보기") { m.done.insert(2); m.boardPath.append(.m2r) }
                }
            }
        }
    }
}

// MARK: 미션 2 결과 — 흔들림 비용 + 회복 경로
struct Mission2ResultView: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        let keep = m.keepPlan
        MissionPage(kicker: "미션 2 완료 · 회복 경로 열림", title: "흔들리면 보통 결과가 깎여요") {
            GuideBubble(pose: .back, text: "같이 봐요. 한 종목에 몰려 흔들림이 크면, 평균이 같아도 보통의 경우는 낮아져요. -50% 뒤에는 +100%가 있어야 본전이에요.", note: "그래프 쪽을 돌아봄")
            Card {
                let yr = keep.year
                HStack { Text("한 해 흔들림"); Spacer(); Text("±\(AppModel.pct(yr.sigma))").fontWeight(.bold) }.appFont(15)
                HStack { Text("보통 연 성장"); Spacer(); Text(AppModel.sgn(exp(yr.g) - 1)).fontWeight(.bold) }.appFont(15)
                Text("평균 연 \(AppModel.sgn(yr.mean)) · 보통은 \(AppModel.sgn(exp(yr.g) - 1))").appFont(13).foregroundStyle(Theme.sub)
            }
            Card {
                Text("앞으로 1년 내 평가액").appFont(15, .bold)
                PathChart(plans: [(keep, Color(hex: 0x2450C8, dark: 0x7FA2F0), false)], V: m.total, C: m.cost, half: m.halfway).frame(height: 150)
                HStack { Text("지금"); Spacer(); Text("3개월"); Spacer(); Text("6개월"); Spacer(); Spacer(); Text("1년") }.appFont(11).foregroundStyle(Theme.muted)
                FlowRow(spacing: 10) {
                    legend(Color(hex: 0x2450C8, dark: 0x7FA2F0), "보통"); legend(Color(hex: 0x2450C8, dark: 0x7FA2F0).opacity(0.2), "10번 중 9번")
                    legend(Color(hex: 0xC8352E), "본전"); legend(Theme.muted, "손실 절반")
                }
            }
            BoardButtons(next: "다음: 내 계획", to: .m3)
        }
    }
}

private func legend(_ c: Color, _ t: String) -> some View {
    HStack(spacing: 4) { RoundedRectangle(cornerRadius: 3).fill(c).frame(width: 12, height: 12); Text(t) }
        .appFont(12).foregroundStyle(Theme.sub)
}

// MARK: 미션 3 — 계획 고르기 (그래프 고정 + 칩)
struct Mission3View: View {
    @Environment(AppModel.self) private var m
    @State private var open: Set<Basket> = []

    var body: some View {
        @Bindable var m = m
        let sel = m.selectedPlan, T = Double(m.horizon) / 12
        PinnedLayout {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .firstTextBaseline) {
                    Text(sel.name).appFont(17, .bold)
                    Text(sel.tag).appFont(13).foregroundStyle(Theme.sub)
                    Spacer()
                    Text("\(m.horizonLabel) 기준").appFont(13).foregroundStyle(Theme.sub)
                }
                mixBar(sel)
                PathChart(plans: [(m.keepPlan, Theme.muted, true), (sel, Theme.teal, false)], V: m.total, C: m.cost, marker: m.horizon).frame(height: 110)
                Text("점선 = 유지 · 빨간 선 = 본전").appFont(11).foregroundStyle(Theme.muted)
                HStack(spacing: 6) {
                    pill("절반 회복", AppModel.pct(sel.prob(m.halfway, T)))
                    pill("본전", AppModel.pct(sel.prob(m.cost, T)))
                    pill("나쁜 경우", AppModel.sgn0(sel.outcome(m.cost, T, -1)))
                }
                ChipRow(items: m.plans.map { ($0.id, $0.name) }, selection: $m.planKey, fill: true)
                    // 모의 바구니는 고른 계획대로 바꾼 내 종목 비중을 따른다
                    .onChange(of: m.planKey) { _, _ in if m.pworld != nil { m.saveBasket(m.planBasket(m.selectedPlan)) } }
                horizonChips($m.horizon)
            }
            .padding(16).background(Theme.card).overlay(alignment: .bottom) { Divider() }
        } content: {
                VStack(alignment: .leading, spacing: 14) {
                    GuideBubble(pose: .side, text: "갈 길을 하나 골라 보세요. 칩을 누르면 위 그래프가 바로 바뀌어요. 흔들림을 줄이면 빠른 본전 확률도, 더 잃을 확률도 함께 줄어요.")
                    Text(sel.desc).appFont(15).lineSpacing(3)
                    holdingsCard
                    basketsCard(Basket.allCases.filter { $0 != .C })
                    PrimaryButton(title: "\"\(sel.name)\"으로 정하기") { m.done.insert(3); m.boardPath.append(.m3r) }
                }
                .padding(16)
        }
        .background(Theme.bg)
        .navigationTitle("미션 3 / 4").navigationBarTitleDisplayMode(.inline)
    }

    private func pill(_ k: String, _ v: String) -> some View {
        VStack(spacing: 2) { Text(k).appFont(11).foregroundStyle(Theme.sub); Text(v).appFont(16, .bold) }
            .frame(maxWidth: .infinity).padding(.vertical, 6)
            .background(Theme.bg, in: RoundedRectangle(cornerRadius: 10))
    }

    private var holdingsCard: some View {
        // 비중 1위 종목의 업종 (업종 자료가 없는 종목은 이름만)
        let d = m.focusIndustry, sector = m.focusRow?.sym.sector ?? ""
        let b = d.map { Basket.of(pe: $0.pe, pe10: $0.pe10, growth: $0.growth) } ?? "?"
        let ratio = d.map { String(format: "%.1f", $0.pe / $0.pe10) } ?? ""
        let restSub = m.restRows.allSatisfy { $0.sym.sector == "지수" } ? "지수 · 분산" : m.restRows.map(\.sym.short).joined(separator: " · ")
        return Card {
            Text("내 종목 업종").appFont(15, .bold)
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(m.focusName) \(AppModel.pct(m.focusWeight))").appFont(15, .bold)
                    Text(d.map { "\($0.ko) · PER 평균의 \(ratio)배 · 성장 \(Int($0.growth.rounded()))%" } ?? (sector.isEmpty || sector == "지수" ? "업종 자료 없음" : sector))
                        .appFont(13).foregroundStyle(Theme.sub)
                }
                Spacer()
                tag(b == "H" ? "과열" : b == "?" ? (sector == "지수" ? "지수" : "업종 모름") : Basket(rawValue: b)?.name ?? "세 묶음 밖", hot: b == "H", neutral: b == "?")
            }
            if let d, b == "H" {
                Text("PER 10년 평균의 \(ratio)배 · 이익 성장 \(Int(d.growth.rounded()))% · 과열이라 세 묶음 밖")
                    .appFont(13).foregroundStyle(Theme.sub).lineSpacing(2)
            }
            if !m.restRows.isEmpty {
                Divider().overlay(Theme.line)
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(m.restName) \(AppModel.pct(1 - m.focusWeight))").appFont(15, .bold)
                        Text(restSub).appFont(13).foregroundStyle(Theme.sub)
                    }
                    Spacer()
                    tag("\(m.restRows.count)종목", hot: false, neutral: true)
                }
            }
        }
    }

    private func tag(_ t: String, hot: Bool, neutral: Bool = false) -> some View {
        Text(t).appFont(12, .bold).padding(.horizontal, 8).padding(.vertical, 3)
            .foregroundStyle(neutral ? Theme.sub : hot ? Color(hex: 0xA3291F, dark: 0xF08A80) : Theme.tealDark)
            .background(neutral ? Theme.track : hot ? Color(hex: 0xFBE9E7, dark: 0x3D1E1B) : Theme.tealBg, in: Capsule())
    }

    func basketsCard(_ list: [Basket]) -> some View {
        BasketList(baskets: list, open: $open)
    }
}

struct BasketList: View {
    let baskets: [Basket]
    @Binding var open: Set<Basket>
    var title = "업종 묶음"
    var body: some View {
        Card(padding: 14) {
            HStack(alignment: .firstTextBaseline) {
                Text(title).appFont(14, .bold)
                Spacer()
                Text("PER 지금 / 10년 평균 · 이익 성장").appFont(11).foregroundStyle(Theme.muted)
            }
            ForEach(baskets, id: \.self) { b in
                let list = b.industries, isOpen = open.contains(b)
                Button { withAnimation { if isOpen { open.remove(b) } else { open.insert(b) } } } label: {
                    HStack(spacing: 8) {
                        RoundedRectangle(cornerRadius: 3).fill(b.color).frame(width: 12, height: 12)
                        Text("\(b.name) \(list.count)").appFont(14, .bold)
                        Text(list.prefix(3).map(\.ko).joined(separator: " · ")).appFont(12).foregroundStyle(Theme.sub).lineLimit(1)
                        Spacer(minLength: 4)
                        Text(isOpen ? "⌃" : "⌄").appFont(12).foregroundStyle(Theme.teal)
                    }
                    .frame(minHeight: 44).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isOpen ? .isSelected : [])
                if isOpen {
                    Text(b.rule).appFont(12).foregroundStyle(Theme.sub)
                    ForEach(list, id: \.key) { r in
                        HStack {
                            Text(r.ko).appFont(13)
                            Spacer()
                            Text(String(format: "%.1f / %.1f", r.pe, r.pe10)).appFont(12).foregroundStyle(Theme.sub).monospacedDigit()
                            Text("+\(Int(r.growth.rounded()))%").appFont(12, .semibold).frame(minWidth: 44, alignment: .trailing)
                        }
                    }
                }
            }
            Text("다모다란 업종 자료 26.1 · 10곳 미만 제외 · 상품 추천 아님").appFont(11).foregroundStyle(Theme.muted)
        }
    }
}

// 계획 비중 막대: 몰린 종목 + 묶음
private func mixBar(_ p: Plan) -> some View {
    let rest = 1 - p.wt
    let segs: [(Color, Double)] = [(Theme.purple, p.wt)] + Basket.allCases.map { ($0.color, rest * (p.mix[$0] ?? 0)) }
    return VStack(alignment: .leading, spacing: 6) {
        GeometryReader { g in
            HStack(spacing: 0) {
                ForEach(segs.indices, id: \.self) { i in
                    Rectangle().fill(segs[i].0).frame(width: g.size.width * segs[i].1)
                        .overlay(alignment: .trailing) { Rectangle().fill(Theme.card).frame(width: segs[i].1 > 0 ? 2 : 0) }
                }
            }
        }
        .frame(height: 10).clipShape(Capsule()).background(Theme.track, in: Capsule())
        FlowRow(spacing: 8) {
            legend(Theme.purple, p.focus)
            ForEach(Basket.allCases, id: \.self) { legend($0.color, $0.name) }
        }
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel("\(p.focus) \(AppModel.pct(p.wt))" + Basket.allCases.compactMap { b in p.mix[b].map { ", \(b.name) \(AppModel.pct(rest * $0))" } }.joined())
}

// MARK: 미션 3 결과 — 비중과 확률 (금액·종목별 매매 안내 없이 비율로만)
struct Mission3ResultView: View {
    @Environment(AppModel.self) private var m
    @State private var open: Set<Basket> = []
    var body: some View {
        let sel = m.selectedPlan, keep = m.keepPlan, T = Double(m.horizon) / 12, freed = m.freedAmount
        let baskets = Basket.allCases.filter { $0 != .C && (sel.mix[$0] ?? 0) > 0 }
        MissionPage(kicker: "미션 3 완료 · 비중 비교 열림", title: "내 계획: \(sel.name)") {
            GuideBubble(pose: .cheer, text: "정했어요. 이 계획은 기록만 해요. 실제 매매 판단은 직접 하셔야 해요.")
            Card {
                Text("\(m.horizonLabel) 안 절반 회복 \(AppModel.pct(sel.prob(m.halfway, T))) · 본전 \(AppModel.pct(sel.prob(m.cost, T))) · 나쁜 경우 \(AppModel.sgn0(sel.outcome(m.cost, T, -1)))")
                    .appFont(15, .semibold).lineSpacing(3)
            }
            Card {
                Text("비중이 바뀌면").appFont(15, .bold)
                moveRow("가장 큰 종목", freed > 0 ? "\(Int((m.focusWeight * 100).rounded()))→\(AppModel.pct(sel.wt))" : AppModel.pct(m.focusWeight),
                        freed > 0 ? Self.pp(sel.wt - m.focusWeight) : "그대로", up: false)
                if !m.restRows.isEmpty { moveRow("나머지", AppModel.pct(1 - m.focusWeight), "그대로", up: true) }
                ForEach(Basket.allCases.filter { (sel.mix[$0] ?? 0) > 0 && sel.id != "keep" }, id: \.self) { b in
                    let r = freed * (sel.mix[b] ?? 0) / max(1, m.total)
                    moveRow(b.name, "0→\(AppModel.pct(r))", Self.pp(r), up: true)
                }
                if sel.id != "keep" {
                    Text("\(m.horizonLabel) 안 본전 \(AppModel.pct(keep.prob(m.cost, T)))→\(AppModel.pct(sel.prob(m.cost, T))) · 나쁜 경우 \(AppModel.sgn0(keep.outcome(m.cost, T, -1)))→\(AppModel.sgn0(sel.outcome(m.cost, T, -1)))")
                        .appFont(13).foregroundStyle(Theme.sub).lineSpacing(2)
                }
                Text(sel.id == "keep" ? "지금 구성 그대로" : "적립·매도 중 맞추는 방법 따라 세금 다름 · 절세 화면")
                    .appFont(12).foregroundStyle(Theme.muted)
            }
            if !baskets.isEmpty && sel.id != "keep" { BasketList(baskets: baskets, open: $open, title: "담을 업종") }
            // 원인 진단·계획을 마친 뒤에 '매달 더 넣으면?' (2026-10-11 결정: 미션 3 뒤)
            if m.cost > m.total { DepositLeverCard(plan: keep) }
            Text("계산 예시 · 매매 추천 아님").appFont(14).foregroundStyle(Theme.sub)
            PrimaryButton(title: "다음: 앱 시작 3단계") { m.boardPath.append(.nx) }
            Button { m.boardPath.append(.m4) } label: {
                Text("해외주식 이익 있으면: 손실로 절세").appFont(15, .semibold).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, minHeight: 50).padding(.horizontal, 10)
                    .foregroundStyle(Theme.teal)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 2))
            }.buttonStyle(.plain)
            BoardButtons(next: nil, to: nil)
        }
    }
    private func moveRow(_ label: String, _ sub: String, _ amt: String, up: Bool) -> some View {
        HStack {
            Text(label).appFont(15, .semibold)
            Text(sub).appFont(13).foregroundStyle(Theme.sub)
            Spacer()
            Text(amt).appFont(15, .bold).foregroundStyle(up ? Theme.teal : Color(hex: 0x2450C8, dark: 0x7FA2F0)).monospacedDigit()
        }
        .frame(minHeight: 36)
    }
    /// 비중 차이 %p (+10%p, -21%p)
    static func pp(_ d: Double) -> String {
        let v = Int((d * 100).rounded())
        return v == 0 ? "그대로" : (v > 0 ? "+" : "") + "\(v)%p"
    }
}

// MARK: 절세 (대한민국 거주자)
struct TaxInputView: View {
    @Environment(AppModel.self) private var m
    @State private var gain = ""
    var body: some View {
        MissionPage(kicker: "절세 · 한국 거주자", title: "올해 판 해외주식 이익은?") {
            GuideBubble(pose: .stand, text: "한국에서는 해외주식 한 해 이익과 손실을 합쳐 250만 원을 넘는 부분에만 22%가 붙어요. 올해 판 게 없으면 0을 넣으세요.")
            Text("한국 거주자 기준").appFont(13, .semibold).foregroundStyle(Theme.teal)
            VStack(alignment: .leading, spacing: 4) {
                Text("올해 실현 이익 (만원)").appFont(13, .semibold).foregroundStyle(Theme.sub)
                TextField("", text: $gain).keyboardType(.numberPad).appFont(18, .semibold)
                    .padding(.horizontal, 12).frame(minHeight: 48)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border, lineWidth: 2))
                    .accessibilityLabel("올해 실현 이익 (만원)")
            }
            PrimaryButton(title: "세금 보기") {
                m.taxGain = max(0, Double(gain) ?? 0); m.done.insert(4); m.boardPath.append(.m4r)
            }
        }
        .onAppear { gain = String(Int(m.taxGain)) }
    }
}

struct TaxResultView: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        @Bindable var m = m
        let won0 = { (x: Double) in Int(x.rounded()).formatted() + "만" }
        let maxQ = m.focusRow?.h.qty ?? 0
        let wAfter = m.focusRow.map { max(0, $0.value - m.krw($0.sym, m.taxSellQty * $0.sym.last)) / m.total } ?? 0
        let over = m.taxSellQty > 0 && m.taxBefore > 0 && m.taxGain + m.taxLossMan < 249
        MissionPage(kicker: "절세 열림", title: "올해 해외주식 세금") {
            GuideBubble(pose: .point, text: "아래 슬라이더를 움직이면 위 숫자가 바로 바뀌어요. 계산 예시이고, 세무 상담은 아니에요.")
            VStack(alignment: .leading, spacing: 8) {
                Text("내년 5월 세금").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text(won0(m.taxBefore)).appFont(18).strikethrough().foregroundStyle(Color(hex: 0x8A949E))
                    Text(won0(m.taxAfter)).appFont(30, .bold)
                    Text("−\(won0(max(0, m.taxBefore - m.taxAfter)))").appFont(14, .bold).foregroundStyle(Theme.mint)
                }
                Text("1위 종목 \(AppModel.pct(m.focusWeight))→\(AppModel.pct(wAfter)) (계획 \(AppModel.pct(m.planWeight))) · 확정 손익 \(m.taxLossMan < 0 ? "−" + won0(-m.taxLossMan) : won0(m.taxLossMan))")
                    .appFont(13).foregroundStyle(Color(hex: 0xC9D0D6)).fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
            Card {
                Text("1위 종목 줄이면 (예시)").appFont(15, .bold)
                Text("보유의 \(AppModel.pct(m.taxSellQty / max(1, maxQ))) 줄임").appFont(13).foregroundStyle(Theme.sub)
                Slider(value: $m.taxSellQty, in: 0...max(1, maxQ), step: 1).tint(Theme.teal)
                    .accessibilityLabel("줄이는 비율")
                Text("이익 \(won0(m.taxGain)) − 공제 250만 = 과세 \(won0(max(0, m.taxGain - 250)))")
                    .appFont(13).foregroundStyle(Theme.sub).lineSpacing(2)
                if over {
                    Text("이미 세금 0 · 더 줄여도 그대로")
                        .appFont(13, .semibold).foregroundStyle(Color(hex: 0x8A4B00, dark: 0xF0B070))
                        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(hex: 0xFDF0E1, dark: 0x3A2A17), in: RoundedRectangle(cornerRadius: 10))
                }
            }
            Text("계산 예시 · 세무 상담·매매 권유 아님 · 결제일 연도 기준")
                .appFont(12).foregroundStyle(Theme.muted).lineSpacing(2)
            BoardButtons(next: "다음: 앱 시작 3단계", to: .nx)
        }
    }
}

// MARK: 앱 시작 3단계 — 한 단계마다 위젯 하나
struct AppStartView: View {
    @Environment(AppModel.self) private var m
    @State private var denied = false
    private let steps = [
        ("종목 하나 이상 추가", "넣었으면 확인만", "자산 추이 위젯"),
        ("알림 하나 켜기", "본전 도달·비중 이탈 중 하나", "본전 진행 위젯"),
        ("오픈 API 연결", "안 하면 가상 주식 샘플만", "블록·오늘의 움직임 위젯"),
    ]
    /// 내 종목 전체 평가액 3개월 흐름 (원화)
    private var totalSeries: [Double] {
        let s = m.rows.map { r in m.prices.series(r.sym, period: .m3).map { m.krw(r.sym, $0 * r.h.qty) } }
        let n = s.map(\.count).min() ?? 0
        return (0..<n).map { i in s.reduce(0) { $0 + $1[i] } }
    }
    var body: some View {
        let n = max(1, m.nxStep)   // 미션 4에 왔으면 1단계(종목 추가)는 이미 한 것
        MissionPage(kicker: "미션 4 / 4", title: "세 가지 하면 위젯 하나씩") {
            GuideBubble(pose: .wave, text: "마지막이에요. 위젯은 미리 드리지 않아요. 한 단계마다 하나씩, 세 단계를 다 하면 나머지 위젯도 모두 열려요.")
            HStack(spacing: 8) {
                tile(1, n) {
                    Text("자산 추이").appFont(11, .bold)
                    Sparkline(points: totalSeries).frame(height: 34)
                    Text(AppModel.man(m.total)).appFont(12, .bold)
                }
                tile(2, n) {
                    Text("본전까지").appFont(11, .bold)
                    Text(AppModel.pct(min(1, m.total / max(1, m.cost)))).appFont(22, .bold)
                    ProgressBar(value: m.total / max(1, m.cost), height: 8, fill: Theme.teal, track: Theme.track)
                }
                tile(3, n) {
                    Text("블록").appFont(11, .bold)
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 3), count: 3), spacing: 3) {
                        ForEach(m.blocks.filter { !$0.inter }) { b in
                            RoundedRectangle(cornerRadius: 4).fill(m.done.contains(b.id) ? Theme.teal : Theme.track).frame(height: 14)
                        }
                    }
                    Text("미션 진행").appFont(11).foregroundStyle(Theme.sub)
                }
                tile(3, n) {
                    Text("오늘의 움직임").appFont(11, .bold)
                    Text(AppModel.sgn(m.todayMove)).appFont(18, .bold).foregroundStyle(Theme.change(m.todayMove))
                }
            }
            ForEach(steps.indices, id: \.self) { i in
                let done = i < n, cur = i == n
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 8) {
                        Text("\(i + 1)").appFont(13, .bold).frame(width: 24, height: 24)
                            .foregroundStyle(.white).background(done || cur ? Theme.teal : Theme.muted, in: Circle())
                        Text(steps[i].0).appFont(15, .bold)
                    }
                    (Text("\(steps[i].1) · 받는 것: ") + Text(steps[i].2).bold()).appFont(13).foregroundStyle(Theme.sub)
                    if cur && i == 1 {
                        // 2단계는 실제로: 아이폰 알림 허용을 받고 본전 도달 알림을 켠다
                        Button(denied ? "설정에서 알림 허용" : "알림 켜기") {
                            Task {
                                if denied, let u = URL(string: UIApplication.openNotificationSettingsURLString) { await UIApplication.shared.open(u); return }
                                if await Notifier.request() {
                                    m.alerts[m.isGoal ? "morn" : "be"] = true
                                    withAnimation { m.nxStep += 1 }
                                } else { denied = true }
                            }
                        }
                            .appFont(14, .bold).foregroundStyle(.white).padding(.horizontal, 14).frame(minHeight: 44)
                            .background(Theme.teal, in: RoundedRectangle(cornerRadius: 10))
                        if denied { Text("알림 꺼짐 · 설정 > 알림 > naeilo에서 허용 후 다시").appFont(12).foregroundStyle(Theme.sub) }
                    } else if cur && i == 2 {
                        // 3단계: 키를 넣었으면 확인만, 아니면 설정의 오픈 API 화면으로. 키 없이도 샘플로 계속할 수 있다
                        Button(TossKeys.on ? "연결했어요" : "오픈 API 연결") {
                            if TossKeys.on { withAnimation { m.nxStep += 1 } } else { m.tab = .settings; m.settingsPath = [.toss] }
                        }
                            .appFont(14, .bold).foregroundStyle(.white).padding(.horizontal, 14).frame(minHeight: 44)
                            .background(Theme.teal, in: RoundedRectangle(cornerRadius: 10))
                        if !TossKeys.on {
                            Button("샘플로 계속") { withAnimation { m.nxStep += 1 } }
                                .appFont(13, .semibold).foregroundStyle(Theme.teal).frame(minHeight: 44)
                        }
                    } else if cur {
                        Button("했어요") { withAnimation { m.nxStep += 1 } }
                            .appFont(14, .bold).foregroundStyle(.white).padding(.horizontal, 14).frame(minHeight: 44)
                            .background(Theme.teal, in: RoundedRectangle(cornerRadius: 10))
                    }
                    if done { Text("완료 · 위젯 열림").appFont(13, .bold).foregroundStyle(Theme.teal) }
                }
                .padding(.horizontal, 14).padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .foregroundStyle(done || cur ? Theme.ink : Theme.muted)
                .background(done ? Theme.tealBg : cur ? Theme.card : Color(hex: 0xF4F6F8, dark: 0x1D252E), in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(done ? Color(hex: 0xB9DCD7, dark: 0x2E5A55) : cur ? Theme.teal : Theme.track, lineWidth: cur ? 2 : 1))
                // 끝낸 단계엔 만세, 지금 단계엔 옆으로 걸어오는 인물 (카드 오른쪽 위에 올라선다)
                .overlay(alignment: .topTrailing) {
                    if done || cur { MotionSprite(friend: m.guideFriend, pose: done ? .cheer : .side, height: 44).offset(x: -14, y: -38) }
                }
                .padding(.top, done || cur ? 24 : 0)
            }
            // 내 캐릭터는 여기서부터 만들어 보고 저장할 수 있다 (홈·위젯에 나온다)
            NavigationLink { MeEditView() } label: {
                HStack(spacing: 12) {
                    CharPixel(id: "me", width: 30, height: 42)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(m.me == nil ? "내 캐릭터 만들어 보기" : "내 캐릭터 고치기").appFont(15, .bold)
                        Text("쉼터 '본인' 칸 · 홈·위젯에 나옴").appFont(12).foregroundStyle(Theme.sub)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                    Text("›").appFont(17, .bold).foregroundStyle(Theme.muted)
                }
                .foregroundStyle(Theme.ink)
                .padding(.horizontal, 14).padding(.vertical, 12)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
            }
            .buttonStyle(.plain)
            if n >= 3 {
                VStack(alignment: .leading, spacing: 8) {
                    Text("특별 선물").appFont(13, .bold).foregroundStyle(Theme.yellow)
                    Text("나머지 위젯 모두 열림").appFont(17, .bold)
                    FlowRow(spacing: 6) {
                        ForEach(["비중", "배당 달력", "환율", "본전 진행 (큰)"], id: \.self) {
                            Text($0).appFont(12, .semibold).padding(.horizontal, 10).padding(.vertical, 4)
                                .background(Theme.slate, in: Capsule())
                        }
                    }
                }
                .foregroundStyle(.white).padding(16).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.night, in: RoundedRectangle(cornerRadius: 18))
                PrimaryButton(title: m.playOn ? "미션 판으로" : "인터미션 시작") { m.finishAppStart() }
            } else {
                BoardButtons(next: nil, to: nil)
            }
        }
    }

    private func tile<C: View>(_ i: Int, _ n: Int, @ViewBuilder _ c: () -> C) -> some View {
        Group {
            if n >= i {
                VStack(alignment: .leading, spacing: 4) { c() }
                    .padding(10).frame(maxWidth: .infinity, minHeight: 108, alignment: .topLeading)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
            } else {
                Text("\(i)단계 하면 열림").appFont(12).multilineTextAlignment(.center).foregroundStyle(Theme.sub2)
                    .frame(maxWidth: .infinity, minHeight: 108)
                    .background(Theme.track, in: RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.dash, style: StrokeStyle(lineWidth: 2, dash: [6, 4])))
            }
        }
    }
}

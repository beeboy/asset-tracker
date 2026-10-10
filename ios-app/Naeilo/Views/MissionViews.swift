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
    .background(.white, in: RoundedRectangle(cornerRadius: 16))
    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border))
}

// MARK: 미션 1 — 종목·단가 넣기
struct Mission1View: View {
    @Environment(AppModel.self) private var m
    @State private var tq = ""
    @State private var tp = ""
    @State private var qq = ""
    @State private var qp = ""

    var body: some View {
        let ok = [tq, tp].allSatisfy { (Double($0) ?? 0) > 0 } || [qq, qp].allSatisfy { (Double($0) ?? 0) > 0 }
        MissionPage(kicker: "미션 1 / 4", title: "가진 종목과 산 가격을 알려주세요") {
            Text("매수 단가가 있어야 \"본전\"을 계산할 수 있어요. 기기 밖으로 나가지 않아요.").appFont(15).foregroundStyle(Theme.sub)
            entry("DRNK", "현재 $250 (예시)", $tq, $tp)
            entry("QQQ", "현재 $480 (예시)", $qq, $qp)
            Text("+ 종목 추가 (종목 탭에서 더 넣을 수 있어요)").appFont(14).foregroundStyle(Theme.muted)
            PrimaryButton(title: ok ? "회복 확률 보기" : "수량과 단가를 넣어 주세요", color: ok ? Theme.teal : Theme.muted) {
                guard ok else { return }
                set("DRNK", tq, tp); set("QQQ", qq, qp)
                if m.route == .recover { m.done.insert(1); m.boardPath.append(.m1r) }
                else { m.gDone.insert("hold"); m.boardPath.append(.gp1r) }
            }
        }
        .onAppear {
            let d = m.holdings.first { $0.symbol == "DRNK" }, q = m.holdings.first { $0.symbol == "QQQ" }
            tq = d.map { $0.qty.formatted() } ?? ""; tp = d.map { $0.avg.formatted() } ?? ""
            qq = q.map { $0.qty.formatted() } ?? ""; qp = q.map { $0.avg.formatted() } ?? ""
        }
    }

    private func set(_ k: String, _ q: String, _ p: String) {
        m.holdings.removeAll { $0.symbol == k }
        if let qn = Double(q), qn > 0, let pn = Double(p), pn > 0 {
            m.holdings.insert(Holding(symbol: k, qty: qn, avg: pn), at: k == "DRNK" ? 0 : min(1, m.holdings.count))
        }
    }

    private func entry(_ k: String, _ now: String, _ q: Binding<String>, _ p: Binding<String>) -> some View {
        Card {
            HStack { LogoTile(symbol: k, size: 28); Text(k).appFont(17, .bold); Spacer(); Text(now).appFont(13).foregroundStyle(Theme.sub) }
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 10) { field("수량(주)", q); field("평균 단가($)", p) }
                VStack(spacing: 10) { field("수량(주)", q); field("평균 단가($)", p) }
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
        MissionPage(kicker: "미션 1 완료 · 새로 열림: 회복 확률", title: "지금 내 평가액과 본전까지") {
            if m.ret >= 0 {
                Card {
                    Text("이미 본전 위에 있어요 (\(AppModel.sgn(m.ret)))").appFont(17, .bold)
                    Text("회복보다 목표 금액을 정하는 쪽이 맞아요. 목표 루트는 다음 빌드에서 옮겨요.").appFont(14).foregroundStyle(Theme.sub)
                }
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    HStack { Text("지금 평가액"); Spacer(); Text("들어간 돈 \(AppModel.man(m.cost))") }
                        .appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Text(AppModel.man(m.total)).appFont(30, .bold)
                        Text(AppModel.sgn(m.ret)).appFont(16, .bold).foregroundStyle(Color(hex: 0x8CC4F2))
                    }
                    ProgressBar(value: m.total / m.cost)
                    Text("본전의 \(AppModel.pct(min(1, m.total / m.cost))) 지점. 손실 절반을 되찾으려면 \(AppModel.sgn(m.halfway / m.total - 1)), 본전까지는 \(AppModel.sgn(m.cost / m.total - 1)) 올라야 해요.")
                        .appFont(14).foregroundStyle(Color(hex: 0xC9D0D6)).fixedSize(horizontal: false, vertical: true)
                }
                .foregroundStyle(.white).padding(18)
                .background(Theme.ink, in: RoundedRectangle(cornerRadius: 20))
                horizonChips($m.horizon)
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 8) { probBoxes(keep, T) }
                    VStack(spacing: 8) { probBoxes(keep, T) }
                }
                Card {
                    Text("\(m.horizonLabel) 뒤 내 손익은 대략 이 사이").appFont(14, .bold)
                    outcomeRow("나쁜 경우", keep.outcome(m.cost, T, -1))
                    outcomeRow("보통", keep.outcome(m.cost, T, 0))
                    outcomeRow("좋은 경우", keep.outcome(m.cost, T, 1))
                    Text("100번 중 90번이 나쁜 경우와 좋은 경우 사이에 들어와요. 지금은 \(AppModel.sgn(m.ret)).").appFont(12).foregroundStyle(Theme.muted)
                }
            }
            BoardButtons(next: "다음 미션: 원인 진단", to: .m2)
        }
    }
    @ViewBuilder private func probBoxes(_ keep: Plan, _ T: Double) -> some View {
        statBox("\(m.horizonLabel) 뒤 손실 절반 이상 회복", AppModel.pct(keep.prob(m.halfway, T)))
        statBox("\(m.horizonLabel) 뒤 본전 이상", AppModel.pct(keep.prob(m.cost, T)))
    }
    private func outcomeRow(_ k: String, _ v: Double) -> some View {
        HStack { Text(k).foregroundStyle(Theme.sub); Spacer(); Text(AppModel.sgn0(v)).fontWeight(.bold).foregroundStyle(Theme.change(v)) }
            .appFont(15)
    }
}

// MARK: 미션 2 — 원인 진단 퀴즈
struct Mission2View: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        let opts = [("conc", "한 종목에 너무 많이 몰려 있어서"), ("mkt", "시장 전체가 떨어져서"), ("fx", "환율 때문에")]
        let right = m.quizRight, ans = m.quizAnswer
        MissionPage(kicker: "미션 2 / 4", title: "내 손실, 가장 큰 원인은 뭘까요?") {
            Text("감으로 하나 골라 보세요. 정답은 내 숫자로 확인해요.").appFont(15).foregroundStyle(Theme.sub)
            ForEach(opts, id: \.0) { k, label in
                let isRight = k == right, picked = ans == k
                Button { withAnimation { m.quizAnswer = k } } label: {
                    Text(label).appFont(16, .semibold).multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading).padding(.horizontal, 16)
                        .foregroundStyle(ans == nil ? Theme.ink : isRight ? Theme.tealDark : picked ? Theme.sub : Theme.sub2)
                        .background(ans != nil && isRight ? Theme.tealBg : .white, in: RoundedRectangle(cornerRadius: 14))
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(ans == nil ? Theme.dash : isRight ? Theme.teal : picked ? Theme.muted : Theme.track, lineWidth: 2))
                }.buttonStyle(.plain)
            }
            if ans != nil {
                let qRet = m.qqqRow.map { $0.value / $0.cost - 1 } ?? 0
                Card {
                    Text(ans == right ? "맞아요." : "내 숫자로 보면 조금 달라요.").appFont(17, .bold)
                    Text("손실 \(AppModel.man(m.cost - m.total)) 중 \(AppModel.pct(m.lossShareDRNK))가 DRNK 한 종목에서 나왔어요. 같은 기간 QQQ는 \(AppModel.sgn(qRet))였어요.")
                        .appFont(15).lineSpacing(3)
                    HStack { Text("DRNK 비중"); Spacer(); Text(AppModel.pct(m.drnkWeight)).fontWeight(.bold) }.appFont(14)
                    ProgressBar(value: m.drnkWeight, height: 10, fill: Theme.purple, track: Theme.track)
                    PrimaryButton(title: "한 가지 더 보기") { m.done.insert(2); m.boardPath.append(.m2r) }
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
        MissionPage(kicker: "미션 2 완료 · 새로 열림: 회복 경로", title: "많이 흔들리면, 보통의 결과가 깎여요") {
            Card {
                HStack { Text("한 해 흔들림 폭"); Spacer(); Text("±\(AppModel.pct(keep.sigma))").fontWeight(.bold) }.appFont(15)
                HStack { Text("보통의 연 성장"); Spacer(); Text(AppModel.sgn(keep.g)).fontWeight(.bold) }.appFont(15)
                Text("평균은 연 9% 오른다고 봐도, 한 종목에 몰려 흔들림이 크면 \"보통의 경우\"는 \(AppModel.sgn(keep.g))에 그쳐요. -50% 뒤에는 +100%가 있어야 본전이기 때문이에요.")
                    .appFont(14).foregroundStyle(Theme.sub).lineSpacing(3)
            }
            Card {
                Text("앞으로 1년, 내 평가액이 지날 길").appFont(15, .bold)
                PathChart(plans: [(keep, Color(hex: 0x2450C8), false)], V: m.total, C: m.cost, half: m.halfway).frame(height: 150)
                HStack { Text("지금"); Spacer(); Text("3개월"); Spacer(); Text("6개월"); Spacer(); Spacer(); Text("1년") }.appFont(11).foregroundStyle(Theme.muted)
                FlowRow(spacing: 10) {
                    legend(Color(hex: 0x2450C8), "보통의 경우"); legend(Color(hex: 0x2450C8).opacity(0.2), "100번 중 90번이 이 안")
                    legend(Color(hex: 0xC8352E), "본전"); legend(Theme.muted, "손실 절반")
                }
            }
            BoardButtons(next: "다음 미션: 내 계획 정하기", to: .m3)
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
                    Text("\(m.horizonLabel) 뒤").appFont(13).foregroundStyle(Theme.sub)
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
                horizonChips($m.horizon)
            }
            .padding(16).background(.white).overlay(alignment: .bottom) { Divider() }
        } content: {
                VStack(alignment: .leading, spacing: 14) {
                    Text(sel.desc).appFont(15).lineSpacing(3)
                    holdingsCard
                    basketsCard(Basket.allCases.filter { $0 != .C })
                    Text("흔들림을 줄이면 빠른 본전 확률도, 더 잃을 확률도 함께 줄어요.").appFont(14).foregroundStyle(Theme.sub)
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
        let d = Industries.all.first { $0.key == "AutoAero" }!
        let b = Basket.of(pe: d.pe, pe10: d.pe10, growth: d.growth)
        let ratio = String(format: "%.1f", d.pe / d.pe10)
        return Card {
            Text("내 종목 업종").appFont(15, .bold)
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("DRNK \(AppModel.pct(m.drnkWeight))").appFont(15, .bold)
                    Text("우주항공·궤도 통신 · PER 평균의 \(ratio)배 · 성장 \(Int(d.growth.rounded()))%").appFont(13).foregroundStyle(Theme.sub)
                }
                Spacer()
                tag(b == "H" ? "과열" : Basket(rawValue: b)?.name ?? "세 묶음 밖", hot: b == "H")
            }
            if b == "H" {
                Text("업종 PER이 10년 평균의 \(ratio)배인데 이익 성장은 \(Int(d.growth.rounded()))%예요. 가격이 이익보다 훨씬 빨리 올라서 세 묶음 어디에도 넣지 않아요.")
                    .appFont(13).foregroundStyle(Theme.sub).lineSpacing(2)
            }
            Divider().overlay(Theme.line)
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("QQQ \(AppModel.pct(1 - m.drnkWeight))").appFont(15, .bold)
                    Text("나스닥100 · 여러 업종에 분산").appFont(13).foregroundStyle(Theme.sub)
                }
                Spacer()
                tag("지수", hot: false, neutral: true)
            }
        }
    }

    private func tag(_ t: String, hot: Bool, neutral: Bool = false) -> some View {
        Text(t).appFont(12, .bold).padding(.horizontal, 8).padding(.vertical, 3)
            .foregroundStyle(neutral ? Theme.slate : hot ? Color(hex: 0xA3291F) : Theme.tealDark)
            .background(neutral ? Theme.track : hot ? Color(hex: 0xFBE9E7) : Theme.tealBg, in: Capsule())
    }

    func basketsCard(_ list: [Basket]) -> some View {
        BasketList(baskets: list, open: $open)
    }
}

struct BasketList: View {
    let baskets: [Basket]
    @Binding var open: Set<Basket>
    var title = "업종 묶음 · 눌러서 펼치기"
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
                        Text(isOpen ? "접기" : "펼치기").appFont(12).foregroundStyle(Theme.teal)
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
            Text("NYU 다모다란 업종 자료 2026년 1월 · 기업 10곳 미만 제외 · 상품 추천 아님").appFont(11).foregroundStyle(Theme.muted)
        }
    }
}

// 계획 비중 막대: DRNK + 묶음
private func mixBar(_ p: Plan) -> some View {
    let rest = 1 - p.wt
    let segs: [(Color, Double)] = [(Theme.purple, p.wt)] + Basket.allCases.map { ($0.color, rest * (p.mix[$0] ?? 0)) }
    return VStack(alignment: .leading, spacing: 6) {
        GeometryReader { g in
            HStack(spacing: 0) {
                ForEach(segs.indices, id: \.self) { i in
                    Rectangle().fill(segs[i].0).frame(width: g.size.width * segs[i].1)
                        .overlay(alignment: .trailing) { Rectangle().fill(.white).frame(width: segs[i].1 > 0 ? 2 : 0) }
                }
            }
        }
        .frame(height: 10).clipShape(Capsule()).background(Theme.track, in: Capsule())
        FlowRow(spacing: 8) {
            legend(Theme.purple, "DRNK")
            ForEach(Basket.allCases, id: \.self) { legend($0.color, $0.name) }
        }
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel("DRNK \(AppModel.pct(p.wt))" + Basket.allCases.compactMap { b in p.mix[b].map { ", \(b.name) \(AppModel.pct(rest * $0))" } }.joined())
}

// MARK: 미션 3 결과 — 옮길 금액
struct Mission3ResultView: View {
    @Environment(AppModel.self) private var m
    @State private var open: Set<Basket> = []
    var body: some View {
        let sel = m.selectedPlan, T = Double(m.horizon) / 12, freed = m.freedAmount
        let baskets = Basket.allCases.filter { $0 != .C && (sel.mix[$0] ?? 0) > 0 }
        MissionPage(kicker: "미션 3 완료 · 새로 열림: 비중 비교", title: "내 계획: \(sel.name)") {
            Text(sel.tag).appFont(15).foregroundStyle(Theme.sub)
            Card {
                Text("\(m.horizonLabel) 뒤 절반 회복 \(AppModel.pct(sel.prob(m.halfway, T))) · 본전 \(AppModel.pct(sel.prob(m.cost, T))) · 나쁜 경우 \(AppModel.sgn0(sel.outcome(m.cost, T, -1)))")
                    .appFont(15, .semibold).lineSpacing(3)
            }
            Card {
                Text("옮길 금액").appFont(15, .bold)
                moveRow("DRNK", freed > 0 ? "\(Int((m.drnkWeight * 100).rounded()))→\(AppModel.pct(sel.wt))" : AppModel.pct(m.drnkWeight),
                        freed > 0 ? "-" + AppModel.man(freed) : "그대로", up: false)
                moveRow("QQQ", AppModel.pct(1 - m.drnkWeight), "그대로", up: true)
                ForEach(Basket.allCases.filter { (sel.mix[$0] ?? 0) > 0 && sel.id != "keep" }, id: \.self) { b in
                    let a = freed * (sel.mix[b] ?? 0)
                    moveRow(b.name, "0→\(AppModel.pct(a / max(1, m.total)))", "+" + AppModel.man(a), up: true)
                }
                Text(sel.id == "keep" ? "지금 구성 그대로 두는 계획이에요." : "DRNK 매도 손실은 같은 해 해외주식 이익과 상계돼요 (절세 화면).")
                    .appFont(12).foregroundStyle(Theme.muted)
            }
            if !baskets.isEmpty && sel.id != "keep" { BasketList(baskets: baskets, open: $open, title: "담을 업종 · 눌러서 펼치기") }
            Text("계획만 기록해요. 매매는 증권사 앱에서 직접 하세요.").appFont(14).foregroundStyle(Theme.sub)
            PrimaryButton(title: "다음 미션: 앱 시작 3단계") { m.boardPath.append(.nx) }
            Button { m.boardPath.append(.m4) } label: {
                Text("올해 다른 해외주식 이익이 있다면: 손실로 세금 줄이기").appFont(15, .semibold).multilineTextAlignment(.center)
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
            Text(amt).appFont(15, .bold).foregroundStyle(up ? Theme.teal : Color(hex: 0x2450C8)).monospacedDigit()
        }
        .frame(minHeight: 36)
    }
}

// MARK: 절세 (대한민국 거주자)
struct TaxInputView: View {
    @Environment(AppModel.self) private var m
    @State private var gain = ""
    var body: some View {
        MissionPage(kicker: "자세히 · 절세 (대한민국 거주자)", title: "올해 이미 판 해외주식, 이익이 얼마였나요?") {
            Text("세금 규칙: 대한민국 거주자 (나라별로 바뀜)").appFont(13, .semibold).foregroundStyle(Theme.teal)
            Text("한국에서는 해외주식 한 해 이익과 손실을 합쳐 250만 원을 넘는 부분에만 22% 세금이 붙어요. 올해 판 것이 없으면 0을 넣으세요.")
                .appFont(15).foregroundStyle(Theme.sub).lineSpacing(3)
            VStack(alignment: .leading, spacing: 4) {
                Text("올해 실현 이익 (만원)").appFont(13, .semibold).foregroundStyle(Theme.sub)
                TextField("", text: $gain).keyboardType(.numberPad).appFont(18, .semibold)
                    .padding(.horizontal, 12).frame(minHeight: 48)
                    .background(.white, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border, lineWidth: 2))
                    .accessibilityLabel("올해 실현 이익 (만원)")
            }
            PrimaryButton(title: "내 세금 계산 보기") {
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
        let won0 = { (x: Double) in Int(x.rounded()).formatted() + "만원" }
        let maxQ = m.drnkRow?.h.qty ?? 0
        let wAfter = m.drnkRow.map { max(0, $0.value - m.taxSellQty * $0.sym.close * Sample.fx) / m.total } ?? 0
        let over = m.taxSellQty > 0 && m.taxBefore > 0 && m.taxGain + m.taxLossMan < 249
        MissionPage(kicker: "새로 열림: 절세 화면", title: "올해 해외주식 세금") {
            VStack(alignment: .leading, spacing: 8) {
                Text("내년 5월에 낼 세금").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text(won0(m.taxBefore)).appFont(18).strikethrough().foregroundStyle(Color(hex: 0x8A949E))
                    Text(won0(m.taxAfter)).appFont(30, .bold)
                    Text("\(won0(max(0, m.taxBefore - m.taxAfter))) 줄어요").appFont(14, .bold).foregroundStyle(Theme.mint)
                }
                Text("\(Int(m.taxSellQty))주 팔면 확정 손실 \(m.taxLossMan < 0 ? "-" + won0(-m.taxLossMan) : won0(m.taxLossMan)) · DRNK 비중 \(AppModel.pct(wAfter)) (계획 \(AppModel.pct(m.planWeight)))")
                    .appFont(13).foregroundStyle(Color(hex: 0xC9D0D6)).fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.ink, in: RoundedRectangle(cornerRadius: 20))
            Card {
                Text("손실 난 DRNK 일부를 올해 안에 판다면?").appFont(15, .bold)
                Text("팔 수량 (보유 \(Int(maxQ))주) · \(Int(m.taxSellQty))주").appFont(13).foregroundStyle(Theme.sub)
                Slider(value: $m.taxSellQty, in: 0...max(1, maxQ), step: 1).tint(Theme.teal)
                    .accessibilityLabel("팔 수량")
                Text("올해 이익 \(won0(m.taxGain)) − 기본공제 250만원 = 과세 \(won0(max(0, m.taxGain - 250))). 슬라이더를 움직이면 위 숫자가 바로 바뀌어요.")
                    .appFont(13).foregroundStyle(Theme.sub).lineSpacing(2)
                if over {
                    Text("이미 세금이 0원이 되는 수량이에요. 이보다 더 팔아도 올해 세금은 더 줄지 않아요.")
                        .appFont(13, .semibold).foregroundStyle(Color(hex: 0x8A4B00))
                        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(hex: 0xFDF0E1), in: RoundedRectangle(cornerRadius: 10))
                }
            }
            Text("계산 예시이며 세무 상담이 아닙니다. 매도 결제일이 올해 안이어야 올해 손익에 들어가요. 사고파는 판단은 직접 하세요.")
                .appFont(12).foregroundStyle(Theme.muted).lineSpacing(2)
            BoardButtons(next: "다음 미션: 앱 시작 3단계", to: .nx)
        }
    }
}

// MARK: 앱 시작 3단계 — 한 단계마다 위젯 하나
struct AppStartView: View {
    @Environment(AppModel.self) private var m
    private let steps = [
        ("앱에서 종목 하나 이상 추가", "이미 넣은 종목이 있으면 확인만", "자산 추이 위젯"),
        ("앱 알림 하나 켜기", "본전 도달, 비중 이탈 중 하나", "본전 진행 위젯"),
        ("기기 자동 동기화 켜기", "폰에서 넣어도 PC naeilo.com에 같은 숫자", "블록 위젯"),
    ]
    var body: some View {
        let n = m.nxStep
        MissionPage(kicker: "미션 4 / 4", title: "앱에서 세 가지만 하면 위젯을 하나씩 드려요") {
            Text("위젯은 미리 주지 않아요. 한 단계 끝낼 때마다 하나씩, 세 단계를 다 하면 특별 선물로 나머지 위젯도 모두 열려요.")
                .appFont(15).foregroundStyle(Theme.sub).lineSpacing(3)
            HStack(spacing: 8) {
                tile(1, n) {
                    Text("자산 추이").appFont(11, .bold)
                    Sparkline(points: m.prices.series(Sample.symbol("DRNK")!, period: .m3)).frame(height: 34)
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
                    if cur {
                        Button("앱에서 했어요 (시안)") { withAnimation { m.nxStep += 1 } }
                            .appFont(14, .bold).foregroundStyle(.white).padding(.horizontal, 14).frame(minHeight: 44)
                            .background(Theme.teal, in: RoundedRectangle(cornerRadius: 10))
                    }
                    if done { Text("완료 · 위젯 열림").appFont(13, .bold).foregroundStyle(Theme.teal) }
                }
                .padding(.horizontal, 14).padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .foregroundStyle(done || cur ? Theme.ink : Theme.muted)
                .background(done ? Theme.tealBg : cur ? .white : Color(hex: 0xF4F6F8), in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(done ? Color(hex: 0xB9DCD7) : cur ? Theme.teal : Theme.track, lineWidth: cur ? 2 : 1))
            }
            if n >= 3 {
                VStack(alignment: .leading, spacing: 8) {
                    Text("특별 선물").appFont(13, .bold).foregroundStyle(Theme.yellow)
                    Text("나머지 위젯도 모두 열렸어요").appFont(17, .bold)
                    FlowRow(spacing: 6) {
                        ForEach(["어제의 움직임 (중간)", "비중", "배당 달력", "환율", "본전 진행 (큰)"], id: \.self) {
                            Text($0).appFont(12, .semibold).padding(.horizontal, 10).padding(.vertical, 4)
                                .background(Theme.slate, in: Capsule())
                        }
                    }
                }
                .foregroundStyle(.white).padding(16).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.ink, in: RoundedRectangle(cornerRadius: 18))
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
                    .background(.white, in: RoundedRectangle(cornerRadius: 18))
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

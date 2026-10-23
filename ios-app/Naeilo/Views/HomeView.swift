import SwiftUI

struct HomeView: View {
    @Environment(AppModel.self) private var m
    @State private var inbox = false
    @AppStorage("hideAmt") private var hideAmt = false
    @AppStorage(BoxLook.key) private var lookRaw = BoxLook.jump.rawValue

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                AppHeader(tab: String(localized: "홈")).padding(.horizontal, -16)
                SampleNote()
                summary
                // 회복 루트: 매달 더 넣으면 본전이 언제인지 (원인 진단·내 계획을 마친 뒤)
                if !m.isGoal, m.done.contains(3), m.cost > m.total, !m.rows.isEmpty { DepositLeverCard(plan: m.keepPlan) }
                shelterBox
                // 말풍선은 한 줄 해석, 바로 아래 '오늘 많이 움직인 종목'이 그 근거 (2026-10-23)
                HoldingsNews(moversOnly: true)
                ParallelHomeCard()
                reminder
                EarnHomeCard()
                TownButton { TownView(scroll: $0) }
                HStack(spacing: 8) {
                    coverButton(String(localized: "위젯"), "rectangle.3.group") { m.widgetsOpen = true }
                    coverButton(String(localized: "설정"), "gearshape") { m.openSettings() }
                }
                .frame(maxWidth: .infinity)
            }
            .screen()
        }
        .background(Theme.bg)
        .refreshable { await Market.shared.pullRefresh(m) }
        // 기록·소식 계산은 앱을 열 때와 1분 시세 갱신 때 한다 (App.swift). 탭 이동 땐 말풍선만
        .onAppear { Remind.shared.nextBubble() }
        .sheet(isPresented: $inbox, onDismiss: { Remind.shared.markAllSeen() }) { RemindSheet().environment(m) }
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: String.self) { route in
            if route == "shelter" { ShelterView() }
            else if route == "library" { ShelterView(onlyLibrary: true) }
            else if route == "parallel" { ParallelView() }
            else if route == "parallel:basket" { ParallelBasketView() }
            else if route == "goal" { GoalSetView(fromSettings: true) }
            else if route.hasPrefix("read:"), let i = Int(route.dropFirst(5)) { ReaderView(index: i) }
            else if route.hasPrefix("vol1:"), let i = Int(route.dropFirst(5)) { ReaderView(index: i, book: .vol1) }
            else if route == "me" { MeEditView() }
            else if route.hasPrefix("char:"), let f = Shelter.friends.first(where: { $0.id == route.dropFirst(5).split(separator: ":").first.map(String.init) }) {
                // char:<인물> = 소설 속 갈래로, char:<인물>:invest = 투자하는 갈래로, …:invest:h<번호> = 그 버릇을 펴서
                let parts = route.split(separator: ":")
                CharacterDetailView(friend: f, invest: parts.contains("invest"),
                                    focus: parts.last.flatMap { $0.hasPrefix("h") ? Int($0.dropFirst()) : nil })
            }
        }
    }

    // 평가액 박스: 고른 모양 (기본 지평선). 길게 누르면 모양 고르기 (설정 > 홈 화면에도 있다)
    private var summary: some View {
        let look = BoxLook(rawValue: lookRaw) ?? .jump
        return Group {
            if look == .bar {
                if m.isGoal { goalSummary } else { recoverSummary }
            } else {
                sceneSummary(look)
            }
        }
        .contextMenu {
            Picker(String(localized: "평가액 박스 모양"), selection: $lookRaw) {
                ForEach(BoxLook.allCases) { Text($0.name).tag($0.rawValue) }
            }
        }
    }

    /// 장면 박스 값: 가로 자리 = 지금 값 ÷ 목표(본전), 계획 자리 = 내 길 계획선(적립 포함)의 오늘 값, 눈금 = 계획선의 1년·2년 값
    private var scene: BoxScene {
        let p = m.myPath, K = max(1, p.key), me = m.trackValue, n = p.need(p.start)
        let onPlan = n <= 0 || me >= n
        if m.isGoal {
            let value = m.route == .plus || m.noviceOwn
            let years = Int(p.end.rounded(.up))
            let ticks: [(String, Double)] = years > 1 ? (1..<years).map { y in (String(localized: "\(String(describing: y))년"), p.need(Double(y)) / K) } : []
            return BoxScene(label: value ? String(localized: "평가액") : String(localized: "모은 돈"),
                            amount: m.route == .novice ? m.goalSaved * 1e4 : m.total,
                            f: me / K, plan: n > 0 ? n / K : nil, ticks: ticks,
                            target: AppModel.wonK(m.gK), half: AppModel.wonK(m.gK / 2),
                            today: value && m.route == .plus && m.total > 0 ? m.todayMove : nil,
                            ret: value && m.cost > 0 ? m.ret : nil,
                            status: m.goalReached ? String(localized: "목표 도달!") : onPlan ? String(localized: "계획대로") : String(localized: "조금 늦어요"))
        }
        let need = m.cost / max(1, m.total) - 1
        return BoxScene(label: String(localized: "평가액"), amount: m.total, f: me / K, plan: n > 0 ? n / K : nil,
                        target: String(localized: "본전"), half: String(localized: "절반"),
                        today: m.total > 0 ? m.todayMove : nil, ret: m.ret,
                        status: need > 0 ? String(localized: "본전까지 +\(String(describing: String(format: "%.1f", need * 100)))%") : String(localized: "본전 도달"))
    }

    private func sceneSummary(_ look: BoxLook) -> some View {
        let reached = m.isGoal ? m.goalReached : m.cost <= m.total
        let setMonthly = m.isGoal && !m.goalReached && m.route != .novice && m.gM <= 0
        return VStack(spacing: 0) {
            SceneBox(look: look, s: scene, hide: $hideAmt)
                .overlay(alignment: .bottomTrailing) {
                    if setMonthly {
                        NavigationLink(value: "goal") { Text("적립 정하기 ›").appFont(12, .bold).foregroundStyle(Theme.yellow).frame(minHeight: 32) }
                            .buttonStyle(.plain).padding(.trailing, 14).accessibilityLabel("다음달 적립 정하기")
                    }
                }
            if reached && !m.rows.isEmpty || m.goalReached {
                Group {
                    if m.isGoal { darkButton(String(localized: "다음 목표 정하기")) { m.suggestNextGoal(); m.homePath.append("goal") } }
                    else { darkButton(String(localized: "목표 루트로")) { m.graduateToGoal(); m.tab = .board } }
                }
                .padding(12)
            }
        }
        .frame(maxWidth: .infinity)
        .background(Theme.night)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    // 목표 루트: 지금 평가액(플러스) 또는 모은 돈(시작 전)과 목표 진행
    private var goalSummary: some View {
        let now = m.route == .novice ? m.goalSaved : m.total / 1e4, value = m.route == .plus || m.noviceOwn, prog = min(1, now / max(1, m.gK))
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(value ? String(localized: "평가액") : String(localized: "모은 돈")).foregroundStyle(Color(hex: 0xC9D0D6)).appFont(13)
                Spacer()
                if value { FreshLabel() }
            }
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                // 금액은 길어도 꺾지 않고 한 줄로, 자리가 모자라면 글자를 줄인다
                // 누르면 금액 가리기(다시 누르면 보이기)
                Text(hideAmt ? "••••••" : AppModel.man(now * 1e4) + String(localized: "원")).appFont(32, .bold).tracking(-0.5).roll(now)
                    .lineLimit(1).minimumScaleFactor(0.4).layoutPriority(1)
                    .contentShape(Rectangle()).onTapGesture { hideAmt.toggle() }
                if value && m.cost > 0 {
                    Text(AppModel.sgn(m.ret)).appFont(14, .bold).foregroundStyle(m.ret >= 0 ? Color(hex: 0xFF8A80) : Color(hex: 0x8CC4F2))
                        .lineLimit(1).minimumScaleFactor(0.6)
                }
            }
            if value && m.route == .plus && m.total > 0 { todayLine }
            HStack {
                Text("목표 \(AppModel.wonK(m.gK)) · \(m.gY)년"); Spacer()
                Text(AppModel.pct(prog)).fontWeight(.bold).foregroundStyle(Theme.yellow)
            }
            .appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            ProgressBar(value: prog)
            HStack {
                Text(m.goalReached ? String(localized: "목표 \(String(describing: AppModel.wonK(m.gK))) 도달!")
                     : m.route == .novice ? String(localized: "매달 \(String(describing: AppModel.wonK(m.gM))) 적립 · 다음 적립일 +\(String(describing: AppModel.wonK(m.gM)))")
                     : AppModel.paceText(m.goalPace()) + (m.gM > 0 ? String(localized: " · 다음달 \(String(describing: AppModel.wonK(m.gM))) 적립") : String(localized: " · 추가 적립 미정")))
                    .appFont(13).foregroundStyle(Color(hex: 0xC9D0D6)).fixedSize(horizontal: false, vertical: true)
                if !m.goalReached && m.route != .novice && m.gM <= 0 {
                    Spacer(minLength: 8)
                    NavigationLink(value: "goal") { Text("정하기 ›").appFont(13, .bold).foregroundStyle(Theme.yellow).frame(minHeight: 32) }
                        .buttonStyle(.plain).accessibilityLabel("다음달 적립 정하기")
                }
            }
            if m.goalReached { darkButton(String(localized: "다음 목표 정하기")) { m.suggestNextGoal(); m.homePath.append("goal") } }
        }
        .foregroundStyle(.white)
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
    }

    private var recoverSummary: some View {
        let need = m.cost / max(1, m.total) - 1
        let needY = m.cost / (m.total / (1 + m.todayMove)) - 1
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("평가액 · 넣은 돈 \(AppModel.man(m.cost))").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                Spacer()
                FreshLabel()
            }
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text(hideAmt ? "••••••" : AppModel.man(m.total)).appFont(32, .bold).tracking(-0.5).roll(m.total)
                    .lineLimit(1).minimumScaleFactor(0.4).layoutPriority(1)
                    .contentShape(Rectangle()).onTapGesture { hideAmt.toggle() }
                Text(AppModel.sgn(m.ret)).appFont(16, .bold).lineLimit(1).fixedSize()
                    .foregroundStyle(m.ret >= 0 ? Color(hex: 0xFF8A80) : Color(hex: 0x8CC4F2))
            }
            if m.total > 0 { todayLine }
            HStack {
                Text("본전까지")
                Spacer()
                Text(need > 0 ? "+" + String(format: "%.1f", need * 100) + String(localized: "% 남음") : String(localized: "본전 도달"))
                    .fontWeight(.bold).foregroundStyle(Theme.yellow)
            }
            .appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            ProgressBar(value: m.total / max(1, m.cost))
            Text(need > 0
                 ? String(localized: "어제 \(String(describing: String(format: "%.1f", needY * 100)))% → 오늘 \(String(describing: String(format: "%.1f", need * 100)))%")
                 : String(localized: "본전 넘음 · 종목·친구 그대로"))
                .appFont(13).foregroundStyle(Color(hex: 0xC9D0D6)).fixedSize(horizontal: false, vertical: true)
            if need <= 0 { darkButton(String(localized: "목표 루트로")) { m.graduateToGoal(); m.tab = .board } }
        }
        .foregroundStyle(.white)
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
    }

    // 오늘 등락: 부호는 한 번만 (▲/▼ 뒤 퍼센트·금액은 부호 없이). 목표·회복 카드 공통
    private var todayLine: some View {
        let mv = m.todayMove, diff = m.stockTotal - m.stockTotal / (1 + mv)
        return Text(String(localized: "오늘 ") + (mv >= 0 ? "▲ " : "▼ ") + String(format: "%.1f%%", abs(mv) * 100) + " · " + AppModel.won(abs(diff)))
            .appFont(13).foregroundStyle(mv >= 0 ? Color(hex: 0xFF8A80) : Color(hex: 0x8CC4F2))
    }

    // 어두운 요약 카드 안의 노란 버튼 (목표 루트로 · 다음 목표)
    private func darkButton(_ t: String, _ act: @escaping () -> Void) -> some View {
        Button(action: act) {
            Text(t + " ›").appFont(15, .bold).foregroundStyle(Theme.inkFixed)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(Theme.yellow, in: RoundedRectangle(cornerRadius: 12))
        }.buttonStyle(.plain)
    }

    // 쉼터 박스: 홈에 둔 친구 + 말풍선 (리마인드 소식이 있으면 그 소식) + 새 소식 + 다른 친구 칸
    private var shelterBox: some View {
        let on = (0..<5).filter { m.friendOn($0) }.count
        let next = (0..<5).first { !m.friendOn($0) }
        let r = Remind.shared, b = r.bubble, n = r.fresh.count
        // 말풍선은 오늘 브리핑 한 문장. 인물 대사는 빼고, 인물은 오늘 장 흐름을 동작으로 보인다 (2026-10-23)
        // 리마인드 소식은 정보라 브리핑 아래에 둔다. 둘 다 없을 때(종목 없음)만 인물 한마디
        let news = b.map { $0.text + ($0.go != .nowhere ? " ›" : "") }
        let say = [m.todayMoversText, news].compactMap { $0 }.joined(separator: "\n")
        let who = m.homeFriendShown
        let tint: Color = b.map { $0.cat.color.opacity(0.14) } ?? Theme.mintBg
        return VStack(alignment: .leading, spacing: 10) {
            Button {
                if let b, b.go != .nowhere { m.go(b.go) } else { m.homePath.append("shelter") }
            } label: {
                HStack(alignment: .bottom, spacing: 10) {
                    Group {
                        if who == "me" ? m.me != nil : FriendMotion.has(who) {
                            MoodFigure(who: who, me: m.me, mood: DayMood.of(m.todayMove), width: 56, height: 80)
                        } else {
                            CharPixel(id: who, width: 56, height: 80).charIdle()
                        }
                    }
                    .accessibilityLabel(m.friendName(who))
                    Text(say.isEmpty ? m.homeSay : say)
                        .appFont(14).lineSpacing(3)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(tint, in: UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 4, bottomTrailingRadius: 14, topTrailingRadius: 14))
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(b.map { String(localized: "\(String(describing: $0.cat.label)) 소식: \(String(describing: $0.text))") } ?? String(localized: "쉼터 보기"))
            if !r.items.isEmpty {
                HStack {
                    Spacer()
                    Button { inbox = true } label: {
                        Text(n > 0 ? String(localized: "새 소식 \(String(describing: n)) ›") : String(localized: "소식함 ›")).appFont(12, .bold)
                            .foregroundStyle(n > 0 ? Theme.inkFixed : Theme.sub)
                            .padding(.horizontal, 10).padding(.vertical, 4)
                            .background(n > 0 ? Theme.yellow : Theme.line, in: Capsule())
                            .frame(minHeight: 32)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(n > 0 ? String(localized: "새 소식 \(String(describing: n))개 보기") : String(localized: "소식함 보기"))
                }
                .padding(.top, -4)
            }
            if on < 5 {
            Divider().overlay(Theme.line)
            NavigationLink(value: "shelter") {
                HStack(alignment: .bottom, spacing: 8) {
                    ForEach(Array(Shelter.friends.enumerated()).filter { $0.element.id != m.homeFriendShown }, id: \.element.id) { i, f in
                        Pixel(name: (m.friendOn(i) ? "spr_" : "sil_") + f.id, width: 20, height: 28).opacity(m.friendOn(i) ? 1 : 0.5)
                    }
                    Spacer()
                    Text(String(localized: "친구 \(String(describing: on))/5") + (next.map { String(localized: " · 다음 \(String(describing: Shelter.friends[$0].name)) \(String(describing: m.friendWhen($0)))") } ?? String(localized: " · 외전 5장")) + " ›")
                        .appFont(12).foregroundStyle(Theme.sub).lineLimit(2).minimumScaleFactor(0.8).multilineTextAlignment(.trailing)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("쉼터 보기")
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
    }

    /// 노란 띠: 3일 안의 일정 (내 종목 먼저). 없으면 인터미션 이번 할 일. 누르면 소식함
    @ViewBuilder private var reminder: some View {
        let r = Remind.shared
        if let e = r.strip {
            stripRow(tag: String(localized: "일정"), text: "\(MarketEvents.when(e.day ?? "")) \(e.text) · \(e.sub ?? "")")
        } else if r.isOn(.doing), let d = r.doing.first(where: { $0.id == "do.inter" }) {
            stripRow(tag: String(localized: "리마인드"), text: d.text)
        }
    }
    private func stripRow(tag: String, text: String) -> some View {
        Button { inbox = true } label: {
            HStack(spacing: 8) {
                Text(tag).appFont(11, .bold).foregroundStyle(Theme.inkFixed).padding(.horizontal, 8).padding(.vertical, 2)
                    .background(Theme.yellow, in: Capsule())
                Text(text).appFont(13).lineLimit(2).multilineTextAlignment(.leading)
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").appFont(11).foregroundStyle(Theme.muted)
            }
            .padding(.horizontal, 12).frame(minHeight: 44)
            .background(Theme.cream, in: RoundedRectangle(cornerRadius: 12))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(tag): \(text). 소식함 열기")
    }

    /// 작은 알약 두 개를 가운데에 (꽉 찬 버튼은 거슬려 줄였다, 2026-10-23). 누르는 자리는 44 높이 그대로
    private func coverButton(_ t: String, _ icon: String, _ act: @escaping () -> Void) -> some View {
        Button(action: act) {
            Label(t, systemImage: icon)
                .appFont(13, .bold).lineLimit(1).fixedSize()
                .padding(.horizontal, 16).frame(height: 34)
                .foregroundStyle(.white).background(Theme.teal, in: Capsule())
                .frame(minHeight: 44).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// 실적 나온 날 (숫자 기다리는 중이거나 오늘 발표): 보유 종목마다 "{회사} 7~9월 실적" + 작은 막대 둘 + 작년 대비 %.
/// 금액·평가하는 말 없음. 누르면 강 팝업. 나온 종목이 없으면 안 보인다
struct EarnHomeCard: View {
    @Environment(AppModel.self) private var m
    @State private var river: EarnTarget? = nil

    var body: some View {
        // 발표가 먼저인 종목부터 (기다리는 중이면 발표일, 아니면 마지막 분기 발표일)
        let items = m.rows.compactMap { r -> (AppModel.Row, Earnings.Summary)? in
            guard let s = EarningsFeed.shared.summary(r.sym, qty: r.h.qty), s.fresh else { return nil }
            return (r, s)
        }.sorted { ($0.1.pending ?? $0.1.announced) < ($1.1.pending ?? $1.1.announced) }
        if !items.isEmpty {
            VStack(spacing: 0) {
                ForEach(items, id: \.0.id) { r, s in
                    Button { river = EarnTarget(sym: r.sym, qty: r.h.qty) } label: {
                        HStack(spacing: 10) {
                            // 발표했지만 숫자(신고서)가 아직이면 지난 분기가 아니라 발표한 분기 이름으로
                            Text("\(r.sym.name) \(s.pending != nil ? Self.nextMonths(s.months) : s.months)월 실적").appFont(14, .bold).lineLimit(1)
                            Spacer(minLength: 4)
                            if s.pending != nil {
                                Text("숫자 기다리는 중").appFont(13).foregroundStyle(Theme.sub).lineLimit(1)
                            } else {
                                if let y = s.netYoY {
                                    Text(y.hasPrefix("+") || y.hasPrefix("−") ? String(localized: "작년보다 \(y)") : y)
                                        .appFont(13).foregroundStyle(Theme.sub).monospacedDigit().lineLimit(1)
                                } else if s.loss {
                                    Text("적자 \(Earnings.money(s.short, s.currency))").appFont(13).foregroundStyle(Theme.sub).monospacedDigit().lineLimit(1)
                                }
                                EarnBars(s: s)
                            }
                        }
                        .padding(.horizontal, 14).frame(minHeight: 48).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint(String(localized: "내 몫 실적 보기"))
                    if r.id != items.last?.0.id { Divider().padding(.leading, 14) }
                }
            }
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
            .sheet(item: $river) { EarningsRiverSheet(target: $0) }
        }
    }

    /// 다음 분기 달: "4~6" → "7~9"
    static func nextMonths(_ m: String) -> String {
        let q = ["1~3", "4~6", "7~9", "10~12"]
        return q.firstIndex(of: m).map { q[($0 + 1) % 4] } ?? m
    }
}

// 오늘의 1분: 내 종목 오늘 숫자 확인 + 날마다 바뀌는 질문 하나 (지난 7일과 안 겹치게). 설명은 계산값으로.
struct RoutineCard: View {
    @Environment(AppModel.self) private var m
    private let days = [String(localized: "월"), String(localized: "화"), String(localized: "수"), String(localized: "목"), String(localized: "금"), String(localized: "토"), String(localized: "일")]
    /// 이번 주 칸 중 채운 날 수 (요일 7칸과 같은 기준)
    private var weekDone: Int {
        (0..<7).filter { i in let d = m.day - m.day % 7 + i; return m.dayLog[d].map { $0.answer != nil } ?? (d < 5 && d < m.day) }.count
    }

    var body: some View {
        let t = m.today, q = m.question
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("오늘의 1분").appFont(13, .bold).tracking(1).foregroundStyle(Theme.mint)
                Spacer()
                Text("이번 주 \(weekDone)일").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            }
            HStack(spacing: 4) {
                ForEach(0..<7, id: \.self) { i in
                    let d = m.day - m.day % 7 + i
                    let ok = m.dayLog[d].map { $0.answer != nil } ?? (d < 5 && d < m.day)
                    Text(days[i]).appFont(12, .bold)
                        .frame(maxWidth: .infinity, minHeight: 28)
                        .foregroundStyle(ok ? Theme.inkFixed : d == m.day ? Theme.yellow : Theme.muted)
                        .background(ok ? Theme.mint : d == m.day ? .clear : Theme.slate, in: RoundedRectangle(cornerRadius: 8))
                        .overlay { if d == m.day && !ok { RoundedRectangle(cornerRadius: 8).stroke(Theme.yellow, lineWidth: 2) } }
                }
            }
            step(tag: String(localized: "1 · 오늘 숫자")) {
                Text((try? AttributedString(markdown: m.routineFact, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(m.routineFact)).appFont(15).lineSpacing(3)
                if !t.seen {
                    Button("확인했어요") { withAnimation { m.markSeen() } }
                        .appFont(14, .bold).foregroundStyle(.white)
                        .padding(.horizontal, 16).frame(minHeight: 40)
                        .background(Theme.teal, in: RoundedRectangle(cornerRadius: 10))
                }
            }
            if t.seen {
                step(tag: "2 · \(q.tag)") {
                    Text(q.q).appFont(15, .semibold).lineSpacing(3)
                    FlowRow(spacing: 6) {
                        ForEach(q.opts.indices, id: \.self) { i in
                            let ans = t.answer
                            let right = q.right >= 0 && i == q.right
                            Button(q.opts[i]) { withAnimation { m.answer(i) } }
                                .disabled(ans != nil)
                                .appFont(14, .bold)
                                .padding(.horizontal, 14).frame(minHeight: 40)
                                .foregroundStyle(ans == nil ? Theme.ink : right ? Theme.tealDark : i == ans ? .white : Theme.muted)
                                .background(ans == nil ? Theme.card : right ? Theme.tealBg : i == ans ? Theme.night : Theme.card, in: RoundedRectangle(cornerRadius: 10))
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(ans == nil ? Theme.dash : right ? Theme.teal : i == ans ? Theme.ink : Theme.track, lineWidth: 2))
                        }
                    }
                    if let a = t.answer {
                        Text((t.ok.map { $0 ? String(localized: "맞아요! ") : String(localized: "아쉬워요. ") } ?? "") + q.fb(a))
                            .appFont(13).lineSpacing(3)
                            .padding(.horizontal, 10).padding(.vertical, 8)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Theme.mintBg, in: RoundedRectangle(cornerRadius: 10))
                    }
                }
            }
            if t.answer != nil {
                let sc = m.quizScore
                HStack(alignment: .firstTextBaseline) {
                    Text(m.routineDoneText).appFont(14, .semibold).foregroundStyle(Theme.yellow)
                    Spacer(minLength: 8)
                    if sc.all > 0 { Text("퀴즈 \(sc.right)/\(sc.all)").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6)) }
                }
            }
            // 날짜 넘기기는 개발자 확인용 (일반 사용자 화면에는 안 보인다)
            if m.devAll {
                Button("다음 날로 넘기기 (개발자)") { withAnimation { m.day += 1 } }
                    .appFont(13).underline().foregroundStyle(Theme.muted).frame(minHeight: 40)
            }
        }
        .foregroundStyle(.white)
        .padding(16)
        .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
    }

    private func step<C: View>(tag: String, @ViewBuilder _ c: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(tag).appFont(12, .bold).foregroundStyle(Theme.teal)
            c()
        }
        .foregroundStyle(Theme.ink)
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
    }
}

// 지금 평가액 카드 오른쪽 작은 그래프: 내 길 '시작부터' (실제 = 민트 선, 내 길 = 보라 점선). 누르면 분석 > 내 길
struct MiniPath: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        // 분석 > 내 길 '지금까지' 그래프와 같은 점·같은 세로 범위 (목표 금액 포함), 눈금·범위 띠만 뺀다
        let p = m.myPath, x1 = p.start + 1.0 / 12, gap = m.myPathGapToday, K = m.gK * 1e4
        let act = p.pts
        let need: [(Double, Double)] = (0...30).map { i in let t = x1 * Double(i) / 30; return (t, p.need(t)) }
        let needAll = (0...30).map { i in p.need(p.end * Double(i) / 30) }
        let vals = act.map(\.1) + needAll + [K]
        let hi = (vals.max() ?? 1) * 1.05, lo = (vals.filter { $0 > 0 }.min() ?? 0) * 0.9
        Button {
            m.tab = .analysis
            m.analysisPath = NavigationPath([AnalysisRoute.myPath])
        } label: {
            Canvas { ctx, size in
                let pt = { (q: (Double, Double)) -> CGPoint in
                    CGPoint(x: CGFloat(q.0 / max(1e-6, x1)) * size.width, y: CGFloat((hi - q.1) / max(1, hi - lo)) * size.height)
                }
                func line(_ qs: [(Double, Double)]) -> Path {
                    var path = Path()
                    for (i, q) in qs.enumerated() { i == 0 ? path.move(to: pt(q)) : path.addLine(to: pt(q)) }
                    return path
                }
                ctx.stroke(line(need), with: .color(Color(hex: 0xB79CF0)), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                ctx.stroke(line(act), with: .color(Theme.mint), style: StrokeStyle(lineWidth: 2, lineJoin: .round))
                if let last = act.last {
                    let end = pt((p.start, last.1))
                    ctx.fill(Path(ellipseIn: CGRect(x: end.x - 3, y: end.y - 3, width: 6, height: 6)), with: .color(Theme.mint))
                }
            }
            .frame(width: 84, height: 40)
            .pulse(Theme.mint, dot: 6) { s in act.last.map { CGPoint(x: CGFloat(p.start / max(1e-6, x1)) * s.width, y: CGFloat((hi - $0.1) / max(1, hi - lo)) * s.height) } }
            .overlay(alignment: .bottom) {
                Text(gap >= 0 ? String(localized: "계획대로") : String(localized: "조금 늦어요")).appFont(10, .bold)
                    .foregroundStyle(gap >= 0 ? Theme.teal : Color(hex: 0xB5651D, dark: 0xE8A060))
                    .fixedSize().offset(y: 16)
            }
            .padding(.bottom, 15)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("내 길 그래프, \(gap >= 0 ? String(localized: "계획대로") : String(localized: "계획보다 조금 늦어요")), 눌러서 분석의 내 길 보기")
    }
}

// 줄바꿈되는 가로 배치
struct FlowRow: Layout {
    var spacing: CGFloat = 6
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let w = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, maxX: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > 0 && x + sz.width > w { x = 0; y += rowH + spacing; rowH = 0 }
            x += sz.width + spacing; rowH = max(rowH, sz.height); maxX = max(maxX, x - spacing)
        }
        return CGSize(width: min(w, maxX), height: y + rowH)
    }
    func placeSubviews(in b: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = b.minX, y = b.minY, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > b.minX && x + sz.width > b.maxX { x = b.minX; y += rowH + spacing; rowH = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: .unspecified)
            x += sz.width + spacing; rowH = max(rowH, sz.height)
        }
    }
}

/// 샘플 종목 안내: 새 사용자는 가상 종목 DRNK·QQQ 샘플로 시작한다. 내 종목을 넣기 전까지 탭마다 보인다
struct SampleNote: View {
    @Environment(AppModel.self) private var m
    var body: some View {
        if m.isSample {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("샘플").appFont(12, .bold).foregroundStyle(.white)
                    .padding(.horizontal, 8).padding(.vertical, 2).background(Theme.orange, in: Capsule())
                Text("지금 숫자는 샘플(가상 종목 DRNK·QQQ)이에요. 종목 탭에서 내 종목을 넣으면 바뀌어요.")
                    .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            }
            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.orange.opacity(0.6)))
        }
    }
}

// MARK: 평가액 박스 모양 (2026-10-23). 사용자가 고른다 점프 직전(기본) → 지평선 → 욕조와 오리 → 진행 막대(예전 모습)
// 장면은 300×170 판에 그리고 박스 폭에 맞춰 늘린다. 글자는 장면 왼쪽 위에 겹친다.
// 가로 위치·물높이 = 지금 값 ÷ 목표(목표 루트) 또는 넣은 돈(회복 루트). 계획 자리 = 내 길 계획선(적립 포함, myPath.need)의 오늘 값.
// 움직임은 작은 조각만 반복 애니메이션 (장면 그림은 값이 바뀔 때만 다시 그린다). 동작 줄이기면 멈춘다

enum BoxLook: String, CaseIterable, Identifiable {
    case jump, horizon, tub, bar
    var id: String { rawValue }
    var name: String {
        switch self {
        case .horizon: return String(localized: "지평선")
        case .jump: return String(localized: "점프 직전")
        case .tub: return String(localized: "욕조와 오리")
        case .bar: return String(localized: "진행 막대")
        }
    }
    static let key = "homeBoxLook"
}

/// 장면에 넘기는 값 한 벌 (HomeView 가 만든다)
struct BoxScene {
    var label: String          // 평가액 · 모은 돈
    var amount: Double         // 크게 쓰는 금액 (원)
    var f: Double              // 지금 자리 (0 = 출발, 1 = 목표)
    var plan: Double?          // 계획 자리
    var ticks: [(String, Double)] = []   // 1년·2년 눈금 자리 (계획선 위)
    var target: String         // 10억 · 본전
    var half: String           // 가운데 눈금 글 (5억)
    var today: Double?         // 오늘 등락
    var ret: Double?           // 넣은 돈 대비
    var status: String         // 계획대로 · 본전까지 +3.1%
}

/// 박스 오른쪽 위 '1분 전'. 오래되면 '10/21 23:50 기준' (Market.freshText)
struct FreshLabel: View {
    var color: Color = Color(hex: 0x8A93A6)
    var body: some View {
        TimelineView(.everyMinute) { ctx in
            Text(Market.shared.freshText(now: ctx.date) ?? "").appFont(12).foregroundStyle(color).lineLimit(1)
        }
    }
}

struct SceneBox: View {
    let look: BoxLook
    let s: BoxScene
    @Binding var hide: Bool

    private var amountText: String { hide ? "••••••" : AppModel.man(s.amount) }
    private static let dim = Color(hex: 0x8A93A6)
    private static let upC = Color(hex: 0xFF8A80), downC = Color(hex: 0x8CC4F2)

    var body: some View {
        VStack(spacing: 0) {
            scene
            // 욕조: 수익률·계획 줄은 장면 아래 따로 (박스 끝에 붙지 않게)
            if look == .tub { subLine(13).padding(.top, 2).padding(.bottom, 12) }
        }
        // 욕조: 아래 수익률 줄까지 타일 벽이 이어지게 (장면 타일과 같은 칸 크기)
        .background { if look == .tub { GeometryReader { g in TileWall(cell: 22 * g.size.width / 300) } } }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(s.label) \(amountText), \(s.target)까지 \(AppModel.pct(s.f)), \(s.status)")
    }

    private var scene: some View {
        GeometryReader { g in
            let k = g.size.width / 300
            ZStack(alignment: .topLeading) {
                Group {
                    switch look {
                    case .jump: JumpScene(s: s)
                    case .tub: TubScene(s: s)
                    default: HorizonScene(s: s)
                    }
                }
                .frame(width: 300, height: 170)
                .scaleEffect(k, anchor: .topLeading)
                .allowsHitTesting(false)
                if look == .tub {
                    // 욕조: 금액은 욕조 앞면 (바닥 테두리 위)
                    amountView(20 * k, color: Color(hex: 0x16203A))
                        .frame(width: 200 * k).position(x: 150 * k, y: 130 * k)
                    head.padding(.horizontal, 18).padding(.top, 12)
                } else {
                    VStack(alignment: .leading, spacing: 2) {
                        head
                        amountView(30, color: .white)
                        subLine(13)
                    }
                    .padding(.horizontal, 18).padding(.top, 12)
                }
            }
        }
        .aspectRatio(300 / 170, contentMode: .fit)
    }

    private var head: some View {
        HStack { Text(s.label); Spacer(); FreshLabel() }.appFont(13).foregroundStyle(Self.dim)
    }
    private func amountView(_ size: CGFloat, color: Color) -> some View {
        Text(amountText).font(.system(size: size, weight: .heavy)).tracking(-0.5).foregroundStyle(color).roll(s.amount)
            .lineLimit(1).minimumScaleFactor(0.5)
            .contentShape(Rectangle()).onTapGesture { hide.toggle() }
    }
    private func subLine(_ size: CGFloat) -> some View {
        (Text(s.ret.map { AppModel.sgn($0) + " · " } ?? "").foregroundColor((s.ret ?? 0) >= 0 ? Self.upC : Self.downC)
         + Text(s.status).foregroundColor(Self.dim))
            .font(.system(size: size)).lineLimit(1).minimumScaleFactor(0.7)
    }
}

/// 욕조 뒤 남색 타일 벽 (cell = 타일 한 칸 크기)
struct TileWall: View {
    let cell: CGFloat
    var body: some View {
        Canvas { ctx, size in
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(hex: 0x1B2744)))
            guard cell > 1 else { return }
            var grid = Path(), x: CGFloat = 0, y: CGFloat = 0
            while x <= size.width { grid.move(to: CGPoint(x: x, y: 0)); grid.addLine(to: CGPoint(x: x, y: size.height)); x += cell }
            while y <= size.height { grid.move(to: CGPoint(x: 0, y: y)); grid.addLine(to: CGPoint(x: size.width, y: y)); y += cell }
            ctx.stroke(grid, with: .color(Color(hex: 0x2A3A60)), lineWidth: 1.5 * cell / 22)
        }
    }
}

/// 장면 그림 도구: 300×170 판 좌표
private enum Sk {
    static func p(_ pts: [(CGFloat, CGFloat)]) -> Path {
        var path = Path()
        for (i, q) in pts.enumerated() { i == 0 ? path.move(to: CGPoint(x: q.0, y: q.1)) : path.addLine(to: CGPoint(x: q.0, y: q.1)) }
        path.closeSubpath()
        return path
    }
    static func line(_ a: CGPoint, _ b: CGPoint) -> Path { var p = Path(); p.move(to: a); p.addLine(to: b); return p }
    static func dot(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)) }
    static func text(_ ctx: GraphicsContext, _ t: String, _ x: CGFloat, _ y: CGFloat, _ c: Color, size: CGFloat = 9.5, bold: Bool = false, anchor: UnitPoint = .leading) {
        ctx.draw(Text(t).font(.system(size: size, weight: bold ? .bold : .regular)).foregroundColor(c), at: CGPoint(x: x, y: y), anchor: anchor)
    }
    static func stars(_ ctx: GraphicsContext) {
        for (x, y, r) in [(200, 18, 0.9), (240, 42, 0.6), (170, 30, 0.5), (270, 22, 0.7), (120, 88, 0.5)] as [(CGFloat, CGFloat, CGFloat)] {
            ctx.fill(dot(x, y, r), with: .color(Color(hex: 0xC9D3EA)))
        }
    }
    /// 오늘 등락 글: ▼4.3%
    static func today(_ v: Double) -> String { String(localized: "오늘 ") + (v >= 0 ? "▲" : "▼") + String(format: "%.1f%%", abs(v) * 100) }
    static func todayColor(_ v: Double) -> Color { v >= 0 ? Color(hex: 0xFF8A80) : Color(hex: 0x9EC1FF) }
}

/// 반복 움직임 하나 (켜지면 on 이 true 로 돌고 되돌기를 되풀이한다)
private struct Loop<V: View>: View {
    var duration: Double
    var reverse = true
    var curve: Animation? = nil
    @ViewBuilder var content: (Bool) -> V
    @State private var on = false
    @Environment(\.accessibilityReduceMotion) private var still
    var body: some View {
        content(on).onAppear {
            guard !still else { return }
            withAnimation((curve ?? .easeInOut(duration: duration)).repeatForever(autoreverses: reverse)) { on = true }
        }
    }
}

// MARK: 시안 1 지평선: 새벽빛 밤하늘, 출발~목표 지평선(1년·2년 눈금), 숨 쉬는 내 불빛과 점선 계획 자리, 오늘 등락은 작은 물결
private struct HorizonScene: View {
    let s: BoxScene
    var body: some View {
        let x0: CGFloat = 10, x1: CGFloat = 290, y: CGFloat = 130
        let X = { (f: Double) in x0 + (x1 - x0) * CGFloat(min(1, max(0, f))) }
        let me = X(s.f)
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                let all = Path(CGRect(x: 0, y: 0, width: 300, height: 170))
                ctx.fill(all, with: .linearGradient(Gradient(colors: [Color(hex: 0x0C111D), Color(hex: 0x1C2747)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: 170)))
                ctx.fill(all, with: .radialGradient(Gradient(colors: [Color(hex: 0xFFD98A).opacity(0.4), Color(hex: 0xFFD98A).opacity(0)]), center: CGPoint(x: 300, y: 136), startRadius: 0, endRadius: 190))
                Sk.stars(ctx)
                ctx.stroke(Sk.line(CGPoint(x: x0, y: y), CGPoint(x: x1, y: y)), with: .color(Color(hex: 0x4A5672)), lineWidth: 1.5)
                if me > x0 {
                    ctx.stroke(Sk.line(CGPoint(x: x0, y: y), CGPoint(x: me, y: y)),
                               with: .linearGradient(Gradient(colors: [Color(hex: 0xF2C14E).opacity(0.15), Color(hex: 0xF2C14E).opacity(0.9)]), startPoint: CGPoint(x: x0, y: y), endPoint: CGPoint(x: me, y: y)), lineWidth: 2.5)
                }
                for (t, f) in s.ticks where f > 0 && f < 1 {
                    ctx.stroke(Sk.line(CGPoint(x: X(f), y: y - 4), CGPoint(x: X(f), y: y + 4)), with: .color(Color(hex: 0x4A5672)), lineWidth: 1)
                    Sk.text(ctx, t, X(f), 150, Color(hex: 0x8A93A6), anchor: .center)
                }
                ctx.fill(Sk.dot(x1, y, 3), with: .color(Color(hex: 0xFFD98A)))
                if let p = s.plan {
                    ctx.stroke(Sk.dot(X(p), y, 7), with: .color(Color(hex: 0x8A93A6)), style: StrokeStyle(lineWidth: 1.2, dash: [2, 2]))
                }
                Sk.text(ctx, String(localized: "출발"), x0, 150, Color(hex: 0x8A93A6))
                Sk.text(ctx, s.target, x1, 150, Color(hex: 0xFFD98A), anchor: .trailing)
            }
            // 숨 쉬는 내 불빛
            Loop(duration: 1.5) { on in
                Circle().fill(RadialGradient(colors: [Color(hex: 0xF2C14E).opacity(0.7), Color(hex: 0xF2C14E).opacity(0)], center: .center, startRadius: 0, endRadius: 16))
                    .frame(width: 32, height: 32).scaleEffect(on ? 1.25 : 1).opacity(on ? 0.35 : 1)
            }
            .position(x: me, y: y)
            Circle().fill(Color(hex: 0xFFF4D6)).frame(width: 9, height: 9).position(x: me, y: y)
            if let v = s.today {
                // 오늘 등락: 내 불빛 위 작은 물결
                Loop(duration: 2) { on in
                    HStack(spacing: 3) {
                        Wave().stroke(Sk.todayColor(v), lineWidth: 1.3).frame(width: 18, height: 4)
                        Text(Sk.today(v)).font(.system(size: 9.5)).foregroundStyle(Sk.todayColor(v)).fixedSize()
                    }
                    .offset(y: on ? -2 : 0)
                }
                .position(x: min(250, max(50, me + 20)), y: 112)
            }
        }
    }
}

/// 물결 한 줄 (지평선 오늘 등락)
private struct Wave: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path(); let n = 3, w = r.width / CGFloat(n)
        p.move(to: CGPoint(x: r.minX, y: r.midY))
        for i in 0..<n {
            let a = r.minX + w * CGFloat(i)
            p.addQuadCurve(to: CGPoint(x: a + w, y: r.midY), control: CGPoint(x: a + w / 2, y: i % 2 == 0 ? r.minY : r.maxY))
        }
        return p
    }
}

// MARK: 시안 3 욕조와 오리: 남색 타일 벽 앞 흰 욕조. 욕조 안쪽 맨 위 눈금이 목표, 그 위로 넘칠 여유. 노란 오리가 나, 금색 줄이 계획
private struct TubScene: View {
    let s: BoxScene
    /// 물높이: 바닥 150, 목표 70 (넘치면 54까지)
    static func level(_ f: Double) -> CGFloat { 150 - 80 * CGFloat(min(1.2, max(0, f))) }
    static let tub: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 40, y: 52)); p.addLine(to: CGPoint(x: 260, y: 52)); p.addLine(to: CGPoint(x: 260, y: 120))
        p.addQuadCurve(to: CGPoint(x: 230, y: 150), control: CGPoint(x: 260, y: 150))
        p.addLine(to: CGPoint(x: 70, y: 150))
        p.addQuadCurve(to: CGPoint(x: 40, y: 120), control: CGPoint(x: 40, y: 150))
        p.closeSubpath()
        return p
    }()

    var body: some View {
        let w = Self.level(s.f)
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                // 타일 벽
                ctx.fill(Path(CGRect(x: 0, y: 0, width: 300, height: 170)), with: .color(Color(hex: 0x1B2744)))
                var grid = Path()
                for i in 0...14 { let x = CGFloat(i) * 22; grid.move(to: CGPoint(x: x, y: 0)); grid.addLine(to: CGPoint(x: x, y: 170)) }
                for j in 0...8 { let y = CGFloat(j) * 22; grid.move(to: CGPoint(x: 0, y: y)); grid.addLine(to: CGPoint(x: 300, y: y)) }
                ctx.stroke(grid, with: .color(Color(hex: 0x2A3A60)), lineWidth: 1.5)
                // 욕조 안쪽 (눈금은 물 위에 따로 그린다)
                var inner = ctx
                inner.clip(to: Self.tub)
                inner.fill(Path(CGRect(x: 0, y: 0, width: 300, height: 170)), with: .color(Color(hex: 0xEEF2F8)))
            }
            // 물: 욕조 안에서 출렁인다
            Loop(duration: 3, reverse: false, curve: .linear(duration: 3)) { on in
                TubWater().fill(LinearGradient(colors: [Color(hex: 0x7CC8F0).opacity(0.85), Color(hex: 0x3F7FC0).opacity(0.9)], startPoint: .top, endPoint: .bottom))
                    .frame(width: 400, height: 170 - w + 6)
                    .offset(x: on ? -10 : -70, y: w - 6)
            }
            .frame(width: 300, height: 170, alignment: .topLeading)
            .clipShape(Self.tub)
            Canvas { ctx, _ in
                // 눈금·계획 줄: 물 위에. 물에 잠긴 눈금은 흰색으로
                var inner = ctx
                inner.clip(to: Self.tub)
                let mark = { (y: CGFloat, dry: Color) in y > w ? Color.white.opacity(0.9) : dry }
                inner.stroke(Sk.line(CGPoint(x: 240, y: 70), CGPoint(x: 254, y: 70)), with: .color(mark(70, Color(hex: 0x7D889E))), lineWidth: 1.6)
                Sk.text(inner, s.target, 237, 70, mark(70, Color(hex: 0x7D889E)), size: 8.5, bold: true, anchor: .trailing)
                for (t, y) in [("75%", 90.0), ("25%", 130.0)] as [(String, CGFloat)] {
                    inner.stroke(Sk.line(CGPoint(x: 246, y: y), CGPoint(x: 254, y: y)), with: .color(mark(y, Color(hex: 0x9AA6BD))), lineWidth: 1.2)
                    Sk.text(inner, t, 243, y, mark(y, Color(hex: 0x9AA6BD)), size: 8, anchor: .trailing)
                }
                if let p = s.plan {
                    let y = Self.level(p)
                    inner.stroke(Sk.line(CGPoint(x: 236, y: y), CGPoint(x: 254, y: y)), with: .color(Color(hex: 0xC98A00)), lineWidth: 2.2)
                    Sk.text(inner, String(localized: "계획"), 233, y - 4, y - 4 > w ? Color(hex: 0xFFE08A) : Color(hex: 0xC98A00), size: 8.5, bold: true, anchor: .trailing)
                }
                ctx.stroke(Self.tub, with: .color(.white), lineWidth: 3)
                ctx.fill(Path(roundedRect: CGRect(x: 32, y: 46, width: 236, height: 8), cornerRadius: 4), with: .color(.white))
            }
            // 오리 = 나
            Loop(duration: 1.3) { on in Duck().rotationEffect(.degrees(on ? -2 : 0)).offset(y: on ? 2.5 : 0) }
                .position(x: 156, y: w - 12)
            if let v = s.today {
                Text(Sk.today(v)).font(.system(size: 9.5)).foregroundStyle(Sk.todayColor(v)).fixedSize()
                    .position(x: 100, y: w - 21)
            }
        }
    }
}

private struct TubWater: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path(); let w: CGFloat = 15
        p.move(to: CGPoint(x: 0, y: 6))
        var x: CGFloat = 0, up = true
        while x < r.width { p.addQuadCurve(to: CGPoint(x: x + w * 2, y: 6), control: CGPoint(x: x + w, y: up ? 1 : 11)); x += w * 2; up.toggle() }
        p.addLine(to: CGPoint(x: x, y: r.height)); p.addLine(to: CGPoint(x: 0, y: r.height)); p.closeSubpath()
        return p
    }
}

private struct Duck: View {
    var body: some View {
        Canvas { ctx, _ in
            // 판 좌표 (136~177, 96~130) 을 (0,0) 기준으로
            ctx.translateBy(x: -133, y: -94)
            let y = Color(hex: 0xFFD23F)
            ctx.fill(Path(ellipseIn: CGRect(x: 133, y: 110, width: 34, height: 20)), with: .color(y))
            var tail = Path(); tail.move(to: CGPoint(x: 136, y: 116)); tail.addQuadCurve(to: CGPoint(x: 134, y: 106), control: CGPoint(x: 130, y: 110))
            tail.addQuadCurve(to: CGPoint(x: 140, y: 114), control: CGPoint(x: 140, y: 110)); tail.closeSubpath()
            ctx.fill(tail, with: .color(y))
            ctx.fill(Sk.dot(162, 105, 8.5), with: .color(y))
            ctx.fill(Sk.p([(169, 104), (177, 106), (169, 109)]), with: .color(Color(hex: 0xFF8A3D)))
            ctx.fill(Sk.dot(164, 103, 1.4), with: .color(Color(hex: 0x1D2330)))
        }
        .frame(width: 46, height: 38)
    }
}

// MARK: 시안 4 점프 직전: 밤 도로를 은빛 쐐기형 차가 불꽃 두 줄을 끌며 오른쪽 파란 일렁임(목표)으로. 앞코 = 내 자리, 이정표 봉 = 계획
private struct JumpScene: View {
    let s: BoxScene
    var body: some View {
        let x0: CGFloat = 20, x1: CGFloat = 284
        let X = { (f: Double) in x0 + (x1 - x0) * CGFloat(min(1, max(0, f))) }
        let nose = X(s.f), rear = nose - 85
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                ctx.fill(Path(CGRect(x: 0, y: 0, width: 300, height: 170)), with: .linearGradient(Gradient(colors: [Color(hex: 0x0B1022), Color(hex: 0x232A52)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: 170)))
                for (x, y, r) in [(200, 18, 0.9), (240, 44, 0.6), (170, 40, 0.5)] as [(CGFloat, CGFloat, CGFloat)] { ctx.fill(Sk.dot(x, y, r), with: .color(Color(hex: 0xC9D3EA))) }
                ctx.fill(Path(CGRect(x: 0, y: 128, width: 300, height: 42)), with: .color(Color(hex: 0x151925)))
                ctx.stroke(Sk.line(CGPoint(x: 0, y: 160), CGPoint(x: 300, y: 160)), with: .color(Color(hex: 0x3A4155)), style: StrokeStyle(lineWidth: 1.5, dash: [10, 8]))
                Sk.text(ctx, s.target, x1, 62, Color(hex: 0x9FDCFF), size: 10, bold: true, anchor: .center)
                for (i, f) in [0, 0.25, 0.5, 0.75].enumerated() {
                    let x = X(f), long = i % 2 == 0
                    ctx.stroke(Sk.line(CGPoint(x: x, y: 128), CGPoint(x: x, y: long ? 134 : 131)), with: .color(Color(hex: 0x6B7489)), lineWidth: 1)
                }
                Sk.text(ctx, String(localized: "출발"), x0, 166, Color(hex: 0x6B7489), size: 8, anchor: .center)
                Sk.text(ctx, s.half, X(0.5), 166, Color(hex: 0x6B7489), size: 8, anchor: .center)
                if let p = s.plan {
                    let x = X(p)
                    ctx.stroke(Sk.line(CGPoint(x: x, y: 96), CGPoint(x: x, y: 131)), with: .color(Color(hex: 0x9AA2B2)), lineWidth: 2)
                    let sign = Path(roundedRect: CGRect(x: x - 12, y: 86, width: 24, height: 13), cornerRadius: 2)
                    ctx.fill(sign, with: .color(Color(hex: 0x2B3350))); ctx.stroke(sign, with: .color(Color(hex: 0x9AA2B2)), lineWidth: 1)
                    Sk.text(ctx, String(localized: "계획"), x, 92.5, Color(hex: 0xFFD98A), size: 8, anchor: .center)
                }
                if let v = s.today { Sk.text(ctx, Sk.today(v), 6, 120, Sk.todayColor(v)) }
            }
            // 목표: 파란 일렁임
            Loop(duration: 1.2) { on in
                Ellipse().fill(EllipticalGradient(colors: [Color(hex: 0xE8F7FF), Color(hex: 0x5FC8FF).opacity(0.8), Color(hex: 0x2A6CFF).opacity(0)], center: .center, startRadiusFraction: 0, endRadiusFraction: 0.5))
                    .frame(width: 32, height: 84).scaleEffect(x: on ? 1.25 : 1, y: 1).opacity(on ? 0.75 : 1)
            }
            .position(x: x1, y: 112)
            // 불꽃 두 줄 (출발부터 차 뒤까지)
            if rear + 13 > x0 {
                Loop(duration: 0.4) { on in
                    VStack(alignment: .leading, spacing: 2.5) {
                        Capsule().frame(width: max(0, rear + 13 - x0), height: 2.5)
                        Capsule().frame(width: max(0, rear + 15 - x0 - 4), height: 2.5).padding(.leading, 4)
                    }
                    .foregroundStyle(LinearGradient(stops: [.init(color: Color(hex: 0xFF5A1F).opacity(0), location: 0), .init(color: Color(hex: 0xFF7A1F), location: 0.6), .init(color: Color(hex: 0xFFE27A), location: 1)], startPoint: .leading, endPoint: .trailing))
                    .opacity(on ? 0.7 : 1)
                }
                .offset(x: x0, y: 140)
            }
            Loop(duration: 0.12) { on in Car().offset(y: on ? 0.6 : 0) }
                .frame(width: 95, height: 35)
                .position(x: nose - 84.7 + 47.5, y: 129.5)
        }
        .clipped()
    }
}

/// 은빛 쐐기형 차 (시안 4 의 차를 0.7 배로, 앞코가 오른쪽 끝)
private struct Car: View {
    var body: some View {
        Canvas { ctx, _ in
            ctx.scaleBy(x: 0.7, y: 0.7)
            ctx.translateBy(x: -100, y: -97)
            let body = Sk.p([(100, 138), (100, 123), (128, 121), (146, 109), (168, 108), (188, 121), (220, 128), (221, 133), (214, 138)])
            ctx.fill(body, with: .linearGradient(Gradient(colors: [Color(hex: 0xEEF1F6), Color(hex: 0x8D95A6)]), startPoint: CGPoint(x: 0, y: 108), endPoint: CGPoint(x: 0, y: 138)))
            ctx.fill(Sk.p([(100, 133), (220, 133), (214, 138), (100, 138)]), with: .color(Color(hex: 0x2A2E38)))
            ctx.stroke(Sk.line(CGPoint(x: 100, y: 128), CGPoint(x: 220, y: 130)), with: .color(Color(hex: 0xAAB1BF)), lineWidth: 0.8)
            ctx.fill(Sk.p([(150, 111), (167, 111), (180, 120), (146, 120)]), with: .color(Color(hex: 0x2B3550)))
            ctx.stroke(Sk.line(CGPoint(x: 158, y: 111), CGPoint(x: 156, y: 120)), with: .color(Color(hex: 0x9AA2B2)), lineWidth: 1)
            for x in [131.0, 135, 139] as [CGFloat] { ctx.stroke(Sk.line(CGPoint(x: x, y: 120), CGPoint(x: x + 6, y: 115)), with: .color(Color(hex: 0x59606F)), lineWidth: 1) }
            ctx.fill(Path(CGRect(x: 113, y: 131, width: 18, height: 9)), with: .color(Color(hex: 0x151925)))
            ctx.fill(Path(CGRect(x: 187, y: 131, width: 18, height: 9)), with: .color(Color(hex: 0x151925)))
            ctx.fill(Path(CGRect(x: 214, y: 128.5, width: 6, height: 2)), with: .color(Color(hex: 0xFFF6C8)))
            ctx.fill(Path(CGRect(x: 100, y: 124, width: 3, height: 4)), with: .color(Color(hex: 0xFF4A4A)))
            for x in [122.0, 196] as [CGFloat] {
                ctx.fill(Sk.dot(x, 140, 7), with: .color(Color(hex: 0x111111)))
                ctx.stroke(Sk.dot(x, 140, 7), with: .color(Color(hex: 0x7D8597)), lineWidth: 2)
            }
            do {   // 앞코·지붕 위 작은 번개
                var arc = Path()
                arc.move(to: CGPoint(x: 222, y: 121)); arc.addLine(to: CGPoint(x: 228, y: 116)); arc.addLine(to: CGPoint(x: 226, y: 123)); arc.addLine(to: CGPoint(x: 234, y: 119))
                arc.move(to: CGPoint(x: 152, y: 104)); arc.addLine(to: CGPoint(x: 156, y: 98)); arc.addLine(to: CGPoint(x: 157, y: 104)); arc.addLine(to: CGPoint(x: 162, y: 99))
                ctx.stroke(arc, with: .color(Color(hex: 0x7FD4FF)), lineWidth: 1.2)
            }
        }
    }
}

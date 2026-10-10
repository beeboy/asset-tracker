import SwiftUI

// 설정 상세 (시안 26·27판): 알림 · 기기 동기화 · 위젯 · 세금 규칙 · 사용 방법 · 루트.
// 실제 알림 예약·동기화 서버 연결은 아직 없고, 화면과 상태만 있다.

private struct SettingsPage<Content: View>: View {
    let title: String
    @ViewBuilder var content: Content
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(title).appFont(22, .bold)
                content
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private func note(_ t: String) -> some View {
    Text(t).appFont(12).foregroundStyle(Theme.muted).lineSpacing(2).frame(maxWidth: .infinity, alignment: .leading)
}

private func optionChips(_ label: String, _ opts: [Int], _ unit: String, _ sel: Int, _ pick: @escaping (Int) -> Void) -> some View {
    HStack(spacing: 6) {
        Text(label).appFont(13).foregroundStyle(Theme.sub)
        ForEach(opts, id: \.self) { n in
            Button("\(n)\(unit)") { pick(n) }
                .appFont(14, .bold).padding(.horizontal, 12).frame(minHeight: 40)
                .foregroundStyle(sel == n ? .white : Theme.ink)
                .background(sel == n ? Theme.teal : .white, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(sel == n ? Theme.teal : Theme.border, lineWidth: 2))
        }
    }
}

// MARK: 알림 — 위에 미리보기 고정

struct AlertsView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        let need = m.cost / max(1, m.total) - 1
        let list: [(k: String, t: String, sub: String, prev: String)] = [
            ("be", "본전 도달", "어제 종가 기준 평가액이 들어간 돈을 넘으면 한 번", "본전에 도착했어요! 평가액이 들어간 돈 \(AppModel.man(m.cost))을 넘었어요."),
            ("drift", "비중 이탈", "DRNK 비중이 계획에서 \(m.alertTh)%p 넘게 벗어나면",
             "DRNK 비중이 \(AppModel.pct(m.drnkWeight))예요. 계획(\(AppModel.pct(m.planWeight)))보다 \(Int((abs(m.drnkWeight - m.planWeight) * 100).rounded()))%p 벗어났어요."),
            ("dep", "연말 절세 확인", "12월 1일, 올해 손실을 확정할지 볼 때", "올해가 한 달 남았어요. 손실 난 종목 일부를 팔면 내년 세금이 줄 수 있어요."),
            ("morn", "아침 한 줄", "평일 \(m.alertHr)시, 어제의 움직임 한 줄", "어제 \(AppModel.sgn(m.yesterdayMove)). 본전까지 \(String(format: "%.1f", need * 100))% 남았어요."),
        ]
        let on = list.filter { m.alerts[$0.k] == true }
        let prev = on.first { $0.k == m.alertLast } ?? on.first
        let perMonth = (m.alerts["drift"] == true ? 1 : 0) + (m.alerts["dep"] == true ? 1 : 0) + (m.alerts["morn"] == true ? 21 : 0)

        PinnedLayout {
            VStack(alignment: .leading, spacing: 10) {
                Text("알림").appFont(22, .bold)
                HStack {
                    Text("알림 미리보기").appFont(13, .bold).foregroundStyle(Theme.sub)
                    Spacer()
                    Text("켠 알림 \(on.count)개" + (perMonth > 0 ? " · 한 달 약 \(perMonth)번" : "")).appFont(12).foregroundStyle(Theme.sub)
                }
                HStack(alignment: .top, spacing: 10) {
                    Text("n").appFont(18, .heavy).foregroundStyle(.white).frame(width: 36, height: 36)
                        .background(Theme.teal, in: RoundedRectangle(cornerRadius: 9))
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text(prev?.t ?? "알림 꺼짐").appFont(14, .bold)
                            Spacer()
                            Text(prev == nil ? "" : prev!.k == "morn" ? "오전 \(m.alertHr):00" : "지금").appFont(12).foregroundStyle(Theme.muted)
                        }
                        Text(prev?.prev ?? "켠 알림이 없어요. 아래에서 하나 이상 켜 보세요.").appFont(13).fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding(12)
                .background(Color(hex: 0xEEF1F5), in: RoundedRectangle(cornerRadius: 16))
            }
            .padding(16).background(.white).overlay(alignment: .bottom) { Divider() }
        } content: {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(list, id: \.k) { a in
                    let isOn = m.alerts[a.k] == true
                    Card(padding: 14) {
                        Toggle(isOn: Binding(get: { isOn }, set: { v in m.alerts[a.k] = v; if v { m.alertLast = a.k } })) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(a.t).appFont(16, .bold)
                                Text(a.sub).appFont(13).foregroundStyle(Theme.sub)
                            }
                        }
                        .tint(Theme.teal)
                        if isOn && a.k == "drift" { optionChips("기준", [3, 5, 10], "%p", m.alertTh) { m.alertTh = $0 } }
                        if isOn && a.k == "morn" { optionChips("시간", [7, 8, 9], "시", m.alertHr) { m.alertHr = $0 } }
                    }
                }
                note("여러 알림이 겹치는 날은 하나로 묶어 하루 한 번만 보내요. 알림 설정은 기기마다 따로예요. 실제 알림 예약은 다음 빌드에서 연결해요.")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: 기기 동기화 — PC naeilo.com 6자리 코드

struct SyncView: View {
    @Environment(AppModel.self) private var m
    @State private var code = ""

    var body: some View {
        SettingsPage(title: "기기 동기화") {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 10) {
                    Image(systemName: "iphone").font(.system(size: 26))
                    Rectangle().fill(m.syncOn ? Theme.yellow : Theme.muted).frame(width: 60, height: 3)
                        .mask { if m.syncOn { Rectangle() } else { HStack(spacing: 4) { ForEach(0..<8, id: \.self) { _ in Rectangle() } } } }
                    Image(systemName: "desktopcomputer").font(.system(size: 26))
                }
                Text(m.syncOn ? "연결됨" : "아직 연결 안 됨").appFont(20, .bold)
                Text(m.syncOn ? "마지막 동기화: 방금" : "폰과 PC가 같은 숫자를 보려면 연결해요").appFont(13)
            }
            .foregroundStyle(m.syncOn ? .white : Theme.ink)
            .padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(m.syncOn ? Theme.teal : Theme.track, in: RoundedRectangle(cornerRadius: 20))

            if m.syncOn {
                Card {
                    Text("연결된 기기").appFont(15, .bold)
                    row("이 iPhone", "지금 사용 중")
                    row("PC · naeilo.com", "오늘 09:12")
                }
                Card {
                    Text("함께 맞춰지는 것").appFont(15, .bold)
                    ForEach([("종목·수량·평균 단가", true), ("미션 진행과 위젯", true), ("목표와 계획", true), ("알림 설정", false)], id: \.0) { k, y in
                        HStack { Text(k).appFont(14); Spacer(); Text(y ? "함께" : "기기마다 따로").appFont(13, .semibold).foregroundStyle(y ? Theme.teal : Theme.muted) }
                    }
                }
                Button("연결 끊기") { withAnimation { m.syncOn = false } }
                    .appFont(15, .semibold).foregroundStyle(Theme.up).frame(maxWidth: .infinity, minHeight: 48)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
            } else {
                let ok = code.count == 6 && code.allSatisfy(\.isNumber)
                Card {
                    Text("PC와 연결하기").appFont(15, .bold)
                    Text("1. PC 브라우저에서 naeilo.com에 들어가요.\n2. 오른쪽 위 \"폰 연결\"을 누르면 6자리 코드가 나와요.\n3. 그 코드를 아래에 넣어요.").appFont(14).lineSpacing(4)
                    TextField("000000", text: $code).keyboardType(.numberPad).appFont(24, .bold).multilineTextAlignment(.center)
                        .frame(minHeight: 56).overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
                        .accessibilityLabel("연결 코드 6자리")
                        .onChange(of: code) { _, v in code = String(v.filter(\.isNumber).prefix(6)) }
                    PrimaryButton(title: "연결하기", color: ok ? Theme.teal : Theme.muted) { if ok { withAnimation { m.syncOn = true; code = "" } } }
                    Text("코드는 5분 동안만 쓸 수 있어요. 시안에서는 아무 숫자 6자리나 넣으면 돼요.").appFont(12).foregroundStyle(Theme.muted)
                }
            }
            note("같은 계정이면 폰에서 넣은 종목과 미션 진행이 PC naeilo.com에도 바로 보여요. 계산 근거와 큰 그래프는 PC에서 보면 편해요.")
        }
    }

    private func row(_ k: String, _ v: String) -> some View {
        HStack { Text(k).appFont(14, .bold); Spacer(); Text(v).appFont(13).foregroundStyle(Theme.sub) }
    }
}

// MARK: 위젯 고르기 — 홈 화면 미리보기 + 받은 것 추가/빼기

extension AppModel {
    struct WidgetItem: Identifiable { let id, name: String; let size: Int; let val: String; let ok: Bool; let how: String; let friend: Bool }
    var widgets: [WidgetItem] {
        let n = nxStep, gift = nxStep >= 3, need = cost / max(1, total) - 1
        let base: [WidgetItem] = [
            .init(id: "trend", name: "자산 추이", size: 1, val: AppModel.man(total), ok: n >= 1, how: "앱 시작 1단계", friend: false),
            .init(id: "prog", name: "본전 진행", size: 1, val: "+" + String(format: "%.1f", need * 100) + "% 남음", ok: n >= 2, how: "앱 시작 2단계", friend: false),
            .init(id: "block", name: "블록", size: 1, val: "\(done.filter { [1, 2, 3, 5].contains($0) }.count)/4", ok: n >= 3, how: "앱 시작 3단계", friend: false),
            .init(id: "yest", name: "어제의 움직임", size: 2, val: AppModel.sgn(yesterdayMove) + " · DRNK -2.2% · QQQ +0.4%", ok: gift, how: "앱 시작 3단계 특별 선물", friend: false),
            .init(id: "mix", name: "비중", size: 1, val: "DRNK " + AppModel.pct(drnkWeight), ok: gift, how: "앱 시작 3단계 특별 선물", friend: false),
            .init(id: "div", name: "배당 달력", size: 2, val: "다음 배당 QQQ 12월", ok: gift, how: "앱 시작 3단계 특별 선물", friend: false),
            .init(id: "fx", name: "환율", size: 1, val: "\(Int(Sample.fx).formatted())원", ok: gift, how: "앱 시작 3단계 특별 선물", friend: false),
            .init(id: "big", name: "본전 진행 (큰)", size: 3, val: AppModel.man(total) + " · +" + String(format: "%.1f", need * 100) + "% 남음", ok: gift, how: "앱 시작 3단계 특별 선물", friend: false),
        ]
        let fr = Shelter.friends.dropFirst().enumerated().map { i, f in
            WidgetItem(id: "ch\(i)", name: "\(f.name) 자산 추이", size: 1, val: f.kind, ok: i < friendsOpen, how: "\(i + 1)주차 인터미션", friend: true)
        }
        return base + fr
    }
    var widgetSelected: [String] {
        (widgetSel ?? widgets.filter(\.ok).prefix(3).map(\.id)).filter { id in widgets.contains { $0.id == id && $0.ok } }
    }
}

struct WidgetPickView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        let all = m.widgets, sel = m.widgetSelected
        let shown = sel.compactMap { id in all.first { $0.id == id } }
        PinnedLayout {
            VStack(alignment: .leading, spacing: 10) {
                Text("위젯 고르기").appFont(22, .bold).foregroundStyle(.white)
                HStack {
                    Text("홈 화면 미리보기").appFont(13, .bold).foregroundStyle(Color(hex: 0xC9D0D6))
                    Spacer()
                    Text("\(shown.count)개").appFont(12).foregroundStyle(Color(hex: 0xC9D0D6))
                }
                if shown.isEmpty {
                    Text(all.contains(where: \.ok) ? "아래에서 위젯을 추가해 보세요." : "아직 받은 위젯이 없어요. 앱 시작 3단계에서 하나씩 받아요.")
                        .appFont(13).foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 80)
                } else {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                        ForEach(shown) { w in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(w.name).appFont(11, .bold)
                                Spacer(minLength: 0)
                                Text(w.val).appFont(w.size == 3 ? 18 : 13, .bold).lineLimit(2).minimumScaleFactor(0.8)
                            }
                            .foregroundStyle(w.friend ? Color(hex: 0x5A3E00) : Theme.ink)
                            .padding(10).frame(maxWidth: .infinity, minHeight: w.size == 3 ? 110 : 64, alignment: .topLeading)
                            .background(w.friend ? Color(hex: 0xFFF6DE) : .white, in: RoundedRectangle(cornerRadius: 16))
                            .gridCellColumns(w.size >= 2 ? 2 : 1)
                        }
                    }
                }
            }
            .padding(16)
            .background(LinearGradient(colors: [Color(hex: 0x2C4A6B), Color(hex: 0x15202B)], startPoint: .top, endPoint: .bottom))
        } content: {
            VStack(alignment: .leading, spacing: 10) {
                VStack(spacing: 0) {
                    ForEach(Array(all.enumerated()), id: \.element.id) { i, w in
                        if i > 0 { Divider().overlay(Theme.line) }
                        let on = sel.contains(w.id)
                        Button {
                            guard w.ok else { return }
                            m.widgetSel = on ? sel.filter { $0 != w.id } : sel + [w.id]
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(w.name).appFont(16, .semibold).foregroundStyle(w.ok ? Theme.ink : Theme.muted)
                                    Text(["", "작은", "중간", "큰"][w.size] + " 위젯 · " + (w.ok ? (on ? "홈 화면에 있음" : "받음") : w.how + "에서 받아요"))
                                        .appFont(12).foregroundStyle(Theme.sub)
                                }
                                Spacer()
                                Text(w.ok ? (on ? "빼기" : "추가") : "잠김").appFont(13, .bold)
                                    .padding(.horizontal, 12).padding(.vertical, 6)
                                    .foregroundStyle(!w.ok ? Theme.muted : on ? Theme.sub : .white)
                                    .background(!w.ok ? Theme.line : on ? .clear : Theme.teal, in: Capsule())
                                    .overlay { if w.ok && on { Capsule().stroke(Theme.dash) } }
                            }
                            .padding(.horizontal, 14).frame(minHeight: 60).contentShape(Rectangle())
                        }
                        .buttonStyle(.plain).disabled(!w.ok)
                    }
                }
                .background(.white, in: RoundedRectangle(cornerRadius: 18))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
                note("실제 아이폰에서는 홈 화면을 길게 눌러 위젯을 놓아요. 여기서 고른 순서대로 추천해 드려요. 위젯 자체는 기존 위젯 앱(ios/)과 합칠 예정이에요.")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: 세금 규칙

struct TaxRulesView: View {
    @Environment(AppModel.self) private var m
    @State private var gain = ""

    var body: some View {
        let G = m.taxGain, tax = max(0, G - 250) * 0.22
        let won0 = { (x: Double) in Int(x.rounded()).formatted() + "만원" }
        SettingsPage(title: "세금 규칙") {
            FlowRow(spacing: 6) {
                ForEach(["대한민국 거주자", "미국 거주자 (준비 중)", "일본 거주자 (준비 중)"], id: \.self) { t in
                    let on = t == "대한민국 거주자"
                    Text(t).appFont(14, .bold).padding(.horizontal, 14).frame(minHeight: 40)
                        .foregroundStyle(on ? .white : Theme.muted)
                        .background(on ? Theme.teal : Theme.line, in: Capsule())
                }
            }
            VStack(alignment: .leading, spacing: 6) {
                Text("내년 5월에 낼 해외주식 세금 (예상)").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                Text(won0(tax)).appFont(30, .bold)
                Text(G > 250 ? "(\(won0(G)) − 기본공제 250만원) × 22%" : "이익이 250만원 이하라 낼 세금이 없어요").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            }
            .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.ink, in: RoundedRectangle(cornerRadius: 20))
            VStack(alignment: .leading, spacing: 4) {
                Text("올해 해외주식 실현 이익 (만원)").appFont(13, .semibold).foregroundStyle(Theme.sub)
                TextField("", text: $gain).keyboardType(.numberPad).appFont(18, .semibold)
                    .padding(.horizontal, 12).frame(minHeight: 48)
                    .background(.white, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border, lineWidth: 2))
                    .onChange(of: gain) { _, v in m.taxGain = max(0, Double(v) ?? 0) }
                    .accessibilityLabel("올해 해외주식 실현 이익 (만원)")
            }
            Card {
                ForEach([("해외주식 양도소득세", "한 해 이익과 손실을 합쳐 250만원을 넘는 부분에 22% (지방세 포함). 다음 해 5월에 직접 신고해요."),
                         ("국내 상장주식", "대주주가 아니면 팔아서 생긴 이익에 양도세가 없어요. 거래할 때 증권거래세만 붙어요."),
                         ("배당", "국내 배당은 15.4%, 미국 배당은 현지에서 15%를 떼고 들어와요."),
                         ("손실 상계", "같은 해 안에서만 해외주식 이익과 손실을 합쳐요. 그래서 연말 전에 손실을 확정하면 세금이 줄 수 있어요.")], id: \.0) { t, v in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(t).appFont(14, .bold)
                        Text(v).appFont(13).foregroundStyle(Theme.sub).lineSpacing(2)
                    }
                    .padding(.vertical, 2)
                }
            }
            note("이 규칙은 미션의 절세 화면과 배당·세금 화면에 같이 쓰여요. 계산 예시이며 세무 상담이 아닙니다.")
        }
        .onAppear { gain = String(Int(m.taxGain)) }
    }
}

// MARK: 사용 방법 (인터미션 뒤 한 번 보인 안내를 보관)

struct HowToView: View {
    var body: some View {
        SettingsPage(title: "사용 방법") {
            ForEach([("위젯", "위젯", "아침에 위젯 속 친구가 알려 주는 본전까지 남은 % 보기"),
                     ("앱", "오늘의 1분", "어제 숫자 하나와 질문 하나. 7일 연속마다 쉼터에 물건이 돌아와요."),
                     ("알림", "알림", "비중이 계획에서 5%p 넘게 벗어난 날만 울려요."),
                     ("미션", "1000칸", "본전을 1000칸으로 나눠 채워요. 100칸마다 선물이 있어요."),
                     ("미션", "주간 예보", "월요일에 앱이 금요일 평가액 범위를 적어 두고, 금요일 종가로 범위 안인지 도장을 찍어요."),
                     ("PC", "naeilo.com", "여러 종목 한 번에 넣기, 증권사 파일, 긴 표와 근거는 PC에서 봐요.")], id: \.1) { w, t, v in
                HStack(alignment: .top, spacing: 10) {
                    Text(w).appFont(11, .bold).foregroundStyle(Theme.ink).padding(.horizontal, 8).padding(.vertical, 3)
                        .background(Theme.mint, in: Capsule())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(t).appFont(15, .bold)
                        Text(v).appFont(14).foregroundStyle(Theme.sub).lineSpacing(2)
                    }
                    Spacer(minLength: 0)
                }
                .padding(14)
                .background(.white, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
            }
        }
    }
}

// MARK: 루트 바꾸기

struct RouteView: View {
    @Environment(AppModel.self) private var m
    @State private var pick = "minus"

    var body: some View {
        let cards = [("minus", "회복 루트", "마이너스예요", "본전까지 가는 길. 원인 진단, 계획 4안, 절세, 4주 인터미션."),
                     ("plus", "목표 루트 · 플러스", "플러스예요", "지금 평가액에서 목표 금액까지. 구성 비교, 비중 조정 세금, 3개월 인터미션."),
                     ("none", "목표 루트 · 시작 전", "아직 시작 전이에요", "매달 넣는 돈으로 첫 목표까지. 구성 고르기, 3개월 인터미션.")]
        SettingsPage(title: "루트 바꾸기") {
            if m.total >= m.cost {
                Text("본전을 넘었어요. 이제 목표 루트 · 플러스로 바꿀 수 있어요.").appFont(14, .semibold).foregroundStyle(Color(hex: 0x0B5E40))
                    .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(hex: 0xE3F4EC), in: RoundedRectangle(cornerRadius: 12))
            }
            ForEach(cards, id: \.0) { k, name, tag, sub in
                let on = pick == k
                Button { pick = k } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(name).appFont(17, .bold)
                            Spacer()
                            Text(k == "minus" ? "지금 루트" : tag).appFont(12, .bold).foregroundStyle(k == "minus" ? Theme.teal : Theme.sub)
                        }
                        Text(sub).appFont(14).foregroundStyle(Theme.sub).multilineTextAlignment(.leading)
                    }
                    .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                    .background(on ? Theme.mintBg : .white, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(on ? Theme.teal : Theme.border, lineWidth: 2))
                }
                .buttonStyle(.plain)
            }
            PrimaryButton(title: pick == "minus" ? "지금 루트예요" : "목표 루트는 다음 빌드에서 열려요", color: Theme.muted) { }
                .disabled(true)
            note("루트를 바꿔도 지금까지 한 미션, 받은 위젯과 캐릭터는 그대로 남아요. 언제든 다시 돌아올 수 있어요.")
        }
    }
}

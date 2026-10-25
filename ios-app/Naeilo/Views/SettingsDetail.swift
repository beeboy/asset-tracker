import SwiftUI
import UniformTypeIdentifiers
import WidgetKit

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
        .toolbarTitleDisplayMode(.inlineLarge)
    }
}

/// 홈 인물 + 말풍선 (루트 바꾸기·개발자 후원 위). 대사는 Lines 의 "<key>.<인물>", 시세가 갱신될 때마다 바뀐다
struct FriendSay: View {
    @Environment(AppModel.self) private var m
    let key: String
    let fallback: String
    var body: some View {
        let who = m.homeFriendShown, name = m.friendName(who)
        HStack(alignment: .bottom, spacing: 10) {
            CharPixel(id: who, width: 42, height: 60).charIdle().accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(name).appFont(12, .semibold).foregroundStyle(Theme.blue)
                Text(Lines.pick("\(key).\(m.voice(who))") ?? fallback).appFont(14).lineSpacing(2).fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.mintBg, in: UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 4, bottomTrailingRadius: 14, topTrailingRadius: 14))
        }
        .accessibilityElement(children: .combine)
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
                .background(sel == n ? Theme.teal : Theme.card, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(sel == n ? Theme.teal : Theme.border, lineWidth: 2))
        }
    }
}

// MARK: 알림 — 위에 미리보기 고정

struct AlertsView: View {
    @Environment(AppModel.self) private var m
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var phase
    @State private var perm: Notifier.Status = .allowed
    @State private var sent = false

    var body: some View {
        let list: [(k: String, t: String, sub: String, prev: String)] = [
            ("be", String(localized: "본전 도달"), String(localized: "평가액이 들어간 돈을 넘으면 한 번"), String(localized: "본전에 도착했어요! 평가액이 들어간 돈 \(String(describing: AppModel.man(m.cost)))을 넘었어요.")),
            ("drift", String(localized: "비중 이탈"), String(localized: "비중 1위 종목 비중이 계획에서 \(String(describing: m.alertTh))%p 넘게 벗어나면"),
             String(localized: "\(String(describing: m.focusName)) 비중이 \(String(describing: AppModel.pct(m.focusWeight)))예요. 계획(\(String(describing: AppModel.pct(m.planWeight))))보다 \(String(describing: Int((abs(m.focusWeight - m.planWeight) * 100).rounded())))%p 벗어났어요.")),
            ("dep", String(localized: "연말 세금 정리"), String(localized: "12월 1일, 올해 손익과 세금 예시 보기"), String(localized: "올해가 한 달 남았어요. 올해 해외주식 손익과 내년 5월 세금 예시를 앱에서 확인해 보세요.")),
            ("morn", String(localized: "아침 한 줄"), String(localized: "평일 \(String(describing: m.alertHr))시, 어제의 움직임 한 줄"), Notifier.morningLine(m)),
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
                    Text(String(localized: "켠 알림 \(String(describing: on.count))개") + (perMonth > 0 ? String(localized: " · 한 달 약 \(String(describing: perMonth))번") : "")).appFont(12).foregroundStyle(Theme.sub)
                }
                HStack(alignment: .top, spacing: 10) {
                    // 설정에서 고른 앱 아이콘 (알림에도 같은 그림이 붙는다)
                    Image(AppIconPicker.icons.first { $0.id == UIApplication.shared.alternateIconName }?.prev ?? "iconprev_star")
                        .resizable().scaledToFit().frame(width: 36, height: 36)
                        .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text(prev?.t ?? String(localized: "알림 꺼짐")).appFont(14, .bold)
                            Spacer()
                            Text(prev == nil ? "" : prev!.k == "morn" ? String(localized: "오전 \(String(describing: m.alertHr)):00") : String(localized: "지금")).appFont(12).foregroundStyle(Theme.muted)
                        }
                        Text(prev?.prev ?? String(localized: "켠 알림이 없어요. 아래에서 하나 이상 켜 보세요.")).appFont(13).fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding(12)
                .background(Color(hex: 0xEEF1F5, dark: 0x222C36), in: RoundedRectangle(cornerRadius: 16))
            }
            .padding(16).background(Theme.card).overlay(alignment: .bottom) { Divider() }
        } content: {
            VStack(alignment: .leading, spacing: 12) {
                if perm != .allowed { permCard }
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
                        if isOn && a.k == "drift" { optionChips(String(localized: "기준"), [3, 5, 10], "%p", m.alertTh) { m.alertTh = $0 } }
                        if isOn && a.k == "morn" { optionChips(String(localized: "시간"), [7, 8, 9], String(localized: "시"), m.alertHr) { m.alertHr = $0 } }
                    }
                }
                if perm == .allowed && !on.isEmpty {
                    Button { Task { await Notifier.test(m); sent = true } } label: {
                        Text(sent ? String(localized: "5초 뒤에 시험 알림이 와요") : String(localized: "시험 알림 받아 보기")).appFont(14, .semibold)
                            .frame(maxWidth: .infinity, minHeight: 44).foregroundStyle(Theme.teal)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 1.5))
                    }.buttonStyle(.plain)
                }
                note(String(localized: "알림은 하루 한 번까지 와요. 아침 한 줄은 앱을 마지막으로 연 때의 숫자예요."))
                Card(padding: 14) { RemindOptions() }
            }
            .padding(16)
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
        .task(id: phase) { perm = await Notifier.status() }
        // 알림을 켜는 순간 아이폰 허용을 묻는다
        .onChange(of: m.alerts) { _, v in
            if v.values.contains(true) && perm == .unknown { Task { await Notifier.request(); perm = await Notifier.status(); await Notifier.reschedule(m) } }
        }
    }

    private var permCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(perm == .denied ? String(localized: "아이폰 알림이 꺼져 있어요") : String(localized: "아이폰 알림 허용이 필요해요")).appFont(15, .bold)
            Text(perm == .denied ? String(localized: "아이폰 설정 > 알림 > naeilo에서 허용해 주세요.") : String(localized: "허용해야 알림이 와요."))
                .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            PrimaryButton(title: perm == .denied ? String(localized: "아이폰 설정 열기") : String(localized: "알림 허용하기")) {
                if perm == .denied { if let u = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(u) } }
                else { Task { await Notifier.request(); perm = await Notifier.status(); await Notifier.reschedule(m) } }
            }
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.cream, in: RoundedRectangle(cornerRadius: 16))
    }
}

// MARK: 기기 옮기기 — 기본은 파일 하나로, 기기 동기화는 켠 사람만 (같은 비밀번호의 폰끼리)

struct SyncView: View {
    @Environment(AppModel.self) private var m
    @State private var pw = ""
    @State private var pw2 = ""
    @State private var askOff = false
    @State private var devOpen = false
    @State private var token = ""
    @State private var exporting = false
    @State private var importing = false
    @State private var exportDoc: BackupFile? = nil
    @State private var pending: Backup? = nil
    @State private var fileMsg: String? = nil
    /// 토스 시세 상태 (주소를 넣은 개발자 기기만): 마지막 성공 시각, 마지막 실패 이유
    private var tossStatus: String {
        _ = Market.shared.updatedAt     // 시세를 새로 받으면 다시 그린다
        let t = TossFeed.okAt
        var s = String(localized: "오픈 API: ")
        guard TossFeed.base != nil else { return s + String(localized: "주소 없음") }
        if t > 0 {
            let f = DateFormatter(); f.locale = AppModel.dateLocale
            let d = Date(timeIntervalSince1970: t)
            f.dateFormat = Calendar.current.isDateInToday(d) ? "a h:mm:ss" : String(localized: "M월 d일 a h:mm")
            s += String(localized: "마지막 성공 \(String(describing: f.string(from: d)))")
        } else { s += String(localized: "아직 성공 없음") }
        if let e = TossFeed.lastError { s += String(localized: " · 실패: \(String(describing: e))") }
        return s
    }
    private var sync: Sync { .shared }

    var body: some View {
        let on = sync.isOn
        SettingsPage(title: String(localized: "기기 옮기기")) {
            // 기본은 파일 하나로 옮기기. 동기화는 켠 사람만 쓰고, 올리기는 앱을 닫을 때 한 번 (App.swift)
            fileCard
            if let c = sync.conflict { conflictCard(c) }
            Card {
                HStack {
                    Text("기기 동기화").appFont(15, .bold)
                    Spacer()
                    Text(on ? String(localized: "켜짐") : String(localized: "꺼짐")).appFont(13, .semibold).foregroundStyle(on ? Theme.teal : Theme.muted)
                }
                Text(statusText).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                if on {
                    PrimaryButton(title: sync.state == .working ? String(localized: "맞추는 중…") : String(localized: "지금 맞추기")) { Task { await sync.pull(m) } }
                    Button("동기화 끄기") { askOff = true }
                        .appFont(15, .semibold).foregroundStyle(Theme.up).frame(maxWidth: .infinity, minHeight: 48)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
                        .confirmationDialog("이 폰 동기화를 끌까요?", isPresented: $askOff, titleVisibility: .visible) {
                            Button("끄기", role: .destructive) { sync.turnOff() }
                        } message: { Text(sync.isDev ? String(localized: "이 폰 값은 그대로 · 토큰은 지워요") : String(localized: "이 폰 값은 그대로 · 다시 켜려면 같은 비밀번호")) }
                } else {
                    let ok = pw.count >= 10 && pw == pw2
                    SecureField("비밀번호 (10자 이상)", text: $pw).textContentType(.newPassword)
                        .padding(.horizontal, 12).frame(minHeight: 48).overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
                    SecureField("한 번 더", text: $pw2).textContentType(.newPassword)
                        .padding(.horizontal, 12).frame(minHeight: 48).overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
                    if !pw.isEmpty && pw.count < 10 { Text("10자 이상").appFont(12).foregroundStyle(Theme.up) }
                    else if !pw2.isEmpty && pw != pw2 { Text("비밀번호가 달라요").appFont(12).foregroundStyle(Theme.up) }
                    PrimaryButton(title: sync.state == .working ? String(localized: "준비 중…") : String(localized: "켜기"), color: ok ? Theme.teal : Theme.muted) {
                        guard ok, sync.state != .working else { return }
                        let p = pw; pw = ""; pw2 = ""
                        Task { await sync.turnOn(password: p, model: m) }
                    }
                }
            }
            if !on {
            DisclosureGroup(isExpanded: $devOpen) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("GitHub 토큰을 넣은 기기끼리 비밀번호 대신 토큰으로 맞춰요.")
                        .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                    SecureField("GitHub 토큰", text: $token).textContentType(.password).autocorrectionDisabled().textInputAutocapitalization(.never)
                        .padding(.horizontal, 12).frame(minHeight: 48).overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
                    PrimaryButton(title: sync.state == .working ? String(localized: "확인 중…") : String(localized: "토큰으로 연결"), color: token.isEmpty ? Theme.muted : Theme.teal) {
                        guard !token.isEmpty, sync.state != .working else { return }
                        let t = token; token = ""
                        Task { await sync.turnOnDev(token: t, model: m) }
                    }
                    Text("토큰은 이 폰 키체인에만 둬요. 개발자 칸은 암호화하지 않아요.")
                        .appFont(12).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
                }
                .padding(.top, 8)
            } label: { Text("개발자용").appFont(14, .semibold).foregroundStyle(Theme.sub) }
            .tint(Theme.sub)
            }
            // 토스 상태는 동기화 켜짐·꺼짐과 상관없이 늘 보인다
            Text(tossStatus).appFont(12).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            note(String(localized: "서버엔 암호화한 값만 · 비밀번호는 저장 안 함"))
        }
    }

    /// 파일 하나로 옮기기 (동기화 없이): 저장하기·불러오기
    private var fileCard: some View {
        Card {
            Text("파일로 옮기기").appFont(15, .bold)
            Text("종목·미션 진행을 파일 하나로 · 새 폰에서 불러오기")
                .appFont(14).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
            PrimaryButton(title: String(localized: "파일로 저장")) { exportDoc = BackupFile(Backup(m)); exporting = true }
            Button("파일 불러오기") { importing = true }
                .appFont(15, .semibold).foregroundStyle(Theme.teal).frame(maxWidth: .infinity, minHeight: 48)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 2))
            if let fileMsg { Text(fileMsg).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true) }
            Text("암호화 안 됨 · 남에게 보내지 마세요")
                .appFont(12).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
        }
        .fileExporter(isPresented: $exporting, document: exportDoc, contentType: .json, defaultFilename: Backup.fileName()) { r in
            switch r {
            case .success: fileMsg = String(localized: "저장함 · 종목 \(String(describing: m.holdings.count))개")
            case .failure(let e): fileMsg = String(localized: "저장 실패: \(String(describing: e.localizedDescription))")
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { r in
            switch r {
            case .success(let url):
                do { pending = try BackupFile.read(url) } catch { fileMsg = String(localized: "naeilo 파일이 아니에요") }
            case .failure(let e): fileMsg = String(localized: "열기 실패: \(String(describing: e.localizedDescription))")
            }
        }
        .confirmationDialog("이 파일로 바꿀까요?", isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } }), titleVisibility: .visible) {
            Button("불러오기") {
                guard let b = pending else { return }
                b.restore(to: m)
                fileMsg = String(localized: "불러옴 · 종목 \(String(describing: b.holdings.count))개")
                pending = nil
            }
        } message: {
            let f = DateFormatter(), b = pending
            let _ = { f.locale = AppModel.dateLocale; f.dateFormat = String(localized: "M월 d일 a h:mm") }()
            Text(String(localized: "\(String(describing: b.map { f.string(from: $0.at) } ?? "")) 저장 · 종목 \(String(describing: b?.holdings.count ?? 0))개 · 이 폰 종목·미션 진행이 바뀌어요") + (sync.isOn ? String(localized: " · 동기화로 다른 폰에도") : ""))
        }
    }

    private var statusText: String {
        switch sync.state {
        case .off: return String(localized: "같은 비밀번호 폰끼리 · 앱 닫을 때 올림")
        case .idle: return sync.isDev ? String(localized: "같은 GitHub 계정 기기끼리") : String(localized: "같은 비밀번호 폰끼리 · 앱 닫을 때 올림")
        case .working: return String(localized: "맞추는 중…")
        case .ok(let d):
            let f = DateFormatter(); f.locale = AppModel.dateLocale; f.dateFormat = "a h:mm"
            return String(localized: "마지막: \(String(describing: f.string(from: d)))")
        case .failed(let e): return String(localized: "실패: \(String(describing: e))")
        }
    }

    private func conflictCard(_ c: Sync.Remote) -> some View {
        let f = DateFormatter(); f.locale = AppModel.dateLocale; f.dateFormat = String(localized: "M월 d일 a h:mm")
        let n = (c.state["holdings"]?.value as? [[String: Any]])?.count ?? 0
        return Card {
            Text("다른 폰 값이 있어요").appFont(15, .bold)
            Text("\(f.string(from: Date(timeIntervalSince1970: c.at / 1000))) 저장 · 종목 \(n)개 · 어느 쪽으로?")
                .appFont(14).fixedSize(horizontal: false, vertical: true)
            PrimaryButton(title: String(localized: "다른 폰 값으로")) { Task { await sync.resolve(useRemote: true, m) } }
            Button("이 폰 값 보내기") { Task { await sync.resolve(useRemote: false, m) } }
                .appFont(15, .semibold).foregroundStyle(Theme.teal).frame(maxWidth: .infinity, minHeight: 48)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 2))
        }
    }
}

// MARK: 위젯 — 받은 위젯과 인물 위젯 미리보기 (미션 진행대로 열림)
// 홈 화면에 놓는 일은 아이폰이 하므로 여기서는 넣고 빼지 않는다. 무엇이 열렸는지, 어떻게 보이는지, 언제 열리는지만 보여 준다.

extension AppModel {
    struct WidgetInfo: Identifiable { let id: String; let name: String; let desc: String; let size: String; let ok: Bool; let how: String }
    /// 기본 위젯 (위젯 추가 화면과 같은 이름·순서, Catalog). 구독 중이거나 7일 무료 동안 모두 열린다
    var baseWidgets: [WidgetInfo] {
        Catalog.base.map { WidgetInfo(id: $0.kind, name: $0.name, desc: $0.desc, size: $0.size, ok: Store.isUnlocked($0.kind), how: WidgetUnlock.how($0.kind)) }
    }
}

/// 설정 > 위젯 > 금액 가리기: 금액을 누르면 모든 위젯 함께 / 누른 위젯만
struct AmountHideCard: View {
    @State private var each = Store.hideEach
    @State private var lockPrivate = Store.lockPrivate

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("금액을 누르면").appFont(14, .semibold)
            Picker("금액을 누르면", selection: $each) {
                Text("모든 위젯 함께").tag(false)
                Text("누른 위젯만").tag(true)
            }
            .pickerStyle(.segmented)
            Toggle("잠긴 동안 잠금화면 금액 가리기", isOn: $lockPrivate).appFont(14).tint(Theme.teal).padding(.top, 4)
        }
        .padding(14)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
        .onChange(of: each) { _, v in
            // 방식을 바꿔도 지금 가려진 상태는 그대로 둔다
            let kinds = Catalog.base.map(\.kind).sorted()
            if v { Store.hiddenKinds = Store.hideAll ? kinds : [] } else { Store.hideAll = Set(kinds).isSubset(of: Store.hiddenKinds) }
            Store.hideEach = v
            WidgetCenter.shared.reloadAllTimelines()
        }
        .onChange(of: lockPrivate) { _, v in
            Store.lockPrivate = v
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
}

struct WidgetPickView: View {
    @Environment(AppModel.self) private var m
    var cover = false   // 홈 [위젯] 전체 화면 팝업: 제목 줄에 X
    @State private var dark = UserDefaults.standard.bool(forKey: "cpDark")
    @State private var who: String? = UserDefaults.standard.string(forKey: "cpWho")
    @State private var baseOpen = false   // 기본 위젯 목록: 처음엔 접어 둔다

    private var reward: WReward { Store.read(WReward.self, "reward.json") ?? .sample }

    var body: some View {
        let base = m.baseWidgets, friendsOn = Shelter.friends.indices.filter { m.friendOn($0) }.count
        let sel = who ?? m.homeFriendShown
        let fi = Shelter.friends.firstIndex { $0.id == sel } ?? 0, on = sel == "me" ? m.meUnlocked : m.friendOn(fi)
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if cover { GameHeader(title: String(localized: "홈 화면 위젯")).toolbar(.hidden, for: .navigationBar) } else { Text("위젯").appFont(22, .bold) }
                // 홈 화면에 위젯 넣는 단계 (단계마다 시스템 아이콘 하나)
                VStack(alignment: .leading, spacing: 8) {
                    addStep(1, "hand.tap", String(localized: "홈 화면 빈 곳 길게 누르기"))
                    addStep(2, "plus.square", String(localized: "왼쪽 위 \"편집\" → \"위젯 추가\""))
                    addStep(3, "magnifyingglass", String(localized: "\"naeilo\" 검색"))
                    addStep(4, "square.grid.2x2", String(localized: "크기 고르고 \"위젯 추가\""))
                    addStep(5, "person.crop.square", String(localized: "인물 바꾸기: 위젯 길게 눌러 \"위젯 편집\""))
                }
                .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
                HStack(spacing: 8) {
                    stat(String(localized: "기본 위젯"), "\(base.filter(\.ok).count)/\(base.count)", note: String(localized: "미션을 마치면 열려요"))
                    stat(String(localized: "인물"), "\(friendsOn)/\(Shelter.friends.count)")
                }
                .fixedSize(horizontal: false, vertical: true)   // 두 칸 높이를 같게

                // 인물 위젯: 만난 인물을 고르면 여덟 가지 모양을 바로 본다 (못 만난 친구는 목록에 없다)
                Text("인물 위젯").appFont(17, .bold).padding(.top, 6)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(Shelter.friends.enumerated()).filter { m.friendOn($0.offset) }, id: \.element.id) { i, f in
                            let ok = m.friendOn(i), cur = f.id == sel
                            Button { who = f.id } label: {
                                VStack(spacing: 2) {
                                    Pixel(name: (ok ? "spr_" : "sil_") + f.id, width: 30, height: 42)
                                    Text(ok ? f.name : "???").appFont(12, .bold)
                                    Text(ok ? (f.id == m.homeFriendShown ? String(localized: "홈에 있음") : String(localized: "만남")) : String(localized: "인터미션 \(String(describing: i))주차")).appFont(10).foregroundStyle(Theme.sub)
                                }
                                .frame(width: 72, height: 86)
                                .background(cur ? Theme.mintBg : Theme.card, in: RoundedRectangle(cornerRadius: 12))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(cur ? Theme.teal : Theme.border, lineWidth: 2))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(ok ? f.name : String(localized: "잠긴 인물, 인터미션 \(String(describing: i))주차"))
                        }
                        // 내 캐릭터: 열렸을 때만
                        if m.meUnlocked {
                            Button { who = "me" } label: {
                                VStack(spacing: 2) {
                                    CharPixel(id: "me", width: 30, height: 42)
                                    Text(m.friendName("me")).appFont(12, .bold).lineLimit(1)
                                    Text(m.homeFriendShown == "me" ? String(localized: "홈에 있음") : String(localized: "내 캐릭터")).appFont(10).foregroundStyle(Theme.sub)
                                }
                                .frame(width: 72, height: 86)
                                .background(sel == "me" ? Theme.mintBg : Theme.card, in: RoundedRectangle(cornerRadius: 12))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(sel == "me" ? Theme.teal : Theme.border, lineWidth: 2))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                Picker("", selection: $dark) { Text("기본").tag(false); Text("다크").tag(true) }.pickerStyle(.segmented)
                let c = WChar.of(on ? sel : "seri"), e = CharEntry(date: Date(), r: reward, c: c, locked: false)
                GeometryReader { g in
                    let w = (g.size.width - 12) / 2
                    VStack(spacing: 12) {
                        HStack(spacing: 12) {
                            caption(String(localized: "자산 언덕")) { tile(e, w: w, h: w) { HillCharView(e: e) } }
                            caption(String(localized: "본전 사다리")) { tile(e, w: w, h: w) { LadderCharView(e: e) } }
                        }
                        HStack(spacing: 12) {
                            caption(String(localized: "블록 탑")) { tile(e, w: w, h: w) { TowerCharView(e: e) } }
                            caption(String(localized: "전광판")) { tile(e, w: w, h: w) { BoardCharView(e: e) } }
                        }
                        HStack(spacing: 12) {
                            caption(String(localized: "키 재기")) { tile(e, w: w, h: w) { HeightCharView(e: e) } }
                            caption(String(localized: "퍼즐 맞추기")) { tile(e, w: w, h: w) { PuzzleCharView(e: e) } }
                        }
                        HStack(spacing: 12) {
                            caption(String(localized: "꽃밭")) { tile(e, w: w, h: w) { GardenCharView(e: e) } }
                            caption(String(localized: "줄다리기")) { tile(e, w: w, h: w) { TugCharView(e: e) } }
                        }
                    }
                }
                .aspectRatio(1 / 2.3, contentMode: .fit)

                // 기본 위젯: 앱 시작 단계로 열림. 처음엔 접어 두고 제목 줄을 눌러 편다
                Button { withAnimation(.easeInOut(duration: 0.2)) { baseOpen.toggle() } } label: {
                    HStack(spacing: 6) {
                        Text("기본 위젯").appFont(17, .bold)
                        Text("\(base.filter(\.ok).count)/\(base.count)").appFont(13).foregroundStyle(Theme.sub)
                        Spacer()
                        Image(systemName: "chevron.down").font(.system(size: 13, weight: .bold)).foregroundStyle(Theme.sub)
                            .rotationEffect(.degrees(baseOpen ? 180 : 0))
                    }
                    .frame(minHeight: 44).contentShape(Rectangle())
                }
                .buttonStyle(.plain).padding(.top, 6)
                if baseOpen {
                VStack(spacing: 0) {
                    ForEach(Array(base.enumerated()), id: \.element.id) { i, w in
                        if i > 0 { Divider().overlay(Theme.line) }
                        HStack(alignment: .center, spacing: 12) {
                            Image(systemName: w.ok ? "checkmark.circle.fill" : "lock.fill")
                                .font(.system(size: 18)).foregroundStyle(w.ok ? Theme.teal : Theme.muted).frame(width: 24)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(w.name).appFont(16, .semibold).foregroundStyle(w.ok ? Theme.ink : Theme.muted)
                                Text(w.size + " · " + (w.ok ? w.desc : w.how)).appFont(12).foregroundStyle(Theme.sub)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 14).padding(.vertical, 10).frame(minHeight: 56)
                        .accessibilityElement(children: .combine)
                    }
                }
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
                note(String(localized: "못 받은 위젯은 잠긴 모습으로 보여요."))
                }

                // 금액 가리기: 위젯에서 금액을 누르면 •••• 로 가려진다. 함께/따로는 Store.hideEach
                Text("금액 가리기").appFont(17, .bold).padding(.top, 6)
                AmountHideCard()
                note(String(localized: "금액을 누르면 가려지고, 다시 누르면 보여요. 잠금화면 위젯은 폰이 잠겨 있는 동안 금액을 가리고, 얼굴이나 지문으로 풀면 보여요."))
            }
            .screen()
        }
        .background(Theme.bg)
        .toolbarTitleDisplayMode(.inlineLarge)
    }

    private func stat(_ k: String, _ v: String, note: String? = nil) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(k).appFont(12).foregroundStyle(Theme.sub)
            Text(v + String(localized: " 열림")).appFont(17, .bold)
            if let note { Text(note).appFont(11).foregroundStyle(Theme.sub).lineLimit(1).minimumScaleFactor(0.8) }
        }
        .frame(maxHeight: .infinity, alignment: .topLeading)
        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
    }

    private func addStep(_ n: Int, _ icon: String, _ t: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon).font(.system(size: 16)).foregroundStyle(Theme.teal).frame(width: 24)
            Text("\(n). " + t).appFont(14).fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
    }

    private func caption<V: View>(_ t: String, @ViewBuilder _ v: () -> V) -> some View {
        VStack(alignment: .leading, spacing: 4) { v(); Text(t).appFont(12).foregroundStyle(Theme.sub) }
    }

    /// 큰 위젯 크기 그대로 (338×354 를 폭에 맞춰 줄인다)
    private func largeTile<V: View>(_ e: CharEntry, w: CGFloat, @ViewBuilder _ v: () -> V) -> some View {
        let k = min(1, w / 338)
        return ZStack {
            CharBG(c: e.c, locked: e.locked, dark: dark)
            v().padding(16).foregroundStyle(.white).environment(\.colorScheme, .dark).environment(\.charDark, dark)
                .frame(width: 338, height: 354).scaleEffect(k)
        }
        .frame(width: w, height: 354 * k)
        .clipShape(RoundedRectangle(cornerRadius: 22 * k, style: .continuous))
    }

    /// 위젯 크기 그대로 (바탕 + 위젯 안쪽 여백)
    private func tile<V: View>(_ e: CharEntry, w: CGFloat, h: CGFloat, @ViewBuilder _ v: () -> V) -> some View {
        let k = min(1, h / 158)
        return ZStack {
            CharBG(c: e.c, locked: e.locked, dark: dark)
            v().padding(16).foregroundStyle(.white).environment(\.colorScheme, .dark).environment(\.charDark, dark)
                .frame(width: w / k, height: 158).scaleEffect(k)
        }
        .frame(width: w, height: h)
        .clipShape(RoundedRectangle(cornerRadius: 22 * k, style: .continuous))
    }
}

// MARK: 세금 규칙

struct TaxRulesView: View {
    @Environment(AppModel.self) private var m
    @State private var gain = ""

    var body: some View {
        let G = m.taxGain, tax = max(0, G - 250) * 0.22
        let won0 = { (x: Double) in Int(x.rounded()).formatted() + String(localized: "만원") }
        SettingsPage(title: String(localized: "세금 규칙")) {
            // 세금 규칙은 아직 대한민국 거주자만: 다른 나라에 살면 준비 중이라고 알리고 한국 기준을 보여 준다
            NavigationLink(value: SettingsRoute.residence) {
                HStack(spacing: 6) {
                    Text("\(m.residenceName) 거주자").appFont(14, .bold)
                    Image(systemName: "chevron.right").appFont(12)
                }
                .padding(.horizontal, 14).frame(minHeight: 40)
                .foregroundStyle(.white).background(Theme.teal, in: Capsule())
            }.buttonStyle(.plain)
            if m.residence != "KR" {
                Text("\(m.residenceName) 세금 규칙은 준비 중이에요. 아래는 대한민국 기준이에요.").appFont(13).foregroundStyle(Theme.sub)
            }
            VStack(alignment: .leading, spacing: 6) {
                Text("내년 5월에 낼 해외주식 세금 (예상)").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                Text(won0(tax)).appFont(30, .bold).roll(won0(tax))
                Text(G > 250 ? String(localized: "(\(String(describing: won0(G))) − 기본공제 250만원) × 22%") : String(localized: "이익이 250만원 이하라 낼 세금이 없어요")).appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
            }
            .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.night, in: RoundedRectangle(cornerRadius: 20))
            VStack(alignment: .leading, spacing: 4) {
                Text("올해 해외주식 실현 이익 (만원)").appFont(13, .semibold).foregroundStyle(Theme.sub)
                TextField("", text: $gain).keyboardType(.numberPad).appFont(18, .semibold)
                    .padding(.horizontal, 12).frame(minHeight: 48)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border, lineWidth: 2))
                    .onChange(of: gain) { _, v in m.taxGain = max(0, Double(v) ?? 0) }
                    .accessibilityLabel("올해 해외주식 실현 이익 (만원)")
            }
            Card {
                ForEach([(String(localized: "해외주식 양도소득세"), String(localized: "한 해 손익 합계에서 250만원 넘는 부분에 22%. 다음 해 5월 신고.")),
                         (String(localized: "국내 상장주식"), String(localized: "대주주가 아니면 양도세 없음. 증권거래세만.")),
                         (String(localized: "배당"), String(localized: "국내 15.4%, 미국 15% (현지 원천징수).")),
                         (String(localized: "손실 상계"), String(localized: "같은 해 이익과 손실을 합쳐서 계산해요."))], id: \.0) { t, v in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(t).appFont(14, .bold)
                        Text(v).appFont(13).foregroundStyle(Theme.sub).lineSpacing(2)
                    }
                    .padding(.vertical, 2)
                }
            }
            note(String(localized: "계산 예시이며 세무 상담이 아닙니다."))
        }
        .doneKey()
        .onAppear { gain = String(Int(m.taxGain)) }
    }
}

// MARK: 사용 방법 (인터미션 뒤 한 번 보인 안내를 보관)

struct HowToView: View {
    var body: some View {
        SettingsPage(title: String(localized: "사용 방법")) {
            ForEach([(String(localized: "위젯"), String(localized: "위젯"), String(localized: "아침에 위젯 속 친구가 알려 주는 본전까지 남은 % 보기")),
                     (String(localized: "홈"), String(localized: "리마인드"), String(localized: "달성·새 소식은 친구 말풍선으로, 3일 안의 일정은 노란 띠로 떠요. 지난 소식은 ‘새 소식’에 30일 남아요.")),
                     (String(localized: "알림"), String(localized: "알림"), String(localized: "비중이 계획에서 5%p 넘게 벗어난 날만 울려요.")),
                     (String(localized: "미션"), String(localized: "1000칸"), String(localized: "본전을 1000칸으로 나눠 채워요.")),
                     (String(localized: "미션"), String(localized: "주간 예보"), String(localized: "월요일에 금요일 범위를 적고, 금요일 종가로 도장을 찍어요."))], id: \.1) { w, t, v in
                HStack(alignment: .top, spacing: 10) {
                    Text(w).appFont(11, .bold).foregroundStyle(Theme.inkFixed).padding(.horizontal, 8).padding(.vertical, 3)
                        .background(Theme.mint, in: Capsule())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(t).appFont(15, .bold)
                        Text(v).appFont(14).foregroundStyle(Theme.sub).lineSpacing(2)
                    }
                    Spacer(minLength: 0)
                }
                .padding(14)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
            }
        }
    }
}

// MARK: 루트 바꾸기

struct RouteView: View {
    @Environment(AppModel.self) private var m
    @State private var pick = ""

    var body: some View {
        let cards = [("minus", String(localized: "회복 루트"), String(localized: "마이너스"), String(localized: "본전까지 · 원인 진단 · 계획 4안 · 절세 · 4주 인터미션")),
                     ("plus", String(localized: "목표 루트 · 플러스"), String(localized: "플러스"), String(localized: "지금 → 목표 금액 · 구성 비교 · 비중 세금 · 3개월 인터미션")),
                     ("none", String(localized: "목표 루트 · 시작 전"), String(localized: "시작 전"), String(localized: "매달 적립으로 첫 목표 · 구성 고르기 · 3개월 인터미션"))]
        let cur = m.route == .recover ? "minus" : m.route == .plus ? "plus" : "none"
        let sel = pick.isEmpty ? cur : pick
        SettingsPage(title: String(localized: "루트 바꾸기")) {
            FriendSay(key: "route", fallback: String(localized: "길을 고르면 아래 그래프가 바뀌어요."))
            if m.route == .recover && m.total >= m.cost {
                Text("본전 넘음 · 목표 루트 · 플러스로 바꿀 수 있어요").appFont(14, .semibold).foregroundStyle(Color(hex: 0x0B5E40, dark: 0x7FD8B0))
                    .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(hex: 0xE3F4EC, dark: 0x163226), in: RoundedRectangle(cornerRadius: 12))
            }
            ForEach(cards, id: \.0) { k, name, tag, sub in
                let on = sel == k
                Button { pick = k } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(name).appFont(17, .bold)
                            Spacer()
                            Text(k == cur ? String(localized: "지금 루트") : tag).appFont(12, .bold).foregroundStyle(k == cur ? Theme.teal : Theme.sub)
                        }
                        Text(sub).appFont(14).foregroundStyle(Theme.sub).multilineTextAlignment(.leading)
                    }
                    .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                    .background(on ? Theme.mintBg : Theme.card, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(on ? Theme.teal : Theme.border, lineWidth: 2))
                }
                .buttonStyle(.plain)
            }
            PrimaryButton(title: sel == cur ? String(localized: "지금 루트") : String(localized: "이 루트로"), color: sel == cur ? Theme.muted : Theme.teal) {
                guard sel != cur else { return }
                m.switchRoute(sel == "minus" ? .recover : sel == "plus" ? .plus : .novice)
                pick = ""; m.settingsPath = []
            }
            note(String(localized: "바꿔도 받은 것은 그대로"))
            // 첫 질문 화면처럼: 고른 루트에 따라 바뀌는 예시 3년 그래프
            StartForecastCard(route: sel == "minus" ? Route.recover : sel == "plus" ? Route.plus : Route.novice)
        }
    }
}

/// 거주 국가 고르기: 세금 기능만 이 값을 따른다 (증권사 연결은 나라와 상관없이 누구나)
struct ResidenceView: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    @State private var q = ""
    private static let all: [(code: String, name: String)] = Locale.Region.isoRegions
        .filter { $0.subRegions.isEmpty }
        .map { ($0.identifier, Locale.current.localizedString(forRegionCode: $0.identifier) ?? $0.identifier) }
        .sorted { $0.name.localizedCompare($1.name) == .orderedAscending }

    var body: some View {
        // 검색 전엔 지금 고른 나라를 맨 위에
        let rows = q.isEmpty ? Self.all.filter { $0.code == m.residence } + Self.all.filter { $0.code != m.residence } : Self.all.filter { $0.name.localizedCaseInsensitiveContains(q) || $0.code.localizedCaseInsensitiveContains(q) }
        List(rows, id: \.code) { r in
            Button {
                m.residence = r.code; dismiss()
            } label: {
                HStack {
                    Text(r.name).foregroundStyle(Theme.ink)
                    Spacer()
                    if r.code == m.residence { Image(systemName: "checkmark").foregroundStyle(Theme.teal) }
                }
            }
        }
        .searchable(text: $q, placement: .navigationBarDrawer(displayMode: .always))
        .navigationTitle("거주 국가")
    }
}

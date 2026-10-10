import SwiftUI
import UniformTypeIdentifiers

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
            ("be", "본전 도달", "평가액이 들어간 돈을 넘으면 한 번", "본전에 도착했어요! 평가액이 들어간 돈 \(AppModel.man(m.cost))을 넘었어요."),
            ("drift", "비중 이탈", "DRNK 비중이 계획에서 \(m.alertTh)%p 넘게 벗어나면",
             "DRNK 비중이 \(AppModel.pct(m.drnkWeight))예요. 계획(\(AppModel.pct(m.planWeight)))보다 \(Int((abs(m.drnkWeight - m.planWeight) * 100).rounded()))%p 벗어났어요."),
            ("dep", "연말 절세 확인", "12월 1일, 올해 손실을 확정할지 볼 때", "올해가 한 달 남았어요. 손실 난 종목 일부를 팔면 내년 세금이 줄 수 있어요."),
            ("morn", "아침 한 줄", "평일 \(m.alertHr)시, 어제의 움직임 한 줄", Notifier.morningLine(m)),
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
                    // 설정에서 고른 앱 아이콘 (알림에도 같은 그림이 붙는다)
                    Image(AppIconPicker.icons.first { $0.id == UIApplication.shared.alternateIconName }?.prev ?? "iconprev_star")
                        .resizable().scaledToFit().frame(width: 36, height: 36)
                        .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
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
                        if isOn && a.k == "drift" { optionChips("기준", [3, 5, 10], "%p", m.alertTh) { m.alertTh = $0 } }
                        if isOn && a.k == "morn" { optionChips("시간", [7, 8, 9], "시", m.alertHr) { m.alertHr = $0 } }
                    }
                }
                if perm == .allowed && !on.isEmpty {
                    Button { Task { await Notifier.test(m); sent = true } } label: {
                        Text(sent ? "5초 뒤에 시험 알림이 와요" : "시험 알림 받아 보기").appFont(14, .semibold)
                            .frame(maxWidth: .infinity, minHeight: 44).foregroundStyle(Theme.teal)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 1.5))
                    }.buttonStyle(.plain)
                }
                note("본전 도달과 비중 이탈은 앱이 시세를 받을 때 확인해서 보내요. 같은 날 겹치면 하나로 묶어 하루 한 번만 보내요. 아침 한 줄은 앱을 마지막으로 연 때의 숫자로 다음 평일 아침에 오고, 앱을 한동안 안 열면 숫자 없이 와요. 알림 설정은 기기마다 따로예요.")
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: phase) { perm = await Notifier.status() }
        // 알림을 켜는 순간 아이폰 허용을 묻는다
        .onChange(of: m.alerts) { _, v in
            if v.values.contains(true) && perm == .unknown { Task { await Notifier.request(); perm = await Notifier.status(); await Notifier.reschedule(m) } }
        }
    }

    private var permCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(perm == .denied ? "아이폰 알림이 꺼져 있어요" : "아이폰 알림 허용이 필요해요").appFont(15, .bold)
            Text(perm == .denied ? "아래 알림을 켜도 오지 않아요. 아이폰 설정 > 알림 > naeilo에서 허용해 주세요." : "허용해야 아래 켠 알림이 실제로 와요.")
                .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            PrimaryButton(title: perm == .denied ? "아이폰 설정 열기" : "알림 허용하기") {
                if perm == .denied { if let u = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(u) } }
                else { Task { await Notifier.request(); perm = await Notifier.status(); await Notifier.reschedule(m) } }
            }
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.cream, in: RoundedRectangle(cornerRadius: 16))
    }
}

// MARK: 기기 동기화 — PC naeilo.com 6자리 코드

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
    private var sync: Sync { .shared }

    var body: some View {
        let on = sync.isOn
        SettingsPage(title: "기기 동기화") {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 10) {
                    Image(systemName: "iphone").font(.system(size: 26))
                    Rectangle().fill(on ? Theme.yellow : Theme.muted).frame(width: 60, height: 3)
                        .mask { if on { Rectangle() } else { HStack(spacing: 4) { ForEach(0..<8, id: \.self) { _ in Rectangle() } } } }
                    Image(systemName: "desktopcomputer").font(.system(size: 26))
                }
                Text(on ? (sync.isDev ? "개발자 동기화 켜짐" : "자동 동기화 켜짐") : "아직 켜지 않음").appFont(20, .bold)
                Text(statusText).appFont(13).fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(on ? .white : Theme.ink)
            .padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(on ? Theme.teal : Theme.track, in: RoundedRectangle(cornerRadius: 20))

            if let c = sync.conflict { conflictCard(c) }

            if on {
                Card {
                    Text("함께 맞춰지는 것").appFont(15, .bold)
                    ForEach([("종목·수량·평균 단가", true), ("미션 진행·오늘의 1분·쉼터", true), ("목표·사건·모형 설정 (사이트)", true), ("알림·화면 모드", false)], id: \.0) { k, y in
                        HStack { Text(k).appFont(14); Spacer(); Text(y ? "함께" : "기기마다 따로").appFont(13, .semibold).foregroundStyle(y ? Theme.teal : Theme.muted) }
                    }
                }
                PrimaryButton(title: sync.state == .working ? "맞추는 중…" : "지금 맞추기") { Task { await sync.pull(m) } }
                Button("이 기기 동기화 끄기") { askOff = true }
                    .appFont(15, .semibold).foregroundStyle(Theme.up).frame(maxWidth: .infinity, minHeight: 48)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
                    .confirmationDialog("이 기기 동기화를 끌까요?", isPresented: $askOff, titleVisibility: .visible) {
                        Button("끄기", role: .destructive) { sync.turnOff() }
                    } message: { Text(sync.isDev ? "이 폰에 있는 값은 그대로 남고, 토큰은 이 폰에서 지워요." : "이 폰에 있는 값은 그대로 남고, 다른 기기와 더 맞추지 않아요. 다시 켜려면 같은 비밀번호가 필요해요.") }
            } else {
                let ok = pw.count >= 10 && pw == pw2
                Card {
                    Text("동기화 켜기").appFont(15, .bold)
                    Text("쓰는 기기마다 같은 동기화 비밀번호를 넣으면 종목과 진행이 자동으로 맞춰져요. PC에서는 naeilo.com 설정 > 기기 자동 동기화에 같은 비밀번호를 넣어요.")
                        .appFont(14).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
                    SecureField("동기화 비밀번호 (10자 이상)", text: $pw).textContentType(.newPassword)
                        .padding(.horizontal, 12).frame(minHeight: 48).overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
                    SecureField("한 번 더", text: $pw2).textContentType(.newPassword)
                        .padding(.horizontal, 12).frame(minHeight: 48).overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
                    if !pw.isEmpty && pw.count < 10 { Text("10자 이상으로 남이 짐작하기 어렵게 정해 주세요.").appFont(12).foregroundStyle(Theme.up) }
                    else if !pw2.isEmpty && pw != pw2 { Text("두 번 넣은 비밀번호가 달라요.").appFont(12).foregroundStyle(Theme.up) }
                    PrimaryButton(title: sync.state == .working ? "준비 중…" : "켜기", color: ok ? Theme.teal : Theme.muted) {
                        guard ok, sync.state != .working else { return }
                        let p = pw; pw = ""; pw2 = ""
                        Task { await sync.turnOn(password: p, model: m) }
                    }
                }
                DisclosureGroup(isExpanded: $devOpen) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("사이트 설정 > 개발자용에 GitHub 토큰을 넣어 둔 기기는 비밀번호 대신 그 토큰으로 맞춰요. 같은 토큰(저장소 쓰기 권한)을 넣으면 그 기기들과 같은 칸을 써요.")
                            .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                        SecureField("GitHub 토큰", text: $token).textContentType(.password).autocorrectionDisabled().textInputAutocapitalization(.never)
                            .padding(.horizontal, 12).frame(minHeight: 48).overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 2))
                        PrimaryButton(title: sync.state == .working ? "확인 중…" : "토큰으로 연결", color: token.isEmpty ? Theme.muted : Theme.teal) {
                            guard !token.isEmpty, sync.state != .working else { return }
                            let t = token; token = ""
                            Task { await sync.turnOnDev(token: t, model: m) }
                        }
                        Text("토큰은 이 폰의 키체인에만 두고, 중계는 저장소 쓰기 권한만 확인해요. 개발자 칸은 암호화하지 않아요.")
                            .appFont(12).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.top, 8)
                } label: { Text("개발자용").appFont(14, .semibold).foregroundStyle(Theme.sub) }
                .tint(Theme.sub)
            }
            fileCard
            note("이 폰에서 암호화한 값만 서버에 두어서, 서버는 보유 내역을 볼 수 없어요. 비밀번호는 어디에도 저장하지 않아서 잊으면 되찾을 수 없어요. 그때는 새 비밀번호로 다시 켜면 돼요. 두 기기에서 같이 고치면 나중에 고친 쪽이 남아요.")
        }
    }

    /// 내 폰에 저장하기·불러오기 (동기화 없이 파일 하나로)
    private var fileCard: some View {
        Card {
            Text("내 폰에 저장하기 · 불러오기").appFont(15, .bold)
            Text("종목·수량·평균 단가와 미션 진행을 파일 하나로 저장해요. 파일 앱의 '나의 iPhone'이나 iCloud Drive에 두었다가, 새 폰이나 앱을 다시 깐 뒤 불러오면 그대로 이어져요.")
                .appFont(14).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
            PrimaryButton(title: "내 폰에 저장하기") { exportDoc = BackupFile(Backup(m)); exporting = true }
            Button("저장한 파일 불러오기") { importing = true }
                .appFont(15, .semibold).foregroundStyle(Theme.teal).frame(maxWidth: .infinity, minHeight: 48)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 2))
            if let fileMsg { Text(fileMsg).appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true) }
            Text("파일은 암호화하지 않아요. 보유 내역이 그대로 들어 있으니 다른 사람에게 보내지 마세요.")
                .appFont(12).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
        }
        .fileExporter(isPresented: $exporting, document: exportDoc, contentType: .json, defaultFilename: Backup.fileName()) { r in
            switch r {
            case .success: fileMsg = "저장했어요. 종목 \(m.holdings.count)개와 미션 진행이 들어 있어요."
            case .failure(let e): fileMsg = "저장하지 못했어요: \(e.localizedDescription)"
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { r in
            switch r {
            case .success(let url):
                do { pending = try BackupFile.read(url) } catch { fileMsg = "naeilo에서 저장한 파일이 아니에요." }
            case .failure(let e): fileMsg = "열지 못했어요: \(e.localizedDescription)"
            }
        }
        .confirmationDialog("이 파일로 이 폰을 맞출까요?", isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } }), titleVisibility: .visible) {
            Button("불러오기") {
                guard let b = pending else { return }
                b.restore(to: m)
                fileMsg = "불러왔어요. 종목 \(b.holdings.count)개와 미션 진행을 이 폰에 넣었어요."
                pending = nil
            }
        } message: {
            let f = DateFormatter(), b = pending
            let _ = { f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "M월 d일 a h:mm" }()
            Text("\(b.map { f.string(from: $0.at) } ?? "")에 저장한 파일 (종목 \(b?.holdings.count ?? 0)개). 지금 이 폰의 종목과 미션 진행은 이 파일 값으로 바뀌어요." + (sync.isOn ? " 동기화가 켜져 있어서 다른 기기에도 보내져요." : ""))
        }
    }

    private var statusText: String {
        switch sync.state {
        case .off: return "폰과 PC가 같은 숫자를 보려면 켜요"
        case .idle: return sync.isDev ? "같은 GitHub 계정의 개발자 기기끼리 맞춰져요" : "같은 비밀번호를 넣은 기기끼리 맞춰져요"
        case .working: return "맞추는 중…"
        case .ok(let d):
            let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "a h:mm"
            return "마지막으로 맞춘 때: \(f.string(from: d))"
        case .failed(let e): return "맞추지 못했어요: \(e)"
        }
    }

    private func conflictCard(_ c: Sync.Remote) -> some View {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "M월 d일 a h:mm"
        let n = (c.state["holdings"]?.value as? [[String: Any]])?.count ?? 0
        return Card {
            Text("다른 기기에 저장된 값이 있어요").appFont(15, .bold)
            Text("\(f.string(from: Date(timeIntervalSince1970: c.at / 1000)))에 저장된 값 (종목 \(n)개)이 있어요. 어느 쪽에 맞출까요?")
                .appFont(14).fixedSize(horizontal: false, vertical: true)
            PrimaryButton(title: "다른 기기 값으로 이 폰을 맞추기") { Task { await sync.resolve(useRemote: true, m) } }
            Button("이 폰 값을 다른 기기로 보내기") { Task { await sync.resolve(useRemote: false, m) } }
                .appFont(15, .semibold).foregroundStyle(Theme.teal).frame(maxWidth: .infinity, minHeight: 48)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 2))
        }
    }
}

// MARK: 위젯 — 받은 위젯과 인물 위젯 미리보기 (미션 진행대로 열림)
// 홈 화면에 놓는 일은 아이폰이 하므로 여기서는 넣고 빼지 않는다. 무엇이 열렸는지, 어떻게 보이는지, 언제 열리는지만 보여 준다.

extension AppModel {
    struct WidgetInfo: Identifiable { let id: String; let name: String; let desc: String; let size: String; let ok: Bool; let how: String }
    /// 기본 위젯 (위젯 추가 화면과 같은 이름·순서). 열림은 앱 시작 단계로 (WidgetUnlock)
    var baseWidgets: [WidgetInfo] {
        let open = Set(WidgetUnlock.kinds(doneSteps: devAll ? 3 : nxStep))
        let list: [(String, (String, String), String)] = [
            ("asset.small", Catalog.asset, "작은"), ("future.small", Catalog.future, "작은"), ("block.small", Catalog.block, "작은"),
            ("pace.medium", Catalog.pace, "중간"), ("target.medium", Catalog.target, "중간"), ("moves.medium", Catalog.moves, "중간"),
            ("future.large", Catalog.futureL, "큰"),
            ("lock.asset", Catalog.lAsset, "잠금 화면"), ("lock.goal", Catalog.lGoal, "잠금 화면"),
            ("lock.future", Catalog.lFuture, "잠금 화면"), ("lock.target", Catalog.lTarget, "잠금 화면"),
        ]
        return list.map { k, c, sz in WidgetInfo(id: k, name: c.0, desc: c.1, size: sz, ok: open.contains(k), how: WidgetUnlock.how(k)) }
    }
}

struct WidgetPickView: View {
    @Environment(AppModel.self) private var m
    @State private var dark = UserDefaults.standard.bool(forKey: "cpDark")
    @State private var who: String? = UserDefaults.standard.string(forKey: "cpWho")

    private var reward: WReward { Store.read(WReward.self, "reward.json") ?? .sample }

    var body: some View {
        let base = m.baseWidgets, friendsOn = Shelter.friends.indices.filter { m.friendOn($0) }.count
        let sel = who ?? m.homeFriendShown
        let fi = Shelter.friends.firstIndex { $0.id == sel } ?? 0, on = m.friendOn(fi)
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("위젯").appFont(22, .bold)
                Text("미션을 하나씩 마치면 위젯과 인물이 열려요. 홈 화면을 길게 누르고 + → naeilo 에서 놓아요.")
                    .appFont(14).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 8) {
                    stat("기본 위젯", "\(base.filter(\.ok).count)/\(base.count)")
                    stat("인물", "\(friendsOn)/\(Shelter.friends.count)")
                }

                // 인물 위젯: 인물을 고르면 네 가지 모양을 바로 본다. 못 만난 인물은 홈 화면에서처럼 실루엣
                Text("인물 위젯").appFont(17, .bold).padding(.top, 6)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(Shelter.friends.enumerated()), id: \.element.id) { i, f in
                            let ok = m.friendOn(i), cur = f.id == sel
                            Button { who = f.id } label: {
                                VStack(spacing: 2) {
                                    Pixel(name: (ok ? "spr_" : "sil_") + f.id, width: 30, height: 42)
                                    Text(ok ? f.name : "???").appFont(12, .bold)
                                    Text(ok ? (f.id == m.homeFriendShown ? "홈에 있음" : "만남") : "인터미션 \(i)주차").appFont(10).foregroundStyle(Theme.sub)
                                }
                                .frame(width: 72, height: 86)
                                .background(cur ? Theme.mintBg : Theme.card, in: RoundedRectangle(cornerRadius: 12))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(cur ? Theme.teal : Theme.border, lineWidth: 2))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(ok ? f.name : "잠긴 인물, 인터미션 \(i)주차")
                        }
                    }
                }
                Picker("", selection: $dark) { Text("기본").tag(false); Text("다크").tag(true) }.pickerStyle(.segmented)
                let c = WChar.of(sel), e = CharEntry(date: Date(), r: reward, c: c, locked: !on)
                if !on {
                    Label("아직 못 만난 인물이에요. 인터미션 \(fi)주차에 만나요. 그 전에는 홈 화면에서도 이렇게 실루엣으로 보여요.", systemImage: "lock.fill")
                        .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
                }
                GeometryReader { g in
                    let w = (g.size.width - 12) / 2
                    VStack(spacing: 12) {
                        HStack(spacing: 12) {
                            caption("자산 추이") { tile(e, w: w, h: w) { TrendCharView(e: e) } }
                            caption("본전 진행") { tile(e, w: w, h: w) { RecoverCharView(e: e) } }
                        }
                        HStack(spacing: 12) {
                            caption("블록") { tile(e, w: w, h: w) { BlockCharView(e: e) } }
                            Spacer(minLength: 0)
                        }
                        caption("오늘의 움직임") { tile(e, w: g.size.width, h: w) { MovesCharView(e: e) } }
                    }
                }
                .aspectRatio(1 / 1.62, contentMode: .fit)
                note("인물 위젯은 위젯을 놓은 뒤 길게 눌러 '위젯 편집'에서 인물을 골라요. 기본값 '앱 홈의 인물 따라가기'는 쉼터에서 홈에 둔 인물을 따라가요.")

                // 기본 위젯: 앱 시작 단계로 열림
                Text("기본 위젯").appFont(17, .bold).padding(.top, 6)
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
                note("못 받은 위젯도 홈 화면에 놓을 수는 있지만, 열리기 전에는 잠긴 모습으로 보이고 누르면 앱 시작 단계로 와요. 위젯은 이 앱이 계산한 숫자를 받아 그려요.")
            }
            .screen()
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func stat(_ k: String, _ v: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(k).appFont(12).foregroundStyle(Theme.sub)
            Text(v + " 열림").appFont(17, .bold)
        }
        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
    }

    private func caption<V: View>(_ t: String, @ViewBuilder _ v: () -> V) -> some View {
        VStack(alignment: .leading, spacing: 4) { v(); Text(t).appFont(12).foregroundStyle(Theme.sub) }
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
                     ("앱", "오늘의 1분", "오늘 숫자 하나와 질문 하나. 7일 연속마다 쉼터에 물건이 돌아와요."),
                     ("알림", "알림", "비중이 계획에서 5%p 넘게 벗어난 날만 울려요."),
                     ("미션", "1000칸", "본전을 1000칸으로 나눠 채워요. 100칸마다 선물이 있어요."),
                     ("미션", "주간 예보", "월요일에 앱이 금요일 평가액 범위를 적어 두고, 금요일 종가로 범위 안인지 도장을 찍어요."),
                     ("PC", "naeilo.com", "여러 종목 한 번에 넣기, 증권사 파일, 긴 표와 근거는 PC에서 봐요.")], id: \.1) { w, t, v in
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
        let cards = [("minus", "회복 루트", "마이너스예요", "본전까지 가는 길. 원인 진단, 계획 4안, 절세, 4주 인터미션."),
                     ("plus", "목표 루트 · 플러스", "플러스예요", "지금 평가액에서 목표 금액까지. 구성 비교, 비중 조정 세금, 3개월 인터미션."),
                     ("none", "목표 루트 · 시작 전", "아직 시작 전이에요", "매달 넣는 돈으로 첫 목표까지. 구성 고르기, 3개월 인터미션.")]
        let cur = m.route == .recover ? "minus" : m.route == .plus ? "plus" : "none"
        let sel = pick.isEmpty ? cur : pick
        SettingsPage(title: "루트 바꾸기") {
            if m.route == .recover && m.total >= m.cost {
                Text("본전을 넘었어요. 이제 목표 루트 · 플러스로 바꿀 수 있어요.").appFont(14, .semibold).foregroundStyle(Color(hex: 0x0B5E40, dark: 0x7FD8B0))
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
                            Text(k == cur ? "지금 루트" : tag).appFont(12, .bold).foregroundStyle(k == cur ? Theme.teal : Theme.sub)
                        }
                        Text(sub).appFont(14).foregroundStyle(Theme.sub).multilineTextAlignment(.leading)
                    }
                    .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                    .background(on ? Theme.mintBg : Theme.card, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(on ? Theme.teal : Theme.border, lineWidth: 2))
                }
                .buttonStyle(.plain)
            }
            PrimaryButton(title: sel == cur ? "지금 루트예요" : "이 루트로 바꾸기", color: sel == cur ? Theme.muted : Theme.teal) {
                guard sel != cur else { return }
                m.switchRoute(sel == "minus" ? .recover : sel == "plus" ? .plus : .novice)
                pick = ""; m.settingsPath = []
            }
            note("루트를 바꿔도 지금까지 한 미션, 받은 위젯과 캐릭터는 그대로 남아요. 언제든 다시 돌아올 수 있어요.")
        }
    }
}

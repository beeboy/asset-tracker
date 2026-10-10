import SwiftUI

enum SettingsRoute: Hashable { case price, soon(String, String) }

struct SettingsView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        let rows: [(String, String, SettingsRoute)] = [
            ("알림", "모두 꺼짐", .soon("알림", "본전 도달, 비중 이탈(3·5·10%p), 연말 절세 확인, 아침 한 줄. 겹치는 날은 하루 한 번으로 묶어요. 알림 설정은 기기마다 따로예요.")),
            ("기기 동기화", "연결 안 됨", .soon("기기 동기화", "PC naeilo.com의 6자리 코드로 연결해요. 종목·미션·목표는 함께, 알림 설정은 기기마다 따로예요.")),
            ("위젯", "받은 위젯 3개", .soon("위젯", "홈 화면 미리보기, 받은 위젯 추가/빼기, 못 받은 위젯은 받는 방법을 보여요. 위젯은 기존 ios/ 위젯 앱과 합칠 예정이에요.")),
            ("세금 규칙", "대한민국 거주자", .soon("세금 규칙", "대한민국 거주자만 고를 수 있어요(다른 나라는 준비 중). 올해 실현 이익을 넣으면 내년 5월 예상 세금을 계산해요.")),
            ("시세 기준", "어제 종가 · 매일 아침 7시 갱신", .price),
            ("사용 방법", "매일 루틴, 1000칸, 주간 예보", .soon("사용 방법", "위젯에서 오늘 숫자 보기, 앱에서 오늘의 1분, 비중이 계획에서 벗어난 날만 알림.")),
            ("루트", "회복 · 마이너스", .soon("루트", "회복 · 목표 · 플러스 세 루트 중에서 바꿀 수 있어요. 미션·위젯·캐릭터는 유지돼요.")),
        ]
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AppHeader().padding(.horizontal, -16)
                Text("설정").appFont(22, .bold)
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { i, r in
                        if i > 0 { Divider().overlay(Theme.line) }
                        NavigationLink(value: r.2) {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(r.0).appFont(15, .bold)
                                    Text(r.1).appFont(13).foregroundStyle(Theme.sub)
                                }
                                Spacer()
                                Image(systemName: "chevron.right").appFont(13).foregroundStyle(Theme.muted)
                            }
                            .padding(.horizontal, 16).frame(minHeight: 60).contentShape(Rectangle())
                        }.buttonStyle(.plain)
                    }
                }
                .background(.white, in: RoundedRectangle(cornerRadius: 18))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))

                Text("시안 조작").appFont(13, .bold).foregroundStyle(Theme.sub).padding(.top, 8)
                HStack(spacing: 8) {
                    demoButton("미션 1부터") { m.resetDemo(.fresh) }
                    demoButton("인터미션 1주차로") { m.resetDemo(.week1) }
                    demoButton("모든 화면 열기") { m.resetDemo(.all) }
                }
                Text("시세는 스텁(시안과 같은 예시 값)이고, 외전 원고도 서버 대신 스텁이에요. 버전 0.1").appFont(12).foregroundStyle(Theme.muted)
            }
            .screen()
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: SettingsRoute.self) { r in
            switch r {
            case .price: PriceBasisView()
            case .soon(let t, let d): SoonView(title: t, detail: d)
            }
        }
    }

    private func demoButton(_ t: String, _ a: @escaping () -> Void) -> some View {
        Button(action: a) {
            Text(t).appFont(14, .semibold).frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(Theme.ink)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.muted, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
        }.buttonStyle(.plain)
    }
}

struct PriceBasisView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("시세 기준").appFont(22, .bold)
                VStack(alignment: .leading, spacing: 6) {
                    Text("지금 보는 숫자").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                    Text("10월 8일 종가").appFont(28, .bold)
                    Text("매일 오전 7시에 갱신해요").appFont(13).foregroundStyle(Color(hex: 0xC9D0D6))
                }
                .foregroundStyle(.white).padding(18).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.ink, in: RoundedRectangle(cornerRadius: 20))
                Card {
                    row("미국 종목", "한국 시간 아침에 전날 종가 반영")
                    row("한국 종목", "장 마감 뒤 그날 종가 반영")
                    row("환율", "원/달러 \(Int(Sample.fx).formatted())원 (시안 가정)")
                    row("시세 제공", "견적 비교 중 (지금은 스텁)")
                }
                Text("하루 안의 움직임은 보여 주지 않아요. 어제 종가 하나로 계산해서 숫자가 하루 동안 같아요.").appFont(13).foregroundStyle(Theme.sub)
            }
            .padding(16)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }
    private func row(_ k: String, _ v: String) -> some View {
        HStack(alignment: .top) { Text(k).fontWeight(.semibold).frame(width: 80, alignment: .leading); Text(v).foregroundStyle(Theme.sub) }
            .appFont(14)
    }
}

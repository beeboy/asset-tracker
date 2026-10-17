import SwiftUI
import WidgetKit

/// 위젯 이름표: 이름·설명·크기를 kind 하나로 찾는다. 위젯 추가 화면, 잠긴 위젯 제목, 앱 '위젯' 탭이 모두 여기서 읽는다
/// 위젯 추가 화면의 순서는 NaeiloWidgets 묶음에 적은 순서다 (자산 추이가 첫 번째)
enum Catalog {
    struct Item { let kind: String; let name: String; let desc: String; let size: String }

    /// 기본 7개 + 잠금화면 4개 (앱 '위젯' 탭의 순서)
    static let base: [Item] = [
        Item(kind: "asset.small", name: "자산 추이", desc: "총자산·오늘 등락·최근 10일 흐름", size: "작은"),
        Item(kind: "future.small", name: "미래 평가액", desc: "3년 뒤 예상과 목표 확률", size: "작은"),
        Item(kind: "block.small", name: "지금 채우는 블록", desc: "목표를 1000칸으로 나눠 지금 채우는 100칸", size: "작은"),
        Item(kind: "pace.medium", name: "목표 페이스", desc: "필요한 경로보다 앞섰는지", size: "중간"),
        Item(kind: "target.medium", name: "이번 주 과녁", desc: "금요일 마감 예측 범위와 지금 위치", size: "중간"),
        Item(kind: "moves.medium", name: "오늘의 움직임", desc: "종목별 오늘 등락과 다음 이벤트", size: "중간"),
        Item(kind: "future.large", name: "미래 평가액 추이", desc: "전망 부채꼴과 좋을 때·나쁠 때", size: "큰"),
        Item(kind: "lock.asset", name: "자산 추이", desc: "총자산·오늘 등락·1달 흐름", size: "잠금 화면"),
        Item(kind: "lock.goal", name: "목표 진행", desc: "목표 대비 %", size: "잠금 화면"),
        Item(kind: "lock.future", name: "3년 뒤", desc: "3년 뒤 예상 범위", size: "잠금 화면"),
        Item(kind: "lock.target", name: "이번 주 과녁", desc: "금요일 마감 예측과 지금", size: "잠금 화면"),
    ]
    /// 인물 4개 + 서재
    static let more: [Item] = [
        Item(kind: "char.trend", name: "인물 · 자산 추이", desc: "총자산·오늘 등락·최근 10일 흐름, 인물이 옆에서 봐요", size: "작은"),
        Item(kind: "char.recover", name: "인물 · 본전 진행", desc: "본전(목표)까지 몇 %인지, 인물이 같이 봐요", size: "작은"),
        Item(kind: "char.block", name: "인물 · 블록", desc: "1000칸 중 지금 채우는 100칸과 10층 탑", size: "작은"),
        Item(kind: "char.moves", name: "인물 · 오늘의 움직임", desc: "종목별 오늘 등락과 인물의 한마디", size: "중간"),
        Item(kind: "story", name: "서재 · 이어 읽기", desc: "외전 『이종 공명』에서 읽던 자리의 한 문단. 누르면 그 장으로 가요", size: "작은·중간"),
    ]
    static func item(_ kind: String) -> Item? { (base + more).first { $0.kind == kind } }
    /// 위젯 추가 화면·잠긴 위젯·VoiceOver 가 쓰는 이름과 설명 (영어 기기면 WL 표의 영어)
    static func name(_ kind: String) -> String { WL.t(item(kind)?.name ?? "naeilo 위젯") }
    static func desc(_ kind: String) -> String { item(kind).map { WL.t($0.desc) } ?? "" }

    /// 위젯을 누르면 앱의 첫 탭(naeilo)으로
    static let openURL = URL(string: "naeilo://guide")!
    /// 잠긴 위젯을 누르면 구독 화면으로
    static let subscribeURL = URL(string: "naeilo://subscribe")!
}

extension WidgetConfiguration {
    /// 위젯 추가 화면의 이름·설명 (Catalog). 인물 위젯 정의가 앱에도 들어가서 여기 둔다
    func catalog(_ kind: String) -> some WidgetConfiguration {
        configurationDisplayName(Catalog.name(kind)).description(Catalog.desc(kind))
    }
}

/// 7일 무료가 끝나고 구독하지 않은 위젯의 잠금 (기본·잠금화면·인물·서재가 모두 이것 하나로 잠근다)
/// 홈 화면에 놓을 수는 있지만 숫자 대신 잠긴 모습. 누르면 앱의 구독 화면으로 간다. 위젯 갤러리 미리보기는 잠그지 않는다.
/// 구독하면 앱이 구독 상태를 다시 쓰고 위젯을 새로 그리게 해서, 같은 자리에서 바로 열린다.
/// 잠긴 모습은 기본이 LockedWidget(Locked.swift), 인물 위젯은 인물 바탕을 지키려고 locked 로 따로 준다 (이 파일은 앱에도 들어간다)
struct Gate<Content: View, Locked: View>: View {
    let kind: String
    let preview: Bool
    @ViewBuilder var content: Content
    @ViewBuilder var locked: Locked
    var body: some View {
        if preview || Store.isUnlocked(kind) { content }
        else { locked.widgetURL(Catalog.subscribeURL) }
    }
}

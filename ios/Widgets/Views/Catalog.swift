import SwiftUI

/// 위젯 이름·설명 (위젯 추가 화면과 앱 '위젯' 탭이 같이 쓴다)
/// 위젯 추가 화면의 순서는 NaeiloWidgets 묶음에 적은 순서다 (자산 추이가 첫 번째)
enum Catalog {
    static let asset = ("자산 추이", "총자산·오늘 등락·3달 흐름")
    static let future = ("미래 평가액", "3년 뒤 예상과 목표 확률")
    static let block = ("지금 채우는 블록", "목표를 1000칸으로 나눠 지금 채우는 100칸")
    static let pace = ("목표 페이스", "필요한 경로보다 앞섰는지")
    static let target = ("이번 주 과녁", "금요일 마감 예측 범위와 지금 위치")
    static let moves = ("오늘의 움직임", "종목별 오늘 등락과 다음 이벤트")
    static let futureL = ("미래 평가액 추이", "전망 부채꼴과 좋을 때·나쁠 때")
    static let lAsset = ("자산 추이", "총자산·오늘 등락·1달 흐름")
    static let lGoal = ("목표 진행", "목표 대비 %")
    static let lFuture = ("3년 뒤", "3년 뒤 예상 범위")
    static let lTarget = ("이번 주 과녁", "금요일 마감 예측과 지금")

    /// 위젯을 누르면 앱의 첫 탭(naeilo)으로
    static let openURL = URL(string: "naeilo://guide")!
}

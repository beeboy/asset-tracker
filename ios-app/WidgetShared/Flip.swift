import SwiftUI

// ↻ 효과 (모든 위젯이 같이 쓴다): 누르면 '받는 중'(Store.isReloading)으로 한 번, 다 받으면 다시 한 번 그려진다. 위젯은 두 그림 사이를 애니메이션으로 잇는다
private let shakeSpring = Animation.interpolatingSpring(stiffness: 220, damping: 6)   // 덜 멈추는 용수철: 좌우로 몇 번 흔들리다 선다
private let hopSpring = Animation.interpolatingSpring(stiffness: 180, damping: 7)     // 통통 튀며 내려앉는 용수철

extension View {
    /// 숫자: 받는 동안 흐려졌다가, 다 받으면 바뀐 자리만 숫자가 굴러가며(플립) 새 값으로
    func flip(_ v: Double) -> some View {
        let busy = Store.isReloading
        return self.contentTransition(.numericText(value: v))
            .opacity(busy ? 0.35 : 1)
            .animation(.snappy(duration: 0.6), value: v)
            .animation(.easeInOut(duration: 0.25), value: busy)
    }
    /// 그림(차트·막대·칸): 받는 동안 흐려졌다가, 다 받으면 새 모양으로
    func flipFade() -> some View {
        let busy = Store.isReloading
        return self.opacity(busy ? 0.35 : 1).animation(.easeInOut(duration: 0.25), value: busy)
    }
    /// 인물: 받는 동안 몸을 움츠렸다 뛰어올라 왼쪽으로 기울고, 다 받으면 통통 튀며 내려앉아 좌우로 흔들린다
    func charShake() -> some View {
        let busy = Store.isReloading
        return self.scaleEffect(x: busy ? 1.06 : 1, y: busy ? 0.92 : 1, anchor: .bottom)
            .rotationEffect(.degrees(busy ? -9 : 0), anchor: .bottom)
            .offset(x: busy ? -5 : 0, y: busy ? -12 : 0)
            .animation(shakeSpring, value: busy)
            .animation(hopSpring, value: busy)
    }
    /// 인물 위젯 숫자 (예전 이름)
    func charFlip(_ v: Double) -> some View { flip(v) }
}

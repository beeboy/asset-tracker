import SwiftUI

// 첫 질문 (시안 첫 화면): 지금 내 투자, 플러스인가요 마이너스인가요?
struct StartView: View {
    @Environment(AppModel.self) private var m
    @State private var pick: Route? = nil     // 고른 길: 아래 예보 카드 숫자가 그 길의 숫자로 바뀐다

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("naeilo").appFont(20, .bold).padding(.top, 24)
                HStack(alignment: .bottom, spacing: 12) {
                    Pixel(name: "spr_seri", width: 56, height: 80).accessibilityLabel("세리")
                    Text("처음 오셨군요. …… 하나만 물어볼게요.").appFont(15)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background(Theme.card, in: UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 4, bottomTrailingRadius: 14, topTrailingRadius: 14))
                        .overlay(UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 4, bottomTrailingRadius: 14, topTrailingRadius: 14).stroke(Theme.border))
                        .padding(.bottom, 14)
                }
                Text("지금 내 투자,\n플러스인가요\n마이너스인가요?").appFont(30, .bold).lineSpacing(4)
                VStack(spacing: 10) {
                    option("마이너스예요", "본전까지 가는 길을 같이 찾아요", .recover)
                    option("플러스예요", "목표 금액까지 가는 길을 그려요", .plus)
                    option("아직 시작 전이에요", "한 달에 얼마씩이면 언제 얼마가 되는지 봐요", .novice)
                }
                if let pick {
                    PrimaryButton(title: "이 길로 시작하기") { m.startRoute(pick) }
                }
                Text("잘 모르겠다면 \"마이너스\"로 시작하세요. 매수 단가를 넣으면 자동으로 알려드려요.").appFont(13).foregroundStyle(Theme.muted)
                StartForecastCard(route: pick).padding(.top, 8)
                Text("See Tomorrow, Today.").appFont(12).kerning(0.4).foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity).padding(.top, 12)
            }
            .padding(.horizontal, 20).padding(.bottom, 24)
            .foregroundStyle(Theme.ink)
        }
        .background(Theme.bg)
    }

    private func option(_ t: String, _ sub: String, _ r: Route) -> some View {
        let on = pick == r
        return Button { withAnimation(.easeOut(duration: 0.2)) { pick = on ? nil : r } } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(t).appFont(19, .bold)
                Text(sub).appFont(14).foregroundStyle(Theme.sub)
            }
            .padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(on ? Theme.teal : Theme.border, lineWidth: 2))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

// 첫 질문 아래 예보 카드 (캐릭터 설정 스레드 시안 first-question-forecast). 숫자는 예시 보유로 계산한 값이다.
// 고른 길에 따라: 마이너스 → 본전까지 보통 걸리는 기간, 플러스(고르기 전 기본) → 3년 안에 목표에 닿을 확률, 시작 전 → 매달 넣으면 3년 뒤 보통
struct StartForecastCard: View {
    let route: Route?

    // 예시 가정: 연 기대 8%, 흔들림 12% (주식과 채권을 섞은 예시 보유)
    private static let mu = 0.08, sigma = 0.12
    private static var g: Double { mu - sigma * sigma / 2 }

    private struct Ex { let v0: Double; let monthly: Double; let line: Double?; let lineName: String; let big: String; let sub: String }
    private var ex: Ex {
        let g = Self.g, s = Self.sigma
        switch route {
        case .recover:
            // 4,350만원 → 본전 5,000만원: 보통의 경우(중앙값)가 본전에 닿는 때 = ln(본전/지금) / g
            let v0 = 4350.0, c = 5000.0, mo = log(c / v0) / g * 12
            return Ex(v0: v0, monthly: 0, line: c, lineName: "본전", big: AppModel.eta(mo).replacingOccurrences(of: " 뒤", with: "").replacingOccurrences(of: "뒤", with: ""), sub: "본전까지 보통 걸리는 기간")
        case .novice:
            let m = 50.0, v = Self.value(0, m, 3, 0)
            return Ex(v0: 0, monthly: m, line: m * 36, lineName: "넣은 돈", big: AppModel.wonK(v), sub: "매달 50만원이면 3년 뒤 보통")
        default:
            // 5,000만원 → 목표 5,800만원, 3년 뒤 목표 이상일 확률
            let v0 = 5000.0, k = 5800.0
            let p = AppModel.normCDF((log(v0 / k) + g * 3) / (s * sqrt(3)))
            return Ex(v0: v0, monthly: 0, line: k, lineName: "목표", big: AppModel.pct(p), sub: "3년 안에 목표에 닿을 확률")
        }
    }

    /// t년 뒤 값 (z = 표준정규 분위). 매달 넣는 돈은 평균 절반 기간만 불어난다고 본다
    static func value(_ v0: Double, _ m: Double, _ t: Double, _ z: Double) -> Double {
        v0 * exp(g * t + z * sigma * sqrt(t)) + m * 12 * t * exp(g * t / 2 + z * sigma * sqrt(t / 3))
    }

    var body: some View {
        let e = ex
        VStack(alignment: .leading, spacing: 14) {
            Text("naeilo · 3년 뒤가 보여요").appFont(13, .semibold).foregroundStyle(Theme.yellow)
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text(e.big).appFont(40, .regular).lineLimit(1).fixedSize().contentTransition(.numericText())
                Text(e.sub).appFont(14).foregroundStyle(Color(hex: 0xC9CFDB))
            }
            .foregroundStyle(.white)
            fan(e).frame(height: 140)
            HStack {
                ForEach(["지금", "1년", "2년", "3년"], id: \.self) { t in
                    Text(t).appFont(12).foregroundStyle(Color(hex: 0x9AA5B8))
                    if t != "3년" { Spacer() }
                }
            }
            Text("종목과 수량만 넣으면 내 숫자로 바뀌어요. 루트를 고르면 그 길의 숫자를 보여 드려요.")
                .appFont(14).foregroundStyle(Color(hex: 0xEEF0F7)).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
            Text("예시 화면이에요 · 확률 모형의 결과이며 투자 권유가 아니에요").appFont(11).foregroundStyle(Color(hex: 0x8A93A6))
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(hex: 0x141A2B, dark: 0x161C2E), in: RoundedRectangle(cornerRadius: 20))
        .animation(.easeOut(duration: 0.25), value: route)
        .accessibilityElement(children: .combine)
    }

    // 3년 부채꼴: 5~95 띠, 25~75 띠, 보통의 경우 선, 노란 점선(목표·본전·넣은 돈)
    private func fan(_ e: Ex) -> some View {
        Canvas { ctx, size in
            let W = size.width, H = size.height
            let ts = stride(from: 0.0, through: 3, by: 0.1).map { $0 }
            let top = max(Self.value(e.v0, e.monthly, 3, 1.645), (e.line ?? 0)) * 1.05
            let bot = e.v0 > 0 ? min(Self.value(e.v0, e.monthly, 3, -1.645), e.v0) * 0.97 : 0
            let X = { (t: Double) in 6 + t / 3 * (W - 12) }
            let Y = { (v: Double) in H - 6 - (v - bot) / (top - bot) * (H - 12) }
            func band(_ z: Double) -> Path {
                var p = Path()
                for (i, t) in ts.enumerated() { let pt = CGPoint(x: X(t), y: Y(Self.value(e.v0, e.monthly, t, z))); i == 0 ? p.move(to: pt) : p.addLine(to: pt) }
                for t in ts.reversed() { p.addLine(to: CGPoint(x: X(t), y: Y(Self.value(e.v0, e.monthly, t, -z)))) }
                p.closeSubpath(); return p
            }
            ctx.fill(band(1.645), with: .color(Color(hex: 0x2A3A6E).opacity(0.75)))
            ctx.fill(band(0.674), with: .color(Color(hex: 0x3A5296).opacity(0.85)))
            var med = Path()
            for (i, t) in ts.enumerated() { let pt = CGPoint(x: X(t), y: Y(Self.value(e.v0, e.monthly, t, 0))); i == 0 ? med.move(to: pt) : med.addLine(to: pt) }
            ctx.stroke(med, with: .color(Color(hex: 0x8FB0FF)), lineWidth: 2.5)
            if let l = e.line {
                // 목표·본전은 가로선, 넣은 돈은 매달 쌓이므로 비스듬한 선
                let y1 = e.monthly > 0 ? Y(0) : Y(l)
                ctx.stroke(Path { $0.move(to: CGPoint(x: X(0), y: y1)); $0.addLine(to: CGPoint(x: e.monthly > 0 ? X(3) : W, y: Y(l))) },
                           with: .color(Theme.yellow), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
                let lp = e.monthly > 0 ? CGPoint(x: X(2.2), y: Y(l * 2.2 / 3) + 14) : CGPoint(x: 16, y: Y(l) - 10)
                ctx.draw(Text(e.lineName).font(.system(size: 12)).foregroundStyle(Theme.yellow), at: lp, anchor: .leading)
            }
            let o = CGPoint(x: X(0), y: Y(Self.value(e.v0, e.monthly, 0, 0)))
            ctx.fill(Path(ellipseIn: CGRect(x: o.x - 5, y: o.y - 5, width: 10, height: 10)), with: .color(.white))
        }
        .accessibilityLabel("3년 예상 범위 부채꼴")
    }
}

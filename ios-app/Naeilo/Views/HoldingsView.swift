import SwiftUI

// 종목 탭: 위에 고정 그래프 + 기간 칩 하나로 아래 목록도 같이 바뀐다
struct HoldingsView: View {
    @Environment(AppModel.self) private var m
    @State private var period: Period = .y1

    var body: some View {
        let rows = m.rows
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AppHeader().padding(.horizontal, -16)
                Text("내 종목").font(.system(size: 22, weight: .bold))
                Text("\(rows.count)종목 · 평가액 \(AppModel.man(m.total)) · \(AppModel.sgn(m.ret))")
                    .font(.system(size: 14)).foregroundStyle(Theme.sub)
                totalChart(rows)
                ChipRow(items: Period.allCases.map { ($0, $0.label) }, selection: $period, fill: true)
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.element.id) { i, r in
                        if i > 0 { Divider().overlay(Theme.line) }
                        NavigationLink(value: r.sym) { row(r) }.buttonStyle(.plain)
                    }
                }
                .background(.white, in: RoundedRectangle(cornerRadius: 18))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.border))
                NavigationLink(value: "add") {
                    Label("종목 추가", systemImage: "plus").font(.system(size: 15, weight: .bold))
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .foregroundStyle(Theme.teal)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.teal, lineWidth: 2))
                }.buttonStyle(.plain)
                Text("작은 그래프는 \(period == .d1 ? "그저께와 어제 종가" : period == .w1 ? "최근 6개 종가" : period.label + " 가격 흐름")이고, 회색 점선은 내 평균 단가예요(그 기간 가격 범위 안에 있을 때만). 오르면 빨강, 내리면 파랑이에요. 시세는 어제 종가 기준이에요. 여러 종목 한 번에 넣기와 증권사 파일은 PC naeilo.com에서 해요.")
                    .font(.system(size: 12)).foregroundStyle(Theme.muted).lineSpacing(3)
            }
            .screen()
        }
        .background(Theme.bg)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: Symbol.self) { HoldingDetailView(sym: $0, period: period) }
        .navigationDestination(for: String.self) { _ in AddHoldingView() }
    }

    private func totalChart(_ rows: [AppModel.Row]) -> some View {
        let series = rows.map { r in m.prices.series(r.sym, period: period).map { m.krw(r.sym, $0 * r.h.qty) } }
        let n = series.map(\.count).min() ?? 0
        let tot = (0..<n).map { i in series.reduce(0) { $0 + $1[i] } }
        let chg = (tot.last ?? 0) / max(1, tot.first ?? 1) - 1
        return Card {
            HStack(alignment: .firstTextBaseline) {
                Text("전체 평가액 · \(period == .d1 ? "그저께→어제" : period.label)").font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.sub)
                Spacer()
                Text(AppModel.sgn(chg)).font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.change(chg))
            }
            Sparkline(points: tot, avg: m.cost, lineWidth: 2.2, showEndDot: true).frame(height: 120)
            Text("점선은 들어간 돈 \(AppModel.man(m.cost)) (그래프 범위 안일 때만)").font(.system(size: 12)).foregroundStyle(Theme.muted)
        }
    }

    private func row(_ r: AppModel.Row) -> some View {
        let pts = m.prices.series(r.sym, period: period)
        let chg = (pts.last ?? 1) / (pts.first ?? 1) - 1
        return HStack(spacing: 10) {
            LogoTile(symbol: r.id, size: 36)
            VStack(alignment: .leading, spacing: 2) {
                Text(r.sym.name).font(.system(size: 15, weight: .bold))
                Text("\(r.id) · \(AppModel.price(r.sym, r.sym.close)) · \(AppModel.pct(r.value / max(1, m.total)))")
                    .font(.system(size: 12)).foregroundStyle(Theme.sub)
            }
            Spacer(minLength: 4)
            Sparkline(points: pts, avg: r.h.avg).frame(width: 64, height: 30)
            VStack(alignment: .trailing, spacing: 2) {
                Text(AppModel.man(r.value)).font(.system(size: 15, weight: .bold)).monospacedDigit()
                Text(AppModel.sgn(chg)).font(.system(size: 13, weight: .bold)).foregroundStyle(Theme.change(chg))
            }
            .frame(minWidth: 72, alignment: .trailing)
        }
        .padding(.horizontal, 14).frame(minHeight: 64)
        .contentShape(Rectangle())
    }
}

struct HoldingDetailView: View {
    @Environment(AppModel.self) private var m
    let sym: Symbol
    @State var period: Period

    var body: some View {
        let h = m.holdings.first { $0.symbol == sym.id } ?? Holding(symbol: sym.id, qty: 0, avg: sym.close)
        let pts = m.prices.series(sym, period: period)
        let chg = (pts.last ?? 1) / (pts.first ?? 1) - 1
        let val = m.krw(sym, h.qty * sym.close), cost = m.krw(sym, h.qty * h.avg)
        let r = cost > 0 ? val / cost - 1 : 0, need = h.avg / sym.close - 1
        let avgIn = period == .y1 || period == .y3 || (h.avg >= (pts.min() ?? 0) && h.avg <= (pts.max() ?? 0))
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    LogoTile(symbol: sym.id, size: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(sym.name).font(.system(size: 22, weight: .bold))
                        Text("\(sym.id) · \(sym.market) · \(sym.sector)").font(.system(size: 13)).foregroundStyle(Theme.sub)
                    }
                }
                Card {
                    HStack(alignment: .firstTextBaseline) {
                        Text(AppModel.man(val)).font(.system(size: 26, weight: .bold))
                        Text(AppModel.sgn(r)).font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.change(r))
                        Spacer()
                        Text("\(period == .d1 ? "그저께→어제 종가" : period.label) \(AppModel.sgn(chg))")
                            .font(.system(size: 13, weight: .bold)).foregroundStyle(Theme.change(chg))
                    }
                    Sparkline(points: pts, avg: avgIn ? h.avg : nil, lineWidth: 2.2, showEndDot: true).frame(height: 116)
                    Text(avgIn ? "점선은 평균 단가 \(AppModel.price(sym, h.avg))" : "평균 단가 \(AppModel.price(sym, h.avg))는 이 범위 밖")
                        .font(.system(size: 12)).foregroundStyle(Theme.muted)
                }
                ChipRow(items: Period.allCases.map { ($0, $0.label) }, selection: $period, fill: true)
                Card(padding: 0) {
                    VStack(spacing: 0) {
                        kv("보유 수량", h.qty.formatted() + "주")
                        kv("평균 단가", AppModel.price(sym, h.avg))
                        kv("어제 종가", AppModel.price(sym, sym.close))
                        kv(need > 0 ? "본전까지" : "본전 대비", need > 0.0005 ? "+" + String(format: "%.1f", need * 100) + "% 올라야 해요"
                           : need > -0.0005 ? "본전과 같아요" : "본전보다 " + String(format: "%.1f", -need * 100) + "% 위")
                        kv("비중", m.total > 0 ? AppModel.pct(val / m.total) : "-")
                        kv("업종", sym.sector, last: true)
                    }
                }
                if let p = Sample.profiles[sym.id] { profile(p) }
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationTitle(sym.id).navigationBarTitleDisplayMode(.inline)
    }

    private func kv(_ k: String, _ v: String, last: Bool = false) -> some View {
        VStack(spacing: 0) {
            HStack { Text(k).foregroundStyle(Theme.sub); Spacer(); Text(v).fontWeight(.semibold) }
                .font(.system(size: 14)).padding(.horizontal, 16).frame(minHeight: 44)
            if !last { Divider().overlay(Theme.line).padding(.horizontal, 16) }
        }
    }

    private func profile(_ p: Profile) -> some View {
        Card {
            HStack {
                Text(p.etf ? "이 ETF는" : "이 회사는").font(.system(size: 15, weight: .bold))
                Spacer()
                if p.virtual {
                    Text("가상 종목").font(.system(size: 11, weight: .bold)).foregroundStyle(Color(hex: 0x5A3E00))
                        .padding(.horizontal, 8).padding(.vertical, 2).background(Color(hex: 0xFFF1C9), in: Capsule())
                }
            }
            Text(p.what).font(.system(size: 14)).lineSpacing(3)
            VStack(alignment: .leading, spacing: 2) {
                Text(p.etf ? "따라가는 지수" : "비전").font(.system(size: 12, weight: .bold)).foregroundStyle(Theme.teal)
                Text(p.vision).font(.system(size: 14, weight: .semibold))
            }
            .padding(10).frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.mintBg, in: RoundedRectangle(cornerRadius: 10))
            let rows: [(String, String)] = [p.ceo, (p.hqKey ?? (p.etf ? "시장" : "본사"), p.hq), (p.etf ? "상장" : "설립", p.since)] + (p.extra.map { [$0] } ?? [])
            ForEach(rows, id: \.0) { k, v in
                HStack(alignment: .top) { Text(k).foregroundStyle(Theme.sub).frame(width: 72, alignment: .leading); Text(v) }
                    .font(.system(size: 13))
            }
            Text(p.virtual ? "드링커는 『중첩된 현실』 속 회사를 바탕으로 한 가상 종목이에요. 가격 흐름은 내 지난 자산 기록을 비율로 바꾼 값이고, 투자 권유가 아니에요."
                 : "소개는 공개 정보 기준(2026년 10월 확인)이에요. 실제 앱은 공시와 데이터 제공처 값을 받아 와요. 종목 추천이 아니에요.")
                .font(.system(size: 12)).foregroundStyle(Theme.muted).lineSpacing(2)
        }
    }
}

// 종목 추가: 한글·영문·티커·종목 코드 검색 → 수량·평균 단가
struct AddHoldingView: View {
    @Environment(AppModel.self) private var m
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var picked: Symbol? = nil
    @State private var qty = ""
    @State private var avg = ""

    var body: some View {
        let q = query.lowercased().replacingOccurrences(of: " ", with: "")
        let found = Sample.symbols.filter { q.isEmpty || "\($0.id)\($0.name)\($0.search)".lowercased().replacingOccurrences(of: " ", with: "").contains(q) }
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("종목 추가").font(.system(size: 22, weight: .bold))
                TextField("한글·영문 이름, 티커, 종목 코드", text: $query)
                    .padding(.horizontal, 14).frame(minHeight: 48)
                    .background(.white, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border))
                if let p = picked {
                    Card {
                        Text("\(p.name) (\(p.id))").font(.system(size: 16, weight: .bold))
                        Text("어제 종가 \(AppModel.price(p, p.close))").font(.system(size: 13)).foregroundStyle(Theme.sub)
                        field("수량 (주)", $qty)
                        field("평균 단가 (\(p.currency == .usd ? "달러" : "원"))", $avg)
                        PrimaryButton(title: "추가하기") {
                            guard let qn = Double(qty), qn > 0, let an = Double(avg), an > 0 else { return }
                            m.holdings.removeAll { $0.symbol == p.id }
                            m.holdings.append(Holding(symbol: p.id, qty: qn, avg: an))
                            dismiss()
                        }
                    }
                } else if found.isEmpty {
                    Text("찾는 종목이 없어요. 한글 이름, 영문 이름, 티커, 종목 코드로 찾을 수 있어요.").font(.system(size: 14)).foregroundStyle(Theme.sub)
                } else {
                    VStack(spacing: 0) {
                        ForEach(found) { s in
                            let held = m.holdings.contains { $0.symbol == s.id }
                            Button { picked = s; qty = ""; avg = String(Int(s.close)) } label: {
                                HStack(spacing: 10) {
                                    LogoTile(symbol: s.id, size: 32)
                                    VStack(alignment: .leading) {
                                        Text(s.name).font(.system(size: 15, weight: .bold))
                                        Text("\(s.id) · \(s.market)").font(.system(size: 12)).foregroundStyle(Theme.sub)
                                    }
                                    Spacer()
                                    Text(held ? "보유 중" : AppModel.price(s, s.close)).font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(held ? Theme.muted : Theme.ink)
                                }
                                .padding(.horizontal, 14).frame(minHeight: 56).contentShape(Rectangle())
                            }.buttonStyle(.plain)
                            Divider().overlay(Theme.line)
                        }
                    }
                    .background(.white, in: RoundedRectangle(cornerRadius: 18))
                }
            }
            .screen().padding(.top, 8)
        }
        .background(Theme.bg)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func field(_ label: String, _ b: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.sub)
            TextField("", text: b).keyboardType(.decimalPad)
                .padding(.horizontal, 12).frame(minHeight: 44)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
        }
    }
}

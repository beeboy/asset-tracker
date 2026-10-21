import Foundation
import CryptoKit

// 숫자 맞추기 D 단계 (Swift 쪽): tools/num_parity/d/dsrc.py 가 앱 원본에서 꺼낸 DSrc.swift 로 고정 입력을 돌려 app.json 을 쓴다.
// 컴파일: swiftc -O ios-app/Naeilo/Data/MonteCarlo.swift <d 폴더>/DSrc.swift tools/num_parity/d/main.swift
// Kotlin 쪽은 android-app/app/src/test/.../NumParityTest.kt dStage (같은 순서·같은 식). 비교는 parity.py check
let dir = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "."
let inp = try JSONSerialization.jsonObject(with: Data(contentsOf: URL(fileURLWithPath: dir + "/input.json"))) as! [String: Any]
func dec<T: Decodable>(_ t: T.Type, _ v: Any?) -> T { try! JSONDecoder().decode(t, from: JSONSerialization.data(withJSONObject: v!)) }
func histOf(_ v: Any?) -> YahooSample.Hist { dec(YahooSample.Hist.self, v) }
var out: [String: Any] = [:]
let nul = NSNull()

// 1. 동기화 비밀번호 → 저장 위치·키 (Sync.derive), AES-GCM (Sync.call 188~190·207~208줄과 같은 CryptoKit 호출, iv 만 고정)
out["derive"] = (inp["pws"] as! [String]).map { pw -> [String: Any] in let c = Sync.derive(pw); return ["id": c.id, "k": c.k] }
let key = SymmetricKey(data: Data(base64Encoded: Sync.derive(inp["aesPw"] as! String).k)!)
let iv = Data((inp["aesIv"] as! [Int]).map { UInt8($0) })
out["aes"] = (inp["plains"] as! [String]).map { p -> [String: Any] in
    let box = try! AES.GCM.seal(Data(p.utf8), using: key, nonce: AES.GCM.Nonce(data: iv))
    let c = iv.base64EncodedString() + "." + (box.ciphertext + box.tag).base64EncodedString()
    let all = Data(base64Encoded: String(c.split(separator: ".")[1]))!
    let back = try! AES.GCM.open(AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: iv), ciphertext: all.dropLast(16), tag: all.suffix(16)), using: key)
    return ["c": c, "back": String(decoding: back, as: UTF8.self)]
}

// 2. 전망 입력값 (SiteStats.params·fx·corr)
let stats = dec(MC.Stats.self, inp["stats"])
out["params"] = (inp["ids"] as! [String]).map { id -> [Double] in let a = SiteStats.params(id, stats), b = SiteStats.params(id, nil); return [a.0, a.1, a.2, b.0, b.1, b.2] }
out["fx"] = [SiteStats.fx(stats).0, SiteStats.fx(stats).1, SiteStats.fx(nil).0, SiteStats.fx(nil).1]
out["corr"] = (inp["pairs"] as! [[String]]).map { p -> [Double] in [SiteStats.corr(p[0], p[1], stats), SiteStats.corr(p[0], p[1], nil)] }

// 3. 표 밖 종목 계산 (ExtraStats.augment)
let sp = (inp["sitePrices"] as! [String: Any]).mapValues { histOf($0) }
Market.shared.prices = SitePrices.File(h: sp)
let ex = ExtraStats()
ex.items = dec([String: ExtraStats.Item].self, inp["items"])
let aug = ex.augment(stats, keys: inp["augKeys"] as! [String])
var tk: [String: Any] = [:]
for (k, t) in aug.tickers where stats.tickers[k] == nil {
    tk[k] = ["mu": t.mu ?? nul, "trend": t.trend ?? nul, "gs": t.gs ?? nul, "vol": t.vol ?? nul, "n": t.n ?? nul, "cash": t.cash ?? nul] as [String: Any]
}
out["augment"] = ["tickers": tk, "keys": aug.corr.keys, "m": aug.corr.m.suffix(aug.corr.keys.count - stats.corr.keys.count).map { $0 }, "same": aug.corr.m.prefix(stats.corr.keys.count).map { $0.suffix(aug.corr.keys.count - stats.corr.keys.count).map { $0 } }]

// 4. 한국 종목 (KoreaEstimate.info·synth)
func infoJ(_ i: KoreaEstimate.Info) -> [String: Any] { ["proxy": i.proxy ?? nul, "hedged": i.hedged, "base": i.base] }
out["info"] = (inp["names"] as! [[String]]).map { infoJ(KoreaEstimate.info($0)) }
out["infoId"] = (inp["idsInfo"] as! [[String]]).map { a -> [String: Any] in var o = infoJ(KoreaEstimate.info(id: a[0], name: a[1], en: a[2])); o["liveOK"] = KoreaEstimate.liveOK(a[0], name: a[1]); return o }
out["synth"] = (inp["synth"] as! [[Any]]).map { a -> Any in
    let info = KoreaEstimate.Info(proxy: a[2] as? String, hedged: a[3] as! Bool)
    guard let h = KoreaEstimate.synth(price: (a[0] as! NSNumber).doubleValue, date: a[1] as! String, info: info) else { return nul }
    return ["dates": h.dates, "closes": h.closes] as [String: Any]
}

// 5. 종목 찾기 (StockSearch.local): 같은 예시 종목·전체 상장 목록으로
KRX_PATH = inp["krx"] as! String
Sample.symbols = (inp["syms"] as! [[String]]).map { Symbol(id: $0[0], name: $0[1], market: $0[2], currency: $0[3] == "usd" ? .usd : .krw, search: $0[4]) }
out["symYahoo"] = Sample.symbols.map { CustomSymbols.yahoo(for: $0) }
out["local"] = (inp["queries"] as! [String]).map { q in StockSearch.local(q).map { "\($0.id)|\($0.yahoo)|\($0.name)|\($0.market)" } }
out["krxCount"] = KrxList.all.count

// 6. 매매기준율 표 (MarFeed.smbs), 사이트 지난 종가 (SitePrices.hist), Yahoo 차트 (CustomSymbols.chart)
out["smbs"] = (inp["smbs"] as! [String]).map { t -> Any in
    guard let f = MarFeed.smbsParse(t, got: (inp["smbsGot"] as! NSNumber).doubleValue) else { return nul }
    return ["rate": f.rate, "date": f.date, "got": f.got, "prev": f.prev.map { ["rate": $0.rate, "date": $0.date] as [String: Any] } ?? nul] as [String: Any]
}
out["hist"] = (inp["hists"] as! [[String: Any]]).map { o -> Any in SitePrices.hist(o).map { ["dates": $0.dates, "closes": $0.closes] as [String: Any] } ?? nul }
let now = Date(timeIntervalSince1970: (inp["now"] as! NSNumber).doubleValue)
out["chart"] = (inp["charts"] as! [[Any]]).map { a -> Any in
    do {
        let (q, h) = try CustomSymbols.chartParse(a[0] as! [String: Any], keepToday: a[1] as! Bool, now: now)
        return ["last": q.last, "prev": q.prev, "dates": h.dates, "closes": h.closes] as [String: Any]
    } catch { return ["err": true] }
}

let e = try JSONSerialization.data(withJSONObject: out, options: [.sortedKeys])
try e.write(to: URL(fileURLWithPath: dir + "/app.json"))
print("D 단계 (Swift): 비밀번호 \((out["derive"] as! [Any]).count) · 암호 \((out["aes"] as! [Any]).count) · 찾기 \((out["local"] as! [Any]).count) · 상장 \(KrxList.all.count)")

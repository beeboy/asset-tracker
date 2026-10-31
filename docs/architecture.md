# 구조

- 파일 위치는 `docs/codemap/`, 짝은 `docs/android-map.md`.
- 계산 규칙은 `docs/guides/data.md`·`analysis.md`.

## 전체 흐름
```
Yahoo·smbs.biz·Shiller → GitHub Actions(server.py --collect 등) → data/*.json(Pages)
                                         ↑ 워커 cron 15분이 깨움
사용자 키(토스·Alpaca·Longbridge) → 공용 중계(Lightsail) → 앱
앱 → App Group JSON → 위젯 / WatchConnectivity → 워치
기기 간: 파일 옮기기, 또는 워커 /esync(암호화)
```

## iOS
- 타깃: `NaeiloApp`(com.naeilo.widget), `NaeiloWidgets`, 워치. App Group `group.com.naeilo.widget`.
- 빌드는 커밋된 `project.pbxproj`로 한다. xcodegen 금지.
- 핵심 데이터: `AppModel`(상태·평가액), `Market`(시세·환율·`valFx`), `MonteCarlo`·`ForecastEngine`(전망), `SiteStats`·`SiteCalc`(사이트 미리 계산), `Sync`·`Lots`, `TossSum`.
- 위젯 공용: `WidgetShared/Config`·`Models`·`Engine`. 앱 `WidgetBridge.write`가 JSON을 쓴다.
- 모의투자: `Naeilo/Mock/`(`MockDesk`가 장부의 유일한 문).

## 안드로이드
- Kotlin·Compose, 모듈 `:app`·`:widget`. iOS를 따른다.

## 입구 웹·예전 웹
- 입구: 루트 `index.html`·`entry.*`. 카드가 `web/model.js`를 쓴다.
- `web/`은 noindex. CI가 `app.js` 함수 원문을 계산 기준으로 쓴다. 지우지 않는다.

## 수집 워크플로
- `collect.yml`: 시세·quotes·mar 커밋(워커가 깨움).
- `stats.yml`: stats.json·calc.json + `check_calc`.
- `macro.yml`: 실러 금리·이익(월 1회).
- `sample.yml`: 가상 샘플 시세.
- `worker.yml`: 워커 배포(수동).
- `ios.yml`·`usage.yml`: 수동만.

## 워커 (`proxy/cloudflare-worker.js`)
- 경로: `?url=`, `/ai`, `/mar`, `/collect`, `/cu`, `/esync`, `/notify`, `/store/*`, `/heads`, `/gyeol` 등.
- KV `VIEWS`: 조회·댓글·동기화·상점·알림 신청.
- 공개판은 `?url=`·`/mar`를 안 쓴다.

## 저장
- 공개: `data/`(시세·환율·통계).
- 사용자: 앱 UserDefaults·App Group, 웹 localStorage.
- 동기화: 워커 KV `e:<id>`, PBKDF2 30만 회 + AES-GCM. 서버는 내용을 모른다.

## 계산 원본과 검사
- 몬테카를로: `web/model.js` = `MonteCarlo.swift`(`mc_parity`).
- 평가액·배당·세금·금리 충격: `calc_parity`가 웹·앱 원문을 같은 입력으로 비교.
- 앱 전용: 회복·목표 경로, 평정, 평행 세계, 모의투자.

## 웹·앱이 다른 곳
- 웹 5일 판정은 `yearsBetween*365 ≤ 5`, 앱은 KST 날짜 차이.
- 위젯 환율 대체값은 1350 고정.
- 앱 전망 경로 수 최대 10,000, 비교용 3,000.
- 앱은 일부 웹 모형 옵션(rebalance_yearly 등)을 지원 안 한다.
- 국내 ETF 규칙: 웹 `data/kr_proxy.json`, 앱은 코드 고정.
- 나머지 불일치는 `docs/todo/known.md`.

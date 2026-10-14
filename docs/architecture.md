# 구조 (architecture)

> 기준: 2026-10-14 코드. 줄 번호는 이 날짜 기준이라 바뀔 수 있습니다. 함수 이름으로 찾으세요.

## 1. 전체 그림
```
 Yahoo / smbs.biz / Shiller          GitHub Actions (collect·stats·macro·sample)
          │                                   │ 커밋
          ▼                                   ▼
   server.py --collect ──────────────►  data/*.json (공개 저장소, GitHub Pages)
                                              │
        Cloudflare 워커 asset-ai ◄────────────┤ (cron이 collect.yml 깨움, /mar·?url= 대체 경로)
          │ /esync 암호화 동기화                │
          ▼                                   ▼
        웹 web/ (브라우저 localStorage) ◄──► iOS 앱 (App Group 캐시) ──► 위젯 (App Group JSON)
```

## 2. 웹 (`web/`)
- 정적 사이트. `web/index.html`이 `charts.js → model.js → research.js → sharecard.js → stocks.js → app.js` 순서로 읽음 (모두 `?v=` 판 번호).
- `app.js`: 화면 로직 전체. 섹션: 서버/데이터 로드(`reloadData`), 동기화(`syncPull/syncPush`, esync), 평가(`valuation`), 시세·종목(`collect`, `yahoo`, KR 추정), 목표·현황(`renderDash`), 외부 요인, 금리 시나리오, 전망(`buildModelNow`, `runForecast`), 본전(`calcBep`), 비중안, 거래 기록, 배당·세금, 환율, 설정, 패턴 예측, 적중 기록판, 1000칸, 공유 이미지, init.
- `model.js` (`window.Model`): 순수 계산. `indicators`, `kalmanTrend`, `buildModel`, `simulate`(다변량 t 몬테카를로 + 환율 + 사건 점프), `reweight`, `breakeven`, `allocPlans`, `krSeries/krNorm/krAt`, `factorBetas`.
- `alloc-worker.js`: Web Worker에서 비중안·연구·몬테카를로 계산.
- 모드: 로컬(`server.py`의 `/api/data`)이 실패하면 정적 모드로 `../data/*` 를 직접 읽음.
- 상태 저장: localStorage `asset-tracker-state`(메인 상태), 그 밖에 `naeilo-*` 키(전망 캐시, 접기, 테마 등), `asset-tracker-extra`(브라우저가 직접 받은 시세).

## 3. iOS 앱 (`ios-app/`)
- 타깃: `NaeiloApp`(com.naeilo.widget), `NaeiloWidgets`(com.naeilo.widget.widgets, `WIDGET_EXT` 플래그). App Group `group.com.naeilo.widget`.
- `project.yml`(xcodegen)이 원본이지만 `project.pbxproj`도 커밋되어 있고 Mac은 이것으로 빌드.
- 주요 데이터 파일 (`Naeilo/Data/`):
  - `AppModel.swift`: 앱 상태, 평가액(`total`, `krw()`), `todayMove`, 전망 작업(`forecastJob`), 오늘의 1분 문제.
  - `Market.swift`: 시세·환율 수집과 캐시(`QuoteFeed`, `MarFeed`, `SitePrices`), `valFx`.
  - `MonteCarlo.swift` / `ForecastEngine.swift`: 웹 `model.js` 이식.
  - `SiteStats.swift`(data/stats.json), `SiteCalc.swift`(data/calc.json): 사이트가 미리 계산한 입력.
  - `Sync.swift`, `Lots.swift`: 웹과 암호화 동기화, 매수 묶음(lots).
  - `StockSearch.swift`: `KoreaEstimate`(국내 ETF 추정), `PriceHistory`, `CustomSymbols`.
  - `Recovery.swift`, `GoalRoute.swift`, `Parallel.swift`, `Calm.swift`, `BrickGame.swift`, `Me.swift`, `Story.swift`, `Support.swift`, `StoreAPI.swift`, `Remind.swift`, `Notifier.swift`, `Backup.swift`.
- `WidgetShared/`: `Config.swift`(URL, App Group 저장), `Models.swift`(위젯 데이터 형식), `Engine.swift`(위젯이 총액·일간 변화 재계산).
- 위젯 데이터: 앱의 `WidgetBridge.write`가 `summary.json`, `feed.json`, `live.json`, `reward.json`, `story.json`, `me.png`를 App Group에 쓰고 타임라인을 새로 고침. 위젯은 `data/quotes.json`을 스스로 받아 `Engine.build`로 총액을 다시 계산.

## 4. 데이터 흐름

### 수집 (GitHub Actions)
| 워크플로 | 일정(UTC) | 하는 일 |
|---|---|---|
| `collect.yml` | `*/30 8-23 * * 1-5`, `15 0 * * 2-6` + 워커 cron이 깨움 | `server.py --collect` → `data/prices/*.json`, `quotes.json`, `index.json`, `mar.json` 커밋 |
| `stats.yml` | `40 0 * * *` | `site_stats.mjs`→`stats.json`(μ·σ·상관·베타·사건), `site_calc.mjs`→`calc.json`(배당·KRW=X 지표), `check_calc.mjs` 검사 |
| `macro.yml` | `40 3 5 * *` | `macro.py` → `data/macro/rates_earnings.json`, `sp_monthly.json` |
| `sample.yml` | `10 0 * * *` | 가상 DRNK 시세 생성 + Playwright로 `sample_calc.json` |
| `worker.yml` | 수동 | wrangler로 워커 배포 |
| `ios.yml` / `usage.yml` | push 경로 필터 | iOS 빌드, 웹↔Swift 몬테카를로 parity |

### 외부 API
- Yahoo Finance(시세, 검색): 수집기·브라우저·워커 `?url=` 프록시·앱 테스트 전용 국내 시세.
- smbs.biz(서울외국환중개 매매기준율): `server.py`, 워커 `/mar`.
- shillerdata.com(금리·이익), Google Gemini(워커 `/ai`), GitHub API(collect 깨우기, 개발자 동기화), Apple StoreKit(JWS 검증), api.anthropic.com(`/cu`, 사용량 앱).
- Tiingo: 앱에 `TiingoProvider` 자리만 있음(`TIINGO_TOKEN` 비어 있음).

### 워커 경로 (`proxy/cloudflare-worker.js`)
`/?url=`(Yahoo 프록시), `/ai`, `/views`, `/like`, `/comments`, `/push/*`, `/mar`, `/collect`, `/cu`, `/sync`(개발자), `/esync`(사용자 암호화 동기화), `/scores`, `/store/*`. `scheduled()`가 15분마다 collect를 깨우고 미국 장중 푸시 알림을 확인.

### 저장 방식
- 공개 저장소 `data/`: 시세·환율·통계 (Tiingo 계약 시 원시 시세는 공개 저장소에서 빼야 함, decisions 참고).
- 사용자 데이터: 웹 localStorage, 앱 UserDefaults·App Group 파일, 기기 간은 워커 KV `e:<id>`(PBKDF2-SHA256 30만 회 + AES-GCM, 서버는 내용을 모름). 로컬 서버는 `data/state.json`(gitignore).
- 워커 KV(`VIEWS`): 조회수·좋아요·댓글·푸시 구독·동기화·순위·상점 데이터.

## 5. 계산이 있는 곳과 웹·앱 중복
| 계산 | 웹 | 앱 | 위젯 | CI 검사 |
|---|---|---|---|---|
| 평가액 | `valuation`, `fxBase`, `curPrice` (app.js) | `AppModel.total`, `Market.valFx` | `Engine.build` | **있음** (`calc_parity`: 환율 고르기·행 평가액) |
| 일간 변화 | 종목별만 (`dayChg`) | `todayMove` | `Engine.build` | 없음 |
| 몬테카를로 전망·목표 확률 | `model.js simulate/reweight`, `buildModelNow` | `MonteCarlo.swift`, `ForecastEngine` | 앱이 쓴 스냅샷 | **있음** (`mc_parity`, 같은 입력일 때만) |
| 내 길(lots) | `recordLot`, `history`, `actualSeries` | `Lots.record`, `AnalysisDetail.pathSeries` | 3개월 전 고정 시작 | 없음 |
| 국내 ETF 추정 | `synthKR` (`data/kr_proxy.json`) | `KoreaEstimate.synth` (규칙 하드코딩) | 앱 값 고정 | `krNorm/krAt`만 |
| 외부 요인·베타 | `factorBetas` (실시간 계산) | `SiteCalc.beta` (stats.json) | - | 없음 |
| 배당 | `dividends`, `divGrowth` | `SiteCalc.project` (calc.json) | - | **있음** (`calc_parity`: 화면이 쓰는 환율 포함), `check_calc`는 calc.json |
| 환율 영향 | `fxInfo` | `FxImpactView` | - | 없음 |
| 세금(지금 모두 판다면) | app.js 배당·세금 섹션 | `AnalysisDetail` `capGainTax` | - | **있음** (`calc_parity`, 미국 종목·실현 이익 0만) |
| 금리 시나리오 | `rateScenDraw` | `RateScenarioView` | - | **있음** (`calc_parity`: 식단 띠·목표 확률 이동, 기간 3년만 같음) |
| 동기화 키 유도 | `esDerive` | `Sync.derive` | - | 없음 |
| 회복 경로·목표 경로·평정·평행 세계 | 없음 | 앱 전용 | - | - |

## 6. 웹·앱 계산 기준이 갈라지는 지점 (중요)

**정해진 기준 (2026-10-14 사용자 결정)**: 평가액·배당·세금·환율 영향 모두 **매매기준율**(토스증권 화면과 맞춤). 일간 변화는 **환율 변화를 빼고** 같은 환율로 비교. 아래에서 이 기준과 다른 곳은 "→ 고칠 곳"으로 표시했습니다.

과거 숫자 불일치의 원인은 대부분 **공식이 아니라 입력 차이**였습니다(묵은 종가, 묵은 매매기준율, 현금 누락, 경로 수 상한, 앱 쪽 묵은 lots, 렌즈·추세 신뢰 설정). PR #121~#131에서 맞췄습니다. 남아 있는 갈림길:

**환율 기준**
1. 평가액: 웹·앱 모두 "5일 이내 매매기준율, 아니면 실시간 환율". 다만 5일 판정이 웹은 `yearsBetween*365 ≤ 5`, 앱·위젯은 KST 날짜 차이 `≥ -5`.
2. 전망 시작값 환율: 웹은 매매기준율 또는 **실시간** 환율, 앱은 매매기준율 또는 **전일 종가** 환율(`forecastJob`). 매매기준율이 있으면 같음; 대체값만 다름.
3. 배당·환율 영향·세금: 웹은 `fxBase`(매매기준율), 앱도 `Market.valFx`(매매기준율)로 맞춤(2026-10-14). 환율 영향 화면의 "지금 원/달러"와 앞날 범위는 웹(`fxInfo`의 `fxNow`)·앱 모두 실시간 환율. 외부 요인의 환율 충격(`ExternalView.move`)은 아직 `fx.last`로 나눔.
4. 위젯 환율 대체값은 하드코딩 1350.

**시세 기준**
5. 웹 `curPrice`는 실시간 시세가 마지막 종가보다 오래되면 종가 사용, 앱은 비교 없이 실시간 우선. 앱만 Tiingo 경로가 있음.
6. 전망 시작 가격은 양쪽 모두 사이트 마지막 종가(웹 `snapPx`/`S.siteClose`, 앱 `forecastJob`).

**설정·모델**
7. 앱은 `rebalance_yearly`, `conservative_mu`, `prior_tau`, `history_years`, `new_listing_vol`을 지원하지 않음.
8. 웹 시나리오가 conservative/history/trend면 앱은 blend로 계산(`scenarioMu`).
9. 베타: 웹은 브라우저에서 실시간 계산, 앱은 하루 1번 만든 stats.json. stats.json 종목 값은 기본 설정으로 만들어져 사용자 설정은 모르는 종목에만 반영.
10. 경로 수: 앱 자체 전망 최대 10,000, 비교용 3,000. 웹은 선택 안 된 시나리오 1,500.
11. 앱 회복 루트 전망 기간은 목표일이 아니라 36개월.
12. 몬테카를로가 끝나기 전 앱은 닫힌 식(log-normal) 값을 먼저 보여 줌. 여기서 국내 종목은 상관을 기본값으로 씀(`SiteStats.key` vs `mcKey`).

**화면별**
13. 일간 변화: 앱 `todayMove`와 홈·잠금 위젯 모두 전일·지금을 같은 환율(평가액 환율)로 비교해 환율 변화 제외(결정된 기준, 2026-10-14 앱 수정). 인물 위젯은 앱 값 사용. 웹에는 전체 일간 % 없음.
14. 현금: 웹·앱 홈은 포함, 위젯은 항상 0.
15. 위젯 스파크라인·목표 시작값: 오늘 수량 × 과거 가격 × **오늘 환율**, 시작일 3개월 전 고정, 회복 루트 목표일 +9개월 고정. 앱 내 길은 lots × 과거 종가 × 그날 환율.
16. 내 길: 가격 이력이 없는 종목을 웹은 빼고, 앱은 현재가로 더함.
17. 세금: 웹은 실현 이익을 합산하지 않고 원화 외 전 종목, 앱은 입력한 실현 이익을 더하고 USD 종목만. 환율은 매매기준율로 통일함(2026-10-14). 실현 이익 포함 여부는 미정.
18. 금리 시나리오: 앱은 기간을 3년으로 고정해 나누고 목표에서 현금을 빼지 않음. 웹은 목표일까지 기간과 목표 − 현금.
19. 주간 범위: 웹은 패턴 모델, 앱·위젯은 닫힌 식 σ/√52.
20. 국내 ETF 규칙: 웹은 `data/kr_proxy.json`, 앱은 코드에 하드코딩(파일이 바뀌면 어긋날 수 있음).

**CI 검사 (2026-10-14 넓힘)**: `mc_parity`는 같은 입력에서 몬테카를로 공식을 비교합니다. `calc_parity`(`tools/calc_parity.mjs` + `ios-app/tools/calc_parity`)는 웹 `app.js`와 앱 Swift 원본에서 함수·줄을 그대로 꺼내 같은 입력으로 돌립니다: 환율 고르기(`fxBase` ↔ `valFx`, 매매기준율 0~30일), 평가액, 배당(화면이 쓰는 환율 식 포함), 세금, 금리 시나리오, 위젯 필요 경로(`goalPath.at` ↔ `Snapshot.need`, PR #170 뒤). 아직 못 고친 차이는 도구의 `KNOWN`에 적어 경고만 합니다(지금: 금리 기간 3년 고정, 18번). 두 검사 모두 `ios.yml` parity 잡이 PR·push와 stats·macro 작업이 끝난 뒤(`workflow_run`)에 돌아 봇이 올린 `data/stats.json`·`calc.json`·금리 식단 변경도 봅니다. 남은 한계: 세금의 실현 이익·미국 밖 외화 종목(17번, 미정), 환율 영향 화면, 설정 매핑(7·8번), 국내 ETF 규칙(20번)은 검사하지 않습니다. `check_calc`는 웹 함수와 calc.json을 비교합니다.

## 7. 동기화 범위 (웹 ↔ 앱)
- 앱이 올리는 것: holdings, `app`(앱 전용 상태), goal(목표 루트일 때), `model.scenario`·`trust`(웹 시나리오가 blend/base/smooth일 때만), daily, lots, kr_log.
- 앱이 읽는 것: holdings, kr_log, goal, events, model(scenario, trust, n_paths, seed, t_dof, earnings_adjust, fx_drift, fx_vol_mult, prior_mu, default_vol, default_corr), cash(읽기 전용).
- 동기화 안 됨: 웹 `ui`, `rebalance_yearly`, `conservative_mu`, `prior_tau`, `history_years`, `alloc_target`, trades, weekly. 앱의 세금 실현 이익, 만약에 매달, 정렬.
- 충돌: `at` 기준 마지막 쓰기가 이김. 과거 날짜 lots는 항상 사이트 것을 씀(`Lots.withSite`).

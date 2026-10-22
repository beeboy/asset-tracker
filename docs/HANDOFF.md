# 인수인계 (HANDOFF)

> 마지막 갱신: 2026-10-15 저녁 (새 Claude 프로젝트로 옮기기 전). **새 프로젝트는 이 문서 맨 위 세 절(작업 규칙 → 지금 상태 → 남은 일)부터 읽고 다음 작업 계획을 세운다.**

## 작업 규칙 (Drinker)
- **최우선 규칙**: 사용자가 기다리지 않고 바뀐 것을 바로 빌드할 수 있게 한다. 작업은 맥 폴더(`/Users/drinker/Documents/asset-tracker`)에서 하는 것이 먼저다.
- 한국어로, 쉬운 말로 짧게. 답부터. 인사·중간 보고·묻지 않은 추가 제안 없음.
- 시킨 작업은 묻지 않고 커밋·push·draft PR까지. CI가 초록이면 바로 머지(쌓였으면 아래부터). 사용자가 빌드해 볼 수 있게 된 뒤에 백그라운드로. 답에 커밋 해시·브랜치·PR·머지 여부 한 줄.
- 코드 수정은 별도 worktree가 아니라 맥 폴더(`/Users/drinker/Documents/asset-tracker`)에 바로 쓴다. 고치는 즉시 사용자가 Xcode로 빌드해 볼 수 있어야 한다. 시작할 때 맥 폴더를 최신 main에서 새 브랜치로 바꾸고 거기서 바로 고친 뒤, 고치자마자 "지금 빌드해 보세요"라고 먼저 말한다. 커밋·push·PR·머지는 그 다음에 백그라운드로 하고, 사용자가 기다리게 하지 않는다. 머지 뒤에는 맥 폴더를 최신 main으로 돌린다. 다른 세션의 커밋 안 된 변경은 버리지 말고 그대로 두고 그렇다고 말한다. 사용자에게 git 명령을 시키지 않는다.
- 앱과 웹은 같은 데이터를 같은 규칙으로 쓴다(시세·지난 종가·환율·기록). 한쪽만 못 맞추면 아래 "알려진 불일치"에 적는다.
- 디자인은 논의 먼저: 방향(문구·구성)을 고른 뒤 시안을 만든다. 시안 대신 글로 길게 설명하지 않는다.
- 화면을 바꾸면 localhost에서 먼저 띄워 확인(curl·스크린샷)하고, 같은 PR 하나에서 고쳐 가며 맞춘다. 루트 입구 페이지는 `python3 -m http.server`로 띄운다(`server.py`는 `/web/`용).
- 한 스레드 = 기능·화면 하나, 코드 PR 한 번. 넓은 요청은 나누는 안부터.
- 비밀값은 저장소에 쓰지 않는다. 사용자에게 토큰·개발 설정을 묻지 않는다.

## 지금 상태 (2026-10-15)
- **naeilo.com/** = 입구 페이지(루트 `index.html`·`entry.js`·`entry.css`). 헤드라인 "귀여운 주식 위젯". 맨 위 아이폰 홈 화면 속 수아(자산 추이)·시오(본전 진행) 위젯을 HTML로 그림(금액 가림, 앱 `CharWidgets.swift` 색·배치, 그림 `img/char/`). 수아·시오는 아이폰 **밖**에 서서 말풍선으로 말함(대사는 앱 `lines_ko.json` widget.down에서 고른 9개씩, 열 때마다 하나). 아이폰 상단 시계는 지금 시각(#232). 그 아래 앱 화면 3장(`img/app-home·mypath·settings.png`, 시뮬레이터 9:41. 두 번째는 쉼터 대신 내 길(`-goal plus -tab analysis -an myPath`, 목표 2,100만원). 세 번째는 미션 대신 오픈 API 연결 후 설정 화면, `toss_user`와 친구 5명 열림(`-devAllTest YES`)으로 찍어 첫 장 홈과 목표 2,100만원·함께할 인물 수아가 같음. 홈 금액은 한 줄 고정). "앱 받기" 바로 위에 구독 칸(7일 무료, 월 1,000원·연 9,000원, 열리는 것·계속 무료인 것). 하단 "직접 해보기"는 아이폰 틀 속 목표일 전망: 예시 자산 1,000만·연 7%·흔들림 18% 로그정규 닫힌 식, 목표일 막대를 밀면 가운데 금액·10번 중 9번 범위·목표 1,500만 확률이 바뀜(앱 몬테카를로가 아니라 숫자 기준이 다름, 예시라서 둠). 종목+수량 입력은 뺌.
- **naeilo.com/web/** = 예전 계산기 웹. 지우지 않고 `noindex`(CI 도구가 `app.js` 원문을, parity가 `model.js`를 씀). 화면 parity는 멈춤(입구 결정).
- 앱 받기 버튼 = **출시 알림 신청**(워커 `/notify`, KV `n:<이메일 해시>`, 이메일·날짜만, 목록 `/notify/list`는 ADMIN_KEY). 개인정보처리방침에 한 줄 있음.
- 앱(iOS, TestFlight 내부): 분석 허브 시안 B, 국내 개별 종목, 미션 탭 두 페이지 등. 앱에서 PC 웹으로 보내던 안내는 지움, 기기 동기화 메뉴는 설정에서 뺐고 그 자리에 '토스증권 오픈 API'(2026-10-15, 예전 이름 '내 토스 키'). 설정 맨 위는 오픈 API 카드(2026-10-16: 연결 상태 한 줄 + 내 폰·서버·토스 작은 띠, 연결 전엔 '연결하기' 버튼), 큰 목표 카드는 '내 목표' 작은 줄 두 개(목표 고치기 · 루트 바꾸기)로 줄였다. 홈 평가액 금액은 어떤 길이든 한 줄(넘치면 글자 축소, 2026-10-16).

### 2026-10-15 머지한 PR (#218~#232)
| PR | 내용 |
|---|---|
| #218~#223 | 웹 쉬운 화면 1~5단계: 탭 4개(홈·종목·분석·설정), 홈 첫 질문(플러스/마이너스 길), 홈 카드 결론 한 줄+자세히, 목표일 전망 앱과 같게, 종목 탭 앱과 같게, 배당·세금·환율 앱과 같게 |
| #224 | 웹 홈 길 선택(회복·목표)을 앱과 동기화 (상태 맨 위 `route`) |
| #226 | 웹 국내 주식 합계를 지우고 앱처럼 종목별로 |
| #227 | 웹 입구 페이지 기획 (`docs/plans/web-entrance.md`) |
| #228 | 웹 정리: `web/` noindex, 앱의 PC 웹 안내 지움 |
| #229 | 입구 페이지: naeilo.com/ 을 앱 소개 + 해 보기 + 출시 알림 신청으로 |
| #230 | CLAUDE.md: 클라우드 세션도 읽도록 공통 작업 규칙 |
| #231 | 입구 첫 페이지 시안: 귀여운 주식 위젯 + 아이폰 속 목표일 전망 |
| #232 | 입구: 아이폰 상단 시계를 지금 시각으로 |

## 남은 일 (다음 계획은 여기서 고르기)
1. ~~워커 배포~~: 2026-10-15 `/notify` 살아 있음 확인(`/notify/list`가 "관리자만 볼 수 있습니다"). 입구에서 신청 한 번 해 보고 `/notify/list`로 확인만 남음.
2. ~~`/web/` 위 "앱으로 옮겨 가요 · 입구로" 띠~~: 2026-10-15 켬(`web/index.html` 맨 위 `.movebar`, 판 20261015i).
3. **입구 다듬기**: 실제 폰·PC 화면에서 확인, 말풍선·위젯 위치, 공유 미리보기(og.png)를 새 첫 페이지에 맞추기, Google 색인 다시 요청.
4. **앱 남은 결정**: 분석 허브 내 길 '처음 닿는 때' vs 목표일 확률 차이, 내 길 상세 '반반이에요' 문구.
5. **실기기 확인**: 앱 시작 속도(`Market.refresh()` 병렬화), 미션 예보 카드, 위젯 일간 변화·현금, 위젯 ↻ 누르면 바로 '받는 중'(…)으로 바뀌는지(2026-10-16, `Store.reloadingSince`), 인물 위젯 ↻ 때 금액이 새 시세로 바뀌는지·인물 흔들림·숫자 플립(2026-10-16, `Engine.liveHoldings`, `charShake`·`charFlip`), 홈·잠금 위젯 ↻ 때 숫자 플립·그래프 흐려짐(2026-10-16, 공용 `WidgetShared/Flip.swift` `flip`·`flipFade`, 인물 위젯 네 개 전부 숫자 플립·그림 흐려짐과 인물 뛰어올랐다 통통 착지(2026-10-16), 서재 위젯 ↻ 버튼·글 흐려짐·인물 뛰기(2026-10-16)).
6. **출시 전**: 키 등록 전 화면을 가상 시장 샘플(`data/sample/`, `tools/sample_market.py`, 2026-10-15 만듦·아직 연결 안 함)로 바꾸기(앱 `Sample`·`YahooSample`, 웹 첫 방문 샘플 QQQ→NXIX, `sample.yml`에서 날짜 맞추기), 국내 실시간 시세 스위치 끄기(`KoreaEstimate.live`), `StubPriceProvider` 대체, 무료/프리미엄 분리(`premium()`), TestFlight 외부 테스트용 재아카이브·IAP 등록, 시세 재배포 조건(Tiingo·EODHD 보류).
7. **운영 확인**: 워커 `/cu` 401(Claude 사용량 위젯), 15분 collect cron 실제 동작.
8. ~~**커피 한 잔 → 구독 재편**~~: 2026-10-16 앱 코드 반영(`Data/Support.swift` 구독, `SupportView` 구독 화면, 위젯 `Gate`가 `Store.widgetsOpen`, 분석 내 길·전망 `SubLocked`, 열림 시간은 `Support.dayN` 기준 `AppModel.friendsOpen`·`itemsOn`·`chapterOn`, `bgOpen`). 워커 `/store/book`은 `sub.*` 구독으로 확인(재배포 필요). 남은 일: App Store Connect 구독 그룹·상품 2개 등록(`com.naeilo.widget.sub.monthly` 1,000원 P1M, `com.naeilo.widget.sub.yearly` 9,000원 P1Y), 워커 재배포. 개인정보처리방침은 구독 화면 링크(`https://naeilo.com/web/privacy.html`)와 구독·판 깨기 기기 저장 문구로 맞춤(2026-10-16), App Store Connect 개인정보처리방침 URL 칸에도 같은 주소 넣기.
9. ~~**'토스' 문구 빼기**~~ 2026-10-16 끝: 앱 화면 '토스'→'오픈 API'(코드 이름·주소는 그대로), 앱 시작 3단계의 3단계를 '오픈 API 연결하기'로(키 있으면 '연결했어요', 없으면 설정 오픈 API 화면으로 가기 또는 '샘플로 계속하기').
10. **다른 증권사 오픈 API 검토**: 국내 증권사 먼저, 그다음 해외 포함. 연결할 수 있는 곳은 출시 전에 중계 서버(`proxy/`)에 미리 세팅해 둔다.
11. **출시 알림 목록 페이지** (외부 공개 테스트 전, 2026-10-16 결정): 워커 `/notify/list`를 볼 화면. 모습은 만들 때 Drinker와 먼저 정하기.
12. **미국 출시** (2026-10-16 계획): 첫 출시는 대한민국만. 영어판 준비 뒤 미국 추가. 할 일: 앱 안 문구 영어화(코드, 새 스레드), 구독 2개·구독 그룹 영어(미국) 현지화, 앱 정보 영어 설명·스크린샷, 앱·구독 '사용 가능 여부'에 미국 추가.
- 열린 PR: #225(초안, 미래 설계 글 파일 이름 `data/beyora.json`→`data/blog.json`) 아직 안 합침.
- 멈춘 채 남은 스레드(맥 세션이 꺼져 대기 중, 새 프로젝트에서 이어 하지 않아도 됨): "내일로 입구 페이지 기획"(앱 받기 버튼 결정 → 출시 알림 신청으로 결정됨), "웹 정리"(띠 켜기 남음 = 위 2번), "naeilo 첫 페이지 시안"(#231로 반영됨).

### 알려진 앱·웹 불일치 (오늘 기준)
- 웹은 국내 종목 지난 종가로 `kr_daily.json`을 안 쓰고 EWY×원/달러로 그림(앱은 `kr_daily.json`). 웹 화면 parity는 멈췄지만 데이터 규칙은 맞춰야 함.
- 입구 직접 해보기 숫자(닫힌 식, 예시 자산)는 앱 몬테카를로와 기준이 다름(의도된 예시).
- 시세 출처: 웹은 Yahoo(사이트 `data/`) 시세만 씀(2026-10-15, 앱·웹 동기화 없음 → 웹 토스 경로 `tossQuotes`·`?toss=` 뺌). 앱은 내 키 토스 시세·일봉·환율을 먼저 쓰고 없으면 Yahoo.
- 아래 "3. 알려진 버그·불일치" 5~8번 그대로.

## 아이폰 기기 옮기기 되살림 (2026-10-21, 로컬 커밋)
- 설정 '도움과 기타'에 "기기 옮기기" 줄(route .sync → SyncView). 화면 순서: 파일로 옮기기 카드 → (충돌 카드) → 기기 동기화 카드 → 개발자용. 웹·사이트 문구는 뺐다(폰끼리만).
- 파일 형식(Backup.swift, JSON, 암호화 없음, 이름 `naeilo-yyyy-MM-dd.json`, 날짜 ISO8601, 키 정렬): `v`(1), `at`, `holdings`, `progress`, `homeFriend?`, `custom`(추가 종목), `hist`(추가 종목 시세), `daily?`(내 기록), `town?`(town.book·town.log Data, town.candy·SurfCloset 키는 정수 문자열). 불러오기: 추가 종목 → 진행 → 내 기록 병합 → 동네 → 종목 순.
- 기기 동기화(/esync 워커, 같은 비밀번호)는 켠 사람만. 값이 바뀌면 `Sync.changed`는 표시(dirty)만, 앱이 백그라운드로 갈 때 `Sync.flush`가 한 번 올린다(beginBackgroundTask). 앱을 열 때 받기(pull)는 그대로, 실패하면 다음 열 때 이 폰이 더 새면 올린다.
- 안드로이드도 같게 맞춤(2026-10-21, 로컬 커밋): 설정 '도움과 기타' "기기 옮기기" 줄, SyncView 같은 순서·문구, `Sync.changed`는 dirty만, `MainActivity.onStop`에서 `Sync.flush` 한 번(MainScope). 파일 형식은 json_parity(Backup 56/56), 동기화 암호는 num_parity d(다름 0)로 아이폰과 서로 읽힘 확인.
- 새 문구 영어는 안드로이드만 `tools/l10n/android_en.json`에 넣음(33개). 아이폰 Localizable.xcstrings는 다른 작업이 고치는 중이라 아직 — 그 작업이 끝나면 같은 영어를 xcstrings에도 옮길 것.
- port_check: 아이폰 커밋으로 밀린 `// ios:` 줄 번호(SettingsDetail·SettingsView·App·Sync.swift)와 exceptions.txt를 함께 고침 (짝 없음 0).

## 안드로이드 키보드 가림·중계 IP (2026-10-21, 로컬 커밋)
- 키보드가 입력칸을 가리던 것: edge-to-edge 라 adjustResize 가 안 먹음 → 공통 두 곳만 고침. `RootView` TabHost 에 ime 인셋(내비 막대·탭 막대 49 뺀 만큼, 배치 단계에서만 읽음), `Sheet` 바깥 상자에 ime 인셋. FullCover·StartView 는 원래 safeDrawing(ime 포함)이라 그대로. 동네 말 걸기(kbTop)는 창 좌표라 겹치지 않음.
- 에뮬레이터 확인: 개발자 토큰 칸·오픈 API client_secret 칸이 키보드 위에 보이고 스크롤됨. 숫자 칸은 에뮬레이터 Gboard 가 떠 있는(floating) 키패드라 인셋 0 — 붙은 키보드에서 다시 볼 것.
- 오픈 API 중계 IP: `Config.tossRelayIP` 고정값을 바로 보여 주고 `/api/toss/ip` 로 받은 값이 오면 바꾼다(받기 실패해도 빈칸 없음). 아이폰은 아직 받기만 함.

## 안드로이드 최종 점검 남은 일 (2026-10-21)
- 숫자 입력칸: 키보드가 0.몇 초 떴다가 바로 사라짐 (flip4, 원인 아직 안 봄).
- 위젯 인물 고르기·정기 갱신은 이미 들어가 있음(정기 갱신이 위젯을 나중에 놓을 때 끊기던 빈틈은 Refresh.ensure로 고침). CharItem·CharTop은 아이폰에서도 안 쓰는 코드라 안 옮김. 남은 것: 접근성 설명(contentDescription) 영어.
- 잠금화면 위젯 4개는 보류 (decisions.md).
- 기기 동기화는 실기기 두 대로 사용자 확인 끝.

## 차트 탭 '흐름' (2026-10-21, 로컬 커밋)
- `Views/FlowView.swift`(화면·차트·이름표 자리), `Data/FlowData.swift`(DRNK 샘플·추세·지지저항·패턴·실데이터 캐시·알파카). 탭 이름은 `FlowTab.name` 한 곳, 탭 자리는 '종목' 옆(드링커님 결정, 아이폰은 미션·설정이 '더 보기'로).
- 실데이터: 토스 키 → `TossHistory.fetch`가 같은 일봉 응답에서 시가·고가·저가·거래량도 `flow_toss.json`에 남김(처음 한 번 3년치). 미국은 토스 캔들이 없을 때만 알파카 `/v2/stocks/bars` feed=sip, adjustment=raw → `flow_alpaca.json`, 하루 한 번·호출 사이 0.35초. 담은 종목만, Yahoo 안 씀.
- 확인 못 한 것(키가 아이폰에만 있음): 토스 캔들의 `openPrice·highPrice·lowPrice·volume` 이름과 값, 알파카 무료 키로 feed=sip 일봉이 막히지 않는지(끝 시각을 20분 전으로 둠), 실데이터 패턴이 너무 많거나 적은지.
- 패턴: 더블바텀·더블탑·헤드앤숄더·상승/하락삼각형·플래그(종가로 견주고 확정된 것부터 겹치지 않게), 캔들 5종은 일봉에서만, 월봉은 패턴 없음. DRNK 샘플엔 하락삼각형이 안 나옴.
- 남은 일: 안드로이드 옮기기(이번엔 안 함), 이름표 영어 폭은 짧은 말로 맞춤(Levels·Patterns·Candles).
- 안드로이드(2026-10-21): `data/FlowData.kt`, `ui/flow/FlowView.kt`, `net/Market.kt` 토스 캔들, `App.kt` Tab.flow(종목 옆, 순서는 Tab enum 한 곳), `res/drawable/tab_flow.xml`. port_check 짝 없음 0·selftest 20/20(씨앗 5), 숫자 비교 `tools/num_parity` flow 단계(DRNK·거꾸로 판 11,964칸 차이 0, 끝자리 1e-12 허용 194). 아이폰과 다른 점: 탭 6개가 다 보임(아이폰은 '더 보기'), 한 손가락 세로 끌기는 화면 스크롤로 넘김, 추세·패턴 계산은 화면 밖(Default)에서 미리.
- 안드로이드 확인 못 한 것: 에뮬레이터 핀치(adb 로 두 손가락을 못 줌), 키 있음 화면(출시판이라 키 표시를 못 켬), 실데이터.

## 내 몫 실적 1단계: 데이터 봇 (2026-10-22)
- 계획: /mnt/project-files/planning/my-share-earnings-plan.md (프로젝트 폴더). 결정은 decisions.md 2026-10-22 줄들.
- `tools/earnings.py`: SEC companyfacts·submissions → `data/earnings/{종목}.json`·`index.json`. 대상은 data/tickers.json 중 CIK 있는 회사(ETF 건너뜀). `--check` 는 PR 검사.
- `tools/earn_calc.mjs`: 웹·앱 공통 계산 기준. `tools/earn_check.mjs` + `earn_fixtures.json` 이 기대값 검사(아이폰·안드로이드도 같은 픽스처로 맞춘다).
- `.github/workflows/earnings.yml`: 하루 한 번(UTC 1:10) 받아 커밋, PR 에선 검사만.
- `data/earnings/DRNK.json`: 손으로 쓴 견본(가상 종목).
- 남은 일: 국내 DART(키는 Actions 시크릿)는 아직 없음. 해외 기업(20-F, IFRS)은 줄표. SEC 는 연락처 있는 User-Agent 를 바라므로 Actions 시크릿 `SEC_UA`("이름 이메일")를 넣으면 좋다.
- 맥 검사(2026-10-22): earn_check 3/3, `earnings.py --check` 통과. 고친 것: SEC 가 이메일 없는 User-Agent 를 403 으로 막아 기본값에 연락처를 넣음, companyfacts 404(SPY·MANA)는 건너뜀, 가중평균 희석 주식 수 대체가 늘 비던 버그(SPCX). 알려진 점: 테슬라는 인도량 8-K 도 2.02 라 7~9월 전 분기 발표일이 7-02 로 잡힘(실제 실적 7-22), GLD 는 1갈래로 나옴. → 둘 다 decisions 10-22 로 고침(마지막 2.02, 신탁 6221 건너뜀).

## 내 몫 실적 2단계: 아이폰 강 팝업·목록 막대 (2026-10-22)
- `Data/Earnings.swift`: earn_calc.mjs 와 같은 계산 + 받기·캐시(`earn_{종목}.json`, 20시간에 한 번, 404 는 자료 없음) + DRNK 견본(앱 안). 검사 `ios-app/tools/earn_parity`(픽스처 3건 + DRNK 견본 = data 파일).
- `Views/EarningsRiver.swift`: EarningsFeed(Market.refresh 에서 같이 받음), 목록 막대 EarnBars, 첫 안내 EarnTip(AppStorage earn.tipClosed), 강 팝업 EarningsRiverSheet.
- HoldingsView 행: 왼쪽 로고·이름·화살표 = 상세, 가운데 평가액·막대 = 강 팝업. 밀어 지우기·contextMenu 는 행 전체에 그대로.
- 확인한 것: 명령줄 빌드 성공, 강 그림(흑자·적자·대기, 밝게·어둡게)을 ImageRenderer 로 뽑아 봄. 못 한 것: 시뮬레이터에서 목록 누르기·밀어 지우기 손 검사(패널 권한 대기), 시안 판16 원본과 좌표 대조(맥에 파일 없음), 새 문구 영어 번역(Localizable 추출 안 함), 안드로이드.
- 웹 입구 `#tryEarn`(index.html·entry.js·entry.css, 2026-10-22): 종목+수량 → 강 SVG(EarningsRiver.swift 판16 좌표·색·동작). 계산은 `tools/earn_calc.mjs` 를 동적 import(같은 함수), 원화는 `data/prices/KRW_X.json` 끝값, 자료 없으면 강 없이 안내. 정한 것: 전망 해보기 아래 별도 구역으로 둠, 화면에 들어올 때 한 번 흐름. server.py 는 web/ 만 내보내서 확인은 루트 `python3 -m http.server` 로 함. 남은 차이: 세리프 한글 글꼴은 브라우저 대체(AppleMyungjo 등), 물안개 흐림 정도 근사.

## 내 몫 실적 3단계: 손실 막대·홈 카드·실적 일정·안드로이드 (2026-10-22, earnings-bot 로컬 커밋)
- 손실 막대: 목록·홈 작은 막대에서 순이익<0 분기는 절댓값 높이 규칙 그대로 속 빈 테두리(같은 색 1.2). 작년 쪽 손실도 같음.
- 홈 카드 `EarnHomeCard`(HomeView, 노란 띠 바로 아래): fresh(pending 또는 오늘 발표) 보유 종목마다 "{이름} {months}월 실적" + 막대 둘 + "작년보다 −43%"(netYoY 가 "작년과 같음"이면 그 글 그대로, 없으면 막대만). 누르면 강 팝업. 나온 종목 없으면 카드 없음.
- 실적 일정 `MarketEvents.live`/`earnRule`: 미국 종목 e_ 는 실적 자료(받은 것→앱 안 견본) 우선 — pending 날, 마지막 발표가 오늘 이후면 그 날, 아니면 다음 분기의 작년 짝(끝 날짜 = 마지막 끝 + 91일 − 365±25) 발표일 + 364일에 "{종목} 실적 무렵". DRNK·국내·자료 없음은 손 값. 동네 행사(earnDay)도 live 를 씀. 견본 기준 애플 10-29 무렵, 엔비디아 11-18 무렵.
- 안드로이드: `data/Earnings.kt`(계산·받기·캐시·DRNK·견본, EarningsFeed 는 Market.refresh 에서 같이), `ui/hold/EarningsRiver.kt`(EarnBars·EarnTip·EarningsRiverSheet·강 Canvas 시안 판16 좌표·색·동작), 목록 행 오른쪽(평가액·막대) = 강 팝업·이름 옆 금색 점, 홈 카드, Remind live. 견본 earnings_seed.json 은 빌드 때 아이폰 Resources 에서 복사. 검사 `EarnParityTest` 픽스처 3건 + DRNK 통과, assembleDebug 성공.
- 정한 것/남은 차이: 안드로이드 물안개 흐림(blur 10)은 가장자리로 옅어지는 둥근 무늬로 근사(BlurMaskFilter 하드웨어 가속 문제 피함). 세리프는 FontFamily.Serif. port_check 줄 이름표는 새 파일에 함수 단위로만 달아 `port_check.py Naeilo/Views/EarningsRiver.swift` 는 아직 짝 없음 다수(줄 단위 이름표는 다음 이식 점검 때). 아이폰 쪽 Remind 줄이 17줄 밀려 기존 안드로이드 `// ios: Data/Remind.swift:5xx` 이름표도 어긋남. 화면 손 검사(에뮬레이터·시뮬레이터 캡처 비교)·새 문구 영어 번역 안 함.

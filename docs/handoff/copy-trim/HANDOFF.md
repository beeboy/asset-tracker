# 인수인계: 화면 글줄이기 (2026-10-18)

## 무엇을 했나
- "naeilo 화면 조정" 세션에서 앱 캡처 40장을 디자인 작업창에 올리고, 화면마다 글을 줄인 `· 시안 1`을 만들어 사용자 확인을 받았다.
- 작업창: https://claude.ai/artifact/KXZmfWGCSD5WUgtJ3mTUTt (원본 판 왼쪽이 시안)
- 시안 원문: `docs/handoff/copy-trim/drafts/*Draft.dc.html` (HTML 안의 글이 최종 문구)
- 규칙: 스킬 `naeilo-copy-trim` (`~/.claude/skills/naeilo-copy-trim/SKILL.md`). 작업 전에 반드시 읽는다.

## 시안 → 코드 대응 (추정, 작업 전 확인)
| 시안 파일 | 화면 | 코드 (ios-app/Naeilo/Views) | 맡은 스레드 |
|---|---|---|---|
| HomeDraft | 홈 | HomeView.swift | A |
| StocksDraft | 종목 | HoldingsView.swift | A |
| AnalysisDraft | 분석 | AnalysisView.swift | A |
| MissionDraft, MissionGoalDraft | 미션 (이번 주 예보, 목표 진행) | MissionViews.swift 등 | A |
| MyPathDraft, OutlookDraft, RateShockDraft, EvidenceDraft, ExternalDraft, FXDraft, StockGlanceDraft, DividendDraft, CapGainDraft | 분석 상세 8개 + 근거 | AnalysisDetail.swift, AnalysisView.swift(ForecastView), ForecastParts.swift | A |
| AltWayDraft, BasketDraft | 다른 방식이었다면?, 모의 바구니 고치기 | ParallelView.swift | A |
| SettingsDraft | 설정 | SettingsView.swift, SettingsDetail.swift | A |
| SioRuleDraft, ShelterDraft | 시오 규칙, 쉼터 (+ 세리·선배·이르·수아에게 같은 규칙) | ShelterView.swift(CharacterDetailView 등), 인물 데이터 | B |
| (시안 없음) | 회복 루트 | 직접 찾기 (CalmView, RemindViews, StartView 의 회복 부분 추정) | C |
| GoalEditDraft + (시안 없음) | 목표 고치기, 목표 루트 (플러스, 시작 전) | GoalViews.swift, StartView.swift 의 목표 부분 추정 | D |

- 파일 주인: 위 표에서 맡은 스레드만 그 파일을 고친다. 공유 문구(인물 규칙 데이터 등)는 B, StartView 는 D가 주인. 남의 파일을 고쳐야 하면 멈추고 사용자에게 알린다.
- 웹(`web/`)에 같은 문구가 있으면 같은 스레드가 함께 고친다.

## 사용자 확인 결과 (2026-10-18)
- 종목 엔비디아 300주 · 8,663만: 맞음
- 외부 요인 "금값" 줄: 맞음
- 목표일 전망 "운 나쁘면 지금(4.90억)보다 줄 수도"로 확정
- 쉼터 배경 설명(소설 장면)은 짧게 줄인 상태 — 원문 유지 여부 사용자 확인 필요

## 나중에 코드 수정 (이번 글줄이기와 별도, 사용자가 "코드 수정"이라 할 때)
- 미션 배지가 갱신 안 됨 ("맞혔어요"인데 "맞힌 횟수 0번")
- 환율 영향 화면 제목 밑 빈 흰 띠
- 홈 "산 가격보다 +65...." 잘림
- 홈·시오 규칙의 `**50% → 49%**`, `**20%**` 별표 노출
- 시오 규칙 "약 281만원 더 벌었을" (둘 다 손실 → "덜 잃었을")
- 미션 "40칸 넘게" (4.9억이면 49칸), 공유 카드 "25% 돌파" (지금 49%)
- 내 길 "가능성 반반"과 "달성 확률 30%" 불일치 — 차후 확인
- 목표일 전망 일정 목록이 날짜순 아님 — 차후 확인
- 설정 "가격 기준: 한국 종목은 넣은 가격" vs 종목 화면은 국내 현재가 — 확인 필요

## 주의
- 메인 폴더(`~/Documents/asset-tracker`)에 다른 세션의 커밋 안 된 변경이 있다 (SettingsView.swift, Market.swift 등). 절대 버리지 않는다. 각 스레드는 자기 worktree 에서 작업한다.
- 기록은 로컬 커밋만. GitHub(fetch·push·PR) 연결 안 함. 고치면 바로 "지금 빌드해 보세요".

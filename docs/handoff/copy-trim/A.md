# 글줄이기 스레드 A 결과 (2026-10-18)

시안대로 글만 바꿈. 카드 구성·색·그래프·캐릭터 대사는 그대로.

## 반영한 화면
- 홈 (HomeView, RoutineCard, 다른 방식이었다면? 홈 카드)
- 종목 (HoldingsView, MyRecordCard)
- 분석 (AnalysisView 허브, 목표일 전망 ForecastView·ForecastParts)
- 분석 상세 (AnalysisDetail): 내 길, 금리 충격 테스트, 근거, 외부 요인, 환율 영향, 종목 한눈에, 받는 배당, 낼 양도세
- 미션: 이번 주 예보(BoardView WeekForecastCard), 목표 진행(GoalViews GoalBoardView·GoalCellsCard)
- 다른 방식이었다면?·모의 바구니 고치기 (ParallelView)
- 설정 첫 화면 (SettingsView, Guide.swift 함께할 인물 줄), 루트 바꾸기 화면
- C가 넘긴 회복 분기 문구: 홈 회복 요약, 회복 미션 판(BoardView), 내 길 회복 화면, 루트 바꾸기

## 공통으로 바뀐 것 (다른 화면에도 퍼짐)
- 금액 함수에서 만·억 뒤 "원" 뺌: AppModel.man / eok / wonK / aboutWon → "4억 9,001만", "4.90억", "10억". 1만 미만은 "원" 그대로.
- 날짜: goalToText "2029.10.5", whenText "2030.4", 일정 when "10/22 D-4", 주간 예보 금요일 "10/16".
- 일정 이름 짧게 (Remind.swift MarketEvents): "한은 금리 결정", "미국 FOMC", "미국 CPI", "엔비디아 실적" 등.
- 구성 설명 (GoalRoute mixes desc): "지수 60 · 성장 업종 30 · 현금 10".
- 인물 규칙 한 줄 (Parallel.rules): "현금 20% · 고점 −20%면 절반 투입" 등 5개.
- 과거 사건 이름 (SiteCalc.replays): "2020.3 코로나" 등.

## 못 한 것
- 설정 "Longbridge 키" 줄: 커밋된 SettingsView 에 없음 (메인 폴더 미커밋 변경에만 있음).
- 웹(web/) 같은 문구: 개발자용 웹이라 손대지 않음.
- 시안 없는 하위 화면(알림·위젯·세금 규칙·사용 방법·요인 상세·비중 바꿔 비교)은 그대로.

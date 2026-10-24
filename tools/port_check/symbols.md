# SF Symbols → Material Symbols 대응표

iOS 의 `Image(systemName:)` 을 안드로이드에서 같은 뜻의 Material Symbols(Rounded, 굵기 400)로 바꾼다.
이후 단계도 이 표를 쓴다. 새 기호가 필요하면 표에 한 줄 넣고 `python3 tools/port_check/symbols.py` 를 돌리면
`android-app/app/src/main/res/drawable/ms_<이름>[_fill].xml` 이 생긴다. 쓰는 기호만 벡터로 넣는다 (글꼴 통째로 넣지 않음).
SF 의 `.fill` 은 채움(FILL 1) 모양으로. 코드에서는 `Sym(R.drawable.ms_…, size, color)` (ui/Controls.kt) 로 그린다.

| SF Symbol | Material Symbol | 모양 | 쓰는 곳 |
|---|---|---|---|
| `chevron.right` | `chevron_right` | - | 설정 줄 끝 › · 홈 노란 띠 |
| `lock` | `lock` | - | 홈 잠긴 오늘의 1분 |
| `lock.fill` | `lock` | 채움 | 오픈 API 그림 자물쇠 · 앱 아이콘 잠김 · 위젯 잠김 |
| `arrow.up.right` | `arrow_outward` | - | 설정 언어 변경 |
| `iphone` | `smartphone` | - | 오픈 API 그림 · 기기 동기화 |
| `server.rack` | `dns` | - | 오픈 API 그림 |
| `chart.line.uptrend.xyaxis` | `show_chart` | - | 오픈 API 그림 · 공유 '언덕' |
| `info.circle.fill` | `info` | 채움 | 오픈 API 안내 |
| `key.fill` | `key` | 채움 | 오픈 API 안내 |
| `checkmark.circle.fill` | `check_circle` | 채움 | 위젯 열림 |
| `checkmark` | `check` | - | 거주 국가 고른 줄 |
| `desktopcomputer` | `desktop_windows` | - | 기기 동기화 |
| `arrow.triangle.2.circlepath` | `sync` | - | 첫 질문 '다른 폰 값 불러오기' |
| `square.and.arrow.up` | `ios_share` | - | 미션 위젯 공유 |
| `stairs` | `stairs` | - | 공유 '사다리' |
| `square.stack.3d.up` | `stacks` | - | 공유 '블록 탑' |
| `tv` | `tv` | - | 공유 '전광판' |
| `ruler` | `straighten` | - | 공유 '키 재기' |
| `puzzlepiece` | `extension` | - | 공유 '퍼즐' |
| `camera.macro` | `local_florist` | - | 공유 '꽃밭' |
| `arrow.left.and.right` | `arrow_range` | - | 공유 '줄다리기' |
| `person.3.fill` | `groups` | 채움 | 공유 '다 같이' |
| `plus` | `add` | - | 내 캐릭터 빈 칸 · 종목 추가 |
| `chevron.down` | `expand_more` | - | 종목 정렬 메뉴 |
| `trash` | `delete` | - | 종목 밀어 지우기 |
| `pencil` | `edit` | - | 수량·평균 단가 고치기 |
| `flag` | `flag` | - | 분석 허브 내 길 |
| `calendar` | `calendar_month` | - | 분석 허브 전망 |
| `building.columns` | `account_balance` | - | 분석 허브 외부 요인 |
| `arrow.left.arrow.right` | `swap_horiz` | - | 분석 허브 환율 |
| `chart.pie` | `pie_chart` | - | 분석 허브 종목 한눈에 |
| `arrow.down.to.line` | `download` | - | 분석 허브 배당 |
| `arrow.up.to.line` | `upload` | - | 분석 허브 세금 |
| `chevron.up` | `expand_less` | - | 전망 자세히 접기 |

탭 막대 아이콘(house·chart.bar·chart.line.uptrend.xyaxis·square.grid.2x2·gearshape)은 iOS 탭 막대처럼 채운 모양을 따로 그린 `tab_*.xml` 을 쓴다 (A 단계).
홈 [설정] 단추의 gearshape 는 `tab_settings.xml` 을 그대로, [위젯] 단추의 rectangle.3.group 은 직접 그린 `ms_rect3_group.xml` 을 쓴다 (탭 정리, 2026-10-21).
| `minus` | `remove` | - | 내 길 비교·모의 바구니 비중 − |
| `xmark` | `close` | - | 모의 바구니 종목 빼기 |
| `info.circle` | `info` | - | 종목 한눈에 안내 |
| `bell` | `notifications` | - | 외부 요인 결과 알림 |
| `bell.fill` | `notifications` | 채움 | 외부 요인 결과 알림 켬 |
| `clock` | `schedule` | - | 미션 판 예보 마감 |
| `sun.max.fill` | `sunny` | 채움 | 미션 판 주간 예보 |
| `circle` | `radio_button_unchecked` | - | 미션 판 할 일 |
| `sparkles` | `auto_awesome` | - | 소식 분류 달성·배지 |
| `hourglass` | `hourglass_empty` | - | 소식 분류 진행 중 |
| `newspaper` | `newspaper` | - | 소식 분류 증시 요약 |
| `gift` | `redeem` | - | 소식 분류 새 소식 |
| `star.fill` | `star` | 채움 | 맞혀보기 배지 |
| `medal.fill` | `military_tech` | 채움 | 맞혀보기 배지 |
| `trophy.fill` | `trophy` | 채움 | 맞혀보기 배지 |
| `crown.fill` | `workspace_premium` | 채움 | 맞혀보기 배지 |
| `square.grid.3x3.fill` | `grid_on` | - | 블록 게임 단추 |
| `water.waves` | `waves` | - | 파도 게임 단추 |
| `gamecontroller` | `sports_esports` | - | 게임 단추 |
| `nosign` | `block` | - | 내 캐릭터 부품 없음 |
| `arrow.counterclockwise` | `replay` | - | 내 캐릭터 처음으로 |
| `circle.dotted` | `radio_button_partial` | - | 내 투자 습관 다음 걸음 |
| `play.fill` | `play_arrow` | 채움 | 서재 이어서 읽기·게임 |
| `photo` | `image` | - | 동네 꾸미기 |
| `pause.fill` | `pause` | 채움 | 게임 멈춤 |
| `trophy` | `trophy` | - | 게임 기록 |
| `star` | `star` | - | 파도 별 |
| `heart` | `favorite` | - | 파도 하트 |
| `heart.fill` | `favorite` | 채움 | 파도 하트 |
| `cloud.fill` | `cloud` | 채움 | 파도 하늘 |
| `circle.fill` | `circle` | 채움 | 파도 점 |
| `arrow.clockwise` | `refresh` | 기본 | 위젯 ↻ 다시 받기 (위젯은 Icon.refresh 경로, 빠른 설정 타일 w_ic_refresh) |
| `ellipsis` | `more_horiz` | 기본 | 위젯 ↻ 받는 중 (Icon.more 경로) |
| `hand.tap` | `touch_app` | - | 위젯 넣는 단계 1 |
| `hand.pinch` | `ic_pinch` (직접 그림) | - | 흐름 탭 단위 이름표 |
| `plus.square` | `add_box` | - | 위젯 넣는 단계 2 |
| `magnifyingglass` | `search` | - | 위젯 넣는 단계 3 |
| `square.grid.2x2` | `grid_view` | - | 위젯 넣는 단계 4 |
| `person.crop.square` | `account_box` | - | 위젯 넣는 단계 5 |
| `questionmark.circle` | `help` | - | 흐름 탭 읽는 법 단추 |
| `scope` | `my_location` | - | 설정 (조준경 기호) |

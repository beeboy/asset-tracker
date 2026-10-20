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

탭 막대 아이콘(house·chart.bar·chart.line.uptrend.xyaxis·square.grid.2x2·gearshape)은 iOS 탭 막대처럼 채운 모양을 따로 그린 `tab_*.xml` 을 쓴다 (A 단계).

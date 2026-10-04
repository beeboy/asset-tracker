# 내 자산 관찰·전망

보유 주식의 평가액을 원화로 관찰하고, 목표 금액 달성 확률을 전망하는 도구입니다.
칼만 필터·이동평균 추세 분석, 환율을 포함한 몬테카를로 전망, 기업 사건(실적·규제·보호예수 해제) 반영.
모든 계산은 브라우저에서 하며 인공지능이나 유료 API 를 부르지 않습니다.

## 두 가지 사용 방법

### 1) 웹 (GitHub Pages)
- 주소: `https://<GitHub 아이디>.github.io/asset-tracker/`
- 시세는 GitHub Actions(`.github/workflows/collect.yml`)가 평일 30분마다 Yahoo Finance 에서 받아 `data/` 에 커밋합니다.
- **보유 수량·평균 매수가·목표·사건은 브라우저(localStorage)에만 저장**되고 저장소에는 올라가지 않습니다.
  다른 기기에서 쓰려면 ⑥ 모형 설정 탭의 *입력값 내보내기 → 불러오기*를 쓰세요.
- ⑥ 설정 탭에서 GitHub 토큰(Fine-grained, 이 저장소만, Actions: Read and write)을 한 번 넣어 두면, 종목을 추가하거나 '최신 데이터 불러오기'를 누를 때 화면에서 바로 수집 작업을 실행하고 끝나면 자동으로 반영합니다. 토큰은 그 브라우저에만 저장됩니다.
- 토큰 없이도 Actions → collect → **Run workflow** 의 *추가 티커* 칸으로 종목을 더할 수 있습니다.

처음 한 번 설정:
1. Settings → Pages → Source: **Deploy from a branch**, Branch: `main` / `(root)` → Save
2. Settings → Actions → General → Workflow permissions: **Read and write permissions** → Save
3. Actions 탭 → collect → **Run workflow** (첫 수집)

### 2) 내 PC (오프라인 가능, 현재가 즉시 수집)
파이썬 3.8+ 만 있으면 됩니다 (추가 패키지 없음).
- Windows: `실행-Windows.bat`, Mac: `실행-Mac.command`, 또는 `python server.py`
- 브라우저가 `http://127.0.0.1:8765` 로 열리고, 입력값은 `data/state.json` 에 저장됩니다 (`.gitignore` 로 커밋 제외).
- 자세한 내용은 `사용법.txt`.

## 구성
| 파일 | 역할 |
|---|---|
| `server.py` | 로컬 서버 + Yahoo 수집기 (`--collect` 는 화면 없이 수집만) |
| `web/model.js` | 칼만 필터, EMA, 다변량 t 몬테카를로, 사건 점프 |
| `web/app.js` | 화면 로직 (로컬/웹 모드 자동 판별) |
| `web/charts.js` | SVG 차트 (외부 라이브러리 없음) |
| `data/prices/*.json` | 일봉 (종가, 수정종가) |
| `data/quotes.json` | 최근 현재가 (프리·정규·애프터) |
| `data/tickers.json` | 웹 수집 대상 종목 |
| `data/state.default.json` | 처음 열 때의 예시 입력 (수량 0) |

전망은 확률 모형의 결과이며 투자 권유가 아닙니다. 국내 증권사 주간거래(데이장) 시세는 포함하지 않습니다.

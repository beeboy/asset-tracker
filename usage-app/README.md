# Claude 사용량 위젯 (본인용)

naeilo 와 따로 설치하는 작은 앱. 홈 화면 작은 블록 위젯(5시간 칸 + 주간 탑 + 캐릭터 말풍선)과 잠금 화면 원형 위젯(링 두 개 + 캐릭터 얼굴).
naeilo 코드는 건드리지 않고, 같은 App Group 에서 naeilo 의 '내 캐릭터'(me.json · me.png)만 읽는다.

- 맥: Xcode 로 `usage-app/ClaudeUsage.xcodeproj` 를 열고 ClaudeUsage 를 아이폰에 Run.
- 사용량: naeilo 중계(proxy/cloudflare-worker.js 의 `/cu`)가 받는다. 처음 한 번만 중계 비밀값 `CLAUDE_TOKEN` 에 `claude setup-token` 결과를 넣고 Deploy.
- 파일을 더하면 `tools/gen_pbxproj.py` 목록에 넣고 다시 돌려 프로젝트 파일을 만든다 (CI 가 같은지 확인).

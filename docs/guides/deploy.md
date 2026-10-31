# 배포·서버·백업

## 호스팅
- 입구는 GitHub Pages + Cloudflare DNS다.
- 워커 배포는 Actions → worker → Run workflow 수동이다.
- collect는 워커 cron(15분)이 깨운다.

## 중계 서버 (Lightsail 서울)
- 공용 토스 중계: `proxy/toss_relay.py`, toss.naeilo.com. 키는 저장 안 한다. 자세히는 `docs/toss-relay.md`.
- 공용 중계 IP는 `Config.tossRelayIP`에 둔다.
- 개발자 서버 naeilo-dev(8765)는 `/api/mock`. 머리글 X-Naeilo-Dev가 맞아야 답한다.
- 개발자 열쇠 폰은 중계 1분 한도를 안 센다.
- 서버 주소·키·토큰은 저장소에 안 쓴다. 시크릿에만 둔다.

## 기기 옮기기
- 기본은 파일(JSON) 옮기기다.
- 동기화(/esync)는 켠 사람만. 앱 닫을 때 한 번 올린다.
- 비밀번호 + AES-GCM. 서버는 내용을 모른다.

## 앱 백업 흐름
- 앱 작업은 커밋하지 않는다. HANDOFF.md 백업 줄에 작업 이름과 파일을 적는다.
- "백업해"면 줄마다 그 파일만 작업별 로컬 커밋, 그다음 push·PR·머지.
- 같은 파일을 고친 작업은 한 커밋으로 묶는다.
- 웹 작업은 바로 올리고 CI가 초록이면 머지한다.

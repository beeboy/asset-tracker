@echo off
chcp 65001 >nul
cd /d "%~dp0"
set PY=
where py >/dev/null 2>/dev/null && set PY=py -3
if not defined PY (where python >/dev/null 2>/dev/null && set PY=python)
if not defined PY (
  echo 파이썬이 설치되어 있지 않습니다.
  echo https://www.python.org/downloads/ 에서 설치하세요. 설치 첫 화면에서 "Add python.exe to PATH" 를 꼭 체크하세요.
  pause
  exit /b 1
)
%PY% server.py
if errorlevel 1 pause

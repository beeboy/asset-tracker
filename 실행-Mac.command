#!/bin/bash
cd "$(dirname "$0")"
if command -v python3 >/dev/null 2>&1; then
  python3 server.py
else
  echo "python3 가 없습니다. 터미널에서 xcode-select --install 을 실행하거나 https://www.python.org/downloads/ 에서 설치하세요."
  read -n 1 -s -r -p "아무 키나 누르면 닫힙니다"
fi

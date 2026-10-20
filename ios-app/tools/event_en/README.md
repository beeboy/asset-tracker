# 규칙과 결과 컷 그림 영어판

`event_<인물>.png`(970×980, 컷 5개) 안의 한글을 지우고 영어를 넣어 `event_<인물>_en.png`를 만든다. 앱은 영어일 때만 _en을 읽는다.

    cd ios-app/tools/event_en && swiftc -O en.swift -o /tmp/event_en
    /tmp/event_en ../../Naeilo/Assets.xcassets/event_seri.imageset/event_seri.png seri.json ../../Naeilo/Assets.xcassets/event_seri_en.imageset/event_seri_en.png

`<인물>.json`: 글 자리(495 폭 기준 찾을 칸 r)와 영어 줄·색. 칸 안에서 가장 흔한 색을 배경으로 보고 글 픽셀만 지운 뒤 Silom 12를 2배 도트로 찍는다.

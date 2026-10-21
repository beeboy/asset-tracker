package com.naeilo.widget

import com.naeilo.app.core.Store
import com.naeilo.app.core.WL

/** 위젯 이름표: 이름·설명·크기를 kind 하나로 찾는다. 위젯 추가 화면, 잠긴 위젯 제목, 앱 '위젯' 탭이 모두 여기서 읽는다
 *  위젯 추가 화면의 순서는 매니페스트에 적은 리시버 순서다 (인물 위젯 다음 자산 추이·내 길·목표일 전망·블록·목표 페이스·과녁·오늘의 움직임·서재) */
// ios: Widgets/Views/Catalog.swift:6
object Catalog {
    // ios: Widgets/Views/Catalog.swift:7
    class Item(val kind: String, val name: String, val desc: String, val size: String)

    /** 기본 작은 위젯 7개 + 잠금화면 4개 (앱 '위젯' 탭의 순서). 잠금화면 4개는 안드로이드에 없어 이름표만 둔다 (앱 목록이 아이폰과 같게) */
    // ios: Widgets/Views/Catalog.swift:10
    val base: List<Item> = listOf(
        // ios: Widgets/Views/Catalog.swift:11
        Item(kind = "asset.small", name = "자산 추이", desc = "총자산·오늘 등락·최근 10일 흐름", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:12
        Item(kind = "path.small", name = "내 길", desc = "계획선보다 앞섰는지와 달성 확률", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:13
        Item(kind = "future.small", name = "목표일 전망", desc = "목표일 예상과 지금보다 몇 %", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:14
        Item(kind = "block.small", name = "지금 채우는 블록", desc = "목표를 1000칸으로 나눠 지금 채우는 100칸", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:15
        Item(kind = "pace.small", name = "목표 페이스", desc = "나와 모의 바구니, 누가 빠른지", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:16
        Item(kind = "target.small", name = "이번 주 과녁", desc = "금요일 마감 예측과 지금 위치", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:17
        Item(kind = "moves.small", name = "오늘의 움직임", desc = "비중 큰 종목의 오늘 등락 히트맵", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:18
        Item(kind = "lock.asset", name = "자산 추이", desc = "총자산·오늘 등락·1달 흐름", size = "잠금 화면"),
        // ios: Widgets/Views/Catalog.swift:19
        Item(kind = "lock.goal", name = "목표 진행", desc = "목표 대비 %", size = "잠금 화면"),
        // ios: Widgets/Views/Catalog.swift:20
        Item(kind = "lock.future", name = "3년 뒤", desc = "3년 뒤 예상 범위", size = "잠금 화면"),
        // ios: Widgets/Views/Catalog.swift:21
        Item(kind = "lock.target", name = "이번 주 과녁", desc = "금요일 마감 예측과 지금", size = "잠금 화면"),
    )
    /** 인물 8개 + 서재 */
    // ios: Widgets/Views/Catalog.swift:24
    val more: List<Item> = listOf(
        // ios: Widgets/Views/Catalog.swift:25
        Item(kind = "char.hill", name = "인물 · 자산 언덕", desc = "최근 10일 총자산을 픽셀 언덕으로", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:26
        Item(kind = "char.ladder", name = "인물 · 본전 사다리", desc = "본전(목표)까지 사다리를 오르는 인물", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:27
        Item(kind = "char.tower", name = "인물 · 블록 탑", desc = "100칸이 한 층, 10층 탑을 쌓아요", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:28
        Item(kind = "char.board", name = "인물 · 전광판", desc = "총자산·오늘 등락·큰 종목 2개를 전광판으로", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:29
        Item(kind = "char.height", name = "인물 · 키 재기", desc = "문틀 기둥에 지난 날 자산을 금으로, 오늘은 인물 머리 높이", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:30
        Item(kind = "char.puzzle", name = "인물 · 퍼즐 맞추기", desc = "본전(목표)까지 20조각 퍼즐, 다 맞추면 깃발", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:31
        Item(kind = "char.garden", name = "인물 · 꽃밭", desc = "블록 1개가 꽃 1송이, 100송이면 다음 밭", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:32
        Item(kind = "char.tug", name = "인물 · 줄다리기", desc = "오른 종목과 내린 종목이 당기는 오늘의 줄다리기", size = "작은"),
        // ios: Widgets/Views/Catalog.swift:33
        Item(kind = "story", name = "서재 · 이어 읽기", desc = "외전 『이종 공명』에서 읽던 자리의 한 문단. 누르면 그 장으로 가요", size = "작은"),
    )
    // ios: Widgets/Views/Catalog.swift:35
    fun item(kind: String): Item? = (base + more).firstOrNull { it.kind == kind }
    /** 위젯 추가 화면·잠긴 위젯·말읽기가 쓰는 이름과 설명 (영어 기기면 WL 표의 영어) */
    // ios: Widgets/Views/Catalog.swift:37
    fun name(kind: String): String = WL.t(item(kind)?.name ?: "naeilo 위젯")
    // ios: Widgets/Views/Catalog.swift:38
    fun desc(kind: String): String = item(kind)?.let { WL.t(it.desc) } ?: ""

    /** 위젯을 누르면 앱의 첫 탭(naeilo)으로 */
    // ios: Widgets/Views/Catalog.swift:41
    const val openURL = "naeilo://guide"
    /** 잠긴 위젯을 누르면 구독 화면으로 */
    // ios: Widgets/Views/Catalog.swift:43
    const val subscribeURL = "naeilo://subscribe"
}

/** 위젯 추가 화면의 이름·설명: 안드로이드는 위젯 정보 XML(res/xml/w_*.xml)의 description 과 리시버 label (strings.xml 에 Catalog 와 같은 글) */
// ios: Widgets/Views/Catalog.swift:46,48,49
fun catalog(kind: String): Pair<String, String> = Catalog.name(kind) to Catalog.desc(kind)

/** 7일 무료가 끝나고 구독하지 않은 위젯의 잠금 (기본·인물·서재가 모두 이것 하나로 잠근다)
 *  홈 화면에 놓을 수는 있지만 숫자 대신 잠긴 모습. 누르면 앱의 구독 화면으로 간다.
 *  구독하면 앱이 구독 상태를 다시 쓰고 위젯을 새로 그리게 해서, 같은 자리에서 바로 열린다.
 *  잠긴 모습은 기본이 lockedWidget(Locked.kt), 인물 위젯은 인물 바탕을 지키려고 locked 로 따로 준다.
 *  돌려주는 값: 누르면 갈 주소 (열렸으면 null = 위젯이 정한 주소) */
// ios: Widgets/Views/Catalog.swift:57
class Gate(
    // ios: Widgets/Views/Catalog.swift:58
    val kind: String,
    // ios: Widgets/Views/Catalog.swift:59
    val preview: Boolean,
    // ios: Widgets/Views/Catalog.swift:60
    val content: Pen.() -> Unit,
    // ios: Widgets/Views/Catalog.swift:61
    val locked: Pen.() -> Unit,
) {
    // ios: Widgets/Views/Catalog.swift:63
    val open: Boolean get() = preview || Store.isUnlocked(kind)
    // ios: Widgets/Views/Catalog.swift:62,64
    fun body(p: Pen): String? {
        if (open) { p.content(); return null }
        p.locked()
        return if (Store.widgetsOpen) Catalog.openURL else Catalog.subscribeURL   // 단계로 잠겼으면 앱으로, 구독으로 잠겼으면 구독 화면으로
    }
}

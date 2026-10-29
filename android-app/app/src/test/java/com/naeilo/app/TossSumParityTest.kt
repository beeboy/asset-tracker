package com.naeilo.app

import com.naeilo.app.data.Lot
import com.naeilo.app.data.Period
import com.naeilo.app.net.TossSum
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

// 종목 탭 합산 그래프(토스 기준, 2026-10-29) 숫자 맞추기: 아이폰 TossSum.swift 를 같은 입력으로 돌린 값과 원 단위까지 같은지.
// 입력: QQQ 10주 + 005930 10주, 2026-10-19~28 사이트 종가(data/prices·kr_daily), 서울외국환중개 매매기준율
class TossSumParityTest {
    private val qd = listOf("2026-10-19", "2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23", "2026-10-26", "2026-10-27", "2026-10-28")
    private val q = TossSum.Item("QQQ", true, qd, listOf(718.359985, 717.630005, 725.200012, 704.469971, 704.400024, 712.200012, 721.25, 722.034973), false, 0.0, 10.0)
    private val k = TossSum.Item("005930.KS", false, qd, listOf(218000.0, 211500.0, 223500.0, 214500.0, 215500.0, 213000.0, 222000.0, 242000.0), false, 0.0, 10.0)
    private val mar = TossSum.Rates(listOf("2026-10-19", "2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23", "2026-10-26", "2026-10-27", "2026-10-28"),
        listOf(1342.4, 1332.1, 1328.0, 1325.5, 1324.6, 1324.3, 1322.6, 1324.3))

    @Test fun lines() {
        // 수량 기록 없음: 주말 줄 = 금요일 줄
        val a = TossSum.points(listOf(q, k), emptyList(), mar, Period.m1, "2026-10-28", 99.0).takeLast(11)
        assertEquals(listOf("2026-10-19", "2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23", "2026-10-24", "2026-10-25", "2026-10-26", "2026-10-27", "2026-10-28", null), a.map { it.date })
        // 10/27 은 정확히 11,759,252.5원 (Math.round 는 올림, Swift %.0f 는 짝수 쪽 252): 원값으로 견준다
        assertEquals(11759252.5, a[8].value)
        assertEquals(listOf(11823264L, 11674549, 11865656, 11482749, 11485483, 11485483, 11485483, 11561665, 11759253, 11981909, 99), a.map { Math.round(it.value) })
        // 10/22 에 QQQ 5주 더 삼, 기록 전 날짜는 첫 기록 수량
        val lots = listOf(Lot("2026-10-20", mapOf("QQQ" to 10.0, "005930.KS" to 10.0)), Lot("2026-10-22", mapOf("QQQ" to 15.0, "005930.KS" to 10.0)))
        val b = TossSum.points(listOf(q, k), lots, mar, Period.w1, "2026-10-28", 99.0)
        assertEquals(listOf(16151624L, 16150724, 16150724, 16150724, 16277497, 16528879, 16762864, 99), b.map { Math.round(it.value) })
        // 주마다: 3년 158점, 끝은 마지막 줄 날짜와 지금
        val w = TossSum.points(listOf(q, k), emptyList(), mar, Period.y3, "2026-10-28", 99.0)
        assertEquals(158, w.size); assertEquals(listOf("2023-11-05", "2023-11-12"), w.take(2).map { it.date }); assertEquals(listOf("2026-10-25", "2026-10-28", null), w.takeLast(3).map { it.date })
        // 마지막 줄: 뉴욕 오후 4시 (한국 05:00, 서머타임 끝나면 06:00)
        assertEquals(listOf("2026-10-27", "2026-10-28", "2026-11-01", "2026-11-02"),
            listOf("2026-10-28T19:59:00Z", "2026-10-28T20:00:00Z", "2026-11-02T20:59:00Z", "2026-11-02T21:00:00Z").map { TossSum.lastLine(Instant.parse(it)) })
    }
}

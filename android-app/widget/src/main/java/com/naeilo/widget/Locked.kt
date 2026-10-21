package com.naeilo.widget

import com.naeilo.app.core.WL
import com.naeilo.app.core.WidgetUnlock
import kotlin.math.max

// 잠긴 위젯의 기본 모습 (잠글지는 Catalog.swift 의 Gate 가 정한다)

/** SF Symbols 이름 → 위젯 기호 (tools/port_check/symbols.md) */
private fun lockSym(name: String): Icon = if (name == "lock.fill") Icon.lock else Icon.more

/** 잠긴 모습을 기본(lockedWidget)으로 두는 Gate */
// ios: Widgets/Views/Locked.swift:5,6,7
fun Gate(kind: String, preview: Boolean, content: Pen.() -> Unit): Gate = Gate(kind, preview, content, gateLocked(kind))
fun gateLocked(kind: String): Pen.() -> Unit = { lockedWidget(kind) }

/** 잠긴 위젯. family: 아이폰 widgetFamily (안드로이드는 홈 위젯만이라 늘 systemSmall·systemMedium. 잠금화면 모양은 코드만 옮겨 둔다) */
// ios: Widgets/Views/Locked.swift:11,12,13,15
fun Pen.lockedWidget(kind: String, family: String = "systemSmall") { widgetBG(); lockedFace(kind, family) }

// ios: Widgets/Views/Locked.swift:17,18,19
private fun Pen.lockedFace(kind: String, family: String) { val title = Catalog.name(kind); val x = pad; val cw = w - pad * 2; val y = pad; val bottom = h - pad
    when (family) {
        // ios: Widgets/Views/Locked.swift:20,21,25,26
        "accessoryRectangular" -> { val vs = 2f
            // ios: Widgets/Views/Locked.swift:22
            val th = size(title, 12f, W.bold).height; icon(lockSym("lock.fill"), x + 7, y + th / 2, 14f, ink); text(title, 12f, x + 14 + 4, y, W.bold, maxW = cw - 18, maxLines = 1)
            // ios: Widgets/Views/Locked.swift:23
            text(WidgetUnlock.how(kind), 11f, x, y + th + vs, maxW = cw, maxLines = 2)
        }
        // ios: Widgets/Views/Locked.swift:27,28
        "accessoryCircular", "accessoryInline" -> icon(lockSym("lock.fill"), w / 2, h / 2, 16f * 1.25f, ink)
        // ios: Widgets/Views/Locked.swift:29,30,48,49,50
        else -> { ink = pal.ink; val vs = 6f
            // ios: Widgets/Views/Locked.swift:31,32,33
            val hs = 6f; val th = size(title, 13f, W.bold).height; icon(lockSym("lock.fill"), x + 6, y + th / 2, 12f * 1.25f, ink); text(title, 13f, x + 12 + hs, y, W.bold, maxW = cw - 12 - hs, maxLines = 1)
            // ios: Widgets/Views/Locked.swift:46
            val cta = WidgetUnlock.cta; val ctaH = size(cta, 10f, W.bold).height; text(cta, 10f, x, bottom - ctaH, W.bold, pal.ink.copy(alpha = 0.5f))
            // ios: Widgets/Views/Locked.swift:44,45
            val how = WidgetUnlock.how(kind); val howH = size(how, 11f, W.semibold, maxW = cw, maxLines = 3).height; text(how, 11f, x, bottom - ctaH - vs - howH, W.semibold, pal.ink.copy(alpha = 0.7f), maxW = cw, maxLines = 3)
            // 흐린 칸: 열리면 이 자리에 숫자가 온다
            // ios: Widgets/Views/Locked.swift:35,37,38,43
            val n = if (family == "systemSmall") 6 else 12; val bsp = 4f; val barsH = 10f + 25; val spacer = max(0f, (bottom - ctaH - vs - howH - vs - (y + th + vs) - barsH - vs) / 2); val by = y + th + vs + spacer + barsH; val bw = (cw - bsp * (n - 1)) / n; for (i in 0 until n) {
                // ios: Widgets/Views/Locked.swift:39,40
                val bh = (10 + (i * 7) % 26).toFloat(); rrect(x + i * (bw + bsp), by - bh, bw, bh, 3f, pal.ink.copy(alpha = 0.12f))
            }
        }
    }
}

/** VoiceOver: 잠긴 위젯은 이름과 여는 방법을 한 문장으로 */
// ios: Widgets/Views/Locked.swift:55,57,58
fun lockedSpeech(kind: String): String = WL.t("{name} 잠김. {how}", mapOf("name" to Catalog.name(kind), "how" to WidgetUnlock.how(kind)))

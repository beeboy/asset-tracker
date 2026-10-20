package com.naeilo.app.core

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.sqrt

// ↻ 효과 (모든 화면·위젯 미리보기가 같이 쓴다): 누르면 '받는 중'(Store.isReloading)으로 한 번, 다 받으면 다시 한 번 그려진다

/** SwiftUI interpolatingSpring(stiffness, damping) 과 같은 용수철 (질량 1): 감쇠비 = damping / (2√stiffness) */
private fun interpolatingSpring(stiffness: Float, damping: Float): SpringSpec<Float> =
    spring(dampingRatio = damping / (2 * sqrt(stiffness)), stiffness = stiffness)

// ios: WidgetShared/Flip.swift:4
private val shakeSpring = interpolatingSpring(stiffness = 220f, damping = 6f)   // 덜 멈추는 용수철: 좌우로 몇 번 흔들리다 선다
// ios: WidgetShared/Flip.swift:5
private val hopSpring = interpolatingSpring(stiffness = 180f, damping = 7f)     // 통통 튀며 내려앉는 용수철

/** 숫자: 받는 동안 흐려졌다가, 다 받으면 바뀐 자리가 굴러 들어오며 새 값으로.
 *  (SwiftUI numericText 는 글자 단위 전환이라 Modifier 로는 숫자 전체가 아래에서 굴러 올라오게 한다) */
// ios: WidgetShared/Flip.swift:7,9,10,11,12,13,14
fun Modifier.flip(v: Double): Modifier = composed {
    val busy = Store.isReloading
    val roll = remember { Animatable(0f) }
    val first = remember { booleanArrayOf(true) }
    val dist = with(LocalDensity.current) { 8f * density }
    LaunchedEffect(v) {   // 값이 바뀔 때만 (snappy 0.6초)
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        roll.snapTo(1f)
        roll.animateTo(0f, tween(durationMillis = (0.6 * 1000).toInt(), easing = FastOutSlowInEasing))
    }
    val a by animateFloatAsState(if (busy) 0.35f else 1f, tween((0.25 * 1000).toInt(), easing = FastOutSlowInEasing), label = "flip")
    this.graphicsLayer { alpha = a * (1 - 0.6f * roll.value); translationY = roll.value * dist }
}

/** 그림(차트·막대·칸): 받는 동안 흐려졌다가, 다 받으면 새 모양으로 */
// ios: WidgetShared/Flip.swift:17,18,19
fun Modifier.flipFade(): Modifier = composed {
    val busy = Store.isReloading
    val a by animateFloatAsState(if (busy) 0.35f else 1f, tween((0.25 * 1000).toInt(), easing = FastOutSlowInEasing), label = "flipFade")
    this.graphicsLayer { alpha = a }
}

/** 인물: 받는 동안 몸을 움츠렸다 뛰어올라 왼쪽으로 기울고, 다 받으면 통통 튀며 내려앉아 좌우로 흔들린다 */
// ios: WidgetShared/Flip.swift:22,23,24,25,26,27,28
fun Modifier.charShake(): Modifier = composed {
    val busy = Store.isReloading
    val sx by animateFloatAsState(if (busy) 1.06f else 1f, shakeSpring, label = "sx")
    val sy by animateFloatAsState(if (busy) 0.92f else 1f, shakeSpring, label = "sy")
    val rot by animateFloatAsState(if (busy) -9f else 0f, shakeSpring, label = "rot")
    val ox by animateFloatAsState(if (busy) -5f else 0f, shakeSpring, label = "ox")
    val oy by animateFloatAsState(if (busy) -12f else 0f, hopSpring, label = "oy")   // 위아래는 통통 튀는 용수철
    val d = LocalDensity.current.density
    this.graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 1f)   // anchor: .bottom
        scaleX = sx; scaleY = sy; rotationZ = rot
        translationX = ox * d; translationY = oy * d
    }
}

/** 인물 위젯 숫자 (예전 이름) */
// ios: WidgetShared/Flip.swift:31
fun Modifier.charFlip(v: Double): Modifier = flip(v)

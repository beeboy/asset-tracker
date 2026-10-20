package com.naeilo.app.pending

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.plainClickable

// H2(미션 판·소식함)이 부르는데 아직 옮기지 않은 이름의 자리. 진짜 이식이 오면 이 파일의 해당 줄을 지운다.

// TODO(Views/Wave/WaveHomeView.swift GameHeader 게임 단계): 게임·소식함 맨 위 줄 (왼쪽 굵은 제목, 오른쪽 X 닫기). 모양은 Swift 와 같게 미리 그려 둔다
@Composable
fun GameHeader(title: String, dismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FitText(title, appFont(20f, FontWeight.Bold), 0.8f, Modifier.weight(1f))
        Box(Modifier.plainClickable(dismiss).padding(8.dp).semantics { contentDescription = "닫기" }) { Sym("xmark", 16f, Theme.ink) }
    }
}

// TODO(Views/Wave/WaveHomeView.swift TightLabel 게임 단계): 아이콘과 글자를 붙인 라벨 (간격 3)
object TightLabel

// TODO(Views/BrickGameView.swift BrickGameButton 게임 단계): 1000칸 카드의 [블록] 버튼. 지금은 모양만 (눌러도 아무것도 안 함, 기호 water·grid 는 SF 표에 없어 글자만)
@Composable
fun BrickGameButton(
    m: AppModel, filled: Int, floor: Int, yesterday: Int,
    title: String = "벽돌깨기", icon: String = "gamecontroller", plain: Boolean = false,
    labelStyle: TightLabel? = null, modifier: Modifier = Modifier,
) {
    GameButtonFace(title, plain, modifier)
}

// TODO(Views/Wave/WaveHomeView.swift WaveGameButton 게임 단계): 1000칸 카드의 [파도] 버튼. 지금은 모양만
@Composable
fun WaveGameButton(plain: Boolean = false, labelStyle: TightLabel? = null, modifier: Modifier = Modifier) {
    GameButtonFace("파도", plain, modifier)
}

// TODO(게임 단계): 위 두 버튼의 겉모양 (진짜 이식 때 함께 지움)
@Composable
private fun GameButtonFace(title: String, plain: Boolean, modifier: Modifier) {
    Box(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
            .background(if (plain) Theme.card else Theme.teal, RoundedCornerShape(12.dp))
            .border(1.5.dp, if (plain) Theme.border else Color.Transparent, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { FitText(title, appFont(14f, FontWeight.Bold), 0.8f, color = if (plain) Theme.ink else Color.White) }
}

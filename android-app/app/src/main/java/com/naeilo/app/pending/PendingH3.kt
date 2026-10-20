package com.naeilo.app.pending

import androidx.compose.runtime.Composable
import com.naeilo.app.AppModel

// H 단계 목표 미션 판(ui/goal/GoalViews.kt)이 부르는데 뒤 단계에서 옮길 화면의 자리. 진짜 이식이 오면 이 파일의 해당 줄을 지운다.
// 전체 화면(fullScreenCover)으로 열린다. 닫기는 LocalNavBack.current?.pop (GoalViews.kt FullCover 가 넣어 준다)

// TODO(Views/BrickGameView.swift 게임 단계): 벽돌깨기 본 게임 (1000칸 값: 채운 칸·바닥·어제)
@Composable fun BrickGameHub(m: AppModel, filled: Int, floor: Int, yesterday: Int) {}
// TODO(Views/Wave/WaveHomeView.swift 파도 단계): 내 자산으로 하는 파도타기
@Composable fun WaveHub(m: AppModel) {}

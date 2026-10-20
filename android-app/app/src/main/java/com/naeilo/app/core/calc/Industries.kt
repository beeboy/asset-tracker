package com.naeilo.app.core.calc

// 업종 자료: NYU 다모다란 pedata (2026년 1월). 10년 평균 = 2016~2025년 1월. 시안과 같은 값.
// (key, 한글 이름, 지금 PER, 10년 평균 PER, 예상 5년 이익 성장 %, 기업 수)
// ios: Data/Industries.swift:3
object Industries {
    /** Swift 이름 붙은 튜플 (key, ko, pe, pe10, growth, count) 자리 */
    data class Row(val key: String, val ko: String, val pe: Double, val pe10: Double, val growth: Double, val count: Int)

    // ios: Data/Industries.swift:4
    val all: List<Row> = listOf(
        Row("Coal", "석탄", 19.7, 8.7, 22.5, 16),   // ios: Data/Industries.swift:5
        Row("Steel", "철강", 24.1, 13.1, 16.0, 19),   // ios: Data/Industries.swift:6
        Row("FinNonbank", "비은행 금융", 22.1, 12.4, 22.0, 176),   // ios: Data/Industries.swift:7
        Row("REOps", "부동산 서비스", 43.8, 24.6, 33.0, 54),   // ios: Data/Industries.swift:8
        Row("Semi", "반도체", 48.8, 27.4, 22.9, 66),   // ios: Data/Industries.swift:9
        Row("SemiEq", "반도체 장비", 34.2, 20.6, 18.7, 31),   // ios: Data/Industries.swift:10
        Row("Grocery", "식료품 소매", 25.2, 15.4, 11.1, 15),   // ios: Data/Industries.swift:11
        Row("Aero", "항공우주·방산", 35.3, 22.2, 25.0, 79),   // ios: Data/Industries.swift:12
        Row("Truck", "트럭 운송", 32.7, 21.0, 20.1, 26),   // ios: Data/Industries.swift:13
        Row("Computers", "컴퓨터·주변기기", 34.3, 22.7, 25.4, 36),   // ios: Data/Industries.swift:14
        Row("Electronics", "전자 부품", 40.2, 26.6, 17.7, 114),   // ios: Data/Industries.swift:15
        Row("Furn", "가구", 25.2, 17.1, 12.3, 27),   // ios: Data/Industries.swift:16
        Row("Biotech", "바이오", 32.1, 22.0, 38.1, 496),   // ios: Data/Industries.swift:17
        Row("RetailAuto", "자동차 판매", 24.0, 16.4, 17.8, 34),   // ios: Data/Industries.swift:18
        Row("Apparel", "의류", 27.1, 19.5, 13.6, 35),   // ios: Data/Industries.swift:19
        Row("AssetMgmt", "자산운용", 23.0, 16.6, 15.0, 283),   // ios: Data/Industries.swift:20
        Row("ChemBasic", "기초 화학", 18.4, 14.0, 19.8, 29),   // ios: Data/Industries.swift:21
        Row("CompSvc", "IT 서비스", 26.1, 19.8, 12.3, 64),   // ios: Data/Industries.swift:22
        Row("Brokerage", "증권", 19.8, 15.1, 20.3, 32),   // ios: Data/Industries.swift:23
        Row("InsGeneral", "일반 보험", 27.3, 20.9, 24.6, 21),   // ios: Data/Industries.swift:24
        Row("TelecomEq", "통신 장비", 35.1, 26.9, 40.3, 57),   // ios: Data/Industries.swift:25
        Row("Farming", "농업", 22.0, 17.1, 12.0, 35),   // ios: Data/Industries.swift:26
        Row("AutoParts", "자동차 부품", 20.2, 16.2, 16.0, 35),   // ios: Data/Industries.swift:27
        Row("BigBank", "대형 은행", 14.2, 11.6, 13.7, 15),   // ios: Data/Industries.swift:28
        Row("Mining", "금속·광업", 29.4, 23.9, 34.4, 73),   // ios: Data/Industries.swift:29
        Row("ElecEq", "전기 장비", 47.2, 22.8, 18.9, 112),   // ios: Data/Industries.swift:30
        Row("Auto", "자동차", 119.6, 38.0, 10.2, 33),   // ios: Data/Industries.swift:31
        Row("AutoAero", "우주항공·궤도 통신", 77.5, 30.1, 17.6, 112),   // ios: Data/Industries.swift:32
        Row("Oilfield", "유전 서비스", 19.4, 13.2, 3.5, 97),   // ios: Data/Industries.swift:33
        Row("Advertising", "광고", 25.5, 17.7, 2.3, 52),   // ios: Data/Industries.swift:34
        Row("Broadcasting", "방송", 13.5, 10.5, 0.9, 24),   // ios: Data/Industries.swift:35
        Row("ConstSupply", "건설 자재", 25.8, 20.3, 7.6, 40),   // ios: Data/Industries.swift:36
        Row("RetailGen", "종합 소매", 33.5, 27.5, 11.7, 23),   // ios: Data/Industries.swift:37
        Row("RetailSpecial", "전문 소매", 24.9, 20.5, 8.1, 94),   // ios: Data/Industries.swift:38
        Row("Distributors", "유통", 22.4, 19.0, 9.5, 62),   // ios: Data/Industries.swift:39
        Row("Shoe", "신발", 30.5, 33.9, 7.3, 11),   // ios: Data/Industries.swift:40
        Row("BizSvc", "기업·소비자 서비스", 24.1, 27.4, 12.8, 155),   // ios: Data/Industries.swift:41
        Row("BuildMat", "건축 자재", 17.1, 20.1, 10.2, 41),   // ios: Data/Industries.swift:42
        Row("GasDist", "가스 수송", 18.5, 22.6, 12.7, 23),   // ios: Data/Industries.swift:43
        Row("InsPC", "손해보험", 11.8, 14.6, 15.3, 57),   // ios: Data/Industries.swift:44
        Row("Office", "사무기기", 18.2, 22.5, 14.4, 14),   // ios: Data/Industries.swift:45
        Row("Green", "신재생 에너지", 26.0, 32.7, 7.8, 15),   // ios: Data/Industries.swift:46
        Row("HCProducts", "의료기기", 30.3, 38.7, 11.1, 204),   // ios: Data/Industries.swift:47
        Row("Packaging", "포장재", 13.9, 18.6, 19.0, 19),   // ios: Data/Industries.swift:48
        Row("Education", "교육", 15.0, 20.6, 19.0, 32),   // ios: Data/Industries.swift:49
        Row("Household", "생활용품", 19.7, 27.5, 8.0, 110),   // ios: Data/Industries.swift:50
        Row("InfoSvc", "정보 서비스", 23.6, 33.8, 10.1, 15),   // ios: Data/Industries.swift:51
        Row("Recreation", "레저", 18.2, 27.0, 9.9, 49),   // ios: Data/Industries.swift:52
        Row("Water", "수도", 20.2, 31.1, 7.6, 14),   // ios: Data/Industries.swift:53
        Row("Wireless", "무선 통신", 19.5, 30.9, 15.1, 12),   // ios: Data/Industries.swift:54
        Row("Transport", "운송", 13.3, 21.6, 15.8, 19),   // ios: Data/Industries.swift:55
        Row("Publishing", "출판", 16.7, 27.2, 9.4, 19),   // ios: Data/Industries.swift:56
        Row("Air", "항공", 12.9, 22.5, 30.2, 23),   // ios: Data/Industries.swift:57
        Row("TelecomSvc", "통신 서비스", 8.0, 14.5, 59.4, 39),   // ios: Data/Industries.swift:58
        Row("Precious", "귀금속", 18.6, 35.1, 71.8, 56),   // ios: Data/Industries.swift:59
        Row("Machinery", "기계", 26.6, 23.7, 13.5, 105),   // ios: Data/Industries.swift:60
        Row("Restaurant", "외식", 32.8, 29.1, 3.5, 64),   // ios: Data/Industries.swift:61
        Row("Tobacco", "담배", 19.8, 17.8, 6.1, 10),   // ios: Data/Industries.swift:62
        Row("Waste", "환경·폐기물", 34.3, 31.5, 12.2, 53),   // ios: Data/Industries.swift:63
        Row("HCIT", "헬스케어 IT", 37.3, 34.7, 11.2, 115),   // ios: Data/Industries.swift:64
        Row("Power", "전력", 21.0, 19.5, 9.6, 46),   // ios: Data/Industries.swift:65
        Row("Diversified", "복합 기업", 16.8, 15.8, 7.6, 20),   // ios: Data/Industries.swift:66
        Row("Hospitals", "병원", 15.6, 15.0, 15.0, 31),   // ios: Data/Industries.swift:67
        Row("Utility", "유틸리티", 20.5, 19.7, 6.9, 14),   // ios: Data/Industries.swift:68
        Row("HCSupport", "헬스케어 서비스", 21.1, 20.4, 12.6, 104),   // ios: Data/Industries.swift:69
        Row("ChemSpecial", "특수 화학", 23.2, 22.8, 8.5, 59),   // ios: Data/Industries.swift:70
        Row("OilProd", "석유·가스 생산", 12.7, 12.4, 4.9, 142),   // ios: Data/Industries.swift:71
        Row("REIT", "리츠", 28.1, 29.0, 4.3, 190),   // ios: Data/Industries.swift:72
        Row("Entertainment", "엔터테인먼트", 32.5, 34.3, 5.8, 92),   // ios: Data/Industries.swift:73
        Row("RegBank", "지역 은행", 12.6, 13.4, 14.0, 568),   // ios: Data/Industries.swift:74
        Row("Hotel", "호텔·카지노", 24.7, 26.8, 13.6, 63),   // ios: Data/Industries.swift:75
        Row("Software", "소프트웨어", 37.5, 35.7, 22.8, 309),   // ios: Data/Industries.swift:76
        Row("Internet", "인터넷", 34.8, 34.1, 20.9, 29),   // ios: Data/Industries.swift:77
        Row("Pharma", "제약", 24.9, 25.1, 17.8, 228),   // ios: Data/Industries.swift:78
        Row("Games", "게임 소프트웨어", 29.6, 27.6, 20.5, 77),   // ios: Data/Industries.swift:79
        Row("Engineering", "엔지니어링·건설", 24.8, 25.8, 21.0, 48),   // ios: Data/Industries.swift:80
        Row("Beverage", "음료", 26.3, 28.9, 15.4, 27),   // ios: Data/Industries.swift:81
        Row("BldgRetail", "건자재 소매", 22.3, 22.3, 17.0, 14),   // ios: Data/Industries.swift:82
        Row("FoodWhole", "식품 도매", 24.7, 25.9, 36.9, 13),   // ios: Data/Industries.swift:83
    )
}

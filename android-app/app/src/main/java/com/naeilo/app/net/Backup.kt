package com.naeilo.app.net

import com.naeilo.app.AppModel
import com.naeilo.app.core.IsoDate
import com.naeilo.app.core.NJson
import com.naeilo.app.core.calc.Calm
import com.naeilo.app.core.calc.MC
import com.naeilo.app.core.calc.PWorld
import com.naeilo.app.data.DayRec
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Holding
import com.naeilo.app.data.Lot
import com.naeilo.app.data.MyRecord
import com.naeilo.app.data.Progress
import com.naeilo.app.data.apply
import com.naeilo.app.data.progress
import com.naeilo.app.data.saveProgress
import com.naeilo.app.ui.wave.SurfCloset
import com.naeilo.app.ui.town.TownBook
import com.naeilo.app.ui.town.TownLog
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

// 내 폰에 저장하기·불러오기: 동기화 없이 종목·미션 진행·추가한 종목 시세를 파일 하나로.
// 파일 앱(내 휴대폰 저장소·Google Drive)에 두고, 새 폰이나 앱을 지웠다 깐 뒤 불러온다. 파일 고르기 화면은 설정 화면 단계에서 붙인다.
// 파일은 암호화하지 않는다 (보유 내역이 그대로 들어 있으니 남에게 보내지 않게 안내한다).
// 파일 형식은 아이폰과 같다: JSON, 날짜 iso8601, 키 이름 순서 (Data 칸은 base64 글자).

/** 백업 파일 날짜는 iso8601 (추가한 종목의 at 도). 그 밖은 NJson 과 같다 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal val BackupJson = Json(NJson) { serializersModule = SerializersModule { contextual(IsoDate) } }

/** Swift Data 의 JSON 형식 (base64 글자) */
object B64 : KSerializer<ByteArray> {
    override val descriptor = PrimitiveSerialDescriptor("B64", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ByteArray) = encoder.encodeString(Base64.getEncoder().encodeToString(value))
    override fun deserialize(decoder: Decoder): ByteArray = Base64.getDecoder().decode(decoder.decodeString())
}

@Serializable
data class Backup(   // ios: Data/Backup.swift:7
    var v: Int = 1,   // ios: Data/Backup.swift:8
    @Serializable(with = IsoDate::class) var at: Instant,   // ios: Data/Backup.swift:9
    var holdings: List<Holding>,   // ios: Data/Backup.swift:10
    var progress: Progress,   // ios: Data/Backup.swift:11
    var homeFriend: String? = null,   // ios: Data/Backup.swift:12
    var custom: List<CustomSymbols.Saved>,   // ios: Data/Backup.swift:13
    var hist: Map<String, CustomSymbols.Hist>,   // ios: Data/Backup.swift:14
    var daily: List<DayRec>? = null,     // 내 기록 (예전 파일에는 없다)   // ios: Data/Backup.swift:15
    var town: Map<String, @Serializable(with = B64::class) ByteArray>? = null,  // 동네: 주민 수첩(이름·모자·이사 온 날)·대화 기록·별사탕 (예전 파일에는 없다)   // ios: Data/Backup.swift:16
    var krLog: List<MC.KrDay>? = null,   // 국내 주식 합계 날짜별 금액. 계획선 출발값에 쓴다 (예전 파일에는 없다)   // ios: Data/Backup.swift:17
    var site: String? = null,   // base64 글자 (아이폰 Data 와 같은 JSON). 계산에 쓰는 사이트 칸(사건·전망 설정·현금). 동기화를 켜든 끄든 같은 숫자가 나오게 (예전 파일에는 없다)   // ios: Data/Backup.swift:18
    var calm: Calm? = null,   // 투자 습관 기록 (예전 파일에는 없다)   // ios: Data/Backup.swift:19
    var pworld: PWorld? = null,   // 평행 세계·모의바구니 (예전 파일에는 없다)   // ios: Data/Backup.swift:20
    var lots: List<Lot>? = null,   // 수량 기록 (날짜별 보유 수량). 내 길 실제 평가액·계획선 출발값에 쓴다 (예전 파일에는 없다)   // ios: Data/Backup.swift:21
    var calc: CalcSnap? = null,   // 계산 자료(전망 입력값). 불러온 기기도 같은 입력으로 센다 (예전 파일에는 없다)   // ios: Data/Backup.swift:22
) {
    companion object {
        val townKeys = listOf("town.book", "town.log")   // ios: Data/Backup.swift:22

        /** UserDefaults.data(forKey:) 자리: SharedPreferences 에 base64 글자로 둔 값 */
        private fun prefData(k: String): ByteArray? = Defaults.prefs.getString(k, null)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

        fun of(m: AppModel): Backup {   // ios: Data/Backup.swift:24,25
            val t = LinkedHashMap<String, ByteArray>()   // ios: Data/Backup.swift:29
            for (k in townKeys) { prefData(k)?.let { d -> t[k] = d } }   // ios: Data/Backup.swift:30
            // 정수 값: 별사탕 + 파도 타기(꾸미기·섬 별·최고 점수)
            for (k in listOf("town.candy") + SurfCloset.keys) t[k] = Defaults.prefs.getInt(k, 0).toString().encodeToByteArray()   // ios: Data/Backup.swift:32
            return Backup(at = Instant.now(), holdings = m.holdings, progress = m.progress, homeFriend = m.homeFriend,   // ios: Data/Backup.swift:26
                custom = CustomSymbols.shared.items, hist = CustomSymbols.shared.hist, daily = m.myRecord, krLog = CustomSymbols.shared.krLog, site = Base64.getEncoder().encodeToString(Sync.shared.calcSite),   // ios: Data/Backup.swift:27
                calm = m.calm, pworld = m.pworld, lots = m.lots, calc = Market.shared.calcOut,   // ios: Data/Backup.swift:28
                town = t)   // ios: Data/Backup.swift:33
        }

        fun fileName(d: Instant = Instant.now()): String {   // ios: Data/Backup.swift:59
            val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).withZone(ZoneId.systemDefault())   // ios: Data/Backup.swift:60
            return "naeilo-${f.format(d)}"   // ios: Data/Backup.swift:61
        }
    }

    /** 불러오기: 추가한 종목 시세부터 넣고(종목 이름·가격이 있어야 보유가 보인다) 종목·진행을 덮는다 */
    fun restore(m: AppModel) {   // ios: Data/Backup.swift:37,38
        CustomSymbols.shared.restore(custom, hist, krLog)   // ios: Data/Backup.swift:39
        site?.let { s -> runCatching { Base64.getDecoder().decode(s) }.getOrNull()?.let { Sync.shared.restoreCalcSite(it) } }   // ios: Data/Backup.swift:40
        calm?.let { c -> m.calm = c }   // ios: Data/Backup.swift:41
        pworld?.let { w -> m.pworld = w }   // ios: Data/Backup.swift:42
        lots?.let { l -> m.lots = l }   // ios: Data/Backup.swift:43
        calc?.let { c -> Market.shared.takeCalc(c) }   // ios: Data/Backup.swift:44
        m.apply(progress)   // ios: Data/Backup.swift:44
        homeFriend?.let { h -> m.homeFriend = h }   // ios: Data/Backup.swift:45
        daily?.let { d -> m.myRecord = MyRecord.merge(m.myRecord, d) }   // ios: Data/Backup.swift:46
        val town = town   // ios: Data/Backup.swift:47
        if (town != null) {
            val e = Defaults.prefs.edit()
            for (k in townKeys) { town[k]?.let { d -> e.putString(k, Base64.getEncoder().encodeToString(d)) } }   // ios: Data/Backup.swift:48
            for (k in listOf("town.candy") + SurfCloset.keys) {   // ios: Data/Backup.swift:49
                town[k]?.decodeToString()?.toIntOrNull()?.let { c -> e.putInt(k, c) }   // ios: Data/Backup.swift:50
            }
            e.apply()
            SurfCloset.keep()   // ios: Data/Backup.swift:52
            TownBook.shared.reload(); TownLog.shared.reload()   // ios: Data/Backup.swift:53
        }
        m.holdings = holdings   // ios: Data/Backup.swift:55
        m.saveProgress()   // ios: Data/Backup.swift:56
    }
}

/** 파일 내보내기용 문서 (JSON). 파일 고르기(SAF)는 화면 쪽이 이 바이트를 쓰고 읽는다 */
object BackupFile {   // ios: Data/Backup.swift:66
    /** 키 이름 순서로 (아이폰 .sortedKeys) */
    private fun sorted(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.keys.sorted().associateWith { sorted(e[it]!!) })
        is JsonArray -> JsonArray(e.map { sorted(it) })
        else -> e
    }
    fun data(b: Backup): ByteArray =   // ios: Data/Backup.swift:68,69
        runCatching { sorted(BackupJson.encodeToJsonElement(Backup.serializer(), b)).toString().encodeToByteArray() }.getOrNull() ?: ByteArray(0)   // ios: Data/Backup.swift:70,71

    fun read(d: ByteArray): Backup =   // ios: Data/Backup.swift:76
        BackupJson.decodeFromString(Backup.serializer(), d.decodeToString())   // ios: Data/Backup.swift:79,80
}

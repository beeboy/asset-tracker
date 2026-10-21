package com.naeilo.app.net

import com.naeilo.app.AppModel
import com.naeilo.app.core.IsoDate
import com.naeilo.app.core.NJson
import com.naeilo.app.data.DayRec
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Holding
import com.naeilo.app.data.MyRecord
import com.naeilo.app.data.Progress
import com.naeilo.app.data.apply
import com.naeilo.app.data.progress
import com.naeilo.app.data.saveProgress
import com.naeilo.app.pending.SurfCloset
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
) {
    companion object {
        val townKeys = listOf("town.book", "town.log")   // ios: Data/Backup.swift:17

        /** UserDefaults.data(forKey:) 자리: SharedPreferences 에 base64 글자로 둔 값 */
        private fun prefData(k: String): ByteArray? = Defaults.prefs.getString(k, null)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

        fun of(m: AppModel): Backup {   // ios: Data/Backup.swift:19,20
            val t = LinkedHashMap<String, ByteArray>()   // ios: Data/Backup.swift:23
            for (k in townKeys) { prefData(k)?.let { d -> t[k] = d } }   // ios: Data/Backup.swift:24
            // 정수 값: 별사탕 + 파도 타기(꾸미기·섬 별·최고 점수)
            for (k in listOf("town.candy") + SurfCloset.keys) t[k] = Defaults.prefs.getInt(k, 0).toString().encodeToByteArray()   // ios: Data/Backup.swift:26
            return Backup(at = Instant.now(), holdings = m.holdings, progress = m.progress, homeFriend = m.homeFriend,   // ios: Data/Backup.swift:21
                custom = CustomSymbols.shared.items, hist = CustomSymbols.shared.hist, daily = m.myRecord,   // ios: Data/Backup.swift:22
                town = t)   // ios: Data/Backup.swift:27
        }

        fun fileName(d: Instant = Instant.now()): String {   // ios: Data/Backup.swift:49
            val f = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).withZone(ZoneId.systemDefault())   // ios: Data/Backup.swift:50
            return "naeilo-${f.format(d)}"   // ios: Data/Backup.swift:51
        }
    }

    /** 불러오기: 추가한 종목 시세부터 넣고(종목 이름·가격이 있어야 보유가 보인다) 종목·진행을 덮는다 */
    fun restore(m: AppModel) {   // ios: Data/Backup.swift:31,32
        CustomSymbols.shared.restore(custom, hist)   // ios: Data/Backup.swift:33
        m.apply(progress)   // ios: Data/Backup.swift:34
        homeFriend?.let { h -> m.homeFriend = h }   // ios: Data/Backup.swift:35
        daily?.let { d -> m.myRecord = MyRecord.merge(m.myRecord, d) }   // ios: Data/Backup.swift:36
        val town = town   // ios: Data/Backup.swift:37
        if (town != null) {
            val e = Defaults.prefs.edit()
            for (k in townKeys) { town[k]?.let { d -> e.putString(k, Base64.getEncoder().encodeToString(d)) } }   // ios: Data/Backup.swift:38
            for (k in listOf("town.candy") + SurfCloset.keys) {   // ios: Data/Backup.swift:39
                town[k]?.decodeToString()?.toIntOrNull()?.let { c -> e.putInt(k, c) }   // ios: Data/Backup.swift:40
            }
            e.apply()
            SurfCloset.keep()   // ios: Data/Backup.swift:42
            TownBook.shared.reload(); TownLog.shared.reload()   // ios: Data/Backup.swift:43
        }
        m.holdings = holdings   // ios: Data/Backup.swift:45
        m.saveProgress()   // ios: Data/Backup.swift:46
    }
}

/** 파일 내보내기용 문서 (JSON). 파일 고르기(SAF)는 화면 쪽이 이 바이트를 쓰고 읽는다 */
object BackupFile {   // ios: Data/Backup.swift:56
    /** 키 이름 순서로 (아이폰 .sortedKeys) */
    private fun sorted(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.keys.sorted().associateWith { sorted(e[it]!!) })
        is JsonArray -> JsonArray(e.map { sorted(it) })
        else -> e
    }
    fun data(b: Backup): ByteArray =   // ios: Data/Backup.swift:58,59
        runCatching { sorted(BackupJson.encodeToJsonElement(Backup.serializer(), b)).toString().encodeToByteArray() }.getOrNull() ?: ByteArray(0)   // ios: Data/Backup.swift:60,61

    fun read(d: ByteArray): Backup =   // ios: Data/Backup.swift:66
        BackupJson.decodeFromString(Backup.serializer(), d.decodeToString())   // ios: Data/Backup.swift:69,70
}

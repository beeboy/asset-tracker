package com.naeilo.app.core.calc

import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.ExperimentalSerializationApi
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naeilo.app.core.Day
import com.naeilo.app.core.NJson
import com.naeilo.app.core.Store
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs

// 화면 스레드 (Swift MainActor): 계산 결과는 여기서 상태에 넣는다
private val mainH: Handler by lazy { Handler(Looper.getMainLooper()) }
private fun onMain(block: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) block() else mainH.post { block() } }
// 계산 줄 하나 = 스레드 하나 (Swift Task.detached). 화면 스레드에서는 계산하지 않는다
private fun worker(name: String, prio: Int): ExecutorService =
    Executors.newSingleThreadExecutor { r -> Thread(r, name).apply { isDaemon = true; priority = prio } }
// 파일 쓰기 (Swift DispatchQueue.global(qos: .utility)). 풀어 쓰기(JSON)도 여기서 해서 화면 스레드가 멈추지 않게
private val writer: ExecutorService by lazy { worker("naeilo-mc-write", Thread.MIN_PRIORITY) }

/** 무거운 계산은 앱이 화면에 떠 있을 때만 (MainActivity 가 onStart·onStop 에서 켜고 끈다). 뒤로 가면 돌던 계산도 멈추고 다시 켜질 때 이어 간다 */
object Compute {   // ios: Data/ForecastEngine.swift:5
    private val lock = ReentrantLock()   // ios: Data/ForecastEngine.swift:6
    private var on = true   // ios: Data/ForecastEngine.swift:7
    val active: Boolean get() { lock.lock(); try { return on } finally { lock.unlock() } }   // ios: Data/ForecastEngine.swift:8
    fun setActive(a: Boolean) {   // ios: Data/ForecastEngine.swift:9
        lock.lock(); val was = on; on = a; lock.unlock()   // ios: Data/ForecastEngine.swift:10
        if (a && !was) onMain { ForecastEngine.shared.resume(); PathRuns.shared.resume() }   // ios: Data/ForecastEngine.swift:11
    }
    /** 계산 하나에 붙이는 멈춤 표시: 경로 32개마다 묻고, 멈췄으면 hit (그 결과는 버린다) */
    class Halt {   // ios: Data/ForecastEngine.swift:14
        @Volatile var hit = false   // ios: Data/ForecastEngine.swift:15
        val check: () -> Boolean = { if (!Compute.active) hit = true; hit }   // ios: Data/ForecastEngine.swift:16
    }
}

// 경로 캐시 파일(수 MB~20MB)은 파일에서 바로 풀고 바로 쓴다. 통째 바이트·글자를 한 번 더 만들지 않아 시작 때 메모리가 덜 튄다 (형식은 그대로)
@OptIn(ExperimentalSerializationApi::class)
private fun <T> readJson(name: String, ser: KSerializer<T>): T? =
    runCatching { Store.url(name).takeIf { it.exists() }?.inputStream()?.buffered()?.use { NJson.decodeFromStream(ser, it) } }.getOrNull()
/** Store.writeData 와 같이 옆 파일에 다 쓴 뒤 바꿔 넣는다 */
@OptIn(ExperimentalSerializationApi::class)
private fun <T> writeJson(name: String, ser: KSerializer<T>, v: T) {
    runCatching {
        val tmp = java.io.File(Store.dir, "$name.tmp")
        tmp.outputStream().buffered().use { NJson.encodeToStream(ser, v, it) }
        if (!tmp.renameTo(Store.url(name))) { tmp.copyTo(Store.url(name), overwrite = true); tmp.delete() }
    }
}

// 앱 전망: 웹과 같은 몬테카를로(MonteCarlo.kt)를 하루 한 번 화면 밖에서 돌리고 경로를 저장해 둔다 (웹 fcdSig 처럼
// 오늘 날짜·종목·수량·목표일·사이트 통계·사건이 그대로면 다시 계산하지 않음). 렌즈·추세 신뢰·월 적립·목표 금액은
// 같은 경로에 기대수익만 바꿔 다시 계산한다 (웹 reweight). 계산이 끝나기 전에는 AppModel 의 닫힌 식 값을 잠깐 보여 준다.
// 상태(run)는 compose mutableStateOf 라 바뀌면 화면이 다시 그려진다. 나머지 칸은 화면 스레드에서만 만진다.
class ForecastEngine private constructor() {   // ios: Data/ForecastEngine.swift:23,24 @Observable = mutableStateOf
    companion object {
        val shared = ForecastEngine()   // ios: Data/ForecastEngine.swift:25
        private const val file = "forecast_mc.plist"   // ios: Data/ForecastEngine.swift:59 (이름만 같고 속은 JSON)

        fun compute(j: Job, stop: (() -> Boolean)? = null): Run? {   // ios: Data/ForecastEngine.swift:92
            val model = MC.build(holdings = j.holdings, stats = j.stats, settings = j.settings, events = j.events, start = j.start, goal = j.horizon) ?: return null   // ios: Data/ForecastEngine.swift:93
            val t0 = System.nanoTime(); val s = j.settings; val mu = MC.scenarioMu(model, s.scenario)   // ios: Data/ForecastEngine.swift:94
            val sim = { ev: Boolean -> MC.simulate(model, holdings = j.holdings, mu = mu, nPaths = s.nPaths, seed = s.seedU32, goal = j.goal, monthly = j.monthly, withEvents = ev, dof = s.dof, stop = stop) }   // ios: Data/ForecastEngine.swift:95
            // 사건 없는 경로는 다 돈 뒤에 차례로 (Swift 와 같다. 같이 돌리면 경로 묶음 두 벌이 4줄 풀과 메모리를 함께 차지한다)
            val ev = sim(true); val no = if (model.eventCount > 0 && stop?.invoke() != true) sim(false) else null   // ios: Data/ForecastEngine.swift:96
            return Run(key = j.key, factors = model.factors, events = model.eventCount, ev = ev, no = no, ms = (System.nanoTime() - t0) / 1000000.0)   // ios: Data/ForecastEngine.swift:97
        }

        /** 짧은 지문 (FNV-1a). 앱을 다시 켜도 같은 값이어야 해서 hashCode 대신 */
        fun fnv(s: String): String {   // ios: Data/ForecastEngine.swift:115
            var h: ULong = 0xcbf29ce484222325uL   // FNV 시작값 (Swift 116줄, 검사기 예외)
            for (b in s.toByteArray(Charsets.UTF_8)) h = (h xor (b.toInt() and 0xff).toULong()) * 0x100000001b3.toULong()   // ios: Data/ForecastEngine.swift:117
            return h.toString(36)   // ios: Data/ForecastEngine.swift:118
        }
    }

    data class Job(   // ios: Data/ForecastEngine.swift:27
        var key: String,   // ios: Data/ForecastEngine.swift:28
        var holdings: List<MC.Holding>,   // ios: Data/ForecastEngine.swift:29
        var stats: MC.Stats,   // ios: Data/ForecastEngine.swift:30
        var settings: MC.Settings,   // ios: Data/ForecastEngine.swift:31
        var events: List<MC.Event>,   // ios: Data/ForecastEngine.swift:32
        var start: String,   // ios: Data/ForecastEngine.swift:33
        var horizon: String,   // ios: Data/ForecastEngine.swift:34
        var goal: Double,   // ios: Data/ForecastEngine.swift:35
        var monthly: Double,   // ios: Data/ForecastEngine.swift:36
        // PathRuns: 같은 구성·기간 묶음 (비중만 뺀 키)과 비중. 시세가 조금 움직여 비중이 바뀌어도 같은 날엔 다시 계산하지 않는다
        var group: String = "",   // ios: Data/ForecastEngine.swift:38
        var w: List<Double> = emptyList(),   // ios: Data/ForecastEngine.swift:39
    )
    /** 계산해 둔 경로. ev = 사건 반영, no = 사건 없이 (사건이 없으면 null) */
    @Serializable data class Run(   // ios: Data/ForecastEngine.swift:42
        var key: String,   // ios: Data/ForecastEngine.swift:43
        var factors: List<MC.Factor>,   // ios: Data/ForecastEngine.swift:44
        var events: Int,   // ios: Data/ForecastEngine.swift:45
        var ev: MC.Sim,   // ios: Data/ForecastEngine.swift:46
        var no: MC.Sim? = null,   // ios: Data/ForecastEngine.swift:47
        var ms: Double,   // ios: Data/ForecastEngine.swift:48
    )

    var run: Run? by mutableStateOf(null)   // ios: Data/ForecastEngine.swift:51
        private set
    /** 계산 중인 job 키 (한 번에 하나만 돈다) */
    private var pending: String? = null   // ios: Data/ForecastEngine.swift:53
    /** 계산 중에 들어온 다음 job (끝나면 이어서). 앱이 뒤로 가서 멈춘 job 도 여기서 기다린다 */
    private var waiting: Job? = null   // ios: Data/ForecastEngine.swift:55
    /** 모형을 못 만든 job (같은 job 을 다시 돌리지 않는다) */
    private val failed = HashSet<String>()   // ios: Data/ForecastEngine.swift:57
    private val views = HashMap<String, MC.Result>()   // ios: Data/ForecastEngine.swift:58
    private val exec = worker("naeilo-forecast", Thread.NORM_PRIORITY)
    // 다시 무게 주기(reweight)도 화면 밖에서: 끝나면 viewTick 을 올려 화면이 다시 읽는다. 그동안은 같은 경로의 마지막 결과
    private val viewExec = worker("naeilo-forecast-view", Thread.NORM_PRIORITY)
    private var viewTick by mutableStateOf(0)
    private val viewBusy = HashSet<String>()
    private var lastView: Pair<String, MC.Result>? = null
    /** 파일에서 읽은 것 (계산 스레드에서만, 처음 계산 하나가 보고 놓는다). Swift 는 그 자리에서 읽어 첫 ensure 가 건너뛴다 */
    private var disk: Run? = null

    init {   // ios: Data/ForecastEngine.swift:61
        // 어제 계산한 것이라도 불러 둔다 (키가 다르면 쓰지 않고 새로 계산). 읽기·풀기는 계산 스레드에서 (계산보다 먼저 줄 선다)
        exec.execute {
            val r = readJson(file, Run.serializer())   // ios: Data/ForecastEngine.swift:63
            disk = r
            if (r != null) mainH.post { if (run == null) run = r }
        }
    }

    /** 이 job 의 경로가 없으면 화면 밖에서 계산을 시작한다. 이미 하나가 돌고 있으면 끝난 뒤에 (돌던 계산을 버리고 새로 시작하지 않는다).
     *  화면 스레드에서 부른다 */
    fun ensure(job: Job) {   // ios: Data/ForecastEngine.swift:67
        if (run?.key == job.key || pending == job.key || failed.contains(job.key)) return   // ios: Data/ForecastEngine.swift:68
        if (pending != null || !Compute.active) { waiting = job; return }   // ios: Data/ForecastEngine.swift:69
        waiting = null   // ios: Data/ForecastEngine.swift:70
        pending = job.key   // ios: Data/ForecastEngine.swift:71
        val halt = Compute.Halt()   // ios: Data/ForecastEngine.swift:72
        exec.execute {   // ios: Data/ForecastEngine.swift:73
            // 읽기가 끝나기 전에 줄 선 계산: 읽은 것이 같은 키면 다시 계산하지 않는다. 그 뒤 ensure 는 화면 스레드에서 run 을 보고 거른다
            val saved = disk?.takeIf { it.key == job.key }; disk = null
            val r = saved ?: runCatching { compute(job, stop = halt.check) }.getOrNull()   // ios: Data/ForecastEngine.swift:74
            val halted = saved == null && halt.hit   // ios: Data/ForecastEngine.swift:75
            mainH.post {   // ios: Data/ForecastEngine.swift:76
                pending = null   // ios: Data/ForecastEngine.swift:77
                if (r != null && !halted) {   // ios: Data/ForecastEngine.swift:78
                    if (run !== r) { views.clear(); lastView = null }   // ios: Data/ForecastEngine.swift:79
                    run = r   // ios: Data/ForecastEngine.swift:80
                    if (saved == null) writer.execute { writeJson(file, Run.serializer(), r) }   // ios: Data/ForecastEngine.swift:81
                } else if (halted) {   // ios: Data/ForecastEngine.swift:82
                    if (waiting == null) waiting = job   // ios: Data/ForecastEngine.swift:83 다시 켜지면 처음부터
                } else failed.add(job.key)   // ios: Data/ForecastEngine.swift:84
                waiting?.let { w -> waiting = null; ensure(w) }   // ios: Data/ForecastEngine.swift:85
            }
        }
    }
    /** 앱이 다시 화면에 뜨면 멈춘 계산을 이어 간다 */
    fun resume() { val w = waiting ?: return; waiting = null; ensure(w) }   // ios: Data/ForecastEngine.swift:90

    /** 렌즈(base·smooth·mine)와 추세 신뢰로 본 결과. withEvents 가 거짓이면 사건 없는 경로 (웹 '미반영').
     *  Swift 는 그 자리에서 reweight 하지만 여기서는 화면 밖에서 계산하고, 끝날 때까지 같은 경로의 마지막 결과(없으면 null)를 준다 */
    fun view(key: String, lens: String, trust: Double, goal: Double, monthly: Double, withEvents: Boolean): MC.Result? {   // ios: Data/ForecastEngine.swift:101
        if (viewTick < 0) return null   // 계산이 끝나면 다시 읽게 (상태 구독)
        val r = run; if (r == null || r.key != key) return null   // ios: Data/ForecastEngine.swift:102
        val sim = if (withEvents) r.ev else (r.no ?: r.ev)   // ios: Data/ForecastEngine.swift:103
        val vk = "$lens|$trust|$goal|$monthly|$withEvents"   // ios: Data/ForecastEngine.swift:104
        views[vk]?.let { lastView = key to it; return it }   // ios: Data/ForecastEngine.swift:105
        val A = sim.A   // ios: Data/ForecastEngine.swift:106
        val mu = r.factors.take(A).map { f -> if (lens == "base") f.base else if (lens == "smooth") f.smooth else MC.blendMu(f.base, f.smooth, trust / 100) }   // ios: Data/ForecastEngine.swift:107
        if (viewBusy.add(vk)) viewExec.execute {
            val v = runCatching { MC.reweight(sim, mu = mu, goal = goal, monthly = monthly) }.getOrNull()   // ios: Data/ForecastEngine.swift:108
            mainH.post {
                viewBusy.remove(vk)
                if (v == null || run !== r) return@post
                if (views.size > 40) views.clear()   // ios: Data/ForecastEngine.swift:109
                views[vk] = v   // ios: Data/ForecastEngine.swift:110
                viewTick += 1
            }
        }
        return lastView?.takeIf { it.first == key }?.second   // ios: Data/ForecastEngine.swift:111
    }
}

// 경로 저장: cg(종목별 누적 로그수익, Float32)는 바이트 그대로 (little-endian, base64 글자로)
object SimSerializer : KSerializer<MC.Sim> {   // ios: Data/ForecastEngine.swift:123
    @Serializable class CodingKeys(val A: Int, val M: Int, val P: Int, val cg: List<String>, val muD: List<Double>, val monthIdx: List<Int>, val monthDates: List<String>, val v0: List<Double>, val start: String, val summary: MC.Result)   // ios: Data/ForecastEngine.swift:124
    override val descriptor: SerialDescriptor = CodingKeys.serializer().descriptor
    override fun deserialize(decoder: Decoder): MC.Sim {   // ios: Data/ForecastEngine.swift:125
        val c = decoder.decodeSerializableValue(CodingKeys.serializer())   // ios: Data/ForecastEngine.swift:126
        val blobs = c.cg.asSequence().map { Base64.getDecoder().decode(it) }   // 하나씩 풀어 바로 Float 로 (바이트를 다 들고 있지 않는다)   // ios: Data/ForecastEngine.swift:127
        val s = MC.Sim(A = c.A, M = c.M, P = c.P,   // ios: Data/ForecastEngine.swift:128
            cg = blobs.map { b -> val a = FloatArray(b.size / 4) { 0f }; ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(a); a }.toList(),   // ios: Data/ForecastEngine.swift:129
            muD = c.muD, monthIdx = c.monthIdx,   // ios: Data/ForecastEngine.swift:130
            monthDates = c.monthDates, v0 = c.v0,   // ios: Data/ForecastEngine.swift:131
            start = c.start, summary = c.summary)   // ios: Data/ForecastEngine.swift:132
        if (s.cg.size != s.A || !s.cg.all { it.size == s.M * s.P }) throw SerializationException("크기")   // ios: Data/ForecastEngine.swift:133
        return s
    }
    override fun serialize(encoder: Encoder, value: MC.Sim) {   // ios: Data/ForecastEngine.swift:135
        val c = CodingKeys(   // ios: Data/ForecastEngine.swift:136
            A = value.A, M = value.M, P = value.P,   // ios: Data/ForecastEngine.swift:137
            cg = value.cg.map { a -> val bb = ByteBuffer.allocate(a.size * 4).order(ByteOrder.LITTLE_ENDIAN); bb.asFloatBuffer().put(a); Base64.getEncoder().encodeToString(bb.array()) },   // ios: Data/ForecastEngine.swift:138
            muD = value.muD, monthIdx = value.monthIdx, monthDates = value.monthDates,   // ios: Data/ForecastEngine.swift:139
            v0 = value.v0, start = value.start, summary = value.summary)   // ios: Data/ForecastEngine.swift:140
        encoder.encodeSerializableValue(CodingKeys.serializer(), c)
    }
}

// 계획 4안·구성 비교·목표 자동 고르기용 경로: 전망과 같은 몬테카를로(같은 사이트 통계·설정·사건, 사건 반영)를
// 구성마다 한 번씩 화면 밖에서 돌려 둔다. 지금 내 구성은 전망 경로(ForecastEngine)를 그대로 쓰고, 여기서는 그 밖의 구성만.
// 하루 한 번 (키에 오늘 날짜), 차례로 하나씩 계산한다. 시세가 움직여 비중이 조금 바뀐 구성은 그날 계산해 둔 경로를 그대로 쓴다
class PathRuns private constructor() {   // ios: Data/ForecastEngine.swift:148,149 @Observable = mutableStateOf
    companion object {
        val shared = PathRuns()   // ios: Data/ForecastEngine.swift:150
        /** 비중이 이만큼 안에서 움직이면 같은 구성으로 본다 (각 종목 2%p) */
        const val near = 0.02   // ios: Data/ForecastEngine.swift:156
        private const val file = "paths_mc.plist"   // ios: Data/ForecastEngine.swift:166 (이름만 같고 속은 JSON)

        fun close(a: List<Double>?, b: List<Double>): Boolean {   // ios: Data/ForecastEngine.swift:175
            if (a == null || a.size != b.size) return false   // ios: Data/ForecastEngine.swift:176
            return a.zip(b).all { (x, y) -> abs(x - y) <= near }   // ios: Data/ForecastEngine.swift:177
        }

        fun compute(job: ForecastEngine.Job, stop: (() -> Boolean)? = null): Run? {   // ios: Data/ForecastEngine.swift:194
            val model = MC.build(holdings = job.holdings, stats = job.stats, settings = job.settings, events = job.events, start = job.start, goal = job.horizon) ?: return null   // ios: Data/ForecastEngine.swift:195
            val s = job.settings; val t0 = System.nanoTime()   // ios: Data/ForecastEngine.swift:196
            val sim = MC.simulate(model, holdings = job.holdings, mu = MC.scenarioMu(model, s.scenario), nPaths = s.nPaths, seed = s.seedU32,   // ios: Data/ForecastEngine.swift:197
                                  goal = Double.POSITIVE_INFINITY, monthly = 0.0, withEvents = true, dof = s.dof, stop = stop)   // ios: Data/ForecastEngine.swift:198
            return Run(key = job.key, factors = model.factors, sim = sim, group = job.group, w = job.w,   // ios: Data/ForecastEngine.swift:199
                       ms = (System.nanoTime() - t0) / 1000000.0, at = System.currentTimeMillis() / 1000.0)   // ios: Data/ForecastEngine.swift:200
        }
    }
    @Serializable data class Run(   // ios: Data/ForecastEngine.swift:151
        var key: String, var factors: List<MC.Factor>, var sim: MC.Sim,   // ios: Data/ForecastEngine.swift:152
        var group: String? = null, var w: List<Double>? = null, var ms: Double? = null, var at: Double? = null,   // ios: Data/ForecastEngine.swift:153
    )

    var runs: Map<String, Run> by mutableStateOf(emptyMap())   // ios: Data/ForecastEngine.swift:158
        private set
    /** 오늘 다른 구성 경로 계산에 쓴 시간 (ms, 개발자 줄) */
    val msToday: Double get() = runs.values.fold(0.0) { s, r -> s + (r.ms ?: 0.0) }   // ios: Data/ForecastEngine.swift:160
    /** 경로 하나가 끝날 때마다 (목표 자동 고르기 다시 하기). 화면 스레드에서 부른다 */
    var onRun: (() -> Unit)? = null   // ios: Data/ForecastEngine.swift:162
    private val queue = ArrayList<ForecastEngine.Job>()   // ios: Data/ForecastEngine.swift:163
    private var busy = false   // ios: Data/ForecastEngine.swift:164
    private val failed = HashSet<String>()   // ios: Data/ForecastEngine.swift:165 모형을 못 만든 job (다시 줄 세우지 않는다)
    private val exec = worker("naeilo-paths", Thread.MIN_PRIORITY)
    private val runsSer = MapSerializer(String.serializer(), Run.serializer())
    /** 파일에서 읽은 오늘 경로 (계산 스레드에서만, 처음 계산 하나가 보고 놓는다). 그 뒤는 next 가 화면 스레드에서 runs 를 보고 거른다 */
    private var disk: Map<String, Run> = emptyMap()
    /** 아직 파일에 안 쓴 경로가 있다. 쓰기는 줄이 비거나 앱이 뒤로 갈 때 한 번 (경로 하나마다 수십 MB 를 통째로 다시 쓰지 않게, 형식은 그대로) */
    private var dirty = false
    private fun flush() { if (!dirty) return; dirty = false; val all = runs; writer.execute { writeJson(file, runsSer, all) } }

    init {   // ios: Data/ForecastEngine.swift:168
        // 파일 읽기·풀기는 계산 스레드에서 (계산보다 먼저 줄 선다). 그새 계산해 둔 것이 있으면 그쪽이 이긴다
        exec.execute {
            // 오늘 쓴 파일이 아니면 오늘 키(날짜로 시작)가 있을 수 없다: 다 풀고 버리지 않게 읽지 않는다 (결과는 같다)
            val fresh = Store.url(file).let { f -> f.exists() && Day.str(java.time.Instant.ofEpochMilli(f.lastModified())) >= Day.today }
            val r = if (fresh) readJson(file, runsSer) else null   // ios: Data/ForecastEngine.swift:169
            disk = r?.filter { it.key.startsWith(Day.today) } ?: emptyMap()
            if (r != null) mainH.post {
                val today = Day.today   // ios: Data/ForecastEngine.swift:170
                runs = r.filter { it.key.startsWith(today) } + runs   // ios: Data/ForecastEngine.swift:171
            }
        }
    }

    /** 계산해 둔 경로 (같은 구성·기간이면 비중이 조금 달라도). 없으면 줄에 세우고 null (끝나면 runs 가 바뀌어 화면이 다시 그려진다). 화면 스레드에서 부른다 */
    fun get(job: ForecastEngine.Job): Run? {   // ios: Data/ForecastEngine.swift:181
        runs[job.key]?.let { return it }   // ios: Data/ForecastEngine.swift:182
        if (job.group.isNotEmpty()) runs.values.firstOrNull { it.group == job.group && close(it.w, job.w) }?.let { return it }   // ios: Data/ForecastEngine.swift:183
        if (failed.contains(job.key)) return null   // ios: Data/ForecastEngine.swift:184
        val i = queue.indexOfFirst { it.key == job.key || (job.group.isNotEmpty() && it.group == job.group) }   // ios: Data/ForecastEngine.swift:185
        if (i >= 0) {
            if (!close(queue[i].w, job.w)) queue[i] = job   // ios: Data/ForecastEngine.swift:186 같은 묶음은 줄에 하나만 (최신 비중으로)
        } else {   // ios: Data/ForecastEngine.swift:187
            queue.add(job)   // ios: Data/ForecastEngine.swift:188
        }
        next()   // ios: Data/ForecastEngine.swift:190
        return null   // ios: Data/ForecastEngine.swift:191
    }

    fun resume() { next() }   // ios: Data/ForecastEngine.swift:203

    private fun next() {   // ios: Data/ForecastEngine.swift:205
        if (busy) return
        if (queue.isEmpty() || !Compute.active) { flush(); return }   // ios: Data/ForecastEngine.swift:206
        busy = true   // ios: Data/ForecastEngine.swift:207
        val job = queue.removeAt(0)   // ios: Data/ForecastEngine.swift:208
        // 줄에 선 사이 파일 읽기·다른 계산으로 생긴 경로면 계산하지 않는다 (get 과 같은 규칙)
        if (runs[job.key] != null || (job.group.isNotEmpty() && runs.values.any { it.group == job.group && close(it.w, job.w) })) { busy = false; onRun?.invoke(); next(); return }
        val halt = Compute.Halt()   // ios: Data/ForecastEngine.swift:209
        exec.execute {   // ios: Data/ForecastEngine.swift:210
            // 읽어 둔 파일에 같은 키·같은 묶음(비중이 가까운)이 있으면 그대로 (get 과 같은 규칙)
            val saved = disk[job.key] ?: if (job.group.isNotEmpty()) disk.values.firstOrNull { it.group == job.group && close(it.w, job.w) } else null
            disk = emptyMap()
            val r = saved ?: runCatching { compute(job, stop = halt.check) }.getOrNull()   // ios: Data/ForecastEngine.swift:211
            val halted = saved == null && halt.hit   // ios: Data/ForecastEngine.swift:212
            mainH.post {   // ios: Data/ForecastEngine.swift:213
                busy = false   // ios: Data/ForecastEngine.swift:214
                if (halted) {   // ios: Data/ForecastEngine.swift:215
                    queue.add(0, job)   // ios: Data/ForecastEngine.swift:216 다시 켜지면 이어서
                    flush()
                    return@post   // ios: Data/ForecastEngine.swift:217
                }
                if (saved != null) { onRun?.invoke(); next(); return@post }   // 이미 runs 에 있다 (읽기를 넣은 뒤)
                if (r != null) {   // ios: Data/ForecastEngine.swift:219
                    val today = Day.today   // ios: Data/ForecastEngine.swift:220
                    val all = LinkedHashMap(runs.filter { it.key.startsWith(today) })   // ios: Data/ForecastEngine.swift:221
                    // 하루에 쌓이는 경로는 16개까지: 가장 오래된 것부터 뺀다 (화면이 쓰는 구성은 8개 안팎)
                    while (all.size >= 16) { val k = all.entries.minWithOrNull(Comparator { x, y -> (x.value.at ?: 0.0).compareTo(y.value.at ?: 0.0) })?.key ?: break; all.remove(k) }   // ios: Data/ForecastEngine.swift:223
                    all[r.key] = r   // ios: Data/ForecastEngine.swift:224
                    runs = all   // ios: Data/ForecastEngine.swift:225
                    dirty = true   // ios: Data/ForecastEngine.swift:226 (쓰기는 flush 에서)
                    onRun?.invoke()   // ios: Data/ForecastEngine.swift:227
                } else failed.add(job.key)   // ios: Data/ForecastEngine.swift:228
                next()   // ios: Data/ForecastEngine.swift:229
            }
        }
    }
}

package com.gildongmu.app

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Calendar

enum class Screen { Today, Road, Friends, Book, Day, Month, Settings }

/** 녹음 화면 상태 */
data class Listening(
    val active: Boolean = false,
    val level: Float = 0f,
    val text: String = "",
    val done: Boolean = false,
    /** 말은 끝났고, 일기에 담는 중 (몇 초) */
    val saving: Boolean = false,
    val mood: Mood? = null,
    val error: String? = null,
    val forEnroll: Boolean = false,
    /** 저녁에 남긴 뒤: 한마디 더 할지, 오늘을 마무리할지 고르게 한다 */
    val offerWrap: Boolean = false,
    /** 다른 목소리로 확인되어, 그 사람의 일기에 담았을 때의 이름 */
    val owner: String? = null,
    /** 방금 들은 말에 길동무가 건네는 한 줄 */
    val reply: String? = null,
    /** 방금 담은 일기 (길 위에 올리거나 내릴 때 쓴다) */
    val entryId: String? = null,
    /** 길 위에도 남겼다면 그 자리 이름 */
    val shared: String? = null,
    /** 남에게 보이는 자리에 올리려 했으나 AI 필터가 막았다면, 그 까닭 */
    val blocked: String? = null,
    /** 거친 말을 들어, 담지 않고 다시 말해 달라고 청하는 중일 때의 알림 */
    val notice: String? = null,
    /** 그렇게 청한 뒤 다시 듣는 중 */
    val again: Boolean = false,
    /** 몇 번째 듣기인지: 새로 듣기 시작할 때마다 달라진다 (듣는 시간을 처음부터 다시 센다) */
    val turn: Int = 0,
)

/** 지도에서 방금 남긴 한마디 (떨어지는 움직임용) */
data class JustLeft(val placeId: String, val at: Long)

/** 다른 목소리로 들렸을 때 */
data class VoiceMismatch(val text: String, val signature: FloatArray, val suggested: Profile?)

class AppModel(private val context: Context) {
    val store = Store(context)
    init { Ai.init(context); Maker.load(context) }
    private val main = Handler(Looper.getMainLooper())
    val voice = Voice(context)
    val input = VoiceInput(context).apply { init() }

    var profiles by mutableStateOf(store.profiles())
    var current by mutableStateOf(store.profiles().firstOrNull { it.id == store.currentProfileId })
    init {
        voice.speaker(GuideVoice.of(current))      // 길동무의 목소리: 운전자와 다른 성별로, 또는 설정에서 고른 대로
        voice.logSpoken = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    private val screenState = mutableStateOf(Screen.Today)
    var screen: Screen
        get() = screenState.value
        set(v) {
            if (v != screenState.value) Log.d("Gildongmu", "screen ${screenState.value} -> $v")
            screenState.value = v
        }
    var dayKey by mutableStateOf(Store.dayKey(System.currentTimeMillis()))
    var entries by mutableStateOf(current?.let { store.entries(it.id) } ?: emptyList())
    var settings by mutableStateOf(store.settings.also { Look.apply(it.season, it.dayPart, it.scene) })
    init { Skin.art = current?.art() ?: Art.AURA }
    var listening by mutableStateOf(Listening())
    var mismatch by mutableStateOf<VoiceMismatch?>(null)
    var ask by mutableStateOf<Ask?>(null)
    var askWhy by mutableStateOf("")
    var carTick by mutableIntStateOf(0)
    var pickingProfile by mutableStateOf(current == null)
    var enrollSignature by mutableStateOf<FloatArray?>(null)

    // 길 위 지도
    val navi = Navi(context)
    private val geo by lazy { GeoMap.get(context) }
    var places by mutableStateOf<List<Place>>(emptyList())
    var selectedPlace by mutableStateOf<String?>(null)
    /** 이 차에서 가장 마지막에 일기에 담긴 말 (앱을 켠 뒤로). 첫 화면은 오늘의 말 가운데 이것을 띄운다. */
    var lastSaidId by mutableStateOf<String?>(null)
    var listenTarget by mutableStateOf<PlaceTarget?>(null)
    var justLeft by mutableStateOf<JustLeft?>(null)
    var liked by mutableStateOf<Set<String>>(emptySet())
    var toast by mutableStateOf<String?>(null)
    var mapCmd by mutableStateOf<String?>(null)

    // 하루 마무리
    var closedAt by mutableStateOf<Long?>(null)
    var closing by mutableStateOf(false)

    // GPS · 지나가며 저절로 보기
    var gps by mutableStateOf<Fix?>(null)
    var gpsName by mutableStateOf(Places.HERE_NAME)
    var demoDriving by mutableStateOf(false)
    var popup by mutableStateOf<Popup?>(null)
    var foreground = true
    private val popQueue = ArrayDeque<String>()
    private val popped = HashMap<String, Long>()
    /** GPS 가 알려 준 지금 빠르기 (m/s). 기어를 아직 알 수 없을 때, 달리고 있는지 가늠하는 데 쓴다. */
    private var gpsSpeed = 0f
    private var gpsSpeedAt = 0L

    /**
     * 주행 중이면 글을 치우고, 지도는 GPS와 저절로 뜨는 이야기만 (목록·친구·일기장·설정은 주차 중에만).
     * 기어를 알면 기어로, 아직 모르면(연결 전·연결 실패) GPS 빠르기로 가늠한다. 달리는데 '정차'로 보는 쪽이 더 위험하기 때문이다.
     */
    val driving: Boolean
        get() {
            @Suppress("UNUSED_VARIABLE") val tick = carTick      // 차 상태가 바뀌면, 이 값을 읽은 화면이 다시 그려진다
            if (demoDriving) return true
            if (car.gear != null) return !car.parked
            return gpsSpeed > 1.5f && System.currentTimeMillis() - gpsSpeedAt < 5000L
        }

    /**
     * 달리는 중이지만 지금은 서 있다 (신호 대기처럼 빠르기가 거의 0).
     * 이 동안에만 지나는 길의 한마디를 글로도 보여 준다. 다시 움직이면 글은 물러나고 한 줄과 목소리만 남는다.
     * (빠르기를 알 수 없으면 서 있지 않은 것으로 본다: 달리는데 글을 띄우는 쪽이 더 위험하다.)
     */
    val standing: Boolean
        get() {
            @Suppress("UNUSED_VARIABLE") val tick = carTick
            if (!driving) return false
            if (demoDriving) return demoHold
            return gpsSpeed < 0.8f && System.currentTimeMillis() - gpsSpeedAt < 5000L
        }
    /** 시연 주행을 잠시 세워 둔다 (빨간 신호에 선 것처럼) */
    var demoHold by mutableStateOf(false)
        private set
    private var holdStart = 0L

    /** 달리는 동안 들린 '다른 목소리'는 차를 세운 뒤에 묻는다 */
    private var pendingMismatch: VoiceMismatch? = null

    /** 앱을 음성 호출로 켰거나 달리는 중에 켰다면, 여는 장면을 건너뛴다 */
    var skipOpening = false

    // ---------------------------------------------------------------- 처음 켰을 때: 런치 화면
    /** 이 차의 운전자 이름 (안드로이드 자동차 OS가 일반 앱에 알려 줄 때만). 런치 화면의 인사와 이름 칸에 쓴다. */
    val carUserName: String? by lazy { CarUser.name(context) }
    /** 런치 화면에서 [시작하기]를 눌렀다 */
    var launchStarted by mutableStateOf(false)
    /** 시연: 계정이 있어도 런치 화면을 보여 준다 */
    var forceLaunch by mutableStateOf(false)
    /** 지금 런치 화면을 보여야 하나: 아직 아무도 시작하지 않았거나(계정 없음), 시연으로 띄웠을 때 */
    val launching: Boolean get() = (profiles.isEmpty() || forceLaunch) && !launchStarted

    /**
     * 처음 켰을 때의 시작 화면부터 다시 본다 (설정 › 시연 도구. 심사·시연 때 몇 번이고 볼 수 있게, release 빌드에서도 된다).
     * 이어지는 환영 화면에서 길동무가 먼저 묻는 것까지 보이도록, 혼자 탄 상황으로 두고 오늘 물은 횟수를 지운다.
     */
    fun showLaunch() {
        profiles.forEach { store.resetAsks(it.id) }
        car.simulateAlone = true; carTick++
        ask = null; voice.stop()
        if (listening.active) closeListening()
        forceLaunch = true; launchStarted = false; pickingProfile = false; screen = Screen.Today
    }

    /** 차에 탔을 때의 환영 화면을 다시 본다 (설정 › 시연 도구) */
    fun replayWelcome() {
        current?.let { store.resetAsks(it.id) }
        car.simulateAlone = true; carTick++
        board(clock())
    }

    // ---------------------------------------------------------------- 차에 탄 순간: 일기장을 펼치며 맞는다

    /** 환영 장면을 다시 틀어 달라는 신호 (값이 바뀔 때마다 한 번) */
    var welcomeCue by mutableIntStateOf(0)
        private set
    /** 환영 장면이 나오는 중 (이 동안에는 따로 말을 걸지 않는다) */
    var welcoming by mutableStateOf(false)
    /** 시연 중에는, 따로 시키지 않는 한 환영 뒤에 묻지 않는다 */
    var demoQuiet = false
    private var boardAsk: Ask? = null
    private var boardWhy = ""
    private var boardMode = 0          // 0 알아서 · 1 묻는다 · 2 인사만
    private var greeted = false

    /** 차에 탔다: 일기장이 펼쳐지며 인사를 건넨다 (달리는 중에는 하지 않는다). ask 가 false 면 인사만 한다. */
    fun board(now: Calendar = clock(), ask: Boolean = true) {
        if (current == null || driving) return
        if (kotlin.math.abs(now.timeInMillis - System.currentTimeMillis()) > 5 * 60_000) { clockOverride = now; clockSetAt = System.currentTimeMillis() }
        this.ask = null; voice.stop()
        boardMode = if (ask) 1 else 2
        greeted = false; boardAsk = null
        pickingProfile = false; screen = Screen.Today
        welcoming = true
        welcomeCue++
    }

    /** 환영 장면에서 인사말이 적히는 순간: 인사를 소리로도 건네고, 이어서 물을 말을 정해 둔다 */
    fun greet() {
        greeted = true
        val now = clock()
        val (a, b, sub) = Words.welcome(current?.name, now.get(Calendar.HOUR_OF_DAY))
        boardAsk = null
        val wants = boardMode == 1 || (boardMode == 0 && !demoQuiet)
        if (current != null && wants && ask == null && !listening.active && mismatch == null) {
            refreshClosed()
            val r = decide("depart", null, now, boarding = true)
            boardAsk = r.ask; boardWhy = r.why
            Log.d("Gildongmu", "환영: ${r.ask?.text ?: "-"} / ${r.why}")
        }
        voice.say(listOfNotNull("$a $b", sub, boardAsk?.text).joinToString(" "))
    }

    /** 환영 장면이 걷히며 오늘 화면이 나타나는 순간: 정해 둔 물음을 화면에 올린다 (목소리는 인사에 이어 이미 건넸다) */
    fun welcomed() {
        val p = current
        val a = boardAsk
        if (p != null && a != null && !driving && !listening.active) { ask = a; askWhy = boardWhy; store.countAsk(p.id) }
        // 인사를 듣기 전에 넘겼다면, 여느 때처럼 조금 뒤에 물을지 생각해 본다
        else if (p != null && !greeted && (boardMode == 1 || (boardMode == 0 && !demoQuiet))) main.postDelayed({ if (ask == null && !listening.active) think("depart", now = clock()) }, 1500)
        boardAsk = null; boardMode = 0
    }

    /** 차를 세웠을 때: 미뤄 둔 물음을 꺼낸다 */
    private fun onParked() {
        pendingMismatch?.let { mm ->
            pendingMismatch = null
            if (!listening.active) {
                mismatch = mm
                voice.say(if (mm.suggested != null) "아까 들린 목소리, 혹시 ${mm.suggested.name}님이셨나요?" else "아까 처음 듣는 목소리가 있었어요. 누구의 일기에 담을까요?")
            }
        }
    }

    /** 바탕이 흘러가는 빠르기: 서 있을 때는 거의 멈춰 있고, 달릴 때는 설정에 따라 (잔잔하게 1 · 흐르게 3 · 흐르지 않게 0) */
    val sceneSpeed: Float get() = if (!driving) 0.1f else when (settings.driveMotion) { "flow" -> 3f; "still" -> 0f; else -> 1f }

    // 친구
    var friends by mutableStateOf<List<Friend>>(emptyList())
    var friendLines by mutableStateOf<List<FriendLine>>(emptyList())
    var myLine by mutableStateOf<FriendLine?>(null)
    var myLikers by mutableStateOf<List<String>>(emptyList())
    var likedLines by mutableStateOf<Set<String>>(emptySet())
    var listenFriends by mutableStateOf(false)

    /** 지금 위치: GPS → 내비가 알려 준 곳(지도 안일 때) → 시연 위치 */
    val here: PlaceTarget
        get() {
            gps?.let { return PlaceTarget(null, gpsName, it.lat, it.lng) }
            val loc = navi.location?.takeIf { geo.inside(geo.xy(it.first, it.second)) }
            return if (loc != null) PlaceTarget(null, "지금 위치", loc.first, loc.second)
            else PlaceTarget(null, Places.HERE_NAME, Places.HERE_LAT, Places.HERE_LNG)
        }

    /** 시연용 시계: 출발·도착 상황을 재현할 때 그 시각으로 일기를 남긴다 */
    var clockOverride by mutableStateOf<Calendar?>(null)
    fun clock(): Calendar = (clockOverride?.clone() as? Calendar)?.apply {
        // 시연 시각에 실제 흐른 시간을 더해 순서가 자연스럽게
        add(Calendar.SECOND, ((System.currentTimeMillis() - (clockSetAt ?: System.currentTimeMillis())) / 1000).toInt())
    } ?: Calendar.getInstance()
    private var clockSetAt: Long? = null

    /** 시연용 시계 맞추기 (null 이면 실제 시각으로 돌아간다) */
    fun demoClock(c: Calendar?) { clockOverride = c; clockSetAt = if (c != null) System.currentTimeMillis() else null; refreshClosed() }

    private var pendingQuestion: String? = null
    private var tripStart: Long? = null
    private var enrollTarget: ((FloatArray?) -> Unit)? = null

    val car = CarWatcher(context, onChange = { carTick++ }, onGear = ::onGear)

    fun start() {
        car.start(); navi.start(); refreshPlaces(); refreshClosed(); refreshFriends(); startGps()
        // 지도 바탕은 미리 한 번 그려 둔다 (길 위 화면을 열 때 바로 보이도록)
        Thread { runCatching { MapArt.base(geo) } }.start()
    }

    fun release() { car.release(); input.release(); voice.release(); navi.release(); runCatching { lm.removeUpdates(locListener) } }

    // ---------------------------------------------------------------- GPS 와 지나가며 보기

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    /** GPS 가 알려 준 지금 자리 (그려 둔 지도 밖이어도 적어 둔다: 닿은 자리를 운행 기록에 남기는 데 쓴다) */
    private var rawSpot: Pair<Double, Double>? = null
    /** 지금 차가 있는 자리: GPS → 내비가 알려 준 곳. 모르면 null */
    private fun spot(): Pair<Double, Double>? = (if (demoDriving) gps?.let { it.lat to it.lng } else null) ?: rawSpot ?: gps?.let { it.lat to it.lng } ?: navi.location
    private val locListener = android.location.LocationListener { loc ->
        rawSpot = loc.latitude to loc.longitude
        if (demoDriving) return@LocationListener
        gpsSpeed = if (loc.hasSpeed()) loc.speed else 0f
        gpsSpeedAt = System.currentTimeMillis()
        if (geo.inside(geo.xy(loc.latitude, loc.longitude))) onFix(Fix(loc.latitude, loc.longitude, loc.bearing, loc.time, gpsSpeed))
    }
    private val appContext = context.applicationContext

    @Suppress("MissingPermission")
    fun startGps() {
        if (appContext.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        runCatching { lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1000L, 0f, locListener, Looper.getMainLooper()) }
            .onFailure { Log.w("Gildongmu", "GPS 시작 실패", it) }
    }

    /** 새 위치가 들어올 때마다: 가까이 온 이야기를 하나씩 띄운다 */
    private fun onFix(f: Fix) {
        gps = f
        val me = geo.xy(f.lat, f.lng)
        val now = System.currentTimeMillis()
        places.asSequence()
            .filter { p -> !p.id.startsWith("my_") } // 내가 남긴 말은 다시 띄우지 않는다
            .filter { p -> popped[p.id]?.let { now - it > 30 * 60_000L } ?: true }
            .map { p -> val q = geo.xy(p.lat, p.lng); p to kotlin.math.hypot(q.x - me.x, q.y - me.y) }
            .filter { (p, d) -> d < Places.radius(p) }
            .sortedBy { it.second }
            .forEach { (p, _) -> popQueue.addLast(p.id); popped[p.id] = now }
        if (popup == null) nextPopup()
    }

    private fun nextPopup() {
        // 아직 아무도 시작하지 않았거나(처음 화면·계정 만들기) 인사를 건네는 중에는 띄우지 않는다.
        // 줄에 세워 둔 이야기는 다음 위치가 들어올 때(1초마다) 다시 본다.
        if (current == null || launching || welcoming) return
        // 말하고 있는 동안에는 끼어들지 않는다 (내 말에 읽는 소리가 섞이지 않게). 조금 뒤에 다시 본다.
        if (listening.active) { main.postDelayed({ if (popup == null) nextPopup() }, 2500); return }
        val id = popQueue.removeFirstOrNull() ?: return
        val p = places.firstOrNull { it.id == id } ?: return nextPopup()
        popup = Popup(id, System.currentTimeMillis(), auto = true)
        Log.d("Gildongmu", "지나는 길의 한마디: ${p.id} (${p.name})")
        voice.say(popupSpeech(p))
        if (!foreground) Notify.nearby(appContext, p)
        main.removeCallbacks(popupDone); main.postDelayed(popupDone, 7000)
    }

    private val popupDone: Runnable = object : Runnable {
        override fun run() {
            // 신호에 서 있는 동안에는 닫지 않는다 (글을 읽을 틈). 다시 움직이면 곧 닫힌다.
            if (popup != null && standing) { main.postDelayed(this, 1200); return }
            popup = null; main.postDelayed({ if (popup == null) nextPopup() }, 450)
        }
    }

    /** 운전 중에 직접 눌러서 보기 */
    fun openPopup(id: String) {
        popup = Popup(id, System.currentTimeMillis(), auto = false)
        places.firstOrNull { it.id == id }?.let { voice.say(popupSpeech(it)) }
        main.removeCallbacks(popupDone); main.postDelayed(popupDone, 9000)
    }

    fun closePopup() { main.removeCallbacks(popupDone); voice.stop(); popup = null; main.postDelayed({ if (popup == null) nextPopup() }, 450) }

    private fun popupSpeech(p: Place): String {
        val d = p.diary
        if (d != null) return "${p.name}에서 ${d.author}님이 쓴 일기가 있어요. ${d.title}."
        val w = p.lead ?: return p.name
        return if (p.kind == PlaceKind.CAUTION) "주의, ${p.name}. ${w.text}" else "${p.name}, ${w.author}님의 한마디. ${w.text}"
    }

    // 시연 주행: 실제 GPS 처럼 0.5초마다 한 점씩, 뚝섬로를 따라 달린다
    private var driveStart = 0L
    private var driveSpeed = 30f
    private val driveCum: FloatArray by lazy {
        val pts = Places.DEMO_DRIVE.map { geo.xy(it.first, it.second) }
        FloatArray(pts.size).also { c -> for (i in 1 until pts.size) c[i] = c[i - 1] + kotlin.math.hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y) }
    }
    private val driveTick = object : Runnable {
        override fun run() {
            val pts = Places.DEMO_DRIVE
            val d = ((if (demoHold) holdStart else System.currentTimeMillis()) - driveStart) / 1000f * driveSpeed
            val total = driveCum.last()
            val dd = d.coerceAtMost(total)
            var i = 1
            while (i < driveCum.size - 1 && driveCum[i] < dd) i++
            val k = ((dd - driveCum[i - 1]) / (driveCum[i] - driveCum[i - 1]).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            val a = pts[i - 1]; val b = pts[i]
            val lat = a.first + (b.first - a.first) * k
            val lng = a.second + (b.second - a.second) * k
            val pa = geo.xy(a.first, a.second); val pb = geo.xy(b.first, b.second)
            val bearing = Math.toDegrees(kotlin.math.atan2((pb.x - pa.x).toDouble(), (-(pb.y - pa.y)).toDouble())).toFloat()
            gpsName = when {
                dd < 420f -> "뚝섬로 · 서울숲 앞"
                dd < 1150f -> "뚝섬로 · 서울숲역 부근"
                else -> "뚝섬로 · 성수동"
            }
            onFix(Fix(lat, lng, bearing, System.currentTimeMillis(), if (demoHold) 0f else driveSpeed))
            if (d < total) main.postDelayed(this, 500)
            else main.postDelayed({ demoDriving = false; gpsName = "성수동 · 뚝섬로 (주차)"; carTick++; onParked() }, 2500)
        }
    }

    /** from: 경로의 몇 미터 지점에서 출발할지 (시연 촬영용) */
    fun startDemoDrive(speed: Float = 30f, from: Float = 0f) {
        driveSpeed = speed
        driveStart = System.currentTimeMillis() - (from / speed.coerceAtLeast(0.1f) * 1000f).toLong()
        popped.clear(); popQueue.clear(); popup = null; selectedPlace = null
        demoHold = false
        demoDriving = true
        main.removeCallbacks(driveTick); main.post(driveTick)
    }

    fun stopDemoDrive() { main.removeCallbacks(driveTick); val was = demoDriving; demoDriving = false; demoHold = false; if (was) onParked() }

    /** 시연 주행: 빨간 신호에 선 것처럼 세우거나(on), 다시 출발한다 */
    fun holdDemoDrive(on: Boolean) {
        if (!demoDriving || on == demoHold) return
        val now = System.currentTimeMillis()
        if (on) holdStart = now else driveStart += now - holdStart
        demoHold = on; carTick++
    }

    /** 시연 주행의 빠르기를 바꾼다 (지금 자리는 그대로) */
    fun setDemoSpeed(v: Float) {
        if (!demoDriving) return
        val now = if (demoHold) holdStart else System.currentTimeMillis()
        val d = (now - driveStart) / 1000f * driveSpeed
        driveSpeed = v.coerceAtLeast(0.5f)
        driveStart = now - (d / driveSpeed * 1000f).toLong()
    }

    /** 시연 위치로 되돌리기 */
    fun resetGps() { stopDemoDrive(); gps = null; gpsName = Places.HERE_NAME; popup = null; popQueue.clear(); popped.clear() }

    // ---------------------------------------------------------------- 친구 (주차 중에만)

    fun refreshFriends() {
        val me = current ?: return
        val all = Friends.seed() + Friends.directory
        friends = store.friendIds(me.id).mapNotNull { id -> all.firstOrNull { it.id == id } }
        friendLines = Friends.lines(System.currentTimeMillis()).filter { l -> friends.any { it.id == l.friendId } }.sortedByDescending { it.time }
        myLine = store.myLine(me.id)
        likedLines = store.likedLines(me.id)
    }

    fun addFriend(code: String): Friend? {
        val me = current ?: return null
        val f = (Friends.seed() + Friends.directory).firstOrNull { it.code.equals(code.trim(), ignoreCase = true) } ?: return null
        store.addFriend(me.id, f.id)
        refreshFriends()
        return f
    }

    fun likeLine(id: String) {
        val me = current ?: return
        likedLines = if (id in likedLines) likedLines - id else likedLines + id
        store.setLikedLines(me.id, likedLines)
    }

    fun postLine(demoPcm: File? = null) { listenFriends = true; startListening(demoPcm) }

    /** 시연 정리: 길 위에 남긴 내 말, 지도에서 남긴 일기 조각, 오늘의 친구 한마디를 지운다 */
    fun demoCleanup(withToday: Boolean = true) {
        val me = current ?: return
        fun at(h: Int, mi: Int) = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi); set(Calendar.SECOND, 0) }.timeInMillis
        val today = listOf(
            Entry(store.newId(), me.id, at(8, 12), "아 비 오니까 길 진짜 막힌다. 아홉 시 회의인데 이러다 늦겠어… 어떡하지 진짜.", Mood.WORRY,
                Scene(rain = true, night = false, phase = "depart", alone = true)),
            Entry(store.newId(), me.id, at(12, 40), "회의는 겨우 맞춰 들어갔다. 점심은 또 편의점 김밥. 오후에 보고가 하나 더 남았네.", Mood.TIRED,
                Scene(rain = false, night = false, phase = "parked", alone = true)),
        ).filter { withToday && it.time < clock().timeInMillis }   // (시연 시계 기준으로, 이미 지난 시각의 것만)
        store.clearDemo(me.id, today)
        store.resetAsks(me.id)
        entries = store.entries(me.id)
        refreshPlaces(); refreshFriends(); refreshClosed()
        myLikers = emptyList(); selectedPlace = null; justLeft = null; liked = emptySet()
    }

    /** 시연 촬영용: 잠시 감춰 둘 예시 이야기 (디버그 명령으로만 바뀐다) */
    var hiddenPlaces: Set<String> = emptySet()

    fun refreshPlaces() {
        val me = current
        val seed = Places.seed(System.currentTimeMillis()).filter { it.id !in hiddenPlaces }
        places = Places.merge(seed, me?.let { store.words(it.id) } ?: emptyList(), me?.name ?: "")
    }

    fun refreshClosed() {
        closedAt = current?.let { store.closedAt(it.id, Store.dayKey(clock().timeInMillis)) }
    }

    fun showToast(text: String) {
        toast = text
        main.removeCallbacks(hideToast)
        main.postDelayed(hideToast, 3200)
    }
    private val hideToast = Runnable { toast = null }

    // ---------------------------------------------------------------- 계정

    /** 운전하는 사람을 고른다. welcome 이면 그 사람의 일기장이 펼쳐지며 맞는다 (화면에서 직접 골랐을 때). */
    fun choose(p: Profile, welcome: Boolean = false) {
        current = p
        Skin.art = p.art()
        voice.speaker(GuideVoice.of(p))
        store.currentProfileId = p.id
        entries = store.entries(p.id)
        pickingProfile = false
        screen = Screen.Today
        refreshPlaces(); refreshClosed(); refreshFriends()
        if (welcome && !driving) board(clock()) else main.postDelayed({ think("depart", now = clock()) }, 800)
    }

    fun createProfile(name: String, motif: String, colorIdx: Int, voice: FloatArray?, gender: String = ""): Profile {
        val p = Profile(store.newId(), name.trim(), motif, colorIdx, voice, gender = gender)
        store.saveProfile(p)
        profiles = store.profiles()
        return p
    }

    /** 나의 화풍 바꾸기: 창밖 풍경이 그 결로 바뀐다 */
    fun setArt(a: Art) {
        val p = current ?: return
        restyle(p, a.name, p.colorIdx)
        Skin.art = a
    }

    /** 길동무의 목소리 바꾸기 (바꾼 목소리로 한마디 들려준다) */
    fun setGuide(id: String) {
        val p = current ?: return
        store.saveProfile(p.copy(guide = id))
        profiles = store.profiles()
        current = profiles.firstOrNull { it.id == p.id }
        voice.speaker(id)
        voice.say("이 목소리로 이야기할게요.")
    }

    /** 이름 바꾸기 */
    fun rename(p: Profile, name: String) {
        if (name.isBlank()) return
        store.saveProfile(p.copy(name = name.trim().take(8)))
        profiles = store.profiles()
        if (current?.id == p.id) current = profiles.firstOrNull { it.id == p.id }
        refreshPlaces()
    }

    /** 나의 빛·화풍 바꾸기 */
    fun restyle(p: Profile, motif: String, colorIdx: Int) {
        store.saveProfile(p.copy(emoji = motif, colorIdx = colorIdx))
        profiles = store.profiles()
        if (current?.id == p.id) current = profiles.firstOrNull { it.id == p.id }
    }

    fun deleteProfile(p: Profile) {
        store.deleteProfile(p.id)
        profiles = store.profiles()
        if (current?.id == p.id) { current = null; store.currentProfileId = null; pickingProfile = true }
    }

    // ---------------------------------------------------------------- 길동무가 먼저 묻기

    private fun onGear(from: VehicleGear?, to: VehicleGear?) {
        val p = current ?: return
        if (from == VehicleGear.GEAR_PARK && to != VehicleGear.GEAR_PARK && to != null) {
            tripStart = System.currentTimeMillis()
            store.saveTrips(p.id, store.trips(p.id) + Trip(p.id, tripStart!!, from = spot()))
            ask = null
        }
        if (to == VehicleGear.GEAR_PARK && from != null && from != VehicleGear.GEAR_PARK) {
            val start = tripStart
            tripStart = null
            // 닿은 때와 자리를 운행 기록에 적어 둔다 (되풀이되는 자리로 집과 일터를 알아본다: Haunts)
            if (start != null) {
                val all = store.trips(p.id)
                all.lastOrNull()?.takeIf { it.start == start }?.let { t -> store.saveTrips(p.id, all.dropLast(1) + t.copy(end = System.currentTimeMillis(), to = spot())) }
            }
            onParked()
            main.postDelayed({ think("arrive", start?.let { ((System.currentTimeMillis() - it) / 60_000).toInt() }) }, 1500)
        }
    }

    /** 지금 물어볼지 판단하고, 물을 거라면 화면과 목소리로 건넨다. */
    fun think(phase: String, tripMinutes: Int? = null, now: Calendar = Calendar.getInstance()): Asker.Result {
        val p = current ?: return Asker.Result(null, "계정 없음")
        if (kotlin.math.abs(now.timeInMillis - System.currentTimeMillis()) > 5 * 60_000) { clockOverride = now; clockSetAt = System.currentTimeMillis() } else clockOverride = null
        refreshClosed()
        val r = decide(phase, tripMinutes, now)
        askWhy = r.why
        Log.d("Gildongmu", "think($phase): ${r.ask?.text ?: "-"} / ${r.why}")
        if (r.ask != null && !driving && !listening.active && mismatch == null) {
            ask = r.ask
            store.countAsk(p.id)
            voice.say(r.ask.text)
        }
        return r
    }

    private fun decide(phase: String, tripMinutes: Int?, now: Calendar, boarding: Boolean = false): Asker.Result {
        val p = current ?: return Asker.Result(null, "계정 없음")
        return Asker.decide(
            Asker.Input(
                now = now, phase = phase, raining = car.raining, alone = car.alone,
                trips = store.trips(p.id), lastEntryTime = entries.maxOfOrNull { it.time },
                tripMinutes = tripMinutes, asksToday = store.asksToday(p.id), settings = settings,
                entriesToday = entries.count { Store.dayKey(it.time) == Store.dayKey(now.timeInMillis) },
                dayClosed = closedAt != null, boarding = boarding, here = spot(),
            )
        )
    }

    fun answerAsk(demoPcm: File? = null) {
        pendingQuestion = ask?.text
        ask = null
        startListening(demoPcm)
    }

    fun dismissAsk() { ask = null; voice.stop() }

    /** 길동무가 바로 말을 건넨다 (시연·마무리 제안용) */
    fun offer(a: Ask) { ask = a; askWhy = "직접 제안"; voice.say(a.text) }

    // ---------------------------------------------------------------- 하루 마무리

    /** 오늘을 일기장에 덮어 두기 */
    fun endDay() {
        val p = current ?: return
        val now = clock()
        val key = Store.dayKey(now.timeInMillis)
        store.closeDay(p.id, key, now.timeInMillis)
        refreshClosed()
        ask = null
        if (listening.active) listening = Listening()
        closing = true
        voice.say("오늘 하루를 일기장에 담았어요. 오늘도 수고 많았어요.")
        main.postDelayed({ closing = false; dayKey = key; screen = Screen.Day }, 4200)
    }

    fun reopenDay() {
        val p = current ?: return
        store.reopenDay(p.id, Store.dayKey(clock().timeInMillis))
        refreshClosed()
    }

    // ---------------------------------------------------------------- 길 위에 한마디

    fun leaveWord(target: PlaceTarget, demoPcm: File? = null) {
        listenTarget = target
        // 떠 있는 이야기에 이어 남기는 동안에는, 그 이야기를 닫지 않고 기다린다
        if (popup?.placeId == target.placeId) { main.removeCallbacks(popupDone); main.postDelayed(popupDone, 25000) }
        startListening(demoPcm)
    }

    fun like(placeId: String) { liked = if (placeId in liked) liked - placeId else liked + placeId }

    // ---------------------------------------------------------------- 한마디 남기기

    private var listenToken = 0
    /** 거친 말을 들어 '다시 말해 달라'고 청한 참인지 (한 번의 말하기에 한 번만 청한다) */
    private var askedAgain = false
    /** 시연: 다시 들을 때 마이크 대신 쓸 소리 */
    var demoAgainPcm: File? = null
    /** 시연: 다음에 시작하는 듣기 한 번에, 마이크 대신 들려줄 소리 (adb 의 arm 명령으로 얹는다) */
    var demoNextPcm: File? = null

    fun startListening(demoPcm: File? = null, again: Boolean = false) {
        if (current == null) return
        voice.stop()
        if (!again) askedAgain = false
        val token = ++listenToken
        listening = Listening(active = true, again = again, turn = token)
        // 아주 길게 이어지면(2분) 여기까지 듣고 담는다. 긴 말도 일기에는 통째로 담기고, 큰 글귀 자리와 길 위에는 한 문장만 놓인다 (Words.headline)
        main.postDelayed({ if (listenToken == token && listening.active && !listening.saving && !listening.done) input.stop() }, 120_000L)
        val cb = object : VoiceInput.Callback {
            override fun onLevel(level: Float) { listening = listening.copy(level = level) }
            override fun onPartial(text: String) { listening = listening.copy(text = text) }
            override fun onFinal(text: String, signature: FloatArray?) = onHeard(text, signature)
            override fun onClosing() { if (listening.active) listening = listening.copy(saving = true, level = 0f) }
            override fun onError(message: String) { listening = listening.copy(error = message) }
        }
        // (시연: 미리 얹어 둔 소리가 있으면, 화면에서 직접 눌러 시작한 듣기에도 마이크 대신 그 소리를 들려준다)
        val pcm = demoPcm ?: demoNextPcm
        demoNextPcm = null
        if (pcm != null) input.startFromPcm(pcm.readBytes(), cb) else input.startMic(cb)
    }

    fun stopListening() = input.stop()

    /** 듣기를 그만둔다: 듣던 말은 버리고, 아무것도 저장하지 않는다 */
    fun closeListening() {
        if (listening.notice != null) voice.stop()
        input.cancel(); listening = Listening(); pendingQuestion = null; listenTarget = null; listenFriends = false; askedAgain = false
    }

    /**
     * 거친 말은 담지 않는다: 화면과 목소리로 안 된다고 알리고, 다듬어서 다시 말해 달라고 청한다 (묻던 말·남길 자리는 그대로 이어 간다).
     * 처음에는 알린 뒤 곧바로 한 번 더 듣는다. 다시 들은 말도 거칠면 알리기만 하고, 다시 말할지는 맡긴다 (잠시 뒤 스스로 닫힌다).
     */
    private fun refuse(kind: Guard.Kind) {
        val first = !askedAgain
        askedAgain = true
        val notice = Guard.notice(kind)
        val token = ++listenToken
        listening = Listening(active = true, notice = notice, again = !first)
        Log.d("Gildongmu", "거친 말이라 담지 않음 · $kind · ${if (first) "다시 청함" else "다시 듣지 않음"}")
        if (first) voice.say("$notice ${Words.again}") { if (listenToken == token) listenAgain() }
        else {
            voice.say("$notice ${Words.later}")
            main.postDelayed({ if (listenToken == token && listening.notice != null) closeListening() }, 12_000L)
        }
    }

    /** 알림 뒤에(또는 '다시 말할게요'를 눌러) 한 번 더 듣는다 */
    fun listenAgain() {
        if (listening.notice == null) return
        Log.d("Gildongmu", "다시 듣기 시작")
        startListening(demoAgainPcm.also { demoAgainPcm = null }, again = true)
    }

    private fun onHeard(text: String, signature: FloatArray?) {
        if (listening.forEnroll) {
            listening = listening.copy(done = true, text = text)
            enrollTarget?.invoke(signature); enrollTarget = null
            return
        }
        if (text.isBlank()) { listening = listening.copy(error = "목소리가 잘 들리지 않았어요. 한 번만 더 들려줄래요?"); return }
        val me = current ?: return
        // 거친 말(욕설·깎아내리는 말·성적인 말·남을 해치는 말)은 담지 않는다. 안 된다고 알리고, 다듬어서 다시 말해 달라고 청한다.
        Guard.rough(text)?.let { refuse(it); return }
        // 목소리가 지금 계정 주인과 다르면 먼저 확인
        if (settings.voiceCheck && signature != null && me.voice != null) {
            val dMe = VoiceSignature.distance(signature, me.voice)
            Log.d("Gildongmu", "voice distance to ${me.name}: $dMe")
            if (dMe > VoiceSignature.SAME_PERSON) {
                val other = profiles.filter { it.id != me.id && it.voice != null }
                    .map { it to VoiceSignature.distance(signature, it.voice!!) }
                    .onEach { (p, d) -> Log.d("Gildongmu", "voice distance to ${p.name}: $d") }
                    .filter { it.second < VoiceSignature.SAME_PERSON }
                    .minByOrNull { it.second }?.first
                listening = Listening()
                val mm = VoiceMismatch(text, signature, other)
                if (driving) {
                    // 달리는 동안에는 화면으로 묻지 않는다. 차를 세우면 그때 묻는다.
                    pendingMismatch = mm
                    voice.say("목소리가 조금 달라서, 차를 세우면 누구의 일기에 담을지 여쭤볼게요.")
                } else {
                    mismatch = mm
                    voice.say(if (other != null) "혹시 ${other.name}님이신가요?" else "처음 듣는 목소리예요. 누구의 일기에 담을까요?")
                }
                return
            }
        }
        save(me, text)
    }

    fun resolveMismatch(target: Profile?) {
        val m = mismatch ?: return
        mismatch = null
        val owner = target ?: current ?: return
        val other = owner.id != current?.id
        if (other) choose(owner)
        save(owner, m.text, moved = other)
    }

    private fun save(owner: Profile, text: String, moved: Boolean = false) {
        val mood = MoodReader.read(text)
        val now = clock()
        val phase = when {
            !car.parked -> "driving"
            pendingQuestion == AskTemplates.evening.text || pendingQuestion == AskTemplates.lateNight.text || pendingQuestion == AskTemplates.longDrive.text -> "arrive"
            pendingQuestion != null && (pendingQuestion!!.contains("출발") || pendingQuestion!!.contains("퇴근")) || pendingQuestion == AskTemplates.morning.text -> "depart"
            else -> "parked"
        }
        val target = listenTarget
        // 남에게 보이는 자리(길 위·친구)에 올라가는 말은 먼저 AI 필터를 거친다. 일기는 나만 보므로 거르지 않는다.
        // 설정이 '길 위에도 남기기'면, 일기에 담는 말도 말한 그 자리에 이름 없이 남긴다.
        val wantsRoad = target == null && !listenFriends && !moved && settings.share == "road"
        val verdict = if (target != null || listenFriends || wantsRoad) Guard.check(text) else Guard.OK
        val spot = if (wantsRoad && verdict.ok) here else null
        val e = Entry(
            id = store.newId(), profileId = owner.id, time = now.timeInMillis, text = text.trim(), mood = mood,
            scene = Scene(
                rain = car.raining, night = now.get(Calendar.HOUR_OF_DAY) !in 6..18, phase = phase, alone = car.alone,
                view = Look.scene.name, light = Look.part(now).name, // 지금 보고 있는 창밖을 그대로 일기에 남긴다
            ),
            question = pendingQuestion, place = if (verdict.ok) target?.name ?: spot?.name else null,
        )
        store.addEntry(e)
        if (owner.id == current?.id) lastSaidId = e.id
        Log.d("Gildongmu", "일기에 담음: \"${e.text.take(24)}\" (${e.mood.label})")
        pendingQuestion = null
        // 마무리한 날에 다시 남기면, 그날을 살며시 다시 연다
        if (store.closedAt(owner.id, Store.dayKey(e.time)) != null) { store.reopenDay(owner.id, Store.dayKey(e.time)); refreshClosed() }
        if (owner.id == current?.id) entries = store.entries(owner.id)

        if ((listenFriends || target != null) && !verdict.ok) {
            // AI 필터가 막은 말: 남에게는 보이지 않게, 내 일기에만 담는다
            listening = Listening(active = true, done = true, text = e.text, mood = mood, entryId = e.id, blocked = verdict.label)
            voice.say(if (verdict.kind == Guard.Kind.SELF_HARM) Words.reply(e.text, e.id) else "이 말은 다른 사람에게는 보이지 않게, 일기에만 담아 둘게요.")
            main.postDelayed({ if (listening.done && listening.text == e.text) { listening = Listening(); listenTarget = null; listenFriends = false } }, 6500)
            return
        }

        if (listenFriends) {
            // 친구들에게 남기는 오늘의 한마디 (일기에도 함께 담긴다)
            val l = FriendLine(e.id, "me", e.time, e.text, mood, 0, here.name)
            store.saveMyLine(owner.id, l)
            myLine = l
            myLikers = emptyList()
            listening = Listening(active = true, done = true, text = e.text, mood = mood)
            voice.say("친구들에게 오늘의 한마디를 남겼어요.")
            main.postDelayed({ if (listening.done) { listening = Listening(); listenFriends = false } }, 3600)
            // 시연: 친구들이 하나둘 좋아요를 누른다 (실제로는 서버에서 알림)
            listOf(5200L to "도윤", 8200L to "하은").forEach { (ms, who) ->
                main.postDelayed({ if (myLine?.id == l.id) { myLikers = myLikers + who; showToast("${who}님이 오늘 한마디를 좋아해요") } }, ms)
            }
            return
        }

        if (target != null) {
            // 길 위에 남기기: 그 장소(또는 지금 위치)에 한마디를 놓고, 일기에도 담는다
            val w = MyWord(e.id, target.placeId, target.name, target.lat, target.lng, e.text, mood, e.time)
            store.addWord(owner.id, w)
            refreshPlaces()
            val pid = target.placeId ?: "my_${w.id}"
            selectedPlace = pid
            justLeft = JustLeft(pid, System.currentTimeMillis())
            listening = Listening(active = true, done = true, text = e.text, mood = mood)
            voice.say("이곳에 남겼어요. 일기에도 담아 둘게요.")
            main.postDelayed({ if (listening.done) { listening = Listening(); listenTarget = null } }, 3800)
            // 달리는 중에 이어 남겼다면, 그 이야기를 다시 띄워 방금 이어진 말까지 보여 준다
            if (driving && target.placeId != null) {
                main.postDelayed({
                    popup = Popup(pid, System.currentTimeMillis(), auto = false)
                    main.removeCallbacks(popupDone); main.postDelayed(popupDone, 9000)
                }, 2600)
            }
            return
        }

        // '길 위에도 남기기': 말한 그 자리에 이름 없이 남긴다 (지나는 운전자에게 '익명의 길동무'로 보인다)
        if (spot != null) {
            store.addWord(owner.id, MyWord(e.id, null, spot.name, spot.lat, spot.lng, Words.headline(e.text), mood, e.time))
            refreshPlaces()
        }

        // 해 질 무렵부터는, 남긴 뒤에 오늘을 마무리할지도 함께 묻는다
        val evening = now.get(Calendar.HOUR_OF_DAY) >= 17 || now.get(Calendar.HOUR_OF_DAY) < 4
        // (달리는 동안에는 고를 것을 내밀지 않는다. 이제 막 길을 나서는 참이라면 마무리를 묻지 않는다: 집에 가서 물을 일이다.)
        val wrap = evening && !moved && !driving && phase != "depart"
        // 방금 들은 말에 길동무가 건네는 한 줄
        // (AI 필터가 길 위에 올리지 않은 말에는 마음결을 넘겨짚는 답을 하지 않는다: 담담하게 받았다고만 하고, 걸렀다는 것을 알린다)
        val held = wantsRoad && !verdict.ok && verdict.kind != Guard.Kind.SELF_HARM
        val reply = if (held) Words.received else Words.reply(e.text, e.id)
        listening = Listening(
            active = true, done = true, text = e.text, mood = mood, offerWrap = wrap, owner = owner.name.takeIf { moved }, reply = reply,
            entryId = e.id, shared = spot?.name, blocked = if (wantsRoad && !verdict.ok) verdict.label else null,
        )
        // 길 위에도 남기려던 말을 AI 필터가 걸렀으면, 그렇게 했다는 것을 목소리로도 알린다 (개인 정보·광고는 묻지 않고 알아서 거른다)
        val filtered = if (held) " " + Guard.spoken(verdict.kind!!) else ""
        // 답이 이미 '잘 받았어요/적어 둘게요'로 끝났으면 같은 말을 한 번 더 붙이지 않는다
        val closing = if (reply == Words.saved || reply.contains("둘게요") || reply.contains("받았어요")) "" else " ${Words.saved}"
        val spoken = reply + closing + filtered
        voice.say(if (moved) "${owner.name}님의 일기에 담아 둘게요." else if (wrap) "$spoken 오늘은 여기까지 할까요, 한마디 더 할까요?" else spoken)
        main.postDelayed({ if (listening.done && !listening.forEnroll && listening.text == e.text) listening = Listening() }, if (wrap) 15000 else 5600)
    }

    fun deleteEntry(e: Entry) { store.deleteEntry(e); entries = store.entries(e.profileId) }

    /**
     * 담은 말 하나를 길 위에도 남기거나(모두에게, 이름 없이), 길 위에서 내린다(나만 보기).
     * 올릴 때는 AI 필터를 다시 거친다. 막히면 올리지 않고 까닭을 알려 준다.
     */
    fun setShared(entryId: String, on: Boolean) {
        val p = current ?: return
        val e = entries.firstOrNull { it.id == entryId } ?: return
        if (on) {
            val v = Guard.check(e.text)
            if (!v.ok) {
                if (listening.entryId == entryId) listening = listening.copy(blocked = v.label)
                showToast("길 위에는 올리지 않았어요 · ${v.label}")
                return
            }
            val spot = here
            store.addWord(p.id, MyWord(e.id, null, spot.name, spot.lat, spot.lng, Words.headline(e.text), e.mood, e.time))
            store.updateEntry(e.copy(place = spot.name))
            if (listening.entryId == entryId) listening = listening.copy(shared = spot.name, blocked = null)
            showToast("길 위에도 남겼어요 · ${spot.name}")
        } else {
            store.removeWord(p.id, entryId)
            store.updateEntry(e.copy(place = null))
            if (listening.entryId == entryId) listening = listening.copy(shared = null)
            showToast("길 위에서 내렸어요 · 내 일기에만 남아요")
        }
        entries = store.entries(p.id)
        refreshPlaces()
    }

    // ---------------------------------------------------------------- 목소리 등록

    fun enroll(demoPcm: File? = null, done: (FloatArray?) -> Unit) {
        enrollTarget = done
        listening = Listening(active = true, forEnroll = true)
        val cb = object : VoiceInput.Callback {
            override fun onLevel(level: Float) { listening = listening.copy(level = level) }
            override fun onPartial(text: String) { listening = listening.copy(text = text) }
            override fun onFinal(text: String, signature: FloatArray?) = onHeard(text, signature)
            override fun onClosing() { if (listening.active) listening = listening.copy(saving = true, level = 0f) }
            override fun onError(message: String) { listening = listening.copy(error = message); enrollTarget = null }
        }
        if (demoPcm != null) input.startFromPcm(demoPcm.readBytes(), cb, needText = false) else input.startMic(cb, needText = false)
    }

    fun updateSettings(s: Settings) { settings = s; store.settings = s; Look.apply(s.season, s.dayPart, s.scene) }

    // ---------------------------------------------------------------- 시연 도구

    val demoDir: File get() = File(context.filesDir, "demo").apply { mkdirs() }

    /** 평일 아침 8시 30분 전후로 집에서 일터로, 저녁 6시 반 전후로 일터에서 집으로 다닌 기록을 만든다 (자리는 성수동 지도 안의 두 곳) */
    fun seedTrips() {
        val p = current ?: return
        val home = Places.DEMO_HOME; val work = Places.DEMO_WORK
        val list = (1..14).mapNotNull { d ->
            val c = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -d)
                set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 20 + (d * 7) % 20)
            }
            if (c.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY)) null
            else Trip(p.id, c.timeInMillis, c.timeInMillis + 40 * 60_000, from = home, to = work)
        }
        val evening = (1..14).mapNotNull { d ->
            val c = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -d)
                set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 15 + (d * 11) % 35)
            }
            if (c.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY)) null
            else Trip(p.id, c.timeInMillis, c.timeInMillis + 45 * 60_000, from = work, to = home)
        }
        store.saveTrips(p.id, (list + evening).sortedBy { it.start })
    }

    /** 시연: 오늘 것만 남기고 예시 일기를 새로 채운다 */
    fun reseedEntries() {
        val p = current ?: return
        val today = Store.dayKey(System.currentTimeMillis())
        store.keepEntries(p.id) { Store.dayKey(it.time) == today }
        entries = store.entries(p.id)
        seedEntries()
    }

    /** 예시 일기: 지난 2주의 하루들과, 지난 계절의 기억 몇 장 (계절마다 다른 그림일기를 보여 주려고). 여러 번 눌러도 겹치지 않는다. */
    fun seedEntries() {
        val p = current ?: return
        val have = entries.map { it.text }.toSet()
        fun add(c: Calendar, text: String, rain: Boolean = text.contains("비가")) {
            if (text in have) return
            val h = c.get(Calendar.HOUR_OF_DAY)
            store.addEntry(Entry(store.newId(), p.id, c.timeInMillis, text, MoodReader.read(text),
                Scene(rain = rain, night = h !in 6..18, phase = if (h < 12) "depart" else "arrive", alone = true)))
        }
        fun ago(days: Int, h: Int, mi: Int) = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days); set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi); set(Calendar.SECOND, 0) }
        fun on(mo: Int, d: Int, h: Int, mi: Int) = Calendar.getInstance().apply { set(get(Calendar.YEAR), mo - 1, d, h, mi, 0) }
        // (말로 남긴 일기라서, 글로 다듬은 문장이 아니라 차 안에서 혼잣말로 나올 법한 입말로 적어 둔다)
        add(ago(1, 18, 34), "오늘은 칼퇴다. 얼마 만이냐 진짜. 해 떠 있을 때 집에 가니까 기분이 이상하네. 좋다.")
        add(ago(2, 8, 12), "비 오니까 길이 꽉 막힌다. 아홉 시 회의인데 아직 다리도 못 건넜어. 어떡하지 진짜.")
        add(ago(2, 19, 40), "회의는 별말 없이 넘어갔다. 그게 어디야, 다행이지. 오늘은 치킨 시켜 먹어야겠다.")
        add(ago(4, 21, 14), "아 피곤해. 오늘도 야근. 이번 주만 벌써 세 번째다. 그래도 라디오에서 아는 노래 나와서 좀 살 것 같네.")
        add(ago(6, 16, 20), "엄마한테 전화 왔는데 밥은 먹고 다니냐고… 괜히 눈물 날 뻔했다. 이번 주말엔 내려가 봐야겠다.", rain = true)
        add(ago(8, 12, 30), "드디어 금요일. 주말에 애들이랑 강릉 가기로 했는데 벌써 설렌다.")
        add(ago(9, 18, 5), "민수랑 별것도 아닌 걸로 싸웠다. 내가 좀 심했나. 집 가서 먼저 톡 보내야겠다.")
        add(ago(11, 8, 21), "오늘은 길이 하나도 안 막히네. 하늘도 맑고. 이런 날은 그냥 기분이 좋다.")
        // 지난 계절의 기억
        add(on(10, 3, 16, 40), "엄마 모시고 단풍 보러 가는 길. 엄마가 창밖 보면서 계속 웃으시네. 진작 올걸.")
        add(on(7, 18, 19, 40), "퇴근하고 그냥 바다까지 와 버렸다. 바람 진짜 시원하다. 오길 잘했어.")
        add(on(4, 7, 17, 50), "벚꽃 핀 거 봐라. 창문 열고 천천히 가는 중인데 괜히 설렌다.")
        add(on(1, 12, 7, 40), "첫눈 온다. 길 미끄러워서 긴장되긴 하는데, 산이 하얘서 예쁘긴 하네.")
        entries = store.entries(p.id)
    }
}

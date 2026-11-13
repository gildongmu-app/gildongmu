package com.gildongmu.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {

    private lateinit var model: AppModel
    private var lastAutoThink = 0L
    private var demoMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = AppModel(this)
        // "글레오, 일기 쓰자"로 켰다면 여는 장면 없이 바로 듣는다
        model.skipOpening = intent?.action == "START_DIARY"
        // 시연: 런치 화면부터 켜 달라는 명령으로 시작했다면, 화면을 그리기 전에 알아 둔다 (환영 장면이 그 위에 겹치지 않게)
        if (intent?.action == "GD_DEMO" && intent.getStringExtra("cmd") == "launch" && intent.getBooleanExtra("on", true) &&
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) model.forceLaunch = true
        model.start()
        // 차에 타서 앱이 켜지면 일기장이 펼쳐지며 맞는다 (그동안에는 따로 말을 걸지 않는다)
        model.welcoming = savedInstanceState == null && !model.skipOpening && !model.driving && !model.launching
        setContent { GildongmuApp(model) }
        val need = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
        handle(intent)
    }

    @Deprecated("Activity Result API 대신 단순 요청")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        model.startGps()
    }

    override fun onPause() {
        super.onPause()
        model.foreground = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        model.foreground = true
        // 앱을 열었을 때 출발 전(P단)이라면, 지금 말을 걸지 생각해 본다
        val now = System.currentTimeMillis()
        if (model.current != null && model.ask == null && !model.listening.active && now - lastAutoThink > 10 * 60_000 && !demoMode && !model.welcoming) {
            lastAutoThink = now
            window.decorView.postDelayed({ model.think("depart") }, 2500)
        }
    }

    /** 글레오가 "일기 쓰자"를 불렀을 때 (assets/schema.json 의 START_DIARY) */
    private fun handle(intent: Intent?) {
        if (intent?.action == "GD_DEMO" && (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            demoMode = true; model.demoQuiet = true; demo(intent); setIntent(Intent(this, MainActivity::class.java)); return
        }
        // 지나는 길 알림을 눌렀을 때: 그 이야기를 지도에서 바로 보여 준다
        intent?.getStringExtra("place")?.let { id ->
            model.pickingProfile = false; model.screen = Screen.Road
            if (model.driving) model.openPopup(id) else model.selectedPlace = id
            setIntent(Intent(this, MainActivity::class.java)); return
        }
        if (intent?.action != "START_DIARY") return
        Log.d("Gildongmu", "Gleo action START_DIARY")
        window.decorView.postDelayed({ if (model.current != null) model.startListening() }, 1500)
        setIntent(Intent(this, MainActivity::class.java))
    }

    /** 개발용(디버그 빌드에서만): adb 로 시연 상황을 재현한다 */
    private fun demo(i: Intent) {
        val m = model
        fun file(name: String?) = name?.let { java.io.File(m.demoDir, it) }?.takeIf { it.exists() }
        when (i.getStringExtra("cmd")) {
            "profile" -> {
                val name = i.getStringExtra("name") ?: return
                val motif = i.getStringExtra("motif") ?: Motif.entries[m.profiles.size % Motif.entries.size].name
                val color = i.getIntExtra("color", m.profiles.size % AvatarTones.size)
                val f = file(i.getStringExtra("enroll"))
                if (f == null) { m.choose(m.createProfile(name, motif, color, null)); return }
                m.enroll(f) { sig ->
                    m.listening = Listening()
                    val p = m.createProfile(name, motif, color, sig)
                    Log.d("Gildongmu", "demo profile $name voice=${sig?.joinToString(",") { "%.2f".format(it) }}")
                    if (i.getBooleanExtra("select", true)) m.choose(p)
                }
            }
            "choose" -> m.profiles.firstOrNull { it.name == i.getStringExtra("name") }?.let { m.choose(it) }
            "diary" -> file(i.getStringExtra("file"))?.let { m.screen = Screen.Today; m.startListening(it) }
            // 길동무의 목소리: speaker(type: haeun|seoyoon|eunwoo|sanghyeok, say: 들려줄 말)
            "speaker" -> { m.voice.speaker(i.getStringExtra("type") ?: GuideVoice.FEMALE); i.getStringExtra("say")?.let { t -> m.voice.say(t) } }
            // 마이크로 실제 소리가 들어오는지: mic (2초 녹음해 크기를 로그로 남긴다. 모두 0 이면 컴퓨터의 마이크가 에뮬레이터에 닿지 않는 것)
            "mic" -> Thread {
                runCatching {
                    val sr = 16_000
                    @Suppress("MissingPermission")
                    val rec = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION, sr, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, sr)
                    rec.startRecording()
                    val buf = ShortArray(sr / 10); var peak = 0; var sum = 0.0; var n = 0L; var zeros = 0L
                    repeat(20) { val k = rec.read(buf, 0, buf.size); for (j in 0 until k) { val v = kotlin.math.abs(buf[j].toInt()); if (v > peak) peak = v; sum += v.toDouble() * v; n++; if (v == 0) zeros++ } }
                    rec.stop(); rec.release()
                    Log.d("Gildongmu", "MIC peak=$peak rms=${"%.1f".format(kotlin.math.sqrt(sum / maxOf(1L, n)))} zeros=${zeros * 100 / maxOf(1L, n)}% samples=$n")
                }.onFailure { Log.d("Gildongmu", "MIC error ${it.message}") }
            }.start()
            // 거친 말을 듣고 다시 청할 때, 마이크 대신 들려줄 소리: again(file)
            "again" -> m.demoAgainPcm = file(i.getStringExtra("file"))
            // 화면에서 직접 눌러 시작하는 다음 듣기에, 마이크 대신 들려줄 소리: arm(file)
            "arm" -> m.demoNextPcm = file(i.getStringExtra("file"))
            "think" -> {
                val now = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, -i.getIntExtra("ago", 0))      // ago: 며칠 전의 날로 (평일·주말을 골라 재현할 때)
                    set(java.util.Calendar.HOUR_OF_DAY, i.getIntExtra("hour", get(java.util.Calendar.HOUR_OF_DAY)))
                    set(java.util.Calendar.MINUTE, i.getIntExtra("minute", 0))
                }
                m.current?.let { m.store.resetAsks(it.id) }
                m.screen = Screen.Today
                val trip = i.getIntExtra("trip", -1).takeIf { it >= 0 }
                m.think(i.getStringExtra("phase") ?: "depart", trip, now)
            }
            "screen" -> runCatching { Screen.valueOf(i.getStringExtra("name") ?: "Today") }.getOrNull()?.let { s ->
                i.getStringExtra("day")?.let { m.dayKey = it }
                m.pickingProfile = false; m.screen = s
            }
            "pick" -> m.pickingProfile = true
            // 런치 화면 (아직 시작하지 않은 사람에게 보이는 첫 화면): launch(on=true|false)
            "launch" -> if (i.getBooleanExtra("on", true)) m.showLaunch() else { m.forceLaunch = false; m.launchStarted = false; m.pickingProfile = false }
            // 말한 것을 남기는 곳: share(mode: diary|road)
            "maker" -> Thread { Log.d("Gildongmu", "봉인 열림: ${Maker.open(i.getStringExtra("pw") ?: "") != null}") }.start()      // 봉인된 서명이 그 비밀번호로 열리는지 (글은 적지 않는다)
            "share" -> m.updateSettings(m.settings.copy(share = i.getStringExtra("mode") ?: "diary"))
            // AI 시험: ai(text) → 마음결과 필터 판단을 기록에 남긴다
            "ai" -> i.getStringExtra("text")?.let { t -> MoodReader.score(t); Guard.check(t); Log.d("Gildongmu", "AI 답: ${Words.reply(t, "x")}") }
            // 차에 탄 순간: 일기장이 펼쳐지며 인사하고, 이어서 묻는다. board(hour, minute, ask=false 면 인사만)
            "board" -> {
                m.current?.let { m.store.resetAsks(it.id) }
                m.board(if (i.hasExtra("hour")) java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, -i.getIntExtra("ago", 0))
                    set(java.util.Calendar.HOUR_OF_DAY, i.getIntExtra("hour", 12)); set(java.util.Calendar.MINUTE, i.getIntExtra("minute", 0)); set(java.util.Calendar.SECOND, 0)
                } else m.clock(), ask = i.getBooleanExtra("ask", true))
            }
            "delprofile" -> m.profiles.firstOrNull { it.name == i.getStringExtra("name") }?.let { m.deleteProfile(it) }
            "voicecheck" -> m.updateSettings(m.settings.copy(voiceCheck = i.getBooleanExtra("on", true)))
            "rename" -> m.profiles.firstOrNull { it.name == i.getStringExtra("name") }?.let { p -> i.getStringExtra("to")?.let { m.rename(p, it) } }
            "restyle" -> m.profiles.firstOrNull { it.name == i.getStringExtra("name") }?.let {
                m.restyle(it, i.getStringExtra("art") ?: it.emoji, i.getIntExtra("color", it.colorIdx))
                if (m.current?.id == it.id) Skin.art = m.current?.art() ?: Art.AURA
            }
            "probe" -> file(i.getStringExtra("file"))?.let { f ->
                m.input.startFromPcm(f.readBytes(), object : VoiceInput.Callback {
                    override fun onLevel(level: Float) {}
                    override fun onPartial(text: String) {}
                    override fun onFinal(text: String, signature: FloatArray?) {
                        val d = m.profiles.filter { it.voice != null && signature != null }.joinToString { p -> "${p.name}=%.2f".format(VoiceSignature.distance(signature!!, p.voice!!)) }
                        Log.d("Gildongmu", "PROBE ${f.name}: \"$text\" voice[$d]")
                    }
                    override fun onError(message: String) { Log.d("Gildongmu", "PROBE ${f.name}: error $message") }
                })
            }
            "alone" -> { m.car.simulateAlone = if (i.hasExtra("on")) i.getBooleanExtra("on", true) else null; m.carTick++ }
            "seed" -> { m.seedTrips(); if (i.getBooleanExtra("fresh", false)) m.reseedEntries() else if (i.getBooleanExtra("entries", false)) m.seedEntries() }
            "answer" -> m.ask?.let { m.answerAsk(file(i.getStringExtra("file"))) }
            // 길 위 지도: select(id) / clear / leave(file, place) / cmd(v: home|in|out|filter:DIARY)
            "map" -> {
                m.pickingProfile = false; m.screen = Screen.Road
                when (i.getStringExtra("act")) {
                    "select" -> m.selectedPlace = i.getStringExtra("id")
                    "clear" -> m.selectedPlace = null
                    "hide" -> { i.getStringExtra("id")?.let { m.hiddenPlaces = m.hiddenPlaces + it }; m.refreshPlaces() }
                    "unhide" -> { m.hiddenPlaces = emptySet(); m.refreshPlaces() }
                    "like" -> i.getStringExtra("id")?.let { m.like(it) }
                    "leave" -> {
                        val p = m.places.firstOrNull { it.id == i.getStringExtra("place") }
                        m.leaveWord(p?.let { PlaceTarget(it.id, it.name, it.lat, it.lng) } ?: m.here, file(i.getStringExtra("file")))
                    }
                    "cmd" -> m.mapCmd = i.getStringExtra("v")
                    "say" -> m.places.firstOrNull { it.id == m.selectedPlace }?.let { p -> m.voice.say(p.diary?.body ?: p.lead?.text ?: "") }
                }
            }
            // 시연 주행: drive(speed) / stop / reset — GPS가 0.5초마다 들어오는 것처럼
            "drive" -> when (i.getStringExtra("act") ?: "start") {
                "stop" -> m.stopDemoDrive()
                "reset" -> m.resetGps()
                "hold" -> m.holdDemoDrive(true)          // 빨간 신호에 선 것처럼 세운다
                "go" -> m.holdDemoDrive(false)           // 다시 출발
                "speed" -> m.setDemoSpeed(i.getFloatExtra("speed", 12f))
                else -> { m.pickingProfile = false; m.screen = Screen.Road; m.startDemoDrive(i.getFloatExtra("speed", 30f), i.getFloatExtra("from", 0f)) }
            }
            "popup" -> i.getStringExtra("id")?.let { m.openPopup(it) }
            "cleanup" -> { m.resetGps(); m.demoCleanup(i.getBooleanExtra("today", true)) }
            // 시연용 시계: clock(hour, minute) / clock (값 없으면 실제 시각)
            "clock" -> m.demoClock(if (i.hasExtra("hour")) java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, -i.getIntExtra("ago", 0))
                set(java.util.Calendar.HOUR_OF_DAY, i.getIntExtra("hour", 12)); set(java.util.Calendar.MINUTE, i.getIntExtra("minute", 0)); set(java.util.Calendar.SECOND, 0)
            } else null)
            "friends" -> {
                m.pickingProfile = false; m.screen = Screen.Friends
                when (i.getStringExtra("act")) {
                    "post" -> m.postLine(file(i.getStringExtra("file")))
                    "like" -> i.getStringExtra("id")?.let { m.likeLine(it) }
                    "add" -> i.getStringExtra("code")?.let { m.addFriend(it) }
                }
            }
            "notify" -> m.places.firstOrNull { it.id == (i.getStringExtra("id") ?: "p_forest_gate") }?.let { Notify.nearby(applicationContext, it) }
            "wrap" -> { m.pickingProfile = false; m.screen = Screen.Today; m.offer(AskTemplates.wrapUp(m.entries.count { Store.dayKey(it.time) == Store.dayKey(m.clock().timeInMillis) }.coerceAtLeast(1))) }
            // 바탕: look(season: SPRING|SUMMER|AUTUMN|WINTER|auto, part: DAWN|DAY|DUSK|NIGHT|auto,
            //            scene: GINKGO|REEDS|COAST|CITY|RIDGES|SNOW|BLOSSOM|GREEN|RAIN|AURORA|auto)
            "look" -> m.updateSettings(m.settings.copy(
                season = i.getStringExtra("season") ?: m.settings.season, dayPart = i.getStringExtra("part") ?: m.settings.dayPart,
                scene = i.getStringExtra("scene") ?: m.settings.scene,
            ))
            "rain" -> { m.car.simulateRain = if (i.hasExtra("on")) i.getBooleanExtra("on", true) else null; m.carTick++ }
            // 결: art(name: AURA(맑게)|GRAIN(필름)|INK(먹))
            "art" -> m.setArt(Art.from(i.getStringExtra("name")))
            // 바탕 고르기 열기·닫기: picker(on) / 장면 원본 다시 읽기: reload (앱 저장소의 scenes/ 에 넣어 둔 파일을 먼저 쓴다)
            "picker" -> Sheets.scenePicker = i.getBooleanExtra("on", true)
            "reload" -> SceneSrc.reload()
            // 장면 셰이더가 이 기기에서 만들어지는지 확인해 로그에 남긴다.
            //  lint            : 앱에 든 장면 전부
            //  lint(name: key) : 앱 저장소의 lint/<key>.agsl 하나 (제작자료/도구/agslr/lint.sh 가 넣어 준다. 화면의 장면은 건드리지 않는다)
            "lint" -> if (android.os.Build.VERSION.SDK_INT >= 33) {
                val one = i.getStringExtra("name")
                if (one == null) { SceneSrc.reload(); SceneKind.entries.forEach { k -> Log.d("Gildongmu", "LINT ${k.key}: " + if (SceneSrc.shader(applicationContext, k.key) != null) "OK" else "FAIL") } }
                else Log.d("Gildongmu", "LINT $one: " + SceneSrc.check(applicationContext, java.io.File(java.io.File(filesDir, "lint"), "$one.agsl")))
            }
            "endday" -> m.endDay()
            "reopen" -> m.reopenDay()
            "navi" -> m.navi.open()
            "mismatch" -> {
                val target = i.getStringExtra("to")
                if (target == "drop") m.mismatch = null else m.resolveMismatch(m.profiles.firstOrNull { it.name == target })
            }
        }
    }

    override fun onDestroy() {
        model.release()
        super.onDestroy()
    }
}

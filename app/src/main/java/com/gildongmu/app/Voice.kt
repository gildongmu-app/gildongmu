package com.gildongmu.app

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "Gildongmu"
private const val SR = 16_000
private const val CHUNK = 3_200 // 100ms, 16kHz 16bit mono

/**
 * 음성 입력: 앱이 마이크를 직접 듣고(AudioRecord), 같은 소리를 Pleos 음성 인식(ON_DEVICE)에 보낸다.
 * 소리는 메모리에서만 쓰고 저장하지 않는다. 목소리 특징값만 계산한다.
 */
class VoiceInput(private val context: Context) {

    interface Callback {
        fun onLevel(level: Float)
        fun onPartial(text: String)
        fun onFinal(text: String, signature: FloatArray?)
        fun onError(message: String)
        /** 말이 끝났고, 이제 담는 중이다 (목소리 특징을 셈하는 몇 초 동안). 화면이 '담고 있어요'를 보여 줄 수 있게 먼저 알린다. */
        fun onClosing() {}
    }

    private val main = Handler(Looper.getMainLooper())
    private var stt: SpeechToText? = null
    private var sttReady = false
    private var callback: Callback? = null
    @Volatile private var recording = false
    private var pcm = ByteArrayOutputStream()
    private var lastText = ""
    private var finished = false
    /** 듣기 한 번마다 올라가는 번호표. 뒤늦게 도착한 옛 듣기의 타이머·결과는 번호가 달라 버려진다. */
    @Volatile private var session = 0

    private val segments = mutableListOf<String>()
    private var partial = ""
    @Volatile private var inputEnded = false
    private fun text() = Words.tidy((segments + partial).filter { it.isNotBlank() }.joinToString(" ").trim())

    private val listener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdated(stt: String, completed: Boolean) {
            Log.d(TAG, "STT ${if (completed) "final" else "partial"}: $stt")
            val my = session
            main.post {
                if (my != session || finished) return@post
                // 말 사이에 쉬면 문장 단위로 끝나므로, 조각을 이어 붙이고 입력이 끝날 때까지 기다린다
                if (completed) { if (stt.isNotBlank()) segments.add(stt.trim()); partial = "" } else partial = stt
                lastText = text()
                callback?.onPartial(lastText)
                if (completed && inputEnded) main.postDelayed({ if (my == session) finish() }, 400)
            }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() { val my = session; main.postDelayed({ if (my == session && inputEnded) finish() }, 400) }
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "STT error $errorType")
            val my = session
            main.post { if (my == session && !finished && inputEnded) { if (lastText.isNotBlank() || !needText) finish() else fail("음성을 알아듣지 못했어요 ($errorType)") } }
        }
    }

    fun init() {
        if (stt != null) return
        stt = SpeechToText(context.applicationContext, ai.pleos.playground.stt.constant.Mode.ON_DEVICE).also { s ->
            s.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    sttReady = true
                    s.addListener(listener)
                    s.setLocale(Locale.KOREA)
                    Log.d(TAG, "STT ready (ON_DEVICE)")
                }
                override fun onFailure(errorType: ErrorType) { Log.w(TAG, "STT init failed $errorType") }
            })
        }
    }

    fun release() { runCatching { stt?.removeListener(listener); stt?.release() }; stt = null }

    /** 차 마이크로 듣기 */
    @SuppressLint("MissingPermission")
    fun startMic(cb: Callback, needText: Boolean = true) {
        begin(cb, needText)
        val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SR, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, CHUNK * 4))
        }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) { fail("마이크를 열 수 없어요"); return }
        val my = session
        Thread {
            rec.startRecording()
            val buf = ByteArray(CHUNK)
            var heard = false
            var quietMs = 0
            var totalMs = 0
            var bad = 0
            while (recording && my == session) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) { if (n < 0 || ++bad > 50) break; continue }   // 마이크가 끊기면 붙잡고 있지 않는다
                val chunk = buf.copyOf(n)
                pushChunk(chunk)
                val lvl = level(chunk)
                if (lvl > 0.08f) { heard = true; quietMs = 0 } else quietMs += 100
                totalMs += 100
                // 말을 멈추고 1.8초 지나거나 2분이 되면 끝 (길게 이어 말해도 2분까지는 끝까지 듣는다)
                if ((heard && quietMs >= 1800) || totalMs >= 120_000) break
            }
            runCatching { rec.stop(); rec.release() }
            main.post { if (my == session) endInput() }
        }.start()
    }

    /** 시연용: 저장된 음성(16kHz PCM)을 마이크 대신 흘려 넣기 */
    fun startFromPcm(bytes: ByteArray, cb: Callback, needText: Boolean = true) {
        begin(cb, needText)
        val my = session
        Thread {
            var i = 0
            while (recording && my == session && i < bytes.size) {
                val chunk = bytes.copyOfRange(i, minOf(i + CHUNK, bytes.size))
                pushChunk(chunk)
                i += CHUNK
                Thread.sleep(100)
            }
            main.post { if (my == session) endInput() }
        }.start()
    }

    /** 사용자가 '다 말했어요'를 눌렀을 때: 여기까지 들은 것으로 마무리한다 */
    fun stop() { recording = false }

    /** 사용자가 '그만두기'를 눌렀을 때: 듣던 것을 버린다 (아무것도 저장되지 않는다) */
    fun cancel() {
        Log.d(TAG, "듣기를 그만둠 (버림)")
        session++
        recording = false
        finished = true
        callback = null
        runCatching { stt?.completeAudioSend() }
    }

    private var needText = true

    private fun begin(cb: Callback, needText: Boolean) {
        this.needText = needText
        session++
        callback = cb
        recording = true
        finished = false
        inputEnded = false
        lastText = ""
        segments.clear()
        partial = ""
        pcm = ByteArrayOutputStream()
        if (!sttReady) Log.w(TAG, "STT not ready yet")
    }

    private fun pushChunk(chunk: ByteArray) {
        pcm.write(chunk)
        runCatching { stt?.sendAudio(chunk) }.onFailure { Log.w(TAG, "sendAudio failed: ${it.message}") }
        val lvl = level(chunk)
        val my = session
        main.post { if (my == session) callback?.onLevel(lvl) }
    }

    private fun endInput() {
        recording = false
        inputEnded = true
        runCatching { stt?.completeAudioSend() }
        // 음성 인식 결과가 늦게 오면 기다렸다가 마무리 (그사이 새 듣기가 시작됐다면 건드리지 않는다)
        val my = session
        // 받아 적던 말이 이미 다 끝맺어졌으면(걸려 있는 조각이 없으면) 오래 기다릴 까닭이 없다: 잠깐만 더 듣고 마무리한다
        if (partial.isBlank() && segments.isNotEmpty()) main.postDelayed({ if (my == session && !finished && partial.isBlank()) finish() }, 1200L)
        main.postDelayed({ if (my == session && !finished) { if (lastText.isNotBlank() || !needText) finish() else fail("목소리가 잘 들리지 않았어요. 한 번만 더 들려줄래요?") } }, if (needText) 3500L else 800L)
    }

    private fun finish() {
        if (finished) return
        finished = true
        recording = false
        // 목소리 특징을 셈하는 일은 무거워서, 화면을 멈추지 않도록 따로 돌린다
        Log.d(TAG, "듣기를 마침: \"${text().take(24)}\"")
        val my = session
        val bytes = pcm.toByteArray()
        val heard = text()
        val cb = callback
        main.post { if (my == session) cb?.onClosing() }
        Thread {
            val sig = runCatching { VoiceSignature.extract(bytes) }.getOrNull()
            main.post { if (my == session) cb?.onFinal(heard, sig) }
        }.start()
    }

    private fun fail(msg: String) {
        if (finished) return
        finished = true
        recording = false
        callback?.onError(msg)
    }

    private fun level(chunk: ByteArray): Float {
        var sum = 0.0
        var n = 0
        var i = 0
        while (i + 1 < chunk.size) {
            val s = (chunk[i].toInt() and 0xff) or (chunk[i + 1].toInt() shl 8)
            sum += (s.toShort() / 32768.0).pow(2); n++; i += 2
        }
        if (n == 0) return 0f
        val db = 20 * log10(sqrt(sum / n) + 1e-9)
        return ((db + 60) / 50).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * 간이 목소리 특징: 목소리 높이(기본 주파수)와 음색(주파수 대역별 에너지 모양).
 * 시연용 1차 버전이며, 실제 서비스에서는 화자 인식 모델로 교체한다.
 */
object VoiceSignature {
    private const val FRAME = 512
    private const val HOP = 256
    private const val BANDS = 20

    fun extract(bytes: ByteArray): FloatArray? {
        val n = bytes.size / 2
        if (n < SR) return null // 1초 미만이면 판단하지 않음
        val x = DoubleArray(n) { i -> ((bytes[2 * i].toInt() and 0xff) or (bytes[2 * i + 1].toInt() shl 8)).toShort() / 32768.0 }
        val frames = (n - FRAME) / HOP
        val energy = DoubleArray(frames) { f -> var s = 0.0; for (k in 0 until FRAME) { val v = x[f * HOP + k]; s += v * v }; s / FRAME }
        val maxE = energy.maxOrNull() ?: return null
        val thr = maxE * 0.03
        val window = DoubleArray(FRAME) { 0.5 - 0.5 * cos(2 * PI * it / (FRAME - 1)) }
        val edges = melEdges()
        val acc = DoubleArray(BANDS)
        val pitches = ArrayList<Double>()
        var voiced = 0
        val re = DoubleArray(FRAME)
        val im = DoubleArray(FRAME)
        // 목소리의 높이는 몇 프레임에 한 번만 잰다: 가운뎃값을 쓰므로 결과는 같고, 모든 프레임에서 재면 긴 말에서는 몇 초씩 걸려
        // 말을 마친 뒤 일기에 담기까지 한참 기다리게 된다.
        val step = if (frames > 160) 4 else 1
        var seen = 0
        for (f in 0 until frames) {
            if (energy[f] < thr) continue
            val off = f * HOP
            if (seen++ % step == 0) pitch(x, off)?.let { pitches.add(it) }
            for (k in 0 until FRAME) { re[k] = x[off + k] * window[k]; im[k] = 0.0 }
            fft(re, im)
            for (b in 0 until BANDS) {
                var s = 0.0
                for (bin in edges[b] until edges[b + 1]) s += re[bin] * re[bin] + im[bin] * im[bin]
                acc[b] += ln(s + 1e-10)
            }
            voiced++
        }
        if (voiced < 20 || pitches.size < 10) return null
        val mean = acc.map { it / voiced }
        val avg = mean.average()
        val shape = mean.map { (it - avg).toFloat() }
        pitches.sort()
        val f0 = pitches[pitches.size / 2]
        return (shape + ln(f0).toFloat()).toFloatArray()
    }

    /** 0에 가까울수록 같은 사람. */
    fun distance(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.size != BANDS + 1) return 9f
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in 0 until BANDS) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val cosDist = 1 - dot / (sqrt(na * nb) + 1e-9)
        val pitchDiff = abs(a[BANDS] - b[BANDS])
        return (cosDist + 1.5 * pitchDiff).toFloat()
    }

    const val SAME_PERSON = 0.35f

    private fun pitch(x: DoubleArray, off: Int): Double? {
        val minLag = SR / 400
        val maxLag = SR / 70
        if (off + FRAME + maxLag >= x.size) return null
        var e0 = 0.0
        for (k in 0 until FRAME) e0 += x[off + k] * x[off + k]
        if (e0 <= 0) return null
        var best = 0.0; var bestLag = 0
        for (lag in minLag..maxLag) {
            var s = 0.0; var e1 = 0.0
            for (k in 0 until FRAME) { val v = x[off + k + lag]; s += x[off + k] * v; e1 += v * v }
            val r = s / sqrt(e0 * e1 + 1e-12)
            if (r > best) { best = r; bestLag = lag }
        }
        return if (best > 0.5 && bestLag > 0) SR.toDouble() / bestLag else null
    }

    private fun melEdges(): IntArray {
        fun mel(f: Double) = 2595 * log10(1 + f / 700)
        fun inv(m: Double) = 700 * (10.0.pow(m / 2595) - 1)
        val lo = mel(100.0); val hi = mel(6000.0)
        return IntArray(BANDS + 1) { i ->
            val hz = inv(lo + (hi - lo) * i / BANDS)
            (hz / SR * FRAME).toInt().coerceIn(1, FRAME / 2)
        }.also { e -> for (i in 1 until e.size) if (e[i] <= e[i - 1]) e[i] = e[i - 1] + 1 }
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/** 길동무의 목소리 (Pleos TTS, ON_DEVICE). 실패하면 조용히 넘어간다. */
class Voice(context: Context) {
    private var ready = false
    private val main = Handler(Looper.getMainLooper())
    private val tts = ai.pleos.playground.tts.TextToSpeech(context.applicationContext, ai.pleos.playground.tts.constant.Mode.ON_DEVICE)
    // 지금 하는 말이 끝난 뒤에 할 일 (say 의 then). 다른 말이 끼어들거나 멈추면 하지 않는다.
    private var after: (() -> Unit)? = null
    private var turn = 0
    private var saidAt = 0L
    @Volatile private var started = false

    init {
        tts.initialize(object : ai.pleos.playground.tts.listener.OnInitializeListener {
            override fun onSuccess() { ready = true; tts.setLocale(Locale.KOREA); Log.d(TAG, "TTS ready"); speaker?.let { applySpeaker(it) } }
            override fun onFailure(errorType: ai.pleos.playground.tts.constant.ErrorType) { Log.w(TAG, "TTS init failed $errorType") }
        })
        runCatching {
            tts.addEventListener(object : ai.pleos.playground.tts.listener.EventListener {
                override fun onStart() { started = true }
                override fun onReady() {}
                override fun onDone() { main.post { if (started) finish(turn) } }
                override fun onError(errorType: ai.pleos.playground.tts.constant.ErrorType) { main.post { if (started) finish(turn) } }
                override fun onStop() {}
                override fun onUpdatedRms(rms: Double) {}
            })
        }.onFailure { Log.w(TAG, "TTS listener failed ${it.message}") }
    }

    private fun finish(t: Int) {
        if (t != turn) return
        val f = after ?: return
        // (말을 시작하자마자 오는 신호는 앞선 말의 것일 수 있으니 조금 기다렸다가 다시 본다)
        val wait = 700L - (System.currentTimeMillis() - saidAt)
        if (wait > 0) { main.postDelayed({ finish(t) }, wait); return }
        after = null
        f()
    }

    /** 말하는 글을 로그에 남길지 (디버그 빌드에서만 켠다) */
    var logSpoken = false

    // 길동무의 목소리 (Pleos 의 화자 이름). 준비되기 전에 정하면, 준비된 뒤에 맞춘다.
    private var speaker: String? = null
    private fun applySpeaker(type: String) { runCatching { tts.setSpeakerType(type) }.onFailure { Log.w(TAG, "TTS speaker failed ${it.message}") }; Log.d(TAG, "TTS speaker $type") }
    fun speaker(type: String) { if (type == speaker) return; speaker = type; if (ready) applySpeaker(type) }

    /** then: 이 말을 끝까지 한 뒤에 할 일 (예: 이어서 다시 듣기). 중간에 다른 말이 끼어들거나 멈추면 하지 않는다. */
    fun say(text: String, then: (() -> Unit)? = null) {
        val t = ++turn
        after = then; started = false; saidAt = System.currentTimeMillis()
        // 끝났다는 신호가 오지 않아도(소리를 낼 수 없는 환경 등) 글 길이만큼 넉넉히 기다린 뒤에는 이어 간다
        if (then != null) main.postDelayed({ if (t == turn) { started = true; finish(t) } }, 1200L + text.length * 210L)
        if (logSpoken) Log.d("Gildongmu", "SAY $text")      // (시연 영상에 목소리를 얹을 때, 앱이 실제로 말한 글과 때를 여기서 읽는다)
        if (!ready) { Log.w(TAG, "TTS not ready: $text"); return }
        runCatching { tts.speak(text, true) }.onFailure { Log.w(TAG, "TTS speak failed ${it.message}") }
    }

    fun stop() { turn++; after = null; runCatching { tts.stop() } }
    fun release() = runCatching { tts.release() }
}

/**
 * 길동무의 목소리. 처음에는 운전자와 다른 성별의 목소리로 건네고(남성이면 여성 목소리, 여성이면 남성 목소리),
 * 설정에서 언제든 바꿀 수 있다. 이름은 Pleos TextToSpeech 의 한국어 화자(setSpeakerType)다.
 */
object GuideVoice {
    const val FEMALE = "haeun"
    const val MALE = "eunwoo"
    val all = listOf("haeun" to "여성 · 하은", "seoyoon" to "여성 · 서윤", "eunwoo" to "남성 · 은우", "sanghyeok" to "남성 · 상혁")
    fun forGender(gender: String) = if (gender == "F") MALE else FEMALE
    fun of(p: Profile?) = p?.guide?.takeIf { it.isNotBlank() } ?: forGender(p?.gender ?: "")
    fun label(id: String) = all.firstOrNull { it.first == id }?.second ?: id
}


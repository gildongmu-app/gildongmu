package com.gildongmu.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID

data class Profile(
    val id: String,
    val name: String,
    val emoji: String,
    val colorIdx: Int,
    val voice: FloatArray? = null, // 목소리 특징값 (녹음 파일 아님)
    /** 처음 만들 때 고른 성별: "M" 남성 · "F" 여성 · "" 고르지 않음 */
    val gender: String = "",
    /** 길동무의 목소리(Pleos 화자 이름). 비어 있으면 성별에 맞춰 정한다 (GuideVoice.of) */
    val guide: String = "",
)

data class Entry(
    val id: String,
    val profileId: String,
    val time: Long,
    val text: String,
    val mood: Mood,
    val scene: Scene,
    val question: String? = null,
    val place: String? = null, // 길 위에 남긴 한마디라면 그 장소 이름
)

data class Trip(
    val profileId: String, val start: Long, val end: Long? = null,
    /** 떠난 자리와 닿은 자리 (위도 to 경도). 위치를 모를 때는 null. 되풀이되는 자리로 집과 일터를 알아보는 데 쓴다 (Haunts) */
    val from: Pair<Double, Double>? = null, val to: Pair<Double, Double>? = null,
)

data class Settings(
    val askOnlyWhenAlone: Boolean = true,
    val maxAsksPerDay: Int = 2,
    val voiceCheck: Boolean = true,
    /**
     * 창밖 풍경: "auto" 면 날짜·시각을 따르고, 아니면 고른 계절(SPRING…)·시간(DAWN…)·풍경(GINKGO…)으로.
     * 처음에는 길동무의 얼굴인 '빗빛 · 밤'(비 오는 밤의 번진 불빛)이 깔린다. 바탕 고르기에서 '그날의 장면'·'지금 시각'으로 바꾸면 날짜와 시각을 따른다.
     */
    val season: String = "auto",
    val dayPart: String = "NIGHT",
    val scene: String = "RAIN",
    /** 달리는 동안의 바탕: "calm" 잔잔하게(느리게 흐르고 초점을 푼다) · "flow" 차창 밖처럼 흐르게 · "still" 흐르지 않게 */
    val driveMotion: String = "calm",
    /** 다른 자리에 누가 타고 있으면, 첫 화면에 내 일기의 말을 띄우지 않는다 (큰 화면은 모두가 보므로) */
    val hideWhenTogether: Boolean = false,
    /** 말한 것을 남기는 곳: "diary" 내 일기에만(기본) · "road" 길 위에도 남기기 (말한 그 자리에 이름 없이, 지나는 운전자에게 보인다) */
    val share: String = "diary",
)

/** 기기 안 JSON 저장소. 운전자(계정)별로 일기가 분리된다. */
class Store(context: Context) {
    private val dir = File(context.filesDir, "gildongmu").apply { mkdirs() }
    private val profilesFile = File(dir, "profiles.json")
    private val stateFile = File(dir, "state.json")

    // ---------------------------------------------------------------- 계정

    fun profiles(): List<Profile> = readArray(profilesFile).map { o ->
        Profile(
            id = o.getString("id"),
            name = o.getString("name"),
            emoji = o.optString("emoji", "🙂"),
            colorIdx = o.optInt("color", 0),
            voice = o.optJSONArray("voice")?.let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } },
            gender = o.optString("gender", ""), guide = o.optString("guide", ""),
        )
    }

    fun saveProfile(p: Profile) {
        val old = profiles()
        val idx = old.indexOfFirst { it.id == p.id }
        writeProfiles(if (idx >= 0) old.toMutableList().apply { set(idx, p) } else old + p)
    }

    fun deleteProfile(id: String) {
        writeProfiles(profiles().filter { it.id != id })
        listOf("entries_$id.json", "trips_$id.json", "words_$id.json").forEach { n -> android.util.AtomicFile(File(dir, n)).delete() }
    }

    private fun writeProfiles(list: List<Profile>) {
        save(profilesFile, JSONArray(list.map { x ->
            JSONObject().apply {
                put("id", x.id); put("name", x.name); put("emoji", x.emoji); put("color", x.colorIdx)
                x.voice?.let { v -> put("voice", JSONArray(v.map { it.toDouble() })) }
                put("gender", x.gender); put("guide", x.guide)
            }
        }).toString())
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    // ---------------------------------------------------------------- 일기 조각

    fun entries(profileId: String): List<Entry> = readArray(File(dir, "entries_$profileId.json")).map { o ->
        val s = o.optJSONObject("scene") ?: JSONObject()
        Entry(
            id = o.getString("id"),
            profileId = profileId,
            time = o.getLong("time"),
            text = o.getString("text"),
            mood = runCatching { Mood.valueOf(o.getString("mood")) }.getOrDefault(Mood.CALM),
            scene = Scene(
                rain = s.optBoolean("rain"), night = s.optBoolean("night"),
                phase = s.optString("phase", "parked"),
                alone = if (s.has("alone")) s.optBoolean("alone") else null,
                view = s.optString("view").ifBlank { null }, light = s.optString("light").ifBlank { null },
            ),
            question = o.optString("question").ifBlank { null },
            place = o.optString("place").ifBlank { null },
        )
    }.sortedBy { it.time }

    fun addEntry(e: Entry) = writeEntries(e.profileId, entries(e.profileId) + e)

    fun deleteEntry(e: Entry) = writeEntries(e.profileId, entries(e.profileId).filter { it.id != e.id })

    fun updateEntry(e: Entry) = writeEntries(e.profileId, entries(e.profileId).map { if (it.id == e.id) e else it })

    fun keepEntries(profileId: String, keep: (Entry) -> Boolean) = writeEntries(profileId, entries(profileId).filter(keep))

    private fun writeEntries(profileId: String, list: List<Entry>) {
        save(File(dir, "entries_$profileId.json"), JSONArray(list.map { e ->
            JSONObject().apply {
                put("id", e.id); put("time", e.time); put("text", e.text); put("mood", e.mood.name)
                put("scene", JSONObject().apply {
                    put("rain", e.scene.rain); put("night", e.scene.night); put("phase", e.scene.phase)
                    e.scene.alone?.let { put("alone", it) }
                    e.scene.view?.let { put("view", it) }; e.scene.light?.let { put("light", it) }
                })
                e.question?.let { put("question", it) }
                e.place?.let { put("place", it) }
            }
        }).toString())
    }

    // ---------------------------------------------------------------- 길 위에 남긴 한마디

    fun words(profileId: String): List<MyWord> = readArray(File(dir, "words_$profileId.json")).map { o ->
        MyWord(
            id = o.getString("id"), placeId = o.optString("place").ifBlank { null }, name = o.optString("name"),
            lat = o.getDouble("lat"), lng = o.getDouble("lng"), text = o.getString("text"),
            mood = runCatching { Mood.valueOf(o.getString("mood")) }.getOrDefault(Mood.CALM), time = o.getLong("time"),
        )
    }

    fun addWord(profileId: String, w: MyWord) = writeWords(profileId, words(profileId).filter { it.id != w.id } + w)

    /** 길 위에 남긴 한마디를 내린다 (일기에는 그대로 남는다) */
    fun removeWord(profileId: String, id: String) = writeWords(profileId, words(profileId).filter { it.id != id })

    private fun writeWords(profileId: String, list: List<MyWord>) {
        save(File(dir, "words_$profileId.json"), JSONArray(list.map { x ->
            JSONObject().apply {
                put("id", x.id); put("place", x.placeId ?: ""); put("name", x.name); put("lat", x.lat); put("lng", x.lng)
                put("text", x.text); put("mood", x.mood.name); put("time", x.time)
            }
        }).toString())
    }

    // ---------------------------------------------------------------- 하루 마무리

    fun closedAt(profileId: String, day: String): Long? =
        state().optJSONObject("closed_$profileId")?.takeIf { it.has(day) }?.getLong(day)

    fun closeDay(profileId: String, day: String, at: Long) = putState {
        put("closed_$profileId", (optJSONObject("closed_$profileId") ?: JSONObject()).apply { put(day, at) })
    }

    fun reopenDay(profileId: String, day: String) = putState { optJSONObject("closed_$profileId")?.remove(day) }

    // ---------------------------------------------------------------- 친구 (시연: 기기 안에 저장, 실제로는 서버)

    fun friendIds(profileId: String): List<String> =
        state().optJSONArray("friends_$profileId")?.let { a -> List(a.length()) { a.getString(it) } } ?: Friends.seed().map { it.id }

    fun addFriend(profileId: String, id: String) = putState {
        val now = friendIds(profileId)
        if (id !in now) put("friends_$profileId", JSONArray(now + id))
    }

    /** 오늘 친구들에게 남긴 나의 한마디 */
    fun myLine(profileId: String): FriendLine? = state().optJSONObject("myline_$profileId")?.let { o ->
        if (o.optString("day") != dayKey(System.currentTimeMillis())) null
        else FriendLine(o.getString("id"), "me", o.getLong("time"), o.getString("text"),
            runCatching { Mood.valueOf(o.getString("mood")) }.getOrDefault(Mood.CALM), o.optInt("likes"), o.optString("where").ifBlank { null })
    }

    fun saveMyLine(profileId: String, l: FriendLine) = putState {
        put("myline_$profileId", JSONObject().apply {
            put("day", dayKey(l.time)); put("id", l.id); put("time", l.time); put("text", l.text); put("mood", l.mood.name)
            put("likes", l.likes); put("where", l.where ?: "")
        })
    }

    fun likedLines(profileId: String): Set<String> =
        state().optJSONArray("liked_$profileId")?.let { a -> List(a.length()) { a.getString(it) }.toSet() } ?: emptySet()

    fun setLikedLines(profileId: String, ids: Set<String>) = putState { put("liked_$profileId", JSONArray(ids.toList())) }

    /** 시연 정리용: 오늘의 일기 조각은 주어진 것들로 바꾼다 */
    fun clearDemo(profileId: String, today: List<Entry> = emptyList()) {
        File(dir, "words_$profileId.json").delete()
        val key = dayKey(System.currentTimeMillis())
        writeEntries(profileId, (entries(profileId).filter { it.place == null && dayKey(it.time) != key } + today).distinctBy { it.text + "|" + dayKey(it.time) })
        putState { remove("myline_$profileId"); remove("liked_$profileId"); optJSONObject("closed_$profileId")?.remove(dayKey(System.currentTimeMillis())) }
    }

    // ---------------------------------------------------------------- 운행 기록 (질문 판단용)

    fun trips(profileId: String): List<Trip> = readArray(File(dir, "trips_$profileId.json")).map { o ->
        fun at(k: String) = if (o.has(k + "a") && o.has(k + "o")) o.getDouble(k + "a") to o.getDouble(k + "o") else null
        Trip(profileId, o.getLong("start"), if (o.has("end")) o.getLong("end") else null, at("f"), at("t"))
    }

    fun saveTrips(profileId: String, list: List<Trip>) {
        save(File(dir, "trips_$profileId.json"), JSONArray(list.takeLast(200).map { t ->
            JSONObject().apply {
                put("start", t.start); t.end?.let { put("end", it) }
                t.from?.let { put("fa", it.first); put("fo", it.second) }
                t.to?.let { put("ta", it.first); put("to", it.second) }
            }
        }).toString())
    }

    // ---------------------------------------------------------------- 상태와 설정

    private fun state(): JSONObject = load(stateFile)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
    private fun putState(block: JSONObject.() -> Unit) = save(stateFile, state().apply(block).toString())

    /**
     * 파일 쓰기: 새 내용을 다 쓴 뒤에야 옛 내용과 바꾼다 (AtomicFile).
     * 차는 시동을 끄면 전원이 끊기는 기기라, 쓰는 도중에 끊겨도 앞의 일기가 그대로 남아야 한다.
     */
    private fun save(f: File, text: String) {
        val af = android.util.AtomicFile(f)
        var out: java.io.FileOutputStream? = null
        try {
            out = af.startWrite()
            out.write(text.toByteArray(Charsets.UTF_8))
            af.finishWrite(out)
        } catch (e: Exception) {
            out?.let { af.failWrite(it) }
            android.util.Log.e("Gildongmu", "저장하지 못했어요: ${f.name}", e)
        }
    }

    private fun load(f: File): String? {
        return runCatching { String(android.util.AtomicFile(f).readFully(), Charsets.UTF_8) }.getOrNull()
    }

    var currentProfileId: String?
        get() = state().optString("current").ifBlank { null }
        set(v) = putState { put("current", v ?: "") }

    var settings: Settings
        get() = state().optJSONObject("settings")?.let {
            Settings(it.optBoolean("alone", true), it.optInt("max", 2), it.optBoolean("voice", true), it.optString("season", "auto"), it.optString("dayPart", "auto"), it.optString("scene", "auto"), it.optString("drive", "calm"), it.optBoolean("hideTogether", false), it.optString("share", "diary"))
        } ?: Settings()
        set(v) = putState {
            put("settings", JSONObject().apply { put("alone", v.askOnlyWhenAlone); put("max", v.maxAsksPerDay); put("voice", v.voiceCheck); put("season", v.season); put("dayPart", v.dayPart); put("scene", v.scene); put("drive", v.driveMotion); put("hideTogether", v.hideWhenTogether); put("share", v.share) })
        }

    /** 오늘 길동무가 먼저 물어본 횟수 */
    fun asksToday(profileId: String): Int {
        val s = state()
        return if (s.optString("askDay_$profileId") == dayKey(System.currentTimeMillis())) s.optInt("asks_$profileId") else 0
    }

    fun countAsk(profileId: String) = putState {
        val today = dayKey(System.currentTimeMillis())
        val n = if (optString("askDay_$profileId") == today) optInt("asks_$profileId") else 0
        put("askDay_$profileId", today); put("asks_$profileId", n + 1)
    }

    fun resetAsks(profileId: String) = putState { remove("asks_$profileId") }

    private fun readArray(f: File): List<JSONObject> {
        val text = load(f) ?: return emptyList()
        return runCatching { val a = JSONArray(text); List(a.length()) { a.getJSONObject(it) } }.getOrElse {
            // 읽을 수 없는 파일은 덮어쓰지 않고 옆에 남겨 둔다 (나중에 되살릴 수 있게)
            runCatching { f.copyTo(File(f.parentFile, f.name + ".읽지못함"), overwrite = true) }
            android.util.Log.e("Gildongmu", "읽지 못한 파일을 남겨 두었어요: ${f.name}")
            emptyList()
        }
    }

    companion object {
        fun dayKey(t: Long): String {
            val c = Calendar.getInstance().apply { timeInMillis = t }
            return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        }
    }
}

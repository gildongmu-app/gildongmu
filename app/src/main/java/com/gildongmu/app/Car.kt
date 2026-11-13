package com.gildongmu.app

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/** 차의 상태를 지켜보는 눈. 기어, 비(와이퍼), 누가 타고 있는지(좌석 감지). */
class CarWatcher(context: Context, private val onChange: () -> Unit, private val onGear: (from: VehicleGear?, to: VehicleGear?) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context.applicationContext)

    var gear: VehicleGear? = null; private set
    private var wiper = false
    /** 시연용: 비 오는 날을 재현한다 (null 이면 와이퍼 상태를 따른다) */
    var simulateRain: Boolean? = null
    val raining: Boolean get() = simulateRain ?: wiper
    /** 좌석 감지를 지원하지 않는 차는 null */
    var occupied: MutableMap<SeatValue, Boolean>? = null; private set

    val parked get() = gear == null || gear == VehicleGear.GEAR_PARK

    /** 시연용: 에뮬레이터에서 바꿀 수 없는 좌석이 있어 '혼자 탄 상황'을 흉내 낸다 */
    var simulateAlone: Boolean? = null

    /** 운전석 말고 다른 자리에 사람이 있는지. 모르면 null */
    val alone: Boolean?
        get() = simulateAlone ?: occupied?.let { m -> m.filterKeys { it != SeatValue.ROW_1_LEFT }.values.none { it } }

    private val frontWiper = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private val seatListeners = mutableListOf<Pair<SeatArea, SeatOccupancyListener>>()

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = main.post { setGear(gear) }.let { }
        override fun onFailed(e: Exception) { Log.w("Gildongmu", "gear: ${e.message}") }
    }
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) = main.post { wiper = state == WiperState.ON; onChange() }.let { }
        override fun onFailed(e: Exception) { Log.w("Gildongmu", "wiper: ${e.message}") }
    }

    fun start() {
        vehicle.initialize()
        main.postDelayed({
            val g = vehicle.getGear()
            g.getCurrentGearSelectionState({ v -> main.post { gear = v; onChange() } }, { })
            g.registerGearSelection(gearListener)
            val w = vehicle.getWiper()
            w.getCurrentWiperState(frontWiper, { s -> main.post { wiper = s == WiperState.ON; onChange() } }, { })
            w.registerWiperState(frontWiper, wiperListener)
            watchSeats()
        }, 1200)
    }

    private fun watchSeats() {
        val seat = vehicle.getSeat()
        seat.checkSeatOccupancyCapabilities({ areas: List<SeatArea> ->
            Log.d("Gildongmu", "seat occupancy areas: ${areas.map { a -> a.values.joinToString("+") }}")
            if (areas.isEmpty()) return@checkSeatOccupancyCapabilities
            val map = mutableMapOf<SeatValue, Boolean>()
            areas.forEach { area ->
                val key = area.values.firstOrNull() ?: return@forEach
                map[key] = false
                seat.getCurrentSeatOccupancy(area, { v -> Log.d("Gildongmu", "seat $key occupied=$v"); main.post { map[key] = v == true; onChange() } }, { e -> Log.w("Gildongmu", "seat $key read failed: ${e.message}") })
                val l = object : SeatOccupancyListener {
                    override fun onSeatOccupancyUpdated(occupied: Boolean?) = main.post { Log.d("Gildongmu", "seat $key now occupied=$occupied"); map[key] = occupied == true; onChange() }.let { }
                    override fun onFailed(e: Exception) {}
                }
                seat.registerSeatOccupancy(area, l)
                seatListeners += area to l
            }
            main.post { occupied = map; onChange() }
        }, { e -> Log.w("Gildongmu", "seat occupancy not supported: ${e.message}") })
    }

    private fun setGear(v: VehicleGear?) {
        val old = gear
        gear = v
        if (old != v) onGear(old, v)
        onChange()
    }

    /** 이번 운행 거리(km). 모르면 null */
    fun tripKm(cb: (Int?) -> Unit) {
        vehicle.getOdometer().getTripCurrentDrivingDistance({ d -> main.post { cb(d?.roundToInt()) } }, { main.post { cb(null) } })
    }

    fun release() {
        runCatching {
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getWiper().unregisterWiperState(frontWiper, wiperListener)
            seatListeners.forEach { (a, l) -> vehicle.getSeat().unregisterSeatOccupancy(a, l) }
            vehicle.release()
        }
    }
}

/**
 * 늘 가는 곳 — 운행 기록에서 읽어 낸다. 주소를 묻지 않고, 차가 닿은 자리가 되풀이되는 것만 본다.
 * 저녁과 밤에 가장 자주 닿는 곳을 집으로, 평일 아침에 가장 자주 닿는 곳을 일터로 본다 (세 번 넘게 되풀이될 때부터).
 * 기록은 운전자별로 이 기기 안에만 있다.
 */
object Haunts {
    private const val NEAR = 400.0           // 같은 곳으로 보는 거리 (미터): 주차 자리가 날마다 조금씩 달라도 한곳으로
    const val KNOWN = 3                      // 이만큼 되풀이되면 '늘 가는 곳'

    fun meters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dy = (a.first - b.first) * 111_320.0
        val dx = (a.second - b.second) * 111_320.0 * cos(Math.toRadians((a.first + b.first) / 2))
        return hypot(dx, dy)
    }

    fun near(a: Pair<Double, Double>?, b: Pair<Double, Double>?): Boolean = a != null && b != null && meters(a, b) <= NEAR

    /** 그런 때에 닿은 자리들 가운데 가장 자주 닿은 곳 (KNOWN 번 넘게 닿았을 때만) */
    private fun top(trips: List<Trip>, pick: (Calendar) -> Boolean): Pair<Double, Double>? {
        val ends = trips.filter { t -> t.to != null && t.end != null && pick(Calendar.getInstance().apply { timeInMillis = t.end }) }.map { it.to!! }
        return ends.map { c -> c to ends.count { near(c, it) } }.maxByOrNull { it.second }?.takeIf { it.second >= KNOWN }?.first
    }

    /** 집: 저녁과 밤(17시~새벽 4시)에 가장 자주 닿는 곳 */
    fun home(trips: List<Trip>): Pair<Double, Double>? = top(trips) { c -> c.get(Calendar.HOUR_OF_DAY).let { it >= 17 || it < 4 } }

    /** 일터: 평일 아침(5~11시)에 가장 자주 닿는 곳 */
    fun work(trips: List<Trip>): Pair<Double, Double>? =
        top(trips) { c -> c.get(Calendar.HOUR_OF_DAY) in 5..11 && c.get(Calendar.DAY_OF_WEEK) !in listOf(Calendar.SATURDAY, Calendar.SUNDAY) }

    /** 그 자리에 전에 몇 번 닿았는지 (떠난 자리로 적힌 것도 센다) */
    fun visits(trips: List<Trip>, at: Pair<Double, Double>): Int = trips.count { near(it.to, at) || near(it.from, at) }
}

/**
 * 길동무가 먼저 말을 걸지 판단한다.
 * v1은 운행 기록과 차 상태를 보는 규칙 기반이며, 같은 입력을 서버 AI 판단으로 바꿀 수 있게 분리해 둔다.
 */
object Asker {
    data class Input(
        val now: Calendar,
        val phase: String,          // depart | arrive | idle
        val raining: Boolean,
        val alone: Boolean?,
        val trips: List<Trip>,
        val lastEntryTime: Long?,
        val tripMinutes: Int? = null,
        val asksToday: Int,
        val settings: Settings,
        val entriesToday: Int = 0,
        val dayClosed: Boolean = false,
        /** 차에 막 탔을 때 (환영 인사에 이어 묻는 자리) */
        val boarding: Boolean = false,
        /** 지금 차가 있는 자리 (위도 to 경도). 모르면 null */
        val here: Pair<Double, Double>? = null,
    )

    data class Result(val ask: Ask?, val why: String)

    fun decide(i: Input): Result {
        if (i.settings.askOnlyWhenAlone && i.alone == false) return Result(null, "동승자가 있어서 묻지 않아요")
        if (i.asksToday >= i.settings.maxAsksPerDay) return Result(null, "오늘은 이미 ${i.asksToday}번 물었어요")

        val hour = i.now.get(Calendar.HOUR_OF_DAY)
        val nowMs = i.now.timeInMillis
        val lastTrip = i.trips.filter { it.start < nowMs - 60_000 }.maxByOrNull { it.start }

        // 1) 늦은 밤
        if (i.phase != "depart" && (hour >= 23 || hour < 4)) return Result(AskTemplates.lateNight, "밤 ${hour}시에 도착")
        // 2) 오랜만의 운전
        if (i.phase == "depart" && lastTrip != null && nowMs - lastTrip.start > 5 * 86_400_000L) {
            return Result(AskTemplates.longTime, "마지막 운행이 ${(nowMs - lastTrip.start) / 86_400_000L}일 전")
        }
        // 3) 평소와 다른 출발 시간 (같은 시간대 운행들의 중간값과 비교)
        if (i.phase == "depart") {
            val bucket = if (hour < 12) 4..11 else 12..22
            val usual = i.trips.map { Calendar.getInstance().apply { timeInMillis = it.start } }
                .filter { it.get(Calendar.HOUR_OF_DAY) in bucket }
                .map { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
                .sorted()
            if (usual.size >= 5 && hour in bucket) {
                val median = usual[usual.size / 2]
                val diff = hour * 60 + i.now.get(Calendar.MINUTE) - median
                if (abs(diff) >= 60) {
                    val why = "평소 출발 ${median / 60}시 ${median % 60}분, 오늘은 ${abs(diff)}분 ${if (diff < 0) "이르게" else "늦게"}"
                    // 저녁에 평소보다 늦게 나서면 '늦은 퇴근'으로 보고 하루의 고단함을 먼저 묻는다
                    return Result(if (diff < 0) AskTemplates.earlyStart(abs(diff)) else if (hour >= 17) AskTemplates.lateLeave(abs(diff)) else AskTemplates.lateStart(abs(diff)), why)
                }
            }
        }
        // 3-1) 늘 가는 곳과 다른 도착: 닿는 자리가 되풀이되어 알게 된 집·일터와 견준다
        if (i.phase == "arrive" && i.here != null) {
            val past = i.trips.filter { (it.end ?: it.start) < nowMs - 10 * 60_000L }      // 방금 끝난 이번 길은 빼고 본다
            val home = Haunts.home(past)
            val weekday = i.now.get(Calendar.DAY_OF_WEEK) !in listOf(Calendar.SATURDAY, Calendar.SUNDAY)
            val seen = Haunts.visits(past, i.here)
            // 평일 저녁인데 집이 아닌 곳에 닿았을 때 (자주 들르는 곳이면 묻지 않는다)
            if (weekday && hour in 17..22 && home != null && !Haunts.near(i.here, home) && seen < Haunts.KNOWN) {
                return Result(AskTemplates.detour, "평일 저녁, 집이 아닌 곳에 도착")
            }
            // 운행 기록에 한 번도 없던 곳에 닿았을 때
            if (seen == 0 && past.count { it.to != null } >= 6) return Result(AskTemplates.newPlace, "운행 기록에 없던 곳에 도착")
        }
        // 4) 비 오는 날
        if (i.raining) return Result(AskTemplates.rain, "와이퍼가 움직이고 있어요")
        // 5) 먼 길 끝
        if (i.phase == "arrive" && (i.tripMinutes ?: 0) >= 90) return Result(AskTemplates.longDrive, "${i.tripMinutes}분을 달려 도착")
        // 6) 오래 머무름
        if (i.phase == "idle") return Result(AskTemplates.longIdle, "시동을 켠 채 한참 머물러 있어요")
        // 6-1) 저녁 귀가, 오늘 남긴 마음이 있으면: 더 남길지, 오늘을 마무리할지 묻는다
        if (i.phase == "arrive" && hour in 17..22 && i.entriesToday > 0 && !i.dayClosed) {
            return Result(AskTemplates.wrapUp(i.entriesToday), "저녁 도착, 오늘 남긴 마음 ${i.entriesToday}개")
        }
        // 7) 그날의 첫 길 / 저녁 귀가에 오늘 기록이 없을 때
        val wroteRecently = i.lastEntryTime?.let { it in (nowMs - 6 * 3_600_000L)..nowMs } == true
        if (!wroteRecently) {
            if (i.phase == "depart" && hour in 5..10) return Result(AskTemplates.morning, "오늘의 첫 출발")
            if (i.phase == "arrive" && hour in 17..22) {
                val atHome = Haunts.near(i.here, Haunts.home(i.trips))
                return Result(if (atHome) AskTemplates.homeArrive else AskTemplates.evening, if (atHome) "저녁, 집에 도착" else "저녁 도착, 아침 이후 남긴 마음 없음")
            }
            // 8) 차에 막 탔을 때에는, 특별한 일이 없어도 인사에 이어 오늘이 어땠는지 가볍게 묻는다
            if (i.boarding && !i.dayClosed) {
                // 저녁에 일터(평일 아침마다 닿던 곳)에서 나서는 길이면 퇴근길로 알아본다
                if (hour in 16..22 && Haunts.near(i.here, Haunts.work(i.trips))) return Result(AskTemplates.leaveWork, "저녁, 일터에서 출발")
                return Result(if (hour in 11..16) AskTemplates.daytime else AskTemplates.evening, "차에 탄 순간의 인사")
            }
        }
        return Result(null, "특별한 순간이 아니라서 조용히 있을게요")
    }
}

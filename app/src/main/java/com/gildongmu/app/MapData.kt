package com.gildongmu.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot

/**
 * 실제 지도 데이터 — 성수동·서울숲·한강 일대 (© OpenStreetMap contributors, ODbL).
 * assets/map_seongsu.bin 은 OSM 데이터를 작게 줄인 것. 좌표는 지도 중심 기준 미터 (x 동쪽, y 남쪽).
 * 실제 서비스에서는 이 자리에 정식 지도 SDK 또는 플랫폼 지도를 쓴다.
 */
class GeoMap(
    val lat0: Double, val lon0: Double, val mlat: Double, val mlon: Double,
    val hx: Float, val hy: Float,
    val water: Path, val holes: Path, val parks: Path, val pitches: Path, val buildings: Path,
    val roads: Array<Path>, val bridges: Array<Path>, val rail: Path,
    val labels: List<MapLabel>,
) {
    fun xy(lat: Double, lng: Double) = PointF(((lng - lon0) * mlon).toFloat(), (-(lat - lat0) * mlat).toFloat())
    fun inside(p: PointF) = p.x in -hx..hx && p.y in -hy..hy

    companion object {
        @Volatile private var cached: GeoMap? = null

        fun get(ctx: Context): GeoMap = cached ?: synchronized(this) {
            cached ?: parse(ctx.assets.open("map_seongsu.bin").use { it.readBytes() }).also { cached = it }
        }

        private fun parse(bytes: ByteArray): GeoMap {
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also { b.get(it) }
            require(String(magic) == "GDM1") { "지도 파일 형식이 달라요" }
            val lat0 = b.float.toDouble(); val lon0 = b.float.toDouble()
            val mlat = b.float.toDouble(); val mlon = b.float.toDouble()
            val hx = b.float; val hy = b.float
            val water = Path(); val holes = Path(); val parks = Path(); val pitches = Path(); val buildings = Path(); val rail = Path()
            val roads = Array(10) { Path() }
            val bridges = Array(10) { Path() }
            repeat(b.int) {
                val layer = b.get().toInt() and 0xFF
                val cls = b.get().toInt() and 0xFF
                val flags = b.short.toInt() and 0xFFFF
                val rings = b.short.toInt() and 0xFFFF
                val target = when (layer) {
                    0 -> if (flags and 1 != 0) bridges[cls.coerceIn(0, 9)] else roads[cls.coerceIn(0, 9)]
                    1 -> water
                    2 -> if (cls == 1) pitches else parks
                    3 -> buildings
                    4 -> rail
                    else -> holes
                }
                repeat(rings) {
                    val k = b.short.toInt() and 0xFFFF
                    for (i in 0 until k) {
                        val x = b.short / 10f; val y = b.short / 10f
                        if (i == 0) target.moveTo(x, y) else target.lineTo(x, y)
                    }
                    if (layer == 1 || layer == 2 || layer == 3 || layer == 5) target.close()
                }
            }
            val labels = ArrayList<MapLabel>()
            repeat(b.int) {
                val kind = b.get().toInt() and 0xFF
                val x = b.float; val y = b.float; val a = b.float
                val len = b.short.toInt() and 0xFFFF
                val t = ByteArray(len).also { b.get(it) }
                labels += MapLabel(kind, x, y, a, String(t, Charsets.UTF_8))
            }
            return GeoMap(lat0, lon0, mlat, mlon, hx, hy, water, holes, parks, pitches, buildings, roads, bridges, rail, labels)
        }
    }
}

/** kind: 0 동네, 1 역, 2 공원, 3 물, 4 큰길, 5 길 */
data class MapLabel(val kind: Int, val x: Float, val y: Float, val angle: Float, val text: String)

/** 차 화면용 어두운 지도 바탕을 한 번 그려 둔다 (이후엔 원근만 바꿔 얹는다) */
object MapArt {
    const val PX = 1.4f

    const val LAND = 0xFF14132B.toInt()
    private const val PARK = 0xFF15293A.toInt()
    private const val PITCH = 0xFF1A3442.toInt()
    private const val WATER = 0xFF10224E.toInt()
    private const val SHORE = 0xFF3B5FA8.toInt()
    private const val BUILDING = 0xFF1D1C3B.toInt()
    private const val BUILDING_EDGE = 0xFF2B2A52.toInt()
    private const val CASING = 0xFF0C0B1C.toInt()
    private const val RAIL = 0xFF5A5788.toInt()

    // 0 고속, 1 간선, 2 주도로, 3 보조, 4 3차, 5 동네길, 6 생활길, 7 이면, 8 큰 연결로, 9 작은 연결로
    private val WIDTH = floatArrayOf(9.6f, 9f, 8f, 6.8f, 5.6f, 4.0f, 3.2f, 2.7f, 4.8f, 4.2f)
    private val COLOR = intArrayOf(
        0xFF575288.toInt(), 0xFF544F84.toInt(), 0xFF4E497C.toInt(), 0xFF464272.toInt(), 0xFF3D3A66.toInt(),
        0xFF323058.toInt(), 0xFF2D2B50.toInt(), 0xFF292748.toInt(), 0xFF4A467A.toInt(), 0xFF3F3B6A.toInt(),
    )
    /** 큰길 한가운데를 흐르는 가는 빛 (0 고속, 1 간선, 2 주도로, 8 큰 연결로). 넓은 띠 대신 이 빛이 길을 그린다. */
    private val THREAD = intArrayOf(0xFFB4AEF0.toInt(), 0xFFABA5E6.toInt(), 0xFF9A94D6.toInt(), 0, 0, 0, 0, 0, 0xFF918BCC.toInt(), 0)
    private val ORDER = intArrayOf(7, 6, 5, 9, 4, 3, 8, 2, 1, 0)

    @Volatile private var base: Bitmap? = null

    /** 이미 그려 둔 바탕 (없으면 null) */
    val ready: Bitmap? get() = base

    fun base(map: GeoMap): Bitmap = base ?: synchronized(this) { base ?: render(map).also { base = it } }

    private fun render(map: GeoMap): Bitmap {
        val w = (map.hx * 2 * PX).toInt()
        val h = (map.hy * 2 * PX).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)   // 4700×4000쯤 되는 그림이라, 색 깊이를 낮춰 메모리를 절반으로 쓴다
        val c = Canvas(bmp)
        c.drawColor(LAND)
        c.translate(map.hx * PX, map.hy * PX)
        c.scale(PX, PX)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

        fill.color = PARK; c.drawPath(map.parks, fill)
        fill.color = PITCH; c.drawPath(map.pitches, fill)
        fill.color = WATER; c.drawPath(map.water, fill)
        fill.color = LAND; c.drawPath(map.holes, fill)
        line.color = SHORE; line.strokeWidth = 3f; c.drawPath(map.water, line)

        fill.color = BUILDING; c.drawPath(map.buildings, fill)
        line.color = BUILDING_EDGE; line.strokeWidth = 0.9f; c.drawPath(map.buildings, line)

        for (k in ORDER) { line.color = CASING; line.strokeWidth = WIDTH[k] + 2.6f; c.drawPath(map.roads[k], line) }
        for (k in ORDER) { line.color = COLOR[k]; line.strokeWidth = WIDTH[k]; c.drawPath(map.roads[k], line) }

        line.color = RAIL; line.strokeWidth = 2.4f; line.strokeCap = Paint.Cap.BUTT
        line.pathEffect = DashPathEffect(floatArrayOf(7f, 5f), 0f)
        c.drawPath(map.rail, line)
        line.pathEffect = null; line.strokeCap = Paint.Cap.ROUND

        for (k in ORDER) { line.color = 0xFF0A0A1E.toInt(); line.strokeWidth = WIDTH[k] + 3.4f; c.drawPath(map.bridges[k], line) }
        for (k in ORDER) { line.color = COLOR[k]; line.strokeWidth = WIDTH[k]; c.drawPath(map.bridges[k], line) }
        for (k in intArrayOf(8, 2, 1, 0)) {
            line.color = THREAD[k]; line.alpha = if (k == 8) 120 else 165; line.strokeWidth = if (k <= 1) 1.7f else 1.4f
            c.drawPath(map.roads[k], line); c.drawPath(map.bridges[k], line)
        }
        line.alpha = 255

        // 이만큼 큰 그림을 보통 그림으로 두면, 그릴 때마다 그래픽 메모리의 한도(약 100MB)를 넘겨 매 프레임 다시 올리게 된다
        // (지도 화면이 초당 20장 남짓으로 떨어진다). 한 번 그래픽 메모리에 올려 둔 그림으로 바꿔 쓴다: 초당 56장.
        // (기기가 이 크기를 받지 못하면 보통 그림 그대로 쓴다. 느리지만 그려지기는 한다.)
        val hw = runCatching { bmp.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
        if (hw != null) { bmp.recycle(); return hw }
        bmp.setHasMipMap(true)
        bmp.prepareToDraw()
        return bmp
    }

    private fun lighten(c: Int): Int {
        fun ch(v: Int) = (v + 22).coerceAtMost(255)
        return (0xFF shl 24) or (ch(c shr 16 and 0xFF) shl 16) or (ch(c shr 8 and 0xFF) shl 8) or ch(c and 0xFF)
    }
}

/** 지도 평면(미터) → 화면. 차 내비처럼 살짝 눕혀서 본다. */
class MapProjection {
    val matrix = Matrix()
    private val inverse = Matrix()
    private val camera = Camera()
    private val tmp = Matrix()
    private val pts = FloatArray(4)
    var zoom = 1f; private set

    fun update(cx: Float, cy: Float, zoom: Float, tilt: Float, heading: Float, ax: Float, ay: Float) {
        this.zoom = zoom
        matrix.setTranslate(-cx, -cy)
        matrix.postRotate(-heading)
        matrix.postScale(zoom, zoom)
        camera.save()
        camera.setLocation(0f, 0f, -12f)
        camera.rotateX(tilt)
        camera.getMatrix(tmp)
        camera.restore()
        matrix.postConcat(tmp)
        matrix.postTranslate(ax, ay)
        matrix.invert(inverse)
    }

    fun screen(x: Float, y: Float, out: FloatArray = FloatArray(2)): FloatArray {
        out[0] = x; out[1] = y; matrix.mapPoints(out); return out
    }

    fun ground(sx: Float, sy: Float): PointF {
        pts[0] = sx; pts[1] = sy; inverse.mapPoints(pts, 0, pts, 0, 1); return PointF(pts[0], pts[1])
    }

    /** 그 자리에서 1미터가 화면에서 몇 픽셀인지 (멀수록 작다) */
    fun localScale(x: Float, y: Float): Float {
        pts[0] = x; pts[1] = y; pts[2] = x + 10f; pts[3] = y
        matrix.mapPoints(pts)
        return hypot(pts[2] - pts[0], pts[3] - pts[1]) / 10f
    }

    /** 지도 위 방향(도, 0 = 동쪽, 시계 방향)이 화면에서 가리키는 방향 (뒤집지 않음) */
    fun screenDir(x: Float, y: Float, deg: Float): Float {
        val r = Math.toRadians(deg.toDouble())
        pts[0] = x; pts[1] = y; pts[2] = x + 20f * kotlin.math.cos(r).toFloat(); pts[3] = y + 20f * kotlin.math.sin(r).toFloat()
        matrix.mapPoints(pts)
        return Math.toDegrees(kotlin.math.atan2((pts[3] - pts[1]).toDouble(), (pts[2] - pts[0]).toDouble())).toFloat()
    }

    /** 지도 위 방향(도)이 화면에서는 몇 도로 보이는지 (글자가 뒤집히지 않게 -90..90) */
    fun screenAngle(x: Float, y: Float, deg: Float): Float {
        val r = Math.toRadians(deg.toDouble())
        pts[0] = x; pts[1] = y; pts[2] = x + 20f * kotlin.math.cos(r).toFloat(); pts[3] = y + 20f * kotlin.math.sin(r).toFloat()
        matrix.mapPoints(pts)
        var a = Math.toDegrees(kotlin.math.atan2((pts[3] - pts[1]).toDouble(), (pts[2] - pts[0]).toDouble())).toFloat()
        if (a > 90f) a -= 180f
        if (a < -90f) a += 180f
        return a
    }
}

package app.delve.ui.art

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import app.delve.domain.Floor
import app.delve.domain.RoomKind
import app.delve.domain.Run
import app.delve.domain.hash
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A map symbol to stamp in a room. */
class Mark(val x: Float, val y: Float, val art: Int, val size: Float, val alpha: Float, val done: Boolean)
class Label(val x: Float, val y: Float, val text: String)

/**
 * What the hero knows of the floor, as drawing geometry in map pixels: inked corridors and rooms where they
 * have walked, pencilled ones they have only glimpsed, rock hatching along every known wall, rubble where a
 * corridor caved in. Built off the main thread whenever the knowledge changes; drawn every frame.
 */
class MapScene(
    val ink: Path, val floor: Path, val roomInk: Path, val roomFloor: Path,
    val pencil: Path, val pencilRooms: Path,
    val wash: Path, val hatch: FloatArray, val marks: List<Mark>, val unknown: List<Mark>, val rubble: List<Mark>,
    val labels: List<Label>, val bounds: RectF,
)

object MapPainter {
    /** Corridor half-width and room radius, map pixels (1.6 m each). */
    const val HW = 2.5f
    const val ROOM = 5.4f
    const val WALL = .55f

    private val circle = setOf(RoomKind.SPRING, RoomKind.GROTTO, RoomKind.RIVER, RoomKind.BOSS, RoomKind.SHAFT, RoomKind.SHRINE)

    fun roomSize(k: RoomKind) = when (k) {
        RoomKind.BOSS -> 8f; RoomKind.MERCHANT, RoomKind.CRYPT, RoomKind.VAULT -> 6.2f; RoomKind.START, RoomKind.DOWN, RoomKind.UP -> 6f; RoomKind.EMPTY -> 4.6f
        else -> ROOM
    }

    fun build(f: Floor, run: Run, ru: Boolean): MapScene {
        val g = f.g
        val knownAll = run.mapped
        val visited = run.visited
        val seen = run.seen
        val ink = Path(); val floor = Path(); val roomInk = Path(); val roomFloor = Path()
        val pencil = Path(); val pencilRooms = Path(); val wash = Path()
        val segs = ArrayList<FloatArray>()          // known centrelines, for the hatching
        val discs = ArrayList<FloatArray>()         // known rooms: x, y, r
        val marks = ArrayList<Mark>(); val unknown = ArrayList<Mark>(); val rubble = ArrayList<Mark>(); val labels = ArrayList<Label>()
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        fun grow(x: Float, y: Float) { x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y) }

        for (e in f.edges) {
            val edge = g.edges[e]
            val open = e in f.open
            val explored = e in run.explored
            val near = edge.a in visited || edge.b in visited
            val glimpsed = (edge.a in seen && edge.b in seen) || near || knownAll
            if (!open) {
                // a caved-in corridor shows as a stub from a known room, choked with rubble
                for (end in listOf(edge.a, edge.b)) if (end in visited) {
                    val stub = stub(edge.pts, end == edge.a, roomSize(f.rooms[end]?.kind ?: RoomKind.EMPTY) + 5f)
                    val p = wobble(stub, e * 7 + end)
                    ink.addPath(p); floor.addPath(p); wash.addPath(p)
                    segs += stub
                    val ex = stub[stub.size - 2]; val ey = stub[stub.size - 1]
                    rubble += Mark(ex, ey, 14, 7.5f, 1f, false)
                    grow(ex, ey)
                }
                continue
            }
            if (!explored && !glimpsed) continue
            val p = wobble(edge.pts, e)
            wash.addPath(p)
            segs += edge.pts
            if (explored) { ink.addPath(p); floor.addPath(p) } else pencil.addPath(p)
            for (i in edge.pts.indices step 2) grow(edge.pts[i], edge.pts[i + 1])
        }
        for (n in f.nodes) {
            val nd = g.nodes[n]
            val room = f.rooms[n] ?: continue
            val v = n in visited
            val s = n in seen || knownAll
            if (!v && !s) continue
            val r = roomSize(room.kind)
            val angle = nd.edges.firstOrNull()?.let { e -> val p = g.edges[e].pts; atan2(p[3] - p[1], p[2] - p[0]) } ?: 0f
            discs += floatArrayOf(nd.x, nd.y, r)
            grow(nd.x - r, nd.y - r); grow(nd.x + r, nd.y + r)
            if (v) {
                shape(roomInk, room.kind, nd.x, nd.y, r + WALL * 1.6f, angle, n)
                shape(roomFloor, room.kind, nd.x, nd.y, r, angle, n)
            } else shape(pencilRooms, room.kind, nd.x, nd.y, r, angle, n)
            wash.addCircle(nd.x, nd.y, r, Path.Direction.CW)
            val done = n in run.resolved
            val art = when (room.kind) {
                RoomKind.EMPTY -> -1
                RoomKind.LAIR, RoomKind.CRYPT -> if (done) room.kind.art.takeIf { room.kind == RoomKind.CRYPT } ?: -1 else room.kind.art
                RoomKind.BOSS -> if (done) RoomKind.DOWN.art else RoomKind.BOSS.art
                else -> room.kind.art
            }
            if (v || knownAll) {
                if (art >= 0) marks += Mark(nd.x, nd.y, art, r * 1.55f, if (v) (if (done && room.kind !in setOf(RoomKind.DOWN, RoomKind.UP, RoomKind.START, RoomKind.MERCHANT)) .42f else 1f) else .5f, done)
                if (v && room.above.isNotBlank()) labels += Label(nd.x, nd.y + r + 5.5f, room.above)
            } else unknown += Mark(nd.x, nd.y, -1, r, 1f, false)
        }
        val hatch = hatch(segs, discs, x0 - 16, y0 - 16, x1 + 16, y1 + 16)
        return MapScene(ink, floor, roomInk, roomFloor, pencil, pencilRooms, wash, hatch, marks, unknown, rubble, labels, RectF(x0, y0, x1, y1))
    }

    /** The first [len] pixels of a corridor from one end. */
    private fun stub(pts: FloatArray, fromA: Boolean, len: Float): FloatArray {
        val p = if (fromA) pts else reverse(pts)
        val out = ArrayList<Float>(); out += p[0]; out += p[1]
        var acc = 0f; var i = 0
        while (i + 3 < p.size) {
            val l = hypot(p[i + 2] - p[i], p[i + 3] - p[i + 1])
            if (acc + l >= len) { val t = (len - acc) / l; out += p[i] + (p[i + 2] - p[i]) * t; out += p[i + 1] + (p[i + 3] - p[i + 1]) * t; break }
            out += p[i + 2]; out += p[i + 3]; acc += l; i += 2
        }
        return out.toFloatArray()
    }

    private fun reverse(p: FloatArray): FloatArray { val n = p.size / 2; val o = FloatArray(p.size); for (i in 0 until n) { o[2 * i] = p[2 * (n - 1 - i)]; o[2 * i + 1] = p[2 * (n - 1 - i) + 1] }; return o }

    /** A corridor's line resampled with a slight hand tremor; the ends stay on the junctions. */
    private fun wobble(pts: FloatArray, seed: Int): Path {
        val path = Path()
        val total = run { var s = 0f; var i = 0; while (i + 3 < pts.size) { s += hypot(pts[i + 2] - pts[i], pts[i + 3] - pts[i + 1]); i += 2 }; s }
        val ph = (hash(seed, 3) * 6.28).toFloat(); val ph2 = (hash(seed, 5) * 6.28).toFloat()
        var acc = 0f
        path.moveTo(pts[0], pts[1])
        var i = 0
        while (i + 3 < pts.size) {
            val ax = pts[i]; val ay = pts[i + 1]; val bx = pts[i + 2]; val by = pts[i + 3]
            val l = hypot(bx - ax, by - ay)
            if (l > 0) {
                val nx = -(by - ay) / l; val ny = (bx - ax) / l
                val steps = max(1, (l / 3f).toInt())
                for (k in 1..steps) {
                    val t = k.toFloat() / steps
                    val s = acc + l * t
                    val fade = min(1f, min(s, total - s) / 6f).coerceAtLeast(0f)
                    val o = (sin(s * .55f + ph) * .32f + sin(s * .21f + ph2) * .22f) * fade
                    path.lineTo(ax + (bx - ax) * t + nx * o, ay + (by - ay) * t + ny * o)
                }
            }
            acc += l; i += 2
        }
        return path
    }

    private fun shape(p: Path, k: RoomKind, x: Float, y: Float, r: Float, angle: Float, seed: Int) {
        if (k in circle) {
            // a hand-drawn circle: twelve points with a little wobble
            val n = 14
            for (i in 0..n) {
                val a = i * 2 * PI.toFloat() / n
                val rr = r * (1 + (hash(seed, i % n) - .5).toFloat() * .07f)
                val px = x + cos(a) * rr; val py = y + sin(a) * rr
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            p.close()
        } else {
            val hx = r; val hy = r * .82f
            val c = cos(angle); val s = sin(angle)
            val corners = listOf(-hx to -hy, hx to -hy, hx to hy, -hx to hy)
            corners.forEachIndexed { i, (cx, cy) ->
                val j = (hash(seed, i + 9) - .5).toFloat() * .5f
                val px = x + (cx + j) * c - (cy - j) * s; val py = y + (cx + j) * s + (cy - j) * c
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            p.close()
        }
    }

    /**
     * Rock hatching in the band just outside the known walls: little clusters of three parallel strokes at
     * random angles, thinning out away from the wall, as on old hand-inked dungeon maps.
     */
    private fun hatch(segs: List<FloatArray>, discs: List<FloatArray>, x0: Float, y0: Float, x1: Float, y1: Float): FloatArray {
        if (segs.isEmpty() && discs.isEmpty()) return FloatArray(0)
        val cell = 10f
        val grid = HashMap<Long, MutableList<FloatArray>>()
        fun key(cx: Int, cy: Int) = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)
        for (p in segs) {
            var i = 0
            while (i + 3 < p.size) {
                val s = floatArrayOf(p[i], p[i + 1], p[i + 2], p[i + 3])
                val a = (min(s[0], s[2]) / cell).toInt() - 1; val b = (max(s[0], s[2]) / cell).toInt() + 1
                val c = (min(s[1], s[3]) / cell).toInt() - 1; val d = (max(s[1], s[3]) / cell).toInt() + 1
                for (x in a..b) for (y in c..d) grid.getOrPut(key(x, y)) { ArrayList() } += s
                i += 2
            }
        }
        val dgrid = HashMap<Long, MutableList<FloatArray>>()
        for (dc in discs) {
            val a = ((dc[0] - dc[2]) / cell).toInt() - 1; val b = ((dc[0] + dc[2]) / cell).toInt() + 1
            val c = ((dc[1] - dc[2]) / cell).toInt() - 1; val d = ((dc[1] + dc[2]) / cell).toInt() + 1
            for (x in a..b) for (y in c..d) dgrid.getOrPut(key(x, y)) { ArrayList() } += dc
        }
        val inner = HW + WALL + .6f
        val band = 7.5f
        val out = ArrayList<Float>(8000)
        val step = 2.7f
        var y = y0
        var row = 0
        while (y < y1) {
            var x = x0 + if (row % 2 == 0) 0f else step / 2
            var col = 0
            while (x < x1) {
                val jx = x + (hash(row, col) - .5).toFloat() * step * .7f
                val jy = y + (hash(col, row + 77) - .5).toFloat() * step * .7f
                val cx = (jx / cell).toInt(); val cy = (jy / cell).toInt()
                var best = Float.MAX_VALUE
                grid[key(cx, cy)]?.forEach { s -> best = min(best, segDist(jx, jy, s) - HW) }
                dgrid[key(cx, cy)]?.forEach { d -> best = min(best, hypot(jx - d[0], jy - d[1]) - d[2]) }
                val dd = best + HW
                if (dd > inner && dd < inner + band) {
                    val t = (dd - inner) / band
                    if (hash(col * 3 + 1, row * 5 + 2) > t * t * 1.1) {
                        val a = (hash(col + 11, row + 13) * PI).toFloat()
                        val ca = cos(a); val sa = sin(a)
                        val len = 1.25f + (hash(col, row * 3) * .5).toFloat()
                        for (k in -1..1) {
                            val ox = -sa * k * .62f; val oy = ca * k * .62f
                            val l = len * (if (k == 0) 1f else .8f)
                            out += jx + ox - ca * l; out += jy + oy - sa * l; out += jx + ox + ca * l; out += jy + oy + sa * l
                        }
                    }
                }
                x += step; col++
            }
            y += step * .87f; row++
        }
        return out.toFloatArray()
    }

    private fun segDist(x: Float, y: Float, s: FloatArray): Float {
        val dx = s[2] - s[0]; val dy = s[3] - s[1]; val l2 = dx * dx + dy * dy
        val t = if (l2 == 0f) 0f else (((x - s[0]) * dx + (y - s[1]) * dy) / l2).coerceIn(0f, 1f)
        val ex = s[0] + t * dx - x; val ey = s[1] + t * dy - y
        return sqrt(ex * ex + ey * ey)
    }
}

/** Paints and textures for the map, made once. */
class MapInk(val density: Float, val hand: Typeface?, val icons: (Int) -> Bitmap?) {
    val paperC = Color.rgb(0xEF, 0xE3, 0xC6)
    val inkC = Color.rgb(0x2C, 0x27, 0x22)
    val sepia = Color.rgb(0x6B, 0x4E, 0x31)
    val blue = Color.rgb(0x3D, 0x6E, 0x9E)

    private val grain: Bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888).also { b ->
        val px = IntArray(192 * 192)
        for (i in px.indices) {
            val x = i % 192; val y = i / 192
            val n = hash(x, y) * .6 + hash(x / 3, y / 3) * .4
            val fibre = if (hash(x / 9, y) > .985) .5 else 0.0
            val a = ((n - .5) * 26 + fibre * 30).toInt()
            px[i] = if (a > 0) Color.argb(a, 0x7A, 0x5C, 0x38) else Color.argb(-a, 0xFF, 0xFA, 0xEE)
        }
        b.setPixels(px, 0, 192, 0, 0, 192, 192)
    }
    val paperPaint = Paint().apply { shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }

    private val tile: Bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b)
        c.drawColor(Color.rgb(0xFB, 0xF6, 0xEA))
        val p = Paint().apply { color = Color.argb(70, 0x3D, 0x6E, 0x9E); strokeWidth = 1.2f }
        c.drawLine(0f, .5f, 24f, .5f, p); c.drawLine(.5f, 0f, .5f, 24f, p)
    }
    val floorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2 * MapPainter.HW; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { setLocalMatrix(android.graphics.Matrix().apply { setScale(3.125f / 24f, 3.125f / 24f) }) }
    }
    val roomFloorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; shader = floorFill.shader }
    val wall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = inkC; strokeWidth = 2 * MapPainter.HW + 2 * MapPainter.WALL; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    val roomWall = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = inkC }
    val pencilEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.argb(120, 0x4A, 0x40, 0x38); strokeWidth = 2 * MapPainter.HW + .9f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(2.2f, 1.4f), 0f)
    }
    val pencilFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.rgb(0xF1, 0xE7, 0xCF); strokeWidth = 2 * MapPainter.HW - .5f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    val pencilRoom = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(140, 0x4A, 0x40, 0x38); strokeWidth = .45f; pathEffect = DashPathEffect(floatArrayOf(1.6f, 1f), 0f) }
    val pencilRoomFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(0xF1, 0xE7, 0xCF) }
    val washes = listOf(16f to 26, 11f to 30, 7f to 34).map { (wd, a) ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(a, 0x7F, 0xA7, 0xCC); strokeWidth = 2 * MapPainter.HW + 2 * wd; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    }
    val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0x2C, 0x27, 0x22); strokeWidth = .26f; strokeCap = Paint.Cap.ROUND }
    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(Color.rgb(0x3A, 0x30, 0x28), PorterDuff.Mode.SRC_IN) }
    val rubblePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(Color.rgb(0x4A, 0x40, 0x38), PorterDuff.Mode.SRC_IN) }
    val question = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 0x4A, 0x40, 0x38); textAlign = Paint.Align.CENTER; typeface = hand; textSize = 8f }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sepia; textAlign = Paint.Align.CENTER; typeface = hand; textSize = 4.6f }
    val labelHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paperC; textAlign = Paint.Align.CENTER; typeface = hand; textSize = 4.6f; style = Paint.Style.STROKE; strokeWidth = 1.4f; strokeJoin = Paint.Join.ROUND }
    val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(0x5E, 0x7D, 0x46); strokeWidth = .7f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val gridMinor = Paint().apply { color = Color.argb(34, 0x3D, 0x6E, 0x9E); strokeWidth = 1f }
    val gridMajor = Paint().apply { color = Color.argb(60, 0x3D, 0x6E, 0x9E); strokeWidth = 1.4f }
    val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0xA8, 0x43, 0x2F); style = Paint.Style.STROKE; strokeWidth = .55f; strokeCap = Paint.Cap.ROUND; pathEffect = DashPathEffect(floatArrayOf(.1f, 1.6f), 0f) }

    private val rect = RectF()

    /** The knowledge layer, under [m] (map pixels → screen). */
    fun scene(c: Canvas, s: MapScene, m: android.graphics.Matrix) {
        c.save(); c.concat(m)
        for (p in washes) { c.drawPath(s.wash, p) }
        if (s.hatch.isNotEmpty()) c.drawLines(s.hatch, hatchPaint)
        c.drawPath(s.pencil, pencilEdge); c.drawPath(s.pencil, pencilFill)
        c.drawPath(s.pencilRooms, pencilRoomFill); c.drawPath(s.pencilRooms, pencilRoom)
        c.drawPath(s.ink, wall); c.drawPath(s.roomInk, roomWall)
        c.drawPath(s.floor, floorFill); c.drawPath(s.roomFloor, roomFloorFill)
        for (r in s.rubble) icon(c, r, rubblePaint)
        for (mk in s.marks) {
            iconPaint.alpha = (mk.alpha * 255).toInt()
            icon(c, mk, iconPaint)
            if (mk.done && mk.alpha < 1f) {
                val x = mk.x + mk.size * .32f; val y = mk.y + mk.size * .3f
                val p = Path().apply { moveTo(x - 1.4f, y); lineTo(x - .3f, y + 1.1f); lineTo(x + 1.8f, y - 1.5f) }
                c.drawPath(p, tick)
            }
        }
        for (u in s.unknown) c.drawText("?", u.x, u.y + 2.8f, question)
        for (l in s.labels) { c.drawText(l.text.take(22), l.x, l.y, labelHalo); c.drawText(l.text.take(22), l.x, l.y, label) }
        c.restore()
    }

    private fun icon(c: Canvas, mk: Mark, p: Paint) {
        val b = icons(mk.art) ?: return
        val h = mk.size; val wdt = h * b.width / b.height
        rect.set(mk.x - wdt / 2, mk.y - h / 2, mk.x + wdt / 2, mk.y + h / 2)
        c.drawBitmap(b, null, rect, p)
    }

    /** Paper grain and the blue survey grid, fixed to the map so they pan with it. */
    fun paper(c: Canvas, m: android.graphics.Matrix, zoom: Float, w: Float, h: Float, cx: Float, cy: Float) {
        c.drawColor(paperC)
        paperPaint.shader.setLocalMatrix(android.graphics.Matrix().apply { setTranslate(-cx * zoom, -cy * zoom) })
        c.drawRect(0f, 0f, w, h, paperPaint)
        // a line every 10 m, stronger every 50 m
        val step = 6.25f
        val left = cx - w / 2 / zoom; val top = cy - h / 2 / zoom
        val sx = Math.floor((left / step).toDouble()).toInt(); val sy = Math.floor((top / step).toDouble()).toInt()
        val pts = FloatArray(4)
        val minor = zoom * step > 9 * density
        var i = sx
        while ((i * step - left) * zoom < w) {
            val x = (i * step - left) * zoom
            if (i % 5 == 0 || minor) { pts[0] = x; pts[1] = 0f; pts[2] = x; pts[3] = h; c.drawLines(pts, if (i % 5 == 0) gridMajor else gridMinor) }
            i++
        }
        var j = sy
        while ((j * step - top) * zoom < h) {
            val y = (j * step - top) * zoom
            if (j % 5 == 0 || minor) { pts[0] = 0f; pts[1] = y; pts[2] = w; pts[3] = y; c.drawLines(pts, if (j % 5 == 0) gridMajor else gridMinor) }
            j++
        }
    }

    /** The walked way on this floor: a dotted red pencil line. */
    fun steps(c: Canvas, steps: List<Float>, m: android.graphics.Matrix) {
        if (steps.size < 4) return
        val p = Path(); p.moveTo(steps[0], steps[1])
        var i = 2; while (i + 1 < steps.size) { p.lineTo(steps[i], steps[i + 1]); i += 2 }
        c.save(); c.concat(m); c.drawPath(p, trail); c.restore()
    }

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val veil = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(110, 0x6B, 0x4E, 0x31); pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f) }

    /** The torch: warm light round the hero and a dusky veil beyond its reach. */
    fun torch(c: Canvas, x: Float, y: Float, r: Float, w: Float, h: Float, flicker: Float, low: Boolean) {
        val rr = r * flicker
        val warm = if (low) Color.argb(70, 0xE0, 0x80, 0x40) else Color.argb(95, 0xFF, 0xB3, 0x47)
        glow.shader = RadialGradient(x, y, rr, intArrayOf(warm, Color.argb(30, 0xFF, 0xC8, 0x70), Color.TRANSPARENT), floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, rr, glow)
        val far = max(w, h) * 1.2f
        veil.shader = RadialGradient(x, y, far, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(if (low) 120 else 80, 0x3A, 0x2C, 0x1E)), floatArrayOf(0f, (rr * 1.05f / far).coerceIn(0f, .9f), 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, veil)
        ring.strokeWidth = 1.3f * density
        c.drawCircle(x, y, r, ring)
    }

    private val token = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tokenRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** A round portrait token: the hero, or a creature on their heels. */
    fun portrait(c: Canvas, b: Bitmap?, x: Float, y: Float, r: Float, edge: Int, crop: Float = .55f) {
        token.shader = null; token.color = paperC
        token.setShadowLayer(4 * density, 0f, 1.5f * density, Color.argb(90, 0, 0, 0))
        c.drawCircle(x, y, r, token)
        token.clearShadowLayer()
        if (b != null) {
            c.save()
            val clip = Path().apply { addCircle(x, y, r * .9f, Path.Direction.CW) }
            c.clipPath(clip)
            // the top of the picture (the head) fills the circle
            val bw = b.width.toFloat(); val bh = b.height * crop
            val k = max(2 * r / bw, 2 * r / bh) * 1.05f
            rect.set(x - bw * k / 2, y - r * 1.02f, x + bw * k / 2, y - r * 1.02f + b.height * k)
            c.drawBitmap(b, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        }
        tokenRing.color = edge; tokenRing.strokeWidth = 2.4f * density
        c.drawCircle(x, y, r, tokenRing)
        tokenRing.color = inkC; tokenRing.strokeWidth = 1f * density
        c.drawCircle(x, y, r + 2f * density, tokenRing)
    }

    private val chip = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = inkC }

    /** A chip at the screen's edge: the symbol of a found stair or well, an arrow its way, the distance. */
    fun pointer(c: Canvas, icon: Bitmap?, x: Float, y: Float, angle: Float, metres: Int, ru: Boolean) {
        val r = 22 * density
        chip.style = Paint.Style.FILL; chip.color = paperC
        chip.setShadowLayer(4 * density, 0f, 1.5f * density, Color.argb(90, 0, 0, 0))
        c.drawCircle(x, y, r, chip); chip.clearShadowLayer()
        chip.style = Paint.Style.STROKE; chip.color = inkC; chip.strokeWidth = 1.6f * density
        c.drawCircle(x, y, r, chip)
        // the arrow sits on the rim, pointing out
        val ax = x + cos(angle) * (r + 7 * density); val ay = y + sin(angle) * (r + 7 * density)
        val p = Path().apply {
            moveTo(ax, ay)
            lineTo(x + cos(angle + .32f) * r, y + sin(angle + .32f) * r)
            lineTo(x + cos(angle - .32f) * r, y + sin(angle - .32f) * r); close()
        }
        chip.style = Paint.Style.FILL; chip.color = Color.rgb(0xA8, 0x43, 0x2F)
        c.drawPath(p, chip)
        if (icon != null) {
            val h = r * 1.15f; val wd = h * icon.width / icon.height
            rect.set(x - wd / 2, y - h / 2 - 3 * density, x + wd / 2, y + h / 2 - 3 * density)
            c.drawBitmap(icon, null, rect, iconPaint.apply { alpha = 255 })
        }
        chipText.typeface = hand; chipText.textSize = 14 * density
        val t = if (metres >= 1000) "%.1f %s".format(metres / 1000f, if (ru) "км" else "km") else "$metres " + if (ru) "м" else "m"
        c.drawText(t, x, y + r * .78f, chipText)
    }

    private val rose = Paint(Paint.ANTI_ALIAS_FLAG)
    private val roseText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.rgb(0x2C, 0x27, 0x22) }

    /** A compass rose: eight points, half of each inked, a red north. */
    fun compass(c: Canvas, x: Float, y: Float, r: Float, serif: Typeface?) {
        rose.style = Paint.Style.FILL
        rose.color = Color.argb(150, 0xEF, 0xE3, 0xC6)
        c.drawCircle(x, y, r * 1.05f, rose)
        rose.style = Paint.Style.STROKE; rose.strokeWidth = .9f * density; rose.color = inkC
        c.drawCircle(x, y, r * .78f, rose)
        c.drawCircle(x, y, r * .7f, rose)
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4 - PI.toFloat() / 2
            val len = if (k % 2 == 0) r else r * .55f
            val wd = r * (if (k % 2 == 0) .17f else .12f)
            val tipX = x + cos(a) * len; val tipY = y + sin(a) * len
            val lx = x + cos(a - PI.toFloat() / 2) * wd; val ly = y + sin(a - PI.toFloat() / 2) * wd
            val rx = x + cos(a + PI.toFloat() / 2) * wd; val ry = y + sin(a + PI.toFloat() / 2) * wd
            val north = k == 0
            rose.style = Paint.Style.FILL
            rose.color = if (north) Color.rgb(0xA8, 0x43, 0x2F) else inkC
            c.drawPath(Path().apply { moveTo(x, y); lineTo(tipX, tipY); lineTo(lx, ly); close() }, rose)
            rose.color = if (north) Color.rgb(0xE0, 0x9A, 0x86) else paperC
            c.drawPath(Path().apply { moveTo(x, y); lineTo(tipX, tipY); lineTo(rx, ry); close() }, rose)
            rose.style = Paint.Style.STROKE; rose.color = inkC; rose.strokeWidth = .7f * density
            c.drawPath(Path().apply { moveTo(lx, ly); lineTo(tipX, tipY); lineTo(rx, ry) }, rose)
        }
        roseText.typeface = serif; roseText.textSize = r * .42f
        c.drawText("N", x, y - r - r * .12f, roseText)
    }

    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)

    /** A scale bar: 50 m in alternating ink and paper. */
    fun scale(c: Canvas, x: Float, y: Float, zoom: Float, ru: Boolean) {
        val seg = 12.5f / 1.6f * zoom  // 12.5 m
        bar.style = Paint.Style.FILL
        for (i in 0 until 4) {
            bar.color = if (i % 2 == 0) inkC else paperC
            c.drawRect(x + i * seg, y, x + (i + 1) * seg, y + 4 * density, bar)
        }
        bar.style = Paint.Style.STROKE; bar.color = inkC; bar.strokeWidth = .8f * density
        c.drawRect(x, y, x + 4 * seg, y + 4 * density, bar)
        roseText.typeface = hand; roseText.textSize = 15 * density
        roseText.textAlign = Paint.Align.LEFT
        c.drawText("0", x - 2 * density, y - 4 * density, roseText)
        roseText.textAlign = Paint.Align.RIGHT
        c.drawText(if (ru) "50 м" else "50 m", x + 4 * seg + 6 * density, y - 4 * density, roseText)
        roseText.textAlign = Paint.Align.CENTER
    }

    @Suppress("unused") private val blur = BlurMaskFilter(1f, BlurMaskFilter.Blur.NORMAL)
}

package app.delve.domain

import app.delve.data.District
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A junction of the street graph: a room of the dungeon. Coordinates are map pixels of the district. */
class Node(val id: Int, val x: Float, val y: Float) {
    val edges = ArrayList<Int>()
}

/** A stretch of street between two junctions: a corridor. [pts] runs from [a] to [b], x then y. */
class Edge(val id: Int, val a: Int, val b: Int, val pts: FloatArray, val len: Float) {
    fun other(n: Int) = if (n == a) b else a
}

/**
 * The walkable streets of the district as a graph of junctions and corridors. Built from the road lines:
 * the ends and shared points become junctions, junctions closer than a crossing's width melt into one
 * (pavements drawn beside a road and their crossings make one junction, not six), parallel corridors
 * between the same two junctions become one, and through-points (degree 2) are taken out, so a corridor
 * runs from a real crossing to the next. Long corridors get a chamber in the middle.
 */
class Graph(val nodes: List<Node>, val edges: List<Edge>, val mpp: Double) {
    fun edgeBetween(a: Int, b: Int): Edge? = nodes[a].edges.map { edges[it] }.firstOrNull { it.other(a) == b }

    fun nearestNode(x: Float, y: Float): Node? = nodes.minByOrNull { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) }

    /** Distance in map pixels from a point to an edge's line, and how far along (0..1) the closest point is. */
    fun toEdge(e: Edge, x: Float, y: Float): Pair<Float, Float> {
        var best = Float.MAX_VALUE; var at = 0f; var acc = 0f
        val p = e.pts
        var i = 0
        while (i + 3 < p.size) {
            val ax = p[i]; val ay = p[i + 1]; val bx = p[i + 2]; val by = p[i + 3]
            val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
            val t = if (l2 == 0f) 0f else (((x - ax) * dx + (y - ay) * dy) / l2).coerceIn(0f, 1f)
            val ex = ax + t * dx - x; val ey = ay + t * dy - y
            val d = sqrt(ex * ex + ey * ey)
            val l = sqrt(l2)
            if (d < best) { best = d; at = acc + t * l }
            acc += l; i += 2
        }
        return best to if (e.len > 0) at / e.len else 0f
    }

    companion object {
        /** Ways a walker may take. Big roads stay: in a town they have pavements. */
        private val WALK = setOf("primary", "secondary", "tertiary", "residential", "unclassified", "living_street",
            "pedestrian", "footway", "path", "cycleway", "track", "steps", "bridleway")

        fun build(d: District): Graph {
            val mpp = d.mpp
            val lines = d.roads.filter { it.type in WALK && !it.tunnel && it.pts.size >= 4 }.map { it.pts.copyOf() }.toMutableList()
            val merge = (14.0 / mpp).toFloat()   // junctions this close are one
            val tee = (4.0 / mpp).toFloat()      // a line ending this close to another joins it

            // 1. T-junctions: a line that ends on another line's middle shares a point with it
            val grid = SegGrid(lines, 24f)
            val inserts = HashMap<Int, MutableList<Pair<Int, FloatArray>>>()   // line → (segment index, point)
            for ((li, l) in lines.withIndex()) for (end in listOf(0, l.size - 2)) {
                val x = l[end]; val y = l[end + 1]
                grid.near(x, y) { lj, si ->
                    if (lj == li) return@near
                    val m = lines[lj]
                    val ax = m[si]; val ay = m[si + 1]; val bx = m[si + 2]; val by = m[si + 3]
                    val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
                    if (l2 == 0f) return@near
                    val t = ((x - ax) * dx + (y - ay) * dy) / l2
                    if (t <= .02f || t >= .98f) return@near
                    val px = ax + t * dx; val py = ay + t * dy
                    if (hypot(px - x, py - y) < tee) inserts.getOrPut(lj) { ArrayList() } += si to floatArrayOf(px, py, t)
                }
            }
            for ((lj, list) in inserts) {
                val m = lines[lj]; val out = ArrayList<Float>()
                val bySeg = list.groupBy { it.first }
                var i = 0
                while (i + 1 < m.size) {
                    out += m[i]; out += m[i + 1]
                    bySeg[i]?.sortedBy { it.second[2] }?.forEach { out += it.second[0]; out += it.second[1] }
                    i += 2
                }
                lines[lj] = out.toFloatArray()
            }

            // 2. how many lines touch each point (rounded to a pixel): shared points and ends are junctions
            fun key(x: Float, y: Float) = (Math.round(x).toLong() shl 32) or (Math.round(y).toLong() and 0xffffffffL)
            val uses = HashMap<Long, Int>()
            for (l in lines) {
                for (i in l.indices step 2) uses.merge(key(l[i], l[i + 1]), if (i == 0 || i == l.size - 2) 2 else 1, Int::plus)
            }
            // 3. cut the lines at junctions into raw corridors
            class Raw(val pts: FloatArray)
            val raws = ArrayList<Raw>()
            for (l in lines) {
                var start = 0
                for (i in 2 until l.size step 2) {
                    val last = i == l.size - 2
                    if (last || (uses[key(l[i], l[i + 1])] ?: 0) >= 2) {
                        raws += Raw(l.copyOfRange(start, i + 2)); start = i
                    }
                }
            }
            // 4. junctions melt together within [merge] (union-find over a grid)
            val jx = ArrayList<Float>(); val jy = ArrayList<Float>(); val jIndex = HashMap<Long, Int>()
            fun junction(x: Float, y: Float): Int = jIndex.getOrPut(key(x, y)) { jx += x; jy += y; jx.size - 1 }
            val ends = raws.map { r -> junction(r.pts[0], r.pts[1]) to junction(r.pts[r.pts.size - 2], r.pts[r.pts.size - 1]) }
            val parent = IntArray(jx.size) { it }
            fun root(i: Int): Int { var r = i; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
            val cell = HashMap<Long, MutableList<Int>>()
            fun ck(cx: Int, cy: Int) = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)
            for (i in jx.indices) cell.getOrPut(ck((jx[i] / merge).toInt(), (jy[i] / merge).toInt())) { ArrayList() } += i
            for (i in jx.indices) {
                val cx = (jx[i] / merge).toInt(); val cy = (jy[i] / merge).toInt()
                for (a in -1..1) for (b in -1..1) cell[ck(cx + a, cy + b)]?.forEach { j ->
                    if (j > i && hypot(jx[i] - jx[j], jy[i] - jy[j]) < merge) parent[root(i)] = root(j)
                }
            }
            // short raw corridors (a crossing's zebra, a kerb link) also melt their ends together
            for ((k, r) in raws.withIndex()) if (length(r.pts) < merge) parent[root(ends[k].first)] = root(ends[k].second)

            val clusterOf = HashMap<Int, Int>(); val cxs = ArrayList<Float>(); val cys = ArrayList<Float>(); val cn = ArrayList<Int>()
            for (i in jx.indices) {
                val r = root(i)
                val c = clusterOf.getOrPut(r) { cxs += 0f; cys += 0f; cn += 0; cxs.size - 1 }
                cxs[c] += jx[i]; cys[c] += jy[i]; cn[c] += 1
            }
            for (c in cxs.indices) { cxs[c] /= cn[c]; cys[c] /= cn[c] }

            // 5. corridors between clusters; parallels between the same pair become the shortest one
            val best = HashMap<Long, FloatArray>()
            for ((k, r) in raws.withIndex()) {
                var a = clusterOf.getValue(root(ends[k].first)); var b = clusterOf.getValue(root(ends[k].second))
                if (a == b) continue
                var pts = r.pts
                if (a > b) { val t = a; a = b; b = t; pts = reverse(pts) }
                val pk = (a.toLong() shl 32) or b.toLong()
                val old = best[pk]
                if (old == null || length(pts) < length(old)) best[pk] = pts
            }
            // adjacency for the merge of through-points
            val adj = HashMap<Int, MutableList<Long>>()
            for (pk in best.keys) { adj.getOrPut((pk ushr 32).toInt()) { ArrayList() } += pk; adj.getOrPut((pk and 0xffffffffL).toInt()) { ArrayList() } += pk }

            // 6. through-points (exactly two corridors) are taken out: their corridors join
            data class Path(val a: Int, val b: Int, val pts: FloatArray)
            val alive = HashMap<Long, Path>()
            for ((pk, pts) in best) alive[pk] = Path((pk ushr 32).toInt(), (pk and 0xffffffffL).toInt(), snap(pts, cxs, cys, (pk ushr 32).toInt(), (pk and 0xffffffffL).toInt()))
            var nextKey = -1L
            val links = HashMap<Int, MutableSet<Long>>()
            for ((pk, p) in alive) { links.getOrPut(p.a) { HashSet() } += pk; links.getOrPut(p.b) { HashSet() } += pk }
            var changed = true
            while (changed) {
                changed = false
                for (n in links.keys.toList()) {
                    val ls = links[n] ?: continue
                    if (ls.size != 2) continue
                    val (k1, k2) = ls.toList()
                    val p1 = alive[k1] ?: continue; val p2 = alive[k2] ?: continue
                    val o1 = if (p1.a == n) p1.b else p1.a; val o2 = if (p2.a == n) p2.b else p2.a
                    if (o1 == o2 || o1 == n || o2 == n) continue
                    // p1 from o1 to n, then p2 from n to o2
                    val s1 = if (p1.b == n) p1.pts else reverse(p1.pts)
                    val s2 = if (p2.a == n) p2.pts else reverse(p2.pts)
                    val joined = s1 + s2.copyOfRange(2, s2.size)
                    // the joined corridor must not duplicate one that already links o1 and o2
                    val dup = links[o1]?.any { k -> alive[k]?.let { it.a == o2 || it.b == o2 } == true } == true
                    if (dup) continue
                    alive.remove(k1); alive.remove(k2)
                    links[o1]?.remove(k1); links[o2]?.remove(k2); links.remove(n)
                    val nk = nextKey--
                    alive[nk] = Path(o1, o2, joined)
                    links.getOrPut(o1) { HashSet() } += nk; links.getOrPut(o2) { HashSet() } += nk
                    changed = true
                }
            }

            // 7. dangling stubs shorter than a few steps are noise
            val minStub = (12.0 / mpp).toFloat()
            var pruned = true
            while (pruned) {
                pruned = false
                for ((k, p) in alive.toList()) {
                    val da = links[p.a]?.size ?: 0; val db = links[p.b]?.size ?: 0
                    if ((da == 1 || db == 1) && length(p.pts) < minStub && !(da == 1 && db == 1)) {
                        alive.remove(k); links[p.a]?.remove(k); links[p.b]?.remove(k); pruned = true
                    }
                }
            }

            // 8. the biggest connected piece; long corridors get chambers
            val used = alive.values.flatMap { listOf(it.a, it.b) }.toSet()
            val comp = HashMap<Int, Int>()
            var bestComp = -1; var bestSize = 0
            for (s in used) {
                if (s in comp) continue
                val id = s; var size = 0
                val stack = ArrayDeque<Int>(); stack += s; comp[s] = id
                while (stack.isNotEmpty()) {
                    val n = stack.removeLast(); size++
                    for (k in links[n].orEmpty()) { val p = alive[k] ?: continue; val o = if (p.a == n) p.b else p.a; if (o !in comp) { comp[o] = id; stack += o } }
                }
                if (size > bestSize) { bestSize = size; bestComp = id }
            }
            val nodes = ArrayList<Node>(); val ids = HashMap<Int, Int>()
            fun node(c: Int) = ids.getOrPut(c) { nodes += Node(nodes.size, cxs[c], cys[c]); nodes.size - 1 }
            val edges = ArrayList<Edge>()
            val chamberEvery = (170.0 / mpp).toFloat()
            for (p in alive.values) {
                if (comp[p.a] != bestComp) continue
                val a = node(p.a); val b = node(p.b)
                val len = length(p.pts)
                val pieces = max(1, (len / chamberEvery).toInt() + if (len > chamberEvery) 1 else 0).coerceAtMost(6)
                if (pieces == 1) { addEdge(nodes, edges, a, b, p.pts); continue }
                var prev = a; var from = p.pts
                for (k in 1 until pieces) {
                    val (head, tail) = cut(from, len / pieces)
                    val mid = Node(nodes.size, head[head.size - 2], head[head.size - 1]); nodes += mid
                    addEdge(nodes, edges, prev, mid.id, head)
                    prev = mid.id; from = tail
                }
                addEdge(nodes, edges, prev, b, from)
            }
            return Graph(nodes, edges, mpp)
        }

        private fun addEdge(nodes: List<Node>, edges: MutableList<Edge>, a: Int, b: Int, pts: FloatArray) {
            val e = Edge(edges.size, a, b, pts, length(pts))
            edges += e; nodes[a].edges += e.id; nodes[b].edges += e.id
        }

        /** Moves a corridor's ends onto its junctions' centres. */
        private fun snap(p: FloatArray, xs: List<Float>, ys: List<Float>, a: Int, b: Int): FloatArray {
            val o = p.copyOf()
            o[0] = xs[a]; o[1] = ys[a]; o[o.size - 2] = xs[b]; o[o.size - 1] = ys[b]
            return o
        }

        /** Splits a line at [at] pixels from its start. */
        private fun cut(p: FloatArray, at: Float): Pair<FloatArray, FloatArray> {
            var acc = 0f; var i = 0
            while (i + 3 < p.size) {
                val l = hypot(p[i + 2] - p[i], p[i + 3] - p[i + 1])
                if (acc + l >= at) {
                    val t = if (l == 0f) 0f else (at - acc) / l
                    val x = p[i] + (p[i + 2] - p[i]) * t; val y = p[i + 1] + (p[i + 3] - p[i + 1]) * t
                    val head = p.copyOfRange(0, i + 2) + floatArrayOf(x, y)
                    val tail = floatArrayOf(x, y) + p.copyOfRange(i + 2, p.size)
                    return head to tail
                }
                acc += l; i += 2
            }
            return p to floatArrayOf(p[p.size - 2], p[p.size - 1], p[p.size - 2], p[p.size - 1])
        }

        fun length(p: FloatArray): Float {
            var s = 0f; var i = 0
            while (i + 3 < p.size) { s += hypot(p[i + 2] - p[i], p[i + 3] - p[i + 1]); i += 2 }
            return s
        }

        fun reverse(p: FloatArray): FloatArray {
            val n = p.size / 2; val o = FloatArray(p.size)
            for (i in 0 until n) { o[2 * i] = p[2 * (n - 1 - i)]; o[2 * i + 1] = p[2 * (n - 1 - i) + 1] }
            return o
        }
    }
}

/** Line segments bucketed into a grid, to find the ones near a point quickly. */
private class SegGrid(val lines: List<FloatArray>, val size: Float) {
    private val cells = HashMap<Long, MutableList<Long>>()
    private fun k(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)

    init {
        for ((li, l) in lines.withIndex()) {
            var i = 0
            while (i + 3 < l.size) {
                val x0 = (min(l[i], l[i + 2]) / size).toInt(); val x1 = (max(l[i], l[i + 2]) / size).toInt()
                val y0 = (min(l[i + 1], l[i + 3]) / size).toInt(); val y1 = (max(l[i + 1], l[i + 3]) / size).toInt()
                for (x in x0..x1) for (y in y0..y1) cells.getOrPut(k(x, y)) { ArrayList() } += (li.toLong() shl 32) or i.toLong()
                i += 2
            }
        }
    }

    fun near(x: Float, y: Float, f: (Int, Int) -> Unit) {
        val cx = (x / size).toInt(); val cy = (y / size).toInt()
        val seen = HashSet<Long>()
        for (a in -1..1) for (b in -1..1) cells[k(cx + a, cy + b)]?.forEach { v -> if (seen.add(v)) f((v ushr 32).toInt(), (v and 0xffffffffL).toInt()) }
    }
}

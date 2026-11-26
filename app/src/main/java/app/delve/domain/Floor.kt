package app.delve.domain

import app.delve.data.District
import app.delve.data.Poly
import java.util.PriorityQueue
import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot

/** A room of a floor: what is in it, and, for rooms bound to a real place above, that place's name. */
class Room(val node: Int, val kind: RoomKind, val beast: String? = null, val above: String = "")

/** What the weather and the hour do to the dungeon. */
data class Conditions(val night: Boolean = false, val rain: Boolean = false, val frost: Boolean = false, val heat: Boolean = false)

/**
 * One floor of the dungeon under the district: the corridors within reach of where it starts, some of them
 * caved in (more the deeper you go, so deep floors turn into mazes), and a room at every junction. The same
 * graph, depth, seed and start always give the same floor, so only what the hero found is saved.
 */
class Floor(val g: Graph, val d: District, val depth: Int, val seed: Long, val start: Int, val cond: Conditions = Conditions()) {
    /** Nodes of this floor. */
    val nodes: Set<Int>
    /** Edges of this floor: open (walkable) or caved in. */
    val edges: List<Int>
    val open: Set<Int>
    val rooms: Map<Int, Room>
    val down: Int
    val ups: List<Int>
    /** Metres from the start to every node along open corridors. */
    val dist: Map<Int, Double>

    init {
        val rnd = Random(seed * 31 + depth * 7919L + start)
        val radius = (RADIUS_M / g.mpp).toFloat()
        val sx = g.nodes[start].x; val sy = g.nodes[start].y
        // the reachable part of the graph within the radius
        val inR = HashSet<Int>()
        val stack = ArrayDeque<Int>(); stack += start; inR += start
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            for (e in g.nodes[n].edges) {
                val o = g.edges[e].other(n)
                if (o !in inR && hypot(g.nodes[o].x - sx, g.nodes[o].y - sy) <= radius) { inR += o; stack += o }
            }
        }
        nodes = inR
        edges = inR.flatMap { g.nodes[it].edges }.distinct().filter { g.edges[it].a in inR && g.edges[it].b in inR }.sorted()

        // a random spanning tree keeps every room reachable; other corridors stay open by chance
        val order = edges.shuffled(rnd)
        val parent = HashMap<Int, Int>()
        fun root(i: Int): Int { var r = i; while ((parent[r] ?: r) != r) r = parent[r]!!; return r }
        val tree = HashSet<Int>()
        for (e in order) {
            val ra = root(g.edges[e].a); val rb = root(g.edges[e].b)
            if (ra != rb) { parent[ra] = rb; tree += e }
        }
        val keep = (0.92 - 0.07 * (depth - 1)).coerceIn(0.28, 0.92)
        open = edges.filter { it in tree || rnd.nextDouble() < keep }.toSet()

        dist = dijkstra(start)

        // the stairs down: about [target] metres away along open corridors
        val target = 360.0 + 25.0 * minOf(depth, 10)
        val far = dist.filterKeys { it != start }.entries.sortedBy { abs(it.value - target) }.take(6)
        down = if (far.isEmpty()) start else far[rnd.nextInt(far.size)].key
        // wells up: away from the start and from the stairs
        val ups = ArrayList<Int>()
        for (e in dist.entries.shuffled(rnd)) {
            if (ups.size >= 2) break
            val n = e.key
            if (n == start || n == down || e.value < 220) continue
            if ((ups + down).any { hypot(g.nodes[it].x - g.nodes[n].x, g.nodes[it].y - g.nodes[n].y) * g.mpp < 220 }) continue
            ups += n
        }
        this.ups = ups

        rooms = HashMap<Int, Room>().also { r ->
            r[start] = Room(start, RoomKind.START)
            r[down] = if (depth % 5 == 0) Room(down, RoomKind.BOSS, bossFor(depth).id) else Room(down, RoomKind.DOWN)
            for (u in ups) r[u] = Room(u, RoomKind.UP)
            val caps = HashMap<RoomKind, Int>()
            fun capped(k: RoomKind, max: Int) = (caps[k] ?: 0) < max
            for (n in inR.sorted()) {
                if (n in r) continue
                val node = g.nodes[n]
                val deg = node.edges.count { it in open }
                val place = above(node.x, node.y)
                val kind = when {
                    place?.first == RoomKind.CRYPT && capped(RoomKind.CRYPT, 4) -> RoomKind.CRYPT
                    place?.first == RoomKind.SPRING && capped(RoomKind.SPRING, 3) -> RoomKind.SPRING
                    place?.first == RoomKind.MERCHANT && capped(RoomKind.MERCHANT, 2) && rnd.nextDouble() < .6 -> RoomKind.MERCHANT
                    place?.first == RoomKind.SHAFT && capped(RoomKind.SHAFT, 1) -> RoomKind.SHAFT
                    place?.first == RoomKind.VAULT && capped(RoomKind.VAULT, 2) -> RoomKind.VAULT
                    place?.first == RoomKind.RIVER && capped(RoomKind.RIVER, if (cond.rain) 5 else 3) -> RoomKind.RIVER
                    place?.first == RoomKind.GROTTO && capped(RoomKind.GROTTO, 4) && rnd.nextDouble() < .7 -> RoomKind.GROTTO
                    else -> pick(rnd, deg)
                }
                caps.merge(kind, 1, Int::plus)
                val beast = when (kind) {
                    RoomKind.LAIR -> pickBeast(rnd, Hab.ANY)
                    RoomKind.CRYPT -> pickBeast(rnd, Hab.CRYPT)
                    RoomKind.GROTTO -> if (rnd.nextDouble() < .4) pickBeast(rnd, Hab.GROTTO) else null
                    RoomKind.RIVER -> if (rnd.nextDouble() < .35) pickBeast(rnd, Hab.RIVER) else null
                    RoomKind.CHEST -> if (rnd.nextDouble() < .1 && depth >= 2) "mimic" else null
                    else -> null
                }
                r[n] = Room(n, kind, beast, if (kind == place?.first) place.second else "")
            }
        }
    }

    private fun pick(rnd: Random, deg: Int): RoomKind {
        // dead ends are never empty: something always waits at the end of a corridor
        val w = if (deg <= 1) listOf(RoomKind.CHEST to 40, RoomKind.LAIR to 25, RoomKind.VAULT to 10, RoomKind.SHRINE to 10, RoomKind.REST to 8, RoomKind.TRAP to 7)
        else listOf(RoomKind.EMPTY to 34, RoomKind.CHEST to 14, RoomKind.LAIR to 18 + minOf(depth, 10), RoomKind.TRAP to 9 + minOf(depth, 6),
            RoomKind.SHRINE to 5, RoomKind.REST to 6, RoomKind.SPRING to 2, RoomKind.VAULT to 2, RoomKind.MERCHANT to 1)
        var t = rnd.nextInt(w.sumOf { it.second })
        for ((k, v) in w) { if (t < v) return k; t -= v }
        return RoomKind.EMPTY
    }

    private fun pickBeast(rnd: Random, hab: Hab): String {
        val want = 1 + depth / 2 + if (cond.night) 1 else 0
        val pool = BEASTS.filter { b ->
            b.hab != Hab.BOSS && b.min <= depth && (b.hab == hab || b.hab == Hab.ANY || (b.hab == Hab.DEEP && depth >= 4))
        }.ifEmpty { BEASTS.filter { it.hab == Hab.ANY && it.min <= 1 } }
        // nearer the wanted tier is likelier; the habitat's own creatures are likelier still
        val weights = pool.map { b -> (1.0 / (1 + abs(b.tier - want))) * if (b.hab == hab && hab != Hab.ANY) 3.0 else 1.0 }
        var t = rnd.nextDouble() * weights.sum()
        for ((i, b) in pool.withIndex()) { if (t < weights[i]) return b.id; t -= weights[i] }
        return pool.last().id
    }

    /** What stands above a junction: a church or cemetery is a crypt, a fountain a spring, a park a grotto… */
    private fun above(x: Float, y: Float): Pair<RoomKind, String>? {
        val r = (40.0 / d.mpp).toFloat()
        var best: Pair<RoomKind, String>? = null; var bd = Float.MAX_VALUE
        for (p in d.places) {
            val dd = hypot(p.x - x, p.y - y)
            if (dd > r || dd >= bd) continue
            val k = when {
                p.kind == "amenity=place_of_worship" -> RoomKind.CRYPT
                p.kind == "amenity=fountain" || p.kind == "amenity=drinking_water" -> RoomKind.SPRING
                p.kind.startsWith("shop=") || p.kind == "amenity=marketplace" -> RoomKind.MERCHANT
                p.kind == "railway=station" || p.kind == "railway=subway_entrance" -> RoomKind.SHAFT
                p.kind == "tourism=museum" || p.kind == "tourism=gallery" || p.kind == "amenity=bank" -> RoomKind.VAULT
                else -> null
            } ?: continue
            best = k to p.name; bd = dd
        }
        if (best != null) return best
        for (gr in d.greens) {
            if (gr.kind == "cemetery" && inside(gr.poly, x, y)) return RoomKind.CRYPT to ""
        }
        val near = (25.0 / d.mpp).toFloat()
        for (w in d.water) for (k in 0 until 8) {
            val a = k * Math.PI / 4
            if (inside(w, x + (near * Math.cos(a)).toFloat(), y + (near * Math.sin(a)).toFloat())) return RoomKind.RIVER to ""
        }
        for (gr in d.greens) if ((gr.kind == "park" || gr.kind == "wood") && inside(gr.poly, x, y)) return RoomKind.GROTTO to ""
        return null
    }

    private fun inside(p: Poly, x: Float, y: Float): Boolean {
        var c = false
        for (ring in p.rings) {
            val n = ring.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = ring[2 * i]; val yi = ring[2 * i + 1]; val xj = ring[2 * j]; val yj = ring[2 * j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
                j = i
            }
        }
        return c
    }

    /** Metres from [from] to every node of the floor along open corridors. */
    fun dijkstra(from: Int): Map<Int, Double> {
        val out = HashMap<Int, Double>()
        val q = PriorityQueue<Pair<Int, Double>>(compareBy { it.second })
        q += from to 0.0
        while (q.isNotEmpty()) {
            val (n, dd) = q.poll()!!
            if (n in out) continue
            out[n] = dd
            for (e in g.nodes[n].edges) {
                if (e !in open) continue
                val o = g.edges[e].other(n)
                if (o !in out) q += o to dd + g.edges[e].len * g.mpp
            }
        }
        return out
    }

    /** Open corridors along the shortest way from [a] to [b], or null if it is far longer than [limitM]. */
    fun route(a: Int, b: Int, limitM: Double): List<Int>? {
        val best = HashMap<Int, Double>(); val via = HashMap<Int, Int>()
        val q = PriorityQueue<Pair<Int, Double>>(compareBy { it.second })
        q += a to 0.0; best[a] = 0.0
        while (q.isNotEmpty()) {
            val (n, dd) = q.poll()!!
            if (dd > (best[n] ?: Double.MAX_VALUE)) continue
            if (n == b) break
            if (dd > limitM) return null
            for (e in g.nodes[n].edges) {
                if (e !in open) continue
                val o = g.edges[e].other(n)
                if (o !in nodes) continue
                val nd = dd + g.edges[e].len * g.mpp
                if (nd < (best[o] ?: Double.MAX_VALUE)) { best[o] = nd; via[o] = e; q += o to nd }
            }
        }
        if (b !in via) return null
        val out = ArrayList<Int>(); var n = b
        while (n != a) { val e = via[n] ?: return null; out += e; n = g.edges[e].other(n) }
        return out
    }

    companion object {
        /** How far from its start a floor reaches. */
        const val RADIUS_M = 650.0
    }
}

package app.delve

import app.delve.data.DemoSeed
import app.delve.data.District
import app.delve.domain.Floor
import app.delve.domain.Game
import app.delve.domain.Graph
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GraphTest {
    private fun district() = District.parse(javaClass.classLoader!!.getResource("petrogradka.osm.json")!!.readText(), DemoSeed.BOX)

    @Test
    fun graphAndFloors() {
        val d = district()
        val g = Graph.build(d)
        val lens = g.edges.map { it.len * d.mpp }
        println("nodes ${g.nodes.size} edges ${g.edges.size} mean ${lens.average().toInt()} m, min ${lens.min().toInt()} max ${lens.max().toInt()}")
        assertTrue(g.nodes.size > 100)
        val start = g.nearestNode(d.x(DemoSeed.HOME.lon), d.y(DemoSeed.HOME.lat))!!.id
        for (depth in listOf(1, 4, 8)) {
            val f = Floor(g, d, depth, 42L, start)
            val kinds = f.rooms.values.groupingBy { it.kind }.eachCount()
            println("depth $depth: ${f.nodes.size} rooms, ${f.edges.size} corridors, ${f.open.size} open, stairs at ${f.dist[f.down]?.toInt()} m; $kinds")
            assertTrue(f.down != start)
        }
        // dump for a look (tools/plot_graph.py)
        val f = Floor(g, d, 4, 42L, start)
        val o = JSONObject().put("w", d.w).put("h", d.h)
            .put("nodes", JSONArray(g.nodes.map { JSONArray(listOf(it.x, it.y)) }))
            .put("edges", JSONArray(g.edges.map { e -> JSONObject().put("p", JSONArray(e.pts.toList())).put("open", e.id in f.open).put("in", e.id in f.edges) }))
            .put("rooms", JSONObject().also { r -> f.rooms.forEach { (n, room) -> r.put(n.toString(), room.kind.name) } })
            .put("roads", JSONArray(d.roads.filter { it.type != "name" }.map { JSONArray(it.pts.toList()) }))
        File("build/graph.json").writeText(o.toString())
    }

    @Test
    fun walkAFloor() {
        val d = district()
        val g = Graph.build(d)
        val game = Game()
        val s = game.newGame("T", "warrior", false)
        val start = g.nearestNode(d.x(DemoSeed.HOME.lon), d.y(DemoSeed.HOME.lat))!!.id
        val run = game.startRun(s, start, 30, 0L, 7L)
        run.at = start
        val f = Floor(g, d, 1, run.seed, start)
        val evs = ArrayList<app.delve.domain.Ev>()
        // walk to the stairs along the open corridors, room by room
        var n = start
        val route = f.route(start, f.down, 5000.0)!!.reversed()
        for (e in route) {
            n = g.edges[e].other(n)
            game.walked(s, g.edges[e].len * d.mpp, evs)
            game.enter(s, f, n, 0L, app.delve.domain.Conditions(), evs)
            val meet = evs.filterIsInstance<app.delve.domain.Ev.Meet>().lastOrNull()
            if (meet != null) { evs.remove(meet); game.fight(s, meet.node, meet.foe, evs) }
        }
        println("events: " + evs.map { it::class.simpleName }.groupingBy { it }.eachCount() + " hp ${run.hp} xp ${run.xp} coins ${run.coins} bag ${run.bag.size}")
        assertTrue(evs.any { it is app.delve.domain.Ev.Stairs })
    }
}

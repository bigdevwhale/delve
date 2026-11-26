package app.delve

import android.app.Application
import android.content.Context
import app.delve.data.DemoSeed
import app.delve.data.District
import app.delve.data.Store
import app.delve.data.Tiles
import app.delve.domain.Conditions
import app.delve.domain.Ev
import app.delve.domain.Floor
import app.delve.domain.Forecast
import app.delve.domain.Foe
import app.delve.domain.Game
import app.delve.domain.GameState
import app.delve.domain.Geo
import app.delve.domain.Graph
import app.delve.domain.Fix
import app.delve.domain.Track
import app.delve.domain.Item
import app.delve.domain.Battle
import app.delve.domain.Move
import app.delve.domain.Tx
import app.delve.domain.Recipe
import app.delve.domain.Weather
import app.delve.domain.metres
import app.delve.work.Buzz
import app.delve.work.WalkService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.hypot

/** Half the side of the district square, metres: a floor reaches 650 m from its start. */
const val DISTRICT_HALF = 1100.0

/** Holds the game for the whole app: the hero, the district under them and the floor they are on. */
class AppContainer(context: Context) {
    val app: Context = context.applicationContext
    val game = Game()
    val store = Store(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var state: GameState? = store.load()
        private set
    val version = MutableStateFlow(0)
    var district: District? = null
        private set
    var graph: Graph? = null
        private set
    private var floorKey: String? = null
    private var floorCache: Floor? = null
    var forecast: Forecast? = null
        private set
    val here = MutableStateFlow<Geo?>(null)
    /** Screens to show, oldest first: only what asks for a choice. */
    val events = MutableStateFlow<List<Ev>>(emptyList())
    /** Quiet news slid over the map for a moment (an empty hall, a chest, a bite…), newest last. */
    val toasts = MutableStateFlow<List<Ev>>(emptyList())
    /** A fight in rounds, while it lasts. */
    val battle = MutableStateFlow<Battle?>(null)
    /** download, parse, or empty. */
    val stage = MutableStateFlow("")
    @Volatile var visible = false

    /** Cleans raw positions (ported from Fogwalk). */
    private val track = Track()
    /** True while the phone moves faster than a walker: the dungeon waits. */
    val riding = MutableStateFlow(false)
    private var warned = 0

    fun changed() {
        state?.let { store.save(it) }
        version.value++
    }

    fun conditions(): Conditions {
        val h = LocalTime.now().hour
        val sky = forecast?.today
        return Conditions(night = h >= 22 || h < 5, rain = sky?.rain == true, frost = sky?.frost == true, heat = sky?.heat == true)
    }

    /** The floor the hero is on, rebuilt from the graph when the run moves on. */
    fun floor(): Floor? {
        val s = state ?: return null; val run = s.run ?: return null; val g = graph ?: return null; val d = district ?: return null
        val key = "${run.seed}/${run.depth}/${run.floorStart}"
        if (key != floorKey || floorCache == null) {
            if (run.floorStart !in g.nodes.indices) return null
            floorCache = Floor(g, d, run.depth, run.seed, run.floorStart, Conditions(night = run.night, rain = run.rain))
            floorKey = key
        }
        return floorCache
    }

    /** Loads the stored district. Heavy: call off the main thread. */
    fun loadDistrict() {
        val s = state ?: return
        val box = s.box ?: return
        val tiles = store.tiles()
        val json = if (tiles == null) store.district() ?: return else null
        stage.value = "parse"
        val d = if (tiles != null) Tiles.parse(tiles, box) else District.parse(json!!, box)
        val g = Graph.build(d)
        district = d; graph = g; floorKey = null
        stage.value = ""
        android.util.Log.i("Delve", "district ${d.w}x${d.h}: ${g.nodes.size} junctions, ${g.edges.size} corridors")
    }

    fun adopt(s: GameState) { state = s; loadDistrict(); changed() }

    fun create(name: String, cls: String, female: Boolean) {
        state = game.newGame(name, cls, female)
        changed()
    }

    /** Downloads the district around a point (the camp, or wherever an expedition starts far from it). */
    suspend fun mapAround(at: Geo) {
        val s = state ?: return
        if (BuildConfig.DEMO) {
            // the demo has no GPS and no download: the camp stands on Petrogradka, the map comes with the APK
            val json = DemoSeed.json(this) ?: return
            store.saveDistrict(json)
            s.home = DemoSeed.HOME; s.box = DemoSeed.BOX
            here.value = DemoSeed.HOME
            loadDistrict()
            changed()
            return
        }
        val box = District.box(at.lat, at.lon, DISTRICT_HALF)
        stage.value = "download"
        val tiles = try { withContext(Dispatchers.IO) { Tiles.download(box) } } catch (e: Exception) { stage.value = ""; throw e }
        store.saveTiles(tiles)
        s.box = box
        loadDistrict()
        changed()
    }

    suspend fun setCamp(at: Geo) {
        state?.home = at
        mapAround(at)
    }

    /** True when the point is near enough the middle of the stored district for a whole floor around it. */
    fun covers(at: Geo): Boolean {
        val d = district ?: return false
        val x = d.x(at.lon); val y = d.y(at.lat)
        val margin = (Floor.RADIUS_M / d.mpp).toFloat() * .75f
        return x > margin && y > margin && x < d.w - margin && y < d.h - margin
    }

    /** Starts an expedition from the junction nearest to where the hero stands. Returns an error key or null. */
    suspend fun descend(minutes: Int): String? {
        val s = state ?: return "state"
        val at = here.value ?: return "gps"
        if (!BuildConfig.DEMO && !covers(at)) runCatching { mapAround(at) }.onFailure { return "net" }
        val d = district ?: return "map"; val g = graph ?: return "map"
        val x = d.x(at.lon); val y = d.y(at.lat)
        val n = g.nearestNode(x, y) ?: return "streets"
        if (hypot(n.x - x, n.y - y) * d.mpp > 120) return "streets"
        synchronized(this) {
            s.torchPick = minutes
            val run = game.startRun(s, n.id, minutes, System.currentTimeMillis(), System.nanoTime() xor at.lat.toBits())
            run.at = n.id; run.visited += n.id; run.seen += n.id
            conditions().let { run.night = it.night; run.rain = it.rain }
            floor()?.let { f -> game.look(s, f, x, y) }
            warned = 0
            run.trail += at.lat; run.trail += at.lon
        }
        WalkService.start(app)
        changed()
        return null
    }

    /** News that needs no choice: it goes to the journal and a toast, not a sheet. */
    private fun quiet(e: Ev) = e is Ev.News || e is Ev.LevelUp || (e is Ev.Fought && e.won)

    private fun push(all: List<Ev>) {
        if (all.isEmpty()) return
        val run = state?.run
        for (e in all.filter(::quiet)) {
            when (e) {
                is Ev.News -> run?.note(Tx(e.title.ru + ". " + e.text.ru, e.title.en + ". " + e.text.en))
                is Ev.LevelUp -> run?.note(Tx("Новый уровень: ${e.level}!", "New level: ${e.level}!"))
                is Ev.Fought -> run?.note(Tx("${e.foe.beast.name.ru} отстал. +${e.coins} монет", "${e.foe.beast.name.en} gave up. +${e.coins} coins"))
                else -> Unit
            }
        }
        toasts.value = (toasts.value + all.filter(::quiet)).takeLast(4)
        val evs = all.filterNot(::quiet)
        if (!visible && all.any(::quiet) && evs.isEmpty() && state?.vibrate != false) Buzz.play(app, Buzz.ROOM)
        if (evs.isEmpty()) return
        events.value = events.value + evs
        if (!visible) {
            val s = state
            val ru = ru()
            for (e in evs) {
                when (e) {
                    is Ev.Meet -> { if (s?.vibrate != false) Buzz.play(app, Buzz.CHASE); WalkService.alert(app, e.foe.beast.name(ru), if (ru) "Кто-то в комнате впереди. Загляни в приложение." else "Something is in the room ahead. Take a look.") }
                    is Ev.Stairs -> { if (s?.vibrate != false) Buzz.play(app, Buzz.STAIRS); WalkService.alert(app, if (ru) "Лестница вниз" else "Stairs down", if (ru) "Ты нашёл спуск на следующий этаж." else "You found the way to the next floor.") }
                    is Ev.Well -> if (s?.vibrate != false) Buzz.play(app, Buzz.STAIRS)
                    is Ev.Fought -> WalkService.alert(app, e.foe.beast.name(ru), if (ru) "Погоня окончена: ты оторвался." else "The chase is over: you shook it off.")
                    is Ev.Ended -> WalkService.alert(app, if (ru) "Вылазка окончена" else "Expedition over", if (ru) "Ты снова в лагере." else "You're back at camp.")
                    else -> if (s?.vibrate != false) Buzz.play(app, Buzz.ROOM)
                }
            }
        }
    }

    fun ru() = app.resources.configuration.locales[0].language == "ru"

    /** The screen dealt with the oldest event. */
    fun pop() { events.value = events.value.drop(1) }

    /** The map showed the oldest toast long enough. */
    fun toastDone(e: Ev) { toasts.value = toasts.value.filter { it !== e } }

    /**
     * A raw position from the phone. It goes through the same filter as Fogwalk's walks first: network positions only
     * place the dot, lone jumps are dropped, standing still adds nothing, and a ride (a tram, a bike) neither wears
     * a chaser out nor walks into halls.
     */
    @Synchronized
    fun fix(raw: Fix) {
        // debug: once the hero is walked by taps, the real GPS keeps out of it until the expedition ends
        if (manual && !raw.simulated) return
        val step = track.add(raw)
        if (step == null) {
            if (!raw.gps && !track.hasGps(raw.time)) here.value = Geo(raw.lat, raw.lon)
            return
        }
        val at = Geo(step.lat, step.lon)
        here.value = at
        riding.value = step.fast
        val s = state ?: return
        val run = s.run ?: return
        val f = floor() ?: return
        val d = district ?: return
        val out = ArrayList<Ev>()
        val t = System.currentTimeMillis()
        if (step.fast) { changed(); return }
        if (step.meters > 0) game.walked(s, step.meters, out)
        val x = d.x(at.lon); val y = d.y(at.lat)
        if (run.steps.size < 2 || hypot(run.steps[run.steps.size - 2] - x, run.steps[run.steps.size - 1] - y) * d.mpp > 6) { run.steps += x; run.steps += y }
        val tr = run.trail
        if (tr.size < 2 || Geo(tr[tr.size - 2], tr[tr.size - 1]).metres(at) > 15) { tr += at.lat; tr += at.lon }
        game.look(s, f, x, y)
        // a room is entered within ~18 m of its junction
        val enter = 18.0 / d.mpp
        val near = f.nodes.minByOrNull { val n = f.g.nodes[it]; (n.x - x) * (n.x - x) + (n.y - y) * (n.y - y) }
        if (near != null && hypot(f.g.nodes[near].x - x, f.g.nodes[near].y - y) <= enter && near != run.at && run.hp > 0) {
            game.enter(s, f, near, t, Conditions(night = run.night, rain = run.rain), out)
        }
        push(out)
        checkEnd()
        changed()
    }

    /** The torch and the hero's health: the expedition ends when either gives out. */
    @Synchronized
    fun checkEnd() {
        val s = state ?: return
        val run = s.run ?: return
        val now = System.currentTimeMillis()
        val left = game.torchLeft(run, now)
        if (left <= 5 && warned < 1) { warned = 1; if (s.vibrate) Buzz.play(app, Buzz.WARN) }
        if (left <= 1 && warned < 2) { warned = 2; if (s.vibrate) Buzz.play(app, Buzz.WARN) }
        when {
            run.hp <= 0 -> finish("faint")
            left <= 0 -> finish("dark")
            else -> WalkService.update(app, torchLine(left))
        }
    }

    private fun torchLine(left: Double): String {
        val m = left.toInt(); val ru = ru()
        val s = state?.run
        return if (ru) "Этаж ${s?.depth ?: 1} · факел: $m мин" else "Floor ${s?.depth ?: 1} · torch: $m min"
    }

    @Synchronized
    fun finish(how: String) {
        val s = state ?: return
        if (s.run == null) return
        val e = game.end(s, System.currentTimeMillis(), LocalDate.now().toEpochDay(), how)
        events.value = listOf(e)
        floorKey = null
        manual = false
        WalkService.stop(app)
        if (!visible) push(listOf(e))
        changed()
    }

    // ——— choices from the screens ———

    private fun act(f: (MutableList<Ev>) -> Unit) {
        val out = ArrayList<Ev>()
        synchronized(this) { f(out) }
        pop()
        if (out.isNotEmpty()) events.value = out + events.value
        checkEnd()
        changed()
    }

    fun fight(node: Int, foe: Foe) {
        val s = state ?: return
        pop()
        battle.value = synchronized(this) { game.battle(s, node, foe) }
    }

    /** One round of the fight; when it is over, its outcome becomes a sheet. */
    fun move(m: Move) {
        val s = state ?: return
        val b = battle.value ?: return
        val out = ArrayList<Ev>()
        val over = synchronized(this) { game.move(s, b, m, out) }
        battle.value = null
        battle.value = if (over) null else b
        if (out.isNotEmpty()) {
            // a level-up during the fight is quiet news; the outcome is a sheet
            val loud = out.filter { it is Ev.Fought }
            toasts.value = (toasts.value + out.filter { it is Ev.LevelUp }).takeLast(4)
            events.value = loud + events.value
        }
        checkEnd()
        changed()
    }

    /** The hero fights the rest alone. */
    fun autoFight() {
        val s = state ?: return
        val b = battle.value ?: return
        val out = ArrayList<Ev>()
        synchronized(this) { game.auto(s, b, out) }
        battle.value = null
        toasts.value = (toasts.value + out.filter { it is Ev.LevelUp }).takeLast(4)
        events.value = out.filter { it is Ev.Fought } + events.value
        checkEnd()
        changed()
    }

    fun takeOrder(id: Long) { state?.let { game.takeOrder(it, id) }; changed() }
    fun dropOrder(id: Long) { state?.let { game.dropOrder(it, id) }; changed() }
    fun board() { val s = state ?: return; if (s.boardDay != LocalDate.now().toEpochDay() || s.board.isEmpty()) { game.board(s, LocalDate.now().toEpochDay()); changed() } }
    fun flee(node: Int, foe: Foe) = act { state?.let { game.flee(it, node, foe) } }
    fun tame(node: Int, foe: Foe) = act { out -> state?.let { game.tame(it, node, foe, out) } }
    fun descendStairs(node: Int, levels: Int) = act { out ->
        val s = state ?: return@act
        game.descend(s, node, levels, out)
        floorKey = null
        val d = district; val at = here.value
        floor()?.let { f -> if (d != null && at != null) game.look(s, f, d.x(at.lon), d.y(at.lat)) }
    }
    fun bless(node: Int, which: Int) = act { state?.let { game.bless(it, node, which) } }
    fun openVault(node: Int): Boolean { var ok = false; act { out -> ok = state?.let { game.openVault(it, node, out) } == true }; return ok }
    fun dismiss() { pop(); changed() }

    /** The room the hero stands in, once more (the stairs, the well, the merchant). */
    fun reopen() {
        val s = state ?: return; val f = floor() ?: return
        val out = ArrayList<Ev>()
        synchronized(this) { game.revisit(s, f, conditions(), out) }
        events.value = events.value + out
    }

    fun goUp() { pop(); finish("up") }
    fun recall(): Boolean { val s = state ?: return false; if (!game.use(s, "recall")) return false; finish("up"); return true }
    fun drink(): Int { val s = state ?: return 0; val g = synchronized(this) { game.drink(s) }; changed(); return g }
    fun smoke(): Boolean { val s = state ?: return false; val ok = synchronized(this) { game.smoke(s) }; changed(); return ok }
    fun readMap(): Boolean { val s = state ?: return false; val f = floor() ?: return false; val ok = synchronized(this) { game.readMap(s, f) }; changed(); return ok }
    fun buy(item: Item): Boolean { val s = state ?: return false; val ok = synchronized(this) { game.buy(s, item) }; changed(); return ok }
    fun buyGood(id: String): Boolean { val s = state ?: return false; val ok = synchronized(this) { game.buyGood(s, id) }; changed(); return ok }
    fun sellFromBag(uid: Long) { val s = state ?: return; synchronized(this) { game.sellFromBag(s, uid) }; changed() }

    fun equip(uid: Long) { state?.let { game.equip(it, uid) }; changed() }
    fun sell(uid: Long) { state?.let { game.sell(it, uid) }; changed() }
    fun build(id: String) { state?.let { game.build(it, id) }; changed() }
    fun upgrade(uid: Long) { state?.let { game.upgrade(it, uid) }; changed() }
    fun brew(r: Recipe) { state?.let { game.brew(it, r) }; changed() }
    fun learn(id: String) { state?.let { game.learn(it, id) }; changed() }
    fun pet(id: String?) { state?.companion = id; changed() }
    fun hintSeen(key: String) { state?.hints?.add(key); changed() }
    fun hintAgain(key: String) { state?.hints?.remove(key); changed() }
    fun setVibrate(on: Boolean) { state?.vibrate = on; changed() }

    fun refreshWeather() {
        val h = state?.home ?: return
        if (forecast != null && System.currentTimeMillis() - forecast!!.at < 3 * 3600_000L) return
        scope.launch(Dispatchers.IO) {
            runCatching { Weather.fetch(h.lat, h.lon) }.getOrNull()?.let { f -> forecast = f; version.value++ }
        }
    }

    private var walkJob: kotlinx.coroutines.Job? = null
    /** Debug builds: the hero is being walked by taps, not by the GPS. */
    @Volatile var manual = false

    /** Debug and demo: walks the hero in small steps to a map point, as the GPS would. */
    fun walkTo(x: Float, y: Float) {
        val d = district ?: return
        val from = here.value ?: return
        // along the corridors when on a floor: from the room the hero is in to the room nearest the press
        val pts = ArrayList<Geo>()
        val f = floor(); val run = state?.run
        if (f != null && run != null && run.at >= 0) {
            val target = f.nodes.minByOrNull { val n = f.g.nodes[it]; (n.x - x) * (n.x - x) + (n.y - y) * (n.y - y) }
            val route = target?.let { f.route(run.at, it, 3000.0) }
            if (route != null) {
                var n = run.at
                pts += Geo(d.lat(f.g.nodes[n].y), d.lon(f.g.nodes[n].x))
                for (e in route.reversed()) {
                    val edge = f.g.edges[e]
                    val p = if (edge.a == n) edge.pts else Graph.reverse(edge.pts)
                    for (i in 2 until p.size step 2) pts += Geo(d.lat(p[i + 1]), d.lon(p[i]))
                    n = edge.other(n)
                }
            }
        }
        if (pts.isEmpty()) { pts += from; pts += Geo(d.lat(y), d.lon(x)) }
        // a new tap changes course: the walk in progress stops where it is
        walkJob?.cancel()
        if (!BuildConfig.DEMO) manual = true
        walkJob = scope.launch {
            var prev = from
            for (to in pts) {
                val n = maxOf(1, (prev.metres(to) / 8).toInt())
                for (i in 1..n) {
                    val k = i.toDouble() / n
                    fix(Fix(prev.lat + (to.lat - prev.lat) * k, prev.lon + (to.lon - prev.lon) * k, 5f, System.currentTimeMillis(), simulated = true))
                    kotlinx.coroutines.delay(40)
                }
                prev = to
            }
        }
    }

    /** The whole save as text, to keep or move to another phone. */
    fun backup(): String = org.json.JSONObject().put("app", "delve").put("v", 1).put("state", state?.toJson()).toString(1)

    suspend fun restore(text: String) {
        val o = org.json.JSONObject(text)
        require(o.optString("app") == "delve") { "not a delve file" }
        val s = GameState.fromJson(o.getJSONObject("state"))
        s.run = null
        state = s
        val h = s.home
        if (h != null) mapAround(h) else changed()
    }
}

class DelveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch {
            runCatching {
                if (BuildConfig.DEMO) DemoSeed.seedIfEmpty(container, BuildConfig.SHOTS) else container.loadDistrict()
            }.onFailure { android.util.Log.e("Delve", "district", it) }
            if (BuildConfig.DEMO && container.here.value == null) {
                // the demo has no GPS: the hero stands in the room they last entered
                val run = container.state?.run; val d = container.district; val g = container.graph
                container.here.value = if (run != null && d != null && g != null && run.at in g.nodes.indices) g.nodes[run.at].let { app.delve.domain.Geo(d.lat(it.y), d.lon(it.x)) } else container.state?.home ?: DemoSeed.HOME
            }
            if (container.state?.run != null && !BuildConfig.DEMO) WalkService.start(this@DelveApp)
            container.version.value++
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as DelveApp).container

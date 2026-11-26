package app.delve.domain

import java.util.Random
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The hero's numbers right now. */
data class Stats(val maxHp: Int, val atk: Int, val def: Int, val dmg: Int, val light: Int, val chase: Int, val torch: Int,
                 val luck: Int, val traps: Int, val loot: Int, val heal: Int, val sell: Int)

/** A creature's numbers at a tier. */
data class Foe(val beast: Beast, val tier: Int, val hp: Int, val atk: Int, val def: Int, val dmg: Int, val stamina: Int, val xp: Int, val coins: Int, val boss: Boolean, val elite: Boolean = false)

/** Something the screen must show: a room's contents, a fight, a question. */
sealed class Ev {
    /** Plain news: an empty hall, a spring, a trap. [items]/[stuff]/[coins] are what was found. */
    class News(val kind: RoomKind, val title: Tx, val text: Tx, val coins: Int = 0, val items: List<Item> = emptyList(), val stuff: Map<String, Int> = emptyMap(), val hpDelta: Int = 0) : Ev()
    /** A creature: fight here, run (a chase), offer a treat or throw smoke. */
    class Meet(val node: Int, val foe: Foe) : Ev()
    /** The outcome of a fight. */
    class Fought(val foe: Foe, val won: Boolean, val log: List<Tx>, val hpLost: Int, val coins: Int, val items: List<Item>, val stuff: Map<String, Int>, val fled: Boolean) : Ev()
    class Tamed(val beast: Beast, val ok: Boolean) : Ev()
    /** Stairs down: go now? */
    class Stairs(val node: Int, val levels: Int) : Ev()
    /** A well up: back to camp? */
    class Well(val node: Int) : Ev()
    class Shop(val node: Int, val stock: List<Item>, val goods: List<String>) : Ev()
    class Vault(val node: Int) : Ev()
    class Shrine(val node: Int, val offer: List<Int>) : Ev()
    class LevelUp(val level: Int) : Ev()
    /** The expedition is over: what came home, at what depth stake, and which orders paid. */
    class Ended(val log: RunLog, val lost: Boolean, val mult: Double = 1.0, val done: List<Contract> = emptyList(),
                val items: List<Item> = emptyList(), val stuff: Map<String, Int> = emptyMap()) : Ev()
}

/** A fight in rounds: the creature shows what it means to do, the hero answers with a move. */
class Battle(val node: Int, val foe: Foe, var hp: Int, var intent: Intent) {
    var round = 0
    val log = ArrayList<Tx>()
    var lost = 0
    /** What the last round did, for the screen to animate: dealt to the creature, taken by the hero, a crit or counter. */
    var dealt = 0
    var taken = 0
    var crit = false
}

/**
 * The rules. Stateless: everything lives in [GameState] and the [Floor] (rebuilt from the graph), so the
 * same calls drive the app, the demo and the tests.
 */
/** Marks a resolve asked for by the hero (a tap on the room card), not by walking in. */
val REVISIT = Conditions()

class Game {
    fun newGame(name: String, cls: String, female: Boolean): GameState {
        val s = GameState(name, cls, female)
        val weapon = when (cls) { "warrior" -> "sword"; "ranger" -> "sling"; "rogue" -> "dagger"; else -> "staff" }
        for (b in listOf(weapon, "jerkin", "torch")) {
            val it = Item(s.nextUid++, b, 1, Rarity.COMMON)
            s.gear += it; s.equipped[it.slot] = it.uid
        }
        s.stuff["potion"] = 2
        s.stuff["treat"] = 1
        return s
    }

    // ——— numbers ———

    fun mods(s: GameState, run: Run? = s.run): Mods {
        var m = heroClass(s.cls).mods
        for (t in heroClass(s.cls).tiers) for (k in listOf(t.first, t.second)) if (k.id in s.skills) m += k.mods
        for (slot in Slot.entries) s.equippedIn(slot)?.let { m += it.mods }
        for (b in BUILDINGS) m += b.per.times(s.level(b.id).toDouble())
        s.companion?.let { c -> beast(c).pet?.let { p -> m += p.times(1 + .2 * s.level("menagerie")) } }
        run?.blessings?.forEach { m += BLESSINGS[it].second }
        return m
    }

    fun stats(s: GameState, run: Run? = s.run): Stats {
        val m = mods(s, run)
        return Stats(
            maxHp = 30 + 6 * (s.level - 1) + m.hp, atk = m.atk + s.level / 2, def = 10 + m.def, dmg = 2 + m.dmg + s.level / 3,
            light = (35 + m.light / 2).coerceAtMost(90), chase = m.chase.coerceIn(-30, 60), torch = m.torch, luck = m.luck, traps = m.traps.coerceAtMost(90),
            loot = m.loot, heal = m.heal, sell = m.sell,
        )
    }

    /**
     * The creature as the hero meets it. A quarter of its numbers come from its kind and the floor, three quarters adapt to the hero:
     * its health to the hero's blows (about three to four hits), its damage to the hero's health, its aim to the
     * hero's armour – so better gear helps, but never makes the dungeon toothless. Deeper floors push all of it up.
     */
    fun foeFor(s: GameState, b: Beast, depth: Int, night: Boolean, elite: Boolean = false): Foe {
        val a = foe(b, depth, night)
        val st = stats(s)
        val gloom = s.run?.gloom ?: 0
        val d = depth.toDouble()
        val boss = a.boss
        val hits = (2.8 + .1 * d).coerceAtMost(5.0) * (if (boss) 3.0 else 1.0)
        val hpR = hits * (st.dmg + 2.5)
        val hitMe = (.5 + .02 * d).coerceAtMost(.78)
        val atkR = st.def - 21 + 20 * hitMe
        val dmgR = st.maxHp * (.08 + .008 * d).coerceAtMost(.2) * (if (boss) 1.3 else 1.0)
        val myHit = (.78 - .01 * d).coerceAtLeast(.55)
        val defR = st.atk + 21 - 20 * myHit
        var k = (if (night && !boss) 1.15 else 1.0) * (if (gloom >= 1) 1.15 else 1.0)
        if (elite || boss) k *= if (gloom >= 5) 1.33 else 1.0
        val eHp = if (elite) 1.6 else 1.0; val eDmg = if (elite) 1.3 else 1.0
        return a.copy(
            hp = max(3, (mix(a.hp.toDouble(), hpR) * k * eHp).roundToInt()),
            atk = mix(a.atk.toDouble(), atkR).roundToInt() + if (elite) 1 else 0,
            def = mix(a.def.toDouble(), defR).roundToInt() + if (elite) 1 else 0,
            dmg = max(1, (mix(a.dmg.toDouble(), dmgR) * k * eDmg).roundToInt()),
            xp = (a.xp * (if (elite) 2.0 else 1.0)).roundToInt(),
            coins = (a.coins * (if (elite) 2.5 else 1.0)).roundToInt(),
            elite = elite,
        )
    }

    /** A quarter from the kind and the floor, three quarters adapted to the hero. */
    private fun mix(fixed: Double, adapted: Double) = fixed * .25 + adapted * .75

    fun foe(b: Beast, depth: Int, night: Boolean): Foe {
        val boss = b.hab == Hab.BOSS
        val t = b.tier + max(0, (depth - b.min) / 3) + if (night && !boss) 1 else 0
        val hp = (4 + 5 * t) * (if (boss) 3 else 1)
        return Foe(b, t, hp, 1 + t, 6 + t, 1 + (t * 1.6).roundToInt(), ((120 + 45 * t) * if (boss) 1.6 else 1.0).toInt(), 12 * t * if (boss) 3 else 1, 4 * t * if (boss) 4 else 1, boss)
    }

    /** «Мрак» levels open: one per floor master beaten. */
    fun gloomOpen(s: GameState) = s.camps.size.coerceAtMost(GLOOM.size)

    /** How much faster than real time the torch burns on a floor: the deep air eats it. */
    fun burnRate(run: Run) = 1.0 + (.03 * (run.depth - 1)).coerceAtMost(.5)

    fun need(level: Int) = 60 * level + 20 * level * level

    /** Skill pairs the hero may pick from now (one per tier reached). */
    fun skillChoices(s: GameState): List<Pair<Skill, Skill>> = heroClass(s.cls).tiers.filterIndexed { i, t ->
        s.level >= SKILL_LEVELS[i] && t.first.id !in s.skills && t.second.id !in s.skills
    }

    fun learn(s: GameState, id: String): Boolean {
        val pair = skillChoices(s).firstOrNull { it.first.id == id || it.second.id == id } ?: return false
        s.skills += id
        return pair.first.id == id || pair.second.id == id
    }

    // ——— expeditions ———

    fun startRun(s: GameState, start: Int, minutes: Int, now: Long, seed: Long): Run {
        val st = stats(s, null)
        val depth = if (s.startDepth in s.camps) s.startDepth else 1
        val run = Run(seed, depth, start, now, now + (minutes + st.torch) * 60_000L, minutes + st.torch, st.maxHp)
        run.from = depth; run.maxDepth = depth
        run.gloom = s.gloom.coerceIn(0, gloomOpen(s))
        if (run.gloom >= 2) run.torchEnd -= ((minutes + st.torch) * 60_000L * .15).toLong()
        s.run = run
        return run
    }

    /** Where the torch stands: minutes left (may be below zero). */
    fun torchLeft(run: Run, now: Long) = (run.torchEnd - now) / 60_000.0

    private fun rnd(run: Run, salt: Int) = Random(run.seed * 1_000_003L + run.depth * 7_919L + salt * 31L + run.walked.toLong())

    /** Marks what the lantern shows around a point: rooms within its reach, and the floor's map if read. */
    fun look(s: GameState, f: Floor, x: Float, y: Float) {
        val run = s.run ?: return
        val r = stats(s).light / f.g.mpp
        for (n in f.nodes) { val nd = f.g.nodes[n]; if (hypot(nd.x - x, nd.y - y) <= r) run.seen += n }
    }

    /**
     * The hero walked [m] metres. Runs a chase on: bites now and then, and the end of it when it is shaken off.
     */
    fun walked(s: GameState, m: Double, out: MutableList<Ev>) {
        val run = s.run ?: return
        run.walked += m
        s.metres += m
        run.prog["metres"] = run.walked.toInt()
        val ch = run.chase ?: return
        ch.left -= m
        val st = stats(s)
        while (ch.left > 0 && ch.total - ch.left >= ch.nextBite) {
            ch.nextBite += 110.0
            val f = foeFor(s, beast(ch.beast), run.depth, false)
            val r = rnd(run, 77 + ch.nextBite.toInt())
            if (r.nextInt(20) + 1 + f.atk >= st.def) {
                val d = max(1, f.dmg - 1 + r.nextInt(3))
                run.hp -= d
                out += Ev.News(RoomKind.LAIR, Tx("Укус на бегу", "Bitten on the run"), Tx("${f.beast.name.ru} догоняет и кусает: −$d.", "${f.beast.name.en} catches up and bites: −$d."), hpDelta = -d)
                if (run.hp <= 0) return
            }
        }
        if (ch.left <= 0) {
            run.chase = null
            val f = foeFor(s, beast(ch.beast), run.depth, false)
            val r = rnd(run, 5)
            val (coins, items, stuff) = spoils(s, f, r)
            lore(s, f.beast.id).beaten++; camp(s, f)
            run.beaten++; run.prog.merge("beaten", 1, Int::plus)
            gainXp(s, f.xp, out)
            out += Ev.Fought(f, true, listOf(Tx("${f.beast.name.ru} выдохся и отстал.", "${f.beast.name.en} ran out of breath and gave up.")), 0, coins, items, stuff, false)
        }
    }

    private fun lore(s: GameState, id: String) = s.lore.getOrPut(id) { Lore() }

    /** Beating a floor's master opens a camp on the floor below: later descents may start there. */
    private fun camp(s: GameState, f: Foe) {
        val run = s.run ?: return
        if (f.boss) s.camps += campDepth(run.depth)
    }

    /** The hero stepped into a room. */
    fun enter(s: GameState, f: Floor, node: Int, now: Long, cond: Conditions, out: MutableList<Ev>) {
        val run = s.run ?: return
        val prev = run.at
        if (prev == node) return
        if (prev >= 0) {
            val e = f.g.edgeBetween(prev, node)
            if (e != null && e.id in f.edges) {
                if (e.id in f.open) explore(s, f, listOf(e.id), out)
                else {
                    // through the rubble: allowed, but the torch pays for it
                    run.torchEnd -= 3 * 60_000L; run.burnt += 3
                    out += Ev.News(RoomKind.EMPTY, Tx("Сквозь завал", "Through the rubble"), Tx("Ты протиснулся через обвал. Факел закоптился: −3 минуты.", "You squeezed through the cave-in. The torch got sooty: −3 minutes."))
                }
            } else {
                // the GPS skipped a junction: the shortest open way between them counts as walked
                val a = f.g.nodes[prev]; val b = f.g.nodes[node]
                val straight = hypot(a.x - b.x, a.y - b.y) * f.g.mpp
                f.route(prev, node, straight * 2.2 + 40)?.let { explore(s, f, it, out) }
            }
        }
        run.at = node
        val first = run.visited.add(node)
        run.seen += node
        if (first) {
            run.rooms++; gainXp(s, 4, out)
            f.rooms[node]?.let { run.prog.merge("room:" + it.kind.name, 1, Int::plus) }
        }
        val room = f.rooms[node] ?: return
        resolve(s, f, room, first, cond, out)
    }

    private fun explore(s: GameState, f: Floor, edges: List<Int>, out: MutableList<Ev>) {
        val run = s.run ?: return
        var xp = 0
        for (e in edges) if (run.explored.add(e)) {
            xp += (f.g.edges[e].len * f.g.mpp / 8).toInt()
            run.seen += f.g.edges[e].a; run.seen += f.g.edges[e].b
        }
        if (xp > 0) gainXp(s, xp, out)
    }

    /** The hero asks for the room they stand in again: the stairs, the well, the merchant. */
    fun revisit(s: GameState, f: Floor, cond: Conditions, out: MutableList<Ev>) {
        val run = s.run ?: return
        val room = f.rooms[run.at] ?: return
        resolve(s, f, room, false, REVISIT, out)
    }

    private fun resolve(s: GameState, f: Floor, room: Room, first: Boolean, cond: Conditions, out: MutableList<Ev>) {
        val run = s.run ?: return
        val n = room.node
        val st = stats(s)
        bitter = run.gloom >= 3
        val r = rnd(run, n)
        val done = n in run.resolved
        // the merchant greets you on the first visit; afterwards a tap on the room card calls him
        val prevSame = !first && run.at == n && cond === REVISIT
        val abv = room.above
        fun meet(id: String) {
            if (run.chase != null) {
                out += Ev.News(room.kind, Tx("Тише…", "Quiet…"), Tx("${beast(id).name.ru} спит. Сначала оторвись от погони, потом возвращайся.", "${beast(id).name.en} is asleep. Shake off your chaser first, then come back."))
                return
            }
            val fo = foeFor(s, beast(id), run.depth, cond.night, n == f.elite)
            lore(s, id).met++
            out += Ev.Meet(n, fo)
        }
        when (room.kind) {
            RoomKind.START -> if (!first && run.depth == 1) out += Ev.Well(n)
            RoomKind.DOWN -> out += Ev.Stairs(n, 1)
            RoomKind.SHAFT -> if (run.depth < 3 || done) out += Ev.News(room.kind, room.kind.title, Tx("Шахта уходит глубоко вниз, но верёвка оборвана. Пока не спуститься.", "The shaft goes deep, but the rope is cut. No way down yet."))
                else out += Ev.Stairs(n, 2)
            RoomKind.UP -> out += Ev.Well(n)
            RoomKind.BOSS -> if (done) out += Ev.Stairs(n, 1) else meet(room.beast ?: bossFor(run.depth).id)
            RoomKind.LAIR, RoomKind.CRYPT -> if (!done) meet(room.beast ?: "rat") else if (room.kind == RoomKind.CRYPT && first) Unit
            RoomKind.MERCHANT -> if (first || prevSame) out += Ev.Shop(n, stock(s, run, n), listOf("potion", "treat", "smoke", "lockpick", "mapscroll", "recall"))
            RoomKind.VAULT -> if (!done) out += Ev.Vault(n)
            RoomKind.SHRINE -> if (!done) {
                val a = r.nextInt(BLESSINGS.size); var b = r.nextInt(BLESSINGS.size - 1); if (b >= a) b++
                out += Ev.Shrine(n, listOf(a, b))
            }
            RoomKind.CHEST -> if (!done) {
                if (room.beast != null) { meet(room.beast); return }
                run.resolved += n
                val (coins, items, stuff) = chest(s, r, 1.0)
                take(run, coins, items, stuff)
                out += Ev.News(room.kind, Tx("Сундук", "A chest"), Tx("Скрипнула крышка.", "The lid creaks open."), coins, items, stuff)
            }
            RoomKind.TRAP -> if (!done) {
                run.resolved += n
                if (r.nextInt(100) < 30 + st.traps + st.luck * 3 - (if (run.gloom >= 6) 20 else 0)) {
                    out += Ev.News(room.kind, Tx("Ловушка!", "A trap!"), Tx("Ты вовремя заметил плиту в полу и перешагнул.", "You spot the pressure plate in time and step over it."))
                } else {
                    val d = (3 + r.nextInt(3) + run.depth) * (if (run.gloom >= 6) 2 else 1)
                    run.hp -= d
                    out += Ev.News(room.kind, Tx("Ловушка!", "A trap!"), Tx("Из стены выскочили шипы. −$d здоровья.", "Spikes shoot out of the wall. −$d health."), hpDelta = -d)
                }
            }
            RoomKind.SPRING -> if (!done) {
                run.resolved += n
                val h = heal(st, st.maxHp * 45 / 100)
                val got = min(h, st.maxHp - run.hp); run.hp += got
                val where = if (abv.isNotBlank()) Tx("Вода пробивается прямо из-под «$abv».", "Water springs from right under «$abv».") else Tx("Холодная чистая вода.", "Cold clear water.")
                out += Ev.News(room.kind, room.kind.title, Tx(where.ru + " +$got здоровья.", where.en + " +$got health."), hpDelta = got)
            }
            RoomKind.REST -> if (!done) {
                run.resolved += n
                val h = heal(st, st.maxHp / 4); val got = min(h, st.maxHp - run.hp); run.hp += got
                run.torchEnd += 2 * 60_000L
                val wax = 1 + r.nextInt(2)
                take(run, 0, emptyList(), mapOf("wax" to wax))
                out += Ev.News(room.kind, room.kind.title, if (got > 0) Tx("Старое кострище. Ты подлил масла в факел (+2 мин) и перевязал раны (+$got).", "An old campfire. You top up the torch (+2 min) and bind your wounds (+$got).")
                    else Tx("Старое кострище. Ты подлил масла в факел: +2 минуты.", "An old campfire. You top up the torch: +2 minutes."), stuff = mapOf("wax" to wax), hpDelta = got)
            }
            RoomKind.GROTTO -> if (!done) {
                if (room.beast != null) { meet(room.beast); return }
                run.resolved += n
                val m = (2 + r.nextInt(3)) * (100 + st.loot) / 100
                take(run, 0, emptyList(), mapOf("mushroom" to m))
                val text = if (abv.isNotBlank()) Tx("Корни парка свисают с потолка, под ними светятся грибы.", "The park's roots hang from the ceiling, mushrooms glow beneath.") else Tx("Светящиеся грибы растут прямо из стен.", "Glowing mushrooms grow right out of the walls.")
                out += Ev.News(room.kind, room.kind.title, text, stuff = mapOf("mushroom" to m))
            }
            RoomKind.RIVER -> if (!done) {
                if (room.beast != null) { meet(room.beast); return }
                run.resolved += n
                val p = 1 + r.nextInt(2) + if (cond.rain) 1 else 0
                take(run, 0, emptyList(), mapOf("pearl" to p))
                out += Ev.News(room.kind, room.kind.title, Tx(if (cond.rain) "После дождя река поднялась и вынесла на берег раковины." else "Чёрная вода течёт под городом. В иле блестят раковины.", if (cond.rain) "The rain has raised the river and washed shells ashore." else "Black water runs under the town. Shells glint in the silt."), stuff = mapOf("pearl" to p))
            }
            RoomKind.EMPTY -> if (first) {
                val line = EMPTY_LINES[r.nextInt(EMPTY_LINES.size)]
                if (r.nextInt(100) < 30 + st.luck * 3) {
                    val coins = (3 + r.nextInt(6)) * (1 + run.depth / 2)
                    val mat = listOf("bone", "iron", "wax")[r.nextInt(3)]
                    take(run, coins, emptyList(), mapOf(mat to 1))
                    out += Ev.News(room.kind, room.kind.title, line, coins, stuff = mapOf(mat to 1))
                } else out += Ev.News(room.kind, room.kind.title, line)
            }
        }
    }

    private var bitter = false
    private fun heal(st: Stats, base: Int) = base * (100 + st.heal) / 100 / (if (bitter) 2 else 1)

    private fun take(run: Run, coins: Int, items: List<Item>, stuff: Map<String, Int>) {
        run.coins += coins; run.bag += items
        for ((k, v) in stuff) { run.stuff.merge(k, v, Int::plus); run.prog.merge("stuff:$k", v, Int::plus) }
    }

    fun newItem(s: GameState, r: Random, depth: Int, luck: Int, slot: Slot? = null): Item {
        val pool = BASES.filter { it.min <= depth && (slot == null || it.slot == slot) }
        val b = pool[r.nextInt(pool.size)]
        val roll = r.nextInt(100) + luck * 2 + depth * 2
        val rar = when { roll >= 112 -> Rarity.LEGEND; roll >= 92 -> Rarity.RARE; roll >= 62 -> Rarity.FINE; else -> Rarity.COMMON }
        val nAff = when (rar) { Rarity.COMMON -> 0; Rarity.FINE -> 1; Rarity.RARE -> 1 + r.nextInt(2); Rarity.LEGEND -> 2 }
        val aff = AFFIXES.shuffled(r).take(nAff).map { it.id }
        return Item(s.nextUid++, b.id, (1 + depth / 2).coerceAtMost(15), rar, aff)
    }

    private fun chest(s: GameState, r: Random, mult: Double): Triple<Int, List<Item>, Map<String, Int>> {
        val run = s.run!!
        val st = stats(s)
        val k = mult * (100 + st.loot) / 100.0
        val coins = ((6 + r.nextInt(10)) * (1 + run.depth * .35) * k).toInt()
        val items = ArrayList<Item>()
        if (r.nextDouble() < .55 * k) items += newItem(s, r, run.depth, st.luck)
        if (mult > 1.5) items += newItem(s, r, run.depth + 2, st.luck + 3)
        val stuff = HashMap<String, Int>()
        if (r.nextDouble() < .5) { val c = listOf("potion", "potion", "treat", "smoke", "lockpick", "mapscroll")[r.nextInt(6)]; stuff[c] = 1 }
        val mat = listOf("bone", "iron", "wax", "mushroom", if (run.depth >= 3) "crystal" else "iron")[r.nextInt(5)]
        stuff.merge(mat, 1 + r.nextInt(2), Int::plus)
        return Triple(coins, items, stuff)
    }

    private fun spoils(s: GameState, f: Foe, r: Random): Triple<Int, List<Item>, Map<String, Int>> {
        val run = s.run!!
        val st = stats(s)
        val coins = (f.coins * (1 + r.nextDouble()) * (100 + st.loot) / 100).toInt()
        val items = ArrayList<Item>()
        if (f.boss || f.elite || r.nextDouble() < .3 + st.luck * .02) items += newItem(s, r, run.depth + if (f.boss || f.elite) 3 else 0, st.luck + if (f.boss) 6 else if (f.elite) 4 else 0)
        val mat = when (f.beast.hab) { Hab.CRYPT -> "bone"; Hab.GROTTO -> "mushroom"; Hab.RIVER -> "pearl"; Hab.DEEP, Hab.BOSS -> "crystal"; Hab.ANY -> listOf("bone", "iron", "wax")[r.nextInt(3)] }
        val stuff = mapOf(mat to 1 + r.nextInt(2) + if (f.boss) 3 else 0)
        take(run, coins, items, stuff)
        return Triple(coins, items, stuff)
    }

    private fun stock(s: GameState, run: Run, node: Int): List<Item> {
        val r = Random(run.seed + node * 13L + run.depth)
        val saved = s.nextUid
        val out = List(3) { newItem(s, r, run.depth + 1, 2) }
        s.nextUid = saved + 3
        return out
    }

    fun price(item: Item) = item.worth * 3

    // ——— choices ———

    /** Fight it out here and now: dice rounds until someone drops, or the creature flees after ten. */
    fun fight(s: GameState, node: Int, f: Foe, out: MutableList<Ev>) {
        val run = s.run ?: return
        val st = stats(s)
        val r = rnd(run, node * 3 + 1)
        var foeHp = f.hp; var lost = 0
        val log = ArrayList<Tx>()
        val name = f.beast.name
        var round = 0
        while (foeHp > 0 && run.hp > 0 && round < 10) {
            round++
            val roll = r.nextInt(20) + 1
            if (roll == 20 || roll + st.atk >= f.def) {
                val d = (st.dmg + r.nextInt(4) + 1) * if (roll == 20) 2 else 1
                foeHp -= d
                log += if (roll >= 19) Tx("Точный удар! −$d", "A perfect hit! −$d") else Tx("Попал: −$d", "Hit: −$d")
            } else log += Tx("Промах.", "Miss.")
            if (foeHp <= 0) break
            val fr = r.nextInt(20) + 1
            if (fr + f.atk >= st.def) {
                val d = max(1, f.dmg + r.nextInt(3) - 1)
                run.hp -= d; lost += d
                log += Tx("${name.ru} попадает: −$d тебе", "${name.en} hits you: −$d")
            } else log += Tx("${name.ru} промахивается.", "${name.en} misses.")
        }
        if (foeHp <= 0) {
            run.resolved += node
            val (coins, items, stuff) = spoils(s, f, r)
            lore(s, f.beast.id).beaten++; camp(s, f); run.beaten++; run.prog.merge("beaten", 1, Int::plus)
            gainXp(s, f.xp, out)
            out.add(0, Ev.Fought(f, true, log, lost, coins, items, stuff, false))
        } else if (run.hp > 0) {
            run.resolved += node
            gainXp(s, f.xp / 2, out)
            log += Tx("${name.ru} убегает в темноту.", "${name.en} flees into the dark.")
            out.add(0, Ev.Fought(f, false, log, lost, 0, emptyList(), emptyMap(), true))
        } else out.add(0, Ev.Fought(f, false, log, lost, 0, emptyList(), emptyMap(), false))
    }

    /** Run for it: the creature follows until the hero has walked it off. */
    fun flee(s: GameState, node: Int, f: Foe) {
        val run = s.run ?: return
        val st = stats(s)
        val len = max(60.0, f.stamina * (100 - st.chase) / 100.0) * (if (run.gloom >= 4) 1.4 else 1.0)
        run.chase = Chase(f.beast.id, f.tier, len, len, 90.0, f.boss)
        run.resolved += node
    }

    fun smoke(s: GameState): Boolean {
        val run = s.run ?: return false
        if (!use(s, "smoke")) return false
        run.chase = null
        return true
    }

    /** Offer a treat: maybe it follows you home. Bosses can't be tamed. */
    fun tame(s: GameState, node: Int, f: Foe, out: MutableList<Ev>) {
        val run = s.run ?: return
        if (f.boss || f.beast.pet == null || !use(s, "treat")) return
        val st = stats(s)
        val r = rnd(run, node * 5 + 3)
        val chance = 45 + st.luck * 4 + 10 * s.level("menagerie") - 6 * (f.tier - 1)
        val ok = r.nextInt(100) < chance.coerceIn(15, 90)
        if (ok) {
            run.resolved += node
            lore(s, f.beast.id).tamed = true
            run.prog.merge("tamed", 1, Int::plus)
            if (s.companion == null) s.companion = f.beast.id
            gainXp(s, f.xp, out)
        } else flee(s, node, f)   // it ate the treat and still wants more: a chase
        out.add(0, Ev.Tamed(f.beast, ok))
    }

    /** Uses one of a stackable thing, from the bag first, then from camp supplies. */
    fun use(s: GameState, id: String): Boolean {
        val run = s.run
        if (run != null && (run.stuff[id] ?: 0) > 0) { run.stuff[id] = run.stuff.getValue(id) - 1; return true }
        if ((s.stuff[id] ?: 0) > 0) { s.stuff[id] = s.stuff.getValue(id) - 1; return true }
        return false
    }

    fun count(s: GameState, id: String) = (s.stuff[id] ?: 0) + (s.run?.stuff?.get(id) ?: 0)

    fun drink(s: GameState): Int {
        val run = s.run ?: return 0
        bitter = run.gloom >= 3
        val st = stats(s)
        if (run.hp >= st.maxHp || !use(s, "potion")) return 0
        val got = min(heal(st, st.maxHp / 2), st.maxHp - run.hp)
        run.hp += got
        return got
    }

    fun readMap(s: GameState, f: Floor): Boolean {
        val run = s.run ?: return false
        if (run.mapped || !use(s, "mapscroll")) return false
        run.mapped = true
        run.seen += f.nodes
        return true
    }

    fun bless(s: GameState, node: Int, which: Int) {
        val run = s.run ?: return
        run.resolved += node
        run.blessings += which
    }

    fun openVault(s: GameState, node: Int, out: MutableList<Ev>): Boolean {
        val run = s.run ?: return false
        if (s.cls != "rogue" && !use(s, "lockpick")) return false
        run.resolved += node
        val (coins, items, stuff) = chest(s, rnd(run, node * 7), 2.2)
        take(run, coins, items, stuff)
        out += Ev.News(RoomKind.VAULT, RoomKind.VAULT.title, Tx("Замок щёлкнул. Внутри – чьи-то забытые сбережения.", "The lock clicks. Inside: somebody's forgotten savings."), coins, items, stuff)
        return true
    }

    fun buy(s: GameState, item: Item): Boolean {
        val run = s.run ?: return false
        val p = price(item) * 100 / (100 + stats(s).sell)
        if (run.coins + s.coins < p) return false
        pay(s, p)
        run.bag += Item(s.nextUid++, item.base, item.tier, item.rarity, item.affixes)
        return true
    }

    fun buyGood(s: GameState, id: String): Boolean {
        val run = s.run ?: return false
        val p = (stuff(id)?.price ?: return false) * 100 / (100 + stats(s).sell)
        if (run.coins + s.coins < p) return false
        pay(s, p)
        run.stuff.merge(id, 1, Int::plus)
        return true
    }

    private fun pay(s: GameState, p: Int) {
        val run = s.run!!
        val fromBag = min(run.coins, p); run.coins -= fromBag; s.coins -= p - fromBag
    }

    fun sellFromBag(s: GameState, uid: Long): Int {
        val run = s.run ?: return 0
        val it = run.bag.firstOrNull { it.uid == uid } ?: return 0
        run.bag.remove(it)
        val p = it.worth * (100 + stats(s).sell) / 100
        run.coins += p
        return p
    }

    /** Down the stairs: a new floor from this junction. */
    fun descend(s: GameState, node: Int, levels: Int, out: MutableList<Ev>) {
        val run = s.run ?: return
        run.depth += levels
        run.maxDepth = max(run.maxDepth, run.depth)
        run.floorStart = node
        run.explored.clear(); run.seen.clear(); run.visited.clear(); run.resolved.clear(); run.steps.clear()
        run.mapped = false
        run.at = node; run.visited += node; run.seen += node
        gainXp(s, 20 * run.depth, out)
    }

    private fun gainXp(s: GameState, xp: Int, out: MutableList<Ev>) {
        s.run?.let { it.xp += xp }
        s.xp += xp
        while (s.xp >= need(s.level)) {
            s.xp -= need(s.level); s.level++
            s.run?.let { it.hp += 6 }
            out += Ev.LevelUp(s.level)
        }
    }

    /**
     * The expedition ends. Up a well or by scroll everything comes home; in the dark or knocked out, half the
     * bag stays below (the coins too), but the experience is kept.
     */
    fun end(s: GameState, now: Long, day: Long, how: String): Ev.Ended {
        val run = s.run!!
        val lost = how == "dark" || how == "faint"
        // the depth stake: what is carried up alive is multiplied; lost in the dark, half stays below and no stake
        // leaving early on purpose keeps everything, but neither the stake nor the orders pay
        val leave = how == "leave"
        val mult = (if (lost || leave) 1.0 else depthMult(run.maxDepth - run.from + 1)) * (1 + .15 * run.gloom)
        val keep = if (lost) run.bag.filterIndexed { i, _ -> i % 2 == 0 } else run.bag
        s.gear += keep
        val coins = if (lost) run.coins / 2 else (run.coins * mult).roundToInt()
        s.coins += coins
        val brought = LinkedHashMap<String, Int>()
        for ((k, v) in run.stuff) {
            val material = stuff(k)?.material == true
            val n = if (lost) v / 2 else if (material) (v * mult).roundToInt() else v
            s.stuff.merge(k, n, Int::plus)
            if (n > 0) brought[k] = n
        }
        s.deepest = max(s.deepest, run.maxDepth)
        run.prog["depth"] = run.maxDepth
        val done = if (lost || leave) emptyList() else s.taken.filter { progress(run, it) >= it.n }
        val out = ArrayList<Ev>()
        for (c in done) {
            s.coins += c.coins
            s.stuff.merge(c.stuffId, c.stuffN, Int::plus)
            gainXp(s, c.xp, out)
        }
        s.taken.removeAll(done.toSet())
        val log = RunLog(day, run.maxDepth, run.walked.toInt(), ((now - run.started) / 60_000).toInt(), run.rooms, run.beaten,
            coins, keep.size, run.xp, how, run.trail.toList())
        s.logs.add(0, log)
        while (s.logs.size > 60) s.logs.removeAt(s.logs.size - 1)
        if (run.gloom > 0 && !lost) gainXp(s, (run.xp * .15 * run.gloom).roundToInt(), out)
        s.run = null
        return Ev.Ended(log, lost, mult, done, keep.toList(), brought)
    }

    // ——— the fight in rounds ———

    private fun nextIntent(f: Foe, r: Random): Intent {
        val heavy = if (f.beast.trait() == Trait.BRUTE || f.boss) 40 else 22
        val guard = if (f.beast.trait() == Trait.ARMOR) 26 else 16
        val x = r.nextInt(100)
        return when { x < heavy -> Intent.HEAVY; x < heavy + guard -> Intent.GUARD; else -> Intent.ATTACK }
    }

    fun battle(s: GameState, node: Int, f: Foe): Battle {
        val run = s.run!!
        return Battle(node, f, f.hp, nextIntent(f, rnd(run, node * 11)))
    }

    /** How the hero's weapon fares against the creature's hide. */
    fun weaponK(s: GameState, f: Foe): Double {
        val blunt = s.equippedIn(Slot.WEAPON)?.base in BLUNT
        return when (f.beast.trait()) {
            Trait.ARMOR -> if (blunt) 1.5 else .5
            Trait.SOFT -> if (blunt) .5 else 1.5
            Trait.GHOST -> .7
            else -> 1.0
        }
    }

    /** One round: the hero's [move] against the creature's shown intent. Returns true when the fight is over. */
    fun move(s: GameState, b: Battle, move: Move, out: MutableList<Ev>): Boolean {
        val run = s.run ?: return true
        val st = stats(s)
        val f = b.foe
        val r = rnd(run, b.node * 13 + b.round * 7 + 1)
        b.round++
        val trait = f.beast.trait()
        val name = f.beast.name
        val k = weaponK(s, f)
        fun dmg(mult: Double) = max(1, ((st.dmg + r.nextInt(4) + 1) * k * mult).roundToInt())
        var foeActs = true
        b.dealt = 0; b.taken = 0; b.crit = false
        val hp0 = b.hp
        when (move) {
            Move.STRIKE -> {
                val roll = r.nextInt(20) + 1
                val target = f.def + (if (b.intent == Intent.GUARD) 5 else 0) + (if (trait == Trait.SWIFT) 3 else 0)
                if (roll == 20 || roll + st.atk >= target) {
                    val d = dmg(if (roll >= 19) 2.0 else 1.0)
                    if (roll >= 19) b.crit = true
                    b.hp -= d
                    b.log += if (roll == 20) Tx("Точный удар! −$d", "A perfect hit! −$d") else Tx("Удар: −$d", "Strike: −$d")
                } else b.log += if (b.intent == Intent.GUARD) Tx("Удар отскочил от защиты.", "The blow bounces off its guard.") else Tx("Промах.", "Miss.")
            }
            Move.GUARD -> b.log += Tx("Ты закрылся.", "You take cover.")
            Move.TRICK -> {
                val roll = r.nextInt(20) + 1 + st.luck + (if (s.cls == "rogue") 4 else 0) + (if (trait == Trait.SWIFT || trait == Trait.GHOST) 4 else 0)
                if (roll >= 11 + f.tier) {
                    val d = max(1, ((st.dmg + r.nextInt(4) + 1) * (if (trait == Trait.GHOST) 1.0 else .6) * (if (b.intent == Intent.GUARD) 2.0 else 1.0)).roundToInt())
                    b.hp -= d; foeActs = false
                    b.log += Tx("Хитрость удалась: ${name.ru} сбит с толку, −$d", "The trick works: ${name.en} is dazed, −$d")
                } else b.log += Tx("${name.ru} не поддался на уловку.", "${name.en} won’t fall for it.")
            }
        }
        if (b.hp > 0 && foeActs && b.intent != Intent.GUARD) {
            val shy = trait == Trait.SHY && st.light >= 60
            val atk = f.atk - if (shy) 3 else 0
            val fr = r.nextInt(20) + 1
            if (move == Move.GUARD || fr + atk >= st.def) {
                var d = max(1, f.dmg + r.nextInt(3) - 1 - if (shy) 1 else 0)
                if (b.intent == Intent.HEAVY) d *= 2
                if (move == Move.GUARD) {
                    d /= 4
                    // guarding a wind-up opens it wide: a full counter-blow; an ordinary blow, a jab
                    val counter = b.intent == Intent.HEAVY
                    val c = if (counter) dmg(1.5) else max(1, (st.dmg / 2.0 * k).roundToInt())
                    b.hp -= c
                    if (counter) b.crit = true
                    b.log += if (counter) Tx("Контратака! −$c", "Counter-blow! −$c") else Tx("Принял удар на защиту (−$d) и ткнул в ответ: −$c", "Took the blow on your guard (−$d) and jabbed back: −$c")
                } else b.log += if (b.intent == Intent.HEAVY) Tx("${name.ru} бьёт со всей силы: −$d тебе", "${name.en} hits with all its might: −$d to you") else Tx("${name.ru} попадает: −$d тебе", "${name.en} hits you: −$d")
                run.hp -= d; b.lost += d; b.taken = d
            } else b.log += Tx("${name.ru} промахивается.", "${name.en} misses.")
        } else if (b.hp > 0 && b.intent == Intent.GUARD && foeActs) b.log += Tx("${name.ru} выжидает.", "${name.en} bides its time.")
        b.dealt = max(0, hp0 - b.hp)
        b.intent = nextIntent(f, r)
        val over = b.hp <= 0 || run.hp <= 0 || b.round >= 6
        if (!over) return false
        if (b.hp <= 0) {
            run.resolved += b.node
            val (coins, items, stuff) = spoils(s, f, r)
            lore(s, f.beast.id).beaten++; camp(s, f); run.beaten++; run.prog.merge("beaten", 1, Int::plus)
            gainXp(s, f.xp, out)
            out.add(0, Ev.Fought(f, true, b.log, b.lost, coins, items, stuff, false))
        } else if (run.hp > 0) {
            run.resolved += b.node
            gainXp(s, f.xp / 2, out)
            b.log += Tx("${name.ru} убегает в темноту.", "${name.en} flees into the dark.")
            out.add(0, Ev.Fought(f, false, b.log, b.lost, 0, emptyList(), emptyMap(), true))
        } else out.add(0, Ev.Fought(f, false, b.log, b.lost, 0, emptyList(), emptyMap(), false))
        return true
    }

    /** The sensible move: guard against a wind-up, strike otherwise. */
    fun advice(b: Battle): Move = if (b.intent == Intent.HEAVY) Move.GUARD else Move.STRIKE

    /** The hero fights it out alone, round by round, always making the sensible move. */
    fun auto(s: GameState, b: Battle, out: MutableList<Ev>) {
        while (!move(s, b, advice(b), out)) Unit
    }

    // ——— the guild's board ———

    fun progress(run: Run, c: Contract): Int = when (c.kind) {
        "depth" -> run.maxDepth
        else -> run.prog[c.kind] ?: 0
    }

    /** A fresh board each day: three orders sized to how deep the hero has been. */
    fun board(s: GameState, day: Long) {
        if (s.boardDay == day && s.board.isNotEmpty()) return
        s.boardDay = day
        s.board.clear()
        val r = Random(day * 7919 + s.name.hashCode())
        val deep = max(2, s.deepest)
        val kinds = mutableListOf(
            "depth" to (deep + r.nextInt(2)).coerceAtMost(30),
            "beaten" to 2 + r.nextInt(3) + deep / 3,
            "metres" to (1 + r.nextInt(3)) * 500,
            "stuff:mushroom" to 3 + r.nextInt(4),
            "stuff:pearl" to 2 + r.nextInt(3),
            "stuff:bone" to 3 + r.nextInt(4),
            "room:GROTTO" to 1, "room:RIVER" to 1, "room:CRYPT" to 1, "room:MERCHANT" to 1, "room:SPRING" to 1,
            "tamed" to 1,
        )
        if (deep >= 3) kinds += "stuff:crystal" to 2 + r.nextInt(3)
        kinds.shuffle(r)
        for ((kind, n) in kinds.take(3)) {
            val hard = when { kind == "depth" -> n * 1.4; kind == "tamed" -> 3.0; kind.startsWith("room:") -> 1.8; kind == "metres" -> n / 400.0; else -> n / 2.0 }
            val mat = listOf("iron", "wax", "pearl", "crystal", "mushroom", "bone")[r.nextInt(6)]
            s.board += Contract(s.nextUid++, kind, n, (15 + 12 * hard + 3 * deep).roundToInt(), mat, 1 + (hard / 2).toInt(), (20 + 15 * hard).roundToInt())
        }
    }

    fun takeOrder(s: GameState, id: Long): Boolean {
        if (s.taken.size >= 2) return false
        val c = s.board.firstOrNull { it.id == id } ?: return false
        s.board.remove(c); s.taken += c
        return true
    }

    fun dropOrder(s: GameState, id: Long) {
        val c = s.taken.firstOrNull { it.id == id } ?: return
        s.taken.remove(c); s.board += c
    }


    // ——— camp ———

    fun equip(s: GameState, uid: Long) { val it = s.gear.firstOrNull { it.uid == uid } ?: return; s.equipped[it.slot] = uid }

    fun sell(s: GameState, uid: Long): Int {
        val it = s.gear.firstOrNull { it.uid == uid } ?: return 0
        if (s.equipped[it.slot] == uid) return 0
        s.gear.remove(it)
        val p = it.worth * (100 + stats(s, null).sell) / 100
        s.coins += p
        return p
    }

    fun canBuild(s: GameState, id: String): Boolean {
        val b = building(id); val lv = s.level(id)
        if (lv >= b.max) return false
        val (c, m) = buildCost(lv)
        return s.coins >= c && (s.stuff[b.material] ?: 0) >= m
    }

    fun build(s: GameState, id: String): Boolean {
        if (!canBuild(s, id)) return false
        val b = building(id); val (c, m) = buildCost(s.level(id))
        s.coins -= c; s.stuff[b.material] = s.stuff.getValue(b.material) - m
        s.buildings[id] = s.level(id) + 1
        return true
    }

    fun upgradeCost(item: Item) = (20 + 25 * item.plus * (item.plus + 1)) to (2 + item.plus * 2)

    fun canUpgrade(s: GameState, item: Item): Boolean {
        if (item.plus >= s.level("forge")) return false
        val (c, m) = upgradeCost(item)
        return s.coins >= c && (s.stuff["iron"] ?: 0) >= m
    }

    fun upgrade(s: GameState, uid: Long): Boolean {
        val item = s.gear.firstOrNull { it.uid == uid } ?: return false
        if (!canUpgrade(s, item)) return false
        val (c, m) = upgradeCost(item)
        s.coins -= c; s.stuff["iron"] = s.stuff.getValue("iron") - m
        item.plus++
        return true
    }

    fun canBrew(s: GameState, r: Recipe) = s.level("alchemist") >= r.minLevel && s.coins >= r.coins && r.needs.all { (k, v) -> (s.stuff[k] ?: 0) >= v }

    fun brew(s: GameState, r: Recipe): Boolean {
        if (!canBrew(s, r)) return false
        s.coins -= r.coins
        for ((k, v) in r.needs) s.stuff[k] = s.stuff.getValue(k) - v
        s.stuff.merge(r.out, 1, Int::plus)
        return true
    }
}

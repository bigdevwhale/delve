package app.delve.domain

import org.json.JSONArray
import org.json.JSONObject

/** A piece of gear. Its numbers follow from the kind, tier, rarity, adjectives and forge upgrades. */
class Item(
    val uid: Long,
    val base: String,
    val tier: Int,
    val rarity: Rarity,
    val affixes: List<String> = emptyList(),
    var plus: Int = 0,
) {
    val kind get() = base(base)
    val slot get() = kind.slot

    val mods: Mods
        get() {
            val k = (1 + .35 * (tier - 1)) * rarity.mult * (1 + .15 * plus)
            // a kind's small numbers (a sword's +1 to hit) still count at tier 1
            var m = Mods(
                hp = scale(kind.mods.hp, k), atk = scale(kind.mods.atk, k), def = scale(kind.mods.def, k), dmg = scale(kind.mods.dmg, k),
                light = scale(kind.mods.light, k), chase = scale(kind.mods.chase, k), torch = scale(kind.mods.torch, k), luck = scale(kind.mods.luck, k),
                traps = scale(kind.mods.traps, k), loot = scale(kind.mods.loot, k), heal = scale(kind.mods.heal, k), sell = scale(kind.mods.sell, k),
            )
            for (a in affixes) affix(a)?.let { m += it.mods.times(1 + .25 * (tier - 1)) }
            return m
        }

    fun name(ru: Boolean): String {
        val k = kind
        val adj = affixes.firstOrNull()?.let { affix(it) }
        val core = if (adj == null) k.name(ru) else if (ru) adj.ru["mfnp".indexOf(k.g).coerceAtLeast(0)] + " " + k.name.ru.replaceFirstChar { it.lowercase() } else adj.en + " " + k.name.en.lowercase()
        return if (plus > 0) "$core +$plus" else core
    }

    /** What the merchant pays for it. */
    val worth: Int get() = (8 * tier * (rarity.ordinal + 1) * (1 + .2 * plus)).toInt()

    fun toJson(): JSONObject = JSONObject().put("u", uid).put("b", base).put("t", tier).put("r", rarity.name).put("a", JSONArray(affixes)).put("p", plus)

    companion object {
        private fun scale(v: Int, k: Double) = if (v == 0) 0 else (v * k).let { if (v > 0) maxOf(1, Math.round(it).toInt()) else Math.round(it).toInt() }

        fun fromJson(o: JSONObject) = Item(
            o.getLong("u"), o.getString("b"), o.optInt("t", 1), runCatching { Rarity.valueOf(o.getString("r")) }.getOrDefault(Rarity.COMMON),
            o.optJSONArray("a")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(), o.optInt("p"),
        )
    }
}

/** A creature on your heels: walk [left] more metres and it gives up. */
class Chase(val beast: String, val tier: Int, val total: Double, var left: Double, var nextBite: Double, val boss: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("b", beast).put("t", tier).put("T", total).put("l", left).put("n", nextBite).put("B", boss)
    companion object {
        fun fromJson(o: JSONObject) = Chase(o.getString("b"), o.getInt("t"), o.getDouble("T"), o.getDouble("l"), o.getDouble("n"), o.optBoolean("B"))
    }
}

/** An expedition under way: the floor, the torch, the bag, what has been found on this floor. */
class Run(
    val seed: Long,
    var depth: Int,
    /** The node the current floor starts from. */
    var floorStart: Int,
    val started: Long,
    /** Wall-clock ms when the torch goes out. */
    var torchEnd: Long,
    val torchMin: Int,
    var hp: Int,
) {
    val explored = HashSet<Int>()
    val seen = HashSet<Int>()
    val visited = HashSet<Int>()
    /** Rooms whose one-time contents are taken (chests opened, beasts beaten…). */
    val resolved = HashSet<Int>()
    /** The node the hero is at (entered last), or -1 between rooms. */
    var at = -1
    var walked = 0.0
    var xp = 0
    var coins = 0
    val bag = ArrayList<Item>()
    val stuff = HashMap<String, Int>()
    val blessings = ArrayList<Int>()
    var chase: Chase? = null
    var maxDepth = 1
    var beaten = 0
    var rooms = 0
    /** The floor map was read: everything is shown. */
    var mapped = false
    /** The way walked, as lat/lon pairs every ~15 m, for the chronicle. */
    val trail = ArrayList<Double>()
    /** The way walked on this floor, map pixels, for the map. */
    val steps = ArrayList<Float>()
    /** Minutes the torch lost to crawling through rubble. */
    var burnt = 0
    /** Counters for the guild's orders: depth, beaten, tamed, metres, room:KIND, stuff:id. */
    val prog = HashMap<String, Int>()
    /** The expedition's journal: quiet news, Russian and English, newest last. */
    val notes = ArrayList<Tx>()
    /** The weather and hour fixed at the descent, so the floors don't change under the hero. */
    var night = false
    var rain = false

    fun note(t: Tx) { notes += t; while (notes.size > 40) notes.removeAt(0) }

    fun toJson(): JSONObject = JSONObject().put("seed", seed).put("depth", depth).put("fs", floorStart).put("st", started).put("te", torchEnd)
        .put("tm", torchMin).put("hp", hp).put("ex", JSONArray(explored.toList())).put("se", JSONArray(seen.toList()))
        .put("vi", JSONArray(visited.toList())).put("re", JSONArray(resolved.toList())).put("at", at).put("w", walked).put("xp", xp)
        .put("c", coins).put("bag", JSONArray(bag.map { it.toJson() })).put("stuff", JSONObject(stuff as Map<*, *>))
        .put("bl", JSONArray(blessings)).put("ch", chase?.toJson()).put("md", maxDepth).put("bt", beaten).put("rm", rooms).put("mp", mapped)
        .put("tr", JSONArray(trail)).put("sp", JSONArray(steps.map { it.toDouble() })).put("bu", burnt)
        .put("ni", night).put("ra", rain).put("pg", JSONObject(prog as Map<*, *>)).put("nt", JSONArray(notes.map { JSONArray(listOf(it.ru, it.en)) }))

    companion object {
        private fun ints(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.getInt(i) } } ?: emptyList()

        fun fromJson(o: JSONObject) = Run(o.getLong("seed"), o.getInt("depth"), o.getInt("fs"), o.getLong("st"), o.getLong("te"), o.getInt("tm"), o.getInt("hp")).also { r ->
            r.explored += ints(o.optJSONArray("ex")); r.seen += ints(o.optJSONArray("se")); r.visited += ints(o.optJSONArray("vi"))
            r.resolved += ints(o.optJSONArray("re")); r.at = o.optInt("at", -1); r.walked = o.optDouble("w"); r.xp = o.optInt("xp")
            r.coins = o.optInt("c")
            o.optJSONArray("bag")?.let { a -> for (i in 0 until a.length()) r.bag += Item.fromJson(a.getJSONObject(i)) }
            o.optJSONObject("stuff")?.let { s -> s.keys().forEach { k -> r.stuff[k] = s.getInt(k) } }
            r.blessings += ints(o.optJSONArray("bl"))
            r.chase = o.optJSONObject("ch")?.let { Chase.fromJson(it) }
            r.maxDepth = o.optInt("md", r.depth); r.beaten = o.optInt("bt"); r.rooms = o.optInt("rm"); r.mapped = o.optBoolean("mp")
            o.optJSONArray("tr")?.let { a -> for (i in 0 until a.length()) r.trail += a.getDouble(i) }
            o.optJSONArray("sp")?.let { a -> for (i in 0 until a.length()) r.steps += a.getDouble(i).toFloat() }
            r.burnt = o.optInt("bu")
            r.night = o.optBoolean("ni"); r.rain = o.optBoolean("ra")
            o.optJSONObject("pg")?.let { p -> p.keys().forEach { k -> r.prog[k] = p.getInt(k) } }
            o.optJSONArray("nt")?.let { a -> for (i in 0 until a.length()) a.optJSONArray(i)?.let { t -> r.notes += Tx(t.getString(0), t.getString(1)) } }
        }
    }
}

/** How an expedition ended, for the chronicle. */
class RunLog(
    val day: Long, val depth: Int, val metres: Int, val minutes: Int, val rooms: Int, val beaten: Int,
    val coins: Int, val items: Int, val xp: Int, val how: String, val trail: List<Double>,
) {
    fun toJson(): JSONObject = JSONObject().put("d", day).put("dp", depth).put("m", metres).put("mi", minutes).put("r", rooms).put("b", beaten)
        .put("c", coins).put("i", items).put("x", xp).put("h", how).put("t", JSONArray(trail))

    companion object {
        fun fromJson(o: JSONObject) = RunLog(o.getLong("d"), o.getInt("dp"), o.getInt("m"), o.optInt("mi"), o.optInt("r"), o.optInt("b"),
            o.optInt("c"), o.optInt("i"), o.optInt("x"), o.optString("h"),
            o.optJSONArray("t")?.let { a -> (0 until a.length()).map { a.getDouble(it) } } ?: emptyList())
    }
}

/** What the bestiary knows about a creature. */
class Lore(var met: Int = 0, var beaten: Int = 0, var tamed: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("m", met).put("b", beaten).put("t", tamed)
    companion object { fun fromJson(o: JSONObject) = Lore(o.optInt("m"), o.optInt("b"), o.optBoolean("t")) }
}

/** The whole save. */
class GameState(
    var name: String,
    var cls: String,
    var female: Boolean,
) {
    var level = 1
    var xp = 0
    var coins = 20
    val skills = ArrayList<String>()
    val gear = ArrayList<Item>()
    val equipped = HashMap<Slot, Long>()
    val stuff = HashMap<String, Int>()
    val buildings = HashMap<String, Int>()
    val lore = HashMap<String, Lore>()
    var companion: String? = null
    var deepest = 0
    var metres = 0.0
    val logs = ArrayList<RunLog>()
    var run: Run? = null
    var home: Geo? = null
    /** The district box: south, west, north, east. */
    var box: DoubleArray? = null
    var nextUid = 1L
    var torchPick = 30
    var vibrate = true
    var tutorial = 0
    /** Pages whose first-visit hint was read. */
    val hints = HashSet<String>()
    /** The guild's board (renewed daily) and the orders taken for the next expedition. */
    val board = ArrayList<Contract>()
    var boardDay = -1L
    val taken = ArrayList<Contract>()

    fun level(b: String) = buildings[b] ?: 0
    fun has(s: String) = (stuff[s] ?: 0) > 0
    fun equippedIn(s: Slot) = gear.firstOrNull { it.uid == equipped[s] }

    fun toJson(): JSONObject = JSONObject().put("name", name).put("cls", cls).put("f", female).put("lv", level).put("xp", xp).put("c", coins)
        .put("sk", JSONArray(skills)).put("gear", JSONArray(gear.map { it.toJson() }))
        .put("eq", JSONObject().also { o -> equipped.forEach { (k, v) -> o.put(k.name, v) } })
        .put("stuff", JSONObject(stuff as Map<*, *>)).put("bld", JSONObject(buildings as Map<*, *>))
        .put("lore", JSONObject().also { o -> lore.forEach { (k, v) -> o.put(k, v.toJson()) } })
        .put("pet", companion).put("deep", deepest).put("m", metres).put("logs", JSONArray(logs.map { it.toJson() }))
        .put("run", run?.toJson()).put("home", home?.let { JSONArray(listOf(it.lat, it.lon)) }).put("box", box?.let { JSONArray(it.toList()) })
        .put("uid", nextUid).put("tp", torchPick).put("vib", vibrate).put("tut", tutorial).put("hints", JSONArray(hints.toList()))
        .put("board", JSONArray(board.map { it.toJson() })).put("bday", boardDay).put("taken", JSONArray(taken.map { it.toJson() }))

    companion object {
        fun fromJson(o: JSONObject) = GameState(o.getString("name"), o.getString("cls"), o.optBoolean("f")).also { s ->
            s.level = o.optInt("lv", 1); s.xp = o.optInt("xp"); s.coins = o.optInt("c")
            o.optJSONArray("sk")?.let { a -> for (i in 0 until a.length()) s.skills += a.getString(i) }
            o.optJSONArray("gear")?.let { a -> for (i in 0 until a.length()) s.gear += Item.fromJson(a.getJSONObject(i)) }
            o.optJSONObject("eq")?.let { e -> e.keys().forEach { k -> runCatching { s.equipped[Slot.valueOf(k)] = e.getLong(k) } } }
            o.optJSONObject("stuff")?.let { e -> e.keys().forEach { k -> s.stuff[k] = e.getInt(k) } }
            o.optJSONObject("bld")?.let { e -> e.keys().forEach { k -> s.buildings[k] = e.getInt(k) } }
            o.optJSONObject("lore")?.let { e -> e.keys().forEach { k -> s.lore[k] = Lore.fromJson(e.getJSONObject(k)) } }
            s.companion = o.optString("pet").takeIf { it.isNotBlank() && it != "null" }
            s.deepest = o.optInt("deep"); s.metres = o.optDouble("m", 0.0)
            o.optJSONArray("logs")?.let { a -> for (i in 0 until a.length()) s.logs += RunLog.fromJson(a.getJSONObject(i)) }
            s.run = o.optJSONObject("run")?.let { Run.fromJson(it) }
            s.home = o.optJSONArray("home")?.let { Geo(it.getDouble(0), it.getDouble(1)) }
            s.box = o.optJSONArray("box")?.let { a -> DoubleArray(4) { a.getDouble(it) } }
            s.nextUid = o.optLong("uid", 1); s.torchPick = o.optInt("tp", 30); s.vibrate = o.optBoolean("vib", true); s.tutorial = o.optInt("tut")
            o.optJSONArray("hints")?.let { a -> for (i in 0 until a.length()) s.hints += a.getString(i) }
            o.optJSONArray("board")?.let { a -> for (i in 0 until a.length()) s.board += Contract.fromJson(a.getJSONObject(i)) }
            s.boardDay = o.optLong("bday", -1)
            o.optJSONArray("taken")?.let { a -> for (i in 0 until a.length()) s.taken += Contract.fromJson(a.getJSONObject(i)) }
        }
    }
}

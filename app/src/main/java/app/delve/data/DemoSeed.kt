package app.delve.data

import app.delve.AppContainer
import app.delve.domain.Game
import app.delve.domain.GameState
import app.delve.domain.Geo
import app.delve.domain.Item
import app.delve.domain.Lore
import app.delve.domain.Rarity
import app.delve.domain.RunLog
import app.delve.domain.Slot
import java.time.LocalDate

/**
 * The demo build (-Pdemo): camp on Petrogradka, St Petersburg, the district bundled in the APK,
 * and a ranger a dozen expeditions in: gear, a built camp, a bestiary half full.
 */
object DemoSeed {
    val HOME = Geo(59.9572, 30.3135)
    /** The box of the bundled Overpass dump. */
    val BOX = doubleArrayOf(59.9455, 30.3040, 59.9585, 30.3260)

    /** The bundled district of the demo: Petrogradka, an Overpass dump in the APK. */
    fun json(c: AppContainer): String? = runCatching { c.app.assets.open("demo/petrogradka.osm.json").bufferedReader().use { it.readText() } }.getOrNull()

    /**
     * The demo starts empty, through the real first screens; only the screenshot build (-Pshots) gets a
     * hero a dozen expeditions in.
     */
    fun seedIfEmpty(c: AppContainer, played: Boolean) {
        if (c.state != null || !played) { c.loadDistrict(); return }
        seed(c, json(c) ?: return)
    }

    fun seed(c: AppContainer, json: String, ru: Boolean = true) {
        c.store.saveDistrict(json)
        val s = hero(c.game, ru)
        s.home = HOME; s.box = BOX
        c.adopt(s)
        c.here.value = HOME
    }

    fun hero(g: Game, ru: Boolean = true): GameState {
        val s = g.newGame(if (ru) "Мира" else "Mira", "ranger", true)
        s.level = 6; s.xp = 310; s.coins = 236
        s.skills += listOf("r_eye", "r_hunter", "r_night")
        fun add(base: String, tier: Int, r: Rarity, vararg aff: String, plus: Int = 0, wear: Boolean = false) {
            val it = Item(s.nextUid++, base, tier, r, aff.toList(), plus)
            s.gear += it
            if (wear) s.equipped[it.slot] = it.uid
        }
        s.gear.clear(); s.equipped.clear()
        add("sling", 3, Rarity.RARE, "swift", "pearl", plus = 2, wear = true)
        add("cloak", 3, Rarity.FINE, "bright", plus = 1, wear = true)
        add("brooch", 2, Rarity.FINE, "lucky", wear = true)
        add("lantern", 3, Rarity.RARE, "deep", "bright", wear = true)
        add("sword", 2, Rarity.COMMON)
        add("mail", 2, Rarity.FINE, "sturdy")
        add("ring", 3, Rarity.LEGEND, "pearl", "lucky")
        add("skull", 1, Rarity.COMMON)
        s.stuff.putAll(mapOf("potion" to 3, "treat" to 2, "smoke" to 1, "mapscroll" to 1, "lockpick" to 1, "recall" to 1,
            "bone" to 7, "mushroom" to 9, "pearl" to 4, "iron" to 6, "crystal" to 2, "wax" to 5))
        s.buildings.putAll(mapOf("forge" to 2, "alchemist" to 2, "cartographer" to 1, "chandler" to 1, "chapel" to 1))
        for ((id, l) in listOf("rat" to Lore(6, 5), "slime" to Lore(3, 3), "bat" to Lore(4, 2), "kobold" to Lore(2, 1, true), "firefly" to Lore(2, 0, true),
            "skeleton" to Lore(3, 3), "draught" to Lore(1, 1), "shroomling" to Lore(4, 4), "crab" to Lore(2, 2), "spider" to Lore(2, 1),
            "goblin" to Lore(1, 1), "candlewraith" to Lore(1, 0), "mimic" to Lore(1, 1), "toad" to Lore(1, 1), "ratking" to Lore(1, 1))) s.lore[id] = l
        s.companion = "firefly"
        s.deepest = 6; s.metres = 23_400.0
        s.camps += 6   // the rat king of floor 5 is beaten: a deep camp and the first «Мрак» level are open
        val today = LocalDate.now().toEpochDay()
        val trail = listOf(59.9572, 30.3135, 59.9578, 30.3150, 59.9586, 30.3171, 59.9580, 30.3195, 59.9566, 30.3203, 59.9555, 30.3180, 59.9560, 30.3150)
        s.logs += RunLog(today - 1, 6, 2870, 41, 23, 7, 118, 3, 640, "up", trail)
        s.logs += RunLog(today - 3, 4, 2140, 30, 17, 4, 64, 2, 380, "dark", trail.reversed())
        s.logs += RunLog(today - 4, 5, 2510, 45, 20, 6, 97, 1, 455, "up", trail)
        s.logs += RunLog(today - 7, 3, 1630, 26, 12, 3, 41, 2, 230, "faint", trail.reversed())
        s.tutorial = 99
        @Suppress("UNUSED_VARIABLE") val unused = Slot.WEAPON
        return s
    }
}

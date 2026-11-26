package app.delve

import app.delve.domain.Battle
import app.delve.domain.Ev
import app.delve.domain.Game
import app.delve.domain.Intent
import app.delve.domain.Move
import app.delve.domain.Trait
import app.delve.domain.beast
import app.delve.domain.trait
import org.junit.Assert.assertTrue
import org.junit.Test

/** The moves must matter: answering the intent should beat mashing «Strike». */
class BattleTest {
    private fun smart(b: Battle): Move = Game().advice(b)

    private fun play(ids: List<String>, depth: Int, geared: Boolean = false, pick: (Battle) -> Move): Pair<Double, Double> {
        val g = Game()
        var lost = 0.0; var wins = 0; var n = 0; var rounds = 0
        for (id in ids) for (seed in 1..60) {
            val s = if (geared) app.delve.data.DemoSeed.hero(g) else g.newGame("T", "warrior", false)
            if (!geared) s.level = 1 + depth
            val run = g.startRun(s, 0, 30, 0L, seed * 977L)
            run.depth = depth
            val hp0 = run.hp
            val b = g.battle(s, seed, g.foeFor(s, beast(id), depth, false))
            val out = ArrayList<Ev>()
            while (!g.move(s, b, pick(b), out)) Unit
            rounds += b.round
            lost += hp0 - run.hp; n++
            if ((out.firstOrNull() as? Ev.Fought)?.won == true) wins++
        }
        if (false) println("  rounds per fight at depth $depth: ${"%.1f".format(rounds.toDouble() / n)}")
        return wins.toDouble() / n to lost / n
    }

    @Test
    fun movesMatter() {
        val ids = listOf("rat", "slime", "skeleton", "draught", "troll", "golem", "spider")
        for (d in listOf(1, 4, 8)) {
            val (ws, ls) = play(ids, d, pick = ::smart)
            val (wm, lm) = play(ids, d, pick = { Move.STRIKE })
            println("depth $d: smart win ${"%.0f".format(ws * 100)}% lost ${"%.1f".format(ls)} hp | strike-only win ${"%.0f".format(wm * 100)}% lost ${"%.1f".format(lm)} hp")
            assertTrue("answering the intent should cost less health at depth $d", ls < lm)
            val (wg, lg) = play(ids, d, geared = true, pick = ::smart)
            println("   geared demo hero: win ${"%.0f".format(wg * 100)}% lost ${"%.1f".format(lg)} hp")
        }
    }
}

class ProgressTest {
    @Test
    fun deepCampsAndDrops() {
        val g = Game()
        val s = g.newGame("T", "mage", false)
        assertTrue(app.delve.domain.heroClass("mage").tiers.size == 10)
        // a beaten fifth-floor master opens floor 6; the next descent may start there, the stake counts from it
        s.camps += app.delve.domain.campDepth(5); s.startDepth = 6
        val run = g.startRun(s, 0, 30, 0L, 1L)
        assertTrue(run.depth == 6 && run.from == 6)
        run.maxDepth = 8
        assertTrue(kotlin.math.abs(app.delve.domain.depthMult(3) - 1.5) < 1e-9)
        // deep kinds never drop on the first floors
        val r = java.util.Random(3)
        repeat(300) { assertTrue(app.delve.domain.base(g.newItem(s, r, 1, 0).base).min <= 1) }
        assertTrue((1..300).any { app.delve.domain.base(g.newItem(s, r, 9, 0).base).min > 1 })
    }
}

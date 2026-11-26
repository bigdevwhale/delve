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

    private fun play(ids: List<String>, depth: Int, pick: (Battle) -> Move): Pair<Double, Double> {
        val g = Game()
        var lost = 0.0; var wins = 0; var n = 0; var rounds = 0
        for (id in ids) for (seed in 1..60) {
            val s = g.newGame("T", "warrior", false)
            s.level = 1 + depth
            val run = g.startRun(s, 0, 30, 0L, seed * 977L)
            run.depth = depth
            val hp0 = run.hp
            val b = g.battle(s, seed, g.foe(beast(id), depth, false))
            val out = ArrayList<Ev>()
            while (!g.move(s, b, pick(b), out)) Unit
            rounds += b.round
            lost += hp0 - run.hp; n++
            if ((out.firstOrNull() as? Ev.Fought)?.won == true) wins++
        }
        println("  rounds per fight at depth $depth: ${"%.1f".format(rounds.toDouble() / n)}")
        return wins.toDouble() / n to lost / n
    }

    @Test
    fun movesMatter() {
        val ids = listOf("rat", "slime", "skeleton", "draught", "troll", "golem", "spider")
        for (d in listOf(1, 4, 8)) {
            val (ws, ls) = play(ids, d, ::smart)
            val (wm, lm) = play(ids, d) { Move.STRIKE }
            println("depth $d: smart win ${"%.0f".format(ws * 100)}% lost ${"%.1f".format(ls)} hp | strike-only win ${"%.0f".format(wm * 100)}% lost ${"%.1f".format(lm)} hp")
            assertTrue("answering the intent should cost less health at depth $d", ls < lm)
        }
    }
}

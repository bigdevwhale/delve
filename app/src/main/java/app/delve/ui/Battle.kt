package app.delve.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.delve.AppContainer
import app.delve.domain.BLUNT
import app.delve.domain.Ev
import app.delve.domain.Intent
import app.delve.domain.Move
import app.delve.domain.Slot
import app.delve.domain.Trait
import app.delve.domain.stuff
import app.delve.domain.trait
import app.delve.ui.theme.C
import app.delve.ui.theme.T
import kotlinx.coroutines.delay

/** The fight in rounds: the creature shows its intent, the hero picks one of three moves. */
@Composable
fun BattleSheet(c: AppContainer) {
    val b by c.battle.collectAsState()
    val v by c.version.collectAsState()
    val battle = b ?: return
    val s = c.state ?: return
    val run = s.run ?: return
    val ru = ru()
    val f = battle.foe
    val st = c.game.stats(s)
    val trait = f.beast.trait()
    v.let { }
    Sheet(null) {
        Text(w(ru, "РАУНД ${battle.round + 1}", "ROUND ${battle.round + 1}"), style = T.Eyebrow.copy(color = C.red))
        Pic(Art.beast(f.beast.id), Modifier.size(140.dp))
        Text(f.beast.name(ru), style = T.Title)
        if (trait != Trait.NONE) {
            Text(trait.title(ru) + ": " + trait.hint(ru), style = T.Small.copy(color = C.ink2), textAlign = TextAlign.Center)
        }
        Gap(8.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(w(ru, "Оно", "It"), style = T.Small, modifier = Modifier.size(42.dp, 18.dp))
            Bar(battle.hp.toFloat() / f.hp, C.red, Modifier.weight(1f), 9.dp)
            Text(" ${maxOf(0, battle.hp)}", style = T.Strong, modifier = Modifier.size(42.dp, 22.dp))
        }
        Gap(4.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(w(ru, "Ты", "You"), style = T.Small, modifier = Modifier.size(42.dp, 18.dp))
            Bar(run.hp.toFloat() / st.maxHp, C.green, Modifier.weight(1f), 9.dp)
            Text(" ${run.hp}", style = T.Strong, modifier = Modifier.size(42.dp, 22.dp))
        }
        Gap(10.dp)
        // what it is about to do
        val (ic, col) = when (battle.intent) { Intent.HEAVY -> "⚡" to C.red; Intent.GUARD -> "◈" to C.blue; Intent.ATTACK -> "⚔" to C.ink }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(col.copy(alpha = .1f)).border(1.4.dp, col, RoundedCornerShape(10.dp)).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$ic  " + battle.intent.title(ru), style = T.Heading.copy(color = col))
            Text(battle.intent.hint(ru), style = T.Small)
        }
        Gap(10.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in Move.entries) {
                val kind = when (m) { Move.STRIKE -> BtnKind.WAX; Move.GUARD -> BtnKind.INK; Move.TRICK -> BtnKind.GOLD }
                Btn(m.title(ru), Modifier.weight(1f), kind, sub = m.hint(ru)) { c.move(m) }
            }
        }
        Gap(8.dp)
        val weapon = s.equippedIn(Slot.WEAPON)
        val k = c.game.weaponK(s, f)
        if (weapon != null) Text(
            w(ru, "Твоё оружие: ${weapon.name(true).lowercase()} (${if (weapon.base in BLUNT) "дробящее" else "режущее"})", "Your weapon: ${weapon.name(false).lowercase()} (${if (weapon.base in BLUNT) "blunt" else "edged"})") +
                if (k != 1.0) " · ×" + "%.1f".format(k) else "",
            style = T.Small.copy(color = if (k > 1) C.green else if (k < 1) C.red else C.muted), textAlign = TextAlign.Center)
        if (battle.log.isNotEmpty()) {
            Gap(8.dp)
            Column(Modifier.fillMaxWidth().paper(C.floor).padding(10.dp)) {
                for (l in battle.log.takeLast(4)) Text("· " + l(ru), style = T.Small.copy(color = C.ink2))
            }
        }
        val potions = c.game.count(s, "potion")
        if (potions > 0 && run.hp < st.maxHp) {
            Gap(8.dp)
            Btn(w(ru, "Выпить зелье (×$potions)", "Drink a potion (×$potions)"), Modifier.fillMaxWidth(), BtnKind.LINE) { c.drink() }
        }
    }
}

/** Quiet news slides in under the floor name for a few seconds, then goes to the journal. */
@Composable
fun ToastLayer(c: AppContainer, modifier: Modifier = Modifier) {
    val list by c.toasts.collectAsState()
    val e = list.firstOrNull()
    LaunchedEffect(e) { if (e != null) { delay(3600); c.toastDone(e) } }
    val ru = ru()
    AnimatedVisibility(e != null, modifier, enter = slideInVertically { -it / 2 } + fadeIn(), exit = slideOutVertically { -it / 2 } + fadeOut()) {
        val ev = e ?: return@AnimatedVisibility
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).paper(C.floor.copy(alpha = .97f)).clickable { c.toastDone(ev) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            when (ev) {
                is Ev.News -> if (ev.kind == app.delve.domain.RoomKind.EMPTY) Pic(Art.item(21), Modifier.size(40.dp)) else Pic(Art.room(ev.kind.art), Modifier.size(40.dp), tint = ColorFilter.tint(C.ink))
                is Ev.LevelUp -> Pic(Art.item(45), Modifier.size(40.dp))
                is Ev.Fought -> Pic(Art.beast(ev.foe.beast.id), Modifier.size(44.dp))
                else -> Box(Modifier.size(40.dp))
            }
            HGap(10.dp)
            Column(Modifier.weight(1f)) {
                val (title, text, coins, stuff) = when (ev) {
                    is Ev.News -> listOf(ev.title(ru), ev.text(ru), ev.coins, ev.stuff)
                    is Ev.LevelUp -> listOf(w(ru, "Уровень ${ev.level}!", "Level ${ev.level}!"), w(ru, "Здоровье выросло.", "Health grew."), 0, emptyMap<String, Int>())
                    is Ev.Fought -> listOf(w(ru, "Оторвался!", "Shaken off!"), w(ru, "${ev.foe.beast.name.ru} выдохся и отстал.", "${ev.foe.beast.name.en} gave up."), ev.coins, ev.stuff)
                    else -> listOf("", "", 0, emptyMap<String, Int>())
                }
                Text(title as String, style = T.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text as String, style = T.Small, maxLines = 2, overflow = TextOverflow.Ellipsis)
                @Suppress("UNCHECKED_CAST") val st = stuff as Map<String, Int>
                val loot = buildList {
                    if ((coins as Int) > 0) add("+$coins " + w(ru, "мон.", "c."))
                    for ((k, n) in st) stuff(k)?.let { add(it.name(ru).lowercase() + " ×$n") }
                }
                if (loot.isNotEmpty()) Text(loot.joinToString(" · "), style = T.Small.copy(color = C.gold))
            }
            if (list.size > 1) Text("+${list.size - 1}", style = T.Small)
        }
    }
}

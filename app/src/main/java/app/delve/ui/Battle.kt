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
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset

/** The fight, simple: the creature shows what it is about to do; strike or guard, or let the hero fight alone. */
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
    val best = c.game.advice(battle)
    v.let { }
    // the round's blow: the creature jolts, the damage floats up, a hit on the hero flashes the edges red
    val shake = remember { Animatable(0f) }
    val float = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(battle.round) {
        if (battle.round == 0) return@LaunchedEffect
        launch { float.snapTo(0f); float.animateTo(1f, tween(450)) }
        if (battle.taken > 0) launch { flash.snapTo(.5f); flash.animateTo(0f, tween(1400)) }
        if (battle.dealt > 0) for (x in listOf(14f, -12f, 8f, -5f, 0f)) shake.animateTo(x, tween(55))
    }
    Box(Modifier.fillMaxSize().drawBehind {
        if (flash.value > 0f) drawRect(Brush.radialGradient(listOf(Color.Transparent, C.red.copy(alpha = flash.value)), radius = size.maxDimension * .7f))
    })
    Sheet(null) {
        // the creature, its health under it, and what it is about to do
        Box(contentAlignment = Alignment.TopCenter) {
            Pic(Art.beast(f.beast.id), Modifier.size(150.dp).offset { IntOffset(shake.value.dp.roundToPx(), 0) })
            if (battle.round > 0) {
                val txt = when { battle.dealt > 0 && battle.crit -> "−${battle.dealt}!"; battle.dealt > 0 -> "−${battle.dealt}"; else -> w(ru, "мимо", "miss") }
                Text(txt, style = (if (battle.crit) T.Display else T.Title).copy(color = if (battle.dealt > 0) C.red else C.muted),
                    modifier = Modifier.offset { IntOffset(78.dp.roundToPx(), (10 - 24 * float.value).dp.roundToPx()) }.alpha(.35f + .65f * float.value))
            }
        }
        if (battle.crit) Text(if (battle.log.lastOrNull()?.ru?.startsWith("Контратака") == true) w(ru, "КОНТРАТАКА!", "COUNTER-BLOW!") else w(ru, "ТОЧНЫЙ УДАР!", "PERFECT HIT!"),
            style = T.Eyebrow.copy(color = C.gold, fontSize = T.Small.fontSize))
        Text(f.beast.name(ru), style = T.Title)
        if (trait != Trait.NONE) Text(trait.title(ru) + " – " + trait.hint(ru).replaceFirstChar { it.lowercase() }, style = T.Small, textAlign = TextAlign.Center)
        Gap(6.dp)
        Bar(battle.hp.toFloat() / f.hp, C.red, Modifier.fillMaxWidth(.7f), 10.dp)
        Gap(10.dp)
        val col = when (battle.intent) { Intent.HEAVY -> C.red; Intent.GUARD -> C.blue; Intent.ATTACK -> C.ink }
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(col.copy(alpha = .1f)).border(1.5.dp, col, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(battle.intent.title(ru), style = T.Heading.copy(color = col))
            Text(battle.intent.hint(ru), style = T.Small)
        }
        Gap(14.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoveButton(Move.STRIKE, 2, w(ru, "Ударить", "Strike"), best == Move.STRIKE, Modifier.weight(1f)) { c.move(Move.STRIKE) }
            MoveButton(Move.GUARD, 3, w(ru, "Защититься", "Guard"), best == Move.GUARD, Modifier.weight(1f)) { c.move(Move.GUARD) }
        }
        Gap(10.dp)
        // your health and what just happened
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.red.copy(alpha = flash.value)).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Pic(statIcon(0), Modifier.size(22.dp)); HGap(6.dp)
            Bar(run.hp.toFloat() / st.maxHp, C.green, Modifier.weight(1f), 9.dp)
            Text("  ${run.hp}/${st.maxHp}", style = T.Strong)
        }
        battle.log.lastOrNull()?.let { Gap(6.dp); Text(it(ru), style = T.Small.copy(color = C.ink2), textAlign = TextAlign.Center) }
        Gap(12.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(w(ru, "Пусть бьётся сам", "Let the hero fight"), Modifier.weight(1f), BtnKind.LINE) { c.autoFight() }
            val potions = c.game.count(s, "potion")
            if (potions > 0 && run.hp < st.maxHp) Btn(w(ru, "Зелье ×$potions", "Potion ×$potions"), Modifier.weight(1f), BtnKind.LINE) { c.drink() }
        }
    }
}

/** A big move button with its icon; the sensible one glows. */
@Composable
private fun MoveButton(m: Move, icon: Int, label: String, best: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(if (best) C.ink else C.floor)
        .border(if (best) 3.dp else 1.5.dp, if (best) C.gold else C.ink.copy(alpha = .5f), RoundedCornerShape(14.dp))
        .clickable(onClick = onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Pic(statIcon(icon), Modifier.size(52.dp))
        Text(label, style = T.Heading.copy(color = if (best) C.gold else C.ink))
    }
    @Suppress("UNUSED_EXPRESSION") m
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

package app.delve.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.delve.AppContainer
import app.delve.domain.BLESSINGS
import app.delve.domain.Ev
import app.delve.domain.Item
import app.delve.domain.RoomKind
import app.delve.domain.stuff
import app.delve.domain.trait
import app.delve.ui.theme.C
import app.delve.ui.theme.T

/** A page of the notebook slid up over the map, the map dimmed behind it. */
@Composable
fun Sheet(onDismiss: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(C.night.copy(alpha = .55f)).clickable(remember { MutableInteractionSource() }, null) { onDismiss?.invoke() }) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().statusBarsPadding().padding(top = 40.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(C.paper)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(44.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(C.ink.copy(alpha = .25f)))
            Gap(10.dp)
            content()
        }
    }
}

/** Whatever the game has to say, oldest first. */
@Composable
fun EventLayer(c: AppContainer) {
    val evs by c.events.collectAsState()
    val e = evs.firstOrNull() ?: return
    val ru = ru()
    when (e) {
        is Ev.News -> Sheet({ c.dismiss() }) { NewsBody(e, ru); Gap(); Btn(w(ru, "Дальше", "Onwards"), Modifier.fillMaxWidth()) { c.dismiss() } }
        is Ev.Meet -> MeetSheet(c, e, ru)
        is Ev.Fought -> FoughtSheet(c, e, ru)
        is Ev.Tamed -> Sheet({ c.dismiss() }) {
            Pic(Art.beast(e.beast.id), Modifier.size(150.dp))
            Text(if (e.ok) w(ru, "Приручен!", "Tamed!") else w(ru, "Не вышло", "No luck"), style = T.Title)
            Gap(6.dp)
            Text(if (e.ok) w(ru, "${e.beast.name.ru} съел лакомство и пошёл за тобой. В лагере его можно взять в спутники.", "${e.beast.name.en} ate the treat and follows you. At camp it can become your companion.")
                 else w(ru, "${e.beast.name.ru} съел лакомство, облизнулся и хочет ещё. Беги!", "${e.beast.name.en} ate the treat, licked its lips and wants more. Run!"), style = T.Body, textAlign = TextAlign.Center)
            Gap()
            Btn(w(ru, "Дальше", "Onwards"), Modifier.fillMaxWidth()) { c.dismiss() }
        }
        is Ev.Stairs -> Sheet({ c.dismiss() }) {
            Pic(Art.room(RoomKind.DOWN.art), Modifier.size(110.dp), tint = androidx.compose.ui.graphics.ColorFilter.tint(C.ink))
            Text(if (e.levels > 1) w(ru, "Шахта вниз", "A shaft down") else w(ru, "Лестница вниз", "Stairs down"), style = T.Title)
            Gap(6.dp)
            val next = (c.state?.run?.depth ?: 1) + e.levels
            Text(w(ru, "Внизу – этаж $next, «${floorName(next, true)}». Там опаснее, но и добыча богаче. Карта этажа начнётся с этого перекрёстка.",
                "Below lies floor $next, «${floorName(next, false)}». More danger, richer loot. The new floor starts from this crossing."), style = T.Body, textAlign = TextAlign.Center)
            val now = app.delve.domain.depthMult(c.state?.run?.maxDepth ?: 1); val then = app.delve.domain.depthMult(maxOf(next, c.state?.run?.maxDepth ?: 1))
            if (then > now) { Gap(6.dp); Text(w(ru, "Ставка глубины: ×${fmt(now)} → ×${fmt(then)} ко всем монетам и материалам, если поднимешься живым.", "Depth stake: ×${fmt(now)} → ×${fmt(then)} on all coins and materials if you come up alive."), style = T.Strong.copy(color = C.gold), textAlign = TextAlign.Center) }
            Gap()
            Btn(w(ru, "Спуститься", "Go down"), Modifier.fillMaxWidth(), BtnKind.WAX) { c.descendStairs(e.node, e.levels) }
            Gap(8.dp)
            Btn(w(ru, "Ещё побродить здесь", "Explore this floor more"), Modifier.fillMaxWidth(), BtnKind.LINE) { c.dismiss() }
        }
        is Ev.Well -> Sheet({ c.dismiss() }) {
            Pic(Art.room(RoomKind.UP.art), Modifier.size(110.dp), tint = androidx.compose.ui.graphics.ColorFilter.tint(C.ink))
            Text(w(ru, "Колодец наверх", "A well up"), style = T.Title)
            Gap(6.dp)
            Text(w(ru, "По скобам можно выбраться в город. Вся добыча из сумки вернётся в лагерь.", "Rungs lead up into town. Everything in your bag comes home to camp."), style = T.Body, textAlign = TextAlign.Center)
            c.state?.run?.let { r ->
                val m = app.delve.domain.depthMult(r.maxDepth)
                if (m > 1) { Gap(6.dp); Text(w(ru, "Ставка глубины ×${fmt(m)}: ${r.coins} монет станут ${(r.coins * m).toInt()}.", "Depth stake ×${fmt(m)}: ${r.coins} coins become ${(r.coins * m).toInt()}."), style = T.Strong.copy(color = C.gold), textAlign = TextAlign.Center) }
                Orders(c, ru)
            }
            Gap()
            Btn(w(ru, "Подняться в лагерь", "Climb up to camp"), Modifier.fillMaxWidth(), BtnKind.GOLD) { c.goUp() }
            Gap(8.dp)
            Btn(w(ru, "Остаться внизу", "Stay below"), Modifier.fillMaxWidth(), BtnKind.LINE) { c.dismiss() }
        }
        is Ev.Shop -> ShopSheet(c, e, ru)
        is Ev.Vault -> Sheet({ c.dismiss() }) {
            Pic(Art.room(RoomKind.VAULT.art), Modifier.size(110.dp), tint = androidx.compose.ui.graphics.ColorFilter.tint(C.ink))
            Text(RoomKind.VAULT.title(ru), style = T.Title)
            Gap(6.dp)
            val s = c.state
            val rogue = s?.cls == "rogue"
            val picks = s?.let { c.game.count(it, "lockpick") } ?: 0
            Text(if (rogue) w(ru, "Окованная дверь. Для вора – пара пустяков.", "An iron-bound door. Child's play for a rogue.")
                 else w(ru, "Окованная дверь на хитром замке. Нужна отмычка (у тебя: $picks).", "An iron-bound door with a tricky lock. You need a lock pick (you have $picks)."), style = T.Body, textAlign = TextAlign.Center)
            Gap()
            Btn(w(ru, "Вскрыть", "Pick the lock"), Modifier.fillMaxWidth(), BtnKind.WAX, enabled = rogue || picks > 0) { c.openVault(e.node) }
            Gap(8.dp)
            Btn(w(ru, "Оставить", "Leave it"), Modifier.fillMaxWidth(), BtnKind.LINE) { c.dismiss() }
        }
        is Ev.Shrine -> Sheet({ c.dismiss() }) {
            Pic(Art.room(RoomKind.SHRINE.art), Modifier.size(100.dp), tint = androidx.compose.ui.graphics.ColorFilter.tint(C.ink))
            Text(RoomKind.SHRINE.title(ru), style = T.Title)
            Gap(4.dp)
            Text(w(ru, "Свеча горит сама по себе. Выбери одно благословение до конца вылазки.", "The candle burns by itself. Choose one blessing for the rest of the expedition."), style = T.Body, textAlign = TextAlign.Center)
            Gap()
            for (i in e.offer) {
                val (name, mods) = BLESSINGS[i]
                Btn(name(ru), Modifier.fillMaxWidth(), BtnKind.LINE, sub = mods.lines(ru).joinToString(", ")) { c.bless(e.node, i) }
                Gap(8.dp)
            }
        }
        is Ev.LevelUp -> Sheet({ c.dismiss() }) {
            val s = c.state
            if (s != null) Pic(Art.hero(s.cls, s.female), Modifier.size(150.dp))
            Text(w(ru, "Уровень ${e.level}!", "Level ${e.level}!"), style = T.Display.copy(color = C.gold))
            Gap(4.dp)
            val skill = app.delve.domain.SKILL_LEVELS.contains(e.level)
            Text(w(ru, "Здоровье выросло. " + if (skill) "В лагере ждёт новое умение на выбор." else "", "Health grew. " + if (skill) "A new skill awaits at camp." else ""), style = T.Body, textAlign = TextAlign.Center)
            Gap()
            Btn(w(ru, "Вперёд", "Onwards"), Modifier.fillMaxWidth(), BtnKind.GOLD) { c.dismiss() }
        }
        is Ev.Ended -> EndedScreen(c, e, ru)
    }
}

fun fmt(m: Double) = "%.2f".format(m).trimEnd('0').trimEnd(',', '.')

/** The guild's orders taken for this expedition, with how far along each is. */
@Composable
fun Orders(c: AppContainer, ru: Boolean) {
    val s = c.state ?: return
    val run = s.run ?: return
    if (s.taken.isEmpty()) return
    Gap(10.dp)
    Text(w(ru, "ЗАКАЗЫ ГИЛЬДИИ", "GUILD ORDERS"), style = T.Eyebrow)
    for (o in s.taken) {
        val p = minOf(o.n, c.game.progress(run, o))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (p >= o.n) "✔" else "○", style = T.Strong.copy(color = if (p >= o.n) C.green else C.muted))
            HGap(8.dp)
            Column(Modifier.weight(1f)) {
                Text(o.text(ru), style = T.Strong)
                Bar(p.toFloat() / o.n, if (p >= o.n) C.green else C.gold, height = 5.dp)
            }
            HGap(8.dp)
            Text("$p/${o.n}", style = T.Small)
        }
    }
    Text(w(ru, "Награда – только если поднимешься живым.", "Paid only if you come up alive."), style = T.Small)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Loot(coins: Int, items: List<Item>, stuff: Map<String, Int>, ru: Boolean) {
    if (coins == 0 && items.isEmpty() && stuff.isEmpty()) return
    Gap(10.dp)
    Text(w(ru, "НАХОДКИ", "FOUND").uppercase(), style = T.Eyebrow)
    Gap(6.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (coins > 0) StuffChip(36, coins, label = w(ru, "Монеты", "Coins"))
        for ((k, n) in stuff) stuff(k)?.let { StuffChip(it.art, n, label = it.name(ru)) }
    }
    for (it in items) {
        Gap(8.dp)
        Row(Modifier.fillMaxWidth().paper(C.floor).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ItemTile(it, size = 52.dp)
            HGap(10.dp)
            Column(Modifier.weight(1f)) {
                Text(it.name(ru), style = T.Strong.copy(color = rarityColor(it.rarity)))
                Text(it.rarity.title(ru) + " · " + slotName(it.slot, ru).lowercase(), style = T.Small)
                ModIcons(it.mods, ru)
            }
        }
    }
}

@Composable
private fun NewsBody(e: Ev.News, ru: Boolean) {
    if (e.kind == RoomKind.EMPTY) Pic(Art.item(21), Modifier.size(96.dp))
    else Pic(Art.room(e.kind.art), Modifier.size(96.dp), tint = androidx.compose.ui.graphics.ColorFilter.tint(C.ink))
    Text(e.title(ru), style = T.Title, textAlign = TextAlign.Center)
    Gap(4.dp)
    Text(e.text(ru), style = T.Body, textAlign = TextAlign.Center)
    if (e.hpDelta != 0) { Gap(6.dp); Row(verticalAlignment = Alignment.CenterVertically) { Heart(Modifier.size(18.dp)); HGap(6.dp); Text((if (e.hpDelta > 0) "+" else "") + e.hpDelta, style = T.Strong.copy(color = if (e.hpDelta > 0) C.green else C.red)) } }
    Loot(e.coins, e.items, e.stuff, ru)
}

@Composable
private fun MeetSheet(c: AppContainer, e: Ev.Meet, ru: Boolean) {
    val s = c.state ?: return
    val st = c.game.stats(s)
    val f = e.foe
    val chase = maxOf(60, f.stamina * (100 - st.chase) / 100)
    // the odds, roughly, so the choice is an informed one
    val hit = ((21 - (f.def - st.atk)).coerceIn(1, 20)) / 20f
    val myHits = Math.ceil(f.hp / ((st.dmg + 2.5) * hit)).toInt()
    val theirHit = ((21 - (st.def - f.atk)).coerceIn(1, 20)) / 20f
    val expect = (myHits * theirHit * f.dmg).toInt()
    Sheet(null) {
        Text(if (f.boss) w(ru, "ХОЗЯИН ЭТАЖА", "MASTER OF THE FLOOR") else w(ru, "В КОМНАТЕ КТО-ТО ЕСТЬ", "SOMETHING IS IN THE ROOM"), style = T.Eyebrow.copy(color = C.red))
        Pic(Art.beast(f.beast.id), Modifier.size(180.dp))
        Text(f.beast.name(ru), style = T.Title, textAlign = TextAlign.Center)
        Text(f.beast.about(ru), style = T.Body.copy(color = C.ink2), textAlign = TextAlign.Center)
        Gap(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Stat(w(ru, "Сила", "Tier"), "${f.tier}"); Stat(w(ru, "Здоровье", "Health"), "${f.hp}"); Stat(w(ru, "Защита", "Armour"), "${f.def}"); Stat(w(ru, "Урон", "Damage"), "~${f.dmg}")
        }
        Gap()
        val hp = s.run?.hp ?: 0
        val trait = f.beast.trait()
        if (trait != app.delve.domain.Trait.NONE) {
            Text(trait.title(ru) + ": " + trait.hint(ru), style = T.Small.copy(color = C.ink2), textAlign = TextAlign.Center)
            Gap(8.dp)
        }
        Btn(w(ru, "Сразиться здесь", "Fight here"), Modifier.fillMaxWidth(), BtnKind.WAX,
            sub = w(ru, "бой по раундам · примерно −$expect из $hp", "a fight in rounds · about −$expect of $hp")) { c.fight(e.node, f) }
        Gap(8.dp)
        Btn(w(ru, "Быстрый бой", "Quick fight"), Modifier.fillMaxWidth(), BtnKind.LINE,
            sub = w(ru, "герой бьётся сам, сразу итог", "the hero fights alone, straight to the outcome")) { c.quickFight(e.node, f) }
        Gap(8.dp)
        Btn(w(ru, "Бежать и измотать", "Run and wear it out"), Modifier.fillMaxWidth(), BtnKind.INK,
            sub = w(ru, "пройди ${metres(chase, true)}, иногда будет кусать", "walk ${metres(chase, false)}, it bites now and then")) { c.flee(e.node, f) }
        if (!f.boss && f.beast.pet != null) {
            Gap(8.dp)
            val treats = c.game.count(s, "treat")
            Btn(w(ru, "Угостить лакомством", "Offer a treat"), Modifier.fillMaxWidth(), BtnKind.LINE, enabled = treats > 0,
                sub = w(ru, "может пойти с тобой · лакомств: $treats", "it may follow you · treats: $treats")) { c.tame(e.node, f) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = T.Number)
        Text(label, style = T.Small)
    }
}

@Composable
private fun FoughtSheet(c: AppContainer, e: Ev.Fought, ru: Boolean) {
    Sheet({ c.dismiss() }) {
        Pic(Art.beast(e.foe.beast.id), Modifier.size(130.dp), alpha = if (e.won) .55f else 1f)
        Text(when {
            e.won -> w(ru, "Победа!", "Victory!")
            e.fled -> w(ru, "Сбежал", "It fled")
            else -> w(ru, "Ты без сил…", "You're spent…")
        }, style = T.Title.copy(color = if (e.won) C.green else C.red))
        Gap(6.dp)
        Column(Modifier.fillMaxWidth().paper(C.floor).padding(10.dp)) {
            for (l in e.log.takeLast(8)) Text("· " + l(ru), style = T.Small.copy(color = C.ink2))
        }
        if (e.hpLost > 0) { Gap(6.dp); Row(verticalAlignment = Alignment.CenterVertically) { Heart(Modifier.size(18.dp)); HGap(6.dp); Text("−${e.hpLost}", style = T.Strong.copy(color = C.red)) } }
        Loot(e.coins, e.items, e.stuff, ru)
        Gap()
        Btn(w(ru, "Дальше", "Onwards"), Modifier.fillMaxWidth()) { c.dismiss() }
    }
}

@Composable
private fun ShopSheet(c: AppContainer, e: Ev.Shop, ru: Boolean) {
    val v by c.version.collectAsState()
    val s = c.state ?: return
    val run = s.run ?: return
    val sell = remember(v) { c.game.stats(s).sell }
    Sheet({ c.dismiss() }) {
        Pic(Art.beast("goblin"), Modifier.size(120.dp))
        Text(RoomKind.MERCHANT.title(ru), style = T.Title)
        Text(w(ru, "Гоблин раскладывает товар на тряпице. «Всё честно добыто. Почти».", "A goblin spreads his wares on a rag. \"All honestly got. Nearly.\""), style = T.Body, textAlign = TextAlign.Center)
        Gap(6.dp)
        Text(w(ru, "У тебя: ${coins(run.coins + s.coins, true)}", "You have: ${coins(run.coins + s.coins, false)}"), style = T.Strong.copy(color = C.gold))
        Heading(null, w(ru, "Снаряжение", "Gear"))
        for (it in e.stock) {
            val p = c.game.price(it) * 100 / (100 + sell)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).paper(C.floor).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ItemTile(it, size = 50.dp)
                HGap(10.dp)
                Column(Modifier.weight(1f)) {
                    Text(it.name(ru), style = T.Strong.copy(color = rarityColor(it.rarity)))
                    Text(it.mods.lines(ru).joinToString(", "), style = T.Small)
                }
                Btn("$p", kind = BtnKind.GOLD, enabled = run.coins + s.coins >= p) { c.buy(it) }
            }
        }
        Heading(null, w(ru, "Припасы", "Supplies"))
        for (id in e.goods) {
            val st = stuff(id) ?: continue
            val p = st.price * 100 / (100 + sell)
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Pic(Art.item(st.art), Modifier.size(40.dp))
                HGap(8.dp)
                Column(Modifier.weight(1f)) { Text(st.name(ru), style = T.Strong); Text(st.about(ru), style = T.Small) }
                Btn("$p", kind = BtnKind.GOLD, enabled = run.coins + s.coins >= p) { c.buyGood(id) }
            }
        }
        if (run.bag.isNotEmpty()) {
            Heading(null, w(ru, "Продать из сумки", "Sell from the bag"))
            for (it in run.bag.toList()) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                ItemTile(it, size = 44.dp)
                HGap(8.dp)
                Text(it.name(ru), style = T.Body, modifier = Modifier.weight(1f))
                Btn("+${it.worth * (100 + sell) / 100}", kind = BtnKind.LINE) { c.sellFromBag(it.uid) }
            }
        }
        Gap()
        Btn(w(ru, "Уйти", "Leave"), Modifier.fillMaxWidth()) { c.dismiss() }
    }
}

/** The bag on an expedition: what was found, the potions, the way home. */
@Composable
fun BagSheet(c: AppContainer, onClose: () -> Unit) {
    val v by c.version.collectAsState()
    val s = c.state ?: return
    val run = s.run ?: return
    val ru = ru()
    remember(v) { 0 }
    Sheet(onClose) {
        Text(w(ru, "Сумка", "Bag"), style = T.Title)
        Text(w(ru, "Всё это вернётся в лагерь, если подняться через колодец или по свитку. Если факел погаснет или силы кончатся – половина останется внизу.",
            "All this comes home if you climb a well or read a scroll. If the torch dies or you faint, half stays below."), style = T.Small, textAlign = TextAlign.Center)
        Loot(run.coins, run.bag, run.stuff.filterValues { it > 0 }, ru)
        if (run.coins == 0 && run.bag.isEmpty() && run.stuff.values.all { it == 0 }) { Gap(); Text(w(ru, "Пока пусто.", "Empty so far."), style = T.Note) }
        val m = app.delve.domain.depthMult(run.maxDepth)
        if (m > 1) { Gap(6.dp); Text(w(ru, "Ставка глубины ×${fmt(m)} – если подняться живым.", "Depth stake ×${fmt(m)} – if you come up alive."), style = T.Strong.copy(color = C.gold)) }
        Orders(c, ru)
        if (run.notes.isNotEmpty()) {
            Heading(null, w(ru, "Журнал вылазки", "Expedition log"))
            Column(Modifier.fillMaxWidth().paper(C.floor).padding(10.dp)) {
                for (n in run.notes.takeLast(12).reversed()) Text("· " + n(ru), style = T.Small.copy(color = C.ink2))
            }
        }
        Gap()
        val recall = c.game.count(s, "recall")
        Btn(w(ru, "Прочесть свиток возврата", "Read a recall scroll"), Modifier.fillMaxWidth(), BtnKind.GOLD, enabled = recall > 0, sub = w(ru, "свитков: $recall", "scrolls: $recall")) { onClose(); c.recall() }
        Gap(8.dp)
        Btn(w(ru, "Закрыть", "Close"), Modifier.fillMaxWidth(), BtnKind.LINE) { onClose() }
    }
}

@Composable
private fun EndedScreen(c: AppContainer, e: Ev.Ended, ru: Boolean) {
    val l = e.log
    Column(Modifier.fillMaxSize().background(C.paper).verticalScroll(rememberScrollState())) {
        Pic("banner_deep", Modifier.fillMaxWidth().heightIn(min = 160.dp))
        Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(when (l.how) {
                "dark" -> w(ru, "Факел погас", "The torch went out")
                "faint" -> w(ru, "Силы кончились", "You fainted")
                else -> w(ru, "Снова в лагере", "Back at camp")
            }, style = T.Display, textAlign = TextAlign.Center)
            Gap(6.dp)
            Text(when (l.how) {
                "dark" -> w(ru, "В темноте тебя вывел сквозняк. Половина добычи осталась внизу.", "A draught led you out in the dark. Half the loot stayed below.")
                "faint" -> w(ru, "Тебя вытащил добрый кобольд. Половина добычи осталась внизу.", "A kind kobold dragged you out. Half the loot stayed below.")
                else -> w(ru, "Вся добыча в лагере. Можно отдыхать и строить.", "All the loot is at camp. Time to rest and build.")
            }, style = T.Body, textAlign = TextAlign.Center)
            if (e.mult > 1) { Gap(8.dp); Text(w(ru, "Ставка глубины ×${fmt(e.mult)} сыграла!", "The depth stake ×${fmt(e.mult)} paid off!"), style = T.Strong.copy(color = C.gold)) }
            for (o in e.done) {
                Gap(8.dp)
                Row(Modifier.fillMaxWidth().paper(C.floor).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pic(Art.item(25), Modifier.size(40.dp)); HGap(8.dp)
                    Column(Modifier.weight(1f)) {
                        Text(w(ru, "Заказ выполнен", "Order done"), style = T.Eyebrow.copy(color = C.green))
                        Text(o.text(ru), style = T.Strong)
                        Text("+${o.coins} " + w(ru, "мон.", "c.") + " · " + (stuff(o.stuffId)?.name?.invoke(ru)?.lowercase() ?: "") + " ×${o.stuffN} · +${o.xp} " + w(ru, "опыта", "xp"), style = T.Small.copy(color = C.gold))
                    }
                }
            }
            Gap(16.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(w(ru, "глубина", "depth"), "${l.depth}")
                Stat(w(ru, "пройдено", "walked"), metres(l.metres, ru))
                Stat(w(ru, "залов", "halls"), "${l.rooms}")
            }
            Gap(12.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(w(ru, "побед", "beaten"), "${l.beaten}")
                Stat(w(ru, "монет", "coins"), "${l.coins}")
                Stat(w(ru, "опыта", "xp"), "${l.xp}")
            }
            if (l.trail.size >= 4) { Gap(18.dp); TrailMap(l, Modifier.fillMaxWidth().aspectRatio(1.4f)) }
            Gap(24.dp)
            Btn(w(ru, "В лагерь", "To camp"), Modifier.fillMaxWidth(), BtnKind.GOLD) { c.dismiss() }
        }
    }
}


package app.delve.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.delve.AppContainer
import app.delve.domain.BUILDINGS
import app.delve.domain.RECIPES
import app.delve.domain.STUFF
import app.delve.domain.buildCost
import app.delve.domain.building
import app.delve.domain.heroClass
import app.delve.domain.stuff
import app.delve.ui.theme.C
import app.delve.ui.theme.T
import kotlinx.coroutines.launch

/** The camp: the hero, the way down, the buildings that grow from the loot. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CampScreen(c: AppContainer, onSettings: () -> Unit) {
    val v by c.version.collectAsState()
    val s = c.state ?: return
    val ru = ru()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val stats = remember(v) { c.game.stats(s, null) }
    val cond = remember(v) { c.conditions() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box {
                Pic("banner_camp", Modifier.fillMaxWidth())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, C.paper))))
                Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(w(ru, "Лагерь", "Camp"), style = T.Display.copy(color = C.floor), modifier = Modifier.weight(1f))
                    HelpButton(c, "camp", dark = true)
                    HGap(8.dp)
                    Box(Modifier.size(42.dp).clip(CircleShape).background(C.ink.copy(alpha = .7f)).clickable { onSettings() }, contentAlignment = Alignment.Center) {
                        Text("⚙", style = T.Title.copy(color = C.paper))
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                // the hero
                Row(Modifier.fillMaxWidth().paper().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pic(Art.hero(s.cls, s.female), Modifier.size(84.dp))
                    HGap(10.dp)
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = T.Title)
                        Text(heroClass(s.cls).name(ru) + " · " + w(ru, "уровень ${s.level}", "level ${s.level}"), style = T.Small)
                        Gap(4.dp)
                        Bar(s.xp.toFloat() / c.game.need(s.level), C.gold)
                        Text(w(ru, "опыт ${s.xp} из ${c.game.need(s.level)}", "xp ${s.xp} of ${c.game.need(s.level)}"), style = T.Small)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${s.coins}", style = T.Number.copy(color = C.gold))
                        Text(w(ru, "монет", "coins"), style = T.Small)
                    }
                }
                if (c.game.skillChoices(s).isNotEmpty()) {
                    Gap(8.dp)
                    Text(w(ru, "✦ Новое умение ждёт выбора – загляни к герою.", "✦ A new skill awaits – see the hero page."), style = T.Note, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }

                // the way down
                Heading(w(ru, "под мостовой", "beneath the cobbles"), w(ru, "Спуск", "The descent"))
                Column(Modifier.fillMaxWidth().paper(C.floor).padding(14.dp)) {
                    Text(w(ru, "Подземелье повторяет улицы вокруг тебя. Каждый перекрёсток – зал, каждая улица – коридор. Выбери, на сколько хватит факела:",
                        "The dungeon follows the streets around you. Every crossing is a hall, every street a corridor. Choose how long the torch should burn:"), style = T.Body)
                    Gap(10.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (m in listOf(15, 30, 45, 60)) {
                            val on = s.torchPick == m
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) C.ink else C.paper).border(1.4.dp, C.ink, RoundedCornerShape(10.dp))
                                .clickable { s.torchPick = m; c.changed() }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$m", style = T.Number.copy(color = if (on) C.gold else C.ink))
                                Text(w(ru, "мин", "min"), style = T.Small.copy(color = if (on) C.paper else C.muted))
                            }
                        }
                    }
                    if (stats.torch > 0) Text(w(ru, "+${stats.torch} мин от снаряжения и лагеря", "+${stats.torch} min from gear and camp"), style = T.Small, modifier = Modifier.padding(top = 6.dp))
                    Gap(10.dp)
                    Conditions(cond, ru)
                    Gap(12.dp)
                    Btn(if (busy) w(ru, "Зажигаю факел…", "Lighting the torch…") else w(ru, "Зажечь факел и спуститься", "Light the torch and go down"), Modifier.fillMaxWidth(), BtnKind.WAX, enabled = !busy) {
                        busy = true; error = null
                        scope.launch {
                            error = runCatching { c.descend(s.torchPick) }.getOrElse { "net" }
                            busy = false
                        }
                    }
                    error?.let {
                        Gap(6.dp)
                        Text(when (it) {
                            "gps" -> w(ru, "Жду, когда телефон поймёт, где ты. Выйди под открытое небо.", "Waiting for the phone to find you. Step outside.")
                            "net" -> w(ru, "Не получилось скачать карту улиц. Нужен интернет.", "Couldn't download the street map. Internet is needed.")
                            "streets" -> w(ru, "Рядом нет улиц и дорожек. Подойди ближе к дороге.", "No streets or paths nearby. Get closer to a road.")
                            else -> w(ru, "Карта района ещё загружается.", "The district map is still loading.")
                        }, style = T.Small.copy(color = C.red))
                    }
                    Gap(6.dp)
                    Text(w(ru, "Рекорд глубины: ${s.deepest} · пройдено всего ${metres(s.metres.toInt(), true)}", "Deepest: ${s.deepest} · walked in all ${metres(s.metres.toInt(), false)}"), style = T.Small, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }

                Heading(w(ru, "лагерь растёт из добычи", "the camp grows from loot"), w(ru, "Постройки", "Buildings"))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
                    for (b in BUILDINGS) {
                        val lv = s.level(b.id)
                        Column(Modifier.weight(1f).paper(if (lv > 0) C.floor else C.paper).clickable { open = b.id }.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Pic(Art.camp(b.id), Modifier.fillMaxWidth().aspectRatio(1.25f), alpha = if (lv > 0) 1f else .45f)
                            Text(b.name(ru), style = T.Heading)
                            Pips(lv, b.max)
                            if (c.game.canBuild(s, b.id)) Text(w(ru, "можно улучшить", "can upgrade"), style = T.Small.copy(color = C.green))
                        }
                    }
                }

                Heading(w(ru, "в сундуке лагеря", "in the camp chest"), w(ru, "Припасы", "Supplies"))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (st in STUFF) { val n = s.stuff[st.id] ?: 0; if (n > 0) StuffChip(st.art, n, label = st.name(ru)) }
                }
                Gap(30.dp)
            }
        }
        open?.let { BuildingSheet(c, it, ru) { open = null } }
        PageHint(c, "camp")
    }
}

@Composable
private fun Conditions(cond: app.delve.domain.Conditions, ru: Boolean) {
    val lines = buildList {
        if (cond.night) add(w(ru, "🌙 Ночь: чудовища сильнее, добыча богаче.", "🌙 Night: creatures are stronger, loot richer."))
        if (cond.rain) add(w(ru, "🌧 Дождь: подземные реки поднялись, у воды больше жемчуга.", "🌧 Rain: the underground rivers rose; more pearls by the water."))
        if (cond.frost) add(w(ru, "❄ Мороз: в коридорах иней.", "❄ Frost: rime in the corridors."))
        if (isEmpty()) add(w(ru, "Сегодня под городом тихо.", "All quiet under the town today."))
    }
    for (l in lines) Text(l, style = T.Small.copy(color = C.ink2))
}

@Composable
private fun BuildingSheet(c: AppContainer, id: String, ru: Boolean, onClose: () -> Unit) {
    val v by c.version.collectAsState()
    val s = c.state ?: return
    val b = building(id)
    val lv = s.level(id)
    remember(v) { 0 }
    Sheet(onClose) {
        Pic(Art.camp(id), Modifier.size(150.dp))
        Text(b.name(ru), style = T.Title)
        Pips(lv, b.max)
        Gap(6.dp)
        Text(b.about(ru), style = T.Body, textAlign = TextAlign.Center)
        if (lv > 0 && (b.per.lines(ru).isNotEmpty())) Text(w(ru, "Сейчас: ", "Now: ") + b.per.times(lv.toDouble()).lines(ru).joinToString(", "), style = T.Small.copy(color = C.green))
        Gap()
        if (lv < b.max) {
            val (coins, mat) = buildCost(lv)
            val m = stuff(b.material)!!
            Row(verticalAlignment = Alignment.CenterVertically) {
                StuffChip(36, coins); HGap(8.dp); StuffChip(m.art, mat, label = m.name(ru))
            }
            Text(w(ru, "в лагере: ${s.coins} монет, ${m.name.ru.lowercase()} ×${s.stuff[b.material] ?: 0}", "at camp: ${s.coins} coins, ${m.name.en.lowercase()} ×${s.stuff[b.material] ?: 0}"), style = T.Small)
            Gap(8.dp)
            Btn(if (lv == 0) w(ru, "Построить", "Build") else w(ru, "Улучшить до ${lv + 1}", "Upgrade to ${lv + 1}"), Modifier.fillMaxWidth(), BtnKind.GOLD, enabled = c.game.canBuild(s, id)) { c.build(id) }
        } else Text(w(ru, "Построено полностью.", "Fully built."), style = T.Note)

        if (id == "forge" && lv > 0) {
            Heading(null, w(ru, "Заточка", "Upgrades"))
            Text(w(ru, "Каждая заточка: +15% к свойствам вещи. Предел – уровень кузницы.", "Each upgrade: +15% to a thing's numbers. The limit is the forge's level."), style = T.Small, textAlign = TextAlign.Center)
            for (it in s.gear.sortedByDescending { g -> s.equipped.values.contains(g.uid) }) {
                val (cc, iron) = c.game.upgradeCost(it)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemTile(it, size = 44.dp)
                    HGap(8.dp)
                    Column(Modifier.weight(1f)) { Text(it.name(ru), style = T.Strong.copy(color = rarityColor(it.rarity))); Text(w(ru, "$cc монет, железо ×$iron", "$cc coins, iron ×$iron"), style = T.Small) }
                    Btn("+1", kind = BtnKind.INK, enabled = c.game.canUpgrade(s, it)) { c.upgrade(it.uid) }
                }
            }
        }
        if (id == "alchemist" && lv > 0) {
            Heading(null, w(ru, "Рецепты", "Recipes"))
            for (r in RECIPES) {
                val out = stuff(r.out)!!
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pic(Art.item(out.art), Modifier.size(44.dp), alpha = if (lv >= r.minLevel) 1f else .4f)
                    HGap(8.dp)
                    Column(Modifier.weight(1f)) {
                        Text(out.name(ru) + " (×${s.stuff[r.out] ?: 0})", style = T.Strong)
                        val needs = r.needs.entries.joinToString(", ") { (k, n) -> stuff(k)!!.name(ru).lowercase() + " ×$n" } + if (r.coins > 0) ", ${r.coins} " + w(ru, "мон.", "c.") else ""
                        Text(if (lv >= r.minLevel) needs else w(ru, "нужен алхимик ${r.minLevel} ур.", "needs alchemist level ${r.minLevel}"), style = T.Small)
                    }
                    Btn(w(ru, "Сварить", "Brew"), kind = BtnKind.INK, enabled = c.game.canBrew(s, r)) { c.brew(r) }
                }
            }
        }
        Gap()
        Btn(w(ru, "Закрыть", "Close"), Modifier.fillMaxWidth(), BtnKind.LINE) { onClose() }
        Gap(4.dp)
        Box(Modifier.height(1.dp))
    }
}

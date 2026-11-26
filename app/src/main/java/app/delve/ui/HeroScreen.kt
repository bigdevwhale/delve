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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.delve.AppContainer
import app.delve.domain.SKILL_LEVELS
import app.delve.domain.Slot
import app.delve.domain.beast
import app.delve.domain.heroClass
import app.delve.ui.theme.C
import app.delve.ui.theme.T

/** The hero: numbers, what they wear, what they carry, what they know, who follows them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeroScreen(c: AppContainer) {
    val v by c.version.collectAsState()
    val s = c.state ?: return
    val ru = ru()
    val st = remember(v) { c.game.stats(s, null) }
    var pick by remember { mutableStateOf<Long?>(null) }
    var help by remember { mutableStateOf<Int?>(null) }
    val cls = heroClass(s.cls)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
                Pic(Art.hero(s.cls, s.female), Modifier.size(150.dp))
                Column(Modifier.weight(1f).padding(start = 8.dp, bottom = 10.dp)) {
                    Text(s.name, style = T.Display)
                    Text(cls.name(ru) + " · " + w(ru, "уровень ${s.level}", "level ${s.level}"), style = T.Body.copy(color = C.muted))
                    Text(cls.about(ru), style = T.Small)
                }
            }
            // the numbers: tap one to learn what it does
            val tiles = listOf(
                Triple(0, "${st.maxHp}", w(ru, "здоровье", "health")),
                Triple(1, "+${st.atk}", w(ru, "меткость", "to hit")),
                Triple(2, "${st.dmg + 1}–${st.dmg + 4}", w(ru, "урон", "damage")),
                Triple(3, "${st.def}", w(ru, "защита", "armour")),
                Triple(4, "${st.light} " + w(ru, "м", "m"), w(ru, "свет", "light")),
                Triple(5, "−${st.chase}%", w(ru, "погоня", "chase")),
                Triple(6, "${st.luck}", w(ru, "удача", "luck")),
                Triple(7, "+${st.loot}%", w(ru, "добыча", "loot")),
            )
            for (row in tiles.chunked(4)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((i, v, l) in row) StatTile(i, v, l, Modifier.weight(1f)) { help = i }
                }
            }
            Text(w(ru, "Нажми на число, чтобы узнать, на что оно влияет.", "Tap a number to see what it does."), style = T.Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            Heading(w(ru, "надето", "worn"), w(ru, "Снаряжение", "Gear"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (slot in Slot.entries) {
                    val it = s.equippedIn(slot)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ItemTile(it, size = 72.dp) { it?.let { x -> pick = x.uid } }
                        Text(slotName(slot, ru), style = T.Small)
                    }
                }
            }
            val bag = s.gear.filter { g -> s.equipped[g.slot] != g.uid }
            if (bag.isNotEmpty()) {
                Gap(14.dp)
                Text(w(ru, "В СУНДУКЕ", "IN THE CHEST"), style = T.Eyebrow)
                Gap(6.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (it in bag) ItemTile(it, size = 62.dp) { pick = it.uid }
                }
            }

            Heading(w(ru, "по одному на выбор", "one of each pair"), w(ru, "Умения", "Skills"))
            Text(w(ru, "На уровнях 2, 4, 6, 8 и 10 открывается пара умений. Выбери одно – второе закроется навсегда.", "At levels 2, 4, 6, 8 and 10 a pair of skills opens. Choose one: the other closes for good."),
                style = T.Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            cls.tiers.forEachIndexed { i, (a, b) ->
                val lv = SKILL_LEVELS[i]
                val open = s.level >= lv
                val chosen = listOf(a, b).firstOrNull { it.id in s.skills }
                val can = open && chosen == null
                if (i > 0) FadeRule()
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    SkillMedal(a, ru, chosen?.id == a.id, chosen != null, open, can, Modifier.weight(1f)) { c.learn(a.id) }
                    LevelKnot(lv, open, ru)
                    SkillMedal(b, ru, chosen?.id == b.id, chosen != null, open, can, Modifier.weight(1f)) { c.learn(b.id) }
                }
                if (can) Text(w(ru, "✦ Выбери одно из двух", "✦ Choose one of the two"), style = T.Note.copy(color = C.gold), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }

            val tamed = s.lore.filter { it.value.tamed }.keys.map { beast(it) }
            Heading(w(ru, "кто пойдёт с тобой", "who walks with you"), w(ru, "Спутники", "Companions"))
            if (tamed.isEmpty()) Text(w(ru, "Пока никого. Угости существо лакомством – некоторые идут следом.", "No one yet. Offer a creature a treat: some will follow."), style = T.Note, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (b in tamed) {
                    val on = s.companion == b.id
                    Column(Modifier.size(104.dp, 150.dp).paper(if (on) C.floor else C.paper, ink = if (on) C.gold else C.ink).clickable { c.pet(if (on) null else b.id) }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Pic(Art.beast(b.id), Modifier.size(70.dp))
                        Text(b.name(ru), style = T.Small.copy(color = C.ink), textAlign = TextAlign.Center, maxLines = 2)
                        b.pet?.let { ModIcons(it, ru, center = true, color = C.green) }
                    }
                }
            }
            Gap(30.dp)
        }
        HelpButton(c, "hero", Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp))
        pick?.let { uid -> s.gear.firstOrNull { it.uid == uid }?.let { ItemSheet(c, it, ru) { pick = null } } ?: run { pick = null } }
        help?.let { i ->
            val (name, text) = app.delve.domain.STAT_HELP[i]
            Sheet({ help = null }) {
                Pic(statIcon(i), Modifier.size(90.dp))
                Text(name(ru), style = T.Title)
                Gap(6.dp)
                Text(text(ru), style = T.Body, textAlign = TextAlign.Center)
                Gap()
                Btn(w(ru, "Понятно", "Got it"), Modifier.fillMaxWidth()) { help = null }
            }
        }
        PageHint(c, "hero")
    }
}

@Composable
private fun Num(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = T.Number); Text(label, style = T.Small) }
}

@Composable
private fun ItemSheet(c: AppContainer, it: app.delve.domain.Item, ru: Boolean, onClose: () -> Unit) {
    val s = c.state ?: return
    val worn = s.equipped[it.slot] == it.uid
    val other = s.equippedIn(it.slot)
    Sheet(onClose) {
        ItemTile(it, size = 120.dp)
        Gap(8.dp)
        Text(it.name(ru), style = T.Title.copy(color = rarityColor(it.rarity)), textAlign = TextAlign.Center)
        Text(it.rarity.title(ru) + " · " + slotName(it.slot, ru).lowercase() + " · " + w(ru, "ступень ${it.tier}", "tier ${it.tier}"), style = T.Small)
        Gap(8.dp)
        ModIcons(it.mods, ru, center = true)
        if (!worn && other != null) {
            Gap(8.dp)
            Text(w(ru, "Сейчас надето: ${other.name(true)}", "Worn now: ${other.name(false)}"), style = T.Small)
            ModLines(other.mods, ru, color = C.faint)
        }
        Gap()
        if (!worn) {
            Btn(w(ru, "Надеть", "Wear"), Modifier.fillMaxWidth(), BtnKind.INK) { c.equip(it.uid); onClose() }
            Gap(8.dp)
            Btn(w(ru, "Продать за ${it.worth}", "Sell for ${it.worth}"), Modifier.fillMaxWidth(), BtnKind.LINE) { c.sell(it.uid); onClose() }
        } else Text(w(ru, "Надето", "Worn"), style = T.Note)
        Gap(8.dp)
        Btn(w(ru, "Закрыть", "Close"), Modifier.fillMaxWidth(), BtnKind.LINE) { onClose() }
    }
}

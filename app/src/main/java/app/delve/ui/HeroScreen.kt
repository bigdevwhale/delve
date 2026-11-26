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
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Num(w(ru, "здоровье", "health"), "${st.maxHp}"); Num(w(ru, "меткость", "to hit"), "+${st.atk}"); Num(w(ru, "урон", "damage"), "${st.dmg}+d4"); Num(w(ru, "защита", "armour"), "${st.def}")
                }
                Gap(10.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Num(w(ru, "свет", "light"), "${st.light} м"); Num(w(ru, "погоня", "chase"), "−${st.chase}%"); Num(w(ru, "удача", "luck"), "${st.luck}"); Num(w(ru, "добыча", "loot"), "+${st.loot}%")
                }
            }

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
            cls.tiers.forEachIndexed { i, (a, b) ->
                val lv = SKILL_LEVELS[i]
                val open = s.level >= lv
                val chosen = listOf(a, b).firstOrNull { it.id in s.skills }
                Text(w(ru, "Уровень $lv", "Level $lv"), style = T.Eyebrow, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (k in listOf(a, b)) {
                        val mine = chosen?.id == k.id
                        val can = open && chosen == null
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (mine) C.ink else C.paper)
                            .border(1.4.dp, if (can) C.gold else C.ink.copy(alpha = if (mine) 1f else .35f), RoundedCornerShape(10.dp))
                            .clickable(enabled = can) { c.learn(k.id) }.padding(10.dp)) {
                            Text(k.name(ru), style = T.Strong.copy(color = if (mine) C.gold else if (open) C.ink else C.faint))
                            for (l in k.mods.lines(ru)) Text(l, style = T.Small.copy(color = if (mine) C.paper else C.muted))
                            if (can) Text(w(ru, "выбрать", "choose"), style = T.Small.copy(color = C.green))
                        }
                    }
                }
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
                        b.pet?.let { Text(it.lines(ru).joinToString(), style = T.Small.copy(color = C.green), textAlign = TextAlign.Center) }
                    }
                }
            }
            Gap(30.dp)
        }
        HelpButton(c, "hero", Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp))
        pick?.let { uid -> s.gear.firstOrNull { it.uid == uid }?.let { ItemSheet(c, it, ru) { pick = null } } ?: run { pick = null } }
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
        ModLines(it.mods, ru)
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

package app.delve.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.delve.AppContainer
import app.delve.ui.theme.C
import app.delve.ui.theme.T

/** A page's first-visit hint: a title and a few short lines. */
class Hint(val title: String, val lines: List<String>)

fun hint(key: String, ru: Boolean): Hint = when (key) {
    "camp" -> Hint(w(ru, "Лагерь", "The camp"), listOf(
        w(ru, "Здесь герой отдыхает между вылазками.", "Your hero rests here between expeditions."),
        w(ru, "Выбери, на сколько хватит факела, – это время прогулки.", "Choose how long the torch lasts: that's how long you'll walk."),
        w(ru, "Выйди на улицу и нажми «Зажечь факел»: подземелье строится из улиц вокруг тебя.", "Go outside and tap «Light the torch»: the dungeon is built from the streets around you."),
        w(ru, "Добыча идёт на постройки: кузница точит оружие, алхимик варит зелья, картограф учит видеть дальше.", "Loot goes into buildings: the forge sharpens weapons, the alchemist brews potions, the cartographer helps you see farther."),
        w(ru, "Победи хозяина пятого, десятого или пятнадцатого этажа – и следующий спуск можно начать со стоянки ниже него.", "Beat the master of the fifth, tenth or fifteenth floor and the next descent can start from a camp below it."),
    ))
    "dungeon" -> Hint(w(ru, "Под городом", "Under the town"), listOf(
        w(ru, "Иди по настоящим улицам. Каждый перекрёсток – зал: в нём сундук, ловушка, источник или чудовище.", "Walk the real streets. Every crossing is a hall: a chest, a trap, a spring or a creature."),
        w(ru, "Телефон можно убрать в карман: у зала, при погоне и у лестницы он завибрирует.", "You can pocket the phone: it buzzes at a hall, in a chase and at the stairs."),
        w(ru, "Лестница ведёт глубже, колодец – наверх в лагерь со всей добычей.", "Stairs lead deeper, a well takes you up to camp with all your loot."),
        w(ru, "Следи за факелом вверху: если он погаснет, половина добычи останется внизу.", "Watch the torch at the top: if it goes out, half the loot stays below."),
        w(ru, "Груды камней – обвалы. Пролезть можно, но факел закоптится.", "Heaps of stones are cave-ins. You can squeeze through, but the torch gets sooty."),
    ))
    "hero" -> Hint(w(ru, "Герой", "The hero"), listOf(
        w(ru, "Наверху – числа героя: от них зависят бой, погоня и свет фонаря.", "At the top are your numbers: they decide fights, chases and how far your light reaches."),
        w(ru, "Нажми на вещь, чтобы надеть её или продать.", "Tap a thing to wear it or sell it."),
        w(ru, "Каждые два уровня, до двадцатого, открывается пара умений – выбери одно из двух.", "Every two levels, up to the twentieth, a pair of skills opens: pick one of the two."),
        w(ru, "Приручённые существа становятся спутниками. Возьми одного с собой – он даст свою силу.", "Tamed creatures become companions. Take one along and it lends you its gift."),
    ))
    "beasts" -> Hint(w(ru, "Бестиарий", "The bestiary"), listOf(
        w(ru, "Здесь все, кого ты встретил под городом.", "Everyone you've met under the town is here."),
        w(ru, "Тени – те, кого ты ещё не видел. Над каждым разделом написано, где их искать.", "Shadows are the ones you haven't seen yet. Each section says where to look for them."),
        w(ru, "Нажми на карточку: там описание и что существо даёт как спутник.", "Tap a card for its story and what it gives as a companion."),
    ))
    "chronicle" -> Hint(w(ru, "Летопись", "The chronicle"), listOf(
        w(ru, "Каждая вылазка записывается сюда вместе с маршрутом.", "Every expedition is written down here with its route."),
        w(ru, "Красный пунктир – твой путь по городу: чёрная точка – начало, золотая – где вылазка кончилась.", "The red dotted line is your way through town: the black dot is the start, the gold one where you ended."),
    ))
    else -> Hint("", emptyList())
}

/**
 * The hint card over a page the first time it opens: light, with the page still in view behind it.
 * «Понятно» hides it for good; the «?» on the page brings it back.
 */
@Composable
fun PageHint(c: AppContainer, key: String, modifier: Modifier = Modifier) {
    val v by c.version.collectAsState()
    val s = c.state ?: return
    remember(v) { 0 }
    if (key in s.hints) return
    val ru = ru()
    val h = hint(key, ru)
    Box(Modifier.fillMaxSize().background(C.night.copy(alpha = .18f)).clickable(remember { MutableInteractionSource() }, null) {}) {
        Column(modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp).paper(C.floor).padding(16.dp)) {
            Text(w(ru, "ПОДСКАЗКА", "A HINT"), style = T.Eyebrow.copy(color = C.gold))
            Text(h.title, style = T.Title)
            Gap(6.dp)
            for (l in h.lines) Row(Modifier.padding(vertical = 3.dp)) {
                Text("✦  ", style = T.Body.copy(color = C.gold))
                Text(l, style = T.Body)
            }
            Gap(10.dp)
            Btn(w(ru, "Понятно", "Got it"), Modifier.fillMaxWidth()) { c.hintSeen(key) }
        }
    }
}

/** The «?» that shows a page's hint again. */
@Composable
fun HelpButton(c: AppContainer, key: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    Box(modifier.size(40.dp).clip(CircleShape).background(if (dark) C.ink.copy(alpha = .7f) else C.paper.copy(alpha = .95f))
        .border(1.4.dp, if (dark) C.paper.copy(alpha = .6f) else C.ink, CircleShape).clickable { c.hintAgain(key) }, contentAlignment = Alignment.Center) {
        Text("?", style = T.Title.copy(fontSize = T.Heading.fontSize, color = if (dark) C.paper else C.ink))
    }
}

@Suppress("unused") private val shape = RoundedCornerShape(12.dp)

/** A slim top row of a page with its «?» at the right, so the button never sits over the content. */
@Composable
fun PageTop(c: AppContainer, key: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) { HelpButton(c, key) }
}

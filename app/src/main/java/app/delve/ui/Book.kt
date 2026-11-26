package app.delve.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.delve.AppContainer
import app.delve.domain.BEASTS
import app.delve.domain.Beast
import app.delve.domain.Hab
import app.delve.domain.RunLog
import app.delve.ui.theme.C
import app.delve.ui.theme.T
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun habName(h: Hab, ru: Boolean) = when (h) {
    Hab.ANY -> w(ru, "Везде", "Anywhere"); Hab.CRYPT -> w(ru, "Склепы под церквями и кладбищами", "Crypts under churches and graveyards")
    Hab.GROTTO -> w(ru, "Гроты под парками", "Grottoes under parks"); Hab.RIVER -> w(ru, "Подземные реки у воды", "Underground rivers by the water")
    Hab.DEEP -> w(ru, "Глубже четвёртого этажа", "Below the fourth floor"); Hab.BOSS -> w(ru, "Тронные залы каждого пятого этажа", "Throne halls of every fifth floor")
}

/** The bestiary: every creature met is drawn in; the rest are ink silhouettes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BestiaryScreen(c: AppContainer) {
    val v by c.version.collectAsState()
    val s = c.state ?: return
    val ru = ru()
    var open by remember { mutableStateOf<Beast?>(null) }
    remember(v) { 0 }
    val known = BEASTS.count { (s.lore[it.id]?.met ?: 0) > 0 }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp)) {
            Heading(w(ru, "встречено $known из ${BEASTS.size}", "met $known of ${BEASTS.size}"), w(ru, "Бестиарий", "Bestiary"))
            for (h in Hab.entries) {
                val list = BEASTS.filter { it.hab == h }
                Text(habName(h, ru).uppercase(), style = T.Eyebrow, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                for (row in list.chunked(3)) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (b in row) {
                        val l = s.lore[b.id]
                        val met = (l?.met ?: 0) > 0
                        Column(Modifier.weight(1f).paper(if (met) C.floor else C.paper).clickable { open = b }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Pic(Art.beast(b.id), Modifier.fillMaxWidth().aspectRatio(1f), tint = if (met) null else Silhouette)
                            Text(if (met) b.name(ru) else "???", style = T.Small.copy(color = C.ink), textAlign = TextAlign.Center, maxLines = 2)
                            if (l?.tamed == true) Text(w(ru, "приручен", "tamed"), style = T.Small.copy(color = C.green))
                            else if ((l?.beaten ?: 0) > 0) Text("× ${l!!.beaten}", style = T.Small)
                        }
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            Gap(30.dp)
        }
        HelpButton(c, "beasts", Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp))
        PageHint(c, "beasts")
        open?.let { b ->
            val l = s.lore[b.id]
            val met = (l?.met ?: 0) > 0
            Sheet({ open = null }) {
                Pic(Art.beast(b.id), Modifier.size(210.dp), tint = if (met) null else Silhouette)
                Text(if (met) b.name(ru) else "???", style = T.Title)
                Text(habName(b.hab, ru) + " · " + w(ru, "с ${b.min}-го этажа", "from floor ${b.min}"), style = T.Small)
                Gap(8.dp)
                Text(if (met) b.about(ru) else w(ru, "Ты ещё не встречал это существо.", "You haven't met this creature yet."), style = T.Body, textAlign = TextAlign.Center)
                if (met) {
                    Gap(8.dp)
                    Text(w(ru, "Встреч: ${l!!.met} · побед: ${l.beaten}", "Met: ${l!!.met} · beaten: ${l.beaten}"), style = T.Small)
                    b.pet?.let { Text(w(ru, "Спутником даёт: ", "As a companion: ") + it.lines(ru).joinToString(), style = T.Small.copy(color = C.green), textAlign = TextAlign.Center) }
                    if (b.pet == null && b.hab != Hab.BOSS) Text(w(ru, "Не приручается.", "Can't be tamed."), style = T.Small)
                }
                Gap()
                Btn(w(ru, "Закрыть", "Close"), Modifier.fillMaxWidth(), BtnKind.LINE) { open = null }
            }
        }
    }
}

/** The chronicle: every expedition with the way it took, drawn small. */
@Composable
fun ChronicleScreen(c: AppContainer) {
    val v by c.version.collectAsState()
    val s = c.state ?: return
    val ru = ru()
    remember(v) { 0 }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp)) {
        Heading(w(ru, "все вылазки", "every expedition"), w(ru, "Летопись", "Chronicle"))
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${s.deepest}", style = T.Number); Text(w(ru, "глубже всего", "deepest"), style = T.Small) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(metres(s.metres.toInt(), ru), style = T.Number); Text(w(ru, "пройдено", "walked"), style = T.Small) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${s.logs.size}", style = T.Number); Text(w(ru, "вылазок", "expeditions"), style = T.Small) }
            }
        }
        if (s.logs.isEmpty()) { Gap(20.dp); Text(w(ru, "Здесь появятся записи о спусках.", "Your descents will be written here."), style = T.Note, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        val fmt = DateTimeFormatter.ofPattern("d MMMM", if (ru) Locale.forLanguageTag("ru") else Locale.ENGLISH)
        for (l in s.logs) {
            Gap(10.dp)
            Row(Modifier.fillMaxWidth().paper(C.floor).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TrailMap(l, Modifier.size(84.dp))
                HGap(12.dp)
                Column(Modifier.weight(1f)) {
                    Text(LocalDate.ofEpochDay(l.day).format(fmt), style = T.Eyebrow)
                    Text(w(ru, "До ${l.depth}-го этажа", "Down to floor ${l.depth}"), style = T.Heading)
                    Text(metres(l.metres, ru) + " · " + w(ru, "${l.minutes} мин · залов ${l.rooms} · побед ${l.beaten}", "${l.minutes} min · halls ${l.rooms} · beaten ${l.beaten}"), style = T.Small)
                    Text(when (l.how) {
                        "dark" -> w(ru, "факел погас", "the torch went out")
                        "faint" -> w(ru, "без сил", "fainted")
                        else -> w(ru, "вернулся с добычей: ${l.coins} мон., вещей ${l.items}", "came back with ${l.coins} coins, ${l.items} things")
                    }, style = T.Small.copy(color = if (l.how == "up") C.green else C.red))
                }
            }
        }
        Gap(30.dp)
    }
    HelpButton(c, "chronicle", Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp))
    PageHint(c, "chronicle")
    }
}

/** The way an expedition took, as a red pencil line on squared paper. */
@Composable
fun TrailMap(l: RunLog, modifier: Modifier) {
    Canvas(modifier) {
        val t = l.trail
        drawRect(C.paper)
        val step = size.width / 6
        for (i in 1 until 6) { drawLine(C.wash.copy(alpha = .4f), Offset(i * step, 0f), Offset(i * step, size.height), 1f); drawLine(C.wash.copy(alpha = .4f), Offset(0f, i * step), Offset(size.width, i * step), 1f) }
        if (t.size < 4) return@Canvas
        val lats = t.filterIndexed { i, _ -> i % 2 == 0 }; val lons = t.filterIndexed { i, _ -> i % 2 == 1 }
        val k = Math.cos(Math.toRadians(lats.average()))
        val minX = lons.min() * k; val maxX = lons.max() * k; val minY = lats.min(); val maxY = lats.max()
        val span = maxOf(maxX - minX, maxY - minY).coerceAtLeast(1e-6)
        val pad = size.width * .12f
        fun px(i: Int) = Offset((pad + ((lons[i] * k - minX) / span) * (size.width - 2 * pad)).toFloat(), (pad + ((maxY - lats[i]) / span) * (size.height - 2 * pad)).toFloat())
        val p = Path()
        for (i in lats.indices) { val o = px(i); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
        drawPath(p, C.red, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
        drawCircle(C.ink, 3.dp.toPx(), px(0))
        drawCircle(C.gold, 3.dp.toPx(), px(lats.size - 1))
        drawRect(C.ink, style = Stroke(1.dp.toPx()))
    }
}

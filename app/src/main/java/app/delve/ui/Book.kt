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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
            PageTop(c, "beasts")
            Heading(w(ru, "встречено $known из ${BEASTS.size}", "met $known of ${BEASTS.size}"), w(ru, "Бестиарий", "Bestiary"))
            for (h in Hab.entries) {
                val list = BEASTS.filter { it.hab == h }
                Text(habName(h, ru).uppercase(), style = T.Eyebrow, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                for (row in list.chunked(3)) Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (b in row) {
                        val l = s.lore[b.id]
                        val met = (l?.met ?: 0) > 0
                        Column(Modifier.weight(1f).fillMaxHeight().paper(if (met) C.floor else C.paper).clickable { open = b }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Pic(Art.beast(b.id), Modifier.fillMaxWidth().aspectRatio(1f), tint = if (met) null else Silhouette)
                            Text(if (met) b.name(ru) else "???", style = T.Small.copy(color = C.ink), textAlign = TextAlign.Center, minLines = 2, maxLines = 2)
                            Text(when {
                                l?.tamed == true -> w(ru, "приручен", "tamed")
                                (l?.beaten ?: 0) > 0 -> "× ${l!!.beaten}"
                                else -> " "
                            }, style = T.Small.copy(color = if (l?.tamed == true) C.green else C.muted), maxLines = 1)
                        }
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            Gap(30.dp)
        }
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

/** The chronicle: a banner, the hero's records, then every expedition with the way it took. */
@Composable
fun ChronicleScreen(c: AppContainer) {
    val v by c.version.collectAsState()
    val s = c.state ?: return
    val ru = ru()
    remember(v) { 0 }
    val beaten = s.lore.values.sumOf { it.beaten }
    val met = s.lore.values.count { it.met > 0 }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box {
            Pic("banner_chronicle", Modifier.fillMaxWidth())
            Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(C.night.copy(alpha = .7f), C.night.copy(alpha = .2f), androidx.compose.ui.graphics.Color.Transparent, C.paper))))
            Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w(ru, "ЗАПИСИ ИСКАТЕЛЯ", "THE SEEKER'S NOTES"), style = T.Eyebrow.copy(color = C.flame))
                    Text(w(ru, "Летопись", "Chronicle"), style = T.Display.copy(color = C.floor, shadow = androidx.compose.ui.graphics.Shadow(C.night, androidx.compose.ui.geometry.Offset(0f, 3f), 10f)))
                }
                HelpButton(c, "chronicle", dark = true)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            // the hero's totals as icon tiles
            val tiles = listOf(
                Triple("room_0", "${s.deepest}", w(ru, "глубже всего", "deepest")),
                Triple(statIcon(5), metres(s.metres.toInt(), ru), w(ru, "пройдено", "walked")),
                Triple(statIcon(8), "${s.logs.size}", w(ru, "вылазок", "expeditions")),
                Triple(statIcon(2), "$beaten", w(ru, "побед", "beaten")),
                Triple(Art.beast("rat"), "$met/${app.delve.domain.BEASTS.size}", w(ru, "существ", "creatures")),
                Triple("camp_depth", "${s.camps.size}", w(ru, "стоянок", "deep camps")),
            )
            for (row in tiles.chunked(3)) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((icon, value, label) in row) Column(Modifier.weight(1f).paper(C.floor).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (icon.startsWith("room_")) Pic(icon, Modifier.size(34.dp), tint = androidx.compose.ui.graphics.ColorFilter.tint(C.ink)) else Pic(icon, Modifier.size(34.dp))
                    Text(value, style = T.Heading, maxLines = 1)
                    Text(label, style = T.Small, maxLines = 1)
                }
            }
            // records of single expeditions
            if (s.logs.isNotEmpty()) {
                Heading(w(ru, "лучшие вылазки", "best expeditions"), w(ru, "Рекорды", "Records"))
                val far = s.logs.maxBy { it.metres }; val deep = s.logs.maxBy { it.depth }; val fights = s.logs.maxBy { it.beaten }; val rich = s.logs.maxBy { it.coins }
                for ((icon, title, value) in listOf(
                    Triple("room_0", w(ru, "Глубже всего за раз", "Deepest in one go"), w(ru, "${deep.depth}-й этаж", "floor ${deep.depth}")),
                    Triple(statIcon(5), w(ru, "Самая длинная прогулка", "Longest walk"), metres(far.metres, ru)),
                    Triple(statIcon(2), w(ru, "Больше всего побед", "Most beaten"), "${fights.beaten}"),
                    Triple(Art.item(36), w(ru, "Самая богатая добыча", "Richest haul"), coins(rich.coins, ru)),
                )) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).paper(C.paper).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (icon.startsWith("room_")) Pic(icon, Modifier.size(30.dp), tint = androidx.compose.ui.graphics.ColorFilter.tint(C.ink)) else Pic(icon, Modifier.size(30.dp))
                        HGap(10.dp)
                        Text(title, style = T.Body, modifier = Modifier.weight(1f))
                        Text(value, style = T.Strong.copy(color = C.gold))
                    }
                }
            }
            Heading(w(ru, "все спуски", "every descent"), w(ru, "Вылазки", "Expeditions"))
            if (s.logs.isEmpty()) Text(w(ru, "Здесь появятся записи о спусках: маршрут, глубина и добыча.", "Your descents will be written here: the route, the depth and the loot."), style = T.Note, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            val fmt = DateTimeFormatter.ofPattern("d MMMM", if (ru) Locale.forLanguageTag("ru") else Locale.ENGLISH)
            for (l in s.logs) {
                Gap(10.dp)
                val (col, mark) = when (l.how) { "dark" -> C.muted to w(ru, "факел погас", "torch out"); "faint" -> C.red to w(ru, "без сил", "fainted"); "leave" -> C.blue to w(ru, "вышел раньше", "left early"); else -> C.green to w(ru, "с добычей", "with loot") }
                Row(Modifier.fillMaxWidth().paper(C.floor).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TrailMap(l, Modifier.size(92.dp))
                        // the depth reached, as a wax seal in the corner of the map
                        Box(Modifier.align(Alignment.BottomEnd).padding(2.dp).size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(col).border(2.dp, C.floor, androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center) { Text("${l.depth}", style = T.Heading.copy(color = C.floor)) }
                    }
                    HGap(12.dp)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(LocalDate.ofEpochDay(l.day).format(fmt), style = T.Eyebrow, modifier = Modifier.weight(1f))
                            Text(mark, style = T.Small.copy(color = col))
                        }
                        Text(w(ru, "До ${l.depth}-го этажа", "Down to floor ${l.depth}"), style = T.Heading)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            for ((icon, t) in listOf(statIcon(5) to metres(l.metres, ru), statIcon(2) to "${l.beaten}", Art.item(36) to "${l.coins}")) {
                                Pic(icon, Modifier.size(18.dp)); HGap(3.dp); Text(t, style = T.Small.copy(color = C.ink2)); HGap(10.dp)
                            }
                        }
                        Text(w(ru, "${l.minutes} мин · залов ${l.rooms} · вещей ${l.items} · +${l.xp} опыта", "${l.minutes} min · ${l.rooms} halls · ${l.items} things · +${l.xp} xp"), style = T.Small)
                    }
                }
            }
            Gap(30.dp)
        }
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

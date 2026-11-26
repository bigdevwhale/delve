package app.delve.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.delve.AppContainer
import app.delve.BuildConfig
import app.delve.R
import app.delve.domain.Floor
import app.delve.domain.RoomKind
import app.delve.domain.Run
import app.delve.domain.beast
import app.delve.ui.art.MapInk
import app.delve.ui.art.MapPainter
import app.delve.ui.art.MapScene
import app.delve.ui.theme.C
import app.delve.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The name of a depth band. */
fun floorName(depth: Int, ru: Boolean) = when {
    depth <= 2 -> w(ru, "Подвалы", "Cellars")
    depth <= 4 -> w(ru, "Старые стоки", "Old drains")
    depth <= 6 -> w(ru, "Склепы", "Crypts")
    depth <= 9 -> w(ru, "Грибные пещеры", "Mushroom caves")
    depth <= 14 -> w(ru, "Глубинные залы", "Deep halls")
    depth <= 19 -> w(ru, "Хрустальные жилы", "Crystal veins")
    else -> w(ru, "Корни города", "Roots of the town")
}

/** Bitmaps of the map symbols (room_N), tinted when drawn. */
class Icons(private val c: android.content.Context) {
    private val cache = HashMap<String, Bitmap?>()
    fun get(name: String): Bitmap? = cache.getOrPut(name) {
        val id = Art.id(c, name)
        if (id == 0) null else BitmapFactory.decodeResource(c.resources, id, BitmapFactory.Options().apply { inScaled = false })
    }
}

/** The expedition: the map of the floor with the torch, and what's going on around the hero. */
@Composable
fun DungeonScreen(c: AppContainer, onBag: () -> Unit) {
    val v by c.version.collectAsState()
    val s = c.state ?: return
    val run = s.run ?: return
    val f = c.floor() ?: return
    val ru = ru()
    val here by c.here.collectAsState()
    val ctx = LocalContext.current
    val icons = remember { Icons(ctx) }
    val density = LocalDensity.current.density
    val ink = remember {
        MapInk(density, ResourcesCompat.getFont(ctx, R.font.caveat)) { i -> icons.get(Art.room(i)) }
    }
    val serif = remember { ResourcesCompat.getFont(ctx, R.font.alegreya) }
    val hero = remember(s.cls, s.female) { icons.get(Art.hero(s.cls, s.female)) }
    val key = "${run.seed}/${run.depth}/${run.floorStart}/${run.explored.size}/${run.seen.size}/${run.visited.size}/${run.resolved.size}/${run.mapped}/$ru"
    // the old drawing stays until the new one is ready, so the map never blinks
    var scene by remember { mutableStateOf<MapScene?>(null) }
    LaunchedEffect(key) { scene = withContext(Dispatchers.Default) { MapPainter.build(f, run, ru) } }
    val stats = remember(v) { c.game.stats(s) }

    // where the hero is on the map: the GPS fix, else the room they last entered
    val d = c.district
    val pos = here?.let { g -> d?.let { Offset(it.x(g.lon), it.y(g.lat)) } } ?: f.g.nodes[maxOf(run.at, run.floorStart)].let { Offset(it.x, it.y) }
    var center by remember { mutableStateOf(pos) }
    var zoom by remember { mutableFloatStateOf(2.7f) }
    var follow by remember { mutableStateOf(true) }
    if (follow) center = pos
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); c.checkEnd(); delay(1000) } }

    Box(Modifier.fillMaxSize().background(C.paper)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wpx = constraints.maxWidth.toFloat(); val hpx = constraints.maxHeight.toFloat()
            val zpx = zoom * density
            val m = Matrix().apply { setTranslate(-center.x, -center.y); postScale(zpx, zpx); postTranslate(wpx / 2, hpx / 2) }
            val gestures = Modifier
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, z, _ ->
                        val zz = if (z.isFinite()) z.coerceIn(.8f, 1.25f) else 1f
                        if (pan.getDistance() > 2f || kotlin.math.abs(zz - 1f) > .01f) follow = false
                        zoom = (zoom * zz).coerceIn(1.2f, 7f)
                        center = Offset(center.x - pan.x / (zoom * density), center.y - pan.y / (zoom * density))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { p ->
                        // demo and debug builds: a long press walks the hero there
                        if (BuildConfig.DEMO || BuildConfig.DEBUG) {
                            val mx = center.x + (p.x - size.width / 2) / (zoom * density)
                            val my = center.y + (p.y - size.height / 2) / (zoom * density)
                            c.walkTo(mx, my)
                        }
                    })
                }
            Canvas(Modifier.fillMaxSize().then(gestures)) {
                drawIntoCanvas { cv ->
                    val nc = cv.nativeCanvas
                    ink.paper(nc, m, zpx, size.width, size.height, center.x, center.y)
                    scene?.let { ink.scene(nc, it, m) }
                    ink.steps(nc, run.steps, m)
                }
            }
            // the torch, the hero and whatever is chasing them: the only layer that moves by itself
            val t = rememberInfiniteTransition(label = "torch")
            val flicker by t.animateFloat(.97f, 1.03f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "f")
            val left = c.game.torchLeft(run, now)
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { cv ->
                    val nc = cv.nativeCanvas
                    val pt = floatArrayOf(pos.x, pos.y); m.mapPoints(pt)
                    val r = (stats.light / 1.6f) * zpx
                    ink.torch(nc, pt[0], pt[1], r, size.width, size.height, flicker, left < 5)
                    run.chase?.let { ch ->
                        // the chaser trails behind along the walked way, closer as it tires… or further
                        val back = (14 + 30 * (ch.left / ch.total)).toFloat() / 1.6f
                        val p = behind(run.steps, pos, back)
                        val q = floatArrayOf(p.x, p.y); m.mapPoints(q)
                        ink.portrait(nc, icons.get(Art.beast(ch.beast)), q[0], q[1], 17 * density, C.red.toArgb(), .9f)
                    }
                    ink.portrait(nc, hero, pt[0], pt[1], 21 * density, C.gold.toArgb())
                    // the stairs and wells already found, when off screen: a chip at the edge pointing the way
                    val goals = (listOf(f.down) + f.ups).filter { it in run.seen || run.mapped }
                    for (g in goals) {
                        val nd = f.g.nodes[g]
                        val q = floatArrayOf(nd.x, nd.y); m.mapPoints(q)
                        val inset = 34 * density
                        val top = 200 * density; val bottom = size.height - 330 * density
                        if (q[0] in inset..size.width - inset && q[1] in top..bottom) continue
                        val dx = q[0] - pt[0]; val dy = q[1] - pt[1]
                        var k = 1f
                        if (dx != 0f) k = minOf(k, ((if (dx > 0) size.width - inset else inset) - pt[0]) / dx)
                        if (dy != 0f) k = minOf(k, ((if (dy > 0) bottom else top) - pt[1]) / dy)
                        val ex = pt[0] + dx * k; val ey = pt[1] + dy * k
                        val metres = (kotlin.math.hypot(nd.x - pos.x, nd.y - pos.y) * 1.6f).toInt()
                        val art = if (g == f.down) (if (f.rooms[g]?.kind == RoomKind.BOSS && g !in run.resolved) RoomKind.BOSS.art else RoomKind.DOWN.art) else RoomKind.UP.art
                        ink.pointer(nc, icons.get(Art.room(art)), ex, ey, kotlin.math.atan2(dy, dx), metres, ru)
                    }
                    ink.compass(nc, size.width - 44 * density, 150 * density, 26 * density, serif)
                }
            }
        }

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Hud(run.depth, left = c.game.torchLeft(run, now), total = run.torchMin.toDouble(), hp = run.hp, maxHp = stats.maxHp, ru = ru)
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Text("${w(ru, "Этаж", "Floor")} ${run.depth} · ${floorName(run.depth, ru)}" + if (run.maxDepth > 1) "  ·  ×" + "%.2f".format(app.delve.domain.depthMult(run.maxDepth)).trimEnd('0').trimEnd(',', '.') else "", style = T.Heading.copy(fontSize = T.Heading.fontSize),
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(C.paper.copy(alpha = .86f)).border(1.dp, C.ink.copy(alpha = .5f), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 3.dp))
            }
        }

        ToastLayer(c, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 112.dp, start = 12.dp, end = 12.dp))
        HelpButton(c, "dungeon", Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 198.dp, end = 24.dp))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Canvas(Modifier.size(130.dp, 34.dp)) { drawIntoCanvas { ink.scale(it.nativeCanvas, 8 * density, size.height - 10 * density, zoom * density, ru) } }
                Box(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RoundBtn("+") { zoom = (zoom * 1.4f).coerceAtMost(7f) }
                    Gap(6.dp)
                    RoundBtn("−") { zoom = (zoom / 1.4f).coerceAtLeast(1.2f) }
                    if (!follow) {
                        Gap(6.dp)
                        Box(Modifier.size(46.dp).clip(CircleShape).background(C.ink).border(2.dp, C.gold, CircleShape).clickable { follow = true }, contentAlignment = Alignment.Center) {
                            Pic(Art.hero(s.cls, s.female), Modifier.size(38.dp).clip(CircleShape))
                        }
                    }
                }
            }
            Gap(8.dp)
            val ch = run.chase
            if (ch != null) ChaseCard(c, ch.beast, ch.left, ch.total, ru)
            else RoomCard(c, f, run, ru, v)
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolBtn(w(ru, "Сумка", "Bag"), 28, run.bag.size + run.stuff.values.sum(), Modifier.weight(1f)) { onBag() }
                ToolBtn(w(ru, "Зелье", "Potion"), 24, c.game.count(s, "potion"), Modifier.weight(1f), enabled = run.hp < stats.maxHp && c.game.count(s, "potion") > 0) { c.drink() }
                ToolBtn(w(ru, "Карта", "Map"), 26, c.game.count(s, "mapscroll"), Modifier.weight(1f), enabled = !run.mapped && c.game.count(s, "mapscroll") > 0) { c.readMap() }
            }
        }
        PageHint(c, "dungeon")
    }
}

/** A point [dist] map pixels back along the walked way from [pos]. */
private fun behind(steps: List<Float>, pos: Offset, dist: Float): Offset {
    var left = dist; var px = pos.x; var py = pos.y
    var i = steps.size - 2
    while (i >= 0) {
        val x = steps[i]; val y = steps[i + 1]
        val l = kotlin.math.hypot(x - px, y - py)
        if (l >= left && l > 0) return Offset(px + (x - px) * left / l, py + (y - py) * left / l)
        left -= l; px = x; py = y; i -= 2
    }
    return Offset(pos.x - dist * .7f, pos.y + dist * .7f)
}

@Composable
private fun Hud(depth: Int, left: Double, total: Double, hp: Int, maxHp: Int, ru: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.size(54.dp).clip(CircleShape).background(C.ink).border(2.dp, C.gold, CircleShape), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("$depth", style = T.Number.copy(color = C.paper, fontSize = T.Number.fontSize))
            Text(w(ru, "ЭТАЖ", "FLOOR"), style = T.Eyebrow.copy(color = C.gold, fontSize = T.Eyebrow.fontSize * .8f))
        }
        Row(Modifier.weight(1f).paper(C.paper.copy(alpha = .94f)).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Pic(Art.item(20), Modifier.size(30.dp))
            HGap(6.dp)
            Column(Modifier.weight(1f)) {
                val mm = maxOf(0, left.toInt()); val ss = maxOf(0, ((left - left.toInt()) * 60).toInt())
                Text("%d:%02d".format(mm, ss), style = T.Strong.copy(color = if (left < 5) C.red else C.ink))
                Bar((left / total).toFloat(), if (left < 5) C.red else C.flame, height = 6.dp)
            }
            HGap(10.dp)
            Heart(Modifier.size(22.dp))
            HGap(5.dp)
            Column(Modifier.width(64.dp)) {
                Text("$hp/$maxHp", style = T.Strong)
                Bar(hp.toFloat() / maxHp, C.red, height = 6.dp)
            }
        }
    }
}

@Composable
fun Heart(modifier: Modifier) {
    Box(modifier.drawBehind {
        val wd = size.width; val h = size.height
        val p = Path().apply {
            moveTo(wd / 2, h * .9f)
            cubicTo(wd * -.1f, h * .45f, wd * .15f, h * -.05f, wd / 2, h * .28f)
            cubicTo(wd * .85f, h * -.05f, wd * 1.1f, h * .45f, wd / 2, h * .9f)
            close()
        }
        drawPath(p, C.red)
        drawPath(p, C.ink, style = androidx.compose.ui.graphics.drawscope.Stroke(1.3.dp.toPx()))
    })
}

@Composable
private fun RoundBtn(label: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(C.paper.copy(alpha = .95f)).border(1.4.dp, C.ink, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = T.Title.copy(fontSize = T.Heading.fontSize))
    }
}

@Composable
private fun ToolBtn(label: String, art: Int, n: Int, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(modifier.paper(C.paper.copy(alpha = .96f)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Pic(Art.item(art), Modifier.size(30.dp), alpha = if (enabled) 1f else .45f)
        HGap(4.dp)
        Column {
            Text(label, style = T.Strong.copy(fontSize = T.Small.fontSize, color = if (enabled) C.ink else C.faint), maxLines = 1)
            Text("×$n", style = T.Small)
        }
    }
}

@Composable
private fun ChaseCard(c: AppContainer, id: String, left: Double, total: Double, ru: Boolean) {
    val b = beast(id)
    Row(Modifier.fillMaxWidth().paper(C.floor, ink = C.red).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Pic(Art.beast(id), Modifier.size(64.dp))
        HGap(10.dp)
        Column(Modifier.weight(1f)) {
            Text(w(ru, "ПОГОНЯ", "CHASE"), style = T.Eyebrow.copy(color = C.red))
            Text(b.name(ru), style = T.Heading)
            Text(w(ru, "Пройди ещё ${metres(left.toInt(), true)}, и ${if (b.name.ru.endsWith("а") || b.name.ru.endsWith("я")) "она" else "он"} отстанет", "Walk ${metres(left.toInt(), false)} more and it gives up"), style = T.Small)
            Gap(4.dp)
            Bar((1 - left / total).toFloat(), C.green)
        }
        if (c.state?.let { c.game.count(it, "smoke") } ?: 0 > 0) {
            HGap(8.dp)
            Column(Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, C.ink, RoundedCornerShape(8.dp)).clickable { c.smoke() }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Pic(Art.item(27), Modifier.size(30.dp))
                Text(w(ru, "Дым", "Smoke"), style = T.Small)
            }
        }
    }
}

@Composable
private fun RoomCard(c: AppContainer, f: Floor, run: Run, ru: Boolean, @Suppress("UNUSED_PARAMETER") version: Int) {
    val room = f.rooms[run.at]
    val kind = room?.kind ?: RoomKind.EMPTY
    val done = run.at in run.resolved
    val (title, hint) = when (kind) {
        RoomKind.START -> (if (run.depth == 1) w(ru, "Люк в мостовой", "The hatch") else w(ru, "Подножие лестницы", "Foot of the stairs")) to
            if (run.depth == 1 && run.rooms > 1) w(ru, "Нажми, чтобы подняться через люк в лагерь со всей добычей.", "Tap to climb out through the hatch to camp with all your loot.") else w(ru, "Иди по улицам: каждый перекрёсток – новый зал. Лестница вниз где-то в ${metres(f.dist[f.down]?.toInt()?.div(100)?.times(100) ?: 400, true)} пути.",
                "Walk the streets: every crossing is a new hall. The stairs down are about ${metres(f.dist[f.down]?.toInt()?.div(100)?.times(100) ?: 400, false)} away.")
        RoomKind.DOWN -> kind.title(ru) to w(ru, "Нажми, чтобы спуститься глубже.", "Tap to go deeper.")
        RoomKind.UP -> kind.title(ru) to w(ru, "Нажми, чтобы подняться в лагерь со всей добычей.", "Tap to climb up to camp with all your loot.")
        RoomKind.MERCHANT -> kind.title(ru) to w(ru, "Нажми, чтобы поторговать.", "Tap to trade.")
        RoomKind.BOSS -> kind.title(ru) to if (done) w(ru, "Хозяин повержен. Лестница вниз свободна.", "The master is beaten. The stairs are free.") else w(ru, "Здесь кто-то очень большой.", "Something very big lives here.")
        else -> kind.title(ru) to if (room?.above?.isNotBlank() == true) w(ru, "Над тобой – «${room.above}».", "Above you: «${room.above}».") else w(ru, "Ищи следующий зал. Факел горит.", "Find the next hall. The torch burns.")
    }
    val tappable = kind in setOf(RoomKind.DOWN, RoomKind.UP, RoomKind.MERCHANT) || (kind == RoomKind.BOSS && done) || (kind == RoomKind.START && run.depth == 1)
    Row(Modifier.fillMaxWidth().paper(C.floor).clickable(enabled = tappable) { c.reopen() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(C.paper).border(1.dp, C.ink.copy(alpha = .4f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Pic(Art.room(kind.art), Modifier.size(40.dp), tint = androidx.compose.ui.graphics.ColorFilter.tint(C.ink))
        }
        HGap(12.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = T.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hint, style = T.Small, textAlign = TextAlign.Start)
        }
    }
}

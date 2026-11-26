package app.delve.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import app.delve.AppContainer
import app.delve.BuildConfig
import app.delve.domain.CLASSES
import app.delve.domain.hash
import app.delve.ui.theme.C
import app.delve.ui.theme.T
import app.delve.work.Tracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Dark = Color(0xFF15110D)
private val Warm = Color(0xFFF2A53A)

/**
 * The way in: five scenes in the dark by torchlight. The town you know, the town beneath, the torch and the
 * chase, who goes down, and where the camp stands. Lines appear one by one; a tap on «Дальше» moves on.
 */
@Composable
fun Onboarding(c: AppContainer) {
    val ru = ru()
    var page by rememberSaveable { mutableIntStateOf(if (c.state == null) 0 else 4) }
    var name by rememberSaveable { mutableStateOf("") }
    var cls by rememberSaveable { mutableStateOf("ranger") }
    var female by rememberSaveable { mutableStateOf(false) }
    val t = rememberInfiniteTransition(label = "torch")
    val flicker by t.animateFloat(.9f, 1.08f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "f")

    Box(Modifier.fillMaxSize().background(Dark).drawBehind {
        // torchlight from below and a soft vignette
        drawRect(Brush.radialGradient(listOf(Warm.copy(alpha = .22f * flicker), Color.Transparent), center = Offset(size.width / 2, size.height * .62f), radius = size.width * .95f * flicker))
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f)), center = Offset(size.width / 2, size.height / 2), radius = size.height * .75f))
    }) {
        AnimatedContent(page, transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(400)) }, label = "page") { p ->
            Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Gap(56.dp)
                when (p) {
                    0 -> SceneTown(ru) { page = 1 }
                    1 -> SceneMap(ru) { page = 2 }
                    2 -> SceneTorch(ru, flicker) { page = 3 }
                    3 -> SceneHero(ru, cls, female, name, { cls = it }, { female = it }, { name = it }) { c.create(name.trim(), cls, female); page = 4 }
                    else -> SceneCamp(c, ru)
                }
                Gap(24.dp)
            }
        }
        // progress and the way out of the story
        Row(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                for (i in 0..4) Box(Modifier.size(if (i == page) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (i <= page) Warm else C.paper.copy(alpha = .25f)))
            }
            if (page < 3) Text(w(ru, "Пропустить", "Skip"), style = T.Small.copy(color = C.paper.copy(alpha = .6f)), modifier = Modifier.clickable { page = 3 }.padding(8.dp))
            else if (page == 0) Unit
        }
    }
}

/** Lines that fade in one after another. */
@Composable
private fun Lines(lines: List<String>, start: Long = 300, step: Long = 900, style: androidx.compose.ui.text.TextStyle = T.Title.copy(color = C.paper)) {
    for ((i, l) in lines.withIndex()) {
        val a = remember { Animatable(0f) }
        LaunchedEffect(Unit) { delay(start + i * step); a.animateTo(1f, tween(900)) }
        Text(l, style = style, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).alpha(a.value))
    }
}

@Composable
private fun ColumnScope.Next(text: String, delayMs: Long, onClick: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs); a.animateTo(1f, tween(700)) }
    Gap(28.dp)
    Box(Modifier.alpha(a.value).fillMaxWidth()) { Btn(text, Modifier.fillMaxWidth(), BtnKind.WAX) { onClick() } }
}

@Composable
private fun ColumnScope.SceneTown(ru: Boolean, next: () -> Unit) {
    Pic("banner_deep", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Warm.copy(alpha = .4f), RoundedCornerShape(14.dp)))
    Gap(26.dp)
    Lines(listOf(
        w(ru, "Ты думал, что знаешь свой район.", "You thought you knew your streets."),
        w(ru, "Каждую улицу. Каждый поворот.", "Every street. Every corner."),
        w(ru, "Но под мостовой лежит другой город.", "But under the cobbles lies another town."),
    ))
    Lines(listOf(w(ru, "Тёмный, старый и очень терпеливый.", "Dark, old and very patient.")), start = 3200, style = T.Note.copy(color = Warm))
    Next(w(ru, "Спуститься", "Go down"), 3800, next)
    Gap(14.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn("Русский", kind = if (ru) BtnKind.GOLD else BtnKind.GHOST) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru")) }
        Btn("English", kind = if (!ru) BtnKind.GOLD else BtnKind.GHOST) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
    }
}

/** Streets drawn in pencil turn into inked corridors with rock around them. */
@Composable
private fun ColumnScope.SceneMap(ru: Boolean, next: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(400); p.animateTo(1f, tween(4200, easing = LinearEasing)) }
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(C.paper).border(1.dp, Warm.copy(alpha = .5f), RoundedCornerShape(14.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // the blue survey grid
            val step = w / 12
            for (i in 1 until 12) { drawLine(C.wash.copy(alpha = .35f), Offset(i * step, 0f), Offset(i * step, h), 1f); drawLine(C.wash.copy(alpha = .35f), Offset(0f, i * step), Offset(w, i * step), 1f) }
            val streets = listOf(
                listOf(.05f to .3f, .35f to .28f, .62f to .33f, .95f to .3f),
                listOf(.05f to .72f, .4f to .7f, .7f to .74f, .95f to .7f),
                listOf(.35f to .05f, .35f to .28f, .4f to .7f, .38f to .95f),
                listOf(.62f to .05f, .62f to .33f, .7f to .74f, .74f to .95f),
                listOf(.62f to .33f, .82f to .5f, .7f to .74f),
            )
            val paths = streets.map { s -> Path().apply { s.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * w, y * h) else lineTo(x * w, y * h) } } }
            val phase1 = (p.value / .35f).coerceIn(0f, 1f)          // pencil streets
            val phase2 = ((p.value - .35f) / .45f).coerceIn(0f, 1f) // ink corridors
            val phase3 = ((p.value - .75f) / .25f).coerceIn(0f, 1f) // rooms and hatching
            val hw = w * .028f
            for (path in paths) {
                val m = PathMeasure(); m.setPath(path, false)
                val part = Path(); m.getSegment(0f, m.length * phase1, part, true)
                drawPath(part, C.ink2.copy(alpha = .45f * (1 - phase2)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            // hatching around the corridors
            if (phase2 > 0) {
                var k = 0
                for (path in paths) {
                    val m = PathMeasure(); m.setPath(path, false)
                    var d = 0f
                    while (d < m.length * phase2) {
                        val pos = m.getPosition(d)
                        for (side in listOf(-1, 1)) {
                            val tan = m.getTangent(d)
                            val nx = -tan.y * side; val ny = tan.x * side
                            val off = hw * (1.9f + hash(k, side + 5).toFloat() * 1.3f)
                            val cx = pos.x + nx * off; val cy = pos.y + ny * off
                            val a = (hash(k, side).toFloat() * PI).toFloat()
                            val l = w * .016f; val gap = w * .008f
                            for (j in -1..1) {
                                val ox = -sin(a) * j * gap; val oy = cos(a) * j * gap
                                drawLine(C.ink.copy(alpha = .75f), Offset(cx + ox - cos(a) * l, cy + oy - sin(a) * l), Offset(cx + ox + cos(a) * l, cy + oy + sin(a) * l), w * .004f, cap = StrokeCap.Round)
                            }
                        }
                        d += w * .022f; k++
                    }
                }
                for (path in paths) {
                    val m = PathMeasure(); m.setPath(path, false)
                    val part = Path(); m.getSegment(0f, m.length * phase2, part, true)
                    drawPath(part, C.ink, style = Stroke(hw * 2 + 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                for (path in paths) {
                    val m = PathMeasure(); m.setPath(path, false)
                    val part = Path(); m.getSegment(0f, m.length * phase2, part, true)
                    drawPath(part, C.floor, style = Stroke(hw * 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            // a hall at every crossing
            if (phase3 > 0) {
                for ((x, y) in listOf(.35f to .28f, .62f to .33f, .4f to .7f, .7f to .74f, .82f to .5f)) {
                    val r = hw * 2.2f * phase3
                    drawRect(C.ink, Offset(x * w - r - 3, y * h - r - 3), Size(2 * r + 6, 2 * r + 6))
                    drawRect(C.floor, Offset(x * w - r, y * h - r), Size(2 * r, 2 * r))
                }
            }
        }
    }
    Gap(22.dp)
    Lines(listOf(
        w(ru, "Улица – коридор.", "A street is a corridor."),
        w(ru, "Перекрёсток – зал.", "A crossing is a hall."),
        w(ru, "Квартал – толща камня.", "A block is solid rock."),
    ), start = 600, step = 1300)
    Lines(listOf(w(ru, "Церковь наверху – склеп внизу. Фонтан – источник. Парк – грибной грот.", "A church above is a crypt below. A fountain, a spring. A park, a mushroom grotto.")),
        start = 4600, style = T.Body.copy(color = C.paper.copy(alpha = .8f)))
    Next(w(ru, "Дальше", "Next"), 5200, next)
}

@Composable
private fun ColumnScope.SceneTorch(ru: Boolean, flicker: Float, next: () -> Unit) {
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(200.dp).drawBehind { drawCircle(Brush.radialGradient(listOf(Warm.copy(alpha = .55f), Color.Transparent)), radius = size.minDimension / 2 * flicker) })
        Pic(Art.item(20), Modifier.size(150.dp))
    }
    Lines(listOf(
        w(ru, "Факел горит, пока ты идёшь.", "The torch burns while you walk."),
        w(ru, "Выбери, на сколько его хватит, – это и есть прогулка.", "Choose how long it lasts: that's your walk."),
    ))
    Gap(18.dp)
    // a rat on the trail of the hero, footprints between them
    val run = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(2000); run.animateTo(1f, tween(2600)) }
    Row(Modifier.fillMaxWidth().alpha(run.value.coerceAtMost(1f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Pic(Art.beast("rat"), Modifier.size(64.dp))
        for (i in 0 until 5) Text("·", style = T.Title.copy(color = Warm.copy(alpha = (run.value * 5 - i).coerceIn(0f, 1f))), modifier = Modifier.padding(horizontal = 4.dp))
        Pic("hero_ranger_m", Modifier.size(72.dp))
    }
    Lines(listOf(
        w(ru, "С чудовищем не обязательно драться – от него можно уйти.", "You don't outfight a chaser – you outwalk it."),
        w(ru, "Пройди пару сотен метров, и оно отстанет.", "Walk a couple of hundred metres and it gives up."),
    ), start = 3000, step = 1100, style = T.Heading.copy(color = C.paper))
    Lines(listOf(w(ru, "Телефон можно убрать в карман: он сам вздрогнет, когда что-то случится.", "You can pocket the phone: it buzzes when something happens.")),
        start = 5400, style = T.Body.copy(color = C.paper.copy(alpha = .8f)))
    Next(w(ru, "Дальше", "Next"), 5800, next)
}

@Composable
private fun ColumnScope.SceneHero(ru: Boolean, cls: String, female: Boolean, name: String, onCls: (String) -> Unit, onFemale: (Boolean) -> Unit, onName: (String) -> Unit, done: () -> Unit) {
    Text(w(ru, "Кто спустится?", "Who goes down?"), style = T.Display.copy(color = C.paper))
    Gap(8.dp)
    val k = CLASSES.first { it.id == cls }
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(240.dp).drawBehind { drawCircle(Brush.radialGradient(listOf(Warm.copy(alpha = .35f), Color.Transparent))) })
        Pic(Art.hero(cls, female), Modifier.size(220.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val i = CLASSES.indexOf(k)
        Text("‹", style = T.Display.copy(color = Warm), modifier = Modifier.clickable { onCls(CLASSES[(i + CLASSES.size - 1) % CLASSES.size].id) }.padding(horizontal = 18.dp))
        Column(Modifier.width(200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(k.name(ru), style = T.Title.copy(color = Warm))
            Text(k.about(ru), style = T.Small.copy(color = C.paper.copy(alpha = .8f)), textAlign = TextAlign.Center)
        }
        Text("›", style = T.Display.copy(color = Warm), modifier = Modifier.clickable { onCls(CLASSES[(i + 1) % CLASSES.size].id) }.padding(horizontal = 18.dp))
    }
    Text(k.mods.lines(ru).joinToString(" · "), style = T.Small.copy(color = C.green.copy(alpha = .9f)), textAlign = TextAlign.Center)
    Gap(12.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn(w(ru, "Он", "He"), kind = if (!female) BtnKind.GOLD else BtnKind.GHOST) { onFemale(false) }
        Btn(w(ru, "Она", "She"), kind = if (female) BtnKind.GOLD else BtnKind.GHOST) { onFemale(true) }
    }
    Gap(12.dp)
    OutlinedTextField(name, { onName(it.take(20)) }, Modifier.fillMaxWidth(), label = { Text(w(ru, "Как зовут героя?", "The hero's name?"), style = T.Body.copy(color = Color.Unspecified)) }, singleLine = true,
        textStyle = T.Body.copy(color = C.paper, fontSize = T.Heading.fontSize),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Warm, unfocusedBorderColor = C.paper.copy(alpha = .4f), focusedLabelColor = Warm,
            unfocusedLabelColor = C.paper.copy(alpha = .6f), focusedTextColor = C.paper, unfocusedTextColor = C.paper, cursorColor = Warm))
    Gap(18.dp)
    Btn(w(ru, "Взять факел", "Take the torch"), Modifier.fillMaxWidth(), BtnKind.WAX, enabled = name.isNotBlank()) { done() }
}

@Composable
private fun ColumnScope.SceneCamp(c: AppContainer, ru: Boolean) {
    val scope = rememberCoroutineScope()
    val here by c.here.collectAsState()
    val stage by c.stage.collectAsState()
    var err by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Tracker.of(ctx).start() }
    Pic("banner_camp", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Warm.copy(alpha = .4f), RoundedCornerShape(14.dp)))
    Gap(22.dp)
    Lines(listOf(
        w(ru, "Каждому спуску нужен лагерь.", "Every descent needs a camp."),
        w(ru, "Разбей его там, где стоишь.", "Pitch it where you stand."),
    ))
    Lines(listOf(w(ru, "Отсюда скачается карта улиц на пару километров вокруг – из неё вырастет подземелье. Чтобы вести героя, нужна геолокация, в том числе когда телефон в кармане.",
        "The street map for a couple of kilometres around downloads from here: the dungeon grows from it. To guide the hero the app needs your location, also with the phone in a pocket.")),
        start = 1800, style = T.Body.copy(color = C.paper.copy(alpha = .8f)))
    Gap(18.dp)
    if (!BuildConfig.DEMO) {
        Btn(w(ru, "Разрешить геолокацию", "Allow location"), Modifier.fillMaxWidth(), BtnKind.GHOST) {
            val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
            perms.launch(list.toTypedArray())
        }
        Gap(8.dp)
    }
    Text(if (BuildConfig.DEMO) w(ru, "Демо: лагерь встанет на Петроградской стороне, а героя водят тапами по карте.", "Demo: the camp stands on Petrogradka in St Petersburg; tap the map to walk.")
        else if (here == null) w(ru, "Жду, когда телефон поймёт, где ты…", "Waiting for the phone to find you…") else w(ru, "Место найдено.", "Found you."),
        style = T.Small.copy(color = Warm), textAlign = TextAlign.Center)
    Gap(10.dp)
    Btn(if (stage == "download") w(ru, "Скачиваю улицы…", "Downloading the streets…") else w(ru, "Разбить лагерь здесь", "Make camp here"), Modifier.fillMaxWidth(), BtnKind.WAX,
        enabled = (here != null || BuildConfig.DEMO) && stage.isEmpty()) {
        err = false
        scope.launch { runCatching { c.setCamp(here ?: app.delve.data.DemoSeed.HOME) }.onFailure { err = true } }
    }
    if (err) Text(w(ru, "Не получилось скачать карту. Нужен интернет – попробуй ещё раз.", "Couldn't download the map. Internet is needed: try again."), style = T.Small.copy(color = C.red))
    Box(Modifier.height(1.dp))
}

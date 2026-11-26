package app.delve.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import app.delve.AppContainer
import app.delve.container
import app.delve.domain.CLASSES
import app.delve.domain.Ev
import app.delve.work.Tracker
import app.delve.ui.theme.C
import app.delve.ui.theme.T
import kotlinx.coroutines.launch

/** Which page of the camp book is open. */
enum class Tab { CAMP, HERO, BEASTS, CHRONICLE }

@Composable
fun Root() {
    val c = LocalContext.current.container
    val v by c.version.collectAsState()
    val evs by c.events.collectAsState()
    val s = c.state
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(C.paper)) {
        when {
            s == null || s.home == null -> Onboarding(c)
            c.graph == null -> Loading(c)
            s.run != null && evs.firstOrNull() !is Ev.Ended -> {
                var bag by remember { mutableStateOf(false) }
                DungeonScreen(c) { bag = true }
                if (bag) BagSheet(c) { bag = false }
            }
            else -> Main(c)
        }
        EventLayer(c)
    }
    remember(v) { 0 }
}

@Composable
private fun Main(c: AppContainer) {
    var tab by rememberSaveable { mutableStateOf(Tab.CAMP) }
    var settings by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.CAMP -> CampScreen(c) { settings = true }
                Tab.HERO -> HeroScreen(c)
                Tab.BEASTS -> BestiaryScreen(c)
                Tab.CHRONICLE -> ChronicleScreen(c)
            }
        }
        NavBar(tab) { tab = it }
    }
    if (settings) SettingsSheet(c) { settings = false }
}

@Composable
private fun NavBar(tab: Tab, onTab: (Tab) -> Unit) {
    val ru = ru()
    Row(Modifier.fillMaxWidth().background(C.ink).navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp)) {
        for ((t, label, pic) in listOf(
            Triple(Tab.CAMP, w(ru, "Лагерь", "Camp"), Art.camp("forge")),
            Triple(Tab.HERO, w(ru, "Герой", "Hero"), Art.item(12)),
            Triple(Tab.BEASTS, w(ru, "Бестиарий", "Bestiary"), Art.beast("rat")),
            Triple(Tab.CHRONICLE, w(ru, "Летопись", "Chronicle"), Art.item(41)),
        )) {
            val on = t == tab
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) C.paper.copy(alpha = .14f) else C.ink).clickable { onTab(t) }.padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Pic(pic, Modifier.size(34.dp), alpha = if (on) 1f else .6f)
                Text(label, style = T.Small.copy(color = if (on) C.gold else C.paper.copy(alpha = .7f)))
            }
        }
    }
}

@Composable
fun Loading(c: AppContainer) {
    val stage by c.stage.collectAsState()
    val ru = ru()
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Pic(Art.room(0), Modifier.size(120.dp), tint = ColorFilter.tint(C.ink))
        Gap()
        Text(when (stage) {
            "download" -> w(ru, "Скачиваю улицы из OpenStreetMap…", "Downloading the streets from OpenStreetMap…")
            else -> w(ru, "Черчу коридоры под улицами…", "Drawing the corridors under the streets…")
        }, style = T.Heading, textAlign = TextAlign.Center)
        Text(w(ru, "Это займёт несколько секунд.", "This takes a few seconds."), style = T.Small)
    }
}

/** Three pages: what this is, who the hero is, where the camp stands. */
@Composable
fun Onboarding(c: AppContainer) {
    val ru = ru()
    var page by rememberSaveable { mutableIntStateOf(if (c.state == null) 0 else 2) }
    var name by rememberSaveable { mutableStateOf("") }
    var cls by rememberSaveable { mutableStateOf("warrior") }
    var female by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val here by c.here.collectAsState()
    val stage by c.stage.collectAsState()
    var err by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Tracker.of(ctx).start() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        when (page) {
            0 -> {
                Pic("banner_deep", Modifier.fillMaxWidth())
                Column(Modifier.padding(20.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(w(ru, "Подземелье", "Delve"), style = T.Display)
                    Gap(8.dp)
                    Text(w(ru, "Под каждым городом лежит подземелье, и его план совпадает с улицами наверху. Улица – коридор, перекрёсток – зал, квартал – толща камня.",
                        "Under every town lies a dungeon whose plan matches the streets above. A street is a corridor, a crossing a hall, a block solid rock."), style = T.Body, textAlign = TextAlign.Center)
                    Gap(10.dp)
                    for ((a, b) in listOf(
                        w(ru, "Факел", "The torch") to w(ru, "это время прогулки. Выбери 15–60 минут и иди.", "is your walk's time. Pick 15–60 minutes and go."),
                        w(ru, "Погоня", "A chase") to w(ru, "чудовище отстанет, если пройти от него пару сотен метров.", "a creature gives up after you walk a couple of hundred metres."),
                        w(ru, "Город", "The town") to w(ru, "церковь наверху – склеп внизу, фонтан – источник, парк – грибной грот.", "a church above is a crypt below, a fountain a spring, a park a grotto."),
                        w(ru, "Лагерь", "The camp") to w(ru, "добыча строит кузницу, алхимика и картографа.", "loot builds a forge, an alchemist, a cartographer."),
                    )) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text("✦ ", style = T.Strong.copy(color = C.gold)); Text("$a ", style = T.Strong); Text(b, style = T.Body, modifier = Modifier.weight(1f)) }
                    }
                    Gap(18.dp)
                    Btn(w(ru, "Собрать героя", "Make a hero"), Modifier.fillMaxWidth(), BtnKind.WAX) { page = 1 }
                    Gap(8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Русский", kind = if (ru) BtnKind.INK else BtnKind.LINE) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru")) }
                        Btn("English", kind = if (!ru) BtnKind.INK else BtnKind.LINE) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
                    }
                }
            }
            1 -> Column(Modifier.statusBarsPadding().padding(20.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Heading(w(ru, "кто спускается", "who goes down"), w(ru, "Герой", "The hero"))
                Pic(app.delve.ui.Art.hero(cls, female), Modifier.size(190.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(w(ru, "Он", "He"), kind = if (!female) BtnKind.INK else BtnKind.LINE) { female = false }
                    Btn(w(ru, "Она", "She"), kind = if (female) BtnKind.INK else BtnKind.LINE) { female = true }
                }
                Gap(10.dp)
                for (k in CLASSES) {
                    val on = k.id == cls
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(if (on) C.floor else C.paper)
                        .border(if (on) 2.dp else 1.dp, if (on) C.gold else C.ink.copy(alpha = .4f), RoundedCornerShape(10.dp)).clickable { cls = k.id }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Pic(Art.hero(k.id, female), Modifier.size(52.dp))
                        HGap(10.dp)
                        Column(Modifier.weight(1f)) { Text(k.name(ru), style = T.Heading); Text(k.about(ru), style = T.Small); Text(k.mods.lines(ru).joinToString(", "), style = T.Small.copy(color = C.green)) }
                    }
                }
                Gap(10.dp)
                OutlinedTextField(name, { name = it.take(20) }, Modifier.fillMaxWidth(), label = { Text(w(ru, "Имя", "Name")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.ink, unfocusedBorderColor = C.ink.copy(alpha = .5f), focusedLabelColor = C.ink))
                Gap(14.dp)
                Btn(w(ru, "Дальше", "Next"), Modifier.fillMaxWidth(), BtnKind.WAX, enabled = name.isNotBlank()) {
                    c.create(name.trim(), cls, female); page = 2
                }
            }
            else -> Column(Modifier.statusBarsPadding().padding(20.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Pic("banner_camp", Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
                Heading(w(ru, "где стоит лагерь", "where the camp stands"), w(ru, "Лагерь", "The camp"))
                Text(w(ru, "Лагерь – там, где ты сейчас. Отсюда скачается карта улиц на пару километров вокруг: из неё и строится подземелье. Чтобы вести героя, приложению нужно знать, где ты, в том числе когда телефон в кармане.",
                    "The camp is where you are now. The street map for a couple of kilometres around downloads from here: the dungeon is built from it. To guide the hero the app needs your location, also with the phone in a pocket."),
                    style = T.Body, textAlign = TextAlign.Center)
                Gap()
                Btn(w(ru, "Разрешить геолокацию", "Allow location"), Modifier.fillMaxWidth(), BtnKind.LINE) {
                    val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
                    perms.launch(list.toTypedArray())
                }
                Gap(8.dp)
                Text(if (here == null) w(ru, "Жду координаты…", "Waiting for a fix…") else w(ru, "Координаты есть.", "Got a fix."), style = T.Small)
                Gap(8.dp)
                Btn(if (stage == "download") w(ru, "Скачиваю улицы…", "Downloading streets…") else w(ru, "Разбить лагерь здесь", "Make camp here"), Modifier.fillMaxWidth(), BtnKind.WAX,
                    enabled = here != null && stage.isEmpty()) {
                    err = false
                    scope.launch { runCatching { c.setCamp(here!!) }.onFailure { err = true } }
                }
                if (err) Text(w(ru, "Не получилось скачать карту. Нужен интернет – попробуй ещё раз.", "Couldn't download the map. Internet is needed: try again."), style = T.Small.copy(color = C.red))
            }
        }
    }
}

@Composable
private fun SettingsSheet(c: AppContainer, onClose: () -> Unit) {
    val ru = ru()
    val s = c.state ?: return
    val scope = rememberCoroutineScope()
    val here by c.here.collectAsState()
    var vib by remember { mutableStateOf(s.vibrate) }
    var moving by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(c.backup().toByteArray()) } }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: return@rememberLauncherForActivityResult
        scope.launch { runCatching { c.restore(text) } }
    }
    Sheet(onClose) {
        Text(w(ru, "Настройки", "Settings"), style = T.Title)
        Gap()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w(ru, "Вибрация в кармане", "Buzz in the pocket"), style = T.Strong)
                Text(w(ru, "Короткая – зал, тройная – погоня, длинная – лестница.", "Short: a hall. Triple: a chase. Long: the stairs."), style = T.Small)
            }
            Switch(vib, { vib = it; c.setVibrate(it) }, colors = SwitchDefaults.colors(checkedTrackColor = C.ink, checkedThumbColor = C.gold))
        }
        Gap()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Btn("Русский", Modifier.weight(1f), if (ru) BtnKind.INK else BtnKind.LINE) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru")) }
            Btn("English", Modifier.weight(1f), if (!ru) BtnKind.INK else BtnKind.LINE) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
        }
        Gap()
        Btn(if (moving) w(ru, "Переношу…", "Moving…") else w(ru, "Перенести лагерь сюда", "Move the camp here"), Modifier.fillMaxWidth(), BtnKind.LINE, enabled = here != null && !moving,
            sub = w(ru, "карта улиц скачается заново", "the street map downloads again")) {
            moving = true
            scope.launch { runCatching { c.setCamp(here!!) }; moving = false }
        }
        Gap(8.dp)
        Btn(w(ru, "Сохранить прогресс в файл", "Save progress to a file"), Modifier.fillMaxWidth(), BtnKind.LINE) { save.launch("delve-${s.name}.json") }
        Gap(8.dp)
        Btn(w(ru, "Загрузить из файла", "Load from a file"), Modifier.fillMaxWidth(), BtnKind.LINE) { load.launch(arrayOf("application/json", "text/plain", "*/*")) }
        Gap()
        Text(w(ru, "Карта © участники OpenStreetMap. Погода – MET Norway.", "Map © OpenStreetMap contributors. Weather: MET Norway."), style = T.Small, textAlign = TextAlign.Center)
        Gap(8.dp)
        Btn(w(ru, "Закрыть", "Close"), Modifier.fillMaxWidth()) { onClose() }
        Box(Modifier.height(4.dp))
    }
}

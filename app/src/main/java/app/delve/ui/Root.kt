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
        BattleSheet(c)
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
        Text(w(ru, "СООБЩЕСТВО", "COMMUNITY"), style = T.Eyebrow)
        Gap(6.dp)
        val open = { url: String -> runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } }
        for ((label, url) in listOf(
            w(ru, "🐞 Сообщить об ошибке", "🐞 Report a bug") to Community.BUG,
            w(ru, "💡 Предложить идею", "💡 Suggest an idea") to Community.IDEA,
            w(ru, "🔔 Следить за обновлениями", "🔔 Follow updates") to Community.FOLLOW,
            w(ru, "💬 Чат StillCraft", "💬 StillCraft chat") to Community.CHAT,
            w(ru, "❤ Поддержать", "❤ Support") to Community.SUPPORT,
        )) { Btn(label, Modifier.fillMaxWidth(), BtnKind.LINE) { open(url) }; Gap(6.dp) }
        Gap()
        Text(w(ru, "Карта © участники OpenStreetMap. Погода – MET Norway.", "Map © OpenStreetMap contributors. Weather: MET Norway."), style = T.Small, textAlign = TextAlign.Center)
        Gap(8.dp)
        Btn(w(ru, "Закрыть", "Close"), Modifier.fillMaxWidth()) { onClose() }
        Box(Modifier.height(4.dp))
    }
}

/** The StillCraft bot: deep links file a bug or an idea for this app, or follow its releases. */
object Community {
    private const val BOT = "https://t.me/ArcaneWhisperBot?start="
    const val SLUG = "delve"
    const val BUG = "${BOT}bug_$SLUG"
    const val IDEA = "${BOT}wish_$SLUG"
    const val FOLLOW = "${BOT}f_$SLUG"
    const val CHAT = "https://t.me/+Q4gFEDk8O842OGVi"
    const val SUPPORT = "https://bigdevwhale.github.io/stillcraft/support/"
}

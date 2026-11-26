package app.delve.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.delve.R

/** An adventurer's notebook: aged paper, sepia ink, the blue of an old dungeon map, wax red, torch gold. */
@Immutable
object C {
    val paper = Color(0xFFEFE3C6)
    val paper2 = Color(0xFFE3D3AE)
    val paper3 = Color(0xFFD5C196)
    val floor = Color(0xFFFBF6EA)
    val ink = Color(0xFF2C2722)
    val ink2 = Color(0xFF4A4038)
    val muted = Color(0xFF7A6650)
    val faint = Color(0xFFA8957A)
    val blue = Color(0xFF3D6E9E)
    val blueDeep = Color(0xFF243447)
    val wash = Color(0xFF7FA7CC)
    val red = Color(0xFFA8432F)
    val redDeep = Color(0xFF6E2318)
    val gold = Color(0xFFC9952B)
    val flame = Color(0xFFF2A53A)
    val green = Color(0xFF5E7D46)
    val night = Color(0xFF1C1915)
}

val HeadFont = FontFamily(Font(R.font.alegreya, FontWeight.Normal))
val BodyFont = FontFamily(Font(R.font.nunito_regular, FontWeight.Normal), Font(R.font.nunito_semibold, FontWeight.SemiBold), Font(R.font.nunito_bold, FontWeight.Bold))
val HandFont = FontFamily(Font(R.font.caveat, FontWeight.Normal))

object T {
    val Display = TextStyle(fontFamily = HeadFont, fontSize = 34.sp, lineHeight = 38.sp, color = C.ink)
    val Title = TextStyle(fontFamily = HeadFont, fontSize = 26.sp, lineHeight = 30.sp, color = C.ink)
    val Heading = TextStyle(fontFamily = HeadFont, fontSize = 21.sp, lineHeight = 25.sp, color = C.ink)
    val Eyebrow = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.12.em, color = C.muted)
    val Body = TextStyle(fontFamily = BodyFont, fontSize = 15.sp, lineHeight = 21.sp, color = C.ink)
    val Strong = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp, color = C.ink)
    val Small = TextStyle(fontFamily = BodyFont, fontSize = 13.sp, lineHeight = 17.sp, color = C.muted)
    val Number = TextStyle(fontFamily = HeadFont, fontSize = 24.sp, lineHeight = 26.sp, color = C.ink)
    val Button = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp)
    val Note = TextStyle(fontFamily = HandFont, fontSize = 22.sp, lineHeight = 24.sp, color = C.blue)
}

@Composable
fun DelveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.ink, onPrimary = C.paper, background = C.paper, surface = C.paper, onSurface = C.ink,
            onBackground = C.ink, surfaceContainer = C.paper, surfaceContainerHigh = C.paper, surfaceContainerHighest = C.paper2,
            surfaceContainerLow = C.paper, outline = C.ink, error = C.red, secondaryContainer = C.paper2,
        ),
        typography = MaterialTheme.typography.copy(bodyLarge = T.Body, bodyMedium = T.Body, bodySmall = T.Small, labelLarge = T.Button, titleLarge = T.Title),
        content = content,
    )
}

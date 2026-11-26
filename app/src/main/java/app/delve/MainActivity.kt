package app.delve

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import app.delve.ui.Root
import app.delve.ui.theme.DelveTheme
import app.delve.work.Tracker

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(t), navigationBarStyle = SystemBarStyle.dark(t))
        setContent { DelveTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        container.visible = true
        Tracker.of(this).start()
        container.refreshWeather()
        container.checkEnd()
    }

    override fun onPause() {
        container.visible = false
        // in a pocket only an expedition keeps the GPS on (through its service)
        Tracker.of(this).stop()
        super.onPause()
    }
}

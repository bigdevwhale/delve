package app.delve.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.delve.BuildConfig
import app.delve.MainActivity
import app.delve.R
import app.delve.container
import app.delve.domain.Geo
import kotlinx.coroutines.flow.MutableStateFlow

/** Listens to the GPS while the app is open or an expedition is on, and feeds every fix to the game. */
class Tracker(private val context: Context) : LocationListener {
    private val lm = context.getSystemService(LocationManager::class.java)
    private val owners = HashSet<String>()
    private var listening = false

    val allowed get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(owner: String = "ui") {
        owners += owner
        if (!allowed || listening) return
        listening = true
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)) {
                runCatching { lm.requestLocationUpdates(p, 3000L, 3f, this, Looper.getMainLooper()) }
                runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.let { onLocationChanged(it) }
            }
        }
    }

    @Synchronized
    fun stop(owner: String = "ui") {
        owners -= owner
        if (owners.isEmpty() && listening) { listening = false; runCatching { lm.removeUpdates(this) } }
    }

    override fun onLocationChanged(l: Location) {
        // the demo lives on Petrogradka: a long press on the map walks the hero instead
        if (BuildConfig.DEMO) return
        context.container.fix(Geo(l.latitude, l.longitude), if (l.hasAccuracy()) l.accuracy else 30f)
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    companion object {
        @Volatile private var one: Tracker? = null
        fun of(c: Context) = one ?: synchronized(this) { one ?: Tracker(c.applicationContext).also { one = it } }
    }
}

/**
 * An expedition goes on with the phone in a pocket: Android lets an app hear the location in the background
 * only as a foreground service, so the descent shows a quiet notification with the torch until it ends.
 */
class WalkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        val ok = runCatching { ServiceCompat.startForeground(this, ID, note(this, getString(R.string.walk_text)), type) }.isSuccess
        if (!ok) { stopSelf(); return START_NOT_STICKY }
        if (!running) { running = true; Tracker.of(this).start("walk") }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running) { running = false; Tracker.of(this).stop("walk") }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "walk"
        const val ALERTS = "alerts"
        const val ID = 7
        const val STOP = "stop"
        @Volatile var running = false

        fun start(c: Context) = runCatching { ContextCompat.startForegroundService(c, Intent(c, WalkService::class.java)) }
        fun stop(c: Context) = runCatching { c.startService(Intent(c, WalkService::class.java).setAction(STOP)) }

        fun note(c: Context, text: String): Notification {
            val m = c.getSystemService(NotificationManager::class.java)
            if (m.getNotificationChannel(CHANNEL) == null) m.createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.channel_walk), NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(c.getString(R.string.walk_title))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
                .build()
        }

        /** Updates the torch line in the ongoing notification. */
        fun update(c: Context, text: String) {
            if (!running) return
            val m = c.getSystemService(NotificationManager::class.java) ?: return
            runCatching { m.notify(ID, note(c, text)) }
        }

        /** A heads-up about something met in a pocket: a room, a chase, the stairs. */
        fun alert(c: Context, title: String, text: String) {
            val m = c.getSystemService(NotificationManager::class.java) ?: return
            if (m.getNotificationChannel(ALERTS) == null) m.createNotificationChannel(NotificationChannel(ALERTS, c.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(c, ALERTS).setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
                .setContentIntent(open).setAutoCancel(true).build()
            runCatching { m.notify(8, n) }
        }
    }
}

/** Patterns felt through a pocket: a room, a chase, the stairs, the torch. */
object Buzz {
    const val ROOM = 0; const val CHASE = 1; const val STAIRS = 2; const val WARN = 3

    fun play(c: Context, kind: Int) {
        val v = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(VibratorManager::class.java)?.defaultVibrator else @Suppress("DEPRECATION") c.getSystemService(Vibrator::class.java)
        v ?: return
        val pattern = when (kind) {
            ROOM -> longArrayOf(0, 60)
            CHASE -> longArrayOf(0, 90, 80, 90, 80, 90)
            STAIRS -> longArrayOf(0, 300, 120, 60)
            else -> longArrayOf(0, 500)
        }
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }
}

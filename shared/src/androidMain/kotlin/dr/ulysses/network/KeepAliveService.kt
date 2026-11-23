package dr.ulysses.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dr.ulysses.Logger
import dr.ulysses.entities.BackgroundTasks
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.media3.ui.R as MediaUiR

/**
 * Keeps the app running while it is connected to a server or a task runs: Android freezes an app in the background
 * otherwise, which ends its connection. Its notification tells what for.
 */
class KeepAliveService : Service() {
    private val scope = MainScope()

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Connection and tasks",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        scope.launch {
            reasons().collect { text ->
                if (text == null) {
                    stopSelf()
                } else {
                    manager.notify(NOTIFICATION_ID, notification(text))
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Each start asks for the foreground again, within a few seconds
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(current ?: "Kristine"),
            if (Build.VERSION.SDK_INT >= 29) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
        )
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(MediaUiR.drawable.exo_notification_small_icon)
        .setContentTitle("Kristine")
        .setContentText(text)
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                packageManager.getLaunchIntentForPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    companion object {
        private const val NOTIFICATION_ID = 229
        private const val CHANNEL_ID = "kristine_keep_alive"

        // The last reason, for the notification the service starts with
        @Volatile
        private var current: String? = null

        /**
         * What keeps the app running, like "Connected to 192.168.8.101 · Sync: Downloading 3 of 20"; null for nothing.
         */
        private fun reasons(): Flow<String?> = combine(
            NetworkManager.currentServer,
            BackgroundTasks.running
        ) { server, tasks ->
            (listOfNotNull(server?.let { "Connected to ${it.first}" }) + tasks.map { it.text })
                .joinToString(" · ")
                .ifEmpty { null }
                .also { current = it }
        }.distinctUntilChanged()

        /**
         * Starts the service each time a reason comes up; it ends itself when none is left. Called once per process.
         */
        fun watch(context: Context) {
            MainScope().launch {
                reasons().map { it != null }.distinctUntilChanged().filter { it }.collect {
                    runCatching {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, KeepAliveService::class.java)
                        )
                    }
                        // Not allowed from the background (Android 12+): the reason came up without the user
                        .onFailure { Logger.w(it) { "Couldn't keep the app running in the background" } }
                }
            }
        }
    }
}

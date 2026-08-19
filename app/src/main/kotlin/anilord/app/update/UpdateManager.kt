package anilord.app.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import anilord.app.BuildConfig
import anilord.app.R
import anilord.app.main.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: UpdateRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _availableUpdate = MutableStateFlow<UpdateInfo?>(null)
    val availableUpdate: StateFlow<UpdateInfo?> = _availableUpdate.asStateFlow()
    private var isChecking = false

    fun check(force: Boolean = false) {
        if (isChecking || (!force && !isCheckDue())) return
        isChecking = true
        preferences.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        scope.launch {
            val update = repository.latest()
            _availableUpdate.value = update?.takeIf {
                it.versionCode > BuildConfig.VERSION_CODE && it.versionName != BuildConfig.VERSION_NAME
            }
            _availableUpdate.value?.let { maybeNotify(it) }
            isChecking = false
        }
    }

    private fun maybeNotify(update: UpdateInfo) {
        if (preferences.getLong(KEY_LAST_NOTIFIED_CODE, Long.MIN_VALUE) >= update.versionCode) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createNotificationChannel()
        val intent = Intent(Intent.ACTION_VIEW, update.apkUrl.toUri()).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val pendingIntent = PendingIntent.getActivity(
            context,
            update.versionCode.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            update.versionCode.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.update_available))
                .setContentText(context.getString(R.string.update_notification_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.update_notification_text)))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
        preferences.edit().putLong(KEY_LAST_NOTIFIED_CODE, update.versionCode).apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.update_available), NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun isCheckDue(): Boolean =
        System.currentTimeMillis() - preferences.getLong(KEY_LAST_CHECK, 0L) >= CHECK_INTERVAL_MS

    private companion object {
        const val PREFERENCES_NAME = "anilord_updates"
        const val KEY_LAST_CHECK = "last_check_at"
        const val KEY_LAST_NOTIFIED_CODE = "last_notified_version_code"
        const val CHANNEL_ID = "app_updates"
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}

private fun String.toUri() = android.net.Uri.parse(this)

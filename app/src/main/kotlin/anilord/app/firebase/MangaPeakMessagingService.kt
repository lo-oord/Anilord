package anilord.app.firebase

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import anilord.app.R
import anilord.app.core.util.ext.checkNotificationPermission
import anilord.app.main.ui.MainActivity

class MangaPeakMessagingService : FirebaseMessagingService() {

	override fun onMessageReceived(message: RemoteMessage) {
		super.onMessageReceived(message)
		val title = message.notification?.title
			?: message.data[KEY_TITLE]
			?: getString(R.string.app_name)
		val body = message.notification?.body
			?: message.data[KEY_BODY]
			?: message.data[KEY_MESSAGE]
			?: return
		showNotification(
			id = message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(),
			title = title,
			body = body,
		)
	}

	override fun onNewToken(token: String) {
		super.onNewToken(token)
		FirebaseCrashlytics.getInstance().log("Firebase Messaging token refreshed")
	}

	private fun showNotification(id: Int, title: String, body: String) {
		createNotificationChannel(this)
		if (!applicationContext.checkNotificationPermission(CHANNEL_ID)) {
			return
		}
		val contentIntent = Intent(this, MainActivity::class.java).apply {
			addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
		}
		val pendingIntent = PendingIntent.getActivity(
			this,
			id,
			contentIntent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
		)
		val notification = NotificationCompat.Builder(this, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_notification)
			.setColor(ContextCompat.getColor(this, R.color.blue_primary))
			.setContentTitle(title)
			.setContentText(body)
			.setStyle(NotificationCompat.BigTextStyle().bigText(body))
			.setContentIntent(pendingIntent)
			.setAutoCancel(true)
			.setCategory(NotificationCompat.CATEGORY_MESSAGE)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.build()
		NotificationManagerCompat.from(this).notify(id, notification)
	}

	companion object {
		const val CHANNEL_ID = "firebase_messages"
		private const val KEY_TITLE = "title"
		private const val KEY_BODY = "body"
		private const val KEY_MESSAGE = "message"

		fun createNotificationChannel(context: android.content.Context) {
			val channel = NotificationChannelCompat.Builder(
				CHANNEL_ID,
				NotificationManagerCompat.IMPORTANCE_DEFAULT,
			)
				.setName(context.getString(R.string.firebase_notification_channel_name))
				.setDescription(context.getString(R.string.firebase_notification_channel_description))
				.setShowBadge(true)
				.setLightColor(ContextCompat.getColor(context, R.color.blue_primary))
				.build()
			NotificationManagerCompat.from(context).createNotificationChannel(channel)
		}
	}
}

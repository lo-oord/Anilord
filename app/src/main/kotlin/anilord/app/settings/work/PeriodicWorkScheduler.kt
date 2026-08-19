package anilord.app.settings.work

interface PeriodicWorkScheduler {

	suspend fun schedule()

	suspend fun unschedule()

	suspend fun isScheduled(): Boolean
}

package anilord.app.update

/** A validated GitHub release that can be installed over the current app. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val apkUrl: String,
)

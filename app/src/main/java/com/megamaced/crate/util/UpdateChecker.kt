package com.megamaced.crate.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.megamaced.crate.BuildConfig
import com.megamaced.crate.R
import com.megamaced.crate.data.api.GitHubReleaseService
import com.megamaced.crate.data.prefs.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val service: GitHubReleaseService,
        private val userPreferences: UserPreferences,
    ) {
        sealed interface Result {
            data object UpToDate : Result

            data class UpdateAvailable(
                val tag: String,
                val htmlUrl: String,
            ) : Result

            data object Failed : Result
        }

        /**
         * User-initiated update check. Only ever called when the user
         * explicitly taps "Check for updates" in Settings — never on app
         * launch — so that an F-Droid (or any other) install of the app
         * never makes an unsolicited network call to github.com.
         */
        suspend fun checkNow(): Result {
            val release =
                runCatching { service.latestRelease() }
                    .onFailure { Timber.tag(TAG).d(it, "GitHub release check failed") }
                    .getOrNull() ?: return Result.Failed

            userPreferences.setUpdateLastCheckedAt(System.currentTimeMillis())

            if (release.draft || release.preRelease) return Result.UpToDate

            val latest = parseSemVer(release.tagName) ?: return Result.UpToDate
            val current = parseSemVer(BuildConfig.VERSION_NAME) ?: return Result.UpToDate
            if (latest <= current) return Result.UpToDate

            val tag = release.tagName
            val state = userPreferences.getUpdateState()
            if (tag != state.lastNotifiedVersion) {
                postNotification(tag, release.htmlUrl)
                userPreferences.setUpdateLastNotifiedVersion(tag)
            }
            return Result.UpdateAvailable(tag, release.htmlUrl)
        }

        private fun postNotification(
            tag: String,
            htmlUrl: String,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val manager = NotificationManagerCompat.from(context)
            ensureChannel()

            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_monochrome)
                    .setContentTitle(context.getString(R.string.update_available_title))
                    .setContentText(context.getString(R.string.update_available_text, tag))
                    .setStyle(
                        NotificationCompat
                            .BigTextStyle()
                            .bigText(context.getString(R.string.update_available_big_text, tag)),
                    ).setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

            manager.notify(NOTIFICATION_ID, notification)
        }

        private fun ensureChannel() {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing != null) return
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.update_channel_description)
                }
            manager.createNotificationChannel(channel)
        }

        companion object {
            private const val TAG = "UpdateChecker"
            private const val CHANNEL_ID = "updates"
            private const val NOTIFICATION_ID = 4711
        }
    }

/**
 * Loosely-parsed semver triple. Strips an optional leading `v` and any
 * `-something` / `+something` suffix, then compares numerically. Returns
 * `null` if the tag doesn't have at least one numeric component.
 */
internal data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int = compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)
}

internal fun parseSemVer(raw: String): SemVer? {
    val trimmed = raw.trim().removePrefix("v").removePrefix("V")
    val core = trimmed.substringBefore('-').substringBefore('+')
    val parts = core.split('.').mapNotNull { it.toIntOrNull() }
    if (parts.isEmpty()) return null
    return SemVer(
        major = parts.getOrElse(0) { 0 },
        minor = parts.getOrElse(1) { 0 },
        patch = parts.getOrElse(2) { 0 },
    )
}

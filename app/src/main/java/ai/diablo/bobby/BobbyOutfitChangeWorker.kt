package ai.diablo.bobby

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlin.random.Random

/**
 * Applies a scheduled wardrobe change and schedules the next one.
 * WorkManager persists this work across process death and device restarts.
 */
class BobbyOutfitChangeWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {
    override fun doWork(): Result {
        val store = BobbyAvatarStore(applicationContext)
        val look = store.load()
        if (!look.autoChangeEnabled) return Result.success()

        val now = System.currentTimeMillis()
        val intervalMillis = look.autoChangeHours.coerceIn(1, 168) * 3_600_000L
        val dueAt = if (look.lastChangeMillis <= 0L) now else look.lastChangeMillis + intervalMillis

        if (now >= dueAt) {
            val nextOutfit = if (look.outfit in 0..998) {
                var candidate = Random.nextInt(0, 1000)
                while (candidate == look.outfit) candidate = Random.nextInt(0, 1000)
                candidate
            } else Random.nextInt(0, 1000)
            val updated = look.copy(outfit = nextOutfit, lastChangeMillis = now)
            store.save(updated)
            BobbyOutfitScheduler.sync(applicationContext, updated)
        } else {
            BobbyOutfitScheduler.sync(applicationContext, look)
        }
        return Result.success()
    }
}

/** One-time work is used instead of periodic work so 1–168 hour intervals are supported. */
object BobbyOutfitScheduler {
    private const val UNIQUE_WORK = "bobby_automatic_wardrobe_change"

    fun sync(context: Context, look: BobbyAvatarLook) {
        val workManager = androidx.work.WorkManager.getInstance(context.applicationContext)
        if (!look.autoChangeEnabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK)
            return
        }

        val intervalMillis = look.autoChangeHours.coerceIn(1, 168) * 3_600_000L
        val dueAt = if (look.lastChangeMillis <= 0L) {
            System.currentTimeMillis()
        } else {
            look.lastChangeMillis + intervalMillis
        }
        val delayMillis = (dueAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = androidx.work.OneTimeWorkRequestBuilder<BobbyOutfitChangeWorker>()
            .setInitialDelay(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            .addTag(UNIQUE_WORK)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
